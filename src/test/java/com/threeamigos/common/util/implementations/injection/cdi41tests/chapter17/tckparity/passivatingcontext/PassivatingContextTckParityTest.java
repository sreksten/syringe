package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter17.tckparity.passivatingcontext;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.inject.IllegalProductException;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Vetoed;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("17.5 - TCK parity for passivating contexts")
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
class PassivatingContextTckParityTest {

    @Test
    @DisplayName("17.5 / PassivatingContextTest - serializable managed bean in passivating scope is allowed")
    void shouldMatchPassivatingContextTestSerializableManagedBean() {
        Syringe syringe = newSyringe(SerializableSessionBean.class);
        assertDoesNotThrow(syringe::setup);
    }

    @Test
    @DisplayName("17.5 / PassivatingContextTest - state survives session context deactivation/activation cycle")
    void shouldMatchPassivatingContextTestPassivationOccurs() {
        Syringe syringe = newSyringe(StatefulSessionBean.class);
        syringe.setup();

        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        beanManager.getContextManager().activateSession("passivation-cycle");
        try {
            StatefulSessionBean bean = syringe.inject(StatefulSessionBean.class);
            bean.setNumber(100);
        } finally {
            beanManager.getContextManager().deactivateSession();
        }

        beanManager.getContextManager().activateSession("passivation-cycle");
        try {
            StatefulSessionBean restored = syringe.inject(StatefulSessionBean.class);
            assertEquals(100, restored.getNumber());
        } finally {
            beanManager.getContextManager().deactivateSession();
        }
    }

    @Test
    @DisplayName("17.5 / PassivatingContextTest - non-serializable producer product in passivating scope causes IllegalProductException at runtime")
    void shouldMatchPassivatingContextTestIllegalProductException() {
        Syringe syringe = newSyringe(RuntimeInvalidProducer.class, Television.class);
        syringe.setup();

        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        beanManager.getContextManager().activateSession("illegal-product-session");
        try {
            RuntimeException runtimeException = assertThrows(RuntimeException.class,
                    () -> syringe.inject(Television.class).turnOn());
            assertTrue(containsCause(runtimeException, IllegalProductException.class));
        } finally {
            beanManager.getContextManager().deactivateSession();
        }
    }

    @Test
    @DisplayName("17.5 / BuiltinBeanPassivationDependencyTest - built-in passivation-capable dependencies are valid in passivating scope")
    void shouldAllowBuiltInPassivationDependenciesInPassivatingScope() {
        Syringe syringe = newSyringe(BuiltinDependencyConsumer.class, Hammer.class);
        syringe.setup();

        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        beanManager.getContextManager().activateSession("builtin-passivation-deps");
        try {
            BuiltinDependencyConsumer consumer = getSessionScopedInstance(beanManager, BuiltinDependencyConsumer.class);
            assertNotNull(consumer.getInstance());
            assertNotNull(consumer.getBeanManager());
            assertNotNull(consumer.getInstance().get());
        } finally {
            beanManager.getContextManager().deactivateSession();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> T getSessionScopedInstance(BeanManagerImpl beanManager, Class<T> type) {
        Bean<T> bean = (Bean<T>) beanManager.resolve((Set) beanManager.getBeans(type));
        return beanManager.getContext(SessionScoped.class).get(bean, beanManager.createCreationalContext(bean));
    }

    private boolean containsCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Syringe newSyringe(Class<?>... classes) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), classes);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        return syringe;
    }

    @SessionScoped
    public static class SerializableSessionBean implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String id = UUID.randomUUID().toString();

        public String getId() {
            return id;
        }
    }

    @SessionScoped
    public static class StatefulSessionBean implements Serializable {
        private static final long serialVersionUID = 1L;
        private int number;

        public int getNumber() {
            return number;
        }

        public void setNumber(int number) {
            this.number = number;
        }
    }

    @Vetoed
    public static class NonSerializablePayload {
    }

    public static class RuntimeInvalidProducer {
        @jakarta.enterprise.inject.Produces
        public NonSerializablePayload producePayload() {
            return new NonSerializablePayload();
        }
    }

    @SessionScoped
    public static class Television implements Serializable {
        private static final long serialVersionUID = 1L;

        @Inject
        NonSerializablePayload payload;

        public void turnOn() {
            assertNotNull(payload);
        }
    }

    @SessionScoped
    public static class BuiltinDependencyConsumer implements Serializable {
        private static final long serialVersionUID = 1L;

        @Inject
        Instance<Hammer> instance;

        @Inject
        BeanManager beanManager;

        Instance<Hammer> getInstance() {
            return instance;
        }

        BeanManager getBeanManager() {
            return beanManager;
        }
    }

    @jakarta.enterprise.context.RequestScoped
    public static class Hammer {
    }
}
