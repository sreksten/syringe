package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.tckparity;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("3 - TCK parity for bean and simple bean definition")
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
class SimpleAndBeanDefinitionTckParityTest {

    private static final Class<?>[] FIXTURE_CLASSES = new Class<?>[] {
            RedSnapper.class,
            Animal.class,
            GenericManagedBeanBroken.class,
            Food.class,
            FoodConsumerBroken.class,
            LeopardBroken.class,
            OuterClass.class,
            OuterClass.StaticInnerClass.class,
            OuterClass.InnerClassNotBean.class,
            Car.class,
            CowNotBean.class,
            AntelopeNotBean.class,
            Donkey.class,
            Sheep.class,
            Tiger.class,
            SnowTiger.class,
            SimpleExtensionImpl.class
    };

    @Test
    @DisplayName("3.1 / BeanDefinitionTest - bean has non-empty bean types, qualifiers and declared scope")
    void shouldExposeBasicBeanMetadata() {
        Syringe syringe = newSyringe(RedSnapper.class, Animal.class);
        syringe.setup();

        Bean<?> bean = uniqueBean(syringe.getBeanManager(), RedSnapper.class);
        assertFalse(bean.getTypes().isEmpty());
        assertFalse(bean.getQualifiers().isEmpty());
        assertEquals(RequestScoped.class, bean.getScope());
    }

    @Test
    @DisplayName("3.1 / BeanDefinitionTest - bean types include class hierarchy and Object")
    void shouldIncludeHierarchyInBeanTypes() {
        Syringe syringe = newSyringe(RedSnapper.class, Animal.class);
        syringe.setup();

        Bean<?> bean = uniqueBean(syringe.getBeanManager(), RedSnapper.class);
        Set<Type> types = bean.getTypes();
        assertTrue(types.contains(RedSnapper.class));
        assertTrue(types.contains(Animal.class));
        assertTrue(types.contains(Object.class));
    }

    @Test
    @DisplayName("3.1 / GenericManagedBeanTest - non-@Dependent generic managed bean is definition error")
    void shouldRejectNonDependentGenericManagedBean() {
        Syringe syringe = newSyringe(GenericManagedBeanBroken.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.5 / ConstructorHasAsyncObservesParameterTest - @Inject constructor with @ObservesAsync parameter is definition error")
    void shouldRejectConstructorWithAsyncObservesParameter() {
        Syringe syringe = newSyringe(Food.class, FoodConsumerBroken.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.1 / NormalScopedWithPublicFieldTest - normal-scoped bean with public field is definition error")
    void shouldRejectNormalScopedBeanWithPublicField() {
        Syringe syringe = newSyringe(LeopardBroken.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.1 / SimpleBeanDefinitionTest - abstract/interface/non-static-inner classes are not discovered as beans")
    void shouldNotDiscoverInvalidSimpleBeanKinds() {
        Syringe syringe = newSyringe(
                OuterClass.StaticInnerClass.class,
                OuterClass.InnerClassNotBean.class,
                Car.class
        );
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        assertEquals(1, beanManager.getBeans(OuterClass.StaticInnerClass.class).size());
        assertEquals(0, beanManager.getBeans(OuterClass.InnerClassNotBean.class).size());
        assertEquals(0, beanManager.getBeans(Car.class).size());
    }

    @Test
    @DisplayName("3.1 / SimpleBeanDefinitionTest - bean must have no-arg constructor or @Inject constructor")
    void shouldRequireValidBeanConstructorForSimpleBeanDiscovery() {
        Syringe syringe = newSyringe(AntelopeNotBean.class, Donkey.class, Sheep.class);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        assertEquals(0, beanManager.getBeans(AntelopeNotBean.class).size());
        assertEquals(1, beanManager.getBeans(Donkey.class).size());
        assertEquals(1, beanManager.getBeans(Sheep.class).size());
    }

    @Test
    @DisplayName("3.1 / SimpleBeanDefinitionTest - dependent/singleton scoped beans may have public non-static fields")
    void shouldAllowDependentOrSingletonWithPublicField() {
        Syringe syringe = newSyringe(Tiger.class, SnowTiger.class);
        syringe.setup();

        assertEquals("pete", syringe.inject(Tiger.class).name);
        assertEquals("martin", syringe.inject(SnowTiger.class).name);
    }

    @Test
    @DisplayName("3.1 / SimpleBeanDefinitionTest - extension type is not discovered as bean")
    void shouldNotDiscoverExtensionAsBean() {
        Syringe syringe = newSyringe(SimpleExtensionImpl.class);
        syringe.setup();
        assertEquals(0, syringe.getBeanManager().getBeans(SimpleExtensionImpl.class).size());
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

        // Exclude other parity fixtures in this package that intentionally fail deployment.
        excludeParityClassAndNested(syringe,
                "com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.tckparity.ProducerFieldOnInterceptorTckParityTest");
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
    private <T> Bean<T> uniqueBean(BeanManager beanManager, Class<T> type) {
        return (Bean<T>) beanManager.resolve(beanManager.getBeans(type));
    }

    public interface Animal {
    }

    @RequestScoped
    public static class RedSnapper implements Animal {
    }

    @RequestScoped
    public static class GenericManagedBeanBroken<T> {
    }

    @Dependent
    public static class Food {
    }

    @Dependent
    public static class FoodConsumerBroken {
        @Inject
        public FoodConsumerBroken(@ObservesAsync Food food) {
        }
    }

    @RequestScoped
    public static class LeopardBroken {
        public String name = "pete";
    }

    public static class OuterClass {
        public static class StaticInnerClass {
        }

        public class InnerClassNotBean {
        }
    }

    public interface Car {
    }

    public abstract static class CowNotBean {
    }

    public static class AntelopeNotBean {
        public AntelopeNotBean(String name) {
        }
    }

    public static class Donkey {
        public Donkey() {
        }
    }

    public static class Sheep {
        @Inject
        public Sheep() {
        }
    }

    @Dependent
    public static class Tiger {
        public String name = "pete";
    }

    @Singleton
    public static class SnowTiger {
        public String name = "martin";
    }

    public static class SimpleExtensionImpl implements Extension {
    }
}
