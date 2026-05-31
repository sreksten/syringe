package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.tckparity;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.resolution.ProducerBean;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.IllegalProductException;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Qualifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("3.2 - TCK parity: ProducerMethodDefinitionTest")
class ProducerMethodDefinitionTckParityTest {

    @Test
    @DisplayName("3.2 (b,aa,e,f) - static producer/disposer behavior and null handling follow spec")
    void staticProducerDisposerAndNullHandlingFollowSpec() {
        StaticProducer.reset();
        InMemoryMessageHandler handler = new InMemoryMessageHandler();
        Syringe syringe = new Syringe(handler,
                StaticProducer.class, NullProducer.class, NonDependentNullProducer.class);
        setupOrThrow(syringe, handler);

        ProducerBean<?> staticBean = findProducerMethod(syringe, StaticProducer.class, "produceString");
        CreationalContext<?> ctx = syringe.getBeanManager().createCreationalContext(staticBean);
        String produced = (String) staticBean.create((CreationalContext) ctx);
        assertEquals("Pete", produced);
        ((ProducerBean) staticBean).destroy(produced, (CreationalContext) ctx);
        assertTrue(StaticProducer.destroyed);

        ProducerBean<?> dependentNull = findProducerMethod(syringe, NullProducer.class, "produceNull");
        CreationalContext<?> depCtx = syringe.getBeanManager().createCreationalContext(dependentNull);
        assertEquals(null, dependentNull.create((CreationalContext) depCtx));

        ProducerBean<?> nonDependentNull = findProducerMethod(syringe, NonDependentNullProducer.class, "produceNull");
        CreationalContext<?> nonDepCtx = syringe.getBeanManager().createCreationalContext(nonDependentNull);
        assertThrows(IllegalProductException.class, () -> nonDependentNull.create((CreationalContext) nonDepCtx));
    }

    @Test
    @DisplayName("3.2 (c,k,ba,bb) - producer method qualifiers, name and scope are applied")
    void producerMethodQualifiersNameAndScopeAreApplied() {
        InMemoryMessageHandler handler = new InMemoryMessageHandler();
        Syringe syringe = new Syringe(handler, ConfiguredProducer.class);
        setupOrThrow(syringe, handler);

        ProducerBean<?> bean = findProducerMethod(syringe, ConfiguredProducer.class, "createSpider");
        assertEquals(RequestScoped.class, bean.getScope());
        assertEquals("blackWidow", bean.getName());
        assertTrue(bean.getQualifiers().contains(new Tame.Literal()));
        assertTrue(bean.getQualifiers().contains(Any.Literal.INSTANCE));
    }

    private ProducerBean<?> findProducerMethod(Syringe syringe, Class<?> declaringClass, String methodName) {
        return syringe.getKnowledgeBase().getProducerBeans().stream()
                .filter(bean -> bean.getDeclaringClass().equals(declaringClass))
                .filter(bean -> bean.getProducerMethod() != null)
                .filter(bean -> bean.getProducerMethod().getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Producer method not found: " + declaringClass.getName() + "#" + methodName));
    }

    private void setupOrThrow(Syringe syringe, InMemoryMessageHandler handler) {
        try {
            syringe.setup();
        } catch (RuntimeException e) {
            throw new AssertionError(String.join(" | ", handler.getAllErrorMessages()), e);
        }
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @interface Tame {
        class Literal extends jakarta.enterprise.util.AnnotationLiteral<Tame> implements Tame {
            private static final long serialVersionUID = 1L;
        }
    }

    @Dependent
    static class StaticProducer {
        static boolean destroyed;

        @Produces
        @Tame
        static String produceString() {
            return "Pete";
        }

        static void dispose(@Disposes @Tame String value) {
            destroyed = true;
        }

        static void reset() {
            destroyed = false;
        }
    }

    @Dependent
    static class NullProducer {
        @Produces
        @Dependent
        String produceNull() {
            return null;
        }
    }

    @Dependent
    static class NonDependentNullProducer {
        @Produces
        @RequestScoped
        String produceNull() {
            return null;
        }
    }

    static class Spider {
    }

    @Dependent
    static class ConfiguredProducer {
        @Produces
        @Tame
        @RequestScoped
        @jakarta.inject.Named("blackWidow")
        Spider createSpider() {
            return new Spider();
        }
    }
}
