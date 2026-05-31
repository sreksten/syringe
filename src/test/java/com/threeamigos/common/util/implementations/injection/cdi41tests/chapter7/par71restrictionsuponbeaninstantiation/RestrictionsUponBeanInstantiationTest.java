package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter7.par71restrictionsuponbeaninstantiation;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("7.1 - Restrictions upon bean instantiation")
@Execution(ExecutionMode.SAME_THREAD)
public class RestrictionsUponBeanInstantiationTest {

    @Test
    @DisplayName("7.1 - Direct application instantiation is not contextual and does not receive container services")
    void shouldNotTreatDirectInstantiationAsContextualInstance() {
        Syringe syringe = newSyringe(DirectlyInstantiableBean.class, SupportingDependency.class);

        DirectlyInstantiableBean direct = new DirectlyInstantiableBean();
        assertNull(direct.dependency);
        assertFalse(direct.postConstructCalled);

        DirectlyInstantiableBean managed = syringe.inject(DirectlyInstantiableBean.class);
        assertNotNull(managed.dependency);
        assertTrue(managed.postConstructCalled);
    }

    @Test
    @DisplayName("7.1 - Context object manages lifecycle by creating and destroying contextual instances via Contextual operations")
    void shouldManageContextualLifecycleViaContextObject() {
        RequestContextLifecycleBean.reset();
        Syringe syringe = newSyringe(RequestContextLifecycleBean.class);
        BeanManager beanManager = syringe.getBeanManager();
        BeanManagerImpl beanManagerImpl = (BeanManagerImpl) beanManager;
        Context requestContext = beanManager.getContext(RequestScoped.class);
        Bean<RequestContextLifecycleBean> bean = resolveBean(beanManager, RequestContextLifecycleBean.class);

        beanManagerImpl.getContextManager().activateRequest();
        try {
            RequestContextLifecycleBean created = requestContext.get(bean, beanManager.createCreationalContext(bean));
            RequestContextLifecycleBean existing = requestContext.get(bean);
            assertNotNull(created);
            assertSame(created, existing);
            assertEquals(1, RequestContextLifecycleBean.postConstructCount.get());
        } finally {
            beanManagerImpl.getContextManager().deactivateRequest();
        }

        assertEquals(1, RequestContextLifecycleBean.preDestroyCount.get());
    }

    @Test
    @DisplayName("7.1 - Producer returning other bean references yields contextual instances with container capabilities")
    void shouldProvideContainerCapabilitiesWhenProducerReturnsContextualReferences() {
        ContextualStrategyProducer.mode = PaymentStrategyType.CREDIT_CARD;
        Syringe syringe = newSyringe(
                SupportingDependency.class,
                CreditCardStrategy.class,
                ChequeStrategy.class,
                OnlineStrategy.class,
                ContextualStrategyProducer.class,
                ContextualStrategyConsumer.class
        );

        ContextualStrategyConsumer consumer = syringe.inject(ContextualStrategyConsumer.class);
        PaymentStrategy strategy = consumer.strategy;

        assertEquals("credit-card", strategy.kind());
        assertTrue(strategy.hasInjectedDependency());
        assertTrue(strategy.postConstructCalled());
    }

    @Test
    @DisplayName("7.1 - Producer returning new object yields non-contextual instance without injection/lifecycle callbacks")
    void shouldNotApplyContainerCapabilitiesToPlainProducedObject() {
        NonContextualStrategyProducer.mode = PaymentStrategyType.CREDIT_CARD;
        Syringe syringe = newSyringe(
                SupportingDependency.class,
                NonContextualStrategyProducer.class,
                NonContextualStrategyConsumer.class
        );

        NonContextualStrategyConsumer consumer = syringe.inject(NonContextualStrategyConsumer.class);
        PaymentStrategy strategy = consumer.strategy;

        assertEquals("plain-credit-card", strategy.kind());
        assertFalse(strategy.hasInjectedDependency());
        assertFalse(strategy.postConstructCalled());
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();
        return syringe;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> Bean<T> resolveBean(BeanManager beanManager, Class<T> beanType) {
        Set<Bean<?>> beans = beanManager.getBeans(beanType);
        return (Bean<T>) beanManager.resolve((Set) beans);
    }

    public enum PaymentStrategyType {
        CREDIT_CARD,
        CHEQUE,
        ONLINE
    }

    @Dependent
    public static class SupportingDependency {
    }

    @Dependent
    public static class DirectlyInstantiableBean {
        @Inject
        SupportingDependency dependency;

        boolean postConstructCalled;

        @PostConstruct
        void postConstruct() {
            postConstructCalled = true;
        }
    }

    @RequestScoped
    public static class RequestContextLifecycleBean {
        static final AtomicInteger postConstructCount = new AtomicInteger(0);
        static final AtomicInteger preDestroyCount = new AtomicInteger(0);

        static void reset() {
            postConstructCount.set(0);
            preDestroyCount.set(0);
        }

        @PostConstruct
        void postConstruct() {
            postConstructCount.incrementAndGet();
        }

        @PreDestroy
        void preDestroy() {
            preDestroyCount.incrementAndGet();
        }
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    public @interface CreditCard {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    public @interface Cheque {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    public @interface Online {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    public @interface FromContextualProducer {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    public @interface FromNonContextualProducer {
    }

    public interface PaymentStrategy {
        String kind();
        boolean hasInjectedDependency();
        boolean postConstructCalled();
    }

    @Dependent
    @CreditCard
    public static class CreditCardStrategy implements PaymentStrategy {
        @Inject
        SupportingDependency dependency;
        boolean postConstructCalled;

        @PostConstruct
        void postConstruct() {
            postConstructCalled = true;
        }

        @Override
        public String kind() {
            return "credit-card";
        }

        @Override
        public boolean hasInjectedDependency() {
            return dependency != null;
        }

        @Override
        public boolean postConstructCalled() {
            return postConstructCalled;
        }
    }

    @Dependent
    @Cheque
    public static class ChequeStrategy implements PaymentStrategy {
        @Inject
        SupportingDependency dependency;
        boolean postConstructCalled;

        @PostConstruct
        void postConstruct() {
            postConstructCalled = true;
        }

        @Override
        public String kind() {
            return "cheque";
        }

        @Override
        public boolean hasInjectedDependency() {
            return dependency != null;
        }

        @Override
        public boolean postConstructCalled() {
            return postConstructCalled;
        }
    }

    @Dependent
    @Online
    public static class OnlineStrategy implements PaymentStrategy {
        @Inject
        SupportingDependency dependency;
        boolean postConstructCalled;

        @PostConstruct
        void postConstruct() {
            postConstructCalled = true;
        }

        @Override
        public String kind() {
            return "online";
        }

        @Override
        public boolean hasInjectedDependency() {
            return dependency != null;
        }

        @Override
        public boolean postConstructCalled() {
            return postConstructCalled;
        }
    }

    @Dependent
    public static class ContextualStrategyProducer {
        static volatile PaymentStrategyType mode = PaymentStrategyType.CREDIT_CARD;

        @Produces
        @FromContextualProducer
        PaymentStrategy getPaymentStrategy(
                @CreditCard PaymentStrategy creditCard,
                @Cheque PaymentStrategy cheque,
                @Online PaymentStrategy online) {
            switch (mode) {
                case CREDIT_CARD:
                    return creditCard;
                case CHEQUE:
                    return cheque;
                case ONLINE:
                    return online;
                default:
                    throw new IllegalStateException("Unsupported mode: " + mode);
            }
        }
    }

    @Dependent
    public static class ContextualStrategyConsumer {
        @Inject
        @FromContextualProducer
        PaymentStrategy strategy;
    }

    public static class PlainCreditCardStrategy implements PaymentStrategy {
        @Inject
        SupportingDependency dependency;
        boolean postConstructCalled;

        @PostConstruct
        void postConstruct() {
            postConstructCalled = true;
        }

        @Override
        public String kind() {
            return "plain-credit-card";
        }

        @Override
        public boolean hasInjectedDependency() {
            return dependency != null;
        }

        @Override
        public boolean postConstructCalled() {
            return postConstructCalled;
        }
    }

    @Dependent
    public static class NonContextualStrategyProducer {
        static volatile PaymentStrategyType mode = PaymentStrategyType.CREDIT_CARD;

        @Produces
        @FromNonContextualProducer
        PaymentStrategy getPaymentStrategy() {
            switch (mode) {
                case CREDIT_CARD:
                    return new PlainCreditCardStrategy();
                case CHEQUE:
                    return new PlainCreditCardStrategy();
                case ONLINE:
                    return new PlainCreditCardStrategy();
                default:
                    throw new IllegalStateException("Unsupported mode: " + mode);
            }
        }
    }

    @Dependent
    public static class NonContextualStrategyConsumer {
        @Inject
        @FromNonContextualProducer
        PaymentStrategy strategy;
    }
}
