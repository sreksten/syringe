package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter17.par175passivation.tckparity;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.IllegalProductException;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Vetoed;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("17.5.5 - TCK parity: passivating producer validation")
class PassivatingProducerTckParityTest {

    @Test
    @DisplayName("17.5.5 (ca) - passivating scoped producer method with final non-serializable return type is deployment problem")
    void nonPassivationCapableProducerMethodIsDeploymentProblem() {
        InMemoryMessageHandler handler = new InMemoryMessageHandler();
        Syringe syringe = newSyringe(handler, InvalidPassivatingProducerMethodHolder.class);
        syringe.exclude(Corral.class, CowProducer.class, FieldInjectionCorralBroken.class,
                SetterInjectionCorralBroken.class, ConstructorInjectionCorralBroken.class);
        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("17.5.5 (fbb) - @SessionScoped bean requiring @Dependent producer-field dependency fails at runtime with IllegalProductException")
    void managedBeanWithIllegalDependentProducerFieldDependencyFailsAtRuntime() {
        InMemoryMessageHandler handler = new InMemoryMessageHandler();
        Syringe syringe = newSyringe(handler,
                CowProducer.class,
                FieldInjectionCorralBroken.class,
                SetterInjectionCorralBroken.class,
                ConstructorInjectionCorralBroken.class
        );
        syringe.exclude(InvalidPassivatingProducerMethodHolder.class);
        syringe.setup();

        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        beanManager.getContextManager().activateSession("17-5-5-managed-field-illegal-dependency");
        try {
            assertIllegalProductThrown(() -> getSessionBean(beanManager, FieldInjectionCorralBroken.class).ping());
            assertIllegalProductThrown(() -> getSessionBean(beanManager, SetterInjectionCorralBroken.class).ping());
            assertIllegalProductThrown(() -> getSessionBean(beanManager, ConstructorInjectionCorralBroken.class).ping());
        } finally {
            beanManager.getContextManager().deactivateSession();
        }
    }

    private void assertIllegalProductThrown(Runnable callback) {
        RuntimeException thrown = assertThrows(RuntimeException.class, callback::run);
        assertTrue(hasCause(thrown, IllegalProductException.class),
                "Expected IllegalProductException in cause chain but got: " + thrown);
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> expected) {
        Throwable current = throwable;
        while (current != null) {
            if (expected.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Syringe newSyringe(InMemoryMessageHandler handler, Class<?>... beanClasses) {
        Syringe syringe = new Syringe(handler, beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        return syringe;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> T getSessionBean(BeanManager beanManager, Class<T> beanType) {
        Set<Bean<?>> beans = beanManager.getBeans(beanType);
        Bean bean = beanManager.resolve((Set) beans);
        CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        return (T) beanManager.getContext(SessionScoped.class).get((Contextual) bean, creationalContext);
    }

    static final class FinalNonSerializableValue {
        String value() {
            return "x";
        }
    }

    static class InvalidPassivatingProducerMethodHolder implements Serializable {
        private static final long serialVersionUID = 1L;
        @Produces
        @SessionScoped
        FinalNonSerializableValue produceInvalid() {
            return new FinalNonSerializableValue();
        }
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
    @interface British {
    }

    static class Cow {
    }

    @Dependent
    static class CowProducer {
        @Produces
        @British
        Cow cow = new Cow();
    }

    @Vetoed
    abstract static class Corral implements Serializable {
        abstract void ping();
    }

    @SessionScoped
    static class FieldInjectionCorralBroken extends Corral {
        @Inject
        @British
        Cow cow;

        @Override
        void ping() {
        }
    }

    @SessionScoped
    static class SetterInjectionCorralBroken extends Corral {
        Cow cow;

        @Inject
        void setCow(@British Cow cow) {
            this.cow = cow;
        }

        @Override
        void ping() {
        }
    }

    @SessionScoped
    static class ConstructorInjectionCorralBroken extends Corral {
        Cow cow;

        ConstructorInjectionCorralBroken() {
        }

        @Inject
        ConstructorInjectionCorralBroken(@British Cow cow) {
            this.cow = cow;
        }

        @Override
        void ping() {
        }
    }
}
