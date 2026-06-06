package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter26;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Destroyed;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("26 - Scopes and contexts in Java SE")
public class ScopesAndContextsInJavaSETest {

    @Test
    @DisplayName("26.1.1 - Application scope is active during method invocation in Java SE")
    void shouldHaveApplicationContextActiveDuringMethodInvocation() {
        try (SeContainer container = SeContainerInitializer.newInstance()
                .disableDiscovery()
                .addBeanClasses(Chapter26_1ApplicationContextProbe.class)
                .initialize()) {
            Chapter26_1ApplicationContextProbe probe = container.select(Chapter26_1ApplicationContextProbe.class).get();
            assertTrue(probe.isApplicationContextActive());
        }
    }

    @Test
    @DisplayName("26.1.1 - Application context is shared between method invocations in the same container")
    void shouldShareApplicationContextWithinSingleContainer() {
        try (SeContainer container = SeContainerInitializer.newInstance()
                .disableDiscovery()
                .addBeanClasses(Chapter26_1ApplicationCounter.class)
                .initialize()) {
            Chapter26_1ApplicationCounter firstLookup = container.select(Chapter26_1ApplicationCounter.class).get();
            Chapter26_1ApplicationCounter secondLookup = container.select(Chapter26_1ApplicationCounter.class).get();

            assertEquals(1, firstLookup.incrementAndGet());
            assertEquals(2, secondLookup.incrementAndGet());
        }
    }

    @Test
    @DisplayName("26.1.1 - Application context is destroyed when container is shut down")
    void shouldDestroyApplicationContextOnShutdown() {
        Chapter26_1DestroyRecorder.reset();
        SeContainer container = SeContainerInitializer.newInstance()
                .disableDiscovery()
                .addBeanClasses(Chapter26_1ApplicationDestroyableBean.class)
                .initialize();

        // Ensure an @ApplicationScoped contextual instance exists.
        container.select(Chapter26_1ApplicationDestroyableBean.class).get().ping();
        container.close();

        assertTrue(Chapter26_1DestroyRecorder.destroyed);
    }

    @Test
    @DisplayName("26.1.1 - Application context lifecycle events in Java SE carry Object payload")
    void shouldFireApplicationLifecycleEventsWithObjectPayload() {
        Chapter26_1PayloadRecorder.reset();
        SeContainer container = SeContainerInitializer.newInstance()
                .disableDiscovery()
                .addBeanClasses(Chapter26_1ApplicationPayloadObserver.class)
                .initialize();

        assertTrue(Chapter26_1PayloadRecorder.initializedObserved);
        assertNotNull(Chapter26_1PayloadRecorder.initializedPayload);

        container.close();

        assertTrue(Chapter26_1PayloadRecorder.destroyedObserved);
        assertNotNull(Chapter26_1PayloadRecorder.destroyedPayload);
    }

    @ApplicationScoped
    public static class Chapter26_1ApplicationContextProbe {
        @Inject
        BeanManager beanManager;

        public boolean isApplicationContextActive() {
            return beanManager.getContext(ApplicationScoped.class).isActive();
        }
    }

    @ApplicationScoped
    public static class Chapter26_1ApplicationCounter {
        private int count;

        public int incrementAndGet() {
            count++;
            return count;
        }
    }

    @ApplicationScoped
    public static class Chapter26_1ApplicationDestroyableBean {
        public String ping() {
            return "ok";
        }

        @PreDestroy
        void onDestroy() {
            Chapter26_1DestroyRecorder.destroyed = true;
        }
    }

    public static class Chapter26_1DestroyRecorder {
        static boolean destroyed;

        static void reset() {
            destroyed = false;
        }
    }

    @ApplicationScoped
    public static class Chapter26_1ApplicationPayloadObserver {
        void onInitialized(@Observes @Initialized(ApplicationScoped.class) Object payload) {
            Chapter26_1PayloadRecorder.initializedObserved = true;
            Chapter26_1PayloadRecorder.initializedPayload = payload;
        }

        void onDestroyed(@Observes @Destroyed(ApplicationScoped.class) Object payload) {
            Chapter26_1PayloadRecorder.destroyedObserved = true;
            Chapter26_1PayloadRecorder.destroyedPayload = payload;
        }
    }

    public static class Chapter26_1PayloadRecorder {
        static boolean initializedObserved;
        static boolean destroyedObserved;
        static Object initializedPayload;
        static Object destroyedPayload;

        static void reset() {
            initializedObserved = false;
            destroyedObserved = false;
            initializedPayload = null;
            destroyedPayload = null;
        }
    }
}
