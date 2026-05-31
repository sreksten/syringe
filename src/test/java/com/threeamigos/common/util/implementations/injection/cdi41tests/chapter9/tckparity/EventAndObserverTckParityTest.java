package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter9.tckparity;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Reception;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.inject.spi.ObserverMethod;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("9 - TCK parity for core event and observer behavior")
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
class EventAndObserverTckParityTest {
    private static final Class<?>[] FIXTURE_CLASSES = new Class<?>[]{
            InitializerBeanBroken.class,
            AlarmSystem.class,
            BreakIn.class,
            Counter.class,
            CounterObserver01.class,
            CounterObserver02.class,
            TerrierObserver.class,
            BullTerrier.class,
            Delivery.class,
            StaticObserver.class,
            PrivateObserver.class,
            MultiBindingEvent.class,
            EventPayload.class,
            Egg.class,
            Farmer.class,
            LazyFarmer.class,
            StockPriceEvent.class,
            StockWatcher.class,
            IntermediateStockWatcher.class,
            IndirectStockWatcher.class,
            AnEventType.class,
            UnusedEventType.class,
            AnObserver.class,
            StockPricePayload.class,
            StockWatcherMetadata.class,
            ConditionalEvent.class,
            ConditionalObserver.class,
            DisobedientDog.class,
            ShowDog.class,
            SmallDog.class,
            LargeDog.class,
            TransactionalObservers.class,
            IntegerObserver.class,
            Volume.class,
            OrangeCheekedWaxbill.class
    };

    @Test
    @DisplayName("9.4.2 / DeploymentFailureTest - observer method cannot be an initializer (@Inject + @Observes)")
    void shouldFailDeploymentForInjectObserverMethod() {
        Syringe syringe = newSyringe(InitializerBeanBroken.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("9.4.4 / DependentIsConditionalObserverTest - @Dependent bean cannot declare IF_EXISTS observer")
    void shouldFailDeploymentForDependentConditionalObserver() {
        Syringe syringe = newSyringe(AlarmSystem.class, BreakIn.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("9.4.1 / SyncEventModificationTest - synchronous observer event parameter modifications are propagated")
    void shouldPropagateEventModificationAcrossOrderedObservers() {
        CounterObserver02.count = 0;
        Syringe syringe = newSyringe(Counter.class, CounterObserver01.class, CounterObserver02.class);
        syringe.setup();

        syringe.getBeanManager().getEvent().select(Counter.class).fire(new Counter());

        assertEquals(3, CounterObserver02.count);
    }

    @Test
    @DisplayName("9.4.1 / EventTest - observer method parameters are injection points")
    void shouldInjectObserverMethodAdditionalParameters() {
        TerrierObserver.reset();
        Syringe syringe = newSyringe(
                BullTerrier.class,
                TerrierObserver.class,
                Volume.class,
                OrangeCheekedWaxbill.class,
                MultiBindingEvent.class
        );
        syringe.setup();

        syringe.getBeanManager().getEvent().select(BullTerrier.class).fire(new BullTerrier());

        assertTrue(TerrierObserver.eventObserved);
        assertTrue(TerrierObserver.parametersInjected);
    }

    @Test
    @DisplayName("9.4.2 / EventTest - static observer method is notified even when request context is inactive")
    void shouldInvokeStaticObserverMethodWithInactiveRequestContext() {
        StaticObserver.reset();
        Syringe syringe = newSyringe(StaticObserver.class, Delivery.class);
        syringe.setup();

        syringe.getBeanManager().getEvent().select(Delivery.class).fire(new Delivery());

        assertTrue(StaticObserver.isDeliveryReceived);
    }

    @Test
    @DisplayName("9.4.2 / EventTest - private observer method is notified")
    void shouldInvokePrivateObserverMethod() {
        PrivateObserver.reset();
        Syringe syringe = newSyringe(PrivateObserver.class, Delivery.class);
        syringe.setup();

        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        beanManager.getContextManager().activateRequest();
        try {
            beanManager.getEvent().select(Delivery.class).fire(new Delivery());
        } finally {
            beanManager.getContextManager().deactivateRequest();
        }

        assertTrue(PrivateObserver.isObserved);
    }

    @Test
    @DisplayName("9.3 / EventTest - observer is notified only when event qualifiers match")
    void shouldNotifyObserverWhenEventQualifiersMatch() {
        BullTerrier.reset();
        Syringe syringe = newSyringe(BullTerrier.class, MultiBindingEvent.class);
        syringe.setup();

        syringe.getBeanManager().getEvent()
                .select(MultiBindingEvent.class, new RoleLiteral("Admin"), TameLiteral.INSTANCE)
                .fire(new MultiBindingEvent());

        assertTrue(BullTerrier.multiBindingEventObserved);
        assertTrue(BullTerrier.singleBindingEventObserved);
    }

    @Test
    @DisplayName("9.4.2 / EventTest - inherited observer method on superclass is notified")
    void shouldInvokeInheritedObserverMethod() {
        Egg egg = new Egg();
        Syringe syringe = newSyringe(Egg.class, Farmer.class, LazyFarmer.class);
        syringe.setup();

        syringe.getBeanManager().getEvent().select(Egg.class).fire(egg);

        assertEquals(2, egg.visitedBy.size());
        assertTrue(containsAssignableObserver(egg.visitedBy, Farmer.class));
        assertTrue(containsAssignableObserver(egg.visitedBy, LazyFarmer.class));
    }

    @Test
    @DisplayName("9.4.2 / EventTest - inherited observer method through intermediate superclass is notified")
    void shouldInvokeIndirectlyInheritedObserverMethod() {
        StockPriceEvent event = new StockPriceEvent();
        Syringe syringe = newSyringe(StockPriceEvent.class, StockWatcher.class, IntermediateStockWatcher.class, IndirectStockWatcher.class);
        syringe.setup();

        syringe.getBeanManager().getEvent().select(StockPriceEvent.class).fire(event);

        assertEquals(3, event.visitedBy.size());
        assertTrue(containsAssignableObserver(event.visitedBy, StockWatcher.class));
        assertTrue(containsAssignableObserver(event.visitedBy, IntermediateStockWatcher.class));
        assertTrue(containsAssignableObserver(event.visitedBy, IndirectStockWatcher.class));
    }

    @Test
    @DisplayName("9.4.1 / EventTest - BeanManager.resolveObserverMethods rejects event type containing type variables")
    void shouldRejectTypeVariableEventObjectForObserverResolution() {
        Syringe syringe = newSyringe(StockWatcher.class, StockPriceEvent.class);
        syringe.setup();

        assertThrows(IllegalArgumentException.class, new Runnable() {
            @Override
            public void run() {
                invokeResolveObserverMethodsWithTypeVariable(syringe);
            }
        }::run);
    }

    @Test
    @DisplayName("9.3 / ChecksEventTypeWhenResolvingTest - BeanManager.resolveObserverMethods checks event type")
    void shouldCheckEventTypeWhenResolvingObservers() {
        Syringe syringe = newSyringe(AnEventType.class, AnObserver.class, UnusedEventType.class);
        syringe.setup();

        assertFalse(syringe.getBeanManager().resolveObserverMethods(new AnEventType()).isEmpty());
        assertTrue(syringe.getBeanManager().resolveObserverMethods(new UnusedEventType("name")).isEmpty());
    }

    @Test
    @DisplayName("9.8 / ObserverMethodTest - ObserverMethod metadata (bean class, declaring bean, observed type/qualifiers, reception, phase)")
    void shouldExposeObserverMethodMetadata() {
        Syringe syringe = newSyringe(
                StockPricePayload.class,
                StockWatcherMetadata.class,
                ConditionalEvent.class,
                ConditionalObserver.class,
                DisobedientDog.class,
                ShowDog.class,
                SmallDog.class,
                LargeDog.class,
                TransactionalObservers.class
        );
        syringe.setup();

        Set<ObserverMethod<? super StockPricePayload>> observers = syringe.getBeanManager().resolveObserverMethods(new StockPricePayload());
        assertEquals(1, observers.size());
        ObserverMethod<? super StockPricePayload> observerMethod = observers.iterator().next();

        assertEquals(StockWatcherMetadata.class, observerMethod.getBeanClass());
        Bean<?> declaringBean = observerMethod.getDeclaringBean();
        assertNotNull(declaringBean);
        assertEquals(StockWatcherMetadata.class, declaringBean.getBeanClass());
        assertEquals(Dependent.class, declaringBean.getScope());
        assertTrue(declaringBean.isAlternative());
        assertEquals(StockPricePayload.class, observerMethod.getObservedType());
        assertTrue(observerMethod.getObservedQualifiers().isEmpty());
        assertEquals(Reception.ALWAYS, observerMethod.getReception());
        assertEquals(TransactionPhase.IN_PROGRESS, observerMethod.getTransactionPhase());

        Set<ObserverMethod<? super ConditionalEvent>> conditional = syringe.getBeanManager().resolveObserverMethods(new ConditionalEvent());
        assertFalse(conditional.isEmpty());
        assertEquals(Reception.IF_EXISTS, conditional.iterator().next().getReception());

        assertEquals(TransactionPhase.BEFORE_COMPLETION,
                syringe.getBeanManager().resolveObserverMethods(new DisobedientDog()).iterator().next().getTransactionPhase());
        assertEquals(TransactionPhase.AFTER_COMPLETION,
                syringe.getBeanManager().resolveObserverMethods(new ShowDog()).iterator().next().getTransactionPhase());
        assertEquals(TransactionPhase.AFTER_FAILURE,
                syringe.getBeanManager().resolveObserverMethods(new SmallDog()).iterator().next().getTransactionPhase());
        assertEquals(TransactionPhase.AFTER_SUCCESS,
                syringe.getBeanManager().resolveObserverMethods(new LargeDog()).iterator().next().getTransactionPhase());
    }

    @Test
    @DisplayName("9.8 / ObserverMethodTest - ObserverMethod.notify(event) invokes observer")
    void shouldInvokeObserverMethodNotify() {
        IntegerObserver.wasNotified = false;
        Syringe syringe = newSyringe(IntegerObserver.class);
        syringe.setup();

        Integer event = Integer.valueOf(1);
        Set<ObserverMethod<? super Integer>> observers = syringe.getBeanManager().resolveObserverMethods(event, new Number.Literal());
        assertEquals(1, observers.size());

        observers.iterator().next().notify(event);
        assertTrue(IntegerObserver.wasNotified);
    }

    private Syringe newSyringe(Class<?>... classes) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), classes);
        Set<Class<?>> included = new HashSet<Class<?>>(Arrays.asList(classes));
        for (Class<?> fixture : FIXTURE_CLASSES) {
            if (!included.contains(fixture)) {
                syringe.exclude(fixture);
            }
        }
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        return syringe;
    }

    private <T> void invokeResolveObserverMethodsWithTypeVariable(Syringe syringe) {
        resolveWithTypeVariable(syringe, new ArrayList<T>());
    }

    private <E> void resolveWithTypeVariable(Syringe syringe, ArrayList<E> eventToFire) {
        syringe.getBeanManager().resolveObserverMethods(eventToFire);
    }

    private boolean containsAssignableObserver(Set<Class<?>> classes, Class<?> expected) {
        for (Class<?> observed : classes) {
            if (expected.isAssignableFrom(observed)) {
                return true;
            }
        }
        return false;
    }

    @Dependent
    public static class InitializerBeanBroken {
        @Inject
        public void initialize(@Observes String ignored) {
        }
    }

    @Dependent
    public static class AlarmSystem {
        void onBreakInAttempt(@Observes(notifyObserver = Reception.IF_EXISTS) BreakIn breakIn) {
        }
    }

    public static class BreakIn {
    }

    @Dependent
    public static class Counter {
        private int value;

        void increment() {
            value++;
        }

        int getValue() {
            return value;
        }
    }

    @Dependent
    public static class CounterObserver01 {
        void observe(@Observes @Priority(ObserverMethod.DEFAULT_PRIORITY) Counter counter) {
            counter.increment();
        }
    }

    @Dependent
    public static class CounterObserver02 {
        static int count;

        void observe(@Observes @Priority(ObserverMethod.DEFAULT_PRIORITY + 100) Counter counter) {
            counter.increment();
        }

        void observeNext(@Observes @Priority(ObserverMethod.DEFAULT_PRIORITY + 200) Counter counter) {
            counter.increment();
            count = counter.getValue();
        }
    }

    @Dependent
    public static class TerrierObserver {
        static boolean eventObserved;
        static boolean parametersInjected;

        void observeDog(@Observes BullTerrier event,
                        jakarta.enterprise.inject.spi.BeanManager beanManager,
                        @Tame Volume volume,
                        OrangeCheekedWaxbill bird) {
            eventObserved = true;
            parametersInjected = beanManager != null && volume != null && bird != null;
        }

        static void reset() {
            eventObserved = false;
            parametersInjected = false;
        }
    }

    @Dependent
    public static class BullTerrier {
        static boolean multiBindingEventObserved;
        static boolean singleBindingEventObserved;

        void observesMultiBindingEvent(@Observes @Role("Admin") @Tame MultiBindingEvent event) {
            multiBindingEventObserved = true;
        }

        void observesSingleBindingEvent(@Observes @Tame MultiBindingEvent event) {
            singleBindingEventObserved = true;
        }

        static void reset() {
            multiBindingEventObserved = false;
            singleBindingEventObserved = false;
        }
    }

    public static class Delivery {
    }

    @RequestScoped
    public static class StaticObserver {
        static boolean isDeliveryReceived;

        public static void accept(@Observes Delivery delivery) {
            isDeliveryReceived = true;
        }

        static void reset() {
            isDeliveryReceived = false;
        }
    }

    @RequestScoped
    public static class PrivateObserver {
        static boolean isObserved;

        private void observesDelivery(@Observes Delivery delivery) {
            isObserved = true;
        }

        static void reset() {
            isObserved = false;
        }
    }

    public static class MultiBindingEvent {
    }

    public static class EventPayload {
        final Set<Class<?>> visitedBy = new LinkedHashSet<Class<?>>();

        void recordVisit(Object observer) {
            visitedBy.add(observer.getClass());
        }
    }

    public static class Egg extends EventPayload {
    }

    @Dependent
    public static class Farmer {
        void observeEggLaying(@Observes Egg egg) {
            egg.recordVisit(this);
        }
    }

    @Dependent
    public static class LazyFarmer extends Farmer {
    }

    public static class StockPriceEvent extends EventPayload {
    }

    @Dependent
    public static class StockWatcher {
        void observeStockPrice(@Observes StockPriceEvent event) {
            event.recordVisit(this);
        }
    }

    @Dependent
    public static class IntermediateStockWatcher extends StockWatcher {
    }

    @Dependent
    public static class IndirectStockWatcher extends IntermediateStockWatcher {
    }

    public static class AnEventType {
    }

    public static class UnusedEventType {
        UnusedEventType(String ignored) {
        }
    }

    @Dependent
    public static class AnObserver {
        boolean wasNotified;

        void observes(@Observes AnEventType event) {
            wasNotified = true;
        }
    }

    public static class StockPricePayload {
    }

    @Dependent
    @Alternative
    @Priority(1)
    public static class StockWatcherMetadata {
        void observeStockPrice(@Observes StockPricePayload ignored) {
        }
    }

    public static class ConditionalEvent {
    }

    @RequestScoped
    public static class ConditionalObserver {
        void conditionalObserve(@Observes(notifyObserver = Reception.IF_EXISTS) ConditionalEvent event) {
        }
    }

    public static class DisobedientDog {
    }

    public static class ShowDog {
    }

    public static class SmallDog {
    }

    public static class LargeDog {
    }

    @Dependent
    public static class TransactionalObservers {
        void train(@Observes(during = TransactionPhase.BEFORE_COMPLETION) DisobedientDog dog) {
        }

        void trainNewTricks(@Observes(during = TransactionPhase.AFTER_COMPLETION) ShowDog dog) {
        }

        void trainCompanion(@Observes(during = TransactionPhase.AFTER_FAILURE) SmallDog dog) {
        }

        void trainSightSeeing(@Observes(during = TransactionPhase.AFTER_SUCCESS) LargeDog dog) {
        }
    }

    @Dependent
    public static class IntegerObserver {
        static boolean wasNotified;

        public static void observeInteger(@Observes @Number Integer event) {
            wasNotified = true;
        }
    }

    @Qualifier
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface Number {
        final class Literal extends AnnotationLiteral<Number> implements Number {
        }
    }

    @Qualifier
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface Tame {
    }

    public static final class TameLiteral extends AnnotationLiteral<Tame> implements Tame {
        static final TameLiteral INSTANCE = new TameLiteral();
    }

    @Qualifier
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface Role {
        String value();
    }

    public static final class RoleLiteral extends AnnotationLiteral<Role> implements Role {
        private final String value;

        RoleLiteral(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }
    }

    @Dependent
    @Tame
    public static class Volume {
    }

    @Dependent
    public static class OrangeCheekedWaxbill {
    }
}
