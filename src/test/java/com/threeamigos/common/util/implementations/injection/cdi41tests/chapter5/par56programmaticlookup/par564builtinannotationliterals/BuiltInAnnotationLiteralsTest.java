package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par564builtinannotationliterals;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.ConversationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.Destroyed;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Specializes;
import jakarta.enterprise.inject.Typed;
import jakarta.enterprise.inject.Vetoed;
import jakarta.enterprise.inject.literal.InjectLiteral;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.enterprise.inject.literal.QualifierLiteral;
import jakarta.enterprise.inject.literal.SingletonLiteral;
import jakarta.enterprise.util.Nonbinding;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("5.6.4 - Built-in annotation literals")
public class BuiltInAnnotationLiteralsTest {

    @Test
    @DisplayName("5.6.4 - CDI built-in annotation literals are available via nested Literal classes")
    void shouldProvideCdiBuiltInAnnotationLiterals() throws Exception {
        List<Class<? extends Annotation>> builtIns = Arrays.asList(
                Any.class,
                Default.class,
                Specializes.class,
                Vetoed.class,
                Nonbinding.class,
                Initialized.class,
                Destroyed.class,
                RequestScoped.class,
                SessionScoped.class,
                ApplicationScoped.class,
                Dependent.class,
                ConversationScoped.class,
                Alternative.class,
                Typed.class
        );
        for (Class<? extends Annotation> annotationType : builtIns) {
            assertHasNestedLiteralClass(annotationType);
        }

        assertInstanceConstant(Any.Literal.class, Any.class);
        assertInstanceConstant(Default.Literal.class, Default.class);
        assertInstanceConstant(Specializes.Literal.class, Specializes.class);
        assertInstanceConstant(Vetoed.Literal.class, Vetoed.class);
        assertInstanceConstant(Nonbinding.Literal.class, Nonbinding.class);
        assertInstanceConstant(RequestScoped.Literal.class, RequestScoped.class);
        assertInstanceConstant(SessionScoped.Literal.class, SessionScoped.class);
        assertInstanceConstant(ApplicationScoped.Literal.class, ApplicationScoped.class);
        assertInstanceConstant(Dependent.Literal.class, Dependent.class);
        assertInstanceConstant(ConversationScoped.Literal.class, ConversationScoped.class);
        assertInstanceConstant(Alternative.Literal.class, Alternative.class);

        Default defaultLiteral = new Default.Literal();
        assertEquals(Default.class, defaultLiteral.annotationType());
        RequestScoped requestScopedLiteral = RequestScoped.Literal.INSTANCE;
        assertEquals(RequestScoped.class, requestScopedLiteral.annotationType());

        Initialized initializedForRequest = Initialized.Literal.of(RequestScoped.class);
        Destroyed destroyedForSession = Destroyed.Literal.of(SessionScoped.class);
        assertEquals(RequestScoped.class, initializedForRequest.value());
        assertEquals(SessionScoped.class, destroyedForSession.value());

        Typed typedLiteral = Typed.Literal.of(new Class<?>[]{String.class, Integer.class});
        assertArrayEquals(new Class<?>[]{String.class, Integer.class}, typedLiteral.value());
    }

    @Test
    @DisplayName("5.6.4 - JSR-330 annotation literals are available")
    void shouldProvideJsr330AnnotationLiterals() {
        Inject injectLiteral = InjectLiteral.INSTANCE;
        Qualifier qualifierLiteral = QualifierLiteral.INSTANCE;
        Singleton singletonLiteral = SingletonLiteral.INSTANCE;
        Named namedLiteral = NamedLiteral.of("orders");

        assertEquals(Inject.class, injectLiteral.annotationType());
        assertEquals(Qualifier.class, qualifierLiteral.annotationType());
        assertEquals(Singleton.class, singletonLiteral.annotationType());
        assertEquals("orders", namedLiteral.value());
    }

    private void assertHasNestedLiteralClass(Class<? extends Annotation> annotationType) {
        Class<?>[] nestedClasses = annotationType.getDeclaredClasses();
        boolean found = false;
        for (Class<?> nested : nestedClasses) {
            if ("Literal".equals(nested.getSimpleName())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Expected nested Literal class on " + annotationType.getName());
    }

    private void assertInstanceConstant(Class<?> literalClass, Class<? extends Annotation> annotationType) throws Exception {
        Field instanceField = literalClass.getField("INSTANCE");
        Object instance = instanceField.get(null);
        assertNotNull(instance);
        assertTrue(annotationType.isInstance(instance),
                "INSTANCE should implement " + annotationType.getName());
    }
}
