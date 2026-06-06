package com.threeamigos.common.util.implementations.injection.interceptors;

import com.threeamigos.common.util.implementations.injection.NotEnabledFeatureException;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXml;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;

/**
 * No-op {@link InterceptorSupport} used when syringe-interceptors is not on the classpath.
 *
 * <p>Returns {@code null} for the resolver and proxy generator (safe because BeanImpl handles
 * {@code null} gracefully when no interceptors are registered). Throws
 * {@link NotEnabledFeatureException} during validation if interceptors are actually required.
 */
public class NoOpInterceptorSupport implements InterceptorSupport {

    private static final String FEATURE_UNAVAILABLE =
            "Interceptor support is not available. Add syringe-interceptors to your classpath.";

    private KnowledgeBase knowledgeBase;

    @Override
    public void setKnowledgeBase(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    @Override
    public void setMessageHandler(MessageHandler messageHandler) {
        // not needed
    }

    @Override
    public InterceptorResolver getResolver() {
        return null;
    }

    @Override
    public InterceptorAwareProxyGenerator getProxyGenerator() {
        return null;
    }

    @Override
    public void validateBeansXmlInterceptorConfiguration() {
        if (!knowledgeBase.getInterceptors().isEmpty() || !knowledgeBase.getInterceptorInfos().isEmpty()) {
            throw new NotEnabledFeatureException(FEATURE_UNAVAILABLE);
        }
        for (BeansXml beansXml : knowledgeBase.getBeansXmlConfigurations()) {
            if (beansXml == null) {
                continue;
            }
            com.threeamigos.common.util.implementations.injection.beansxml.Interceptors interceptors =
                    beansXml.getInterceptors();
            if (interceptors != null && interceptors.getClasses() != null
                    && !interceptors.getClasses().isEmpty()) {
                throw new NotEnabledFeatureException(FEATURE_UNAVAILABLE);
            }
        }
    }

    @Override
    public void clear() {
        // no-op
    }
}
