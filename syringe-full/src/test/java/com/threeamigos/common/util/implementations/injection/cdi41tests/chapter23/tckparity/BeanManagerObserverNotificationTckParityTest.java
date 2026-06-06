package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter23.tckparity;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.event.Reception;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ObserverMethod;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Qualifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@DisplayName("23.6 - TCK parity for BeanManagerObserverNotificationTest")
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
class BeanManagerObserverNotificationTckParityTest {

    @Test
    @DisplayName("23.6 / BeanManagerObserverNotificationTest - BeanManager.getEvent().fire() notifies only matching synthetic observer")
    void shouldNotifyOnlyMatchingSyntheticObserverRegisteredByExtension() {
        GiraffeObserverRegistry.reset();
        Syringe syringe = newSyringe(Giraffe.class);
        syringe.addExtension(ObserverExtension.class.getName());
        syringe.setup();

        Giraffe payload = new Giraffe();
        syringe.getBeanManager().getEvent().select(Giraffe.class).fire(payload);

        assertSame(payload, GiraffeObserverRegistry.anyObserver.receivedPayload);
        assertNull(GiraffeObserverRegistry.fiveMeterTallObserver.receivedPayload);
        assertNull(GiraffeObserverRegistry.sixMeterTallAngryObserver.receivedPayload);
        assertNull(GiraffeObserverRegistry.angryNubianObserver.receivedPayload);
    }

    private Syringe newSyringe(Class<?>... classes) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), classes);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        return syringe;
    }

    public static class ObserverExtension implements Extension {
        public void register(@jakarta.enterprise.event.Observes AfterBeanDiscovery abd) {
            abd.addObserverMethod(GiraffeObserverRegistry.anyObserver);
            abd.addObserverMethod(GiraffeObserverRegistry.fiveMeterTallObserver);
            abd.addObserverMethod(GiraffeObserverRegistry.sixMeterTallAngryObserver);
            abd.addObserverMethod(GiraffeObserverRegistry.angryNubianObserver);
        }
    }

    static final class GiraffeObserverRegistry {
        static final RecordingObserver anyObserver = new RecordingObserver(Any.Literal.INSTANCE);
        static final RecordingObserver fiveMeterTallObserver = new RecordingObserver(TallLiteral.FIVE_METERS);
        static final RecordingObserver sixMeterTallAngryObserver =
                new RecordingObserver(TallLiteral.SIX_METERS, new AngryLiteral());
        static final RecordingObserver angryNubianObserver =
                new RecordingObserver(new AngryLiteral(), new NubianLiteral());

        static void reset() {
            anyObserver.receivedPayload = null;
            fiveMeterTallObserver.receivedPayload = null;
            sixMeterTallAngryObserver.receivedPayload = null;
            angryNubianObserver.receivedPayload = null;
        }
    }

    static final class RecordingObserver implements ObserverMethod<Giraffe> {
        private final Set<Annotation> qualifiers;
        private Giraffe receivedPayload;

        RecordingObserver(Annotation... qualifiers) {
            this.qualifiers = new HashSet<Annotation>(Arrays.asList(qualifiers));
        }

        @Override
        public Class<?> getBeanClass() {
            return getClass();
        }

        @Override
        public Type getObservedType() {
            return new TypeLiteral<Giraffe>() {
            }.getType();
        }

        @Override
        public Set<Annotation> getObservedQualifiers() {
            return qualifiers;
        }

        @Override
        public Reception getReception() {
            return Reception.ALWAYS;
        }

        @Override
        public TransactionPhase getTransactionPhase() {
            return TransactionPhase.IN_PROGRESS;
        }

        @Override
        public void notify(Giraffe event) {
            receivedPayload = event;
        }
    }

    public static class Giraffe {
    }

    @Qualifier
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Angry {
    }

    static final class AngryLiteral extends AnnotationLiteral<Angry> implements Angry {
    }

    @Qualifier
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Nubian {
    }

    static final class NubianLiteral extends AnnotationLiteral<Nubian> implements Nubian {
    }

    @Qualifier
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Tall {
        int height();
    }

    static final class TallLiteral extends AnnotationLiteral<Tall> implements Tall {
        static final TallLiteral FIVE_METERS = new TallLiteral(5);
        static final TallLiteral SIX_METERS = new TallLiteral(6);

        private final int height;

        TallLiteral(int height) {
            this.height = height;
        }

        @Override
        public int height() {
            return height;
        }
    }
}
