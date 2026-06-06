package com.threeamigos.common.util.implementations.injection.interceptors;

import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;

/**
 * Service provider interface for interceptor support.
 *
 * <p>Discovered via {@link java.util.ServiceLoader}. If syringe-interceptors is on the classpath,
 * {@code InterceptorSupportImpl} is loaded; otherwise {@link NoOpInterceptorSupport} is used.
 */
public interface InterceptorSupport {

    void setKnowledgeBase(KnowledgeBase knowledgeBase);

    void setMessageHandler(MessageHandler messageHandler);

    /**
     * Returns the {@link InterceptorResolver} for this container instance, or {@code null} if
     * interceptor support is not available.
     */
    InterceptorResolver getResolver();

    /**
     * Returns the {@link InterceptorAwareProxyGenerator} for this container instance, or
     * {@code null} if interceptor support is not available.
     */
    InterceptorAwareProxyGenerator getProxyGenerator();

    /**
     * Validates beans.xml {@code <interceptors>} entries against the discovered beans.
     * Records any errors into the KnowledgeBase via {@code addDefinitionError}.
     */
    void validateBeansXmlInterceptorConfiguration();

    /**
     * Clears any per-instance caches held by this support object (e.g. proxy class cache).
     */
    void clear();
}
