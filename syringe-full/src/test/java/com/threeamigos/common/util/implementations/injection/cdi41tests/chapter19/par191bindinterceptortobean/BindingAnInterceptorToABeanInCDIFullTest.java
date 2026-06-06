package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter19.par191bindinterceptortobean;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Vetoed;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InterceptionFactory;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("19.1 - Binding an interceptor to a bean in CDI")
@Execution(ExecutionMode.SAME_THREAD)
public class BindingAnInterceptorToABeanInCDIFullTest {

    @Test
    @DisplayName("19.1 - Interceptor bindings associate interceptors with managed beans that are not decorators")
    void shouldAssociateInterceptorBindingToManagedBean() {
        InvocationRecorder.reset();
        Syringe syringe = newSyringe(TrackedInterceptor.class, NonDecoratorManagedBean.class);
        syringe.setup();

        NonDecoratorManagedBean bean = syringe.inject(NonDecoratorManagedBean.class);
        assertEquals("ok", bean.call());
        assertEquals(Arrays.asList("interceptor-before", "bean-business", "interceptor-after"), InvocationRecorder.events());
    }

    @Test
    @DisplayName("19.1 - Interceptors may be applied programmatically to producer return values using InterceptionFactory")
    void shouldApplyInterceptorsProgrammaticallyToProducerReturnValue() {
        InvocationRecorder.reset();
        Syringe syringe = newSyringe(
                TrackedInterceptor.class,
                InterceptionFactoryProducer.class,
                ProducedServiceConsumer.class
        );
        syringe.setup();

        ProducedServiceConsumer consumer = syringe.inject(ProducedServiceConsumer.class);
        assertEquals("produced-ok", consumer.invoke());
        assertEquals(Arrays.asList("interceptor-before", "produced-business", "interceptor-after"), InvocationRecorder.events());
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        return syringe;
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
    public @interface Tracked {
    }

    public static final class TrackedLiteral extends AnnotationLiteral<Tracked> implements Tracked {
        static final TrackedLiteral INSTANCE = new TrackedLiteral();
    }

    @Tracked
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION + 10)
    public static class TrackedInterceptor {
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

    @Tracked
    @Dependent
    public static class NonDecoratorManagedBean {
        public String call() {
            InvocationRecorder.record("bean-business");
            return "ok";
        }
    }

    @Vetoed
    public static class ProducedService {
        public String business() {
            InvocationRecorder.record("produced-business");
            return "produced-ok";
        }
    }

    @Dependent
    public static class InterceptionFactoryProducer {
        @Inject
        BeanManager beanManager;

        @Produces
        @Dependent
        ProducedService produce() {
            CreationalContext<ProducedService> creationalContext = beanManager.createCreationalContext(null);
            InterceptionFactory<ProducedService> interceptionFactory =
                    beanManager.createInterceptionFactory(creationalContext, ProducedService.class);
            interceptionFactory.configure().add(TrackedLiteral.INSTANCE);
            return interceptionFactory.createInterceptedInstance(new ProducedService());
        }
    }

    @Dependent
    public static class ProducedServiceConsumer {
        @Inject
        ProducedService producedService;

        String invoke() {
            return producedService.business();
        }
    }
}
