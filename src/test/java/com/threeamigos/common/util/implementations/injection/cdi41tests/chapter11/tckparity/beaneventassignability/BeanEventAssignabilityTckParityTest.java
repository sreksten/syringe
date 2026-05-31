package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter11.tckparity.beaneventassignability;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.enterprise.inject.spi.BeanContainer;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Qualifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("11.1.14 - TCK Parity: BeanEventAssignabilityTest")
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
class BeanEventAssignabilityTckParityTest {

    private Syringe syringe;
    private BeanContainer beanContainer;

    @BeforeEach
    void setUp() {
        syringe = new Syringe(
                new InMemoryMessageHandler(),
                MyBean.class,
                MyQualifiedBean.class,
                MyEvent.class
        );
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();
        beanContainer = syringe.getBeanManager();
    }

    @AfterEach
    void tearDown() {
        if (syringe != null) {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("TCK parity - BeanEventAssignabilityTest.testBeanMatching")
    void shouldMatchBeansLikeTckTestBeanMatching() {
        Set<Type> beanTypes = setOf(MyBean.class, MyBeanInterface.class, Object.class);

        assertTrue(
                beanContainer.isMatchingBean(beanTypes, setOf(), MyBean.class, setOf()),
                "Bean did not match its own type"
        );
        assertFalse(
                beanContainer.isMatchingBean(beanTypes, setOf(), MyBean.class, setOf(Qualifier1.Literal.INSTANCE)),
                "Bean matched despite not having required qualifier"
        );
        assertTrue(
                beanContainer.isMatchingBean(
                        beanTypes,
                        setOf(Qualifier1.Literal.INSTANCE),
                        MyBean.class,
                        setOf(Qualifier1.Literal.INSTANCE)
                ),
                "Bean did not match despite having required qualifier"
        );
        assertTrue(
                beanContainer.isMatchingBean(
                        beanTypes,
                        setOf(Qualifier1.Literal.INSTANCE, Qualifier2.Literal.INSTANCE),
                        MyBean.class,
                        setOf(Qualifier1.Literal.INSTANCE)
                ),
                "Bean did not match despite having a superset of required qualifiers"
        );

        Set<Type> reducedBeanTypes = setOf(MyBean.class);
        assertTrue(
                beanContainer.isMatchingBean(reducedBeanTypes, setOf(), MyBean.class, setOf()),
                "Bean did not match its own type"
        );
        assertFalse(
                beanContainer.isMatchingBean(reducedBeanTypes, setOf(), MyBeanInterface.class, setOf()),
                "Bean matched MyBeanInterface despite not being in bean types"
        );
        assertTrue(
                beanContainer.isMatchingBean(reducedBeanTypes, setOf(), Object.class, setOf()),
                "Bean did not match when Object requested"
        );

        assertTrue(
                beanContainer.isMatchingBean(setOf(MyQualifiedBean.class), setOf(), MyQualifiedBean.class, setOf()),
                "Qualifier annotations on bean type classes should not be considered"
        );
        assertFalse(
                beanContainer.isMatchingBean(
                        setOf(MyQualifiedBean.class),
                        setOf(),
                        MyQualifiedBean.class,
                        setOf(Qualifier1.Literal.INSTANCE)
                ),
                "Qualifier annotations on bean type classes should not be considered"
        );
    }

    @Test
    @DisplayName("TCK parity - BeanEventAssignabilityTest.testBeanMatchingDefaultQualifiers")
    void shouldApplyDefaultAndAnySemanticsForBeanMatching() {
        Set<Type> beanTypes = setOf(MyBean.class, MyBeanInterface.class, Object.class);

        assertTrue(beanContainer.isMatchingBean(beanTypes, setOf(Default.Literal.INSTANCE), MyBean.class, setOf()));
        assertTrue(beanContainer.isMatchingBean(beanTypes, setOf(), MyBean.class, setOf(Default.Literal.INSTANCE)));
        assertTrue(beanContainer.isMatchingBean(beanTypes, setOf(Any.Literal.INSTANCE), MyBean.class, setOf()));
        assertTrue(beanContainer.isMatchingBean(beanTypes, setOf(NamedLiteral.of("foo")), MyBean.class, setOf()));
        assertFalse(beanContainer.isMatchingBean(beanTypes, setOf(Qualifier1.Literal.INSTANCE), MyBean.class, setOf()));
        assertTrue(beanContainer.isMatchingBean(beanTypes, setOf(), MyBean.class, setOf(Any.Literal.INSTANCE)));
        assertTrue(
                beanContainer.isMatchingBean(
                        beanTypes,
                        setOf(Qualifier1.Literal.INSTANCE),
                        MyBean.class,
                        setOf(Any.Literal.INSTANCE)
                )
        );
    }

    @Test
    @DisplayName("TCK parity - BeanEventAssignabilityTest.testBeanMatchingNullException")
    void shouldRejectNullArgumentsForBeanMatching() {
        Set<Type> beanTypes = setOf(MyBean.class, MyBeanInterface.class, Object.class);

        assertThrows(IllegalArgumentException.class,
                () -> beanContainer.isMatchingBean(null, setOf(), MyBean.class, setOf()));
        assertThrows(IllegalArgumentException.class,
                () -> beanContainer.isMatchingBean(beanTypes, null, MyBean.class, setOf()));
        assertThrows(IllegalArgumentException.class,
                () -> beanContainer.isMatchingBean(beanTypes, setOf(), null, setOf()));
        assertThrows(IllegalArgumentException.class,
                () -> beanContainer.isMatchingBean(beanTypes, setOf(), MyBean.class, null));
    }

    @Test
    @DisplayName("TCK parity - BeanEventAssignabilityTest.testBeanMatchingNonQualifiersException")
    void shouldRejectNonQualifierAnnotationsForBeanMatching() {
        Set<Type> beanTypes = setOf(MyBean.class, MyBeanInterface.class, Object.class);

        assertThrows(IllegalArgumentException.class,
                () -> beanContainer.isMatchingBean(
                        beanTypes,
                        setOf(Qualifier1.Literal.INSTANCE, NonQualifier.Literal.INSTANCE),
                        MyBean.class,
                        setOf()
                ));

        assertThrows(IllegalArgumentException.class,
                () -> beanContainer.isMatchingBean(
                        beanTypes,
                        setOf(),
                        MyBean.class,
                        setOf(Qualifier1.Literal.INSTANCE, NonQualifier.Literal.INSTANCE)
                ));
    }

    @Test
    @DisplayName("TCK parity - BeanEventAssignabilityTest.testNonLegalBeanTypesIgnored")
    void shouldIgnoreIllegalBeanTypesContainingWildcardTypeVariables() {
        TypeLiteral<List<?>> listOfWildcard = new TypeLiteral<List<?>>() {
        };
        Set<Type> beanTypes = setOf(MyBean.class, MyBeanInterface.class, listOfWildcard.getType(), Object.class);

        assertTrue(beanContainer.isMatchingBean(beanTypes, setOf(), MyBean.class, setOf()));
        assertFalse(beanContainer.isMatchingBean(beanTypes, setOf(), listOfWildcard.getType(), setOf()));
    }

    @Test
    @DisplayName("TCK parity - BeanEventAssignabilityTest.testEventMatching")
    void shouldMatchEventsLikeTck() {
        assertTrue(beanContainer.isMatchingEvent(MyEvent.class, setOf(), MyEvent.class, setOf()));
        assertFalse(beanContainer.isMatchingEvent(MyEvent.class, setOf(), MyEvent.class, setOf(Qualifier1.Literal.INSTANCE)));
        assertTrue(
                beanContainer.isMatchingEvent(
                        MyEvent.class,
                        setOf(Qualifier1.Literal.INSTANCE),
                        MyEvent.class,
                        setOf(Qualifier1.Literal.INSTANCE)
                )
        );
        assertTrue(
                beanContainer.isMatchingEvent(
                        MyEvent.class,
                        setOf(Qualifier1.Literal.INSTANCE, Qualifier2.Literal.INSTANCE),
                        MyEvent.class,
                        setOf(Qualifier1.Literal.INSTANCE)
                )
        );
        assertTrue(beanContainer.isMatchingEvent(MyEvent.class, setOf(), MyEventInterface.class, setOf()));
        assertTrue(beanContainer.isMatchingEvent(MyEvent.class, setOf(), Object.class, setOf()));
        assertTrue(beanContainer.isMatchingEvent(MyEvent.class, setOf(Default.Literal.INSTANCE), MyEvent.class, setOf()));
        assertTrue(beanContainer.isMatchingEvent(MyEvent.class, setOf(Any.Literal.INSTANCE), MyEvent.class, setOf()));
        assertTrue(beanContainer.isMatchingEvent(MyEvent.class, setOf(Qualifier1.Literal.INSTANCE), MyEvent.class, setOf()));
        assertTrue(beanContainer.isMatchingEvent(MyEvent.class, setOf(NamedLiteral.of("foo")), MyEvent.class, setOf()));
    }

    @Test
    @DisplayName("TCK parity - BeanEventAssignabilityTest.testEventMatchingDefaultQualifier")
    void shouldApplyDefaultSemanticsForEventMatching() {
        assertTrue(
                beanContainer.isMatchingEvent(
                        MyEvent.class,
                        setOf(),
                        MyEvent.class,
                        setOf(Default.Literal.INSTANCE)
                )
        );
        assertTrue(
                beanContainer.isMatchingEvent(
                        MyEvent.class,
                        setOf(Default.Literal.INSTANCE),
                        MyEvent.class,
                        setOf(Default.Literal.INSTANCE)
                )
        );
        assertFalse(
                beanContainer.isMatchingEvent(
                        MyEvent.class,
                        setOf(Qualifier1.Literal.INSTANCE),
                        MyEvent.class,
                        setOf(Default.Literal.INSTANCE)
                )
        );
    }

    @Test
    @DisplayName("TCK parity - BeanEventAssignabilityTest.testEventMatchingParameterized")
    void shouldMatchParameterizedEventsLikeTck() {
        TypeLiteral<List<String>> listOfString = new TypeLiteral<List<String>>() {
        };
        TypeLiteral<List<?>> listOfWildcard = new TypeLiteral<List<?>>() {
        };
        TypeLiteral<List<Integer>> listOfInteger = new TypeLiteral<List<Integer>>() {
        };

        assertTrue(beanContainer.isMatchingEvent(listOfString.getType(), setOf(), listOfString.getType(), setOf()));
        assertTrue(beanContainer.isMatchingEvent(listOfString.getType(), setOf(), listOfWildcard.getType(), setOf()));
        assertFalse(beanContainer.isMatchingEvent(listOfString.getType(), setOf(), listOfInteger.getType(), setOf()));
    }

    @Test
    @DisplayName("TCK parity - BeanEventAssignabilityTest.testEventMatchingAnyQualifier")
    void shouldApplyAnySemanticsForEventMatching() {
        assertTrue(beanContainer.isMatchingEvent(MyEvent.class, setOf(), MyEvent.class, setOf(Any.Literal.INSTANCE)));
        assertTrue(
                beanContainer.isMatchingEvent(
                        MyEvent.class,
                        setOf(Qualifier1.Literal.INSTANCE),
                        MyEvent.class,
                        setOf(Any.Literal.INSTANCE)
                )
        );
    }

    @Test
    @DisplayName("TCK parity - BeanEventAssignabilityTest.testEventMatchingNullException")
    void shouldRejectNullArgumentsForEventMatching() {
        assertThrows(IllegalArgumentException.class,
                () -> beanContainer.isMatchingEvent(null, setOf(), MyEvent.class, setOf()));
        assertThrows(IllegalArgumentException.class,
                () -> beanContainer.isMatchingEvent(MyEvent.class, null, MyEvent.class, setOf()));
        assertThrows(IllegalArgumentException.class,
                () -> beanContainer.isMatchingEvent(MyEvent.class, setOf(), null, setOf()));
        assertThrows(IllegalArgumentException.class,
                () -> beanContainer.isMatchingEvent(MyEvent.class, setOf(), MyEvent.class, null));
    }

    @Test
    @DisplayName("TCK parity - BeanEventAssignabilityTest.testEventMatchingTypeVarException")
    <X> void shouldRejectEventTypeContainingTypeVariable() {
        TypeLiteral<List<X>> eventTypeWithVariable = new TypeLiteral<List<X>>() {
        };

        assertThrows(
                IllegalArgumentException.class,
                () -> beanContainer.isMatchingEvent(eventTypeWithVariable.getType(), setOf(), MyBean.class, setOf())
        );
    }

    @Test
    @DisplayName("TCK parity - BeanEventAssignabilityTest.testEventMatchingNonQualifiersException")
    void shouldRejectNonQualifierAnnotationsForEventMatching() {
        assertThrows(
                IllegalArgumentException.class,
                () -> beanContainer.isMatchingEvent(
                        MyEvent.class,
                        setOf(Qualifier1.Literal.INSTANCE, NonQualifier.Literal.INSTANCE),
                        MyEvent.class,
                        setOf()
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> beanContainer.isMatchingEvent(
                        MyEvent.class,
                        setOf(),
                        MyEvent.class,
                        setOf(Qualifier1.Literal.INSTANCE, NonQualifier.Literal.INSTANCE)
                )
        );
    }

    @SafeVarargs
    private static <T> Set<T> setOf(T... values) {
        return new LinkedHashSet<T>(Arrays.asList(values));
    }

    interface MyBeanInterface {
    }

    static class MyBean implements MyBeanInterface {
    }

    interface MyEventInterface {
    }

    static class MyEvent implements MyEventInterface {
    }

    @Qualifier1
    static class MyQualifiedBean {
    }

    @Retention(RUNTIME)
    @Target({TYPE, METHOD, FIELD, PARAMETER})
    @interface NonQualifier {
        class Literal extends AnnotationLiteral<NonQualifier> implements NonQualifier {
            private static final long serialVersionUID = 1L;
            static final NonQualifier INSTANCE = new Literal();
        }
    }

    @Qualifier
    @Retention(RUNTIME)
    @Target({TYPE, METHOD, FIELD, PARAMETER})
    @interface Qualifier1 {
        class Literal extends AnnotationLiteral<Qualifier1> implements Qualifier1 {
            private static final long serialVersionUID = 1L;
            static final Qualifier1 INSTANCE = new Literal();
        }
    }

    @Qualifier
    @Retention(RUNTIME)
    @Target({TYPE, METHOD, FIELD, PARAMETER})
    @interface Qualifier2 {
        class Literal extends AnnotationLiteral<Qualifier2> implements Qualifier2 {
            private static final long serialVersionUID = 1L;
            static final Qualifier2 INSTANCE = new Literal();
        }
    }
}
