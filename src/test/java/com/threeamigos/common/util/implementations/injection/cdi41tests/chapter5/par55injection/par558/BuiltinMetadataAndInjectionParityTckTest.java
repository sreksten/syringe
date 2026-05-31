package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par558;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.Priority;
import jakarta.decorator.Delegate;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Decorated;
import jakarta.enterprise.inject.Intercepted;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.inject.spi.Decorator;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("5.5.8 - TCK parity for built-in metadata injection and invalid injection points")
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
class BuiltinMetadataAndInjectionParityTckTest {

    private static final Class<?>[] FIXTURE_CLASSES = new Class<?>[] {
            DecoratedConstructorInjector.class,
            DecoratedFieldInjector.class,
            InterceptedConstructorInjector.class,
            Cream.class,
            YoghurtField.class,
            MilkProduct.class,
            Yoghurt.class,
            YoghurtFactory.class,
            MilkProductDecorator.class,
            Fruit.class,
            Probiotic.class,
            InvalidInterceptorMetadataConsumer.class,
            InvalidInterceptedBeanMetadataConsumer.class,
            InvalidBeanMetadataTypeParameterConsumer.class,
            InvalidProducerBeanMetadataTypeParameterBean.class,
            InvalidDisposerBeanMetadataParameterBean.class
    };

    @Test
    @DisplayName("5.5.8 / DecoratedBeanConstructorInjectionTest - @Decorated Bean<T> constructor injection outside decorator is definition error")
    void shouldRejectDecoratedBeanConstructorInjectionOutsideDecorator() {
        Syringe syringe = newSyringe(DecoratedConstructorInjector.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("5.5.8 / DecoratedBeanFieldInjectionTest - @Decorated Bean<T> field injection outside decorator is definition error")
    void shouldRejectDecoratedBeanFieldInjectionOutsideDecorator() {
        Syringe syringe = newSyringe(DecoratedFieldInjector.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("5.5.8 / InterceptedBeanConstructorInjectionTest - @Intercepted Bean<T> constructor injection outside interceptor is definition error")
    void shouldRejectInterceptedBeanConstructorInjectionOutsideInterceptor() {
        Syringe syringe = newSyringe(InterceptedConstructorInjector.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("5.5.8 / BeanTypeParamFieldTest - Bean<T> type parameter on field must match declaring bean type")
    void shouldRejectBeanMetadataFieldTypeParameterMismatch() {
        Syringe syringe = newSyringe(Cream.class, YoghurtField.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("5.5.8 / BuiltinMetadataBeanTest - container exposes Bean/Interceptor/Decorator metadata with correct declaring context")
    void shouldExposeBuiltinMetadataForManagedProducerInterceptorAndDecorator() {
        Syringe syringe = newSyringe(
                Yoghurt.class,
                YoghurtFactory.class,
                MilkProductDecorator.class,
                Fruit.class,
                Probiotic.class,
                MilkProduct.class
        );
        syringe.setup();

        MilkProduct fruity = syringe.inject(MilkProduct.class, new Fruit.Literal());
        MilkProduct probiotic = syringe.inject(MilkProduct.class, new Probiotic.Literal());
        assertNotNull(fruity);
        assertNotNull(probiotic);
        Bean<?> resolvedFruitBean = syringe.getBeanManager().resolve(
                syringe.getBeanManager().getBeans(Yoghurt.class, new Fruit.Literal()));
        Bean<?> resolvedProbioticBean = syringe.getBeanManager().resolve(
                syringe.getBeanManager().getBeans(Yoghurt.class, new Probiotic.Literal()));
        assertNotNull(resolvedFruitBean);
        assertNotNull(resolvedProbioticBean);
        assertNotNull(YoghurtFactory.fruitBean);
        assertNotNull(YoghurtFactory.probioticBean);
        assertTrue(YoghurtFactory.fruitBean.getTypes().contains(Yoghurt.class));
        assertTrue(YoghurtFactory.probioticBean.getTypes().contains(Yoghurt.class));

        MilkProduct product = syringe.inject(MilkProduct.class);
        assertEquals("decorated:yoghurt", product.name());
        assertNotNull(MilkProductDecorator.lastDecoratorMetadata);
        assertEquals(MilkProductDecorator.class, MilkProductDecorator.lastDecoratorMetadata.getBeanClass());
        assertNotNull(MilkProductDecorator.lastDecoratedBeanMetadata);
        assertEquals(Yoghurt.class, MilkProductDecorator.lastDecoratedBeanMetadata.getBeanClass());

        Set<Type> decoratedTypes = new HashSet<Type>();
        decoratedTypes.add(MilkProduct.class);
        Decorator<?> decorator = syringe.getBeanManager().resolveDecorators(decoratedTypes).iterator().next();
        assertEquals(MilkProductDecorator.class, decorator.getBeanClass());
        assertEquals(decorator, MilkProductDecorator.lastDecoratorMetadata);
        assertEquals(MilkProductDecorator.lastDecoratorMetadata, decorator);

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

        // Exclude sibling parity fixtures that intentionally fail deployment.
        syringe.exclude(InvalidBeanMetadataTypeParameterConsumer.class);
        syringe.exclude(InvalidInterceptorMetadataConsumer.class);
        syringe.exclude(InvalidInterceptedBeanMetadataConsumer.class);
        syringe.exclude(InvalidProducerBeanMetadataTypeParameterBean.class);
        syringe.exclude(InvalidDisposerBeanMetadataParameterBean.class);
        syringe.exclude(DecoratorBeanMetadataTypeParameterTckParityTest.InvalidDecoratorMetadataField.class);
        syringe.exclude(DecoratorBeanMetadataTypeParameterTckParityTest.InvalidDecoratorMetadataConstructor.class);
        syringe.exclude(DecoratorBeanMetadataTypeParameterTckParityTest.InvalidDecoratedBeanMetadataField.class);
        return syringe;
    }

    @Dependent
    public static class DecoratedConstructorInjector {
        @Inject
        public DecoratedConstructorInjector(@Decorated Bean<DecoratedConstructorInjector> bean) {
        }
    }

    @Dependent
    public static class DecoratedFieldInjector {
        @Inject
        @Decorated
        Bean<DecoratedFieldInjector> bean;
    }

    @Dependent
    public static class InterceptedConstructorInjector {
        @Inject
        public InterceptedConstructorInjector(@Intercepted Bean<InterceptedConstructorInjector> bean) {
        }
    }

    public static class Cream {
    }

    @Dependent
    public static class YoghurtField {
        @Inject
        private Bean<Cream> bean;
    }

    public interface MilkProduct {
        String name();
    }

    @Dependent
    public static class Yoghurt implements MilkProduct {

        @Inject
        private Bean<Yoghurt> selfBean;

        @Override
        public String name() {
            return "yoghurt";
        }

        Bean<Yoghurt> getSelfBean() {
            return selfBean;
        }
    }

    @Dependent
    public static class YoghurtFactory {
        static Bean<Yoghurt> fruitBean;
        static Bean<Yoghurt> probioticBean;

        @Produces
        @Fruit
        Yoghurt fruitYoghurt(Bean<Yoghurt> bean) {
            fruitBean = bean;
            return new Yoghurt();
        }

        @Produces
        @Probiotic
        Yoghurt probioticYoghurt(Bean<Yoghurt> bean) {
            probioticBean = bean;
            return new Yoghurt();
        }
    }

    @jakarta.decorator.Decorator
    @Priority(100)
    public static class MilkProductDecorator implements MilkProduct {

        static Decorator<MilkProductDecorator> lastDecoratorMetadata;
        static Bean<MilkProduct> lastDecoratedBeanMetadata;

        @Inject
        @Delegate
        private MilkProduct delegate;

        @Inject
        private Decorator<MilkProductDecorator> decorator;

        @Inject
        @Decorated
        private Bean<MilkProduct> decoratedBean;

        @Override
        public String name() {
            lastDecoratorMetadata = decorator;
            lastDecoratedBeanMetadata = decoratedBean;
            return "decorated:" + delegate.name();
        }
    }

    @jakarta.inject.Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE})
    public @interface Fruit {
        class Literal extends jakarta.enterprise.util.AnnotationLiteral<Fruit> implements Fruit {
            private static final long serialVersionUID = 1L;
        }
    }

    @jakarta.inject.Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE})
    public @interface Probiotic {
        class Literal extends jakarta.enterprise.util.AnnotationLiteral<Probiotic> implements Probiotic {
            private static final long serialVersionUID = 1L;
        }
    }

}
