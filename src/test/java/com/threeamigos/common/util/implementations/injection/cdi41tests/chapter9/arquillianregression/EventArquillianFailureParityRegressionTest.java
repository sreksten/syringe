package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter9.arquillianregression;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.event.Reception;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InjectionTarget;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import jakarta.interceptor.Interceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("9 - Arquillian failing event TCK parity regressions")
@Tag("bce-conformance")
@Execution(ExecutionMode.SAME_THREAD)
class EventArquillianFailureParityRegressionTest {

    @Test
    @DisplayName("EventTypesTest parity")
    void shouldMatchEventTypesTestBehavior() {
        Syringe syringe = newSyringe(
                ETListener.class,
                ETTuneSelect.class,
                ETSong.class,
                ETEventTypeFamilyObserver.class,
                ETComplexEvent.class
        );
        try {
            ETListener listener = getContextualReference(syringe, ETListener.class);
            listener.reset();

            ETSong song = new ETSong();
            getContextualReference(syringe, ETTuneSelect.class).songPlaying(song);
            assertEquals(1, listener.getObjectsFired().size());
            assertEquals(song, listener.getObjectsFired().get(0));

            syringe.getBeanManager().getEvent().select(ETSong.class).fire(song);
            assertEquals(2, listener.getObjectsFired().size());
            assertEquals(song, listener.getObjectsFired().get(1));

            ETBroadcast broadcast = new ETBroadcast() {
            };
            getContextualReference(syringe, ETTuneSelect.class).broadcastPlaying(broadcast);
            assertEquals(3, listener.getObjectsFired().size());
            assertEquals(broadcast, listener.getObjectsFired().get(2));

            syringe.getBeanManager().getEvent().select(ETExtraLiteral.INSTANCE).fire(Integer.valueOf(1));
            assertEquals(4, listener.getObjectsFired().size());
            assertEquals(Integer.valueOf(1), listener.getObjectsFired().get(3));

            listener.reset();
            ETSong[] songArray = new ETSong[]{new ETSong()};
            syringe.getBeanManager().getEvent().select(ETSong[].class).fire(songArray);
            assertEquals(1, listener.getObjectsFired().size());
            assertTrue(listener.getObjectsFired().get(0) instanceof ETSong[]);
            assertEquals(songArray, listener.getObjectsFired().get(0));

            Integer[] integerArray = new Integer[]{0, 1};
            syringe.getBeanManager().getEvent().select(Integer[].class).fire(integerArray);
            assertEquals(2, listener.getObjectsFired().size());
            assertTrue(listener.getObjectsFired().get(1) instanceof Integer[]);
            assertEquals(integerArray, listener.getObjectsFired().get(1));

            int[] intArray = new int[]{1, 2};
            syringe.getBeanManager().getEvent().select(int[].class).fire(intArray);
            assertEquals(3, listener.getObjectsFired().size());
            assertTrue(listener.getObjectsFired().get(2) instanceof int[]);
            assertEquals(intArray, listener.getObjectsFired().get(2));

            ETEventTypeFamilyObserver observer = getContextualReference(syringe, ETEventTypeFamilyObserver.class);
            observer.reset();
            syringe.getBeanManager().getEvent().select(ETComplexEvent.class).fire(new ETComplexEvent());
            assertEquals(1, observer.getGeneralEventQuantity());
            assertEquals(1, observer.getAbstractEventQuantity());
            assertEquals(1, observer.getComplexEventQuantity());
            assertEquals(1, observer.getObjectEventQuantity());
            assertEquals(4, observer.getTotalEventsObserved());
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("ConditionalObserverTest parity")
    void shouldMatchConditionalObserverTestBehavior() throws Exception {
        WidowSpider.reset();
        RecluseSpider.reset();
        Tarantula.reset();
        AsyncConditionalObserver.reset();

        Syringe syringe = newSyringe(
                WidowSpider.class,
                RecluseSpider.class,
                Web.class,
                Tarantula.class,
                TarantulaEvent.class,
                ConditionalEvent.class,
                AsyncConditionalObserver.class,
                AsyncConditionalEvent.class
        );
        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        boolean requestActive = false;
        try {
            beanManager.getContextManager().activateRequest();
            requestActive = true;
            Event<ConditionalEvent> conditionalEvents = beanManager.getEvent().select(ConditionalEvent.class);

            conditionalEvents.fire(new ConditionalEvent());
            assertFalse(WidowSpider.isNotified());

            WidowSpider widowSpider = getContextualReference(syringe, WidowSpider.class);
            widowSpider.ping();
            assertFalse(widowSpider.isInstanceNotified());

            conditionalEvents.fire(new ConditionalEvent());
            assertTrue(WidowSpider.isNotified());
            assertTrue(widowSpider.isInstanceNotified());

            RecluseSpider recluseSpider = getContextualReference(syringe, RecluseSpider.class);
            recluseSpider.setWeb(new Web());
            conditionalEvents.fire(new ConditionalEvent());
            assertTrue(recluseSpider.isInstanceNotified());
            assertEquals(1, recluseSpider.getWeb().getRings());

            Tarantula tarantula = getContextualReference(syringe, Tarantula.class);
            tarantula.ping();
            beanManager.getContextManager().deactivateRequest();
            requestActive = false;

            Event<TarantulaEvent> tarantulaEvents = beanManager.getEvent().select(TarantulaEvent.class);
            tarantulaEvents.fire(new TarantulaEvent());
            assertFalse(Tarantula.isNotified());

            beanManager.getContextManager().activateRequest();
            requestActive = true;
            getContextualReference(syringe, Tarantula.class).ping();
            tarantulaEvents.fire(new TarantulaEvent());
            assertTrue(Tarantula.isNotified());

            Event<AsyncConditionalEvent> asyncEvents = beanManager.getEvent().select(AsyncConditionalEvent.class);
            asyncEvents.fireAsync(new AsyncConditionalEvent()).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertFalse(AsyncConditionalObserver.isNotified().get());

            AsyncConditionalObserver observer = getContextualReference(syringe, AsyncConditionalObserver.class);
            observer.ping();
            asyncEvents.fireAsync(new AsyncConditionalEvent()).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertTrue(AsyncConditionalObserver.isNotified().get());
        } finally {
            if (requestActive) {
                try {
                    beanManager.getContextManager().deactivateRequest();
                } catch (Exception ignored) {
                    // Best effort cleanup for this regression test.
                }
            }
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("EventObserverOrderingTest parity")
    void shouldMatchEventObserverOrderingTestBehavior() {
        PrioritySequenceRecorder.reset();
        Syringe syringe = newSyringe(
                Sunrise.class,
                Sunset.class,
                MoonActivity.class,
                Moonrise.class,
                SunriseAsianObserver.class,
                SunriseGermanObserver.class,
                SunriseItalianObserver.class,
                SunsetAsianObserver.class,
                SunsetEuropeanObserver.class,
                SunsetAmericanObserver.class,
                MoonObserver1.class,
                MoonObserver2.class,
                MoonObserver3.class,
                MoonObserver4.class
        );
        try {
            BeanManager beanManager = syringe.getBeanManager();

            beanManager.getEvent().select(Sunrise.class).fire(new Sunrise());
            List<String> sunriseSequence = PrioritySequenceRecorder.snapshot();
            assertEquals(3, sunriseSequence.size());
            assertEquals(SunriseAsianObserver.class.getName(), sunriseSequence.get(0));
            assertTrue(sunriseSequence.contains(SunriseGermanObserver.class.getName()));
            assertTrue(sunriseSequence.contains(SunriseItalianObserver.class.getName()));

            Set<jakarta.enterprise.inject.spi.ObserverMethod<? super Sunrise>> sunriseObservers =
                    beanManager.resolveObserverMethods(new Sunrise());
            assertEquals(3, sunriseObservers.size());
            assertEquals(SunriseAsianObserver.class, sunriseObservers.iterator().next().getBeanClass());
            Set<Class<?>> sunriseClasses = new HashSet<Class<?>>();
            for (jakarta.enterprise.inject.spi.ObserverMethod<? super Sunrise> observer : sunriseObservers) {
                sunriseClasses.add(observer.getBeanClass());
            }
            assertTrue(sunriseClasses.contains(SunriseGermanObserver.class));
            assertTrue(sunriseClasses.contains(SunriseItalianObserver.class));

            PrioritySequenceRecorder.reset();
            beanManager.getEvent().select(Sunset.class).fire(new Sunset());
            List<String> sunsetSequence = PrioritySequenceRecorder.snapshot();
            assertEquals(3, sunsetSequence.size());
            assertEquals(SunsetAsianObserver.class.getName(), sunsetSequence.get(0));
            assertEquals(SunsetEuropeanObserver.class.getName(), sunsetSequence.get(1));
            assertEquals(SunsetAmericanObserver.class.getName(), sunsetSequence.get(2));

            PrioritySequenceRecorder.reset();
            beanManager.getEvent().select(Moonrise.class).fire(new Moonrise());
            List<String> moonriseSequence = PrioritySequenceRecorder.snapshot();
            assertEquals(4, moonriseSequence.size());
            assertEquals(MoonObserver1.class.getName(), moonriseSequence.get(0));
            assertEquals(MoonObserver2.class.getName(), moonriseSequence.get(1));
            assertEquals(MoonObserver3.class.getName(), moonriseSequence.get(2));
            assertEquals(MoonObserver4.class.getName(), moonriseSequence.get(3));

            PrioritySequenceRecorder.reset();
            beanManager.getEvent().select(MoonActivity.class).fire(new MoonActivity());
            List<String> moonActivitySequence = PrioritySequenceRecorder.snapshot();
            assertEquals(2, moonActivitySequence.size());
            assertEquals(MoonObserver1.class.getName(), moonActivitySequence.get(0));
            assertEquals(MoonObserver3.class.getName(), moonActivitySequence.get(1));
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("ParameterizedEventTest parity")
    void shouldMatchParameterizedEventTestBehavior() {
        Syringe syringe = newSyringe(
                ParameterizedEventObserver.class,
                IntegerListObserver.class,
                StringListObserver.class,
                Foo.class,
                Bar.class,
                Baz.class,
                Blah.class
        );
        try {
            BeanManager beanManager = syringe.getBeanManager();
            Event<Object> event = beanManager.getEvent();
            EventObserverState observer = getContextualReference(syringe, ParameterizedEventObserver.class);
            IntegerListObserver integerObserver = getContextualReference(syringe, IntegerListObserver.class);
            StringListObserver stringObserver = getContextualReference(syringe, StringListObserver.class);

            resetParameterizedObservers(observer, integerObserver, stringObserver);
            event.select(new TypeLiteral<Bar<List<Integer>>>() {
            }).fire(new Bar<List<Integer>>());

            assertTrue(observer.isIntegerListFooableObserved());
            assertTrue(observer.isIntegerListFooObserved());
            assertTrue(observer.isIntegerListBarObserved());
            assertFalse(observer.isBazObserved());
            assertFalse(observer.isStringListFooableObserved());
            assertTrue(integerObserver.isFooableObserved());
            assertTrue(integerObserver.isFooObserved());
            assertTrue(integerObserver.isBarObserved());
            assertFalse(stringObserver.isFooableObserved());
            assertFalse(stringObserver.isFooObserved());
            assertFalse(stringObserver.isBarObserved());

            resetParameterizedObservers(observer, integerObserver, stringObserver);
            event.select(new TypeLiteral<Foo<List<Integer>>>() {
            }).fire(new Foo<List<Integer>>());
            assertTrue(observer.isIntegerListFooableObserved());
            assertTrue(observer.isIntegerListFooObserved());
            assertFalse(observer.isIntegerListBarObserved());
            assertFalse(observer.isBazObserved());

            resetParameterizedObservers(observer, integerObserver, stringObserver);
            event.select(new TypeLiteral<Foo<List<Integer>>>() {
            }).fire(new Bar<List<Integer>>());
            assertTrue(observer.isIntegerListFooableObserved());
            assertTrue(observer.isIntegerListFooObserved());
            assertTrue(observer.isIntegerListBarObserved());

            resetParameterizedObservers(observer, integerObserver, stringObserver);
            event.select(new TypeLiteral<List<Character>>() {
            }).fire(new ArrayList<Character>());
            assertTrue(observer.isCharacterListObserved());

            resetParameterizedObservers(observer, integerObserver, stringObserver);
            event.select(new TypeLiteral<Bar<List<Integer>>>() {
            }).fire(new Baz());
            assertTrue(observer.isIntegerListFooableObserved());
            assertTrue(observer.isIntegerListFooObserved());
            assertTrue(observer.isIntegerListBarObserved());
            assertTrue(observer.isBazObserved());

            assertThrows(IllegalArgumentException.class, () ->
                    event.select(new TypeLiteral<Foo<List<Integer>>>() {
                    }).fire(new Blah<List<Integer>, Integer>()));
            assertThrows(IllegalArgumentException.class, () -> unresolvedTypeVariableCase2(event));
            assertThrows(IllegalArgumentException.class, () -> unresolvedTypeVariableCase3(event));

            resetParameterizedObservers(observer, integerObserver, stringObserver);
            event.select(new TypeLiteral<Foo<? extends Number>>() {
            }).fire(new Bar<Integer>());
            assertTrue(observer.isIntegerFooObserved());
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("Arquillian CDIInjectionEnricher style member injection parity")
    void shouldInjectArquillianStyleTestClassesWithEventFields() {
        Syringe syringe = newSyringe(
                ETSong.class,
                ETListener.class,
                ETTuneSelect.class,
                ConditionalEvent.class,
                AsyncConditionalEvent.class,
                Sunset.class,
                Sunrise.class,
                Foo.class,
                Bar.class,
                Blah.class,
                Baz.class,
                ParameterizedEventObserver.class,
                IntegerListObserver.class,
                StringListObserver.class
        );
        try {
            BeanManager beanManager = syringe.getBeanManager();

            EventTypesLikeArquillianClass eventTypesLike = assertDoesNotThrow(() -> injectLikeArquillian(beanManager, new EventTypesLikeArquillianClass()));
            assertNotNull(eventTypesLike.beanManager);
            assertNotNull(eventTypesLike.songEvent);
            assertNotNull(eventTypesLike.songArrayEvent);
            assertNotNull(eventTypesLike.intArrayEvent);

            ConditionalLikeArquillianClass conditionalLike = assertDoesNotThrow(() -> injectLikeArquillian(beanManager, new ConditionalLikeArquillianClass()));
            assertNotNull(conditionalLike.beanManager);
            assertNotNull(conditionalLike.asyncConditionalEventEvent);

            OrderingLikeArquillianClass orderingLike = assertDoesNotThrow(() -> injectLikeArquillian(beanManager, new OrderingLikeArquillianClass()));
            assertNotNull(orderingLike.beanManager);
            assertNotNull(orderingLike.sunset);
            assertNotNull(orderingLike.sunrise);

            ParameterizedLikeArquillianClass parameterizedLike = assertDoesNotThrow(() -> injectLikeArquillian(beanManager, new ParameterizedLikeArquillianClass()));
            assertNotNull(parameterizedLike.beanManager);
            assertNotNull(parameterizedLike.event);
            assertNotNull(parameterizedLike.integerListFooEvent);
            assertNotNull(parameterizedLike.integerListBarEvent);
            assertNotNull(parameterizedLike.fooEvent);
            assertNotNull(parameterizedLike.observer);
            assertNotNull(parameterizedLike.integerObserver);
            assertNotNull(parameterizedLike.stringObserver);
        } finally {
            syringe.shutdown();
        }
    }

    private <T> T injectLikeArquillian(BeanManager beanManager, T instance) {
        @SuppressWarnings("unchecked")
        Class<T> type = (Class<T>) instance.getClass();
        CreationalContext<T> creationalContext = beanManager.createCreationalContext(null);
        @SuppressWarnings("unchecked")
        InjectionTarget<T> injectionTarget = (InjectionTarget<T>) beanManager
                .getInjectionTargetFactory(beanManager.createAnnotatedType(type))
                .createInjectionTarget(null);
        injectionTarget.inject(instance, creationalContext);
        return instance;
    }

    private <T> T getContextualReference(Syringe syringe, Class<T> beanClass) {
        BeanManager beanManager = syringe.getBeanManager();
        Set<Bean<?>> beans = beanManager.getBeans(beanClass);
        Bean<?> resolved = beanManager.resolve(beans);
        if (resolved == null) {
            throw new IllegalStateException("No bean resolved for " + beanClass.getName());
        }
        @SuppressWarnings("unchecked")
        Bean<T> typedBean = (Bean<T>) resolved;
        return beanClass.cast(beanManager.getReference(typedBean, beanClass, beanManager.createCreationalContext(typedBean)));
    }

    private Syringe newSyringe(Class<?>... classes) {
        Syringe syringe = new Syringe();
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.initialize();
        for (Class<?> clazz : classes) {
            syringe.addDiscoveredClass(clazz, BeanArchiveMode.EXPLICIT);
        }
        syringe.start();
        return syringe;
    }

    private <T> void unresolvedTypeVariableCase2(Event<Object> event) {
        unresolvedTypeVariableCase2Internal(event);
    }

    private <T> void unresolvedTypeVariableCase2Internal(Event<Object> event) {
        event.select(new TypeLiteral<Map<Exception, T>>() {
        }).fire(new HashMap<Exception, T>());
    }

    private <T> void unresolvedTypeVariableCase3(Event<Object> event) {
        unresolvedTypeVariableCase3Internal(event);
    }

    private <T> void unresolvedTypeVariableCase3Internal(Event<Object> event) {
        event.select(new TypeLiteral<ArrayList<List<List<List<T>>>>>() {
        }).fire(new ArrayList<List<List<List<T>>>>());
    }

    private void resetParameterizedObservers(EventObserverState observer,
                                             IntegerListObserver integerObserver,
                                             StringListObserver stringObserver) {
        observer.reset();
        integerObserver.reset();
        stringObserver.reset();
    }

    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Qualifier
    public @interface ETExtra {
    }

    public static class ETExtraLiteral extends AnnotationLiteral<ETExtra> implements ETExtra {
        static final ETExtraLiteral INSTANCE = new ETExtraLiteral();
    }

    public interface ETGeneralEvent {
    }

    public abstract static class ETAbstractEvent implements ETGeneralEvent {
    }

    public static class ETComplexEvent extends ETAbstractEvent implements ETGeneralEvent {
    }

    public static class ETSong {
    }

    public abstract static class ETBroadcast {
    }

    @ApplicationScoped
    public static class ETListener {
        private final List<Object> objectsFired = new ArrayList<Object>();

        void registerNumberFired(@Observes @ETExtra Integer value) {
            objectsFired.add(value);
        }

        void registerSongFired(@Observes ETSong song) {
            objectsFired.add(song);
        }

        void registerBroadcastFired(@Observes ETBroadcast broadcast) {
            objectsFired.add(broadcast);
        }

        void registerArrayOfSongs(@Observes ETSong[] songs) {
            objectsFired.add(songs);
        }

        void registerArrayOfNumbers(@Observes Integer[] integers) {
            objectsFired.add(integers);
        }

        void registerArrayOfNumberPrimitives(@Observes int[] integers) {
            objectsFired.add(integers);
        }

        public List<Object> getObjectsFired() {
            return objectsFired;
        }

        public void reset() {
            objectsFired.clear();
        }
    }

    @Dependent
    public static class ETTuneSelect {
        @Inject
        Event<ETSong> songEvent;

        @Inject
        Event<ETBroadcast> broadcastEvent;

        public void songPlaying(ETSong song) {
            songEvent.fire(song);
        }

        public void broadcastPlaying(ETBroadcast broadcast) {
            broadcastEvent.fire(broadcast);
        }
    }

    @Dependent
    public static class ETEventTypeFamilyObserver {
        // Mirrors original CDI TCK helper (dependent bean with static counters).
        private static int objectEventQuantity;
        private static int generalEventQuantity;
        private static int abstractEventQuantity;
        private static int complexEventQuantity;

        void observeObject(@Observes Object event) {
            if (event instanceof ETComplexEvent) {
                objectEventQuantity++;
            }
        }

        void observeGeneralEvent(@Observes ETGeneralEvent event) {
            generalEventQuantity++;
        }

        void observeAbstractEvent(@Observes ETAbstractEvent event) {
            abstractEventQuantity++;
        }

        void observeComplexEvent(@Observes ETComplexEvent event) {
            complexEventQuantity++;
        }

        public void reset() {
            objectEventQuantity = 0;
            generalEventQuantity = 0;
            abstractEventQuantity = 0;
            complexEventQuantity = 0;
        }

        int getGeneralEventQuantity() {
            return generalEventQuantity;
        }

        int getAbstractEventQuantity() {
            return abstractEventQuantity;
        }

        int getComplexEventQuantity() {
            return complexEventQuantity;
        }

        int getObjectEventQuantity() {
            return objectEventQuantity;
        }

        int getTotalEventsObserved() {
            return objectEventQuantity + generalEventQuantity + abstractEventQuantity + complexEventQuantity;
        }
    }

    public static class ConditionalEvent {
    }

    public static class TarantulaEvent {
    }

    public static class AsyncConditionalEvent {
    }

    @RequestScoped
    public static class WidowSpider {
        private static boolean notified;
        private static boolean instanceNotified;

        static void reset() {
            notified = false;
            instanceNotified = false;
        }

        void observe(@Observes(notifyObserver = Reception.IF_EXISTS) ConditionalEvent event) {
            notified = true;
            instanceNotified = true;
        }

        void ping() {
        }

        static boolean isNotified() {
            return notified;
        }

        boolean isInstanceNotified() {
            return instanceNotified;
        }
    }

    @RequestScoped
    public static class RecluseSpider {
        private static boolean instanceNotified;
        private Web web;

        static void reset() {
            instanceNotified = false;
        }

        void observe(@Observes(notifyObserver = Reception.IF_EXISTS) ConditionalEvent event) {
            instanceNotified = true;
            if (web != null) {
                web.addRing();
            }
        }

        boolean isInstanceNotified() {
            return instanceNotified;
        }

        Web getWeb() {
            return web;
        }

        void setWeb(Web web) {
            this.web = web;
        }
    }

    @RequestScoped
    public static class Tarantula {
        private static boolean notified;

        static void reset() {
            notified = false;
        }

        void observe(@Observes(notifyObserver = Reception.IF_EXISTS) TarantulaEvent event) {
            notified = true;
        }

        void ping() {
        }

        static boolean isNotified() {
            return notified;
        }
    }

    @ApplicationScoped
    public static class AsyncConditionalObserver {
        private static final AtomicBoolean NOTIFIED = new AtomicBoolean(false);

        static void reset() {
            NOTIFIED.set(false);
        }

        static AtomicBoolean isNotified() {
            return NOTIFIED;
        }

        void observeAsync(@ObservesAsync(notifyObserver = Reception.IF_EXISTS) AsyncConditionalEvent event) {
            NOTIFIED.set(true);
        }

        void ping() {
        }
    }

    @Dependent
    public static class Web {
        private int rings;

        void addRing() {
            rings++;
        }

        int getRings() {
            return rings;
        }
    }

    public static class Sunrise {
    }

    public static class Sunset {
    }

    public static class MoonActivity {
    }

    public static class Moonrise extends MoonActivity {
    }

    @Dependent
    public static class SunriseAsianObserver {
        void observe(@Observes @Priority(Interceptor.Priority.APPLICATION + 499) Sunrise sunrise) {
            PrioritySequenceRecorder.add(getClass().getName());
        }
    }

    @Dependent
    public static class SunriseGermanObserver {
        void observe(@Observes Sunrise sunrise) {
            PrioritySequenceRecorder.add(getClass().getName());
        }
    }

    @Dependent
    public static class SunriseItalianObserver {
        void observe(@Observes Sunrise sunrise) {
            PrioritySequenceRecorder.add(getClass().getName());
        }
    }

    @Dependent
    public static class SunsetAsianObserver {
        void observe(@Observes @Priority(2599) Sunset sunset) {
            PrioritySequenceRecorder.add(getClass().getName());
        }
    }

    @Dependent
    public static class SunsetEuropeanObserver {
        void observe(@Observes @Priority(2600) Sunset sunset) {
            PrioritySequenceRecorder.add(getClass().getName());
        }
    }

    @Dependent
    public static class SunsetAmericanObserver {
        void observe(@Observes @Priority(2700) Sunset sunset) {
            PrioritySequenceRecorder.add(getClass().getName());
        }
    }

    @Dependent
    public static class MoonObserver1 {
        void observe(@Observes @Priority(Interceptor.Priority.APPLICATION) MoonActivity event) {
            PrioritySequenceRecorder.add(getClass().getName());
        }
    }

    @Dependent
    public static class MoonObserver2 {
        void observe(@Observes Moonrise event) {
            PrioritySequenceRecorder.add(getClass().getName());
        }
    }

    @Dependent
    public static class MoonObserver3 {
        void observe(@Observes @Priority(Interceptor.Priority.APPLICATION + 900) MoonActivity event) {
            PrioritySequenceRecorder.add(getClass().getName());
        }
    }

    @Dependent
    public static class MoonObserver4 {
        void observe(@Observes @Priority(Interceptor.Priority.APPLICATION + 950) Moonrise event) {
            PrioritySequenceRecorder.add(getClass().getName());
        }
    }

    public static class PrioritySequenceRecorder {
        private static final List<String> SEQUENCE = new ArrayList<String>();

        static synchronized void reset() {
            SEQUENCE.clear();
        }

        static synchronized void add(String value) {
            SEQUENCE.add(value);
        }

        static synchronized List<String> snapshot() {
            return new ArrayList<String>(SEQUENCE);
        }
    }

    public interface Fooable<F> {
    }

    public static class Foo<F> implements Fooable<F> {
    }

    public static class Bar<B> extends Foo<B> {
    }

    public static class Baz extends Bar<List<Integer>> {
    }

    public static class Blah<B1, B2> extends Bar<B1> {
    }

    public interface EventObserverState {
        void reset();

        boolean isIntegerListFooableObserved();

        boolean isStringListFooableObserved();

        boolean isIntegerListFooObserved();

        boolean isIntegerListBarObserved();

        boolean isBazObserved();

        boolean isCharacterListObserved();

        boolean isIntegerFooObserved();
    }

    @ApplicationScoped
    public static class ParameterizedEventObserver implements EventObserverState {
        private boolean integerListFooableObserved;
        private boolean stringListFooableObserved;
        private boolean integerListFooObserved;
        private boolean integerListBarObserved;
        private boolean bazObserved;
        private boolean characterListObserved;
        private boolean integerFooObserved;

        void observeIntegerFooable(@Observes Fooable<List<Integer>> event) {
            integerListFooableObserved = true;
        }

        void observeStringFooable(@Observes Fooable<List<String>> event) {
            stringListFooableObserved = true;
        }

        void observeIntegerFoo(@Observes Foo<List<Integer>> event) {
            integerListFooObserved = true;
        }

        void observeIntegerBar(@Observes Bar<List<Integer>> event) {
            integerListBarObserved = true;
        }

        void observeBaz(@Observes Baz event) {
            bazObserved = true;
        }

        void observeCharacterList(@Observes List<Character> event) {
            characterListObserved = true;
        }

        void observeIntegerFooWildcard(@Observes Foo<? extends Number> event) {
            integerFooObserved = true;
        }

        @Override
        public void reset() {
            integerListFooableObserved = false;
            stringListFooableObserved = false;
            integerListFooObserved = false;
            integerListBarObserved = false;
            bazObserved = false;
            characterListObserved = false;
            integerFooObserved = false;
        }

        @Override
        public boolean isIntegerListFooableObserved() {
            return integerListFooableObserved;
        }

        @Override
        public boolean isStringListFooableObserved() {
            return stringListFooableObserved;
        }

        @Override
        public boolean isIntegerListFooObserved() {
            return integerListFooObserved;
        }

        @Override
        public boolean isIntegerListBarObserved() {
            return integerListBarObserved;
        }

        @Override
        public boolean isBazObserved() {
            return bazObserved;
        }

        @Override
        public boolean isCharacterListObserved() {
            return characterListObserved;
        }

        @Override
        public boolean isIntegerFooObserved() {
            return integerFooObserved;
        }
    }

    public static abstract class AbstractParameterizedObserver<T> {
        private boolean fooableObserved;
        private boolean fooObserved;
        private boolean barObserved;

        void observeFooable(@Observes Fooable<T> event) {
            fooableObserved = true;
        }

        void observeFoo(@Observes Foo<T> event) {
            fooObserved = true;
        }

        void observeBar(@Observes Bar<T> event) {
            barObserved = true;
        }

        boolean isFooableObserved() {
            return fooableObserved;
        }

        boolean isFooObserved() {
            return fooObserved;
        }

        boolean isBarObserved() {
            return barObserved;
        }

        void reset() {
            fooableObserved = false;
            fooObserved = false;
            barObserved = false;
        }
    }

    @ApplicationScoped
    public static class IntegerListObserver extends AbstractParameterizedObserver<List<Integer>> {
    }

    @ApplicationScoped
    public static class StringListObserver extends AbstractParameterizedObserver<List<String>> {
    }

    public abstract static class AbstractArquillianLike {
        @Inject
        BeanManager beanManager;
    }

    public static class EventTypesLikeArquillianClass extends AbstractArquillianLike {
        @Inject
        Event<ETSong> songEvent;

        @Inject
        Event<ETSong[]> songArrayEvent;

        @Inject
        Event<int[]> intArrayEvent;
    }

    public static class ConditionalLikeArquillianClass extends AbstractArquillianLike {
        @Inject
        Event<AsyncConditionalEvent> asyncConditionalEventEvent;
    }

    public static class OrderingLikeArquillianClass extends AbstractArquillianLike {
        @Inject
        Event<Sunset> sunset;

        @Inject
        Event<Sunrise> sunrise;
    }

    public static class ParameterizedLikeArquillianClass extends AbstractArquillianLike {
        @Inject
        Event<Object> event;

        @Inject
        Event<Foo<List<Integer>>> integerListFooEvent;

        @Inject
        Event<Bar<List<Integer>>> integerListBarEvent;

        @Inject
        Event<Foo<? extends Number>> fooEvent;

        @Inject
        ParameterizedEventObserver observer;

        @Inject
        IntegerListObserver integerObserver;

        @Inject
        StringListObserver stringObserver;
    }
}
