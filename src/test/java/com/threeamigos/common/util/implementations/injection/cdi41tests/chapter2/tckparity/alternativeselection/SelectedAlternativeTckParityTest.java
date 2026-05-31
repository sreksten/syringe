package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.tckparity.alternativeselection;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Vetoed;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("2.7 - TCK parity for SelectedAlternative01/02/03")
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
class SelectedAlternativeTckParityTest {

    private static final String DEFAULT = "default";
    private static final String ALT1 = "alt1";
    private static final String ALT2 = "alt2";

    @Test
    @DisplayName("SelectedAlternative01Test - selected alternatives are available across consumers")
    void shouldSelectAlternativeManagedBeanAndProducerForAllConsumers() {
        Syringe syringe = newSyringe(
                Alpha.class,
                Bravo.class,
                Charlie.class,
                Foo.class,
                Bar.class,
                BarProducer.class,
                Boss.class,
                SimpleTestBean.class,
                TestBean.class
        );
        try {
            Alpha alpha = resolveManagedBean(syringe.getBeanManager(), Alpha.class);
            Bravo bravo = resolveManagedBean(syringe.getBeanManager(), Bravo.class);
            Charlie charlie = resolveManagedBean(syringe.getBeanManager(), Charlie.class);

            alpha.assertAvailable(Foo.class);
            bravo.assertAvailable(Foo.class);
            charlie.assertAvailable(Foo.class);

            alpha.assertAvailable(Bar.class, Wild.Literal.INSTANCE);
            bravo.assertAvailable(Bar.class, Wild.Literal.INSTANCE);
            charlie.assertAvailable(Bar.class, Wild.Literal.INSTANCE);

            alpha.assertAvailable(Bar.class, Tame.Literal.INSTANCE);
            bravo.assertAvailable(Bar.class, Tame.Literal.INSTANCE);
            charlie.assertAvailable(Bar.class, Tame.Literal.INSTANCE);
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("SelectedAlternative02Test - ambiguous dependency resolves to highest-priority selected alternative")
    void shouldResolveToHighestPrioritySelectedAlternative() {
        Syringe syringe = newSyringe(
                Alpha.class,
                Bravo.class,
                Charlie.class,
                SimpleTestBean.class,
                Boss.class,
                Foo.class,
                Bar.class,
                TestBean.class
        );
        try {
            Alpha alpha = resolveManagedBean(syringe.getBeanManager(), Alpha.class);
            Bravo bravo = resolveManagedBean(syringe.getBeanManager(), Bravo.class);
            Charlie charlie = resolveManagedBean(syringe.getBeanManager(), Charlie.class);

            assertEquals(Bar.class.getName(), alpha.assertAvailable(TestBean.class).getId());
            assertEquals(Bar.class.getName(), bravo.assertAvailable(TestBean.class).getId());
            assertEquals(Bar.class.getName(), charlie.assertAvailable(TestBean.class).getId());
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("SelectedAlternative03Test - highest-priority alternative producer is selected")
    void shouldSelectHighestPriorityAlternativeProducer() {
        Syringe syringe = newSyringe(
                DeltaConsumer.class,
                Delta.class,
                StandardDeltaProducer.class,
                AlternativeDeltaProducer1.class,
                AlternativeDeltaProducer2.class
        );
        try {
            DeltaConsumer consumer = resolveManagedBean(syringe.getBeanManager(), DeltaConsumer.class);
            assertNotNull(consumer);
            assertEquals(ALT2, consumer.ping());
        } finally {
            syringe.shutdown();
        }
    }

    private Syringe newSyringe(Class<?>... classes) {
        Syringe syringe = new Syringe();
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.initialize();
        for (Class<?> clazz : classes) {
            syringe.addDiscoveredClass(clazz, BeanArchiveMode.EXPLICIT);
        }
        syringe.start();
        return syringe;
    }

    @SuppressWarnings("unchecked")
    private <T> T resolveManagedBean(BeanManager beanManager, Class<T> type) {
        return (T) beanManager.getReference(
                beanManager.resolve(beanManager.getBeans(type)),
                type,
                beanManager.createCreationalContext(null)
        );
    }

    interface TestBean {
        String getId();
    }

    @Priority(1000)
    @Alternative
    @Dependent
    static class Foo implements TestBean {
        @Override
        public String getId() {
            return Foo.class.getName();
        }
    }

    @Priority(2000)
    @Alternative
    @Dependent
    static class Bar implements TestBean {
        @Override
        public String getId() {
            return Bar.class.getName();
        }
    }

    @Priority(1100)
    @Dependent
    static class BarProducer {
        @Alternative
        @Produces
        @Wild
        final Bar producedBar = new Bar();

        @Alternative
        @Produces
        @Tame
        Bar produceTameBar() {
            return new Bar();
        }
    }

    @Priority(900)
    @Alternative
    @Dependent
    static class Boss {
        @Produces
        TestBean produceSimpleTestBean() {
            return new SimpleTestBean();
        }
    }

    static class SimpleTestBean implements TestBean {
        @Override
        public String getId() {
            return SimpleTestBean.class.getName();
        }
    }

    @Dependent
    static class Alpha extends AssertBean {
    }

    @Dependent
    static class Bravo extends AssertBean {
    }

    @Dependent
    static class Charlie extends AssertBean {
    }

    static abstract class AssertBean {
        @Inject
        @Any
        Instance<Object> instance;

        @Inject
        BeanManager beanManager;

        <T> T assertAvailable(Class<T> beanType, Annotation... qualifiers) {
            assertNotNull(beanManager.resolve(beanManager.getBeans(beanType, qualifiers)));
            Instance<T> selected = instance.select(beanType, qualifiers);
            T bean = selected.get();
            assertNotNull(bean);
            return bean;
        }
    }

    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @Qualifier
    @interface Wild {
        final class Literal extends AnnotationLiteral<Wild> implements Wild {
            private static final long serialVersionUID = 1L;
            static final Wild INSTANCE = new Literal();
        }
    }

    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @Qualifier
    @interface Tame {
        final class Literal extends AnnotationLiteral<Tame> implements Tame {
            private static final long serialVersionUID = 1L;
            static final Tame INSTANCE = new Literal();
        }
    }

    @Vetoed
    static class Delta {
        private final String value;

        Delta(String value) {
            this.value = value;
        }

        String ping() {
            return value;
        }
    }

    @ApplicationScoped
    static class StandardDeltaProducer {
        @Produces
        Delta produce() {
            return new Delta(DEFAULT);
        }
    }

    @ApplicationScoped
    @Alternative
    @Priority(100)
    static class AlternativeDeltaProducer1 {
        @Produces
        Delta produce() {
            return new Delta(ALT1);
        }
    }

    @ApplicationScoped
    @Alternative
    @Priority(200)
    static class AlternativeDeltaProducer2 {
        @Produces
        Delta produce() {
            return new Delta(ALT2);
        }
    }

    @Dependent
    static class DeltaConsumer {
        @Inject
        Delta delta;

        String ping() {
            return delta.ping();
        }
    }
}
