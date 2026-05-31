package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter24.tckparity;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Stereotype;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.ProcessBeanAttributes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("24 - TCK parity for deployment discovery and trimmed bean archives")
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
class BeanDiscoveryAndTrimmedTckParityTest {

    @Test
    @DisplayName("24 / BeanDiscoveryTest - explicit archive discovers plain classes")
    void shouldDiscoverPlainClassInExplicitArchiveMode() {
        DiscoveryRecorder.reset();
        Syringe syringe = new Syringe(new InMemoryMessageHandler());
        syringe.addExtension(DiscoveryRecorder.class.getName());
        syringe.initialize();
        syringe.addDiscoveredClass(PlainAlpha.class, BeanArchiveMode.EXPLICIT);
        syringe.start();

        assertTrue(DiscoveryRecorder.discovered.contains(PlainAlpha.class));
        assertTrue(syringe.getBeanManager().getBeans(PlainAlpha.class).size() == 1);
    }

    @Test
    @DisplayName("24 / BeanDiscoveryTest - implicit archive discovers only bean-defining classes")
    void shouldDiscoverOnlyBeanDefiningClassesInImplicitArchiveMode() {
        DiscoveryRecorder.reset();
        Syringe syringe = new Syringe(new InMemoryMessageHandler());
        syringe.addExtension(DiscoveryRecorder.class.getName());
        syringe.initialize();
        syringe.addDiscoveredClass(ScopedEcho.class, BeanArchiveMode.IMPLICIT);
        syringe.addDiscoveredClass(PlainFoxtrot.class, BeanArchiveMode.IMPLICIT);
        syringe.start();

        assertTrue(DiscoveryRecorder.discovered.contains(ScopedEcho.class));
        assertFalse(DiscoveryRecorder.discovered.contains(PlainFoxtrot.class));
        assertEquals(1, syringe.getBeanManager().getBeans(ScopedEcho.class).size());
        assertEquals(0, syringe.getBeanManager().getBeans(PlainFoxtrot.class).size());
    }

    @Test
    @DisplayName("24 / BeanDiscoveryTest - archive mode none discovers no bean classes but still runs extensions")
    void shouldNotDiscoverBeansInArchiveModeNoneButStillRunExtensions() {
        DiscoveryRecorder.reset();
        Syringe syringe = new Syringe(new InMemoryMessageHandler());
        syringe.addExtension(DiscoveryRecorder.class.getName());
        syringe.initialize();
        syringe.addDiscoveredClass(ScopedEcho.class, BeanArchiveMode.NONE);
        syringe.start();

        assertTrue(DiscoveryRecorder.beforeBeanDiscoveryObserved);
        assertFalse(DiscoveryRecorder.discovered.contains(ScopedEcho.class));
        assertEquals(0, syringe.getBeanManager().getBeans(ScopedEcho.class).size());
    }

    @Test
    @DisplayName("24 / TrimmedBeanArchiveTest - trimmed archive discovers PAT for all but PBA only for bean-defining types")
    void shouldTrimBeanDiscoveryToBeanDefiningTypesAtPbaStage() {
        TrimmedRecorder.reset();
        Syringe syringe = new Syringe(new InMemoryMessageHandler());
        syringe.addExtension(TrimmedRecorder.class.getName());
        syringe.initialize();
        syringe.addDiscoveredClass(Bus.class, BeanArchiveMode.TRIMMED);
        syringe.addDiscoveredClass(Car.class, BeanArchiveMode.TRIMMED);
        syringe.addDiscoveredClass(BikeProducer.class, BeanArchiveMode.TRIMMED);
        syringe.addDiscoveredClass(Bike.class, BeanArchiveMode.TRIMMED);
        syringe.addDiscoveredClass(Segway.class, BeanArchiveMode.TRIMMED);
        syringe.start();

        assertFalse(TrimmedRecorder.bikeProducerPatFired.get());
        assertFalse(TrimmedRecorder.bikeProducerPbaFired.get());
        assertEquals(1, TrimmedRecorder.vehiclePbaInvocations.get());

        assertEquals(1, syringe.getBeanManager().getBeans(Bus.class).size());
        assertEquals(0, syringe.getBeanManager().getBeans(Car.class).size());
        assertEquals(0, syringe.getBeanManager().getBeans(Bike.class).size());
        assertEquals(0, syringe.getBeanManager().getBeans(BikeProducer.class).size());
        assertEquals(1, syringe.getBeanManager().getBeans(Segway.class).size());
    }

    public static class DiscoveryRecorder implements Extension {
        static final Set<Class<?>> discovered = new HashSet<Class<?>>();
        static volatile boolean beforeBeanDiscoveryObserved;

        static void reset() {
            discovered.clear();
            beforeBeanDiscoveryObserved = false;
        }

        public void observeBbd(@Observes jakarta.enterprise.inject.spi.BeforeBeanDiscovery bbd) {
            beforeBeanDiscoveryObserved = true;
        }

        public void observePat(@Observes ProcessAnnotatedType<?> pat) {
            discovered.add(pat.getAnnotatedType().getJavaClass());
        }
    }

    public static class TrimmedRecorder implements Extension {
        static final AtomicInteger vehiclePbaInvocations = new AtomicInteger(0);
        static final AtomicBoolean bikeProducerPbaFired = new AtomicBoolean(false);
        static final AtomicBoolean bikeProducerPatFired = new AtomicBoolean(false);

        static void reset() {
            vehiclePbaInvocations.set(0);
            bikeProducerPbaFired.set(false);
            bikeProducerPatFired.set(false);
        }

        public void observesVehiclePba(@Observes ProcessBeanAttributes<?> event) {
            for (Type type : event.getBeanAttributes().getTypes()) {
                if (type instanceof Class && MotorizedVehicle.class.equals(type)) {
                    vehiclePbaInvocations.incrementAndGet();
                    break;
                }
            }
        }

        public void observesBikeProducerPba(@Observes ProcessBeanAttributes<?> event) {
            if (BikeProducer.class.equals(event.getAnnotated().getBaseType())) {
                bikeProducerPbaFired.set(true);
            }
        }

        public void observesBikeProducerPat(@Observes ProcessAnnotatedType<?> event) {
            if (BikeProducer.class.equals(event.getAnnotatedType().getJavaClass())) {
                bikeProducerPatFired.set(true);
            }
        }
    }

    public interface MotorizedVehicle {
        String start();
    }

    public static class PlainAlpha {
    }

    @ApplicationScoped
    public static class ScopedEcho {
    }

    public static class PlainFoxtrot {
    }

    public static class Bike {
    }

    public static class BikeProducer {
        @Produces
        Bike createBike() {
            return new Bike();
        }
    }

    @ApplicationScoped
    public static class Bus implements MotorizedVehicle {
        @Override
        public String start() {
            return "Bus";
        }
    }

    public static class Car implements MotorizedVehicle {
        @Override
        public String start() {
            return "Car";
        }
    }

    @Popular
    public static class Segway {
    }

    @RequestScoped
    @Stereotype
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Popular {
    }
}
