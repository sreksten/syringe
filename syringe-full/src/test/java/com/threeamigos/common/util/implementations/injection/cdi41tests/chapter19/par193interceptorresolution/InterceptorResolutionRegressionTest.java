package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter19.par193interceptorresolution;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.interceptor.AroundConstruct;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("19.3 - Interceptor resolution regression tests")
@Execution(ExecutionMode.SAME_THREAD)
public class InterceptorResolutionRegressionTest {

    @Test
    @DisplayName("19.3 - Constructor interceptor resolution merges class and constructor bindings, and constructor-level bindings override class-level values")
    void shouldMergeConstructorAndClassBindingsWithConstructorOverride() {
        ConstructorRecorder.reset();
        Syringe syringe = newSyringe(
                ConstructorTarget.class,
                AlphaOneInterceptor.class,
                AlphaTwoInterceptor.class,
                BravoInterceptor.class
        );
        syringe.exclude(
                ConflictingBoundBean.class,
                FooBinding.class,
                BarBinding.class,
                Baz.class
        );
        syringe.setup();

        syringe.inject(ConstructorTarget.class);

        assertEquals(Arrays.asList("alpha-2", "bravo", "bean"), ConstructorRecorder.events());
    }

    @Test
    @DisplayName("19.1 - Conflicting transitive interceptor binding member values are definition errors")
    void shouldRejectConflictingTransitiveInterceptorBindingValues() {
        Syringe syringe = newSyringe(ConflictingBoundBean.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        return syringe;
    }

    static class ConstructorRecorder {
        private static final List<String> EVENTS = new ArrayList<String>();

        static synchronized void reset() {
            EVENTS.clear();
        }

        static synchronized void add(String value) {
            EVENTS.add(value);
        }

        static synchronized List<String> events() {
            return new ArrayList<String>(EVENTS);
        }
    }

    @InterceptorBinding
    @Target({ElementType.TYPE, ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Alpha {
        int value();
    }

    @InterceptorBinding
    @Alpha(2)
    @Target({ElementType.TYPE, ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Beta {
    }

    @InterceptorBinding
    @Target({ElementType.TYPE, ElementType.CONSTRUCTOR, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Bravo {
    }

    @Alpha(1)
    @Dependent
    public static class ConstructorTarget {
        @jakarta.inject.Inject
        @Beta
        @Bravo
        public ConstructorTarget() {
            ConstructorRecorder.add("bean");
        }
    }

    @Interceptor
    @Alpha(1)
    @Priority(Interceptor.Priority.APPLICATION + 300)
    public static class AlphaOneInterceptor {
        @AroundConstruct
        Object around(InvocationContext ctx) throws Exception {
            ConstructorRecorder.add("alpha-1");
            return ctx.proceed();
        }
    }

    @Interceptor
    @Alpha(2)
    @Priority(Interceptor.Priority.APPLICATION + 100)
    public static class AlphaTwoInterceptor {
        @AroundConstruct
        Object around(InvocationContext ctx) throws Exception {
            ConstructorRecorder.add("alpha-2");
            return ctx.proceed();
        }
    }

    @Interceptor
    @Bravo
    @Priority(Interceptor.Priority.APPLICATION + 200)
    public static class BravoInterceptor {
        @AroundConstruct
        Object around(InvocationContext ctx) throws Exception {
            ConstructorRecorder.add("bravo");
            return ctx.proceed();
        }
    }

    @InterceptorBinding
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Baz {
        boolean value();
    }

    @InterceptorBinding
    @Baz(true)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface FooBinding {
    }

    @InterceptorBinding
    @Baz(false)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface BarBinding {
    }

    @FooBinding
    @BarBinding
    @Dependent
    public static class ConflictingBoundBean {
    }
}
