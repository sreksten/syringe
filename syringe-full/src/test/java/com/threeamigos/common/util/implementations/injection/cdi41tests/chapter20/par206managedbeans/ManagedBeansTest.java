package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter20.par206managedbeans;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("20.6 - Managed beans")
@Execution(ExecutionMode.SAME_THREAD)
public class ManagedBeansTest {

    @Test
    @DisplayName("20.6 - Bean class annotated with both @Interceptor and @Decorator is a definition error")
    void shouldFailWhenBeanClassHasBothInterceptorAndDecorator() {
        Syringe syringe = newSyringe(
                ServiceBean.class,
                InvalidInterceptorDecoratorBean.class
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
                InvalidInterceptorDecoratorBean.class
        );
    }

    public interface Service {
        String call();
    }

    @Dependent
    public static class ServiceBean implements Service {
        @Override
        public String call() {
            return "ok";
        }
    }

    @Dependent
    @Interceptor
    @Decorator
    public static class InvalidInterceptorDecoratorBean implements Service {
        @Inject
        @Delegate
        Service delegate;

        @AroundInvoke
        Object around(InvocationContext ctx) throws Exception {
            return ctx.proceed();
        }

        @Override
        public String call() {
            return delegate.call();
        }
    }
}
