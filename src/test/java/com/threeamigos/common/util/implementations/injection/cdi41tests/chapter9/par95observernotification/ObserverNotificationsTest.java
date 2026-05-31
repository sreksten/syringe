package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter9.par95observernotification;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.discovery.NonPortableBehaviourException;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.injection.util.tx.TransactionServices;
import com.threeamigos.common.util.implementations.injection.util.tx.TransactionSynchronizationCallbacks;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.event.ObserverException;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.interceptor.Interceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("9.5 - Observer Notifications Test")
@Execution(ExecutionMode.SAME_THREAD)
public class ObserverNotificationsTest {

    @Test
    @DisplayName("9.5 - Container honors observer priority ordering")
    void shouldHonorObserverPriorityOrdering() {
        Recorder.reset();
        Syringe syringe = newSyringe(PriorityObservers.class);

        syringe.getBeanManager().getEvent().select(SimpleEvent.class).fire(new SimpleEvent("p"));

        assertEquals(2, Recorder.values().size());
        assertEquals("priority-10", Recorder.values().get(0));
        assertEquals("priority-100", Recorder.values().get(1));
    }

    @Test
    @DisplayName("9.5 - Transactional observers are registered and invoked in the appropriate transaction completion phases")
    void shouldRegisterTransactionalObserversAndInvokeAtProperPhases() throws Exception {
        Recorder.reset();
        Syringe syringe = newSyringe(TransactionalPhaseObserver.class);
        ControlledTransactionServices tx = new ControlledTransactionServices(true, false);
        setTransactionServices(syringe, tx);

        syringe.getBeanManager().getEvent().select(SimpleEvent.class).fire(new SimpleEvent("tx"));

        assertEquals(1, tx.registerCount);
        assertEquals(1, Recorder.count("tx-in-progress"));
        assertEquals(0, Recorder.count("tx-before"));
        assertEquals(0, Recorder.count("tx-after-completion"));
        assertEquals(0, Recorder.count("tx-after-success"));
        assertEquals(0, Recorder.count("tx-after-failure"));

        tx.beforeCompletion();
        assertEquals(1, Recorder.count("tx-before"));

        tx.afterCompletion(true);
        assertEquals(1, Recorder.count("tx-after-completion"));
        assertEquals(1, Recorder.count("tx-after-success"));
        assertEquals(0, Recorder.count("tx-after-failure"));
    }

    @Test
    @DisplayName("9.5 - If there is no active context for the observer bean scope, immediate observer notification is skipped")
    void shouldSkipImmediateObserverWhenScopeContextIsInactive() {
        Recorder.reset();
        Syringe syringe = newSyringe(InactiveScopeImmediateObserver.class);

        syringe.getBeanManager().getEvent().select(SimpleEvent.class).fire(new SimpleEvent("inactive-immediate"));

        assertEquals(0, Recorder.count("inactive-immediate-observer"));
    }

    @Test
    @DisplayName("9.5 - If there is no active context for the observer bean scope, transactional observer notification is skipped")
    void shouldSkipTransactionalObserverWhenScopeContextIsInactive() throws Exception {
        Recorder.reset();
        Syringe syringe = newSyringe(InactiveScopeTransactionalObserver.class);
        ControlledTransactionServices tx = new ControlledTransactionServices(true, false);
        setTransactionServices(syringe, tx);

        syringe.getBeanManager().getEvent().select(SimpleEvent.class).fire(new SimpleEvent("inactive-tx"));
        tx.beforeCompletion();
        tx.afterCompletion(true);

        assertEquals(0, Recorder.count("inactive-transactional-observer"));
    }

    @Test
    @DisplayName("9.5 - Exception from non-transactional synchronous observer aborts event processing and is rethrown")
    void shouldAbortEventAndRethrowRuntimeExceptionForSynchronousObserver() {
        Recorder.reset();
        Syringe syringe = newSyringe(RuntimeThrowingObserver.class);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> syringe.getBeanManager().getEvent().select(SimpleEvent.class).fire(new SimpleEvent("runtime")));

        assertEquals("runtime-failure", thrown.getMessage());
        assertEquals(0, Recorder.count("sync-after-runtime"));
    }

    @Test
    @DisplayName("9.5 - Checked exception from non-transactional synchronous observer is wrapped in ObserverException and aborts event processing")
    void shouldWrapCheckedExceptionInObserverExceptionForSynchronousObserver() {
        Recorder.reset();
        Syringe syringe = newSyringe(CheckedThrowingObserver.class);

        ObserverException thrown = assertThrows(ObserverException.class,
                () -> syringe.getBeanManager().getEvent().select(SimpleEvent.class).fire(new SimpleEvent("checked")));

        assertTrue(thrown.getCause() instanceof Exception);
        assertEquals("checked-failure", thrown.getCause().getMessage());
        assertEquals(0, Recorder.count("sync-after-checked"));
    }

    @Test
    @DisplayName("9.5 - Exception from transactional observer is caught and does not abort transaction phase notification")
    void shouldCatchTransactionalObserverExceptionAndContinuePhaseObservers() throws Exception {
        Recorder.reset();
        Syringe syringe = newSyringe(TransactionalExceptionObservers.class);
        ControlledTransactionServices tx = new ControlledTransactionServices(true, false);
        setTransactionServices(syringe, tx);

        syringe.getBeanManager().getEvent().select(SimpleEvent.class).fire(new SimpleEvent("tx-ex"));
        tx.afterCompletion(true);

        assertEquals(1, Recorder.count("tx-throws"));
        assertEquals(1, Recorder.count("tx-after-throws"));
    }

    @Test
    @DisplayName("9.5 - Exception from asynchronous observer does not abort notification of other asynchronous observers")
    void shouldContinueAsynchronousNotificationAfterObserverException() throws Exception {
        Recorder.reset();
        Syringe syringe = newSyringe(AsyncExceptionObservers.class);
        Event<SimpleEvent> events = syringe.getBeanManager().getEvent().select(SimpleEvent.class);

        try {
            events.fireAsync(new SimpleEvent("async-ex")).toCompletableFuture().get(5, TimeUnit.SECONDS);
        } catch (ExecutionException ignored) {
            // Completion may be exceptional depending on container-specific async exception policy.
        }

        assertEquals(1, Recorder.count("async-throws"));
        assertEquals(1, Recorder.count("async-after-throws"));
    }

    @Test
    @DisplayName("9.5.1 - If asynchronous observers throw exceptions fireAsync completes exceptionally with CompletionException containing suppressed observer exceptions")
    void shouldCompleteAsyncExceptionallyWithSuppressedObserverExceptions() {
        Recorder.reset();
        Syringe syringe = newSyringe(AsyncMultipleExceptionObservers.class);
        Event<SimpleEvent> events = syringe.getBeanManager().getEvent().select(SimpleEvent.class);

        CompletionException thrown = assertThrows(CompletionException.class,
                () -> events.fireAsync(new SimpleEvent("async-multi-ex"))
                        .toCompletableFuture().join());

        Throwable[] suppressed = thrown.getSuppressed();
        assertEquals(2, suppressed.length);
        assertTrue(containsMessage(suppressed, "async-first-failure"));
        assertTrue(containsMessage(suppressed, "async-second-failure"));
        assertEquals(1, Recorder.count("async-multi-first"));
        assertEquals(1, Recorder.count("async-multi-second"));
        assertEquals(1, Recorder.count("async-multi-third"));
    }

    @Test
    @DisplayName("9.5.1 - If no asynchronous observer throws exception fireAsync completion stage completes normally with the event object")
    void shouldCompleteAsyncNormallyWithEventObjectWhenNoObserverThrows() throws Exception {
        Recorder.reset();
        Syringe syringe = newSyringe(AsyncNoExceptionObserver.class);
        Event<SimpleEvent> events = syringe.getBeanManager().getEvent().select(SimpleEvent.class);

        SimpleEvent payload = new SimpleEvent("async-ok");
        SimpleEvent result = events.fireAsync(payload).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertSame(payload, result);
        assertEquals(1, Recorder.count("async-ok"));
    }

    @Test
    @DisplayName("9.5.2 - Observer priority declared on event parameter orders observers with smaller values first")
    void shouldOrderObserversByEventParameterPriority() {
        Recorder.reset();
        Syringe syringe = newSyringe(ParameterPriorityOrderedObservers.class);

        syringe.getBeanManager().getEvent().select(SimpleEvent.class).fire(new SimpleEvent("ordered"));

        assertEquals(2, Recorder.values().size());
        assertEquals("param-priority-low", Recorder.values().get(0));
        assertEquals("param-priority-high", Recorder.values().get(1));
    }

    @Test
    @DisplayName("9.5.2 - Default observer priority is Interceptor.Priority.APPLICATION + 500")
    void shouldApplyDefaultObserverPriorityWhenMissingPriorityAnnotation() {
        Recorder.reset();
        Syringe syringe = newSyringe(DefaultPriorityObservers.class);

        syringe.getBeanManager().getEvent().select(SimpleEvent.class).fire(new SimpleEvent("default-priority"));

        assertEquals(2, Recorder.values().size());
        assertEquals("default-explicit-application", Recorder.values().get(0));
        assertEquals("default-implicit", Recorder.values().get(1));
    }

    @Test
    @DisplayName("9.5.2 - More than one observer with same priority has undefined ordering but all are notified")
    void shouldNotifyAllObserversWithSamePriorityWithoutAssumingOrder() {
        Recorder.reset();
        Syringe syringe = newSyringe(SamePriorityObservers.class);

        syringe.getBeanManager().getEvent().select(SimpleEvent.class).fire(new SimpleEvent("same-priority"));

        assertEquals(1, Recorder.count("same-priority-a"));
        assertEquals(1, Recorder.count("same-priority-b"));
    }

    @Test
    @DisplayName("9.5.2 - Async observer event parameter annotated with @Priority is non-portable and causes NonPortableBehaviourException")
    void shouldRejectAsyncObserverEventParameterPriorityAsNonPortable() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), AsyncPriorityOnEventParameterObserver.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        assertThrows(NonPortableBehaviourException.class, syringe::setup);
    }

    @Test
    @DisplayName("9.5.3 - Non-transactional synchronous observer is invoked in the same transaction and lifecycle contexts as Event.fire")
    void shouldInvokeSynchronousObserverInSameLifecycleContextAsFire() {
        ContextInvocationRecorder.reset();
        Syringe syringe = newSyringe(SyncLifecycleObserver.class, RequestScopedMarker.class);
        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        beanManager.getContextManager().activateRequest();
        try {
            String firingMarkerId = getRequestScopedInstance(beanManager, RequestScopedMarker.class).getId();
            String firingThread = Thread.currentThread().getName();

            beanManager.getEvent().select(LifecycleEvent.class).fire(new LifecycleEvent("sync"));

            assertEquals(firingMarkerId, ContextInvocationRecorder.lastSyncMarkerId());
            assertEquals(firingThread, ContextInvocationRecorder.lastSyncThreadName());
        } finally {
            beanManager.getContextManager().deactivateRequest();
        }
    }

    @Test
    @DisplayName("9.5.3 - Asynchronous observer is invoked in new lifecycle contexts and built-in normal scopes do not propagate")
    void shouldInvokeAsyncObserverWithNewLifecycleContext() throws Exception {
        ContextInvocationRecorder.reset();
        Syringe syringe = newSyringe(AsyncLifecycleObserver.class, RequestScopedMarker.class);
        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        beanManager.getContextManager().activateRequest();
        try {
            String firingMarkerId = getRequestScopedInstance(beanManager, RequestScopedMarker.class).getId();
            String firingThread = Thread.currentThread().getName();

            beanManager.getEvent().select(LifecycleEvent.class)
                    .fireAsync(new LifecycleEvent("async"))
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);

            assertTrue(!firingMarkerId.equals(ContextInvocationRecorder.lastAsyncMarkerId()));
            assertTrue(!firingThread.equals(ContextInvocationRecorder.lastAsyncThreadName()));
        } finally {
            beanManager.getContextManager().deactivateRequest();
        }
    }

    @Test
    @DisplayName("9.5.3 - Before completion transactional observer is invoked with the same lifecycle contexts")
    void shouldInvokeBeforeCompletionTransactionalObserverWithSameLifecycleContexts() throws Exception {
        ContextInvocationRecorder.reset();
        Syringe syringe = newSyringe(BeforeCompletionLifecycleObserver.class, RequestScopedMarker.class);
        ControlledTransactionServices tx = new ControlledTransactionServices(true, false);
        setTransactionServices(syringe, tx);
        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        beanManager.getContextManager().activateRequest();
        try {
            String firingMarkerId = getRequestScopedInstance(beanManager, RequestScopedMarker.class).getId();

            beanManager.getEvent().select(LifecycleEvent.class).fire(new LifecycleEvent("before-completion"));
            tx.beforeCompletion();

            assertEquals(firingMarkerId, ContextInvocationRecorder.lastBeforeCompletionMarkerId());
        } finally {
            beanManager.getContextManager().deactivateRequest();
        }
    }

    @Test
    @DisplayName("9.5.3 - Non-before-completion transactional observer is invoked with the same lifecycle contexts as the transaction that completed")
    void shouldInvokeAfterCompletionTransactionalObserverWithSameLifecycleContexts() throws Exception {
        ContextInvocationRecorder.reset();
        Syringe syringe = newSyringe(AfterCompletionLifecycleObserver.class, RequestScopedMarker.class);
        ControlledTransactionServices tx = new ControlledTransactionServices(true, false);
        setTransactionServices(syringe, tx);
        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        beanManager.getContextManager().activateRequest();
        try {
            String firingMarkerId = getRequestScopedInstance(beanManager, RequestScopedMarker.class).getId();

            beanManager.getEvent().select(LifecycleEvent.class).fire(new LifecycleEvent("after-completion"));
            tx.afterCompletion(true);

            assertEquals(firingMarkerId, ContextInvocationRecorder.lastAfterCompletionMarkerId());
        } finally {
            beanManager.getContextManager().deactivateRequest();
        }
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        Set<Class<?>> included = new HashSet<Class<?>>(Arrays.asList(beanClasses));
        Class<?>[] allFixtures = new Class<?>[]{
                PriorityObservers.class,
                TransactionalPhaseObserver.class,
                InactiveScopeImmediateObserver.class,
                InactiveScopeTransactionalObserver.class,
                RuntimeThrowingObserver.class,
                CheckedThrowingObserver.class,
                TransactionalExceptionObservers.class,
                AsyncExceptionObservers.class,
                AsyncMultipleExceptionObservers.class,
                AsyncNoExceptionObserver.class,
                ParameterPriorityOrderedObservers.class,
                DefaultPriorityObservers.class,
                SamePriorityObservers.class,
                AsyncPriorityOnEventParameterObserver.class,
                RequestScopedMarker.class,
                SyncLifecycleObserver.class,
                AsyncLifecycleObserver.class,
                BeforeCompletionLifecycleObserver.class,
                AfterCompletionLifecycleObserver.class
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

    private void setTransactionServices(Syringe syringe, TransactionServices services) throws Exception {
        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        Object beanResolver = beanManager.getBeanResolver();
        Method setter = beanResolver.getClass().getDeclaredMethod("setTransactionServices", TransactionServices.class);
        setter.setAccessible(true);
        setter.invoke(beanResolver, services);
    }

    @ApplicationScoped
    public static class PriorityObservers {
        @Priority(100)
        void second(@Observes SimpleEvent event) {
            Recorder.record("priority-100");
        }

        @Priority(10)
        void first(@Observes SimpleEvent event) {
            Recorder.record("priority-10");
        }
    }

    @ApplicationScoped
    public static class TransactionalPhaseObserver {
        void inProgress(@Observes(during = TransactionPhase.IN_PROGRESS) SimpleEvent event) {
            Recorder.record("tx-in-progress");
        }

        void before(@Observes(during = TransactionPhase.BEFORE_COMPLETION) SimpleEvent event) {
            Recorder.record("tx-before");
        }

        void afterCompletion(@Observes(during = TransactionPhase.AFTER_COMPLETION) SimpleEvent event) {
            Recorder.record("tx-after-completion");
        }

        void afterSuccess(@Observes(during = TransactionPhase.AFTER_SUCCESS) SimpleEvent event) {
            Recorder.record("tx-after-success");
        }

        void afterFailure(@Observes(during = TransactionPhase.AFTER_FAILURE) SimpleEvent event) {
            Recorder.record("tx-after-failure");
        }
    }

    @RequestScoped
    public static class InactiveScopeImmediateObserver {
        void observe(@Observes SimpleEvent event) {
            Recorder.record("inactive-immediate-observer");
        }
    }

    @RequestScoped
    public static class InactiveScopeTransactionalObserver {
        void observe(@Observes(during = TransactionPhase.AFTER_COMPLETION) SimpleEvent event) {
            Recorder.record("inactive-transactional-observer");
        }
    }

    @ApplicationScoped
    public static class RuntimeThrowingObserver {
        @Priority(1)
        void throwsRuntime(@Observes SimpleEvent event) {
            throw new IllegalStateException("runtime-failure");
        }

        @Priority(100)
        void shouldNotRun(@Observes SimpleEvent event) {
            Recorder.record("sync-after-runtime");
        }
    }

    @ApplicationScoped
    public static class CheckedThrowingObserver {
        @Priority(1)
        void throwsChecked(@Observes SimpleEvent event) throws Exception {
            throw new Exception("checked-failure");
        }

        @Priority(100)
        void shouldNotRun(@Observes SimpleEvent event) {
            Recorder.record("sync-after-checked");
        }
    }

    @ApplicationScoped
    public static class TransactionalExceptionObservers {
        @Priority(1)
        void throwing(@Observes(during = TransactionPhase.AFTER_COMPLETION) SimpleEvent event) {
            Recorder.record("tx-throws");
            throw new IllegalStateException("tx-observer-failure");
        }

        @Priority(100)
        void after(@Observes(during = TransactionPhase.AFTER_COMPLETION) SimpleEvent event) {
            Recorder.record("tx-after-throws");
        }
    }

    @ApplicationScoped
    public static class AsyncExceptionObservers {
        @Priority(1)
        void throwing(@ObservesAsync SimpleEvent event) {
            Recorder.record("async-throws");
            throw new IllegalStateException("async-observer-failure");
        }

        @Priority(100)
        void after(@ObservesAsync SimpleEvent event) {
            Recorder.record("async-after-throws");
        }
    }

    @ApplicationScoped
    public static class AsyncMultipleExceptionObservers {
        @Priority(1)
        void first(@ObservesAsync SimpleEvent event) {
            Recorder.record("async-multi-first");
            throw new IllegalStateException("async-first-failure");
        }

        @Priority(10)
        void second(@ObservesAsync SimpleEvent event) {
            Recorder.record("async-multi-second");
            throw new IllegalArgumentException("async-second-failure");
        }

        @Priority(100)
        void third(@ObservesAsync SimpleEvent event) {
            Recorder.record("async-multi-third");
        }
    }

    @ApplicationScoped
    public static class AsyncNoExceptionObserver {
        void observe(@ObservesAsync SimpleEvent event) {
            Recorder.record("async-ok");
        }
    }

    @ApplicationScoped
    public static class ParameterPriorityOrderedObservers {
        void low(@Observes @Priority(Interceptor.Priority.APPLICATION) SimpleEvent event) {
            Recorder.record("param-priority-low");
        }

        void high(@Observes @Priority(Interceptor.Priority.APPLICATION + 100) SimpleEvent event) {
            Recorder.record("param-priority-high");
        }
    }

    @ApplicationScoped
    public static class DefaultPriorityObservers {
        void implicitDefault(@Observes SimpleEvent event) {
            Recorder.record("default-implicit");
        }

        void explicitApplication(@Observes @Priority(Interceptor.Priority.APPLICATION) SimpleEvent event) {
            Recorder.record("default-explicit-application");
        }
    }

    @ApplicationScoped
    public static class SamePriorityObservers {
        void a(@Observes @Priority(Interceptor.Priority.APPLICATION + 10) SimpleEvent event) {
            Recorder.record("same-priority-a");
        }

        void b(@Observes @Priority(Interceptor.Priority.APPLICATION + 10) SimpleEvent event) {
            Recorder.record("same-priority-b");
        }
    }

    @ApplicationScoped
    public static class AsyncPriorityOnEventParameterObserver {
        void invalid(@ObservesAsync @Priority(Interceptor.Priority.APPLICATION) SimpleEvent event) {
            Recorder.record("async-priority-invalid");
        }
    }

    @RequestScoped
    public static class RequestScopedMarker {
        final String id = UUID.randomUUID().toString();

        public String getId() {
            return id;
        }
    }

    @ApplicationScoped
    public static class SyncLifecycleObserver {
        void observe(@Observes LifecycleEvent event, RequestScopedMarker marker) {
            ContextInvocationRecorder.recordSync(marker.getId(), Thread.currentThread().getName());
        }
    }

    @ApplicationScoped
    public static class AsyncLifecycleObserver {
        void observe(@ObservesAsync LifecycleEvent event, RequestScopedMarker marker) {
            ContextInvocationRecorder.recordAsync(marker.getId(), Thread.currentThread().getName());
        }
    }

    @ApplicationScoped
    public static class BeforeCompletionLifecycleObserver {
        void observe(@Observes(during = TransactionPhase.BEFORE_COMPLETION) LifecycleEvent event, RequestScopedMarker marker) {
            ContextInvocationRecorder.recordBeforeCompletion(marker.getId());
        }
    }

    @ApplicationScoped
    public static class AfterCompletionLifecycleObserver {
        void observe(@Observes(during = TransactionPhase.AFTER_COMPLETION) LifecycleEvent event, RequestScopedMarker marker) {
            ContextInvocationRecorder.recordAfterCompletion(marker.getId());
        }
    }

    public static class SimpleEvent {
        final String value;

        SimpleEvent(String value) {
            this.value = value;
        }
    }

    public static class LifecycleEvent {
        final String value;

        LifecycleEvent(String value) {
            this.value = value;
        }
    }

    public static class Recorder {
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

        static synchronized List<String> values() {
            return new ArrayList<String>(EVENTS);
        }
    }

    public static class ContextInvocationRecorder {
        private static String syncMarkerId;
        private static String syncThreadName;
        private static String asyncMarkerId;
        private static String asyncThreadName;
        private static String beforeCompletionMarkerId;
        private static String afterCompletionMarkerId;

        static synchronized void reset() {
            syncMarkerId = null;
            syncThreadName = null;
            asyncMarkerId = null;
            asyncThreadName = null;
            beforeCompletionMarkerId = null;
            afterCompletionMarkerId = null;
        }

        static synchronized void recordSync(String markerId, String threadName) {
            syncMarkerId = markerId;
            syncThreadName = threadName;
        }

        static synchronized void recordAsync(String markerId, String threadName) {
            asyncMarkerId = markerId;
            asyncThreadName = threadName;
        }

        static synchronized void recordBeforeCompletion(String markerId) {
            beforeCompletionMarkerId = markerId;
        }

        static synchronized void recordAfterCompletion(String markerId) {
            afterCompletionMarkerId = markerId;
        }

        static synchronized String lastSyncMarkerId() {
            return syncMarkerId;
        }

        static synchronized String lastSyncThreadName() {
            return syncThreadName;
        }

        static synchronized String lastAsyncMarkerId() {
            return asyncMarkerId;
        }

        static synchronized String lastAsyncThreadName() {
            return asyncThreadName;
        }

        static synchronized String lastBeforeCompletionMarkerId() {
            return beforeCompletionMarkerId;
        }

        static synchronized String lastAfterCompletionMarkerId() {
            return afterCompletionMarkerId;
        }
    }

    private boolean containsMessage(Throwable[] throwables, String message) {
        for (Throwable throwable : throwables) {
            if (message.equals(throwable.getMessage())) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> T getRequestScopedInstance(BeanManagerImpl beanManager, Class<T> beanClass) {
        BeanManager bm = beanManager;
        Set<Bean<?>> beans = bm.getBeans(beanClass);
        Bean<T> bean = (Bean<T>) bm.resolve((Set) beans);
        return (T) bm.getContext(bean.getScope()).get(bean, bm.createCreationalContext(bean));
    }

    private static final class ControlledTransactionServices implements TransactionServices {
        private final boolean active;
        private final boolean failRegistration;
        private TransactionSynchronizationCallbacks callbacks;
        private int registerCount;

        private ControlledTransactionServices(boolean active, boolean failRegistration) {
            this.active = active;
            this.failRegistration = failRegistration;
        }

        @Override
        public boolean isTransactionActive() {
            return active;
        }

        @Override
        public void registerSynchronization(TransactionSynchronizationCallbacks callbacks) {
            registerCount++;
            if (failRegistration) {
                throw new IllegalStateException("cannot register synchronization");
            }
            this.callbacks = callbacks;
        }

        private void beforeCompletion() {
            if (callbacks != null) {
                callbacks.beforeCompletion();
            }
        }

        private void afterCompletion(boolean committed) {
            if (callbacks != null) {
                callbacks.afterCompletion(committed);
            }
        }
    }
}
