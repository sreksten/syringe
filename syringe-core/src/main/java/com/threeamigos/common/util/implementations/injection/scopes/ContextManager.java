package com.threeamigos.common.util.implementations.injection.scopes;

import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.ConversationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.inject.UnproxyableResolutionException;
import jakarta.enterprise.inject.spi.Bean;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsEnum.APPLICATION_SCOPED;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsEnum.DEPENDENT;

/**
 * Manages all scoped contexts for the CDI container.
 * Maps scope annotations to their corresponding context implementations.
 *
 * @author Stefano Reksten
 */
public class ContextManager {
    public interface RequestContextLifecycleListener {
        void onInitialized();
        void onBeforeDestroyed();
        void onDestroyed();
    }

    public interface ApplicationContextLifecycleListener {
        void onInitialized();
        void onBeforeDestroyed();
        void onDestroyed();
    }

    private final MessageHandler messageHandler;

    private final Map<Class<? extends Annotation>, ScopeContext> contexts = new ConcurrentHashMap<>();
    private final Map<Class<? extends Annotation>, List<ScopeContext>> contextsByScope = new ConcurrentHashMap<>();
    private final ConversationScopedContext conversationContext;
    private final SessionScopedContext sessionContext;
    private final RequestScopedContext requestContext;
    private volatile boolean destroyed = false;
    private volatile RequestContextLifecycleListener requestContextLifecycleListener;
    private volatile ApplicationContextLifecycleListener applicationContextLifecycleListener;

    private final ClientProxyGenerator proxyGenerator;

    public ContextManager(MessageHandler messageHandler) {

        this.messageHandler = messageHandler;

        // Initialize built-in contexts
        contexts.put(ApplicationScoped.class, new ApplicationScopedContext());
        contexts.put(Dependent.class, new DependentContext());

        // Initialize and register conversation, session, and request contexts
        conversationContext = new ConversationScopedContext(messageHandler);
        sessionContext = new SessionScopedContext(messageHandler);
        requestContext = new RequestScopedContext();
        ConversationImpl.setConversationContext(conversationContext);

        contexts.put(ConversationScoped.class, conversationContext);
        contexts.put(SessionScoped.class, sessionContext);
        contexts.put(RequestScoped.class, requestContext);
        contextsByScope.put(ApplicationScoped.class, new CopyOnWriteArrayList<>(Collections.singletonList(contexts.get(ApplicationScoped.class))));
        contextsByScope.put(Dependent.class, new CopyOnWriteArrayList<>(Collections.singletonList(contexts.get(Dependent.class))));
        contextsByScope.put(ConversationScoped.class, new CopyOnWriteArrayList<>(Collections.singletonList(conversationContext)));
        contextsByScope.put(SessionScoped.class, new CopyOnWriteArrayList<>(Collections.singletonList(sessionContext)));
        contextsByScope.put(RequestScoped.class, new CopyOnWriteArrayList<>(Collections.singletonList(requestContext)));

        // Initialize proxy generator
        proxyGenerator = new ClientProxyGenerator(this);
    }

    /**
     * Gets the context for a given scope annotation.
     *
     * @param scopeAnnotation the scope annotation class
     * @return the corresponding context
     * @throws IllegalArgumentException if the scope is not supported
     */
    public ScopeContext getContext(Class<? extends Annotation> scopeAnnotation) {
        if (destroyed) {
            throw new IllegalStateException("CDI container is shut down");
        }
        List<ScopeContext> registered = contextsByScope.get(scopeAnnotation);
        if (registered == null || registered.isEmpty()) {
            throw new IllegalArgumentException("Unsupported scope: " + scopeAnnotation.getName());
        }
        List<ScopeContext> activeContexts = getActiveContexts(scopeAnnotation);
        if (activeContexts.isEmpty()) {
            return registered.get(0);
        }
        if (activeContexts.size() > 1) {
            throw new IllegalStateException("More than one active context for scope: " + scopeAnnotation.getName());
        }
        return activeContexts.get(0);
    }

    public List<ScopeContext> getActiveContexts(Class<? extends Annotation> scopeAnnotation) {
        if (destroyed) {
            throw new IllegalStateException("CDI container is shut down");
        }
        List<ScopeContext> registered = contextsByScope.get(scopeAnnotation);
        if (registered == null || registered.isEmpty()) {
            throw new IllegalArgumentException("Unsupported scope: " + scopeAnnotation.getName());
        }
        List<ScopeContext> activeContexts = new ArrayList<>();
        for (ScopeContext context : registered) {
            if (context != null && context.isActive()) {
                activeContexts.add(context);
            }
        }
        return activeContexts;
    }

    public Collection<ScopeContext> getRegisteredContexts(Class<? extends Annotation> scopeAnnotation) {
        if (destroyed) {
            throw new IllegalStateException("CDI container is shut down");
        }
        List<ScopeContext> registered = contextsByScope.get(scopeAnnotation);
        if (registered == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(registered);
    }

    /**
     * Destroys all contexts and their instances.
     */
    public void destroyAll() {
        if (destroyed) {
            return;
        }

        Map<ScopeContext, Boolean> destroyedContexts = new IdentityHashMap<>();
        ScopeContext applicationScopeContext = contexts.get(ApplicationScoped.class);
        ApplicationContextLifecycleListener applicationListener = applicationContextLifecycleListener;
        if (applicationScopeContext != null) {
            try {
                if (applicationListener != null) {
                    applicationListener.onBeforeDestroyed();
                }
                if (applicationListener != null) {
                    applicationListener.onDestroyed();
                }
                applicationScopeContext.destroy();
                destroyedContexts.put(applicationScopeContext, Boolean.TRUE);
            } catch (Exception e) {
                messageHandler.error("Error destroying context: " + e.getMessage());
            }
        }

        // Destroy all registered contexts, including additional custom contexts per scope.
        for (Map.Entry<Class<? extends Annotation>, List<ScopeContext>> entry : contextsByScope.entrySet()) {
            if (APPLICATION_SCOPED.matches(entry.getKey())) {
                continue;
            }
            List<ScopeContext> registered = entry.getValue();
            if (registered == null) {
                continue;
            }
            for (ScopeContext scopeContext : registered) {
                if (scopeContext == null || destroyedContexts.containsKey(scopeContext)) {
                    continue;
                }
                try {
                    scopeContext.destroy();
                } catch (Exception e) {
                    messageHandler.error("Error destroying context: " + e.getMessage());
                } finally {
                    destroyedContexts.put(scopeContext, Boolean.TRUE);
                }
            }
        }

        // If built-in contexts were replaced, ensure original built-in instances are also destroyed.
        destroyContextIfNeeded(conversationContext, destroyedContexts);
        destroyContextIfNeeded(sessionContext, destroyedContexts);
        destroyContextIfNeeded(requestContext, destroyedContexts);

        // Ensure ThreadLocal state is released for the current thread at shutdown.
        try {
            conversationContext.clearCurrentThread();
        } catch (Exception ignored) {
        }
        try {
            sessionContext.deactivateSession();
        } catch (Exception ignored) {
        }
        try {
            requestContext.deactivateRequest();
        } catch (Exception ignored) {
        }

        // Drop proxy class caches and context references.
        proxyGenerator.clearCache();
        contexts.clear();
        contextsByScope.clear();
        requestContextLifecycleListener = null;
        applicationContextLifecycleListener = null;

        destroyed = true;
    }

    private void destroyContextIfNeeded(ScopeContext context, Map<ScopeContext, Boolean> destroyedContexts) {
        if (context == null || destroyedContexts.containsKey(context)) {
            return;
        }
        try {
            context.destroy();
        } catch (Exception e) {
            messageHandler.error("Error destroying context: " + e.getMessage());
        } finally {
            destroyedContexts.put(context, Boolean.TRUE);
        }
    }

    // === Conversation Scope Management ===

    /**
     * Begins a new conversation with the given ID.
     *
     * @param conversationId the unique identifier for this conversation
     */
    public void beginConversation(String conversationId) {
        ConversationImpl.setConversationContext(conversationContext);
        conversationContext.beginConversation(conversationId);
    }

    /**
     * Ends the current conversation.
     */
    public void endConversation() {
        ConversationImpl.setConversationContext(conversationContext);
        conversationContext.endConversation();
    }

    /**
     * Ends a specific conversation by ID.
     *
     * @param conversationId the conversation to end
     */
    public void endConversation(String conversationId) {
        ConversationImpl.setConversationContext(conversationContext);
        conversationContext.endConversation(conversationId);
    }

    /**
     * Gets the current conversation ID.
     *
     * @return the current conversation ID, or null if no conversation is active
     */
    public String getCurrentConversationId() {
        return conversationContext.getCurrentConversationId();
    }

    // === Session Scope Management ===

    /**
     * Activates a session for the current thread.
     *
     * @param sessionId the session identifier
     */
    public void activateSession(String sessionId) {
        sessionContext.activateSession(sessionId);
    }

    /**
     * Deactivates the session from the current thread.
     */
    public void deactivateSession() {
        sessionContext.deactivateSession();
    }

    /**
     * Invalidates and destroys a specific session.
     *
     * @param sessionId the session to invalidate
     */
    public void invalidateSession(String sessionId) {
        sessionContext.invalidateSession(sessionId);
    }

    /**
     * Gets the current session ID.
     *
     * @return the current session ID, or null if no session is active
     */
    public String getCurrentSessionId() {
        return sessionContext.getCurrentSessionId();
    }

    // === Request Scope Management ===

    /**
     * Activates the request scope for the current thread.
     */
    public void activateRequest() {
        ConversationImpl.setConversationContext(conversationContext);
        requestContext.activateRequest();
        if (conversationContext.getCurrentConversationId() == null) {
            String transientConversationId = ConversationImpl.getCurrentConversationStateId();
            if (transientConversationId != null && !transientConversationId.trim().isEmpty()) {
                conversationContext.beginConversation(transientConversationId);
            }
        }
        RequestContextLifecycleListener listener = requestContextLifecycleListener;
        if (listener != null) {
            listener.onInitialized();
        }
    }

    /**
     * Deactivates the request scope for the current thread.
     */
    public void deactivateRequest() {
        boolean wasActive = requestContext.isActive();
        RequestContextLifecycleListener listener = requestContextLifecycleListener;
        if (wasActive && listener != null) {
            listener.onBeforeDestroyed();
        }
        requestContext.deactivateRequest();
        String currentConversationId = conversationContext.getCurrentConversationId();
        if (currentConversationId != null && ConversationImpl.isCurrentConversationTransient()) {
            conversationContext.endConversation(currentConversationId);
        }
        conversationContext.clearCurrentThread();
        ConversationImpl.clearCurrentConversation();
        if (wasActive && listener != null) {
            listener.onDestroyed();
        }
    }

    // === Proxy Management ===

    /**
     * Checks if a scope annotation represents a normal scope (requires proxies).
     * <p>
     * Normal scopes in CDI:
     * - @ApplicationScoped
     * - @RequestScoped
     * - @SessionScoped
     * - @ConversationScoped
     * <p>
     * Pseudo-scopes (no proxies needed):
     * - @Dependent
     *
     * @param scopeAnnotation the scope annotation to check
     * @return true if this is a normal scope that requires proxies
     */
    public boolean isNormalScope(Class<? extends Annotation> scopeAnnotation) {
        // Dependent is a pseudo-scope, not a normal scope
        if (DEPENDENT.matches(scopeAnnotation)) {
            return false;
        }

        // All other registered scopes are normal scopes
        return contexts.containsKey(scopeAnnotation);
    }

    /**
     * Creates a client proxy for a bean.
     * <p>
     * This is called during bean creation for normal-scoped beans.
     * The proxy will delegate all method calls to the contextual instance
     * from the appropriate scope.
     *
     * @param bean the bean to create a proxy for
     * @param <T> the bean type
     * @return a client proxy instance
     */
    public <T> T createClientProxy(Bean<T> bean) {
        try {
            return proxyGenerator.createProxy(bean);
        } catch (UnproxyableResolutionException e) {
            throw e;
        } catch (RuntimeException | Error e) {
            throw new UnproxyableResolutionException(
                    "Cannot create client proxy for bean " + bean.getBeanClass().getName(), e);
        }
    }

    /**
     * Registers a custom scope context programmatically.
     *
     * <p>This allows runtime registration of custom scopes, useful for:
     * <ul>
     *   <li>Testing with custom scopes</li>
     *   <li>Legacy ScopeHandler adaptation</li>
     *   <li>Dynamic scope registration</li>
     * </ul>
     *
     * @param scopeAnnotation the scope annotation class
     * @param context the scope context implementation
     * @throws IllegalArgumentException if any parameter is null
     */
    public void registerContext(Class<? extends Annotation> scopeAnnotation, ScopeContext context) {
        if (scopeAnnotation == null) {
            throw new IllegalArgumentException("Scope annotation cannot be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        if (isBuiltInScope(scopeAnnotation)) {
            ScopeContext previousContext = contexts.get(scopeAnnotation);
            List<ScopeContext> previousRegisteredContexts = contextsByScope.get(scopeAnnotation);

            // CDI allows extensions to replace built-in contexts; keep a single active mapping.
            CopyOnWriteArrayList<ScopeContext> replacement = new CopyOnWriteArrayList<>();
            replacement.add(context);
            contextsByScope.put(scopeAnnotation, replacement);
            contexts.put(scopeAnnotation, context);

            // Avoid leaking replaced built-in contexts (especially conversation timeout schedulers).
            destroyReplacedBuiltInContexts(previousContext, previousRegisteredContexts, context);
            return;
        }
        List<ScopeContext> registered =
                contextsByScope.computeIfAbsent(scopeAnnotation, key -> new CopyOnWriteArrayList<>());
        for (ScopeContext existing : registered) {
            if (Objects.equals(existing, context)) {
                return;
            }
        }
        registered.add(context);
        if (!contexts.containsKey(scopeAnnotation)) {
            contexts.put(scopeAnnotation, context);
        }
    }

    private void destroyReplacedBuiltInContexts(ScopeContext previousContext,
                                                List<ScopeContext> previousRegisteredContexts,
                                                ScopeContext replacementContext) {
        Map<ScopeContext, Boolean> destroyed = new IdentityHashMap<>();
        destroyContextIfReplaced(previousContext, replacementContext, destroyed);
        if (previousRegisteredContexts == null) {
            return;
        }
        for (ScopeContext scopeContext : previousRegisteredContexts) {
            destroyContextIfReplaced(scopeContext, replacementContext, destroyed);
        }
    }

    private void destroyContextIfReplaced(ScopeContext candidate,
                                          ScopeContext replacementContext,
                                          Map<ScopeContext, Boolean> destroyed) {
        if (candidate == null || candidate == replacementContext || destroyed.containsKey(candidate)) {
            return;
        }
        try {
            candidate.destroy();
        } catch (Exception e) {
            messageHandler.error("Error destroying replaced context: " + e.getMessage());
        } finally {
            destroyed.put(candidate, Boolean.TRUE);
        }
    }

    private boolean isBuiltInScope(Class<? extends Annotation> scopeAnnotation) {
        return ApplicationScoped.class.equals(scopeAnnotation)
                || Dependent.class.equals(scopeAnnotation)
                || ConversationScoped.class.equals(scopeAnnotation)
                || SessionScoped.class.equals(scopeAnnotation)
                || RequestScoped.class.equals(scopeAnnotation);
    }

    public void setRequestContextLifecycleListener(RequestContextLifecycleListener listener) {
        this.requestContextLifecycleListener = listener;
    }

    public void setApplicationContextLifecycleListener(ApplicationContextLifecycleListener listener) {
        this.applicationContextLifecycleListener = listener;
    }

    public void fireApplicationContextInitialized() {
        ApplicationContextLifecycleListener listener = applicationContextLifecycleListener;
        if (listener != null) {
            listener.onInitialized();
        }
    }
}
