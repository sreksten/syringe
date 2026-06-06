package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.tckparity.memberlevelinheritance;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Vetoed;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.ObserverMethod;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("4.2 - TCK parity for MemberLevelInheritanceTest")
class MemberLevelInheritanceTckParityTest {

    @Test
    @DisplayName("MemberLevelInheritanceTest - inherited generic injection point types are substituted")
    void inheritedInjectionPointsUseSubstitutedTypeArguments() {
        Syringe syringe = newSyringe(Foo.class, Baz.class, Qux.class, Producer.class);
        syringe.setup();

        Bean<?> fooBean = syringe.getBeanManager().resolve(syringe.getBeanManager().getBeans(Foo.class));
        Set<InjectionPoint> injectionPoints = fooBean.getInjectionPoints();

        assertEquals(4, injectionPoints.size());

        Type expectedBaz = new TypeLiteral<Baz<String>>() {}.getType();
        Type expectedListBaz = new TypeLiteral<Baz<List<Qux>>>() {}.getType();

        boolean sawBaz = false;
        boolean sawT1 = false;
        boolean sawListBaz = false;
        boolean sawArray = false;

        for (InjectionPoint ip : injectionPoints) {
            String name = ip.getMember().getName();
            if ("baz".equals(name)) {
                assertEquals(expectedBaz, ip.getType());
                sawBaz = true;
            } else if ("t1".equals(name)) {
                assertEquals(String.class, ip.getType());
                sawT1 = true;
            } else if ("t2BazList".equals(name)) {
                assertEquals(expectedListBaz, ip.getType());
                sawListBaz = true;
            } else if ("setT1Array".equals(name)) {
                assertEquals(String[].class, ip.getType());
                sawArray = true;
            }
        }

        assertTrue(sawBaz && sawT1 && sawListBaz && sawArray);

        RequestContextController requestContext = syringe.getBeanManager()
                .createInstance()
                .select(RequestContextController.class)
                .get();
        requestContext.activate();
        try {
            Foo foo = syringe.inject(Foo.class);
            assertNotNull(foo.getBaz());
            assertNotNull(foo.getT2BazList());
            assertNotNull(foo.getT1Array());
        } finally {
            requestContext.deactivate();
        }
    }

    @Test
    @DisplayName("MemberLevelInheritanceTest - inherited generic observer resolves substituted observed type")
    void inheritedObserverUsesSubstitutedObservedType() {
        Syringe syringe = newSyringe(Foo.class, Baz.class, Qux.class, Producer.class);
        syringe.setup();

        Set<ObserverMethod<? super Qux>> observers = syringe.getBeanManager().resolveObserverMethods(new Qux(null));
        assertEquals(1, observers.size());

        ObserverMethod<? super Qux> observer = observers.iterator().next();
        assertEquals(Foo.class, observer.getBeanClass());
        assertEquals(new TypeLiteral<Baz<String>>() {}.getType(), observer.getObservedType());

        RequestContextController requestContext = syringe.getBeanManager()
                .createInstance()
                .select(RequestContextController.class)
                .get();
        requestContext.activate();
        try {
            Foo foo = syringe.inject(Foo.class);
            syringe.getBeanManager().getEvent().select(Qux.class).fire(new Qux("event"));
            assertNotNull(foo.getT1BazEvent());
            assertEquals("ok", foo.getT1ObserverInjectionPoint());
        } finally {
            requestContext.deactivate();
        }
    }

    private Syringe newSyringe(Class<?>... classes) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), classes);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        return syringe;
    }

    @Qualifier
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @interface Amazing {
    }

    @Dependent
    public static class Producer {
        @Produces
        @Amazing
        String produceString() {
            return "ok";
        }

        @Produces
        String[] produceStringArray() {
            return new String[] {"v"};
        }
    }

    @Dependent
    public static class Baz<T> {
    }

    public static class Qux extends Baz<String> {
        Qux(String ignored) {
        }
    }

    @Vetoed
    public static class GenericParent<T1, T2> {
        @Inject
        private Baz<T1> baz;

        @Inject
        @Amazing
        private T1 t1;

        @Inject
        private Baz<List<T2>> t2BazList;

        private T1[] t1Array;

        private Baz<T1> t1BazEvent;

        private T1 t1ObserverInjectionPoint;

        @Inject
        void setT1Array(T1[] t1Array) {
            this.t1Array = t1Array;
        }

        void observeBaz(@Observes Baz<T1> baz, @Amazing T1 t1) {
            t1BazEvent = baz;
            t1ObserverInjectionPoint = t1;
        }

        public Baz<T1> getBaz() {
            return baz;
        }

        public Baz<List<T2>> getT2BazList() {
            return t2BazList;
        }

        public T1[] getT1Array() {
            return t1Array;
        }

        public Baz<T1> getT1BazEvent() {
            return t1BazEvent;
        }

        public T1 getT1ObserverInjectionPoint() {
            return t1ObserverInjectionPoint;
        }
    }

    @RequestScoped
    public static class Foo extends GenericParent<String, Qux> {
    }
}
