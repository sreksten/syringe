package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter19.tckparity.aroundconstruct;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundConstruct;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("19 - TCK parity for AroundConstructTest")
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
class AroundConstructTckParityTest {

    @Test
    @DisplayName("AroundConstructTest - interceptor invocation around constructor")
    void shouldInvokeAroundConstructInterceptor() {
        InvocationRecorder.reset();
        Syringe syringe = newSyringe(Alpha.class, AlphaInterceptor.class);
        try {
            syringe.inject(Alpha.class);
            assertSequenceEquals(AlphaInterceptor.class);
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("AroundConstructTest - replacing constructor parameters via InvocationContext.setParameters")
    void shouldReplaceConstructorParameters() {
        InvocationRecorder.reset();
        Syringe syringe = newSyringe(Bravo.class, BravoInterceptor.class, BravoParameterProducer.class);
        try {
            Bravo bravo = syringe.inject(Bravo.class);
            assertNotNull(bravo.getParameter());
            assertEquals(BravoInterceptor.NEW_PARAMETER_VALUE, bravo.getParameter().getValue());
            assertSequenceEquals(BravoInterceptor.class);
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("AroundConstructTest - lifecycle interceptor exception propagation and ordering")
    void shouldPropagateExceptionAndPreserveInterceptorOrdering() {
        InvocationRecorder.reset();
        Syringe syringe = newSyringe(Charlie.class, CharlieInterceptor1.class, CharlieInterceptor2.class);
        try {
            assertThrows(CharlieException.class, () -> syringe.inject(Charlie.class));
            // reverse order because interceptor1 records after proceed() throws
            assertSequenceEquals(CharlieInterceptor2.class, CharlieInterceptor1.class);
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

    private void assertSequenceEquals(Class<?>... expected) {
        List<String> expectedNames = new ArrayList<String>();
        for (Class<?> expectedClass : expected) {
            expectedNames.add(expectedClass.getSimpleName());
        }
        assertEquals(expectedNames, InvocationRecorder.events());
    }

    static final class InvocationRecorder {
        private static final List<String> EVENTS = new ArrayList<String>();

        private InvocationRecorder() {
        }

        static synchronized void reset() {
            EVENTS.clear();
        }

        static synchronized void addAction(String event) {
            EVENTS.add(event);
        }

        static synchronized List<String> events() {
            return new ArrayList<String>(EVENTS);
        }
    }

    abstract static class AbstractInterceptor {
        protected void setInvoked() {
            InvocationRecorder.addAction(getClass().getSimpleName());
        }

        protected void checkConstructor(InvocationContext ctx, Class<?> expectedDeclaringClass) {
            Constructor<?> constructor = ctx.getConstructor();
            assertNotNull(constructor);
            assertEquals(expectedDeclaringClass, constructor.getDeclaringClass());
        }
    }

    @InterceptorBinding
    @Inherited
    @Target({ElementType.TYPE, ElementType.CONSTRUCTOR})
    @Retention(RetentionPolicy.RUNTIME)
    @interface AlphaBinding {
    }

    @Dependent
    static class Alpha {
        @AlphaBinding
        @Inject
        Alpha(BeanManager manager) {
        }
    }

    @Interceptor
    @AlphaBinding
    @Priority(Interceptor.Priority.APPLICATION)
    static class AlphaInterceptor extends AbstractInterceptor {
        @AroundConstruct
        void aroundConstruct(InvocationContext ctx) {
            try {
                setInvoked();
                checkConstructor(ctx, Alpha.class);
                assertNull(ctx.getMethod());
                assertNull(ctx.getTarget());
                assertEquals(1, ctx.getParameters().length);
                assertTrue(ctx.getParameters()[0] instanceof BeanManager);

                assertNull(ctx.proceed());

                checkConstructor(ctx, Alpha.class);
                assertNull(ctx.getMethod());
                assertNotNull(ctx.getTarget());
                assertTrue(ctx.getTarget() instanceof Alpha);
                assertEquals(1, ctx.getParameters().length);
                assertTrue(ctx.getParameters()[0] instanceof BeanManager);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @InterceptorBinding
    @Inherited
    @Target({ElementType.TYPE, ElementType.CONSTRUCTOR})
    @Retention(RetentionPolicy.RUNTIME)
    @interface BravoBinding {
    }

    @ApplicationScoped
    static class Bravo {
        private final BravoParameter parameter;

        Bravo() {
            this.parameter = null;
        }

        @BravoBinding
        @Inject
        Bravo(BravoParameter parameter) {
            this.parameter = parameter;
        }

        BravoParameter getParameter() {
            return parameter;
        }
    }

    static class BravoParameter {
        private final String value;

        BravoParameter(String value) {
            this.value = value;
        }

        String getValue() {
            return value;
        }
    }

    @Dependent
    static class BravoParameterProducer {
        @Produces
        BravoParameter produceParameter1() {
            return new BravoParameter("parameter1");
        }
    }

    @Interceptor
    @BravoBinding
    @Priority(Interceptor.Priority.APPLICATION)
    static class BravoInterceptor extends AbstractInterceptor {
        static final String NEW_PARAMETER_VALUE = "enhanced parameter1";

        @AroundConstruct
        void aroundConstruct(InvocationContext ctx) {
            try {
                setInvoked();
                checkConstructor(ctx, Bravo.class);
                assertNull(ctx.getMethod());
                assertNull(ctx.getTarget());

                assertEquals(1, ctx.getParameters().length);
                assertTrue(ctx.getParameters()[0] instanceof BravoParameter);
                BravoParameter parameter = (BravoParameter) ctx.getParameters()[0];
                assertEquals("parameter1", parameter.getValue());

                ctx.setParameters(new Object[]{new BravoParameter(NEW_PARAMETER_VALUE)});
                assertNull(ctx.proceed());

                checkConstructor(ctx, Bravo.class);
                assertNull(ctx.getMethod());
                assertNotNull(ctx.getTarget());
                assertTrue(ctx.getTarget() instanceof Bravo);
                assertEquals(1, ctx.getParameters().length);
                assertTrue(ctx.getParameters()[0] instanceof BravoParameter);
                parameter = (BravoParameter) ctx.getParameters()[0];
                assertEquals(NEW_PARAMETER_VALUE, parameter.getValue());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @InterceptorBinding
    @Inherited
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface CharlieBinding {
    }

    @CharlieBinding
    @Dependent
    static class Charlie {
    }

    static class CharlieException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    @Interceptor
    @CharlieBinding
    @Priority(100)
    static class CharlieInterceptor1 extends AbstractInterceptor {
        @AroundConstruct
        void aroundConstruct(InvocationContext ctx) {
            try {
                ctx.proceed();
            } catch (CharlieException expected) {
                setInvoked();
                throw expected;
            } catch (Exception e) {
                throw new AssertionError("Unexpected checked exception", e);
            }
            throw new AssertionError("Expected CharlieException");
        }
    }

    @Interceptor
    @CharlieBinding
    @Priority(200)
    static class CharlieInterceptor2 extends AbstractInterceptor {
        @AroundConstruct
        void aroundConstruct(InvocationContext ctx) {
            try {
                ctx.proceed();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            setInvoked();
            throw new CharlieException();
        }
    }
}
