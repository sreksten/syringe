package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter9.par91eventtypesandqualifiertypes;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("9.1 - Event types and qualifier types")
@Execution(ExecutionMode.SAME_THREAD)
public class EventTypesAndQualifierTypesTest {

    @BeforeEach
    void resetRecorder() {
        ObservedEventsRecorder.reset();
    }

    @Test
    @DisplayName("9.1 - Event types include all superclasses and interfaces of the runtime class")
    void shouldDeliverEventToObserversOfRuntimeTypeSuperclassesAndInterfaces() {
        Syringe syringe = newSyringe(
                ObservedEventsRecorder.class,
                EventTypeHierarchyObserver.class
        );

        Event<Object> event = syringe.getBeanManager().getEvent();
        event.select(DogEvent.class).fire(new DogEvent());

        Set<String> observed = ObservedEventsRecorder.snapshot();
        assertTrue(observed.contains("dog"));
        assertTrue(observed.contains("animal"));
        assertTrue(observed.contains("pet"));
    }

    @Test
    @DisplayName("9.1 - Event qualifier types are standard qualifier types and qualifier-based delivery works")
    void shouldDeliverOnlyToObserverWithMatchingQualifierType() {
        Syringe syringe = newSyringe(
                ObservedEventsRecorder.class,
                QualifiedEventObserver.class
        );

        Event<Object> event = syringe.getBeanManager().getEvent();
        event.select(QualifiedPayload.class, ImportantLiteral.INSTANCE).fire(new QualifiedPayload());

        Set<String> observed = ObservedEventsRecorder.snapshot();
        assertTrue(observed.contains("qualified-important"));
        assertTrue(!observed.contains("qualified-minor"));
    }

    @Test
    @DisplayName("9.1 - Every event has qualifier @Any even when not explicitly declared")
    void shouldAlwaysHaveAnyQualifier() {
        Syringe syringe = newSyringe(
                ObservedEventsRecorder.class,
                AnyQualifiedObserver.class
        );

        Event<Object> event = syringe.getBeanManager().getEvent();
        event.select(AnyPayload.class).fire(new AnyPayload());

        assertTrue(ObservedEventsRecorder.snapshot().contains("any-observer"));
    }

    @Test
    @DisplayName("9.1 - Any Java type may be an observed event type")
    void shouldAllowObservingArbitraryJavaType() {
        Syringe syringe = newSyringe(
                ObservedEventsRecorder.class,
                PrimitiveWrapperObserver.class
        );

        Event<Object> event = syringe.getBeanManager().getEvent();
        event.select(Integer.class).fire(Integer.valueOf(42));

        assertTrue(ObservedEventsRecorder.snapshot().contains("integer-observer"));
    }

    @Test
    @DisplayName("9.1 - Wildcard event types are allowed and are not treated as unresolvable type variables")
    void shouldAllowWildcardEventTypeSelection() {
        Syringe syringe = newSyringe(
                ObservedEventsRecorder.class,
                WildcardListObserver.class
        );

        Event<Object> event = syringe.getBeanManager().getEvent();
        event.select(new TypeLiteral<List<?>>() {
        }).fire(new StringListEvent());

        assertTrue(ObservedEventsRecorder.snapshot().contains("wildcard-list-observer"));
    }

    @Test
    @DisplayName("9.1 - Event type containing unresolvable type variable is rejected")
    void shouldRejectUnresolvableTypeVariableInEventType() {
        Syringe syringe = newSyringe(ObservedEventsRecorder.class);

        Event<Object> event = syringe.getBeanManager().getEvent();
        GenericSelector<String> genericSelector = new GenericSelector<String>();

        assertThrows(IllegalArgumentException.class, () -> genericSelector.selectWithTypeVariable(event));
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();
        return syringe;
    }

    public interface PetEvent {
    }

    public static class AnimalEvent {
    }

    public static class DogEvent extends AnimalEvent implements PetEvent {
    }

    public static class AnyPayload {
    }

    public static class QualifiedPayload {
    }

    public static class StringListEvent extends ArrayList<String> {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE})
    public @interface Important {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE})
    public @interface Minor {
    }

    public static final class ImportantLiteral extends AnnotationLiteral<Important> implements Important {
        static final ImportantLiteral INSTANCE = new ImportantLiteral();
    }

    @Dependent
    public static class ObservedEventsRecorder {
        private static final Set<String> OBSERVED = new HashSet<String>();

        static synchronized void record(String event) {
            OBSERVED.add(event);
        }

        static synchronized void reset() {
            OBSERVED.clear();
        }

        static synchronized Set<String> snapshot() {
            return new HashSet<String>(OBSERVED);
        }
    }

    @Dependent
    public static class EventTypeHierarchyObserver {
        void observeDog(@Observes DogEvent event) {
            ObservedEventsRecorder.record("dog");
        }

        void observeAnimal(@Observes AnimalEvent event) {
            ObservedEventsRecorder.record("animal");
        }

        void observePet(@Observes PetEvent event) {
            ObservedEventsRecorder.record("pet");
        }
    }

    @Dependent
    public static class QualifiedEventObserver {
        void observeImportant(@Observes @Important QualifiedPayload payload) {
            ObservedEventsRecorder.record("qualified-important");
        }

        void observeMinor(@Observes @Minor QualifiedPayload payload) {
            ObservedEventsRecorder.record("qualified-minor");
        }
    }

    @Dependent
    public static class AnyQualifiedObserver {
        void observeAny(@Observes @Any AnyPayload payload) {
            ObservedEventsRecorder.record("any-observer");
        }
    }

    @Dependent
    public static class PrimitiveWrapperObserver {
        void observeInteger(@Observes Integer value) {
            ObservedEventsRecorder.record("integer-observer");
        }
    }

    @Dependent
    public static class WildcardListObserver {
        void observeWildcardList(@Observes List<?> value) {
            ObservedEventsRecorder.record("wildcard-list-observer");
        }
    }

    public static class GenericSelector<X> {
        Event<List<X>> selectWithTypeVariable(Event<Object> event) {
            return event.select(new TypeLiteral<List<X>>() {
            });
        }
    }
}
