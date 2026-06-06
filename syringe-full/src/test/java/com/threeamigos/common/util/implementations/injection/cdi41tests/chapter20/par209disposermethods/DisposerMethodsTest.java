package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter20.par209disposermethods;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("20.9 - Disposer methods")
@Execution(ExecutionMode.SAME_THREAD)
public class DisposerMethodsTest {

    @Test
    @DisplayName("20.9 - Decorator may not declare disposer methods")
    void shouldRejectDisposerMethodDeclaredByDecorator() {
        Syringe syringe = newSyringe(
                ServiceBean.class,
                ValidProducerHolder.class,
                DecoratorWithDisposerMethod.class
        );
        assertThrows(DefinitionException.class, syringe::setup);
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        Set<Class<?>> included = new HashSet<Class<?>>(Arrays.asList(beanClasses));
        for (Class<?> fixture : allFixtureTypes()) {
            if (!included.contains(fixture)) {
                syringe.exclude(fixture);
            }
        }
        return syringe;
    }

    private Collection<Class<?>> allFixtureTypes() {
        return Arrays.<Class<?>>asList(
                Service.class,
                ServiceBean.class,
                ProducedValue.class,
                ValidProducerHolder.class,
                DecoratorWithDisposerMethod.class
        );
    }

    public interface Service {
        String ping();
    }

    @Dependent
    public static class ServiceBean implements Service {
        @Override
        public String ping() {
            return "ok";
        }
    }

    public static class ProducedValue {
    }

    @Dependent
    public static class ValidProducerHolder {
        @Produces
        @Dependent
        ProducedValue produce() {
            return new ProducedValue();
        }
    }

    @Decorator
    @Dependent
    public static class DecoratorWithDisposerMethod implements Service {
        @Inject
        @Delegate
        Service delegate;

        void disposeInvalid(@Disposes ProducedValue value) {
            // no-op
        }

        @Override
        public String ping() {
            return delegate.ping();
        }
    }
}
