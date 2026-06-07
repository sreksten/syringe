package com.threeamigos.common.util.implementations.injection.scopes;

import com.threeamigos.common.util.implementations.injection.NotEnabledFeatureException;
import com.threeamigos.common.util.implementations.injection.events.ObserverSupport;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.modules.ModulesEnum;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Conversation;
import jakarta.enterprise.context.ConversationScoped;
import jakarta.enterprise.context.NormalScope;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;

import java.lang.annotation.Annotation;

/**
 * No-op {@link ScopeSupport} used when syringe-scopes is absent from the classpath.
 */
public class NoOpScopeSupport implements ScopeSupport {

    private KnowledgeBase knowledgeBase;

    @Override
    public void setMessageHandler(MessageHandler messageHandler) {
        // not needed
    }

    @Override
    public void setKnowledgeBase(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    @Override
    public void setBeanManager(BeanManager beanManager) {
        // not needed
    }

    @Override
    public void setObserverSupport(ObserverSupport observerSupport) {
        // not needed
    }

    @Override
    public void registerNormalScopes(ContextManager contextManager) {
        // no-op
    }

    @Override
    public <T> T createClientProxy(Bean<T> bean) {
        return null;
    }

    @Override
    public void validateNormalScopeUsage() {
        if (knowledgeBase == null) {
            return;
        }
        for (Bean<?> bean : knowledgeBase.getBeans()) {
            if (bean == null) {
                continue;
            }
            Class<?> beanClass = bean.getBeanClass();
            if (Conversation.class.equals(beanClass)) {
                // Built-in conversation bean is always present in core; skip it here.
                continue;
            }
            Class<? extends Annotation> scope = bean.getScope();
            if (scope == null) {
                continue;
            }
            if (!isNormalScope(scope)) {
                continue;
            }

            String scopeName = "@" + scope.getSimpleName();
            String location = beanClass != null ? beanClass.getName() : "unknown type";
            throw new NotEnabledFeatureException(
                    scopeName + " found on class " + location + " but scope support is not available.",
                    ModulesEnum.SCOPES);
        }
    }

    @Override
    public boolean activateRequestContextIfNeeded() {
        return false;
    }

    @Override
    public void deactivateRequestContextIfActive() {
        // no-op
    }

    @Override
    public String activateSyntheticSessionContextIfNeeded() {
        return null;
    }

    @Override
    public void deactivateSessionContextIfActive() {
        // no-op
    }

    @Override
    public void invalidateSessionContext(String sessionId) {
        // no-op
    }

    @Override
    public void activateRequestContext() {
        // no-op
    }

    @Override
    public void deactivateRequestContext() {
        // no-op
    }

    @Override
    public void activateSessionContext(String sessionId) {
        // no-op
    }

    @Override
    public void deactivateSessionContext() {
        // no-op
    }

    @Override
    public String getCurrentSessionId() {
        return null;
    }

    @Override
    public void beginConversation(String conversationId) {
        // no-op
    }

    @Override
    public void endConversation() {
        // no-op
    }

    @Override
    public void endConversation(String conversationId) {
        // no-op
    }

    @Override
    public String getCurrentConversationId() {
        return null;
    }

    @Override
    public Conversation createConversation() {
        throw new NotEnabledFeatureException(
                "Conversation API requested but scope support is not available.",
                ModulesEnum.SCOPES);
    }

    @Override
    public void registerContainer(ClassLoader classLoader, BeanManager beanManager, ContextManager contextManager) {
        // no-op
    }

    @Override
    public void unregisterContainer(ClassLoader classLoader, BeanManager beanManager, ContextManager contextManager) {
        // no-op
    }

    @Override
    public void clearGlobalState() {
        // no-op
    }

    @Override
    public void clear() {
        // no-op
    }

    private boolean isNormalScope(Class<? extends Annotation> scopeType) {
        if (ApplicationScoped.class.equals(scopeType)
                || RequestScoped.class.equals(scopeType)
                || SessionScoped.class.equals(scopeType)
                || ConversationScoped.class.equals(scopeType)) {
            return true;
        }
        return scopeType.isAnnotationPresent(NormalScope.class);
    }
}
