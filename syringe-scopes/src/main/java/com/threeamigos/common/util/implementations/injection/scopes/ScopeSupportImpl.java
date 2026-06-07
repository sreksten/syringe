package com.threeamigos.common.util.implementations.injection.scopes;

import com.threeamigos.common.util.implementations.injection.events.NoOpObserverSupport;
import com.threeamigos.common.util.implementations.injection.events.ObserverSupport;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Conversation;
import jakarta.enterprise.context.ConversationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.inject.UnproxyableResolutionException;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;

import java.util.UUID;

/**
 * Full {@link ScopeSupport} implementation, active when syringe-scopes is on the classpath.
 */
public class ScopeSupportImpl implements ScopeSupport {

    private MessageHandler messageHandler;
    @SuppressWarnings("unused")
    private KnowledgeBase knowledgeBase;
    private BeanManager beanManager;
    private ObserverSupport observerSupport = new NoOpObserverSupport();

    private ContextManager contextManager;
    private ApplicationScopedContext applicationScopedContext;
    private RequestScopedContext requestScopedContext;
    private SessionScopedContext sessionScopedContext;
    private ConversationScopedContext conversationScopedContext;
    private ClientProxyGenerator clientProxyGenerator;

    @Override
    public void setMessageHandler(MessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }

    @Override
    public void setKnowledgeBase(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    @Override
    public void setBeanManager(BeanManager beanManager) {
        this.beanManager = beanManager;
    }

    @Override
    public void setObserverSupport(ObserverSupport observerSupport) {
        this.observerSupport = observerSupport != null ? observerSupport : new NoOpObserverSupport();
    }

    @Override
    public void registerNormalScopes(ContextManager contextManager) {
        this.contextManager = contextManager;
        ensureContextsCreated();

        ConversationImpl.setConversationContext(conversationScopedContext);
        contextManager.registerContext(ApplicationScoped.class, applicationScopedContext);
        contextManager.registerContext(RequestScoped.class, requestScopedContext);
        contextManager.registerContext(SessionScoped.class, sessionScopedContext);
        contextManager.registerContext(ConversationScoped.class, conversationScopedContext);

        contextManager.setRequestContextLifecycleListener(new ContextManager.RequestContextLifecycleListener() {
            @Override
            public void onInitialized() {
                observerSupport.fireContextInitializedEvent(RequestScoped.class);
            }

            @Override
            public void onBeforeDestroyed() {
                observerSupport.fireContextBeforeDestroyedEvent(RequestScoped.class);
            }

            @Override
            public void onDestroyed() {
                observerSupport.fireContextDestroyedEvent(RequestScoped.class);
            }
        });

        contextManager.setApplicationContextLifecycleListener(new ContextManager.ApplicationContextLifecycleListener() {
            @Override
            public void onInitialized() {
                observerSupport.fireContextInitializedEvent(ApplicationScoped.class);
            }

            @Override
            public void onBeforeDestroyed() {
                observerSupport.fireContextBeforeDestroyedEvent(ApplicationScoped.class);
            }

            @Override
            public void onDestroyed() {
                observerSupport.fireContextDestroyedEvent(ApplicationScoped.class);
            }
        });
    }

    private void ensureContextsCreated() {
        if (applicationScopedContext == null) {
            applicationScopedContext = new ApplicationScopedContext();
        }
        if (requestScopedContext == null) {
            requestScopedContext = new RequestScopedContext();
        }
        if (sessionScopedContext == null) {
            sessionScopedContext = new SessionScopedContext(requireMessageHandler());
        }
        if (conversationScopedContext == null) {
            conversationScopedContext = new ConversationScopedContext(requireMessageHandler());
        }
        if (contextManager != null) {
            clientProxyGenerator = new ClientProxyGenerator(contextManager);
        }
    }

    private MessageHandler requireMessageHandler() {
        if (messageHandler == null) {
            throw new IllegalStateException("messageHandler is not configured");
        }
        return messageHandler;
    }

    @Override
    public <T> T createClientProxy(Bean<T> bean) {
        if (clientProxyGenerator == null || bean == null) {
            return null;
        }
        try {
            return clientProxyGenerator.createProxy(bean);
        } catch (UnproxyableResolutionException e) {
            throw e;
        } catch (RuntimeException | Error e) {
            throw new UnproxyableResolutionException(
                    "Cannot create client proxy for bean " + bean.getBeanClass().getName(), e);
        }
    }

    @Override
    public void validateNormalScopeUsage() {
        // normal scopes are available and registered
    }

    @Override
    public boolean activateRequestContextIfNeeded() {
        if (requestScopedContext == null) {
            return false;
        }
        if (requestScopedContext.isActive()) {
            return false;
        }
        activateRequestContext();
        return true;
    }

    @Override
    public void deactivateRequestContextIfActive() {
        if (requestScopedContext == null || !requestScopedContext.isActive()) {
            return;
        }
        deactivateRequestContext();
    }

    @Override
    public String activateSyntheticSessionContextIfNeeded() {
        String currentSessionId = getCurrentSessionId();
        if (currentSessionId != null) {
            return null;
        }
        String syntheticSessionId = "syringe-auto-session-" + UUID.randomUUID();
        activateSessionContext(syntheticSessionId);
        return syntheticSessionId;
    }

    @Override
    public void deactivateSessionContextIfActive() {
        if (getCurrentSessionId() == null) {
            return;
        }
        deactivateSessionContext();
    }

    @Override
    public void invalidateSessionContext(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return;
        }
        if (sessionScopedContext != null) {
            sessionScopedContext.invalidateSession(sessionId);
        }
    }

    @Override
    public void activateRequestContext() {
        if (requestScopedContext == null) {
            return;
        }
        ConversationImpl.setConversationContext(conversationScopedContext);
        requestScopedContext.activateRequest();
        if (conversationScopedContext == null) {
            return;
        }
        if (conversationScopedContext.getCurrentConversationId() == null) {
            String transientConversationId = ConversationImpl.getCurrentConversationStateId();
            if (transientConversationId != null && !transientConversationId.trim().isEmpty()) {
                conversationScopedContext.beginConversation(transientConversationId);
            }
        }
    }

    @Override
    public void deactivateRequestContext() {
        if (requestScopedContext == null) {
            return;
        }
        requestScopedContext.deactivateRequest();
        if (conversationScopedContext != null) {
            String currentConversationId = conversationScopedContext.getCurrentConversationId();
            if (currentConversationId != null && ConversationImpl.isCurrentConversationTransient()) {
                conversationScopedContext.endConversation(currentConversationId);
            }
            conversationScopedContext.clearCurrentThread();
        }
        ConversationImpl.clearCurrentConversation();
    }

    @Override
    public void activateSessionContext(String sessionId) {
        if (sessionScopedContext != null) {
            sessionScopedContext.activateSession(sessionId);
        }
    }

    @Override
    public void deactivateSessionContext() {
        if (sessionScopedContext != null) {
            sessionScopedContext.deactivateSession();
        }
    }

    @Override
    public String getCurrentSessionId() {
        return sessionScopedContext != null ? sessionScopedContext.getCurrentSessionId() : null;
    }

    @Override
    public void beginConversation(String conversationId) {
        if (conversationScopedContext == null) {
            return;
        }
        ConversationImpl.setConversationContext(conversationScopedContext);
        conversationScopedContext.beginConversation(conversationId);
    }

    @Override
    public void endConversation() {
        if (conversationScopedContext == null) {
            return;
        }
        ConversationImpl.setConversationContext(conversationScopedContext);
        conversationScopedContext.endConversation();
    }

    @Override
    public void endConversation(String conversationId) {
        if (conversationScopedContext == null) {
            return;
        }
        ConversationImpl.setConversationContext(conversationScopedContext);
        conversationScopedContext.endConversation(conversationId);
    }

    @Override
    public String getCurrentConversationId() {
        return conversationScopedContext != null ? conversationScopedContext.getCurrentConversationId() : null;
    }

    @Override
    public Conversation createConversation() {
        ConversationImpl.setConversationContext(conversationScopedContext);
        return new ConversationImpl();
    }

    @Override
    public void registerContainer(ClassLoader classLoader, BeanManager beanManager, ContextManager contextManager) {
        ClientProxyGenerator.registerContainer(classLoader, beanManager, contextManager);
    }

    @Override
    public void unregisterContainer(ClassLoader classLoader, BeanManager beanManager, ContextManager contextManager) {
        ClientProxyGenerator.unregisterContainer(classLoader, beanManager, contextManager);
    }

    @Override
    public void clearGlobalState() {
        ConversationImpl.clearAllGlobalState();
    }

    @Override
    public void clear() {
        if (clientProxyGenerator != null) {
            clientProxyGenerator.clearCache();
        }
    }
}
