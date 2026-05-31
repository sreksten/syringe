package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.tckparity.unproxyable;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.UnproxyableResolutionException;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.DeploymentException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("3.10 - TCK parity for UnproxyableManagedBeanTest")
class UnproxyableManagedBeanTckParityTest {

    @Test
    @DisplayName("UnproxyableManagedBeanTest - unproxyable private-constructor bean throws UnproxyableResolutionException when obtaining reference")
    void privateConstructorBeanFailsOnContextualReference() {
        assertUnproxyableOnGetReference(UnproxyablePrivateConstructorBean.class);
    }

    @Test
    @DisplayName("UnproxyableManagedBeanTest - unproxyable final class bean throws UnproxyableResolutionException when obtaining reference")
    void finalClassBeanFailsOnContextualReference() {
        assertUnproxyableOnGetReference(UnproxyableFinalClassBean.class);
    }

    @Test
    @DisplayName("UnproxyableManagedBeanTest - unproxyable final method bean throws UnproxyableResolutionException when obtaining reference")
    void finalMethodBeanFailsOnContextualReference() {
        assertUnproxyableOnGetReference(UnproxyableFinalMethodBean.class);
    }

    private <T> void assertUnproxyableOnGetReference(Class<T> beanClass) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClass);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        try {
            syringe.setup();
        } catch (DeploymentException deploymentException) {
            throw deploymentException;
        }

        @SuppressWarnings("unchecked")
        Bean<T> bean = (Bean<T>) syringe.getBeanManager().resolve(syringe.getBeanManager().getBeans(beanClass));
        CreationalContext<T> context = syringe.getBeanManager().createCreationalContext(bean);

        assertThrows(UnproxyableResolutionException.class,
                () -> syringe.getBeanManager().getReference(bean, beanClass, context));
    }

    @ApplicationScoped
    public static class UnproxyablePrivateConstructorBean {
        private UnproxyablePrivateConstructorBean() {
        }
    }

    @ApplicationScoped
    public static final class UnproxyableFinalClassBean {
    }

    @ApplicationScoped
    public static class UnproxyableFinalMethodBean {
        public final String ping() {
            return "pong";
        }
    }
}
