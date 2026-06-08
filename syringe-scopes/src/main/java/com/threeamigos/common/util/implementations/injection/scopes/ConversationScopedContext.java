package com.threeamigos.common.util.implementations.injection.scopes;

import com.threeamigos.common.util.implementations.injection.annotations.AnnotationsEnum;
import com.threeamigos.common.util.implementations.injection.resolution.BeanImpl;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.PassivationCapable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.threeamigos.common.util.implementations.injection.util.ClassHelper.collectClassHierarchyFromObject;

/**
 * Implementation of ConversationScoped context.
 * Maintains instances for the duration of a conversation, which spans multiple requests
 * and must be explicitly started and ended.
 *
 * <p><b>PHASE 2 - Interceptor Support:</b> This context automatically wraps beans that
 * have interceptors with interceptor-aware proxies. This ensures that interceptor chains
 * are executed before business methods are called.
 *
 * <p><b>CDI 4.1 Conversation Timeout:</b> This context implements automatic timeout handling
 * per CDI 4.1 Section 6.7.4. Conversations that are inactive for longer than the configured
 * timeout period are automatically destroyed. The default timeout is 30 minutes, but can be
 * configured via {@link #setDefaultTimeout(long, TimeUnit)}. Each conversation access updates
 * the last access time, preventing premature timeout.
 *
 * @author Stefano Reksten
 */
public class ConversationScopedContext implements ScopeContext {

    private final MessageHandler messageHandler;
    private final Map<String, Map<Bean<?>, Object>> conversationInstances = new ConcurrentHashMap<>();
    private final Map<String, Map<Bean<?>, CreationalContext<?>>> conversationContexts = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Bean<?>>> conversationBeans = new ConcurrentHashMap<>();
    private final Map<String, ConversationMetadata> conversationMetadata = new ConcurrentHashMap<>();
    private final ThreadLocal<String> currentConversationId = new ThreadLocal<>();
    private volatile boolean active = true;

    // Timeout configuration (default 30 minutes per CDI spec)
    private volatile long defaultTimeoutMillis = 30 * 60 * 1000; // 30 minutes

    // Scheduled cleanup for timed-out conversations
    private final ScheduledExecutorService timeoutScheduler = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r, "ConversationTimeout-Cleaner");
        t.setDaemon(true); // Don't prevent JVM shutdown
        try {
            t.setContextClassLoader(ConversationScopedContext.class.getClassLoader());
        } catch (SecurityException ignored) {
            // Best-effort only.
        }
        return t;
    });

    /**
     * Metadata for tracking conversation timeout.
     * Thread-safe via volatile fields and atomic operations.
     */
    private static class ConversationMetadata {
        private volatile long lastAccessTime;
        private volatile long timeoutMillis;

        ConversationMetadata(long timeoutMillis) {
            this.lastAccessTime = System.currentTimeMillis();
            this.timeoutMillis = timeoutMillis;
        }

        void touch() {
            this.lastAccessTime = System.currentTimeMillis();
        }

        boolean isTimedOut() {
            return System.currentTimeMillis() - lastAccessTime > timeoutMillis;
        }

        long getLastAccessTime() {
            return lastAccessTime;
        }

        long getTimeoutMillis() {
            return timeoutMillis;
        }

        void setTimeout(long timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
        }
    }

    /**
     * Constructor that starts the timeout cleanup scheduler.
     * The scheduler runs every 5 minutes to clean up expired conversations.
     */
    public ConversationScopedContext(MessageHandler messageHandler) {
        this.messageHandler = Objects.requireNonNull(messageHandler, "messageHandler cannot be null");
        // Schedule cleanup every 5 minutes
        timeoutScheduler.scheduleAtFixedRate(
            this::cleanupTimedOutConversations,
            5, // initial delay
            5, // period
            TimeUnit.MINUTES
        );
    }

    /**
     * Sets the default timeout for new conversations.
     * Existing conversations are not affected.
     *
     * @param timeout the timeout value
     * @param unit the time unit
     */
    public void setDefaultTimeout(long timeout, TimeUnit unit) {
        if (timeout <= 0) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        this.defaultTimeoutMillis = unit.toMillis(timeout);
    }

    /**
     * Gets the default timeout in milliseconds.
     *
     * @return the default timeout
     */
    public long getDefaultTimeoutMillis() {
        return defaultTimeoutMillis;
    }

    /**
     * Sets the timeout for a specific conversation.
     *
     * @param conversationId the conversation ID
     * @param timeout the timeout value
     * @param unit the time unit
     */
    public void setTimeout(String conversationId, long timeout, TimeUnit unit) {
        if (timeout <= 0) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        ConversationMetadata metadata = conversationMetadata.get(conversationId);
        if (metadata != null) {
            metadata.setTimeout(unit.toMillis(timeout));
        }
    }

    /**
     * Gets the timeout for a specific conversation in milliseconds.
     *
     * @param conversationId the conversation ID
     * @return the timeout in milliseconds, or -1 if conversation doesn't exist
     */
    public long getTimeout(String conversationId) {
        ConversationMetadata metadata = conversationMetadata.get(conversationId);
        return metadata != null ? metadata.getTimeoutMillis() : -1;
    }

    /**
     * Gets the last access time for a specific conversation.
     *
     * @param conversationId the conversation ID
     * @return the last access time in milliseconds since epoch, or -1 if conversation doesn't exist
     */
    public long getLastAccessTime(String conversationId) {
        ConversationMetadata metadata = conversationMetadata.get(conversationId);
        return metadata != null ? metadata.getLastAccessTime() : -1;
    }

    /**
     * Begins a new conversation with the given ID and default timeout.
     *
     * @param conversationId the unique identifier for this conversation
     */
    public void beginConversation(String conversationId) {
        currentConversationId.set(conversationId);
        conversationInstances.putIfAbsent(conversationId, new ConcurrentHashMap<>());
        conversationContexts.putIfAbsent(conversationId, new ConcurrentHashMap<>());
        conversationBeans.putIfAbsent(conversationId, new ConcurrentHashMap<>());
        conversationMetadata.putIfAbsent(conversationId, new ConversationMetadata(defaultTimeoutMillis));
    }

    /**
     * Begins a new conversation with the given ID and custom timeout.
     *
     * @param conversationId the unique identifier for this conversation
     * @param timeout the timeout value
     * @param unit the time unit
     */
    public void beginConversation(String conversationId, long timeout, TimeUnit unit) {
        if (timeout <= 0) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        currentConversationId.set(conversationId);
        conversationInstances.putIfAbsent(conversationId, new ConcurrentHashMap<>());
        conversationContexts.putIfAbsent(conversationId, new ConcurrentHashMap<>());
        conversationMetadata.putIfAbsent(conversationId, new ConversationMetadata(unit.toMillis(timeout)));
    }

    /**
     * Ends the current conversation, destroying all associated instances.
     */
    public void endConversation() {
        String conversationId = currentConversationId.get();
        if (conversationId != null) {
            destroyConversation(conversationId);
            currentConversationId.remove();
        }
    }

    /**
     * Ends a specific conversation by ID.
     *
     * @param conversationId the conversation to end
     */
    public void endConversation(String conversationId) {
        destroyConversation(conversationId);
        if (conversationId.equals(currentConversationId.get())) {
            currentConversationId.remove();
        }
    }

    /**
     * Gets the current conversation ID.
     *
     * @return the current conversation ID, or null if no conversation is active
     */
    public String getCurrentConversationId() {
        return currentConversationId.get();
    }

    /**
     * Synchronizes this context with the Conversation bean state.
     * Called by ConversationImpl when a conversation is promoted to long-running.
     *
     * <p>This method:
     * <ul>
     *   <li>Sets the conversation ID for the current thread</li>
     *   <li>Initializes conversation storage if needed</li>
     *   <li>Creates metadata for timeout tracking</li>
     * </ul>
     *
     * @param conversationId the conversation ID to synchronize with
     */
    public void syncWithConversation(String conversationId) {
        currentConversationId.set(conversationId);
        conversationInstances.putIfAbsent(conversationId, new ConcurrentHashMap<>());
        conversationContexts.putIfAbsent(conversationId, new ConcurrentHashMap<>());
        conversationBeans.putIfAbsent(conversationId, new ConcurrentHashMap<>());
        conversationMetadata.putIfAbsent(conversationId,
            new ConversationMetadata(defaultTimeoutMillis));
    }

    /**
     * Clears the current thread's conversation ID.
     * Called by ConversationPropagationFilter at the request end to prevent memory leaks.
     */
    public void clearCurrentThread() {
        currentConversationId.remove();
    }

    /**
     * Touches the current conversation, updating its last access time.
     * This prevents the conversation from timing out.
     */
    private void touchCurrentConversation() {
        String conversationId = currentConversationId.get();
        if (conversationId != null) {
            ConversationMetadata metadata = conversationMetadata.get(conversationId);
            if (metadata != null) {
                metadata.touch();
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Bean<T> bean, CreationalContext<T> creationalContext) {
        if (!active) {
            throw new ContextNotActiveException("ConversationScoped context is not active");
        }

        String conversationId = currentConversationId.get();
        if (conversationId == null) {
            throw new ContextNotActiveException("No active conversation. Call beginConversation() first.");
        }

        // Touch conversation to update last access time
        touchCurrentConversation();

        Map<Bean<?>, Object> instances = conversationInstances.get(conversationId);
        Map<Bean<?>, CreationalContext<?>> contexts = conversationContexts.get(conversationId);
        Map<String, Bean<?>> beans = conversationBeans.computeIfAbsent(conversationId, id -> new ConcurrentHashMap<>());

        String beanId = getBeanId(bean);
        return (T) instances.computeIfAbsent(bean, b -> {
            if (creationalContext != null) {
                contexts.put(bean, creationalContext);
            }
            beans.put(beanId, bean);

            // Step 1: Create the actual bean instance
            T instance = bean.create(creationalContext);

            // Step 2: PHASE 2 - Wrap with interceptor-aware proxy if bean has interceptors
            if (bean instanceof BeanImpl) {
                BeanImpl<T> beanImpl = (BeanImpl<T>) bean;
                if (beanImpl.hasInterceptors()) {
                    instance = beanImpl.createInterceptorAwareProxy(instance);
                }
            }

            return instance;
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getIfExists(Bean<T> bean) {
        String conversationId = currentConversationId.get();
        if (conversationId == null) {
            return null;
        }

        // Touch conversation to update last access time
        touchCurrentConversation();

        Map<Bean<?>, Object> instances = conversationInstances.get(conversationId);
        return instances != null ? (T) instances.get(bean) : null;
    }

    @Override
    public void destroy() {
        // Shutdown timeout scheduler
        timeoutScheduler.shutdown();
        try {
            if (!timeoutScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                timeoutScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            timeoutScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Destroy all conversations
        for (String conversationId : conversationInstances.keySet()) {
            destroyConversation(conversationId);
        }
        conversationInstances.clear();
        conversationContexts.clear();
        conversationBeans.clear();
        conversationMetadata.clear();
        currentConversationId.remove();
        active = false;
    }

    @Override
    public boolean isActive() {
        return active && currentConversationId.get() != null;
    }

    @Override
    public boolean isPassivationCapable() {
        // ConversationScoped beans CAN be passivated (serialized to disk/database)
        // when long-running conversations are passivated by the servlet container
        // Therefore, beans in this scope MUST be Serializable
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void destroy(Contextual<?> contextual) {
        if (!active) {
            throw new ContextNotActiveException("ConversationScoped context is not active");
        }
        String conversationId = currentConversationId.get();
        if (conversationId == null) {
            throw new ContextNotActiveException("No active conversation. Call beginConversation() first.");
        }
        if (!(contextual instanceof Bean)) {
            return;
        }

        Bean<Object> bean = (Bean<Object>) contextual;
        Map<Bean<?>, Object> instances = conversationInstances.get(conversationId);
        Map<Bean<?>, CreationalContext<?>> contexts = conversationContexts.get(conversationId);
        Map<String, Bean<?>> beans = conversationBeans.get(conversationId);
        if (instances == null || contexts == null) {
            return;
        }

        Object instance = instances.remove(bean);
        CreationalContext<Object> ctx = (CreationalContext<Object>) contexts.remove(bean);
        if (beans != null) {
            beans.remove(getBeanId(bean));
        }
        if (instance != null) {
            bean.destroy(instance, ctx);
        }
    }

    /**
    * Passivates (serializes) a conversation to a byte array.
    *
    * <p>Before serialization, invokes @PrePassivate on all beans to allow resource cleanup.</p>
    */
    @SuppressWarnings("unchecked")
    public byte[] passivateConversation(String conversationId) {
        Map<Bean<?>, Object> instances = conversationInstances.get(conversationId);
        Map<String, Bean<?>> beans = conversationBeans.get(conversationId);

        if (instances == null || instances.isEmpty()) {
            return null;
        }

        // Step 1: Invoke @PrePassivate on all beans
        if (beans != null) {
            for (Map.Entry<Bean<?>, Object> entry : instances.entrySet()) {
                Bean<?> bean = entry.getKey();
                Object instance = entry.getValue();

                if (bean instanceof BeanImpl) {
                    BeanImpl<Object> beanImpl = (BeanImpl<Object>) bean;
                    try {
                        beanImpl.invokePrePassivate(instance);
                    } catch (Exception e) {
                        messageHandler.exception(
                            "Error invoking @PrePassivate on bean " + bean.getBeanClass().getName() +
                                " in conversation " + conversationId + ": " + e.getMessage(),
                            e
                        );
                    }
                } else {
                    invokeAnnotationIfPresent(instance, AnnotationsEnum.PRE_PASSIVATE);
                }
            }
        }

        // Step 2: Serialize instances + bean class metadata
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {

            Map<String, Object> serializableInstances = new HashMap<>();
            Map<String, String> beanClasses = new HashMap<>();

            for (Map.Entry<Bean<?>, Object> entry : instances.entrySet()) {
                Bean<?> bean = entry.getKey();
                String id = getBeanId(bean);
                serializableInstances.put(id, entry.getValue());
                beanClasses.put(id, bean.getBeanClass().getName());
            }

            ConversationPassivationData data = new ConversationPassivationData(serializableInstances, beanClasses);
            oos.writeObject(data);
            return baos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Conversation passivation failed for " + conversationId, e);
        }
    }

    /**
    * Activates (deserializes) a conversation from a byte array.
    *
    * <p>After deserialization, invokes @PostActivate on all beans.</p>
    */
    @SuppressWarnings("unchecked")
    public void activateConversation(String conversationId, byte[] serializedData) {
        if (serializedData == null) {
            throw new IllegalArgumentException("Serialized data cannot be null");
        }

        ConversationPassivationData data;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(serializedData);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            data = (ConversationPassivationData) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Conversation activation failed for " + conversationId, e);
        }

        Map<Bean<?>, Object> restoredInstances = new ConcurrentHashMap<>();
        Map<Bean<?>, CreationalContext<?>> contexts = new ConcurrentHashMap<>();
        Map<String, Bean<?>> beans = conversationBeans.computeIfAbsent(conversationId, id -> new ConcurrentHashMap<>());

        BeanManager beanManager = CDI.current().getBeanManager();

        for (Map.Entry<String, Object> entry : data.getInstances().entrySet()) {
            String beanId = entry.getKey();
            String className = data.getBeanClasses().get(beanId);

            Bean<?> bean = beans.get(beanId);
            if (bean == null) {
                bean = resolveBean(beanManager, beanId, className);
                if (bean != null) {
                    beans.put(beanId, bean);
                }
            }

            if (bean != null) {
                restoredInstances.put(bean, entry.getValue());
            }
        }

        conversationInstances.put(conversationId, restoredInstances);
        conversationContexts.put(conversationId, contexts);
        conversationMetadata.putIfAbsent(conversationId, new ConversationMetadata(defaultTimeoutMillis));

        currentConversationId.set(conversationId);

        // Invoke @PostActivate
        for (Map.Entry<Bean<?>, Object> entry : restoredInstances.entrySet()) {
            Bean<?> bean = entry.getKey();
            Object instance = entry.getValue();

            if (bean instanceof BeanImpl) {
                BeanImpl<Object> beanImpl = (BeanImpl<Object>) bean;
                try {
                    beanImpl.invokePostActivate(instance);
                } catch (Exception e) {
                    messageHandler.exception(
                        "Error invoking @PostActivate on bean " + bean.getBeanClass().getName() +
                            " in conversation " + conversationId + ": " + e.getMessage(),
                        e
                    );
                }
            } else {
                invokeAnnotationIfPresent(instance, AnnotationsEnum.POST_ACTIVATE);
            }
        }
    }

    /**
     * Cleans up timed-out conversations.
     * Called periodically by the timeout scheduler.
     */
    private void cleanupTimedOutConversations() {
        if (!active) {
            return;
        }

        for (Map.Entry<String, ConversationMetadata> entry : conversationMetadata.entrySet()) {
            String conversationId = entry.getKey();
            ConversationMetadata metadata = entry.getValue();

            if (metadata.isTimedOut()) {
                messageHandler.info("Conversation " + conversationId + " timed out after " +
                    TimeUnit.MILLISECONDS.toMinutes(metadata.getTimeoutMillis()) + " minutes of inactivity. Destroying...");
                destroyConversation(conversationId);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void destroyConversation(String conversationId) {
        Map<Bean<?>, Object> instances = conversationInstances.remove(conversationId);
        Map<Bean<?>, CreationalContext<?>> contexts = conversationContexts.remove(conversationId);
        conversationBeans.remove(conversationId);
        conversationMetadata.remove(conversationId);

        if (instances != null) {
            for (Map.Entry<Bean<?>, Object> entry : instances.entrySet()) {
                Bean<Object> bean = (Bean<Object>) entry.getKey();
                Object instance = entry.getValue();
                CreationalContext<Object> ctx = contexts != null ? (CreationalContext<Object>) contexts.get(bean) : null;

                try {
                    bean.destroy(instance, ctx);
                } catch (Exception e) {
                    messageHandler.exception(
                        "Error destroying bean " + bean.getBeanClass().getName() +
                            " in conversation " + conversationId + ": " + e.getMessage(),
                        e
                    );
                }
            }
        }
    }

    private String getBeanId(Bean<?> bean) {
        if (bean instanceof PassivationCapable) {
            String id = ((PassivationCapable) bean).getId();
            if (id != null) {
                return id;
            }
        }

        String qualifierSignature = bean.getQualifiers().stream()
            .map(Annotation::annotationType)
            .map(Class::getName)
            .sorted()
            .collect(Collectors.joining(","));

        return bean.getBeanClass().getName() + "|" + qualifierSignature;
    }

    private Bean<?> resolveBean(BeanManager beanManager, String beanId, String className) {
        try {
            Bean<?> bean = beanManager.getPassivationCapableBean(beanId);
            if (bean != null) {
                return bean;
            }
        } catch (Exception ignored) {
            // getPassivationCapableBean may throw if ID not found; ignore and fallback
        }

        try {
            Class<?> clazz = Class.forName(className);
            Set<Bean<?>> candidates = beanManager.getBeans(clazz);
            if (!candidates.isEmpty()) {
                return beanManager.resolve(candidates);
            }
        } catch (ClassNotFoundException ignored) {
            // Class disappeared between passivation/activation
        }
        return null;
    }

    private void invokeAnnotationIfPresent(Object instance, AnnotationsEnum annotationType) {
        if (instance == null || annotationType == null) {
            return;
        }
        try {
            for (Class<?> clazz : collectClassHierarchyFromObject(instance)) {
                for (Method method : clazz.getDeclaredMethods()) {
                    if (annotationType.isPresent(method)) {
                        method.setAccessible(true);
                        method.invoke(instance);
                    }
                }
            }
        } catch (Exception e) {
            messageHandler.exception(
                "Error invoking @" + annotationType.name() + " on " + instance.getClass().getName() +
                    ": " + e.getMessage(),
                e
            );
        }
    }

    private static class ConversationPassivationData implements Serializable {
        private static final long serialVersionUID = 1L;
        private final Map<String, Object> instances;
        private final Map<String, String> beanClasses;

        ConversationPassivationData(Map<String, Object> instances, Map<String, String> beanClasses) {
            this.instances = instances;
            this.beanClasses = beanClasses;
        }

        Map<String, Object> getInstances() {
            return instances;
        }

        Map<String, String> getBeanClasses() {
            return beanClasses;
        }
    }
}
