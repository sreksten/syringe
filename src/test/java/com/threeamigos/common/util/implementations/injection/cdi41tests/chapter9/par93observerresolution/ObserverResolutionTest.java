package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter9.par93observerresolution;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.enterprise.util.Nonbinding;
import jakarta.inject.Qualifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName( "9.3 - Observer resolution tests")
@Execution(ExecutionMode.SAME_THREAD)
public class ObserverResolutionTest {

    @Test
    @DisplayName("9.3 - Event is delivered only to observer methods that belong to an enabled bean")
    void shouldDeliverOnlyToObserversBelongingToEnabledBeans() {
        ResolutionRecorder.reset();
        Syringe syringe = newSyringe(EnabledObserver.class, DisabledAlternativeObserver.class);

        syringe.getBeanManager().getEvent().select(BasicEvent.class).fire(new BasicEvent("ok"));

        assertEquals(1, ResolutionRecorder.count("enabled"));
        assertEquals(0, ResolutionRecorder.count("disabled-alternative"));
    }

    @Test
    @DisplayName("9.3 - Event is delivered when event type is assignable to observed event type considering type parameters")
    void shouldResolveObserversByTypeAssignabilityWithTypeParameters() {
        ResolutionRecorder.reset();
        Syringe syringe = newSyringe(GenericTypedObserver.class);

        syringe.getBeanManager().getEvent()
                .select(new jakarta.enterprise.util.TypeLiteral<GenericPayload<String>>() {})
                .fire(new StringPayload("abc"));

        assertEquals(1, ResolutionRecorder.count("generic-string"));
        assertEquals(0, ResolutionRecorder.count("generic-integer"));
    }

    @Test
    @DisplayName("9.3 - Observer qualifiers must be a subset of event qualifiers and @Nonbinding members are ignored")
    void shouldResolveObserversWithQualifierSubsetAndIgnoreNonbindingMembers() {
        ResolutionRecorder.reset();
        Syringe syringe = newSyringe(QualifiedObservers.class);

        syringe.getBeanManager().getEvent()
                .select(QualifiedEvent.class, new RouteLiteral("login", "event-source"), AdminLiteral.INSTANCE)
                .fire(new QualifiedEvent("u"));

        assertEquals(1, ResolutionRecorder.count("route-login"));
        assertEquals(1, ResolutionRecorder.count("route-login-any-source"));
        assertEquals(0, ResolutionRecorder.count("route-admin"));
    }

    @Test
    @DisplayName("9.3 - Container lifecycle events are delivered to extension observers, not to normal bean observers")
    void shouldDeliverContainerLifecycleEventOnlyToExtensionObservers() {
        ExtensionLifecycleRecorder.reset();

        Syringe syringe = new Syringe(
                new InMemoryMessageHandler(),
                NonExtensionLifecycleObserver.class
        );
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.addExtension(LifecycleExtensionObserver.class.getName());
        syringe.setup();

        assertEquals(1, ExtensionLifecycleRecorder.extensionAfterBeanDiscoveryCalls());
        assertEquals(0, ExtensionLifecycleRecorder.nonExtensionAfterBeanDiscoveryCalls());
    }

    @Test
    @DisplayName("9.3 - Synchronous fire delivers only to synchronous observers")
    void shouldDeliverSyncEventsOnlyToSynchronousObservers() {
        ResolutionRecorder.reset();
        Syringe syringe = newSyringe(SyncAsyncObservers.class);

        syringe.getBeanManager().getEvent().select(BasicEvent.class).fire(new BasicEvent("ok"));

        assertEquals(1, ResolutionRecorder.count("sync"));
        assertEquals(0, ResolutionRecorder.count("async"));
    }

    @Test
    @DisplayName("9.3 - Asynchronous fire delivers only to asynchronous observers")
    void shouldDeliverAsyncEventsOnlyToAsynchronousObservers() throws Exception {
        ResolutionRecorder.reset();
        Syringe syringe = newSyringe(SyncAsyncObservers.class);

        CompletionStage<BasicEvent> stage = syringe.getBeanManager().getEvent()
                .select(BasicEvent.class)
                .fireAsync(new BasicEvent("ok-async"));
        stage.toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, ResolutionRecorder.count("sync"));
        assertEquals(1, ResolutionRecorder.count("async"));
    }

    @Test
    @DisplayName("9.3 - If runtime type of event object contains unresolvable type variable container throws IllegalArgumentException")
    void shouldThrowForRuntimeEventTypeWithUnresolvableTypeVariable() {
        Syringe syringe = newSyringe(EnabledObserver.class);

        assertThrows(IllegalArgumentException.class, () -> syringe.getBeanManager().getEvent().fire(new GenericRuntimeEvent<String>()));
    }

    @Test
    @DisplayName("9.3.1 - Event type is assignable to observed type variable when assignable to upper bound")
    void shouldAssignEventTypeToObservedTypeVariableUpperBound() {
        ResolutionRecorder.reset();
        Syringe syringe = newSyringe(AssignabilityObservers.class);

        syringe.getBeanManager().getEvent().fire(Integer.valueOf(42));

        assertEquals(1, ResolutionRecorder.count("type-variable-upper-bound"));
    }

    @Test
    @DisplayName("9.3.1 - Raw event type is assignable to parameterized observed type when observed type parameter is java.lang.Object")
    void shouldAssignRawEventTypeToParameterizedObservedTypeWithObjectParameter() {
        ResolutionRecorder.reset();
        Syringe syringe = newSyringe(AssignabilityObservers.class);

        syringe.getBeanManager().getEvent().fire(new RawPayload("raw-event"));

        assertEquals(1, ResolutionRecorder.count("raw-to-parameterized-object"));
    }

    @Test
    @DisplayName("9.3.1 - Parameterized event type is assignable to raw observed type with identical raw type")
    void shouldAssignParameterizedEventTypeToRawObservedType() {
        ResolutionRecorder.reset();
        Syringe syringe = newSyringe(AssignabilityObservers.class);

        syringe.getBeanManager().getEvent().fire(new IntegerPayload(7));

        assertEquals(1, ResolutionRecorder.count("parameterized-to-raw"));
    }

    @Test
    @DisplayName("9.3.1 - Parameterized event type is assignable to parameterized observed type with wildcard bounds")
    void shouldAssignParameterizedTypeWithWildcardBounds() {
        ResolutionRecorder.reset();
        Syringe syringe = newSyringe(AssignabilityObservers.class);

        syringe.getBeanManager().getEvent().fire(new IntegerPayload(11));

        assertEquals(1, ResolutionRecorder.count("parameterized-wildcard-extends"));
        assertEquals(1, ResolutionRecorder.count("parameterized-wildcard-super"));
    }

    @Test
    @DisplayName("9.3.1 - Parameterized event type is assignable to parameterized observed type with bounded type variable")
    void shouldAssignParameterizedTypeWithBoundedTypeVariable() {
        ResolutionRecorder.reset();
        Syringe syringe = newSyringe(AssignabilityObservers.class);

        syringe.getBeanManager().getEvent().fire(new IntegerPayload(19));

        assertEquals(1, ResolutionRecorder.count("parameterized-bounded-type-variable"));
    }

    @Test
    @DisplayName("9.3.2 - Observer with no qualifier is always notified even when event is fired with qualifier member values")
    void shouldAlwaysNotifyUnqualifiedObserverWhenEventHasQualifierMembers() {
        ResolutionRecorder.reset();
        Syringe syringe = newSyringe(RoleQualifiedObservers.class);

        syringe.getBeanManager().getEvent()
                .select(LoggedInEvent.class, new RoleLiteral("admin"))
                .fire(new LoggedInEvent(new User("alice", "admin")));

        assertEquals(1, ResolutionRecorder.count("login-any-role"));
    }

    @Test
    @DisplayName("9.3.2 - Observer with qualifier member value is notified only when dynamic event qualifier member value equals it")
    void shouldNotifyRoleSpecificObserverOnlyForMatchingMemberValue() {
        ResolutionRecorder.reset();
        Syringe syringe = newSyringe(RoleQualifiedObservers.class);

        syringe.getBeanManager().getEvent()
                .select(LoggedInEvent.class, new RoleLiteral("admin"))
                .fire(new LoggedInEvent(new User("bob", "admin")));

        syringe.getBeanManager().getEvent()
                .select(LoggedInEvent.class, new RoleLiteral("user"))
                .fire(new LoggedInEvent(new User("carol", "user")));

        assertEquals(2, ResolutionRecorder.count("login-any-role"));
        assertEquals(1, ResolutionRecorder.count("login-admin-only"));
    }

    @Test
    @DisplayName("9.3.2 - Container uses equals() of qualifier member values when comparing event qualifiers")
    void shouldUseEqualsForQualifierMemberValueComparison() {
        ResolutionRecorder.reset();
        Syringe syringe = newSyringe(RoleQualifiedObservers.class);

        String dynamicAdminValue = new String("admin");
        syringe.getBeanManager().getEvent()
                .select(LoggedInEvent.class, new RoleLiteral(dynamicAdminValue))
                .fire(new LoggedInEvent(new User("dave", "admin")));

        assertEquals(1, ResolutionRecorder.count("login-admin-only"));
        assertEquals(1, ResolutionRecorder.count("login-any-role"));
    }

    @Test
    @DisplayName("9.3.3 - Observer with multiple qualifiers is notified when its qualifiers are a subset of fired event qualifiers")
    void shouldNotifyObserversWhenObserverQualifiersAreSubsetOfEventQualifiers() {
        ResolutionRecorder.reset();
        Syringe syringe = newSyringe(DocumentObservers.class);

        syringe.getBeanManager().getEvent()
                .select(
                        DocumentEvent.class,
                        UpdatedLiteral.INSTANCE,
                        ByAdminLiteral.INSTANCE,
                        ClarificationLiteral.INSTANCE
                )
                .fire(new DocumentEvent("v1"));

        assertEquals(1, ResolutionRecorder.count("doc-updated-by-admin"));
        assertEquals(1, ResolutionRecorder.count("doc-updated"));
        assertEquals(1, ResolutionRecorder.count("doc-any"));
        assertEquals(0, ResolutionRecorder.count("doc-default"));
    }

    @Test
    @DisplayName("9.3.3 - @Default observer is notified only for events with no qualifiers or only @Default qualifier")
    void shouldNotifyDefaultObserverOnlyForNoQualifierOrDefaultQualifierEvents() {
        ResolutionRecorder.reset();
        Syringe syringe = newSyringe(DocumentObservers.class);

        syringe.getBeanManager().getEvent()
                .select(
                        DocumentEvent.class,
                        UpdatedLiteral.INSTANCE,
                        ByAdminLiteral.INSTANCE,
                        ClarificationLiteral.INSTANCE
                )
                .fire(new DocumentEvent("v1"));
        syringe.getBeanManager().getEvent()
                .select(DocumentEvent.class)
                .fire(new DocumentEvent("v2"));
        syringe.getBeanManager().getEvent()
                .select(DocumentEvent.class, DefaultLiteral.INSTANCE)
                .fire(new DocumentEvent("v3"));

        assertEquals(2, ResolutionRecorder.count("doc-default"));
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();
        return syringe;
    }

    @ApplicationScoped
    public static class EnabledObserver {
        void observe(@Observes BasicEvent event) {
            ResolutionRecorder.record("enabled");
        }
    }

    @Alternative
    @ApplicationScoped
    public static class DisabledAlternativeObserver {
        void observe(@Observes BasicEvent event) {
            ResolutionRecorder.record("disabled-alternative");
        }
    }

    @ApplicationScoped
    public static class GenericTypedObserver {
        void observeString(@Observes GenericPayload<String> event) {
            ResolutionRecorder.record("generic-string");
        }

        void observeInteger(@Observes GenericPayload<Integer> event) {
            ResolutionRecorder.record("generic-integer");
        }
    }

    @ApplicationScoped
    public static class QualifiedObservers {
        void observeRouteLogin(
                @Observes @Route(value = "login", source = "observer-source") QualifiedEvent event) {
            ResolutionRecorder.record("route-login");
        }

        void observeRouteLoginAndAdmin(
                @Observes @Route(value = "login", source = "another-source") @Admin QualifiedEvent event) {
            ResolutionRecorder.record("route-login-any-source");
        }

        void observeRouteAdmin(@Observes @Route("admin") QualifiedEvent event) {
            ResolutionRecorder.record("route-admin");
        }
    }

    @ApplicationScoped
    public static class SyncAsyncObservers {
        void observeSync(@Observes BasicEvent event) {
            ResolutionRecorder.record("sync");
        }

        void observeAsync(@ObservesAsync BasicEvent event) {
            ResolutionRecorder.record("async");
        }
    }

    @ApplicationScoped
    public static class AssignabilityObservers {
        <T extends Number> void observeTypeVariableUpperBound(@Observes T event) {
            ResolutionRecorder.record("type-variable-upper-bound");
        }

        void observeRawToParameterizedObject(@Observes GenericPayload<Object> event) {
            ResolutionRecorder.record("raw-to-parameterized-object");
        }

        void observeParameterizedToRaw(@Observes GenericPayload event) {
            ResolutionRecorder.record("parameterized-to-raw");
        }

        void observeParameterizedWildcardExtends(@Observes GenericPayload<? extends Number> event) {
            ResolutionRecorder.record("parameterized-wildcard-extends");
        }

        void observeParameterizedWildcardSuper(@Observes GenericPayload<? super Integer> event) {
            ResolutionRecorder.record("parameterized-wildcard-super");
        }

        <T extends Number> void observeParameterizedBoundedTypeVariable(@Observes GenericPayload<T> event) {
            ResolutionRecorder.record("parameterized-bounded-type-variable");
        }
    }

    @ApplicationScoped
    public static class RoleQualifiedObservers {
        void afterLogin(@Observes LoggedInEvent event) {
            ResolutionRecorder.record("login-any-role");
        }

        void afterAdminLogin(@Observes @Role("admin") LoggedInEvent event) {
            ResolutionRecorder.record("login-admin-only");
        }
    }

    @ApplicationScoped
    public static class DocumentObservers {
        void afterDocumentUpdatedByAdmin(@Observes @Updated @ByAdmin DocumentEvent event) {
            ResolutionRecorder.record("doc-updated-by-admin");
        }

        void afterDocumentUpdated(@Observes @Updated DocumentEvent event) {
            ResolutionRecorder.record("doc-updated");
        }

        void afterDocumentEvent(@Observes DocumentEvent event) {
            ResolutionRecorder.record("doc-any");
        }

        void afterDocumentDefaultEvent(@Observes @Default DocumentEvent event) {
            ResolutionRecorder.record("doc-default");
        }
    }

    @ApplicationScoped
    public static class NonExtensionLifecycleObserver {
        void shouldNotObserve(@Observes AfterBeanDiscovery event) {
            ExtensionLifecycleRecorder.nonExtensionAfterBeanDiscoveryCalled();
        }
    }

    public static class LifecycleExtensionObserver implements Extension {
        public void afterBeanDiscovery(@Observes AfterBeanDiscovery event) {
            ExtensionLifecycleRecorder.extensionAfterBeanDiscoveryCalled();
        }
    }

    public static class ResolutionRecorder {
        private static final List<String> EVENTS = new ArrayList<String>();

        static synchronized void reset() {
            EVENTS.clear();
        }

        static synchronized void record(String value) {
            EVENTS.add(value);
        }

        static synchronized int count(String value) {
            int count = 0;
            for (String current : EVENTS) {
                if (value.equals(current)) {
                    count++;
                }
            }
            return count;
        }
    }

    public static class ExtensionLifecycleRecorder {
        private static int extensionAfterBeanDiscoveryCalls;
        private static int nonExtensionAfterBeanDiscoveryCalls;

        static synchronized void reset() {
            extensionAfterBeanDiscoveryCalls = 0;
            nonExtensionAfterBeanDiscoveryCalls = 0;
        }

        static synchronized void extensionAfterBeanDiscoveryCalled() {
            extensionAfterBeanDiscoveryCalls++;
        }

        static synchronized void nonExtensionAfterBeanDiscoveryCalled() {
            nonExtensionAfterBeanDiscoveryCalls++;
        }

        static synchronized int extensionAfterBeanDiscoveryCalls() {
            return extensionAfterBeanDiscoveryCalls;
        }

        static synchronized int nonExtensionAfterBeanDiscoveryCalls() {
            return nonExtensionAfterBeanDiscoveryCalls;
        }
    }

    public static class BasicEvent {
        private final String value;

        BasicEvent(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public static class GenericPayload<T> {
        private final T value;

        GenericPayload(T value) {
            this.value = value;
        }

        public T value() {
            return value;
        }
    }

    public static class StringPayload extends GenericPayload<String> {
        StringPayload(String value) {
            super(value);
        }
    }

    public static class IntegerPayload extends GenericPayload<Integer> {
        IntegerPayload(Integer value) {
            super(value);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static class RawPayload extends GenericPayload {
        RawPayload(Object value) {
            super(value);
        }
    }

    public static class QualifiedEvent {
        private final String user;

        QualifiedEvent(String user) {
            this.user = user;
        }

        public String user() {
            return user;
        }
    }

    public static class GenericRuntimeEvent<T> {
    }

    public static class LoggedInEvent {
        private final User user;

        LoggedInEvent(User user) {
            this.user = user;
        }

        public User user() {
            return user;
        }
    }

    public static class User {
        private final String name;
        private final String role;

        User(String name, String role) {
            this.name = name;
            this.role = role;
        }

        public String name() {
            return name;
        }

        public String role() {
            return role;
        }
    }

    public static class DocumentEvent {
        private final String id;

        DocumentEvent(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    @Qualifier
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Route {
        String value();

        @Nonbinding
        String source() default "";
    }

    @Qualifier
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Admin {
    }

    @Qualifier
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Role {
        String value();
    }

    @Qualifier
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Updated {
    }

    @Qualifier
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ByAdmin {
    }

    @Qualifier
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Clarification {
    }


    public static class RouteLiteral extends AnnotationLiteral<Route> implements Route {
        private final String value;
        private final String source;

        RouteLiteral(String value, String source) {
            this.value = value;
            this.source = source;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public String source() {
            return source;
        }
    }

    public static class AdminLiteral extends AnnotationLiteral<Admin> implements Admin {
        static final AdminLiteral INSTANCE = new AdminLiteral();
    }

    public static class RoleLiteral extends AnnotationLiteral<Role> implements Role {
        private final String value;

        RoleLiteral(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }
    }

    public static class UpdatedLiteral extends AnnotationLiteral<Updated> implements Updated {
        static final UpdatedLiteral INSTANCE = new UpdatedLiteral();
    }

    public static class ByAdminLiteral extends AnnotationLiteral<ByAdmin> implements ByAdmin {
        static final ByAdminLiteral INSTANCE = new ByAdminLiteral();
    }

    public static class ClarificationLiteral extends AnnotationLiteral<Clarification> implements Clarification {
        static final ClarificationLiteral INSTANCE = new ClarificationLiteral();
    }

    public static class DefaultLiteral extends AnnotationLiteral<Default> implements Default {
        static final DefaultLiteral INSTANCE = new DefaultLiteral();
    }

}
