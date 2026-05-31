package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter20.par204decoratorinvocation;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("20.4 - Decorator invocation tests")
@Execution(ExecutionMode.SAME_THREAD)
public class DecoratorInvocationTest {

    @Test
    @DisplayName("20.4 - Container invokes decorators after method interceptors and before bean business method")
    void shouldInvokeDecoratorsAfterInterceptors() {
        InvocationRecorder.reset();
        Syringe syringe = newSyringe(
                DecoratedInvocationServiceBean.class,
                FirstInvocationDecorator.class,
                SecondInvocationDecorator.class,
                InvocationInterceptor.class
        );
        syringe.setup();

        InvocationService service = syringe.inject(InvocationService.class);
        assertEquals("alpha", service.alpha());
        assertEquals(
                Arrays.asList(
                        "interceptor-before",
                        "decorator-1-alpha-before",
                        "decorator-2-alpha-before",
                        "business-alpha",
                        "decorator-2-alpha-after",
                        "decorator-1-alpha-after",
                        "interceptor-after"
                ),
                InvocationRecorder.events()
        );
    }

    @Test
    @DisplayName("20.4 - Delegate invocation is intercepted and routed to the next decorator implementing the method")
    void shouldRouteDelegateInvocationToNextDecorator() {
        InvocationRecorder.reset();
        Syringe syringe = newSyringe(
                DecoratedInvocationServiceBean.class,
                FirstInvocationDecorator.class,
                SecondInvocationDecorator.class
        );
        syringe.setup();

        InvocationService service = syringe.inject(InvocationService.class);
        assertEquals("alpha", service.alpha());
        assertEquals(
                Arrays.asList(
                        "decorator-1-alpha-before",
                        "decorator-2-alpha-before",
                        "business-alpha",
                        "decorator-2-alpha-after",
                        "decorator-1-alpha-after"
                ),
                InvocationRecorder.events()
        );
    }

    @Test
    @DisplayName("20.4 - If no later decorator handles the delegate invocation, container invokes business method on intercepted instance")
    void shouldInvokeInterceptedInstanceWhenNoLaterDecoratorExists() {
        InvocationRecorder.reset();
        Syringe syringe = newSyringe(
                DecoratedInvocationServiceBean.class,
                FirstInvocationDecorator.class
        );
        syringe.setup();

        InvocationService service = syringe.inject(InvocationService.class);
        assertEquals("beta", service.beta());
        assertEquals(
                Arrays.asList(
                        "decorator-1-beta-before",
                        "business-beta",
                        "decorator-1-beta-after"
                ),
                InvocationRecorder.events()
        );
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        Set<Class<?>> included = new HashSet<Class<?>>(Arrays.asList(beanClasses));
        for (Class<?> fixture : allFixtureTypes()) {
            if (!included.contains(fixture)) {
                syringe.exclude(fixture);
            }
        }
        return syringe;
    }

    private Collection<Class<?>> allFixtureTypes() {
        return Arrays.<Class<?>>asList(
                InvocationBinding.class,
                InvocationBindingLiteral.class,
                InvocationService.class,
                DecoratedInvocationServiceBean.class,
                FirstInvocationDecorator.class,
                SecondInvocationDecorator.class,
                InvocationInterceptor.class
        );
    }

    static class InvocationRecorder {
        private static final List<String> EVENTS = new ArrayList<String>();

        static synchronized void reset() {
            EVENTS.clear();
        }

        static synchronized void record(String event) {
            EVENTS.add(event);
        }

        static synchronized List<String> events() {
            return new ArrayList<String>(EVENTS);
        }
    }

    @InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface InvocationBinding {
    }

    public static final class InvocationBindingLiteral extends AnnotationLiteral<InvocationBinding>
            implements InvocationBinding {
        static final InvocationBindingLiteral INSTANCE = new InvocationBindingLiteral();
    }

    public interface InvocationService {
        String alpha();
        String beta();
    }

    @Dependent
    @InvocationBinding
    public static class DecoratedInvocationServiceBean implements InvocationService {
        @Override
        public String alpha() {
            InvocationRecorder.record("business-alpha");
            return "alpha";
        }

        @Override
        public String beta() {
            InvocationRecorder.record("business-beta");
            return "beta";
        }
    }

    @Decorator
    @Dependent
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 10)
    public static class FirstInvocationDecorator implements InvocationService {
        @Inject
        @Delegate
        InvocationService delegate;

        @Override
        public String alpha() {
            InvocationRecorder.record("decorator-1-alpha-before");
            try {
                return delegate.alpha();
            } finally {
                InvocationRecorder.record("decorator-1-alpha-after");
            }
        }

        @Override
        public String beta() {
            InvocationRecorder.record("decorator-1-beta-before");
            try {
                return delegate.beta();
            } finally {
                InvocationRecorder.record("decorator-1-beta-after");
            }
        }
    }

    @Decorator
    @Dependent
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 20)
    public static class SecondInvocationDecorator implements InvocationService {
        @Inject
        @Delegate
        InvocationService delegate;

        @Override
        public String alpha() {
            InvocationRecorder.record("decorator-2-alpha-before");
            try {
                return delegate.alpha();
            } finally {
                InvocationRecorder.record("decorator-2-alpha-after");
            }
        }

        @Override
        public String beta() {
            return delegate.beta();
        }
    }

    @InvocationBinding
    @Interceptor
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 5)
    public static class InvocationInterceptor {
        @AroundInvoke
        Object around(InvocationContext context) throws Exception {
            InvocationRecorder.record("interceptor-before");
            try {
                return context.proceed();
            } finally {
                InvocationRecorder.record("interceptor-after");
            }
        }
    }
}
