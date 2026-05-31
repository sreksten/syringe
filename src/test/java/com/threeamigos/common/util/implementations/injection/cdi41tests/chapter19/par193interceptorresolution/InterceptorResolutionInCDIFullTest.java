package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter19.par193interceptorresolution;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.InterceptionType;
import jakarta.enterprise.inject.spi.Prioritized;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Interceptor Resolution in CDI Full Test")
@Execution(ExecutionMode.SAME_THREAD)
public class InterceptorResolutionInCDIFullTest {

    @Test
    @DisplayName("19.3 - For custom Interceptor, container uses getInterceptorBindings() to resolve interceptor bindings")
    void shouldResolveCustomInterceptorBindingsViaGetInterceptorBindings() {
        SyntheticInterceptorRecorder.reset();
        Syringe syringe = newSyringe(
                SyntheticInterceptorExtension.class,
                CustomBoundTarget.class
        );
        syringe.addExtension(SyntheticInterceptorExtension.class.getName());
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        List<jakarta.enterprise.inject.spi.Interceptor<?>> resolved = beanManager.resolveInterceptors(
                InterceptionType.AROUND_INVOKE,
                CustomBindingLiteral.INSTANCE
        );

        assertTrue(resolved.stream().anyMatch(i -> i.getBeanClass().equals(BindingAwareInterceptor.class)));
        assertTrue(SyntheticInterceptorRecorder.bindingCalls("BindingAwareInterceptor") > 0);
    }

    @Test
    @DisplayName("19.3 - For custom Interceptor, container uses intercepts() to determine supported interception types")
    void shouldUseInterceptsForCustomInterceptorResolution() {
        SyntheticInterceptorRecorder.reset();
        Syringe syringe = newSyringe(
                SyntheticInterceptorExtension.class,
                CustomBoundTarget.class
        );
        syringe.addExtension(SyntheticInterceptorExtension.class.getName());
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        List<jakarta.enterprise.inject.spi.Interceptor<?>> resolved = beanManager.resolveInterceptors(
                InterceptionType.AROUND_INVOKE,
                CustomBindingLiteral.INSTANCE
        );

        assertTrue(resolved.stream().noneMatch(i -> i.getBeanClass().equals(LifecycleOnlyInterceptor.class)));
        assertTrue(SyntheticInterceptorRecorder.interceptsCalls("LifecycleOnlyInterceptor") > 0);
    }

    @Test
    @DisplayName("19.3 - A custom Interceptor implementing Prioritized is enabled application-wide with its priority value")
    void shouldEnablePrioritizedCustomInterceptorsWithPriorityOrdering() {
        SyntheticInterceptorRecorder.reset();
        Syringe syringe = newSyringe(
                SyntheticInterceptorExtension.class,
                CustomBoundTarget.class
        );
        syringe.addExtension(SyntheticInterceptorExtension.class.getName());
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        List<jakarta.enterprise.inject.spi.Interceptor<?>> resolved = beanManager.resolveInterceptors(
                InterceptionType.AROUND_INVOKE,
                CustomBindingLiteral.INSTANCE
        );

        List<Class<?>> interceptorClasses = new ArrayList<Class<?>>();
        for (jakarta.enterprise.inject.spi.Interceptor<?> interceptor : resolved) {
            interceptorClasses.add(interceptor.getBeanClass());
        }

        int fastIndex = interceptorClasses.indexOf(PrioritizedFastInterceptor.class);
        int slowIndex = interceptorClasses.indexOf(PrioritizedSlowInterceptor.class);

        assertTrue(fastIndex >= 0, "Prioritized fast interceptor should be enabled");
        assertTrue(slowIndex >= 0, "Prioritized slow interceptor should be enabled");
        assertTrue(fastIndex < slowIndex, "Lower priority value must come first");
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        // Keep this test isolated from regression fixtures that intentionally fail deployment.
        syringe.exclude(
                InterceptorResolutionRegressionTest.ConflictingBoundBean.class,
                InterceptorResolutionRegressionTest.FooBinding.class,
                InterceptorResolutionRegressionTest.BarBinding.class,
                InterceptorResolutionRegressionTest.Baz.class
        );
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        return syringe;
    }

    @InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface CustomBinding {
    }

    public static final class CustomBindingLiteral extends AnnotationLiteral<CustomBinding> implements CustomBinding {
        static final CustomBindingLiteral INSTANCE = new CustomBindingLiteral();
    }

    @Dependent
    @CustomBinding
    public static class CustomBoundTarget {
        public String call() {
            return "ok";
        }
    }

    static class SyntheticInterceptorRecorder {
        private static final java.util.Map<String, Integer> BINDING_CALLS = new java.util.HashMap<String, Integer>();
        private static final java.util.Map<String, Integer> INTERCEPTS_CALLS = new java.util.HashMap<String, Integer>();

        static synchronized void reset() {
            BINDING_CALLS.clear();
            INTERCEPTS_CALLS.clear();
        }

        static synchronized void recordBindingCall(String name) {
            Integer current = BINDING_CALLS.get(name);
            BINDING_CALLS.put(name, current == null ? 1 : current + 1);
        }

        static synchronized void recordInterceptsCall(String name) {
            Integer current = INTERCEPTS_CALLS.get(name);
            INTERCEPTS_CALLS.put(name, current == null ? 1 : current + 1);
        }

        static synchronized int bindingCalls(String name) {
            Integer count = BINDING_CALLS.get(name);
            return count == null ? 0 : count;
        }

        static synchronized int interceptsCalls(String name) {
            Integer count = INTERCEPTS_CALLS.get(name);
            return count == null ? 0 : count;
        }
    }

    public static class SyntheticInterceptorExtension implements Extension {
        public void afterBeanDiscovery(@Observes AfterBeanDiscovery afterBeanDiscovery) {
            afterBeanDiscovery.addBean(new BindingAwareInterceptor());
            afterBeanDiscovery.addBean(new LifecycleOnlyInterceptor());
            afterBeanDiscovery.addBean(new PrioritizedFastInterceptor());
            afterBeanDiscovery.addBean(new PrioritizedSlowInterceptor());
        }
    }

    public static class BindingAwareInterceptor implements jakarta.enterprise.inject.spi.Interceptor<Object> {
        @Override
        public Set<Annotation> getInterceptorBindings() {
            SyntheticInterceptorRecorder.recordBindingCall("BindingAwareInterceptor");
            return Collections.<Annotation>singleton(CustomBindingLiteral.INSTANCE);
        }

        @Override
        public boolean intercepts(InterceptionType type) {
            SyntheticInterceptorRecorder.recordInterceptsCall("BindingAwareInterceptor");
            return type == InterceptionType.AROUND_INVOKE;
        }

        @Override
        public Object intercept(InterceptionType type, Object instance, InvocationContext ctx) throws Exception {
            return ctx.proceed();
        }

        @Override
        public Class<?> getBeanClass() {
            return BindingAwareInterceptor.class;
        }

        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            return Collections.emptySet();
        }

        @Override
        public Object create(CreationalContext<Object> creationalContext) {
            return this;
        }

        @Override
        public void destroy(Object instance, CreationalContext<Object> creationalContext) {
            if (creationalContext != null) {
                creationalContext.release();
            }
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public Set<Annotation> getQualifiers() {
            return Collections.<Annotation>singleton(jakarta.enterprise.inject.Default.Literal.INSTANCE);
        }

        @Override
        public Class<? extends Annotation> getScope() {
            return Dependent.class;
        }

        @Override
        public Set<Class<? extends Annotation>> getStereotypes() {
            return Collections.emptySet();
        }

        @Override
        public Set<Type> getTypes() {
            return new HashSet<Type>(Arrays.<Type>asList(Object.class, jakarta.enterprise.inject.spi.Interceptor.class));
        }

        @Override
        public boolean isAlternative() {
            return false;
        }
    }

    public static class LifecycleOnlyInterceptor implements jakarta.enterprise.inject.spi.Interceptor<Object> {
        @Override
        public Set<Annotation> getInterceptorBindings() {
            SyntheticInterceptorRecorder.recordBindingCall("LifecycleOnlyInterceptor");
            return Collections.<Annotation>singleton(CustomBindingLiteral.INSTANCE);
        }

        @Override
        public boolean intercepts(InterceptionType type) {
            SyntheticInterceptorRecorder.recordInterceptsCall("LifecycleOnlyInterceptor");
            return type == InterceptionType.POST_CONSTRUCT;
        }

        @Override
        public Object intercept(InterceptionType type, Object instance, InvocationContext ctx) throws Exception {
            return ctx.proceed();
        }

        @Override
        public Class<?> getBeanClass() {
            return LifecycleOnlyInterceptor.class;
        }

        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            return Collections.emptySet();
        }

        @Override
        public Object create(CreationalContext<Object> creationalContext) {
            return this;
        }

        @Override
        public void destroy(Object instance, CreationalContext<Object> creationalContext) {
            if (creationalContext != null) {
                creationalContext.release();
            }
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public Set<Annotation> getQualifiers() {
            return Collections.<Annotation>singleton(jakarta.enterprise.inject.Default.Literal.INSTANCE);
        }

        @Override
        public Class<? extends Annotation> getScope() {
            return Dependent.class;
        }

        @Override
        public Set<Class<? extends Annotation>> getStereotypes() {
            return Collections.emptySet();
        }

        @Override
        public Set<Type> getTypes() {
            return new HashSet<Type>(Arrays.<Type>asList(Object.class, jakarta.enterprise.inject.spi.Interceptor.class));
        }

        @Override
        public boolean isAlternative() {
            return false;
        }
    }

    public static class PrioritizedFastInterceptor extends BindingAwareInterceptor implements Prioritized {
        @Override
        public int getPriority() {
            return 50;
        }

        @Override
        public Class<?> getBeanClass() {
            return PrioritizedFastInterceptor.class;
        }
    }

    public static class PrioritizedSlowInterceptor extends BindingAwareInterceptor implements Prioritized {
        @Override
        public int getPriority() {
            return 500;
        }

        @Override
        public Class<?> getBeanClass() {
            return PrioritizedSlowInterceptor.class;
        }
    }
}
