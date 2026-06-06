package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter9.par92firingevents;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

@DisplayName("9.2 - Firing Events")
@Execution(ExecutionMode.SAME_THREAD)
public class FiringEventsTest {

    @BeforeEach
    void resetRecorder() {
        EventRecorder.reset();
        SyncRecorder.reset();
        AsyncRecorder.reset();
    }

    @Test
    @DisplayName("9.2 - Beans can fire events via injected Event interface instance")
    void shouldFireEventThroughInjectedEvent() {
        Syringe syringe = newSyringe(EventRecorder.class, LoginEventService.class, LoginObservers.class);
        LoginEventService service = syringe.inject(LoginEventService.class);

        service.fireDefaultSync(new User("john", false));

        assertEquals(1, EventRecorder.count("sync-default"));
    }

    @Test
    @DisplayName("9.2 - Any combination of qualifiers can be specified at Event injection point")
    void shouldFireQualifiedEventFromQualifiedInjectionPoint() {
        Syringe syringe = newSyringe(EventRecorder.class, LoginEventService.class, LoginObservers.class);
        LoginEventService service = syringe.inject(LoginEventService.class);

        service.fireAdminSyncViaQualifiedInjectionPoint(new User("admin", true));

        assertEquals(1, EventRecorder.count("sync-admin"));
        assertEquals(0, EventRecorder.count("sync-vip"));
    }

    @Test
    @DisplayName("9.2 - Application can specify qualifiers dynamically using select() before fire()")
    void shouldFireWithDynamicQualifierUsingSelect() {
        Syringe syringe = newSyringe(EventRecorder.class, LoginEventService.class, LoginObservers.class);
        LoginEventService service = syringe.inject(LoginEventService.class);

        service.fireAdminSyncDynamically(new User("boss", true));

        assertEquals(1, EventRecorder.count("sync-admin"));
        assertEquals(0, EventRecorder.count("sync-vip"));
    }

    @Test
    @DisplayName("9.2 - Non-admin flow can fire synchronously with fire() and asynchronously with fireAsync()")
    void shouldFireSyncThenAsyncForNonAdminFlow() throws Exception {
        Syringe syringe = newSyringe(EventRecorder.class, LoginEventService.class, LoginObservers.class);
        LoginEventService service = syringe.inject(LoginEventService.class);

        service.loginWithSpecLikeFlow(new User("alice", false));

        assertEquals(1, EventRecorder.count("sync-default"));
        assertEquals(1, EventRecorder.count("async-default"));
        assertTrue(EventRecorder.count("sync-admin") == 0);
    }

    @Test
    @DisplayName("9.2 - Admin flow can dynamically select @Admin and fire synchronously")
    void shouldFireSelectedAdminSyncForAdminFlow() throws Exception {
        Syringe syringe = newSyringe(EventRecorder.class, LoginEventService.class, LoginObservers.class);
        LoginEventService service = syringe.inject(LoginEventService.class);

        service.loginWithSpecLikeFlow(new User("root", true));

        assertEquals(1, EventRecorder.count("sync-admin"));
        assertEquals(1, EventRecorder.count("sync-default"));
        assertEquals(0, EventRecorder.count("async-default"));
    }

    @Test
    @DisplayName("9.2.1 - fire() notifies all resolved synchronous observers")
    void shouldNotifyAllResolvedSynchronousObservers() {
        Syringe syringe = newSyringe(SyncRecorder.class, SyncFiringService.class, SyncObservers.class);
        SyncFiringService service = syringe.inject(SyncFiringService.class);

        service.fireSynchronously();

        assertEquals(2, SyncRecorder.observerInvocations());
    }

    @Test
    @DisplayName("9.2.1 - fire() invokes synchronous observers in the same thread that called fire()")
    void shouldInvokeSynchronousObserversOnCallingThread() {
        Syringe syringe = newSyringe(SyncRecorder.class, SyncFiringService.class, SyncObservers.class);
        SyncFiringService service = syringe.inject(SyncFiringService.class);

        long callerThread = Thread.currentThread().getId();
        service.fireSynchronously();

        for (Long observerThreadId : SyncRecorder.observerThreadIds()) {
            assertEquals(callerThread, observerThreadId.longValue());
        }
    }

    @Test
    @DisplayName("9.2.1 - fire() blocks the calling thread until synchronous observer notification completes")
    void shouldBlockCallingThreadUntilSynchronousObserversComplete() {
        Syringe syringe = newSyringe(SyncRecorder.class, SyncFiringService.class, SyncObservers.class);
        SyncFiringService service = syringe.inject(SyncFiringService.class);

        long elapsedMillis = service.fireSynchronouslyAndReturnElapsedMillis();

        assertTrue(elapsedMillis >= 120L);
    }

    @Test
    @DisplayName("9.2.2 - fireAsync() returns immediately without waiting for asynchronous observers to complete")
    void shouldReturnImmediatelyWhenFiringAsynchronously() throws Exception {
        Syringe syringe = newSyringe(AsyncRecorder.class, AsyncFiringService.class, AsyncObservers.class);
        AsyncFiringService service = syringe.inject(AsyncFiringService.class);

        long elapsedMillis = service.fireAsynchronouslyAndReturnElapsedMillis();
        CompletionStage<AsyncProbeEvent> completionStage = service.lastStage();

        assertTrue(elapsedMillis < 120L);
        completionStage.toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(2, AsyncRecorder.observerInvocations());
    }

    @Test
    @DisplayName("9.2.2 - fireAsync() notifies all resolved asynchronous observers")
    void shouldNotifyAllResolvedAsynchronousObservers() throws Exception {
        Syringe syringe = newSyringe(AsyncRecorder.class, AsyncFiringService.class, AsyncObservers.class);
        AsyncFiringService service = syringe.inject(AsyncFiringService.class);

        service.fireAsynchronously().toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(2, AsyncRecorder.observerInvocations());
    }

    @Test
    @DisplayName("9.2.2 - Resolved asynchronous observers are invoked in threads different from the fireAsync() caller thread")
    void shouldInvokeAsynchronousObserversOnDifferentThreads() throws Exception {
        Syringe syringe = newSyringe(AsyncRecorder.class, AsyncFiringService.class, AsyncObservers.class);
        AsyncFiringService service = syringe.inject(AsyncFiringService.class);
        long callerThreadId = Thread.currentThread().getId();

        service.fireAsynchronously().toCompletableFuture().get(5, TimeUnit.SECONDS);

        boolean atLeastOneDifferentThread = false;
        for (Long observerThreadId : AsyncRecorder.observerThreadIds()) {
            if (observerThreadId.longValue() != callerThreadId) {
                atLeastOneDifferentThread = true;
                break;
            }
        }
        assertTrue(atLeastOneDifferentThread);
    }

    @Test
    @DisplayName("9.2.3 - Injected Event uses specified type and qualifiers declared at the injection point")
    void shouldUseInjectedEventSpecifiedTypeAndQualifiers() {
        Syringe syringe = newSyringe(EventRecorder.class, LoginEventService.class, LoginObservers.class);
        LoginEventService service = syringe.inject(LoginEventService.class);

        service.fireAdminSyncViaQualifiedInjectionPoint(new User("spec", true));

        assertEquals(1, EventRecorder.count("sync-admin"));
    }

    @Test
    @DisplayName("9.2.3 - select(Annotation...) returns child Event with parent type and additional qualifiers")
    void shouldSelectAdditionalQualifiersKeepingParentType() {
        Syringe syringe = newSyringe(EventRecorder.class, LoginEventService.class, LoginObservers.class);
        LoginEventService service = syringe.inject(LoginEventService.class);

        service.fireAdminSyncDynamically(new User("child", true));

        assertEquals(1, EventRecorder.count("sync-admin"));
    }

    @Test
    @DisplayName("9.2.3 - select(Class, qualifiers) returns child Event for given subtype and qualifiers")
    void shouldSelectClassSubtypeAndQualifiers() {
        Syringe syringe = newSyringe(EventRecorder.class, LoginEventService.class, LoginObservers.class);
        LoginEventService service = syringe.inject(LoginEventService.class);

        service.fireClassSubtypeAdminEvent(new User("class-sub", true));

        assertEquals(1, EventRecorder.count("sync-admin-class-subtype"));
    }

    @Test
    @DisplayName("9.2.3 - select(TypeLiteral, qualifiers) returns child Event for given subtype and qualifiers")
    void shouldSelectTypeLiteralSubtypeAndQualifiers() {
        Syringe syringe = newSyringe(EventRecorder.class, LoginEventService.class, LoginObservers.class);
        LoginEventService service = syringe.inject(LoginEventService.class);

        service.fireTypeLiteralSubtypeAdminEvent(new User("type-sub", true));

        assertEquals(1, EventRecorder.count("sync-admin-typeliteral-subtype"));
    }

    @Test
    @DisplayName("9.2.3 - If specified type in select() contains a type variable IllegalArgumentException is thrown")
    void shouldThrowForTypeVariableSpecifiedTypeInSelect() {
        Syringe syringe = newSyringe(EventRecorder.class, LoginEventService.class);
        Event<Object> any = syringe.getBeanManager().getEvent();
        GenericEventSelector<String> selector = new GenericEventSelector<String>();

        assertThrows(IllegalArgumentException.class, () -> selector.selectWithTypeVariable(any));
    }

    @Test
    @DisplayName("9.2.3 - If two instances of same non-repeating qualifier are passed to select() IllegalArgumentException is thrown")
    void shouldThrowForDuplicateNonRepeatingQualifierInSelect() {
        Syringe syringe = newSyringe(EventRecorder.class, LoginEventService.class);
        Event<LoggedInEvent> any = syringe.inject(LoginEventService.class).loggedInEvent;

        assertThrows(IllegalArgumentException.class, () ->
                any.select(AdminLiteral.INSTANCE, new AdminLiteral()).fire(new LoggedInEvent(new User("dup", true))));
    }

    @Test
    @DisplayName("9.2.3 - If a non-qualifier annotation is passed to select() IllegalArgumentException is thrown")
    void shouldThrowForNonQualifierAnnotationInSelect() {
        Syringe syringe = newSyringe(EventRecorder.class, LoginEventService.class);
        Event<LoggedInEvent> any = syringe.inject(LoginEventService.class).loggedInEvent;

        assertThrows(IllegalArgumentException.class, () -> any.select(NotAQualifierLiteral.INSTANCE));
    }

    @Test
    @DisplayName("9.2.3 - fireAsync(event, NotificationOptions) can use provided Executor for asynchronous delivery")
    void shouldUseProvidedExecutorForFireAsyncNotificationOptions() throws Exception {
        Syringe syringe = newSyringe(AsyncRecorder.class, AsyncFiringService.class, AsyncObservers.class);
        AsyncFiringService service = syringe.inject(AsyncFiringService.class);
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("custom-9-2-3-executor");
            return t;
        });

        try {
            service.fireAsynchronouslyWithOptions(NotificationOptions.ofExecutor(executor))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        boolean usedCustomExecutor = false;
        for (String name : AsyncRecorder.observerThreadNames()) {
            if (name.contains("custom-9-2-3-executor")) {
                usedCustomExecutor = true;
                break;
            }
        }
        assertTrue(usedCustomExecutor);
    }

    @Test
    @DisplayName("9.2.3 - If runtime type of event object contains unresolvable type variable IllegalArgumentException is thrown")
    void shouldThrowForRuntimeTypeContainingTypeVariable() {
        Syringe syringe = newSyringe(EventRecorder.class, LoginEventService.class);
        Event<LoggedInEvent> any = syringe.inject(LoginEventService.class).loggedInEvent;

        assertThrows(IllegalArgumentException.class, () ->
                any.fire((LoggedInEvent) (Object) new GenericRuntimeLoggedInEvent<String>()));
    }

    @Test
    @DisplayName("9.2.3 - If runtime type of event object is assignable to container lifecycle event type IllegalArgumentException is thrown")
    void shouldThrowForContainerLifecycleEventRuntimeType() {
        Syringe syringe = newSyringe(EventRecorder.class, LoginEventService.class);
        Event<Object> any = syringe.getBeanManager().getEvent();

        assertThrows(IllegalArgumentException.class, () -> any.fire(new FakeLifecycleEvent()));
    }

    @Test
    @DisplayName("9.2.4 - Built-in Event is provided automatically for Event<X> injection points with legal type parameter X")
    void shouldProvideBuiltInEventAutomatically() {
        Syringe syringe = newSyringe(BuiltInEventConsumer.class);
        BuiltInEventConsumer consumer = syringe.inject(BuiltInEventConsumer.class);

        assertNotNull(consumer.anyLoggedInEvent);
        assertNotNull(consumer.stringEvent);
    }

    @Test
    @DisplayName("9.2.4 - Built-in Event supports every event qualifier type at injection points")
    void shouldSupportAnyEventQualifierTypeAtInjectionPoint() {
        Syringe syringe = newSyringe(EventRecorder.class, QualifiedBuiltInEventConsumer.class, LoginObservers.class);
        QualifiedBuiltInEventConsumer consumer = syringe.inject(QualifiedBuiltInEventConsumer.class);

        consumer.fireVip(new User("vip-user", false));

        assertEquals(1, EventRecorder.count("sync-vip"));
    }

    @Test
    @DisplayName("9.2.4 - Built-in Event has @Dependent scope behavior and is not shared across distinct injection points")
    void shouldCreateDistinctBuiltInEventInstancesPerInjectionPoint() {
        Syringe syringe = newSyringe(BuiltInEventConsumer.class);
        BuiltInEventConsumer consumer = syringe.inject(BuiltInEventConsumer.class);

        assertNotSame(consumer.firstEvent, consumer.secondEvent);
    }

    @Test
    @DisplayName("9.2.4 - Built-in Event has no bean name and uses container-provided implementation")
    void shouldUseContainerProvidedEventImplementation() {
        Syringe syringe = newSyringe(BuiltInEventConsumer.class);
        BuiltInEventConsumer consumer = syringe.inject(BuiltInEventConsumer.class);

        String className = consumer.anyLoggedInEvent.getClass().getName();
        assertTrue(className.contains("EventImpl"));
    }

    @Test
    @DisplayName("9.2.4 - Injection point of raw type Event is a definition error")
    void shouldFailForRawEventInjectionPoint() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), RawEventInjectionBean.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(RawEventInjectionBean.class);
        syringe.setup();
        return syringe;
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    public @interface Admin {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    public @interface Vip {
    }

    public static final class AdminLiteral extends AnnotationLiteral<Admin> implements Admin {
        static final AdminLiteral INSTANCE = new AdminLiteral();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    public @interface NotAQualifier {
    }

    public static final class NotAQualifierLiteral extends AnnotationLiteral<NotAQualifier> implements NotAQualifier {
        static final NotAQualifierLiteral INSTANCE = new NotAQualifierLiteral();
    }

    public static class User {
        final String username;
        final boolean admin;

        User(String username, boolean admin) {
            this.username = username;
            this.admin = admin;
        }

        boolean isAdmin() {
            return admin;
        }
    }

    public static class LoggedInEvent {
        final User user;

        LoggedInEvent(User user) {
            this.user = user;
        }
    }

    public static class AdminLoggedInClassSubtypeEvent extends LoggedInEvent {
        AdminLoggedInClassSubtypeEvent(User user) {
            super(user);
        }
    }

    public static class AdminLoggedInTypeLiteralSubtypeEvent extends LoggedInEvent {
        AdminLoggedInTypeLiteralSubtypeEvent(User user) {
            super(user);
        }
    }

    public static class GenericRuntimeLoggedInEvent<T> extends LoggedInEvent {
        GenericRuntimeLoggedInEvent() {
            super(new User("generic", false));
        }
    }

    public static class SyncProbeEvent {
    }

    public static class AsyncProbeEvent {
    }

    @Dependent
    public static class EventRecorder {
        private static final Set<String> EVENTS = new HashSet<String>();

        static synchronized void reset() {
            EVENTS.clear();
        }

        static synchronized void record(String eventName) {
            EVENTS.add(eventName + "-" + System.nanoTime());
        }

        static synchronized int count(String prefix) {
            int count = 0;
            for (String value : EVENTS) {
                if (value.startsWith(prefix + "-")) {
                    count++;
                }
            }
            return count;
        }
    }

    @Dependent
    public static class SyncRecorder {
        private static int observerInvocations;
        private static final List<Long> OBSERVER_THREAD_IDS = new ArrayList<Long>();

        static synchronized void reset() {
            observerInvocations = 0;
            OBSERVER_THREAD_IDS.clear();
        }

        static synchronized void recordObserverInvocation() {
            observerInvocations++;
            OBSERVER_THREAD_IDS.add(Thread.currentThread().getId());
        }

        static synchronized int observerInvocations() {
            return observerInvocations;
        }

        static synchronized List<Long> observerThreadIds() {
            return new ArrayList<Long>(OBSERVER_THREAD_IDS);
        }
    }

    @Dependent
    public static class AsyncRecorder {
        private static int observerInvocations;
        private static final List<Long> OBSERVER_THREAD_IDS = new ArrayList<Long>();
        private static final List<String> OBSERVER_THREAD_NAMES = new ArrayList<String>();

        static synchronized void reset() {
            observerInvocations = 0;
            OBSERVER_THREAD_IDS.clear();
            OBSERVER_THREAD_NAMES.clear();
        }

        static synchronized void recordObserverInvocation() {
            observerInvocations++;
            OBSERVER_THREAD_IDS.add(Thread.currentThread().getId());
            OBSERVER_THREAD_NAMES.add(Thread.currentThread().getName());
        }

        static synchronized int observerInvocations() {
            return observerInvocations;
        }

        static synchronized List<Long> observerThreadIds() {
            return new ArrayList<Long>(OBSERVER_THREAD_IDS);
        }

        static synchronized List<String> observerThreadNames() {
            return new ArrayList<String>(OBSERVER_THREAD_NAMES);
        }
    }

    @Dependent
    public static class LoginEventService {
        @Inject
        Event<LoggedInEvent> loggedInEvent;

        @Inject
        @Admin
        Event<LoggedInEvent> adminLoggedInEvent;

        void fireDefaultSync(User user) {
            loggedInEvent.fire(new LoggedInEvent(user));
        }

        void fireAdminSyncViaQualifiedInjectionPoint(User user) {
            adminLoggedInEvent.fire(new LoggedInEvent(user));
        }

        void fireAdminSyncDynamically(User user) {
            loggedInEvent.select(AdminLiteral.INSTANCE).fire(new LoggedInEvent(user));
        }

        void fireClassSubtypeAdminEvent(User user) {
            loggedInEvent.select(AdminLoggedInClassSubtypeEvent.class, AdminLiteral.INSTANCE)
                    .fire(new AdminLoggedInClassSubtypeEvent(user));
        }

        void fireTypeLiteralSubtypeAdminEvent(User user) {
            loggedInEvent.select(new TypeLiteral<AdminLoggedInTypeLiteralSubtypeEvent>() {
                    }, AdminLiteral.INSTANCE)
                    .fire(new AdminLoggedInTypeLiteralSubtypeEvent(user));
        }

        void loginWithSpecLikeFlow(User user) throws Exception {
            LoggedInEvent event = new LoggedInEvent(user);
            if (user.isAdmin()) {
                loggedInEvent.select(AdminLiteral.INSTANCE).fire(event);
            } else {
                loggedInEvent.fire(event);
                loggedInEvent.fireAsync(event).toCompletableFuture().get(5, TimeUnit.SECONDS);
            }
        }
    }

    @Dependent
    public static class SyncFiringService {
        @Inject
        Event<SyncProbeEvent> syncProbeEvent;

        void fireSynchronously() {
            syncProbeEvent.fire(new SyncProbeEvent());
        }

        long fireSynchronouslyAndReturnElapsedMillis() {
            long start = System.nanoTime();
            syncProbeEvent.fire(new SyncProbeEvent());
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        }
    }

    @Dependent
    public static class AsyncFiringService {
        @Inject
        Event<AsyncProbeEvent> asyncProbeEvent;

        private CompletionStage<AsyncProbeEvent> lastStage;

        CompletionStage<AsyncProbeEvent> fireAsynchronously() {
            lastStage = asyncProbeEvent.fireAsync(new AsyncProbeEvent());
            return lastStage;
        }

        long fireAsynchronouslyAndReturnElapsedMillis() {
            long start = System.nanoTime();
            lastStage = asyncProbeEvent.fireAsync(new AsyncProbeEvent());
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        }

        CompletionStage<AsyncProbeEvent> lastStage() {
            return lastStage;
        }

        CompletionStage<AsyncProbeEvent> fireAsynchronouslyWithOptions(NotificationOptions options) {
            lastStage = asyncProbeEvent.fireAsync(new AsyncProbeEvent(), options);
            return lastStage;
        }
    }

    @Dependent
    public static class LoginObservers {
        void onDefaultSync(@Observes LoggedInEvent event) {
            EventRecorder.record("sync-default");
        }

        void onAdminSync(@Observes @Admin LoggedInEvent event) {
            EventRecorder.record("sync-admin");
        }

        void onVipSync(@Observes @Vip LoggedInEvent event) {
            EventRecorder.record("sync-vip");
        }

        void onAdminClassSubtype(@Observes @Admin AdminLoggedInClassSubtypeEvent event) {
            EventRecorder.record("sync-admin-class-subtype");
        }

        void onAdminTypeLiteralSubtype(@Observes @Admin AdminLoggedInTypeLiteralSubtypeEvent event) {
            EventRecorder.record("sync-admin-typeliteral-subtype");
        }

        void onDefaultAsync(@ObservesAsync LoggedInEvent event) {
            EventRecorder.record("async-default");
        }
    }

    @Dependent
    public static class SyncObservers {
        void first(@Observes SyncProbeEvent event) {
            SyncRecorder.recordObserverInvocation();
            sleep(60);
        }

        void second(@Observes SyncProbeEvent event) {
            SyncRecorder.recordObserverInvocation();
            sleep(60);
        }

        private void sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }

    @Dependent
    public static class AsyncObservers {
        void first(@ObservesAsync AsyncProbeEvent event) {
            AsyncRecorder.recordObserverInvocation();
            sleep(120);
        }

        void second(@ObservesAsync AsyncProbeEvent event) {
            AsyncRecorder.recordObserverInvocation();
            sleep(120);
        }

        private void sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }

    public static class GenericEventSelector<X> {
        Event<Object> selectWithTypeVariable(Event<Object> event) {
            @SuppressWarnings("rawtypes")
            Event raw = event;
            @SuppressWarnings("unchecked")
            Event<Object> selected = (Event<Object>) raw.select(new TypeLiteral<List<X>>() {
            });
            return selected;
        }
    }

    public static class FakeLifecycleEvent implements jakarta.enterprise.inject.spi.AfterDeploymentValidation {
        @Override
        public void addDeploymentProblem(Throwable t) {
            // no-op for test lifecycle-event type marker
        }
    }

    @Dependent
    public static class BuiltInEventConsumer {
        @Inject
        Event<LoggedInEvent> anyLoggedInEvent;

        @Inject
        Event<String> stringEvent;

        @Inject
        Event<LoggedInEvent> firstEvent;

        @Inject
        Event<LoggedInEvent> secondEvent;
    }

    @Dependent
    public static class QualifiedBuiltInEventConsumer {
        @Inject
        @Vip
        Event<LoggedInEvent> vipEvent;

        void fireVip(User user) {
            vipEvent.fire(new LoggedInEvent(user));
        }
    }

    @Dependent
    public static class RawEventInjectionBean {
        @Inject
        Event rawEvent;
    }
}
