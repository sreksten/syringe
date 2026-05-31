package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par34disposermethods.tckparity;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.TransientReference;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("3.4.1 - TCK parity: DisposesMethodCalledOnceTest")
@Isolated
class DisposesMethodCalledOnceTckParityTest {

    @Test
    @DisplayName("3.4.1 (ba) - each matching disposer method is called exactly once")
    void eachDisposerMethodIsCalledExactlyOnce() {
        Producer.reset();
        InMemoryMessageHandler handler = new InMemoryMessageHandler();
        Syringe syringe = new Syringe(handler,
                Producer.class, Observer.class, FirstBean.class, SecondBean.class);
        setupOrThrow(syringe, handler);

        syringe.getBeanManager().getEvent().select(String.class).fire("Hello");

        assertEquals(1, Producer.disposedA.get());
        assertEquals(1, Producer.disposedB.get());
        assertEquals(1, Producer.disposedC.get());
        assertEquals(1, Producer.disposedD.get());
        assertEquals(1, Producer.disposedE.get());
        assertEquals(1, Producer.disposedF.get());
    }

    private void setupOrThrow(Syringe syringe, InMemoryMessageHandler handler) {
        try {
            syringe.setup();
        } catch (RuntimeException e) {
            throw new AssertionError(String.join(" | ", handler.getAllErrorMessages()), e);
        }
    }

    @Qualifier @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER}) @interface A {}
    @Qualifier @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER}) @interface B {}
    @Qualifier @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER}) @interface C {}
    @Qualifier @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER}) @interface D {}
    @Qualifier @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER}) @interface E {}
    @Qualifier @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER}) @interface F {}

    @Dependent
    static class FirstBean {
        void ping() {
        }
    }

    @Dependent
    static class SecondBean {
        void ping() {
        }
    }

    @Dependent
    static class Producer {
        static AtomicInteger disposedA = new AtomicInteger();
        static AtomicInteger disposedB = new AtomicInteger();
        static AtomicInteger disposedC = new AtomicInteger();
        static AtomicInteger disposedD = new AtomicInteger();
        static AtomicInteger disposedE = new AtomicInteger();
        static AtomicInteger disposedF = new AtomicInteger();

        @Produces
        @A
        public FirstBean produceA() {
            return new FirstBean();
        }

        @Produces
        @B
        public FirstBean produceB() {
            return new FirstBean();
        }

        @Produces
        @C
        public FirstBean produceC(@D SecondBean secondBean) {
            secondBean.ping();
            return new FirstBean();
        }

        @Produces
        @E
        public FirstBean produceE(@TransientReference @F SecondBean secondBean) {
            secondBean.ping();
            return new FirstBean();
        }

        @Produces
        @D
        public SecondBean produceD() {
            return new SecondBean();
        }

        @Produces
        @F
        public SecondBean produceF() {
            return new SecondBean();
        }

        public void disposeA(@Disposes @A FirstBean bean) {
            disposedA.incrementAndGet();
        }

        public void disposeB(@Disposes @B FirstBean bean) {
            disposedB.incrementAndGet();
        }

        public void disposeC(@Disposes @C FirstBean bean) {
            disposedC.incrementAndGet();
        }

        public void disposeD(@Disposes @D SecondBean bean) {
            disposedD.incrementAndGet();
        }

        public void disposeF(@Disposes @F SecondBean bean) {
            disposedE.incrementAndGet();
        }

        public void disposeE(@Disposes @E FirstBean bean) {
            disposedF.incrementAndGet();
        }

        static void reset() {
            disposedA = new AtomicInteger();
            disposedB = new AtomicInteger();
            disposedC = new AtomicInteger();
            disposedD = new AtomicInteger();
            disposedE = new AtomicInteger();
            disposedF = new AtomicInteger();
        }
    }

    @Dependent
    static class Observer {
        @Inject
        @B
        Instance<FirstBean> beanB;

        @Inject
        @C
        FirstBean beanC;

        @Inject
        @E
        FirstBean beanE;

        public void observes(@Observes String message, @A FirstBean dependentBean) {
            beanB.destroy(beanB.get());
            beanE.ping();
            beanC.ping();
            dependentBean.ping();
        }
    }
}
