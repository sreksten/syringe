package com.threeamigos.common.util.implementations.injection.scopes;

import com.threeamigos.common.util.implementations.injection.events.ObserverSupport;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.context.Conversation;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;

/**
 * Service provider interface for normal-scope support.
 *
 * <p>Discovered via {@link java.util.ServiceLoader}. If syringe-scopes.jar is on the classpath,
 * {@code ScopeSupportImpl} is loaded; otherwise {@link NoOpScopeSupport} is used.
 */
public interface ScopeSupport {

    void setMessageHandler(MessageHandler messageHandler);

    void setKnowledgeBase(KnowledgeBase knowledgeBase);

    void setBeanManager(BeanManager beanManager);

    void setObserverSupport(ObserverSupport observerSupport);

    void registerNormalScopes(ContextManager contextManager);

    <T> T createClientProxy(Bean<T> bean);

    void validateNormalScopeUsage();

    boolean activateRequestContextIfNeeded();

    void deactivateRequestContextIfActive();

    String activateSyntheticSessionContextIfNeeded();

    void deactivateSessionContextIfActive();

    void invalidateSessionContext(String sessionId);

    void activateRequestContext();

    void deactivateRequestContext();

    void activateSessionContext(String sessionId);

    void deactivateSessionContext();

    String getCurrentSessionId();

    void beginConversation(String conversationId);

    void endConversation();

    void endConversation(String conversationId);

    String getCurrentConversationId();

    Conversation createConversation();

    void registerContainer(ClassLoader classLoader, BeanManager beanManager, ContextManager contextManager);

    void unregisterContainer(ClassLoader classLoader, BeanManager beanManager, ContextManager contextManager);

    void clearGlobalState();

    /**
     * Clears any per-instance caches held by this support object.
     */
    void clear();
}
