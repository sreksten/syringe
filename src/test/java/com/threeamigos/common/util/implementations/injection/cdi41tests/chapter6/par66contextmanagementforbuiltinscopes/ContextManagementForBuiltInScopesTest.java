package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par66contextmanagementforbuiltinscopes;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.BeforeDestroyed;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.ConversationScoped;
import jakarta.enterprise.context.Destroyed;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("6.6 - Context Management for Built-In Scopes Test")
@Execution(ExecutionMode.SAME_THREAD)
public class ContextManagementForBuiltInScopesTest {

    @Test
    @DisplayName("6.6 - Container provides Context implementations for all CDI built-in scopes")
    void shouldProvideContextImplementationsForAllBuiltInScopes() {
        Syringe syringe = newSyringe(
                NormalScopePropagationProbe.class,
                RequestScopedMarker.class,
                SessionScopedMarker.class,
                ConversationScopedMarker.class
        );
        BeanManager beanManager = syringe.getBeanManager();

        Context applicationContext = beanManager.getContext(ApplicationScoped.class);
        Context requestContext = beanManager.getContext(RequestScoped.class);
        Context sessionContext = beanManager.getContext(SessionScoped.class);
        Context conversationContext = beanManager.getContext(ConversationScoped.class);

        assertNotNull(applicationContext);
        assertNotNull(requestContext);
        assertNotNull(sessionContext);
        assertNotNull(conversationContext);

        assertEquals(ApplicationScoped.class, applicationContext.getScope());
        assertEquals(RequestScoped.class, requestContext.getScope());
        assertEquals(SessionScoped.class, sessionContext.getScope());
        assertEquals(ConversationScoped.class, conversationContext.getScope());
    }

    @Test
    @DisplayName("6.6 - Built-in normal scope contexts propagate across local synchronous Java method calls")
    void shouldPropagateNormalScopeContextsAcrossLocalSynchronousCalls() {
        Syringe syringe = newSyringe(
                NormalScopePropagationProbe.class,
                RequestScopedMarker.class,
                SessionScopedMarker.class,
                ConversationScopedMarker.class
        );
        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        NormalScopePropagationProbe probe = syringe.inject(NormalScopePropagationProbe.class);

        beanManager.getContextManager().activateRequest();
        beanManager.getContextManager().activateSession("session-6-6");
        beanManager.getContextManager().beginConversation("conversation-6-6");
        try {
            String requestIdFirst = probe.requestIdThroughLocalCalls();
            String requestIdSecond = probe.requestIdThroughLocalCalls();
            assertEquals(requestIdFirst, requestIdSecond);

            String sessionIdFirst = probe.sessionIdThroughLocalCalls();
            String sessionIdSecond = probe.sessionIdThroughLocalCalls();
            assertEquals(sessionIdFirst, sessionIdSecond);

            String conversationIdFirst = probe.conversationIdThroughLocalCalls();
            String conversationIdSecond = probe.conversationIdThroughLocalCalls();
            assertEquals(conversationIdFirst, conversationIdSecond);
        } finally {
            beanManager.getContextManager().endConversation("conversation-6-6");
            beanManager.getContextManager().deactivateSession();
            beanManager.getContextManager().deactivateRequest();
        }
    }

    @Test
    @DisplayName("6.6 - Built-in normal scope contexts do not propagate to asynchronous processes")
    void shouldNotPropagateNormalScopeContextsToAsynchronousProcesses() throws Exception {
        Syringe syringe = newSyringe(
                NormalScopePropagationProbe.class,
                RequestScopedMarker.class,
                SessionScopedMarker.class,
                ConversationScopedMarker.class
        );
        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        NormalScopePropagationProbe probe = syringe.inject(NormalScopePropagationProbe.class);

        beanManager.getContextManager().activateRequest();
        beanManager.getContextManager().activateSession("session-6-6-async");
        beanManager.getContextManager().beginConversation("conversation-6-6-async");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            assertAsyncInvocationFailsWithInactiveContext(executor, new Callable<String>() {
                @Override
                public String call() {
                    return probe.requestIdThroughLocalCalls();
                }
            });
            assertAsyncInvocationFailsWithInactiveContext(executor, new Callable<String>() {
                @Override
                public String call() {
                    return probe.sessionIdThroughLocalCalls();
                }
            });
            assertAsyncInvocationFailsWithInactiveContext(executor, new Callable<String>() {
                @Override
                public String call() {
                    return probe.conversationIdThroughLocalCalls();
                }
            });
        } finally {
            executor.shutdownNow();
            beanManager.getContextManager().endConversation("conversation-6-6-async");
            beanManager.getContextManager().deactivateSession();
            beanManager.getContextManager().deactivateRequest();
        }
    }

    @Test
    @DisplayName("6.6.1 - Request context lifecycle events @Initialized, @BeforeDestroyed and @Destroyed are synchronously fired in order")
    void shouldFireRequestLifecycleEventsInOrder() {
        RequestLifecycleRecorder.reset();
        Syringe syringe = newSyringe(RequestLifecycleObserver.class);
        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();

        beanManager.getContextManager().activateRequest();
        beanManager.getContextManager().deactivateRequest();

        List<String> events = RequestLifecycleRecorder.events();
        assertEquals(3, events.size());
        assertEquals("initialized", events.get(0));
        assertEquals("before-destroyed", events.get(1));
        assertEquals("destroyed", events.get(2));
    }

    @Test
    @DisplayName("6.6.1 - Request context is active during asynchronous observer notification and destroyed after invocation completes")
    void shouldActivateRequestContextForAsyncObserverAndDestroyAfterCompletion() throws Exception {
        RequestLifecycleRecorder.reset();
        Syringe syringe = newSyringe(
                AsyncLifecycleObserver.class,
                AsyncProbeEvent.class,
                RequestScopedMarker.class
        );
        BeanManager beanManager = syringe.getBeanManager();

        beanManager.getEvent().select(AsyncProbeEvent.class)
                .fireAsync(new AsyncProbeEvent("async-6-6-1"))
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);

        String observedRequestId = RequestLifecycleRecorder.lastAsyncObservedRequestId();
        assertNotNull(observedRequestId);
        assertTrue(RequestLifecycleRecorder.destroyedRequestIds().contains(observedRequestId));
    }

    @Test
    @DisplayName("6.6.1 - Request context is active during @PostConstruct callback and destroyed afterwards if it did not already exist")
    void shouldActivateRequestContextDuringPostConstructAndDestroyWhenTemporarilyCreated() {
        RequestLifecycleRecorder.reset();
        Syringe syringe = newSyringe(
                PostConstructRequestContextProbe.class,
                RequestScopedMarker.class
        );
        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        Bean<PostConstructRequestContextProbe> probeBean = resolveBean(beanManager, PostConstructRequestContextProbe.class);
        PostConstructRequestContextProbe probe = (PostConstructRequestContextProbe) beanManager.getReference(
                probeBean,
                PostConstructRequestContextProbe.class,
                beanManager.createCreationalContext(probeBean)
        );

        String idUsedInPostConstruct = probe.createdWithRequestId();
        assertNotNull(idUsedInPostConstruct);
        assertFalse(beanManager.getContextManager().getContext(RequestScoped.class).isActive());
        assertTrue(RequestLifecycleRecorder.destroyedRequestIds().contains(idUsedInPostConstruct));
    }

    @Test
    @DisplayName("6.6.1 - Request context remains active after @PostConstruct when it already existed and is destroyed on normal deactivation")
    void shouldKeepExistingRequestContextAfterPostConstruct() {
        RequestLifecycleRecorder.reset();
        Syringe syringe = newSyringe(
                PostConstructRequestContextProbe.class,
                RequestScopedMarker.class,
                RequestContextControllerConsumer.class
        );
        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        Bean<RequestContextController> controllerBean = resolveBean(beanManager, RequestContextController.class);
        RequestContextController controller = (RequestContextController) beanManager.getReference(
                controllerBean,
                RequestContextController.class,
                beanManager.createCreationalContext(controllerBean)
        );

        controller.activate();
        try {
            Bean<PostConstructRequestContextProbe> probeBean = resolveBean(beanManager, PostConstructRequestContextProbe.class);
            PostConstructRequestContextProbe probe = (PostConstructRequestContextProbe) beanManager.getReference(
                    probeBean,
                    PostConstructRequestContextProbe.class,
                    beanManager.createCreationalContext(probeBean)
            );
            String idUsedInPostConstruct = probe.createdWithRequestId();
            assertNotNull(idUsedInPostConstruct);
            assertTrue(beanManager.getContextManager().getContext(RequestScoped.class).isActive());
            assertFalse(RequestLifecycleRecorder.destroyedRequestIds().contains(idUsedInPostConstruct));
        } finally {
            controller.deactivate();
        }
        assertTrue(RequestLifecycleRecorder.events().contains("destroyed"));
    }

    @Test
    @DisplayName("6.6.3 - Application context lifecycle events @Initialized, @BeforeDestroyed and @Destroyed are synchronously fired in order")
    void shouldFireApplicationLifecycleEventsInOrder() {
        ApplicationLifecycleRecorder.reset();
        Syringe syringe = newSyringe(ApplicationLifecycleObserver.class);

        assertEquals(1, ApplicationLifecycleRecorder.events().size());
        assertEquals("initialized", ApplicationLifecycleRecorder.events().get(0));

        syringe.shutdown();

        List<String> events = ApplicationLifecycleRecorder.events();
        assertEquals(3, events.size());
        assertEquals("initialized", events.get(0));
        assertEquals("before-destroyed", events.get(1));
        assertEquals("destroyed", events.get(2));
    }

    @Test
    @DisplayName("6.6.3 - Application context lifecycle events are fired synchronously on the calling thread")
    void shouldFireApplicationLifecycleEventsSynchronously() {
        ApplicationLifecycleRecorder.reset();
        long setupThreadId = Thread.currentThread().getId();
        Syringe syringe = newSyringe(ApplicationLifecycleObserver.class);

        assertEquals(setupThreadId, ApplicationLifecycleRecorder.initializedThreadId());

        long shutdownThreadId = Thread.currentThread().getId();
        syringe.shutdown();
        assertEquals(shutdownThreadId, ApplicationLifecycleRecorder.beforeDestroyedThreadId());
        assertEquals(shutdownThreadId, ApplicationLifecycleRecorder.destroyedThreadId());
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();
        return syringe;
    }

    private boolean containsCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> Bean<T> resolveBean(BeanManager beanManager, Class<T> beanType) {
        java.util.Set<Bean<?>> beans = beanManager.getBeans(beanType);
        return (Bean<T>) beanManager.resolve((java.util.Set) beans);
    }

    private void assertAsyncInvocationFailsWithInactiveContext(ExecutorService executor, Callable<String> task)
            throws InterruptedException {
        Future<String> future = executor.submit(task);
        ExecutionException executionException = assertThrows(ExecutionException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() throws Throwable {
                future.get();
            }
        });
        assertTrue(
                containsCause(executionException, ContextNotActiveException.class) ||
                containsCause(executionException, IllegalStateException.class)
        );
    }

    @ApplicationScoped
    public static class NormalScopePropagationProbe {
        @Inject
        RequestScopedMarker requestScopedMarker;

        @Inject
        SessionScopedMarker sessionScopedMarker;

        @Inject
        ConversationScopedMarker conversationScopedMarker;

        public String requestIdThroughLocalCalls() {
            return requestLevelOne();
        }

        public String sessionIdThroughLocalCalls() {
            return sessionLevelOne();
        }

        public String conversationIdThroughLocalCalls() {
            return conversationLevelOne();
        }

        private String requestLevelOne() {
            return requestLevelTwo();
        }

        private String requestLevelTwo() {
            return requestScopedMarker.id();
        }

        private String sessionLevelOne() {
            return sessionLevelTwo();
        }

        private String sessionLevelTwo() {
            return sessionScopedMarker.id();
        }

        private String conversationLevelOne() {
            return conversationLevelTwo();
        }

        private String conversationLevelTwo() {
            return conversationScopedMarker.id();
        }
    }

    @RequestScoped
    public static class RequestScopedMarker {
        private final String id = UUID.randomUUID().toString();

        public String id() {
            return id;
        }

        @PreDestroy
        void preDestroy() {
            RequestLifecycleRecorder.markDestroyedRequestId(id);
        }
    }

    @SessionScoped
    public static class SessionScopedMarker implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String id = UUID.randomUUID().toString();

        public String id() {
            return id;
        }
    }

    @ConversationScoped
    public static class ConversationScopedMarker implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String id = UUID.randomUUID().toString();

        public String id() {
            return id;
        }
    }

    @ApplicationScoped
    public static class RequestLifecycleObserver {
        void onInitialized(@Observes @Initialized(RequestScoped.class) Object payload) {
            RequestLifecycleRecorder.addEvent("initialized");
        }

        void onBeforeDestroyed(@Observes @BeforeDestroyed(RequestScoped.class) Object payload) {
            RequestLifecycleRecorder.addEvent("before-destroyed");
        }

        void onDestroyed(@Observes @Destroyed(RequestScoped.class) Object payload) {
            RequestLifecycleRecorder.addEvent("destroyed");
        }
    }

    public static class AsyncProbeEvent {
        private final String payload;

        public AsyncProbeEvent(String payload) {
            this.payload = payload;
        }

        public String payload() {
            return payload;
        }
    }

    @ApplicationScoped
    public static class AsyncLifecycleObserver {
        void onAsync(@ObservesAsync AsyncProbeEvent event, RequestScopedMarker requestScopedMarker) {
            RequestLifecycleRecorder.setLastAsyncObservedRequestId(requestScopedMarker.id());
        }
    }

    @ApplicationScoped
    public static class PostConstructRequestContextProbe {
        @Inject
        RequestScopedMarker requestScopedMarker;

        private String createdWithRequestId;

        @PostConstruct
        void init() {
            createdWithRequestId = requestScopedMarker.id();
            RequestLifecycleRecorder.setLastPostConstructRequestId(createdWithRequestId);
        }

        public String createdWithRequestId() {
            return createdWithRequestId;
        }
    }

    @ApplicationScoped
    public static class RequestContextControllerConsumer {
        @Inject
        Instance<RequestContextController> controllers;

        RequestContextController newController() {
            return controllers.get();
        }
    }

    public static class RequestLifecycleRecorder {
        private static final List<String> EVENTS = new ArrayList<String>();
        private static final List<String> DESTROYED_REQUEST_IDS = new ArrayList<String>();
        private static String lastAsyncObservedRequestId;
        private static String lastPostConstructRequestId;

        static synchronized void reset() {
            EVENTS.clear();
            DESTROYED_REQUEST_IDS.clear();
            lastAsyncObservedRequestId = null;
            lastPostConstructRequestId = null;
        }

        static synchronized void addEvent(String event) {
            EVENTS.add(event);
        }

        static synchronized List<String> events() {
            return new ArrayList<String>(EVENTS);
        }

        static synchronized void setLastAsyncObservedRequestId(String id) {
            lastAsyncObservedRequestId = id;
        }

        static synchronized String lastAsyncObservedRequestId() {
            return lastAsyncObservedRequestId;
        }

        static synchronized void setLastPostConstructRequestId(String id) {
            lastPostConstructRequestId = id;
        }

        static synchronized String lastPostConstructRequestId() {
            return lastPostConstructRequestId;
        }

        static synchronized void markDestroyedRequestId(String id) {
            DESTROYED_REQUEST_IDS.add(id);
        }

        static synchronized List<String> destroyedRequestIds() {
            return new ArrayList<String>(DESTROYED_REQUEST_IDS);
        }
    }

    @ApplicationScoped
    public static class ApplicationLifecycleObserver {
        void onInitialized(@Observes @Initialized(ApplicationScoped.class) Object payload) {
            ApplicationLifecycleRecorder.recordInitialized();
        }

        void onBeforeDestroyed(@Observes @BeforeDestroyed(ApplicationScoped.class) Object payload) {
            ApplicationLifecycleRecorder.recordBeforeDestroyed();
        }

        void onDestroyed(@Observes @Destroyed(ApplicationScoped.class) Object payload) {
            ApplicationLifecycleRecorder.recordDestroyed();
        }
    }

    public static class ApplicationLifecycleRecorder {
        private static final List<String> EVENTS = new ArrayList<String>();
        private static Long initializedThreadId;
        private static Long beforeDestroyedThreadId;
        private static Long destroyedThreadId;

        static synchronized void reset() {
            EVENTS.clear();
            initializedThreadId = null;
            beforeDestroyedThreadId = null;
            destroyedThreadId = null;
        }

        static synchronized void recordInitialized() {
            EVENTS.add("initialized");
            initializedThreadId = Thread.currentThread().getId();
        }

        static synchronized void recordBeforeDestroyed() {
            EVENTS.add("before-destroyed");
            beforeDestroyedThreadId = Thread.currentThread().getId();
        }

        static synchronized void recordDestroyed() {
            EVENTS.add("destroyed");
            destroyedThreadId = Thread.currentThread().getId();
        }

        static synchronized List<String> events() {
            return new ArrayList<String>(EVENTS);
        }

        static synchronized Long initializedThreadId() {
            return initializedThreadId;
        }

        static synchronized Long beforeDestroyedThreadId() {
            return beforeDestroyedThreadId;
        }

        static synchronized Long destroyedThreadId() {
            return destroyedThreadId;
        }
    }
}
