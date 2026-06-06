package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.tckparity;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.resolution.ProducerBean;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.IllegalProductException;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("3.3 - TCK parity: ProducerFieldLifecycleTest")
class ProducerFieldLifecycleTckParityTest {

    @Test
    @DisplayName("3.3 (a,b) - static producer field is a producer bean and can be injected")
    void staticProducerFieldCanBeInjected() {
        InMemoryMessageHandler handler = new InMemoryMessageHandler();
        Syringe syringe = new Syringe(handler,
                StaticTarantulaProducer.class, StaticTarantulaConsumer.class);
        setupOrThrow(syringe, handler);

        StaticTarantulaConsumer consumer = getContextualReference(syringe, StaticTarantulaConsumer.class);
        assertSame(StaticTarantulaProducer.produced, consumer.consumed);
    }

    @Test
    @DisplayName("3.3 (d,m,n) - null producer field handling depends on scope")
    void nullProducerFieldHandlingDependsOnScope() {
        InMemoryMessageHandler handler = new InMemoryMessageHandler();
        Syringe syringe = new Syringe(handler,
                DependentNullProducer.class, NonDependentNullProducer.class);
        setupOrThrow(syringe, handler);

        ProducerBean<?> dependentBean = findProducerField(syringe, DependentNullProducer.class, "nullSpider");
        CreationalContext<?> dependentCtx = syringe.getBeanManager().createCreationalContext(dependentBean);
        assertNull(dependentBean.create((CreationalContext) dependentCtx));

        ProducerBean<?> nonDependentBean = findProducerField(syringe, NonDependentNullProducer.class, "nullSpider");
        CreationalContext<?> nonDependentCtx = syringe.getBeanManager().createCreationalContext(nonDependentBean);
        assertThrows(IllegalProductException.class, () -> nonDependentBean.create((CreationalContext) nonDependentCtx));
    }

    @Test
    @DisplayName("3.3 (o) - disposer method for producer field is called on destroy")
    void disposerMethodForProducerFieldIsCalledOnDestroy() {
        TrackingProducer.reset();
        InMemoryMessageHandler handler = new InMemoryMessageHandler();
        Syringe syringe = new Syringe(handler, TrackingProducer.class);
        setupOrThrow(syringe, handler);

        ProducerBean<?> producerBean = findProducerField(syringe, TrackingProducer.class, "produced");
        assertNotNull(producerBean.getDisposerMethod());

        CreationalContext<?> ctx = syringe.getBeanManager().createCreationalContext(producerBean);
        Tarantula instance = (Tarantula) producerBean.create((CreationalContext) ctx);
        ((ProducerBean) producerBean).destroy(instance, (CreationalContext) ctx);

        assertTrue(TrackingProducer.destroyed);
        assertEquals(instance.birth, TrackingProducer.destroyedBirth);
    }

    private ProducerBean<?> findProducerField(Syringe syringe, Class<?> declaringClass, String fieldName) {
        return syringe.getKnowledgeBase().getProducerBeans().stream()
                .filter(bean -> bean.getDeclaringClass().equals(declaringClass))
                .filter(bean -> bean.getProducerField() != null)
                .filter(bean -> bean.getProducerField().getName().equals(fieldName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Producer field not found: " + declaringClass.getName() + "#" + fieldName));
    }

    private void setupOrThrow(Syringe syringe, InMemoryMessageHandler handler) {
        try {
            syringe.setup();
        } catch (RuntimeException e) {
            throw new AssertionError(String.join(" | ", handler.getAllErrorMessages()), e);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> T getContextualReference(Syringe syringe, Class<T> beanType) {
        Set<Bean<?>> beans = syringe.getBeanManager().getBeans(beanType);
        Bean bean = syringe.getBeanManager().resolve((Set) beans);
        CreationalContext<?> ctx = syringe.getBeanManager().createCreationalContext(bean);
        return (T) syringe.getBeanManager().getContext(bean.getScope()).get((Contextual) bean, ctx);
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @interface StaticQ {
        class Literal extends AnnotationLiteral<StaticQ> implements StaticQ {
            private static final long serialVersionUID = 1L;
        }
    }

    static class Tarantula {
        final long birth = System.nanoTime();
    }

    @Dependent
    static class StaticTarantulaProducer {
        @Produces
        @StaticQ
        static Tarantula produced = new Tarantula();
    }

    @Dependent
    static class StaticTarantulaConsumer {
        @Inject
        @StaticQ
        Tarantula consumed;
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @interface NullQ {
    }

    @Dependent
    static class DependentNullProducer {
        @Produces
        @Dependent
        @NullQ
        Tarantula nullSpider = null;
    }

    @Dependent
    static class NonDependentNullProducer {
        @Produces
        @RequestScoped
        @NullQ
        @Broken
        Tarantula nullSpider = null;
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @interface TrackingQ {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @interface Broken {
    }

    @Dependent
    static class TrackingProducer {
        static boolean destroyed;
        static long destroyedBirth;

        @Produces
        @TrackingQ
        Tarantula produced = new Tarantula();

        void dispose(@Disposes @TrackingQ Tarantula tarantula) {
            destroyed = true;
            destroyedBirth = tarantula.birth;
        }

        static void reset() {
            destroyed = false;
            destroyedBirth = -1L;
        }
    }
}
