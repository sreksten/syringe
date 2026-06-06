package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.tckparity.resolutionbytype;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.inject.Qualifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Type;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("5.2 - TCK parity for ResolutionByTypeTest")
class ResolutionByTypeTckParityTest {

    @Test
    @DisplayName("ResolutionByTypeTest - default qualifier is assumed in typesafe resolution")
    void defaultQualifierIsAssumed() {
        Syringe syringe = newSyringe(Tuna.class);
        syringe.setup();

        Set<Bean<?>> beans = syringe.getBeanManager().getBeans(Tuna.class, Default.Literal.INSTANCE);
        assertEquals(1, beans.size());
    }

    @Test
    @DisplayName("ResolutionByTypeTest - all qualifiers at injection point must be present on bean")
    void allQualifiersMustMatch() {
        Syringe syringe = newSyringe(Animal.class, Cod.class, Sole.class);
        syringe.setup();

        Set<Bean<?>> exact = syringe.getBeanManager().getBeans(Animal.class, new Chunky.Literal(), new Whitefish.Literal());
        assertEquals(1, exact.size());

        Set<Bean<?>> partial = syringe.getBeanManager().getBeans(Animal.class, new Whitefish.Literal());
        assertEquals(2, partial.size());
    }

    @Test
    @DisplayName("ResolutionByTypeTest - primitive and wrapper types are both resolved")
    void primitiveAndWrapperTypesAreResolved() {
        Syringe syringe = newSyringe(NumberProducer.class);
        syringe.setup();

        Set<Bean<?>> wrapperBeans = syringe.getBeanManager().getBeans(Double.class, NumberQualifier.Literal.INSTANCE);
        Set<Bean<?>> primitiveBeans = syringe.getBeanManager().getBeans(double.class, NumberQualifier.Literal.INSTANCE);
        assertEquals(2, wrapperBeans.size());
        assertEquals(2, primitiveBeans.size());

        Double min = syringe.inject(Double.class, new Min.Literal());
        Double max = syringe.inject(Double.class, new Max.Literal());
        assertEquals(NumberProducer.MIN, min);
        assertEquals(NumberProducer.MAX, max);
    }

    @Test
    @DisplayName("ResolutionByTypeTest - restricted bean types on producer field")
    void restrictedBeanTypesOnProducerField() {
        Syringe syringe = newSyringe(CatProducer.class);
        syringe.setup();

        Set<Bean<?>> catBeans = syringe.getBeanManager().getBeans(Cat.class, Wild.Literal.INSTANCE);
        assertEquals(1, catBeans.size());
        Set<Type> types = catBeans.iterator().next().getTypes();
        assertTrue(types.contains(Cat.class));
        assertTrue(types.contains(Object.class));
        assertTrue(!types.contains(Lion.class));
    }

    private Syringe newSyringe(Class<?>... classes) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), classes);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        return syringe;
    }

    interface Animal {
    }

    static class Tuna implements Animal {
    }

    @Chunky
    @Whitefish
    static class Cod implements Animal {
    }

    @Whitefish
    static class Sole implements Animal {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE, ElementType.METHOD})
    @interface Chunky {
        class Literal extends jakarta.enterprise.util.AnnotationLiteral<Chunky> implements Chunky {
            private static final long serialVersionUID = 1L;
        }
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE, ElementType.METHOD})
    @interface Whitefish {
        class Literal extends jakarta.enterprise.util.AnnotationLiteral<Whitefish> implements Whitefish {
            private static final long serialVersionUID = 1L;
        }
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE, ElementType.METHOD})
    @interface NumberQualifier {
        class Literal extends jakarta.enterprise.util.AnnotationLiteral<NumberQualifier> implements NumberQualifier {
            private static final long serialVersionUID = 1L;
            static final Literal INSTANCE = new Literal();
        }
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE, ElementType.METHOD})
    @interface Min {
        class Literal extends jakarta.enterprise.util.AnnotationLiteral<Min> implements Min {
            private static final long serialVersionUID = 1L;
            static final Literal INSTANCE = new Literal();
        }
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE, ElementType.METHOD})
    @interface Max {
        class Literal extends jakarta.enterprise.util.AnnotationLiteral<Max> implements Max {
            private static final long serialVersionUID = 1L;
            static final Literal INSTANCE = new Literal();
        }
    }

    static class NumberProducer {
        static final double MIN = 1.5d;
        static final double MAX = 99.5d;

        @Produces
        @NumberQualifier
        @Min
        Double min = MIN;

        @Produces
        @NumberQualifier
        @Max
        double max() {
            return MAX;
        }
    }

    static class Lion {
    }

    static class Cat {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE, ElementType.METHOD})
    @interface Wild {
        class Literal extends jakarta.enterprise.util.AnnotationLiteral<Wild> implements Wild {
            private static final long serialVersionUID = 1L;
            static final Literal INSTANCE = new Literal();
        }
    }

    static class CatProducer {
        @Produces
        @Wild
        Cat produce() {
            return new Cat();
        }
    }
}
