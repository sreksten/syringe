package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter9.par96observablecontainerlifecycleevents;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.BeforeDestroyed;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Shutdown;
import jakarta.enterprise.event.Startup;
import jakarta.enterprise.inject.Any;
import jakarta.interceptor.Interceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("9.6 - Observable Container Lifecycle Events Test")
@Execution(ExecutionMode.SAME_THREAD)
public class ObservableContainerLifecycleEventsTest {

    @Test
    @DisplayName("9.6.1 - Container fires Startup synchronously after @Initialized(ApplicationScoped.class) and before request processing")
    void shouldFireStartupAfterApplicationInitializedAndBeforeRequests() {
        StartupRecorder.reset();
        Syringe syringe = newSyringe(StartupLifecycleOrderObserver.class);

        assertEquals(2, StartupRecorder.events().size());
        assertEquals("app-initialized", StartupRecorder.events().get(0));
        assertEquals("startup", StartupRecorder.events().get(1));

        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        beanManager.getContextManager().activateRequest();
        try {
            assertEquals(3, StartupRecorder.events().size());
            assertEquals("request-initialized", StartupRecorder.events().get(2));
        } finally {
            beanManager.getContextManager().deactivateRequest();
        }
    }

    @Test
    @DisplayName("9.6.1 - Startup event includes qualifier @Any")
    void shouldFireStartupWithAnyQualifier() {
        StartupRecorder.reset();
        newSyringe(StartupAnyObserver.class);

        assertEquals(1, StartupRecorder.count("startup-any"));
    }

    @Test
    @DisplayName("9.6.1 - Startup observers can use @Priority and lower values are invoked first")
    void shouldOrderStartupObserversByPriority() {
        StartupRecorder.reset();
        newSyringe(StartupPriorityObservers.class);

        assertEquals(2, StartupRecorder.events().size());
        assertEquals("startup-priority-low", StartupRecorder.events().get(0));
        assertEquals("startup-priority-high", StartupRecorder.events().get(1));
    }

    @Test
    @DisplayName("9.6.1 - Application must not manually fire Startup events")
    void shouldRejectManualStartupEventFiring() {
        Syringe syringe = newSyringe(StartupAnyObserver.class);

        assertThrows(IllegalArgumentException.class,
                () -> syringe.getBeanManager().getEvent().select(Startup.class).fire(new Startup()));
    }

    @Test
    @DisplayName("9.6.2 - Container fires Shutdown synchronously during shutdown and not later than @BeforeDestroyed(ApplicationScoped.class)")
    void shouldFireShutdownNotLaterThanApplicationBeforeDestroyed() {
        StartupRecorder.reset();
        Syringe syringe = newSyringe(ShutdownLifecycleOrderObserver.class);

        syringe.shutdown();

        assertEquals(2, StartupRecorder.events().size());
        assertEquals("shutdown", StartupRecorder.events().get(0));
        assertEquals("app-before-destroyed", StartupRecorder.events().get(1));
    }

    @Test
    @DisplayName("9.6.2 - Shutdown event includes qualifier @Any")
    void shouldFireShutdownWithAnyQualifier() {
        StartupRecorder.reset();
        Syringe syringe = newSyringe(ShutdownAnyObserver.class);

        syringe.shutdown();
        assertEquals(1, StartupRecorder.count("shutdown-any"));
    }

    @Test
    @DisplayName("9.6.2 - Shutdown observers can use @Priority and lower values are invoked first")
    void shouldOrderShutdownObserversByPriority() {
        StartupRecorder.reset();
        Syringe syringe = newSyringe(ShutdownPriorityObservers.class);

        syringe.shutdown();
        assertEquals(2, StartupRecorder.events().size());
        assertEquals("shutdown-priority-low", StartupRecorder.events().get(0));
        assertEquals("shutdown-priority-high", StartupRecorder.events().get(1));
    }

    @Test
    @DisplayName("9.6.2 - Application must not manually fire Shutdown events")
    void shouldRejectManualShutdownEventFiring() {
        Syringe syringe = newSyringe(ShutdownAnyObserver.class);

        assertThrows(IllegalArgumentException.class,
                () -> syringe.getBeanManager().getEvent().select(Shutdown.class).fire(new Shutdown()));
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        Set<Class<?>> included = new HashSet<Class<?>>(Arrays.asList(beanClasses));
        Class<?>[] allFixtures = new Class<?>[]{
                StartupLifecycleOrderObserver.class,
                StartupAnyObserver.class,
                StartupPriorityObservers.class,
                ShutdownLifecycleOrderObserver.class,
                ShutdownAnyObserver.class,
                ShutdownPriorityObservers.class
        };
        for (Class<?> fixture : allFixtures) {
            if (!included.contains(fixture)) {
                syringe.exclude(fixture);
            }
        }
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();
        return syringe;
    }

    @ApplicationScoped
    public static class StartupLifecycleOrderObserver {
        void onAppInitialized(@Observes @Initialized(ApplicationScoped.class) Object event) {
            StartupRecorder.record("app-initialized");
        }

        void onStartup(@Observes Startup event) {
            StartupRecorder.record("startup");
        }

        void onRequestInitialized(@Observes @Initialized(RequestScoped.class) Object event) {
            StartupRecorder.record("request-initialized");
        }
    }

    @ApplicationScoped
    public static class StartupAnyObserver {
        void onStartupAny(@Observes @Any Startup event) {
            StartupRecorder.record("startup-any");
        }
    }

    @ApplicationScoped
    public static class StartupPriorityObservers {
        void low(@Observes @Priority(Interceptor.Priority.APPLICATION) Startup event) {
            StartupRecorder.record("startup-priority-low");
        }

        void high(@Observes @Priority(Interceptor.Priority.APPLICATION + 100) Startup event) {
            StartupRecorder.record("startup-priority-high");
        }
    }

    @ApplicationScoped
    public static class ShutdownLifecycleOrderObserver {
        void onShutdown(@Observes Shutdown event) {
            StartupRecorder.record("shutdown");
        }

        void onAppBeforeDestroyed(@Observes @BeforeDestroyed(ApplicationScoped.class) Object event) {
            StartupRecorder.record("app-before-destroyed");
        }
    }

    @ApplicationScoped
    public static class ShutdownAnyObserver {
        void onShutdownAny(@Observes @Any Shutdown event) {
            StartupRecorder.record("shutdown-any");
        }
    }

    @ApplicationScoped
    public static class ShutdownPriorityObservers {
        void low(@Observes @Priority(Interceptor.Priority.APPLICATION) Shutdown event) {
            StartupRecorder.record("shutdown-priority-low");
        }

        void high(@Observes @Priority(Interceptor.Priority.APPLICATION + 100) Shutdown event) {
            StartupRecorder.record("shutdown-priority-high");
        }
    }

    public static class StartupRecorder {
        private static final List<String> EVENTS = new ArrayList<String>();

        static synchronized void reset() {
            EVENTS.clear();
        }

        static synchronized void record(String value) {
            EVENTS.add(value);
        }

        static synchronized int count(String value) {
            int c = 0;
            for (String current : EVENTS) {
                if (value.equals(current)) {
                    c++;
                }
            }
            return c;
        }

        static synchronized List<String> events() {
            return new ArrayList<String>(EVENTS);
        }
    }
}
