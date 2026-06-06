package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par50introduction;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("5 - Circular dependency regression")
public class CircularDependencyRegressionTest {

    @Test
    @DisplayName("5 / Injection and resolution - Circular injection between normal scoped beans is resolvable")
    void shouldResolveCircularInjectionBetweenTwoNormalScopedBeans() {
        Syringe syringe = newSyringe(Pig.class, Food.class);
        try {
            RequestContextController controller = resolveRequestContextController(syringe);
            controller.activate();
            try {
                Pig pig = syringe.inject(Pig.class);
                Food food = syringe.inject(Food.class);
                assertEquals(food.getName(), pig.getNameOfFood());
                assertEquals(pig.getName(), food.getNameOfPig());
            } finally {
                controller.deactivate();
            }
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("5 / Injection and resolution - Circular injection between normal and dependent beans is resolvable")
    void shouldResolveCircularInjectionBetweenNormalAndDependentBean() {
        Syringe syringe = newSyringe(Petrol.class, Car.class);
        try {
            Petrol petrol = syringe.inject(Petrol.class);
            Car car = syringe.inject(Car.class);
            assertEquals(car.getName(), petrol.getNameOfCar());
            assertEquals(petrol.getName(), car.getNameOfPetrol());
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("5 / Injection and resolution - Self-consuming normal producer on normal bean is resolvable")
    void shouldResolveNormalSelfConsumingNormalProducer() {
        Syringe syringe = newSyringe(NormalSelfConsumingNormalProducer.class, Violation.class);
        try {
            syringe.inject(NormalSelfConsumingNormalProducer.class).ping();
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("5 / Injection and resolution - Self-consuming normal producer on dependent bean is resolvable")
    void shouldResolveDependentSelfConsumingNormalProducer() {
        Syringe syringe = newSyringe(DependentSelfConsumingNormalProducer.class, Violation.class);
        try {
            syringe.inject(DependentSelfConsumingNormalProducer.class).ping();
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("5 / Injection and resolution - Circular constructors on two normal beans are resolvable")
    void shouldResolveNormalCircularConstructors() {
        Syringe syringe = newSyringe(Bird.class, Air.class);
        try {
            assertNotNull(syringe.inject(Bird.class));
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("5 / Injection and resolution - Circular constructors on normal and dependent beans are resolvable")
    void shouldResolveNormalAndDependentCircularConstructors() {
        Syringe syringe = newSyringe(Planet.class, Space.class);
        try {
            assertNotNull(syringe.inject(Planet.class));
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("5 / Injection and resolution - Self-consuming constructor on normal bean is resolvable")
    void shouldResolveSelfConsumingConstructorOnNormalBean() {
        Syringe syringe = newSyringe(House.class);
        try {
            assertNotNull(syringe.inject(House.class));
        } finally {
            syringe.shutdown();
        }
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

    @SuppressWarnings("unchecked")
    private RequestContextController resolveRequestContextController(Syringe syringe) {
        Bean<RequestContextController> controllerBean = (Bean<RequestContextController>) syringe.getBeanManager()
                .resolve(syringe.getBeanManager().getBeans(RequestContextController.class));
        return (RequestContextController) syringe.getBeanManager().getReference(
                controllerBean,
                RequestContextController.class,
                syringe.getBeanManager().createCreationalContext(controllerBean)
        );
    }

    @RequestScoped
    public static class Pig implements Serializable {
        private static final long serialVersionUID = 1L;

        @Inject
        Food food;

        public String getName() {
            return "john";
        }

        public String getNameOfFood() {
            return food.getName();
        }
    }

    @ApplicationScoped
    public static class Food {
        @Inject
        Pig pig;

        public String getName() {
            return "food";
        }

        public String getNameOfPig() {
            return pig.getName();
        }
    }

    @ApplicationScoped
    public static class Petrol {
        @Inject
        Car car;

        public String getName() {
            return "petrol";
        }

        public String getNameOfCar() {
            return car.getName();
        }
    }

    @Dependent
    public static class Car {
        @Inject
        Petrol petrol;

        public String getName() {
            return "herbie";
        }

        public String getNameOfPetrol() {
            return petrol.getName();
        }
    }

    @ApplicationScoped
    public static class NormalSelfConsumingNormalProducer {
        @Inject
        @SelfConsumingNormal
        Violation violation;

        @Produces
        @ApplicationScoped
        @SelfConsumingNormal
        public Violation produceViolation() {
            return new Violation(NormalSelfConsumingNormalProducer.class.getName());
        }

        public void ping() {
            violation.ping();
        }
    }

    @Dependent
    public static class DependentSelfConsumingNormalProducer {
        @Inject
        @SelfConsumingNormal1
        Violation violation;

        @Produces
        @ApplicationScoped
        @SelfConsumingNormal1
        public Violation produceViolation() {
            return new Violation(DependentSelfConsumingNormalProducer.class.getName());
        }

        public void ping() {
            violation.ping();
        }
    }

    @ApplicationScoped
    public static class Bird {
        public Bird() {
        }

        @Inject
        public Bird(Air air) {
        }
    }

    @ApplicationScoped
    public static class Air {
        public Air() {
        }

        @Inject
        public Air(Bird bird) {
        }
    }

    @Dependent
    public static class Planet {
        @Inject
        public Planet(Space space) {
        }
    }

    @ApplicationScoped
    public static class Space {
        public Space() {
        }

        @Inject
        public Space(Planet planet) {
        }
    }

    @ApplicationScoped
    public static class House {
        public House() {
        }

        @Inject
        public House(House house) {
            house.ping();
        }

        private void ping() {
        }
    }

    @Target({TYPE, METHOD, PARAMETER, FIELD})
    @Retention(RUNTIME)
    @Documented
    @Qualifier
    public @interface SelfConsumingNormal {
    }

    @Target({TYPE, METHOD, PARAMETER, FIELD})
    @Retention(RUNTIME)
    @Documented
    @Qualifier
    public @interface SelfConsumingNormal1 {
    }

    public static class Violation implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String producer;

        public Violation() {
            this.producer = null;
        }

        public Violation(String producer) {
            this.producer = producer;
        }

        void ping() {
            // no-op
        }

        String getProducer() {
            return producer;
        }
    }
}
