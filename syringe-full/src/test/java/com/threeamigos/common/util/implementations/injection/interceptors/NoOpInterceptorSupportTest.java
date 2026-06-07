package com.threeamigos.common.util.implementations.injection.interceptors;

import com.threeamigos.common.util.implementations.injection.NotEnabledFeatureException;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NoOpInterceptorSupportTest {

    @Test
    void validateThrowsCanonicalMessageWhenInterceptorUsageIsDetected() {
        NoOpInterceptorSupport noOp = new NoOpInterceptorSupport();
        KnowledgeBase knowledgeBase = new KnowledgeBase(new InMemoryMessageHandler());
        knowledgeBase.addInterceptor(TestInterceptor.class);
        noOp.setKnowledgeBase(knowledgeBase);

        NotEnabledFeatureException ex = assertThrows(
                NotEnabledFeatureException.class,
                noOp::validateBeansXmlInterceptorConfiguration
        );

        assertTrue(ex.getMessage().startsWith(
                "@Interceptor found at class " + TestInterceptor.class.getName() +
                        " but interceptor support is not available."));
    }

    @Test
    void createInterceptionFactoryThrowsCanonicalApiMessage() {
        NoOpInterceptorSupport noOp = new NoOpInterceptorSupport();

        NotEnabledFeatureException ex = assertThrows(
                NotEnabledFeatureException.class,
                () -> noOp.createInterceptionFactory(null, TestInterceptor.class, null)
        );

        assertTrue(ex.getMessage().startsWith(
                "API call found at BeanManager.createInterceptionFactory(CreationalContext, Class) but interceptor support is not available."));
    }

    private static class TestInterceptor {
    }
}
