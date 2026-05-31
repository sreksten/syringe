package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par67contextmanagementforcustomscopes;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("6.7 - Context management for custom scopes")
@Execution(ExecutionMode.SAME_THREAD)
public class ContextManagementForCustomScopesTest {

    @Test
    @DisplayName("6.7 - Syringe handles a custom scope context implementation and resolves custom-scoped beans")
    void shouldHandleCustomContextForCustomScope() {
        MyTestCustomContext context = new MyTestCustomContext();
        Syringe syringe = newSyringe(
                context,
                MyCustomScopedService.class
        );
        context.setBeanManager(syringe.getBeanManager());
        BeanManager beanManager = syringe.getBeanManager();
        Context cdiContext = beanManager.getContext(MyTestCustomScoped.class);
        Bean<MyCustomScopedService> bean = resolveBean(beanManager, MyCustomScopedService.class);

        context.activate("ctx-1");
        String firstId;
        String firstIdAgain;
        try {
            MyCustomScopedService first = cdiContext.get(bean, beanManager.createCreationalContext(bean));
            MyCustomScopedService again = cdiContext.get(bean);
            firstId = first.id();
            firstIdAgain = again.id();
            assertSame(first, again);
            assertEquals(firstId, firstIdAgain);
        } finally {
            context.deactivate("ctx-1");
        }

        context.activate("ctx-2");
        try {
            MyCustomScopedService second = cdiContext.get(bean, beanManager.createCreationalContext(bean));
            String secondId = second.id();
            assertNotEquals(firstId, secondId);
        } finally {
            context.deactivate("ctx-2");
        }
    }

    @Test
    @DisplayName("6.7 - Custom context lifecycle can synchronously fire @Initialized, @BeforeDestroyed and @Destroyed events for custom scope")
    void shouldFireCustomScopeLifecycleEvents() {
        CustomScopeLifecycleRecorder.reset();
        MyTestCustomContext context = new MyTestCustomContext();
        Syringe syringe = newSyringe(
                context,
                CustomScopeLifecycleObserver.class
        );
        context.setBeanManager(syringe.getBeanManager());

        context.activate("custom-payload");
        context.deactivate("custom-payload");

        List<String> events = CustomScopeLifecycleRecorder.events();
        assertEquals(3, events.size());
        assertEquals("initialized:custom-payload", events.get(0));
        assertEquals("before-destroyed:custom-payload", events.get(1));
        assertEquals("destroyed:custom-payload", events.get(2));
    }

    @Test
    @DisplayName("6.7 - Build compatible extensions can define custom context classes for custom scopes")
    void shouldAllowExtensionsToDefineCustomContextsForCustomScopes() {
        CustomScopeContextExtension.reset();

        Syringe syringe = new Syringe(
                new InMemoryMessageHandler(),
                MyCustomScopedService.class
        );
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.addExtension(CustomScopeContextExtension.class.getName());
        syringe.setup();

        MyTestCustomContext context = CustomScopeContextExtension.registeredContext();
        assertNotNull(context);
        context.setBeanManager(syringe.getBeanManager());

        BeanManager beanManager = syringe.getBeanManager();
        Context cdiContext = beanManager.getContext(MyTestCustomScoped.class);
        Bean<MyCustomScopedService> bean = resolveBean(beanManager, MyCustomScopedService.class);
        context.activate("ext-ctx");
        try {
            assertNotNull(cdiContext.get(bean, beanManager.createCreationalContext(bean)));
        } finally {
            context.deactivate("ext-ctx");
        }
    }

    @Test
    @DisplayName("6.7 - Build compatible extensions may define custom context classes for built-in scopes")
    void shouldAllowCustomContextsForBuiltInScopes() {
        Syringe syringe = new Syringe(
                new InMemoryMessageHandler(),
                BuiltInScopeMarker.class
        );
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.addExtension(InvalidBuiltInScopeContextExtension.class.getName());

        assertDoesNotThrow(syringe::setup);
    }

    @Test
    @DisplayName("6.7 - When custom context is destroyed, it destroys contextual instances created in that context")
    void shouldDestroyCustomScopeInstancesWhenContextIsDestroyed() {
        MyCustomScopedService.resetDestroyedCount();
        MyTestCustomContext context = new MyTestCustomContext();
        Syringe syringe = newSyringe(
                context,
                MyCustomScopedService.class
        );
        context.setBeanManager(syringe.getBeanManager());
        BeanManager beanManager = syringe.getBeanManager();
        Context cdiContext = beanManager.getContext(MyTestCustomScoped.class);
        Bean<MyCustomScopedService> bean = resolveBean(beanManager, MyCustomScopedService.class);

        context.activate("destroy-cycle");
        cdiContext.get(bean, beanManager.createCreationalContext(bean));
        context.deactivate("destroy-cycle");

        assertTrue(MyCustomScopedService.destroyedCount() >= 1);
    }

    private Syringe newSyringe(MyTestCustomContext customContext, Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.registerCustomContext(MyTestCustomScoped.class, customContext);
        syringe.setup();
        return syringe;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> Bean<T> resolveBean(BeanManager beanManager, Class<T> beanType) {
        Set<Bean<?>> beans = beanManager.getBeans(beanType);
        return (Bean<T>) beanManager.resolve((Set) beans);
    }

    public static class CustomScopeContextExtension implements Extension {
        private static volatile MyTestCustomContext REGISTERED_CONTEXT;

        static void reset() {
            REGISTERED_CONTEXT = null;
        }

        static MyTestCustomContext registeredContext() {
            return REGISTERED_CONTEXT;
        }

        public void afterBeanDiscovery(@Observes AfterBeanDiscovery afterBeanDiscovery) {
            MyTestCustomContext context = new MyTestCustomContext();
            REGISTERED_CONTEXT = context;
            afterBeanDiscovery.addContext(context);
        }
    }

    public static class InvalidBuiltInScopeContextExtension implements Extension {
        public void afterBeanDiscovery(@Observes AfterBeanDiscovery afterBeanDiscovery) {
            afterBeanDiscovery.addContext(new InvalidRequestScopedCustomContext());
        }
    }

    public static class InvalidRequestScopedCustomContext extends MyTestCustomContext {
        @Override
        public Class<? extends java.lang.annotation.Annotation> getScope() {
            return RequestScoped.class;
        }
    }

    @ApplicationScoped
    public static class BuiltInScopeMarker {
    }

    @MyTestCustomScoped
    public static class MyCustomScopedService {
        private static int destroyed;
        private final String id = java.util.UUID.randomUUID().toString();

        static void resetDestroyedCount() {
            destroyed = 0;
        }

        static int destroyedCount() {
            return destroyed;
        }

        String id() {
            return id;
        }

        @PreDestroy
        void destroy() {
            destroyed++;
        }
    }

    @ApplicationScoped
    public static class CustomScopeLifecycleObserver {
        void onInitialized(@Observes @jakarta.enterprise.context.Initialized(MyTestCustomScoped.class) Object payload) {
            CustomScopeLifecycleRecorder.record("initialized:" + payload);
        }

        void onBeforeDestroyed(
                @Observes @jakarta.enterprise.context.BeforeDestroyed(MyTestCustomScoped.class) Object payload) {
            CustomScopeLifecycleRecorder.record("before-destroyed:" + payload);
        }

        void onDestroyed(@Observes @jakarta.enterprise.context.Destroyed(MyTestCustomScoped.class) Object payload) {
            CustomScopeLifecycleRecorder.record("destroyed:" + payload);
        }
    }

    public static class CustomScopeLifecycleRecorder {
        private static final List<String> EVENTS = new ArrayList<String>();

        static synchronized void reset() {
            EVENTS.clear();
        }

        static synchronized void record(String event) {
            EVENTS.add(event);
        }

        static synchronized List<String> events() {
            return new ArrayList<String>(EVENTS);
        }
    }
}
