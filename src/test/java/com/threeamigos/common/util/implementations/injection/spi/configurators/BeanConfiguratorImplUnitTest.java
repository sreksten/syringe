package com.threeamigos.common.util.implementations.injection.spi.configurators;

import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.inject.spi.Bean;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BeanConfiguratorImplUnitTest {

    @Test
    void completeInfersBeanClassFromConfiguredTypes() {
        MessageHandler messageHandler = new InMemoryMessageHandler();
        KnowledgeBase knowledgeBase = new KnowledgeBase(messageHandler);

        BeanConfiguratorImpl<Object> configurator = new BeanConfiguratorImpl<>(messageHandler, knowledgeBase);
        configurator.types(ServiceType.class, Object.class)
                .createWith(ctx -> new Object());

        configurator.complete();

        Collection<Bean<?>> beans = knowledgeBase.getBeans();
        assertFalse(beans.isEmpty());
        Bean<?> bean = beans.iterator().next();
        assertEquals(ServiceType.class, bean.getBeanClass());
    }

    @Test
    void completeFallsBackToObjectBeanClassWhenNoTypesConfigured() {
        MessageHandler messageHandler = new InMemoryMessageHandler();
        KnowledgeBase knowledgeBase = new KnowledgeBase(messageHandler);

        BeanConfiguratorImpl<Object> configurator = new BeanConfiguratorImpl<>(messageHandler, knowledgeBase);
        configurator.createWith(ctx -> new Object());

        configurator.complete();

        Bean<?> bean = knowledgeBase.getBeans().iterator().next();
        assertEquals(Object.class, bean.getBeanClass());
    }

    private interface ServiceType {
    }
}
