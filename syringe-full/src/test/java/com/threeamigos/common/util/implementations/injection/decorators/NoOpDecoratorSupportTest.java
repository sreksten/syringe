package com.threeamigos.common.util.implementations.injection.decorators;

import com.threeamigos.common.util.implementations.injection.NotEnabledFeatureException;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.inject.spi.Decorator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NoOpDecoratorSupportTest {

    @Test
    void validateBeansXmlDecoratorConfigurationThrowsCanonicalMessageWhenDecoratorUsageIsDetected() {
        NoOpDecoratorSupport noOp = new NoOpDecoratorSupport();
        KnowledgeBase knowledgeBase = new KnowledgeBase(new InMemoryMessageHandler());
        knowledgeBase.addDecorator(TestDecorator.class);
        noOp.setKnowledgeBase(knowledgeBase);

        NotEnabledFeatureException ex = assertThrows(
                NotEnabledFeatureException.class,
                noOp::validateBeansXmlDecoratorConfiguration
        );

        assertTrue(ex.getMessage().startsWith(
                "@Decorator found at class " + TestDecorator.class.getName() +
                        " but decorator support is not available."));
    }

    @SuppressWarnings("unchecked")
    @Test
    void validateProgrammaticDecoratorConfigurationThrowsCanonicalMessageWhenDecoratorBeanIsRegistered() {
        NoOpDecoratorSupport noOp = new NoOpDecoratorSupport();
        KnowledgeBase knowledgeBase = new KnowledgeBase(new InMemoryMessageHandler());
        Decorator<Object> decoratorBean = (Decorator<Object>) mock(Decorator.class);
        when(decoratorBean.getBeanClass()).thenReturn((Class) TestDecorator.class);
        knowledgeBase.addBean(decoratorBean);
        noOp.setKnowledgeBase(knowledgeBase);

        NotEnabledFeatureException ex = assertThrows(
                NotEnabledFeatureException.class,
                noOp::validateProgrammaticDecoratorConfiguration
        );

        assertTrue(ex.getMessage().startsWith(
                "programmatic decorator found at class " + TestDecorator.class.getName() +
                        " but decorator support is not available."));
    }

    private static final class TestDecorator {
    }
}
