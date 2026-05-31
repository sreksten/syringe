package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter7.par72containerinvocationsandinterceptors;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("7.2 - Container invocations and interceptors")
@Execution(ExecutionMode.SAME_THREAD)
public class ContainerInvocationsAndInterceptorsTest {

    @Test
    @DisplayName("7.2 - Invocation through a contextual reference is treated as business method invocation and passes through method interceptors")
    void shouldInterceptBusinessMethodInvocationThroughContextualReference() {
        InterceptorRecorder.reset();
        Syringe syringe = newSyringe(
                InterceptorRecorder.class,
                TrackedAroundInvokeInterceptor.class,
                TrackedLifecycleInterceptor.class,
                InterceptedComponent.class
        );
        InterceptedComponent component = syringe.inject(InterceptedComponent.class);

        InterceptorRecorder.resetBusiness();
        assertEquals("business-ok", component.businessMethod());
        assertTrue(InterceptorRecorder.businessMethods().contains("businessMethod"));
    }

    @Test
    @DisplayName("7.2 - Initializer method invocations by container are not business method invocations and are not intercepted by method interceptors")
    void shouldNotInterceptInitializerMethodInvocation() {
        InterceptorRecorder.reset();
        Syringe syringe = newSyringe(
                InterceptorRecorder.class,
                TrackedAroundInvokeInterceptor.class,
                TrackedLifecycleInterceptor.class,
                InterceptedComponent.class
        );
        InterceptedComponent component = syringe.inject(InterceptedComponent.class);

        assertFalse(InterceptorRecorder.businessMethods().contains("initializerMethod"));
    }

    @Test
    @DisplayName("7.2 - Producer and disposer method invocations by container are business invocations and are intercepted by method interceptors")
    void shouldInterceptProducerAndDisposerMethodInvocations() {
        InterceptorRecorder.reset();
        Syringe syringe = newSyringe(
                InterceptorRecorder.class,
                TrackedAroundInvokeInterceptor.class,
                TrackedLifecycleInterceptor.class,
                InterceptedComponent.class,
                ProducedStringConsumer.class
        );
        BeanManager beanManager = syringe.getBeanManager();

        ProducedStringConsumer consumer = syringe.inject(ProducedStringConsumer.class);
        assertEquals("produced-value", consumer.value);

        Bean<String> producedBean = resolveQualifiedBean(beanManager, String.class, TrackedProductLiteral.INSTANCE);
        jakarta.enterprise.context.spi.CreationalContext<String> creationalContext =
                beanManager.createCreationalContext(producedBean);
        String produced = (String) beanManager.getReference(producedBean, String.class, creationalContext);
        producedBean.destroy(produced, creationalContext);

        assertTrue(InterceptorRecorder.businessMethods().contains("produceTrackedValue"));
        assertTrue(InterceptorRecorder.businessMethods().contains("disposeTrackedValue"));
    }

    @Test
    @DisplayName("7.2 - Observer method invocation by container is a business invocation and is intercepted by method interceptors")
    void shouldInterceptObserverMethodInvocation() {
        InterceptorRecorder.reset();
        Syringe syringe = newSyringe(
                InterceptorRecorder.class,
                TrackedAroundInvokeInterceptor.class,
                TrackedLifecycleInterceptor.class,
                InterceptedComponent.class,
                TestEvent.class
        );
        BeanManager beanManager = syringe.getBeanManager();

        beanManager.getEvent().select(TestEvent.class).fire(new TestEvent("evt"));

        assertTrue(InterceptorRecorder.businessMethods().contains("observeTrackedEvent"));
    }

    @Test
    @DisplayName("7.2 - Lifecycle callback invocations are not business invocations, but lifecycle interceptor callbacks are invoked")
    void shouldInvokeLifecycleInterceptorsButNotTreatLifecycleAsBusiness() {
        InterceptorRecorder.reset();
        Syringe syringe = newSyringe(
                InterceptorRecorder.class,
                TrackedAroundInvokeInterceptor.class,
                TrackedLifecycleInterceptor.class,
                InterceptedComponent.class
        );

        InterceptedComponent component = syringe.inject(InterceptedComponent.class);
        assertNotNull(component);
        assertTrue(InterceptorRecorder.lifecycleCallbacks().contains("interceptor-post-construct"));
        assertTrue(InterceptorRecorder.lifecycleCallbacks().contains("bean-post-construct"));
        assertFalse(InterceptorRecorder.businessMethods().contains("postConstructCallback"));
    }

    @Test
    @DisplayName("7.2 - Invocations of java.lang.Object methods are not business method invocations and are not intercepted")
    void shouldNotInterceptObjectMethodInvocations() {
        InterceptorRecorder.reset();
        Syringe syringe = newSyringe(
                InterceptorRecorder.class,
                TrackedAroundInvokeInterceptor.class,
                TrackedLifecycleInterceptor.class,
                InterceptedComponent.class
        );
        InterceptedComponent component = syringe.inject(InterceptedComponent.class);

        InterceptorRecorder.resetBusiness();
        component.hashCode();
        component.toString();

        assertTrue(InterceptorRecorder.businessMethods().isEmpty());
    }

    @Test
    @DisplayName("7.2 - Non-business invocations are normal Java calls and do not pass through method interceptors")
    void shouldNotInterceptDirectNonContextualJavaCall() {
        InterceptorRecorder.reset();
        InterceptedComponent direct = new InterceptedComponent();

        assertEquals("business-ok", direct.businessMethod());
        assertTrue(InterceptorRecorder.businessMethods().isEmpty());
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();
        return syringe;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> Bean<T> resolveQualifiedBean(BeanManager beanManager, Class<T> beanType, java.lang.annotation.Annotation qualifier) {
        Set<Bean<?>> beans = beanManager.getBeans(beanType, qualifier);
        return (Bean<T>) beanManager.resolve((Set) beans);
    }

    @InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface TrackedInvocation {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE})
    public @interface TrackedProduct {
    }

    public static final class TrackedProductLiteral extends AnnotationLiteral<TrackedProduct> implements TrackedProduct {
        static final TrackedProductLiteral INSTANCE = new TrackedProductLiteral();
    }

    @Dependent
    public static class InterceptorRecorder {
        private static final List<String> BUSINESS_METHODS = new ArrayList<String>();
        private static final List<String> LIFECYCLE_CALLBACKS = new ArrayList<String>();

        static synchronized void reset() {
            BUSINESS_METHODS.clear();
            LIFECYCLE_CALLBACKS.clear();
        }

        static synchronized void resetBusiness() {
            BUSINESS_METHODS.clear();
        }

        static synchronized void recordBusiness(String methodName) {
            BUSINESS_METHODS.add(methodName);
        }

        static synchronized void recordLifecycle(String callbackName) {
            LIFECYCLE_CALLBACKS.add(callbackName);
        }

        static synchronized List<String> businessMethods() {
            return new ArrayList<String>(BUSINESS_METHODS);
        }

        static synchronized List<String> lifecycleCallbacks() {
            return new ArrayList<String>(LIFECYCLE_CALLBACKS);
        }
    }

    @TrackedInvocation
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION + 10)
    public static class TrackedAroundInvokeInterceptor {
        @AroundInvoke
        public Object aroundInvoke(InvocationContext context) throws Exception {
            if (context.getMethod() != null) {
                InterceptorRecorder.recordBusiness(context.getMethod().getName());
            }
            return context.proceed();
        }
    }

    @TrackedInvocation
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION + 20)
    public static class TrackedLifecycleInterceptor {
        @PostConstruct
        public Object postConstruct(InvocationContext context) throws Exception {
            InterceptorRecorder.recordLifecycle("interceptor-post-construct");
            return context.proceed();
        }

        @PreDestroy
        public Object preDestroy(InvocationContext context) throws Exception {
            InterceptorRecorder.recordLifecycle("interceptor-pre-destroy");
            return context.proceed();
        }
    }

    @TrackedInvocation
    @Dependent
    public static class InterceptedComponent {
        boolean initializerCalled;

        @Inject
        void initializerMethod() {
            initializerCalled = true;
        }

        @PostConstruct
        void postConstructCallback() {
            InterceptorRecorder.recordLifecycle("bean-post-construct");
        }

        public String businessMethod() {
            return "business-ok";
        }

        @Produces
        @TrackedProduct
        String produceTrackedValue() {
            return "produced-value";
        }

        void disposeTrackedValue(@Disposes @TrackedProduct String value) {
            // recorder is updated by the interceptor around this method
        }

        void observeTrackedEvent(@Observes TestEvent event) {
            // recorder is updated by the interceptor around this method
        }
    }

    @Dependent
    public static class ProducedStringConsumer {
        @Inject
        @TrackedProduct
        String value;
    }

    public static class TestEvent {
        private final String value;

        public TestEvent(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
