package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter23.par238interceptionfactory;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.discovery.NonPortableBehaviourException;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.UnproxyableResolutionException;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InterceptionFactory;
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
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("23.8 - InterceptionFactory interface")
@Execution(ExecutionMode.SAME_THREAD)
public class InterceptionFactoryInterfaceTest {

    @Test
    @DisplayName("23.8 - configure() returns the same AnnotatedTypeConfigurator instance")
    void shouldReturnSameConfiguratorInstance() {
        Syringe syringe = newSyringe(FactoryTarget.class, FactoryBindingInterceptor.class);
        BeanManager beanManager = syringe.getBeanManager();
        CreationalContext<FactoryTarget> context = beanManager.createCreationalContext(null);

        InterceptionFactory<FactoryTarget> factory = beanManager.createInterceptionFactory(context, FactoryTarget.class);
        assertSame(factory.configure(), factory.configure());
    }

    @Test
    @DisplayName("23.8 - createInterceptedInstance wraps provided instance and applies method interceptors")
    void shouldCreateInterceptedInstanceWithMethodInterceptors() {
        FactoryRecorder.reset();
        Syringe syringe = newSyringe(FactoryTarget.class, FactoryBindingInterceptor.class);
        BeanManager beanManager = syringe.getBeanManager();
        CreationalContext<FactoryTarget> context = beanManager.createCreationalContext(null);

        InterceptionFactory<FactoryTarget> factory = beanManager.createInterceptionFactory(context, FactoryTarget.class);
        factory.configure().add(FactoryBindingLiteral.INSTANCE);
        FactoryTarget wrapped = factory.createInterceptedInstance(new FactoryTarget());

        assertEquals("ok", wrapped.business());
        assertEquals(3, FactoryRecorder.events().size());
        assertEquals("before", FactoryRecorder.events().get(0));
        assertEquals("business", FactoryRecorder.events().get(1));
        assertEquals("after", FactoryRecorder.events().get(2));
    }

    @Test
    @DisplayName("23.8 - createInterceptedInstance may only be called once")
    void shouldRejectSecondCreateInterceptedInstanceInvocation() {
        Syringe syringe = newSyringe(FactoryTarget.class, FactoryBindingInterceptor.class);
        BeanManager beanManager = syringe.getBeanManager();
        CreationalContext<FactoryTarget> context = beanManager.createCreationalContext(null);

        InterceptionFactory<FactoryTarget> factory = beanManager.createInterceptionFactory(context, FactoryTarget.class);
        factory.configure().add(FactoryBindingLiteral.INSTANCE);
        factory.createInterceptedInstance(new FactoryTarget());

        assertThrows(IllegalStateException.class, () -> factory.createInterceptedInstance(new FactoryTarget()));
    }

    @Test
    @DisplayName("23.8 - unproxyable intercepted instance type throws UnproxyableResolutionException")
    void shouldRejectUnproxyableTypeWithoutIgnoreFinalMethods() {
        Syringe syringe = newSyringe(FinalMethodTarget.class, FactoryBindingInterceptor.class);
        BeanManager beanManager = syringe.getBeanManager();
        CreationalContext<FinalMethodTarget> context = beanManager.createCreationalContext(null);

        InterceptionFactory<FinalMethodTarget> factory = beanManager.createInterceptionFactory(context, FinalMethodTarget.class);
        factory.configure().add(FactoryBindingLiteral.INSTANCE);

        assertThrows(UnproxyableResolutionException.class,
                () -> factory.createInterceptedInstance(new FinalMethodTarget()));
    }

    @Test
    @DisplayName("23.8 - ignoreFinalMethods loosens unproxyable rule for final methods")
    void shouldAllowFinalMethodsWhenIgnoreFinalMethodsIsUsed() {
        Syringe syringe = newSyringe(FinalMethodTarget.class, FactoryBindingInterceptor.class);
        BeanManager beanManager = syringe.getBeanManager();
        CreationalContext<FinalMethodTarget> context = beanManager.createCreationalContext(null);

        InterceptionFactory<FinalMethodTarget> factory = beanManager.createInterceptionFactory(context, FinalMethodTarget.class);
        factory.configure().add(FactoryBindingLiteral.INSTANCE);
        FinalMethodTarget wrapped = factory.ignoreFinalMethods().createInterceptedInstance(new FinalMethodTarget());

        assertNotNull(wrapped);
    }

    @Test
    @DisplayName("23.8 - Passing internal container construct to createInterceptedInstance is non-portable")
    void shouldRejectInternalContainerConstructInstance() {
        Syringe syringe = newSyringe(ApplicationScopedProxyTarget.class, FactoryBindingInterceptor.class);
        BeanManager beanManager = syringe.getBeanManager();
        CreationalContext<ApplicationScopedProxyTarget> context = beanManager.createCreationalContext(null);

        ApplicationScopedProxyTarget proxyInstance = syringe.inject(ApplicationScopedProxyTarget.class);
        InterceptionFactory<ApplicationScopedProxyTarget> factory =
                beanManager.createInterceptionFactory(context, ApplicationScopedProxyTarget.class);
        factory.configure().add(FactoryBindingLiteral.INSTANCE);

        assertThrows(NonPortableBehaviourException.class,
                () -> factory.createInterceptedInstance(proxyInstance));
    }

    @Test
    @DisplayName("23.8 - Container provides built-in @Dependent @Default bean for InterceptionFactory")
    void shouldExposeInterceptionFactoryBuiltInBean() {
        Syringe syringe = newSyringe(FactoryTarget.class);
        BeanManager beanManager = syringe.getBeanManager();

        Set<Bean<?>> beans = beanManager.getBeans(InterceptionFactory.class, Default.Literal.INSTANCE);
        Bean<?> resolved = beanManager.resolve((Set) beans);

        assertNotNull(resolved);
        assertEquals(Dependent.class, resolved.getScope());
    }

    @Test
    @DisplayName("23.8 - InterceptionFactory injection point outside producer method parameter is a definition error")
    void shouldFailWhenInterceptionFactoryIsInjectedOutsideProducerMethodParameter() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidFieldInjectionBean.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        assertThrows(jakarta.enterprise.inject.spi.DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("23.8 - InterceptionFactory injection point with non-Java-class type parameter is non-portable")
    void shouldRejectNonJavaClassInterceptionFactoryTypeParameterInProducerMethod() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidInterceptionFactoryProducer.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        assertThrows(NonPortableBehaviourException.class, syringe::setup);
    }

    @Test
    @DisplayName("23.8 - Producer method parameter InterceptionFactory<T> is supported")
    void shouldSupportInterceptionFactoryProducerMethodParameter() {
        FactoryRecorder.reset();
        Syringe syringe = new Syringe(new InMemoryMessageHandler(),
                FactoryProductProducer.class,
                FactoryBindingInterceptor.class,
                FactoryProductConsumer.class
        );
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(InvalidFieldInjectionBean.class, InvalidInterceptionFactoryProducer.class, FactoryProduct.class);
        syringe.setup();

        FactoryProductConsumer consumer = syringe.inject(FactoryProductConsumer.class);
        assertEquals("product-ok", consumer.invoke());
        assertTrue(FactoryRecorder.events().contains("before"));
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(
                InvalidFieldInjectionBean.class,
                InvalidInterceptionFactoryProducer.class,
                FactoryProductProducer.class,
                FactoryProductConsumer.class,
                FactoryProduct.class
        );
        syringe.setup();
        return syringe;
    }

    @InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface FactoryBinding {
    }

    public static class FactoryBindingLiteral extends jakarta.enterprise.util.AnnotationLiteral<FactoryBinding>
            implements FactoryBinding {
        static final FactoryBindingLiteral INSTANCE = new FactoryBindingLiteral();
    }

    @Interceptor
    @FactoryBinding
    @jakarta.annotation.Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 10)
    public static class FactoryBindingInterceptor {
        @AroundInvoke
        public Object around(InvocationContext context) throws Exception {
            FactoryRecorder.record("before");
            try {
                return context.proceed();
            } finally {
                FactoryRecorder.record("after");
            }
        }
    }

    public static class FactoryTarget {
        public String business() {
            FactoryRecorder.record("business");
            return "ok";
        }
    }

    public static class FinalMethodTarget {
        public final String finalBusiness() {
            return "final";
        }
    }

    @ApplicationScoped
    public static class ApplicationScopedProxyTarget {
        public String ping() {
            return "pong";
        }
    }

    @Dependent
    public static class InvalidFieldInjectionBean {
        @Inject
        InterceptionFactory<FactoryTarget> interceptionFactory;
    }

    @Dependent
    public static class InvalidInterceptionFactoryProducer {
        @Produces
        List<String> produce(InterceptionFactory<List<String>> factory) {
            return java.util.Collections.emptyList();
        }
    }

    public static class FactoryProduct {
        public String ping() {
            return "product-ok";
        }
    }

    @Dependent
    public static class FactoryProductProducer {
        @Produces
        @Dependent
        FactoryProduct produce(InterceptionFactory<FactoryProduct> factory) {
            factory.configure().add(new FactoryBinding() {
                @Override
                public Class<? extends java.lang.annotation.Annotation> annotationType() {
                    return FactoryBinding.class;
                }
            });
            return factory.createInterceptedInstance(new FactoryProduct() {
                @Override
                public String ping() {
                    FactoryRecorder.record("product-business");
                    return super.ping();
                }
            });
        }
    }

    @Dependent
    public static class FactoryProductConsumer {
        @Inject
        FactoryProduct product;

        String invoke() {
            return product.ping();
        }
    }

    public static class FactoryRecorder {
        private static final java.util.concurrent.CopyOnWriteArrayList<String> EVENTS =
                new java.util.concurrent.CopyOnWriteArrayList<String>();

        static void reset() {
            EVENTS.clear();
        }

        static void record(String value) {
            EVENTS.add(value);
        }

        static List<String> events() {
            return EVENTS;
        }
    }
}
