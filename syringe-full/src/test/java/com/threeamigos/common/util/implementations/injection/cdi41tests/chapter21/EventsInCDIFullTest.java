package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter21;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.inject.spi.EventContext;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ObserverMethod;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName( "21 - Events in CDI full test")
@Execution(ExecutionMode.SAME_THREAD)
public class EventsInCDIFullTest {
    private static final Class<?>[] FIXTURE_CLASSES = new Class<?>[]{
            SessionBeanInjectingEvent.class,
            ExtensionAnchor.class,
            CustomEvent.class,
            Observed.class,
            ObservedLiteral.class,
            SyntheticObserverRegistrationExtension.class,
            TrackingObserverMethod.class,
            MultiObserverPortableExtension.class,
            DecoratedContract.class,
            DecoratedService.class,
            InvalidDecoratorWithObserverMethod.class
    };

    @Test
    @DisplayName("21.1.1 - Built-in Event is a passivation-capable dependency")
    void shouldTreatBuiltInEventAsPassivationCapableDependency() {
        Syringe syringe = newSyringe(SessionBeanInjectingEvent.class);
        assertDoesNotThrow(syringe::setup);
    }

    @Test
    @DisplayName("21.2 - Container uses custom ObserverMethod metadata for observed type, qualifiers and async mode")
    void shouldUseCustomObserverMethodMetadataForResolution() throws Exception {
        SyntheticObserverRecorder.reset();
        Syringe syringe = newSyringe(ExtensionAnchor.class);
        syringe.addExtension(SyntheticObserverRegistrationExtension.class.getName());
        syringe.setup();

        syringe.getBeanManager().getEvent()
                .select(CustomEvent.class, ObservedLiteral.INSTANCE)
                .fire(new CustomEvent("sync"));

        assertTrue(SyntheticObserverRecorder.syncNotifications.get() > 0);
        assertTrue(SyntheticObserverRecorder.getObservedTypeCalls.get() > 0);
        assertTrue(SyntheticObserverRecorder.getObservedQualifiersCalls.get() > 0);
        assertTrue(SyntheticObserverRecorder.isAsyncCalls.get() > 0);

        CompletionStage<CustomEvent> asyncStage = syringe.getBeanManager().getEvent()
                .select(CustomEvent.class, ObservedLiteral.INSTANCE)
                .fireAsync(new CustomEvent("async"));
        asyncStage.toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertTrue(SyntheticObserverRecorder.asyncNotifications.get() > 0);
    }

    @Test
    @DisplayName("21.3 - Portable extension observer methods are non-abstract extension methods and multiple observer methods are supported")
    void shouldInvokeMultipleObserverMethodsDeclaredByPortableExtension() {
        PortableExtensionObserverRecorder.reset();
        Syringe syringe = newSyringe(ExtensionAnchor.class);
        syringe.addExtension(MultiObserverPortableExtension.class.getName());
        syringe.setup();

        assertEquals(1, PortableExtensionObserverRecorder.beforeBeanDiscoveryCalls.get());
        assertEquals(1, PortableExtensionObserverRecorder.afterBeanDiscoveryCalls.get());
    }

    @Test
    @DisplayName("21.3.1 - Decorators may not declare observer methods")
    void shouldFailDeploymentWhenDecoratorDeclaresObserverMethod() {
        Syringe syringe = newSyringe(
                DecoratedContract.class,
                DecoratedService.class,
                InvalidDecoratorWithObserverMethod.class
        );
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("21.4 - Container uses getTransactionPhase() and notify(EventContext) for custom ObserverMethod")
    void shouldInvokeCustomObserverWithEventContextAndTransactionPhase() {
        SyntheticObserverRecorder.reset();
        Syringe syringe = newSyringe(ExtensionAnchor.class);
        syringe.addExtension(SyntheticObserverRegistrationExtension.class.getName());
        syringe.setup();

        syringe.getBeanManager().getEvent()
                .select(CustomEvent.class, ObservedLiteral.INSTANCE)
                .fire(new CustomEvent("phase-check"));

        assertTrue(SyntheticObserverRecorder.getTransactionPhaseCalls.get() > 0);
        assertTrue(SyntheticObserverRecorder.notifyEventContextCalls.get() > 0);
        assertEquals(0, SyntheticObserverRecorder.notifyLegacyCalls.get());
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        Set<Class<?>> included = new HashSet<Class<?>>(Arrays.asList(beanClasses));
        for (Class<?> fixture : FIXTURE_CLASSES) {
            if (!included.contains(fixture)) {
                syringe.exclude(fixture);
            }
        }
        return syringe;
    }

    @SessionScoped
    public static class SessionBeanInjectingEvent implements Serializable {
        @Inject
        Event<CustomEvent> event;
    }

    public static class ExtensionAnchor {
    }

    public static class CustomEvent {
        private final String message;

        public CustomEvent(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE, ElementType.METHOD})
    public @interface Observed {
    }

    public static final class ObservedLiteral extends AnnotationLiteral<Observed> implements Observed {
        public static final ObservedLiteral INSTANCE = new ObservedLiteral();
    }

    public static class SyntheticObserverRecorder {
        static final AtomicInteger getObservedTypeCalls = new AtomicInteger();
        static final AtomicInteger getObservedQualifiersCalls = new AtomicInteger();
        static final AtomicInteger isAsyncCalls = new AtomicInteger();
        static final AtomicInteger getTransactionPhaseCalls = new AtomicInteger();
        static final AtomicInteger notifyEventContextCalls = new AtomicInteger();
        static final AtomicInteger notifyLegacyCalls = new AtomicInteger();
        static final AtomicInteger syncNotifications = new AtomicInteger();
        static final AtomicInteger asyncNotifications = new AtomicInteger();

        static void reset() {
            getObservedTypeCalls.set(0);
            getObservedQualifiersCalls.set(0);
            isAsyncCalls.set(0);
            getTransactionPhaseCalls.set(0);
            notifyEventContextCalls.set(0);
            notifyLegacyCalls.set(0);
            syncNotifications.set(0);
            asyncNotifications.set(0);
        }
    }

    public static class SyntheticObserverRegistrationExtension implements Extension {
        public void register(@Observes AfterBeanDiscovery afterBeanDiscovery) {
            afterBeanDiscovery.addObserverMethod(new TrackingObserverMethod(false));
            afterBeanDiscovery.addObserverMethod(new TrackingObserverMethod(true));
        }
    }

    public static class TrackingObserverMethod implements ObserverMethod<CustomEvent> {
        private final boolean async;

        TrackingObserverMethod(boolean async) {
            this.async = async;
        }

        @Override
        public Class<?> getBeanClass() {
            return ExtensionAnchor.class;
        }

        @Override
        public Type getObservedType() {
            SyntheticObserverRecorder.getObservedTypeCalls.incrementAndGet();
            return CustomEvent.class;
        }

        @Override
        public Set<Annotation> getObservedQualifiers() {
            SyntheticObserverRecorder.getObservedQualifiersCalls.incrementAndGet();
            Set<Annotation> qualifiers = new HashSet<Annotation>();
            qualifiers.add(ObservedLiteral.INSTANCE);
            return qualifiers;
        }

        @Override
        public jakarta.enterprise.event.Reception getReception() {
            return jakarta.enterprise.event.Reception.ALWAYS;
        }

        @Override
        public TransactionPhase getTransactionPhase() {
            SyntheticObserverRecorder.getTransactionPhaseCalls.incrementAndGet();
            return TransactionPhase.IN_PROGRESS;
        }

        @Override
        public int getPriority() {
            return ObserverMethod.DEFAULT_PRIORITY;
        }

        @Override
        public void notify(CustomEvent event) {
            SyntheticObserverRecorder.notifyLegacyCalls.incrementAndGet();
            throw new AssertionError("Container should invoke notify(EventContext) for custom ObserverMethod");
        }

        @Override
        public void notify(EventContext<CustomEvent> eventContext) {
            SyntheticObserverRecorder.notifyEventContextCalls.incrementAndGet();
            if (async) {
                SyntheticObserverRecorder.asyncNotifications.incrementAndGet();
            } else {
                SyntheticObserverRecorder.syncNotifications.incrementAndGet();
            }
            assertEquals(CustomEvent.class, eventContext.getMetadata().getType());
            assertTrue(eventContext.getMetadata().getQualifiers().contains(ObservedLiteral.INSTANCE));
        }

        @Override
        public boolean isAsync() {
            SyntheticObserverRecorder.isAsyncCalls.incrementAndGet();
            return async;
        }
    }

    public static class PortableExtensionObserverRecorder {
        static final AtomicInteger beforeBeanDiscoveryCalls = new AtomicInteger();
        static final AtomicInteger afterBeanDiscoveryCalls = new AtomicInteger();

        static void reset() {
            beforeBeanDiscoveryCalls.set(0);
            afterBeanDiscoveryCalls.set(0);
        }
    }

    public static class MultiObserverPortableExtension implements Extension {
        public void before(@Observes jakarta.enterprise.inject.spi.BeforeBeanDiscovery beforeBeanDiscovery) {
            PortableExtensionObserverRecorder.beforeBeanDiscoveryCalls.incrementAndGet();
        }

        public void after(@Observes AfterBeanDiscovery afterBeanDiscovery) {
            PortableExtensionObserverRecorder.afterBeanDiscoveryCalls.incrementAndGet();
        }
    }

    public interface DecoratedContract {
        String run();
    }

    @ApplicationScoped
    public static class DecoratedService implements DecoratedContract {
        @Override
        public String run() {
            return "bean";
        }
    }

    @Decorator
    public static class InvalidDecoratorWithObserverMethod implements DecoratedContract {
        @Inject
        @Delegate
        DecoratedContract delegate;

        @Override
        public String run() {
            return delegate.run();
        }

        void observe(@Observes CustomEvent ignored) {
            // Invalid by spec: decorators may not declare observer methods.
        }
    }
}
