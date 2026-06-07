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
 *
 * <p>Core always provides {@code @Dependent}. Built-in normal scopes are registered
 * by {@link ScopeSupport} (syringe-scopes module) when available.
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
    private volatile boolean destroyed = false;
    private volatile RequestContextLifecycleListener requestContextLifecycleListener;
    private volatile ApplicationContextLifecycleListener applicationContextLifecycleListener;
    private volatile ScopeSupport scopeSupport = new NoOpScopeSupport();

    public ContextManager(MessageHandler messageHandler) {
        this.messageHandler = messageHandler;
        ScopeContext dependentContext = new DependentContext();
        contexts.put(Dependent.class, dependentContext);
        contextsByScope.put(Dependent.class,
                new CopyOnWriteArrayList<>(Collections.singletonList(dependentContext)));
    }

    public void setScopeSupport(ScopeSupport scopeSupport) {
        this.scopeSupport = scopeSupport != null ? scopeSupport : new NoOpScopeSupport();
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

        destroyApplicationContexts(destroyedContexts);

        for (Map.Entry<Class<? extends Annotation>, List<ScopeContext>> entry : contextsByScope.entrySet()) {
            if (APPLICATION_SCOPED.matches(entry.getKey())) {
                continue;
            }
            List<ScopeContext> registered = entry.getValue();
            if (registered == null) {
                continue;
            }
            for (ScopeContext scopeContext : registered) {
                destroyContextIfNeeded(scopeContext, destroyedContexts);
            }
        }

        try {
            scopeSupport.deactivateSessionContext();
        } catch (Exception ignored) {
            // best-effort shutdown cleanup
        }
        try {
            scopeSupport.deactivateRequestContext();
        } catch (Exception ignored) {
            // best-effort shutdown cleanup
        }

        contexts.clear();
        contextsByScope.clear();
        requestContextLifecycleListener = null;
        applicationContextLifecycleListener = null;
        destroyed = true;
    }

    private void destroyApplicationContexts(Map<ScopeContext, Boolean> destroyedContexts) {
        List<ScopeContext> applicationContexts = contextsByScope.get(ApplicationScoped.class);
        if (applicationContexts == null || applicationContexts.isEmpty()) {
            return;
        }
        ApplicationContextLifecycleListener listener = applicationContextLifecycleListener;
        for (ScopeContext scopeContext : applicationContexts) {
            if (scopeContext == null || destroyedContexts.containsKey(scopeContext)) {
                continue;
            }
            try {
                if (listener != null) {
                    listener.onBeforeDestroyed();
                }
                if (listener != null) {
                    listener.onDestroyed();
                }
                scopeContext.destroy();
            } catch (Exception e) {
                messageHandler.error("Error destroying context: " + e.getMessage());
            } finally {
                destroyedContexts.put(scopeContext, Boolean.TRUE);
            }
        }
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

    public void beginConversation(String conversationId) {
        scopeSupport.beginConversation(conversationId);
    }

    public void endConversation() {
        scopeSupport.endConversation();
    }

    public void endConversation(String conversationId) {
        scopeSupport.endConversation(conversationId);
    }

    public String getCurrentConversationId() {
        return scopeSupport.getCurrentConversationId();
    }

    // === Session Scope Management ===

    public void activateSession(String sessionId) {
        scopeSupport.activateSessionContext(sessionId);
    }

    public void deactivateSession() {
        scopeSupport.deactivateSessionContext();
    }

    public void invalidateSession(String sessionId) {
        scopeSupport.invalidateSessionContext(sessionId);
    }

    public String getCurrentSessionId() {
        return scopeSupport.getCurrentSessionId();
    }

    // === Request Scope Management ===

    public void activateRequest() {
        ScopeContext requestContext = contexts.get(RequestScoped.class);
        boolean wasActive = requestContext != null && requestContext.isActive();
        scopeSupport.activateRequestContext();
        if (!wasActive && requestContext != null && requestContext.isActive()) {
            RequestContextLifecycleListener listener = requestContextLifecycleListener;
            if (listener != null) {
                listener.onInitialized();
            }
        }
    }

    public void deactivateRequest() {
        ScopeContext requestContext = contexts.get(RequestScoped.class);
        boolean wasActive = requestContext != null && requestContext.isActive();
        RequestContextLifecycleListener listener = requestContextLifecycleListener;
        if (wasActive && listener != null) {
            listener.onBeforeDestroyed();
        }
        scopeSupport.deactivateRequestContext();
        if (wasActive && listener != null) {
            listener.onDestroyed();
        }
    }

    // === Proxy Management ===

    public boolean isNormalScope(Class<? extends Annotation> scopeAnnotation) {
        if (scopeAnnotation == null || DEPENDENT.matches(scopeAnnotation)) {
            return false;
        }
        return contexts.containsKey(scopeAnnotation);
    }

    public <T> T createClientProxy(Bean<T> bean) {
        try {
            return scopeSupport.createClientProxy(bean);
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

            CopyOnWriteArrayList<ScopeContext> replacement = new CopyOnWriteArrayList<>();
            replacement.add(context);
            contextsByScope.put(scopeAnnotation, replacement);
            contexts.put(scopeAnnotation, context);
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
