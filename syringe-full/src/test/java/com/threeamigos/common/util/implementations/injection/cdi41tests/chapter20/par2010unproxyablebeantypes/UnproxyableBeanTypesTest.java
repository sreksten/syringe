package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter20.par2010unproxyablebeantypes;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("20.10 - Unproxyable bean types")
@Execution(ExecutionMode.SAME_THREAD)
public class UnproxyableBeanTypesTest {

    @Test
    @DisplayName("20.10 - Deployment problem when injection resolves to bean with associated decorator and unproxyable bean type")
    void shouldFailDeploymentForDecoratedUnproxyableBeanType() {
        Syringe syringe = newSyringe(
                DecoratedContract.class,
                FinalDecoratedBean.class,
                ContractDecorator.class,
                ContractConsumer.class
        );
        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("20.10 - Decorated bean with proxyable bean type is valid")
    void shouldAllowDecoratedProxyableBeanType() {
        Syringe syringe = newSyringe(
                DecoratedContract.class,
                ProxyableDecoratedBean.class,
                ContractDecorator.class,
                ContractConsumer.class
        );
        syringe.setup();

        ContractConsumer consumer = syringe.inject(ContractConsumer.class);
        assertEquals("decorated:ok", consumer.call());
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
                DecoratedContract.class,
                FinalDecoratedBean.class,
                ProxyableDecoratedBean.class,
                ContractDecorator.class,
                ContractConsumer.class
        );
    }

    public interface DecoratedContract {
        String ping();
    }

    @Dependent
    public static final class FinalDecoratedBean implements DecoratedContract {
        @Override
        public String ping() {
            return "ok";
        }
    }

    @Dependent
    public static class ProxyableDecoratedBean implements DecoratedContract {
        @Override
        public String ping() {
            return "ok";
        }
    }

    @Decorator
    @Dependent
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 10)
    public static class ContractDecorator implements DecoratedContract {
        @Inject
        @Delegate
        DecoratedContract delegate;

        @Override
        public String ping() {
            return "decorated:" + delegate.ping();
        }
    }

    @Dependent
    public static class ContractConsumer {
        @Inject
        DecoratedContract contract;

        String call() {
            return contract.ping();
        }
    }
}
