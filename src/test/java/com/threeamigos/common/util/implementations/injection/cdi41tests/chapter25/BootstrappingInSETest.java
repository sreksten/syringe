package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter25;

import com.threeamigos.common.util.implementations.injection.se.SyringeSeContainerInitializer;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Stereotype;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("25 - Bootstrapping in SE")
public class BootstrappingInSETest {

    @Test
    @DisplayName("25.1 - SeContainerInitializer.newInstance discovers exactly one provider and returns new instances")
    void shouldDiscoverProviderAndCreateNewInitializerInstances() {
        SeContainerInitializer one = SeContainerInitializer.newInstance();
        SeContainerInitializer two = SeContainerInitializer.newInstance();

        assertTrue(one instanceof SyringeSeContainerInitializer);
        assertTrue(two instanceof SyringeSeContainerInitializer);
        assertNotSame(one, two);
    }

    @Test
    @DisplayName("25.1 - initialize bootstraps container and close shuts it down")
    void shouldInitializeAndCloseContainer() {
        SeContainerInitializer initializer = SeContainerInitializer.newInstance()
                .disableDiscovery()
                .addBeanClasses(Chapter25_1SimpleBean.class);

        SeContainer container = initializer.initialize();
        assertTrue(container.isRunning());
        assertEquals("ok", container.select(Chapter25_1SimpleBean.class).get().value());

        container.close();
        assertFalse(container.isRunning());
    }

    @Test
    @DisplayName("25.1 - SeContainer can be closed automatically via try-with-resources")
    void shouldSupportTryWithResources() {
        SeContainer containerRef;
        try (SeContainer container = SeContainerInitializer.newInstance()
                .disableDiscovery()
                .addBeanClasses(Chapter25_1SimpleBean.class)
                .initialize()) {
            containerRef = container;
            assertTrue(container.isRunning());
        }
        assertFalse(containerRef.isRunning());
    }

    @Test
    @DisplayName("25.1 - addBeanClasses adds to synthetic explicit archive")
    void shouldTreatAddedBeanClassesAsExplicitSyntheticArchive() {
        try (SeContainer container = SeContainerInitializer.newInstance()
                .disableDiscovery()
                .addBeanClasses(Chapter25_1PlainSyntheticBean.class)
                .initialize()) {
            // Plain class has no bean defining annotation; explicit synthetic archive must still discover it.
            assertEquals("plain", container.select(Chapter25_1PlainSyntheticBean.class).get().value());
        }
    }

    @Test
    @DisplayName("25.1 - disableDiscovery ignores automatic scanning unless elements are explicitly added")
    void shouldDisableAutomaticDiscovery() {
        try (SeContainer container = SeContainerInitializer.newInstance()
                .disableDiscovery()
                .initialize()) {
            assertThrows(UnsatisfiedResolutionException.class, () ->
                    container.select(Chapter25_1SimpleBean.class).get());
        }
    }

    @Test
    @DisplayName("25.1 - addExtensions supports class and instance additions")
    void shouldAddExtensionsByClassAndInstance() {
        Chapter25_1ExtensionRecorder.reset();
        try (SeContainer container = SeContainerInitializer.newInstance()
                .disableDiscovery()
                .addBeanClasses(Chapter25_1SimpleBean.class)
                .addExtensions(Chapter25_1ClassExtension.class)
                .addExtensions(new Chapter25_1InstanceExtension())
                .initialize()) {
            assertTrue(container.isRunning());
            assertTrue(Chapter25_1ExtensionRecorder.classExtensionObservedBbd);
            assertTrue(Chapter25_1ExtensionRecorder.instanceExtensionObservedBbd);
        }
    }

    @Test
    @DisplayName("25.1 - enableInterceptors and enableDecorators configure synthetic archive")
    void shouldEnableInterceptorsAndDecoratorsFromInitializer() {
        Chapter25_1LifecycleRecorder.reset();
        try (SeContainer container = SeContainerInitializer.newInstance()
                .disableDiscovery()
                .addBeanClasses(
                        Chapter25_1InterceptedServiceImpl.class,
                        Chapter25_1NonPriorityInterceptor.class,
                        Chapter25_1DecoratedServiceImpl.class,
                        Chapter25_1NonPriorityDecorator.class
                )
                .enableInterceptors(Chapter25_1NonPriorityInterceptor.class)
                .enableDecorators(Chapter25_1NonPriorityDecorator.class)
                .initialize()) {
            String intercepted = container.select(Chapter25_1InterceptedService.class).get().ping();
            String decorated = container.select(Chapter25_1DecoratedService.class).get().ping();

            assertEquals("intercepted", intercepted);
            assertTrue(Chapter25_1LifecycleRecorder.interceptorCalled);
            assertEquals("decorated-base-decorated", decorated);
        }
    }

    @Test
    @DisplayName("25.1 - selectAlternatives enables alternative class in synthetic archive")
    void shouldEnableAlternativeClassSelection() {
        try (SeContainer container = SeContainerInitializer.newInstance()
                .disableDiscovery()
                .addBeanClasses(
                        Chapter25_1Service.class,
                        Chapter25_1DefaultService.class,
                        Chapter25_1SelectedAlternative.class
                )
                .selectAlternatives(Chapter25_1SelectedAlternative.class)
                .initialize()) {
            Chapter25_1Service service = container.select(Chapter25_1Service.class).get();
            assertEquals("selected", service.id());
        }
    }

    @Test
    @DisplayName("25.1 - selectAlternativeStereotypes enables alternative stereotype in synthetic archive")
    void shouldEnableAlternativeStereotypeSelection() {
        try (SeContainer container = SeContainerInitializer.newInstance()
                .disableDiscovery()
                .addBeanClasses(
                        Chapter25_1Service.class,
                        Chapter25_1DefaultService.class,
                        Chapter25_1StereotypedAlternative.class
                )
                .selectAlternativeStereotypes(Chapter25_1AlternativeStereotype.class)
                .initialize()) {
            Chapter25_1Service service = container.select(Chapter25_1Service.class).get();
            assertEquals("stereotype", service.id());
        }
    }

    @Test
    @DisplayName("25.1 - addProperty and setProperties keep mutable container properties and replace map")
    void shouldReplacePropertiesMapWhenSetPropertiesIsCalled() throws Exception {
        SeContainerInitializer initializer = SeContainerInitializer.newInstance()
                .addProperty("alpha", "one");

        Map<String, Object> replacement = new HashMap<String, Object>();
        replacement.put("beta", "two");
        initializer.setProperties(replacement);
        initializer.addProperty("gamma", "three");

        Field propertiesField = initializer.getClass().getDeclaredField("properties");
        propertiesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) propertiesField.get(initializer);

        assertFalse(properties.containsKey("alpha"));
        assertEquals("two", properties.get("beta"));
        assertEquals("three", properties.get("gamma"));
    }

    @Test
    @DisplayName("25.2 - close throws IllegalStateException when container is already shut down")
    void shouldThrowWhenCloseCalledTwice() {
        SeContainer container = SeContainerInitializer.newInstance()
                .disableDiscovery()
                .addBeanClasses(Chapter25_2DefaultQualifiedService.class)
                .initialize();

        assertTrue(container.isRunning());
        container.close();
        assertFalse(container.isRunning());
        assertThrows(IllegalStateException.class, container::close);
    }

    @Test
    @DisplayName("25.2 - getBeanManager returns BeanManager while running and throws after shutdown")
    void shouldGuardGetBeanManagerAfterShutdown() {
        SeContainer container = SeContainerInitializer.newInstance()
                .disableDiscovery()
                .addBeanClasses(Chapter25_2DefaultQualifiedService.class)
                .initialize();

        BeanManager beanManager = container.getBeanManager();
        assertTrue(beanManager != null);

        container.close();
        assertThrows(IllegalStateException.class, container::getBeanManager);
    }

    @Test
    @DisplayName("25.2 - select assumes @Default when no qualifier is passed")
    void shouldAssumeDefaultQualifierForUnqualifiedSelect() {
        try (SeContainer container = SeContainerInitializer.newInstance()
                .disableDiscovery()
                .addBeanClasses(
                        Chapter25_2Service.class,
                        Chapter25_2DefaultQualifiedService.class,
                        Chapter25_2SpecialQualifiedService.class
                )
                .initialize()) {
            Chapter25_2Service service = container.select(Chapter25_2Service.class).get();
            assertEquals("default", service.id());
        }
    }

    @Test
    @DisplayName("25.2 - Instance.select methods throw IllegalStateException when container is shut down")
    void shouldThrowForAnySelectAfterShutdown() {
        SeContainer container = SeContainerInitializer.newInstance()
                .disableDiscovery()
                .addBeanClasses(Chapter25_2DefaultQualifiedService.class)
                .initialize();
        container.close();

        assertThrows(IllegalStateException.class, () -> container.select(Chapter25_2DefaultQualifiedService.class));
        assertThrows(IllegalStateException.class, () -> container.select(new Chapter25_2ServiceTypeLiteral()));
        assertThrows(IllegalStateException.class, () -> container.select());
    }

    @Dependent
    public static class Chapter25_1SimpleBean {
        String value() {
            return "ok";
        }
    }

    public static class Chapter25_1PlainSyntheticBean {
        String value() {
            return "plain";
        }
    }

    public static class Chapter25_1ExtensionRecorder {
        static boolean classExtensionObservedBbd;
        static boolean instanceExtensionObservedBbd;

        static void reset() {
            classExtensionObservedBbd = false;
            instanceExtensionObservedBbd = false;
        }
    }

    public static class Chapter25_1ClassExtension implements Extension {
        public void before(@Observes BeforeBeanDiscovery event) {
            Chapter25_1ExtensionRecorder.classExtensionObservedBbd = true;
        }
    }

    public static class Chapter25_1InstanceExtension implements Extension {
        public void before(@Observes BeforeBeanDiscovery event) {
            Chapter25_1ExtensionRecorder.instanceExtensionObservedBbd = true;
        }
    }

    public static class Chapter25_1LifecycleRecorder {
        static boolean interceptorCalled;

        static void reset() {
            interceptorCalled = false;
        }
    }

    @InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface Chapter25_1Binding {
    }

    public interface Chapter25_1InterceptedService {
        String ping();
    }

    @Dependent
    public static class Chapter25_1InterceptedServiceImpl implements Chapter25_1InterceptedService {
        @Override
        @Chapter25_1Binding
        public String ping() {
            return "intercepted";
        }
    }

    @Interceptor
    @Chapter25_1Binding
    public static class Chapter25_1NonPriorityInterceptor {
        @AroundInvoke
        public Object around(InvocationContext ctx) throws Exception {
            Chapter25_1LifecycleRecorder.interceptorCalled = true;
            return ctx.proceed();
        }
    }

    public interface Chapter25_1DecoratedService {
        String ping();
    }

    @Dependent
    public static class Chapter25_1DecoratedServiceImpl implements Chapter25_1DecoratedService {
        @Override
        public String ping() {
            return "decorated-base";
        }
    }

    @Decorator
    public static class Chapter25_1NonPriorityDecorator implements Chapter25_1DecoratedService {
        @Inject
        @Delegate
        Chapter25_1DecoratedService delegate;

        @Override
        public String ping() {
            return delegate.ping() + "-decorated";
        }
    }

    public interface Chapter25_1Service {
        String id();
    }

    @Dependent
    public static class Chapter25_1DefaultService implements Chapter25_1Service {
        @Override
        public String id() {
            return "default";
        }
    }

    @Alternative
    @Dependent
    public static class Chapter25_1SelectedAlternative implements Chapter25_1Service {
        @Override
        public String id() {
            return "selected";
        }
    }

    @Stereotype
    @Alternative
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Chapter25_1AlternativeStereotype {
    }

    @Chapter25_1AlternativeStereotype
    @Dependent
    public static class Chapter25_1StereotypedAlternative implements Chapter25_1Service {
        @Override
        public String id() {
            return "stereotype";
        }
    }

    public interface Chapter25_2Service {
        String id();
    }

    @Dependent
    public static class Chapter25_2DefaultQualifiedService implements Chapter25_2Service {
        @Override
        public String id() {
            return "default";
        }
    }

    @Qualifier
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Chapter25_2Special {
    }

    @Dependent
    @Chapter25_2Special
    public static class Chapter25_2SpecialQualifiedService implements Chapter25_2Service {
        @Override
        public String id() {
            return "special";
        }
    }

    public static class Chapter25_2ServiceTypeLiteral extends jakarta.enterprise.util.TypeLiteral<Chapter25_2Service> {
    }
}
