package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.tckparity;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.NormalScope;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("2 - TCK parity for scope and built-in qualifiers")
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
class ScopeAndQualifierDefinitionTckParityTest {

    private static final Class<?>[] FIXTURE_CLASSES = new Class<?>[] {
            ThirdPartyScope.class,
            ThirdPartyScopeBean.class,
            AnotherScopeType.class,
            Mullet.class,
            SeaBass.class,
            ScopeDefaultBean.class,
            Order.class,
            OrderProcessor.class,
            NamedBean.class,
            AnyBean.class,
            NamedAnyBean.class,
            BeanProducer.class,
            ProducedAnyBean.class,
            ProducedNamedBean.class,
            ProducedNamedAnyBean.class
    };

    @Test
    @DisplayName("2.4 / ScopeDefinedInOtherBDATest - custom @NormalScope declared externally is bean-defining")
    void shouldTreatCustomScopeAsBeanDefiningAnnotation() {
        Syringe syringe = newSyringe(ThirdPartyScopeBean.class, ThirdPartyScope.class);
        syringe.setup();

        Bean<?> bean = uniqueBean(syringe.getBeanManager(), ThirdPartyScopeBean.class);
        assertEquals(ThirdPartyScope.class, bean.getScope());
    }

    @Test
    @DisplayName("2.4 / ScopeDefinitionTest - custom scope type has proper target and @NormalScope")
    void shouldExposeCustomScopeTypeMetadata() {
        Syringe syringe = newSyringe(Mullet.class, AnotherScopeType.class);
        syringe.setup();

        Bean<?> bean = uniqueBean(syringe.getBeanManager(), Mullet.class);
        assertEquals(AnotherScopeType.class, bean.getScope());
        Target target = AnotherScopeType.class.getAnnotation(Target.class);
        assertTrue(Arrays.asList(target.value()).contains(ElementType.TYPE));
        assertTrue(Arrays.asList(target.value()).contains(ElementType.METHOD));
        assertTrue(Arrays.asList(target.value()).contains(ElementType.FIELD));
        assertTrue(AnotherScopeType.class.isAnnotationPresent(NormalScope.class));
    }

    @Test
    @DisplayName("2.4 / ScopeDefinitionTest - bean without explicit scope defaults to @Dependent")
    void shouldDefaultBeanScopeToDependent() {
        Syringe syringe = newSyringe(ScopeDefaultBean.class);
        syringe.setup();

        Bean<?> bean = uniqueBean(syringe.getBeanManager(), ScopeDefaultBean.class);
        assertEquals(Dependent.class, bean.getScope());
    }

    @Test
    @DisplayName("2.3 / BuiltInQualifierDefinitionTest - managed beans declare built-in @Default and @Any")
    void shouldExposeBuiltInQualifiersOnManagedBeansAndInjectionPoint() {
        Syringe syringe = newSyringe(Order.class, OrderProcessor.class);
        syringe.setup();

        Bean<?> orderBean = uniqueBean(syringe.getBeanManager(), Order.class);
        assertTrue(orderBean.getQualifiers().contains(Default.Literal.INSTANCE));
        assertTrue(orderBean.getQualifiers().contains(Any.Literal.INSTANCE));
        assertEquals(2, orderBean.getQualifiers().size());
        assertEquals(1, orderBean.getInjectionPoints().size());
        assertTrue(orderBean.getInjectionPoints().iterator().next().getQualifiers().contains(Default.Literal.INSTANCE));
    }

    @Test
    @DisplayName("2.3 / BuiltInQualifierDefinitionTest - @Named and/or @Any beans and producers still include @Default")
    void shouldIncludeDefaultForNamedAndAnyBeansAndProducedBeans() {
        Syringe syringe = newSyringe(
                NamedBean.class,
                AnyBean.class,
                NamedAnyBean.class,
                BeanProducer.class,
                ProducedAnyBean.class,
                ProducedNamedBean.class,
                ProducedNamedAnyBean.class
        );
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        Bean<?> named = uniqueBean(beanManager, NamedBean.class);
        assertTrue(named.getQualifiers().contains(Default.Literal.INSTANCE));
        assertTrue(named.getQualifiers().contains(Any.Literal.INSTANCE));
        assertTrue(named.getQualifiers().contains(NamedLiteral.of("namedBean")));
        assertEquals("namedBean", named.getName());

        Bean<?> any = uniqueBean(beanManager, AnyBean.class, Any.Literal.INSTANCE);
        assertTrue(any.getQualifiers().contains(Default.Literal.INSTANCE));
        assertTrue(any.getQualifiers().contains(Any.Literal.INSTANCE));

        Bean<?> namedAny = uniqueBean(beanManager, NamedAnyBean.class, Any.Literal.INSTANCE);
        assertTrue(namedAny.getQualifiers().contains(Default.Literal.INSTANCE));
        assertTrue(namedAny.getQualifiers().contains(Any.Literal.INSTANCE));
        assertTrue(namedAny.getQualifiers().contains(NamedLiteral.of("namedAnyBean")));
        assertEquals("namedAnyBean", namedAny.getName());

        Bean<?> producedNamed = uniqueBean(beanManager, ProducedNamedBean.class, NamedLiteral.of("producedNamedBean"));
        assertTrue(producedNamed.getQualifiers().contains(Default.Literal.INSTANCE));
        assertTrue(producedNamed.getQualifiers().contains(Any.Literal.INSTANCE));
        assertTrue(producedNamed.getQualifiers().contains(NamedLiteral.of("producedNamedBean")));
        assertEquals("producedNamedBean", producedNamed.getName());
    }

    private Syringe newSyringe(Class<?>... classes) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), classes);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        Set<Class<?>> included = new HashSet<Class<?>>(Arrays.asList(classes));
        for (Class<?> fixture : FIXTURE_CLASSES) {
            if (!included.contains(fixture)) {
                syringe.exclude(fixture);
            }
        }

        // Exclude parity fixtures from sibling tests in the same package.
        excludeParityClassAndNested(syringe,
                "com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.tckparity.AlternativeAndStereotypeTckParityTest");
        return syringe;
    }

    private void excludeParityClassAndNested(Syringe syringe, String className) {
        try {
            Class<?> clazz = Class.forName(className);
            syringe.exclude(clazz);
            for (Class<?> nested : clazz.getDeclaredClasses()) {
                syringe.exclude(nested);
            }
        } catch (ClassNotFoundException ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Bean<T> uniqueBean(BeanManager beanManager, Class<T> type, Annotation... qualifiers) {
        return (Bean<T>) beanManager.resolve(beanManager.getBeans(type, qualifiers));
    }

    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @NormalScope
    public @interface ThirdPartyScope {
    }

    @ThirdPartyScope
    public static class ThirdPartyScopeBean {
    }

    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @NormalScope
    public @interface AnotherScopeType {
    }

    @AnotherScopeType
    public static class Mullet {
    }

    @RequestScoped
    public static class SeaBass {
    }

    public static class ScopeDefaultBean {
    }

    @Dependent
    public static class Order {
        @Inject
        public Order(OrderProcessor processor) {
        }
    }

    @Dependent
    public static class OrderProcessor {
    }

    @Named
    @Dependent
    public static class NamedBean {
    }

    @Any
    @Dependent
    public static class AnyBean {
    }

    @Named
    @Any
    @Dependent
    public static class NamedAnyBean {
    }

    @Dependent
    public static class BeanProducer {
        @Produces
        @Named
        @Any
        ProducedNamedAnyBean producedNamedAnyBean() {
            return new ProducedNamedAnyBean();
        }

        @Produces
        @Any
        ProducedAnyBean producedAnyBean() {
            return new ProducedAnyBean();
        }

        @Produces
        @Named
        ProducedNamedBean producedNamedBean() {
            return new ProducedNamedBean();
        }
    }

    public static class ProducedAnyBean {
    }

    public static class ProducedNamedBean {
    }

    public static class ProducedNamedAnyBean {
    }
}
