package com.threeamigos.common.util.implementations.injection.interceptors;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Execution(ExecutionMode.SAME_THREAD)
@Isolated
class InvocationContextInterceptorBindingsSanitizationTest {

    @Test
    void getInterceptorBindingsShouldNotContainInterceptorBindingMetaAnnotation() {
        BindingProbeInterceptor.reset();
        Syringe syringe = new Syringe(
                new InMemoryMessageHandler(),
                ProbeBinding.class,
                ProbeBean.class,
                BindingProbeInterceptor.class
        );
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        try {
            syringe.setup();

            ProbeBean bean = syringe.getBeanManager().createInstance().select(ProbeBean.class).get();
            assertEquals("pong", bean.ping());

            Set<Annotation> bindings = BindingProbeInterceptor.getAllBindings();
            assertNotNull(bindings);
            assertEquals(1, bindings.size());
            assertTrue(bindings.iterator().next().annotationType().equals(ProbeBinding.class));
        } finally {
            syringe.shutdown();
        }
    }

    @InterceptorBinding
    @Inherited
    @Target({TYPE, METHOD})
    @Retention(RUNTIME)
    public @interface ProbeBinding {
    }

    @ProbeBinding
    @Dependent
    public static class ProbeBean {
        public String ping() {
            return "pong";
        }
    }

    @Interceptor
    @ProbeBinding
    @Priority(100)
    public static class BindingProbeInterceptor {
        private static Set<Annotation> allBindings = Collections.emptySet();

        @AroundInvoke
        public Object intercept(InvocationContext invocationContext) throws Exception {
            allBindings = new HashSet<>(invocationContext.getInterceptorBindings());
            return invocationContext.proceed();
        }

        static void reset() {
            allBindings = Collections.emptySet();
        }

        static Set<Annotation> getAllBindings() {
            return allBindings;
        }
    }
}
