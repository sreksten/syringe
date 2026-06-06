package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter20.par205additionaldecoratorrules;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.discovery.NonPortableBehaviourException;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("20.5 - Additional decorator rules")
@Execution(ExecutionMode.SAME_THREAD)
public class AdditionalDecoratorRulesTest {

    @Test
    @DisplayName("20.5.1 - Decorator with bean name is non-portable and throws NonPortableBehaviourException")
    void shouldRejectNamedDecoratorAsNonPortable() {
        Syringe syringe = newSyringe(BasicServiceBean.class, NamedBasicServiceDecorator.class);
        assertThrows(NonPortableBehaviourException.class, syringe::setup);
    }

    @Test
    @DisplayName("20.5.2 - Alternative decorator is non-portable and throws NonPortableBehaviourException")
    void shouldRejectAlternativeDecoratorAsNonPortable() {
        Syringe syringe = newSyringe(BasicServiceBean.class, AlternativeBasicServiceDecorator.class);
        assertThrows(NonPortableBehaviourException.class, syringe::setup);
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
                BasicService.class,
                BasicServiceBean.class,
                NamedBasicServiceDecorator.class,
                AlternativeBasicServiceDecorator.class
        );
    }

    public interface BasicService {
        String ping();
    }

    @Dependent
    public static class BasicServiceBean implements BasicService {
        @Override
        public String ping() {
            return "ok";
        }
    }

    @Decorator
    @Dependent
    @Named("namedDecorator")
    public static class NamedBasicServiceDecorator implements BasicService {
        @Inject
        @Delegate
        BasicService delegate;

        @Override
        public String ping() {
            return delegate.ping();
        }
    }

    @Decorator
    @Dependent
    @Alternative
    public static class AlternativeBasicServiceDecorator implements BasicService {
        @Inject
        @Delegate
        BasicService delegate;

        @Override
        public String ping() {
            return delegate.ping();
        }
    }
}
