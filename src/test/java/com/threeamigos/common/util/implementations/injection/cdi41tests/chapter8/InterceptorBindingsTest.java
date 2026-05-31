package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter8;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.discovery.NonPortableBehaviourException;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Stereotype;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.util.Nonbinding;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundConstruct;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("8 - Interceptor bindings")
@Execution(ExecutionMode.SAME_THREAD)
public class InterceptorBindingsTest {

    @BeforeEach
    void clearRecorder() {
        InterceptorRecorder.reset();
        ConstructorContractInterceptor.reset();
    }

    @Test
    @DisplayName("8 - Interceptors declared with interceptor bindings are applied to target classes, and @Priority defines ordering")
    void shouldApplyBoundInterceptorsInPriorityOrder() {
        Syringe syringe = newSyringe(
                InterceptorRecorder.class,
                PriorityFirstInterceptor.class,
                PrioritySecondInterceptor.class,
                OrderedTargetBean.class
        );

        OrderedTargetBean bean = syringe.inject(OrderedTargetBean.class);
        String result = bean.ping();

        assertEquals("pong", result);
        assertEquals(Arrays.asList(
                "first-before",
                "second-before",
                "target-business",
                "second-after",
                "first-after"
        ), InterceptorRecorder.events());
    }

    @Test
    @DisplayName("8 - @AroundConstruct interception is supported for interceptor classes associated using interceptor bindings")
    void shouldSupportAroundConstructInterception() {
        Syringe syringe = newSyringe(
                InterceptorRecorder.class,
                AroundConstructInterceptor.class,
                ConstructorInterceptedBean.class
        );

        ConstructorInterceptedBean bean = syringe.inject(ConstructorInterceptedBean.class);

        assertTrue(bean.created);
        assertEquals(Arrays.asList(
                "around-construct-before",
                "target-constructor",
                "around-construct-after"
        ), InterceptorRecorder.events());
    }

    @Test
    @DisplayName("8.3 - Regression: @AroundConstruct proceed returns null, target is set after proceed, and setParameters is honored")
    void shouldRespectAroundConstructProceedContractAndParameterReplacement() {
        Syringe syringe = newSyringe(
                ConstructorContractInterceptor.class,
                ConstructorContractBean.class,
                ContractParameter.class
        );

        ConstructorContractBean bean = syringe.inject(ConstructorContractBean.class);

        assertEquals("from-interceptor", bean.receivedValue);
        assertEquals("original", ConstructorContractInterceptor.parameterValueBeforeReplace);
        assertNull(ConstructorContractInterceptor.targetBeforeProceed);
        assertNull(ConstructorContractInterceptor.proceedResult);
        assertNotNull(ConstructorContractInterceptor.targetAfterProceed);
        assertNotEquals(ConstructorContractInterceptor.targetBeforeProceed, ConstructorContractInterceptor.targetAfterProceed);
        assertTrue(ConstructorContractInterceptor.targetAfterProceed instanceof ConstructorContractBean);
    }

    @Test
    @DisplayName("8.3 - Regression: @AroundConstruct proceeds with original constructor exception type")
    void shouldPropagateOriginalConstructorExceptionFromAroundConstructProceed() {
        Syringe syringe = newSyringe(
                ConstructorFailureInterceptor.class,
                ConstructorFailureBean.class
        );

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> syringe.inject(ConstructorFailureBean.class));
        assertEquals("boom", thrown.getMessage());
    }

    @Test
    @DisplayName("8 - @PostConstruct and @PreDestroy are supported on interceptor classes and target beans")
    void shouldSupportLifecycleInterceptionAndBeanLifecycleCallbacks() {
        Syringe syringe = newSyringe(
                InterceptorRecorder.class,
                LifecycleBindingInterceptor.class,
                LifecycleTargetBean.class
        );
        BeanManager beanManager = syringe.getBeanManager();

        LifecycleTargetBean bean = syringe.inject(LifecycleTargetBean.class);
        assertTrue(bean.initialized);
        assertTrue(InterceptorRecorder.events().contains("interceptor-post-construct"));
        assertTrue(InterceptorRecorder.events().contains("bean-post-construct"));

        Bean<LifecycleTargetBean> lifecycleBean = resolveBean(beanManager, LifecycleTargetBean.class);
        jakarta.enterprise.context.spi.CreationalContext<LifecycleTargetBean> creationalContext =
                beanManager.createCreationalContext(lifecycleBean);
        LifecycleTargetBean contextualRef = (LifecycleTargetBean) beanManager.getReference(lifecycleBean, LifecycleTargetBean.class, creationalContext);
        lifecycleBean.destroy(contextualRef, creationalContext);

        assertTrue(InterceptorRecorder.events().contains("interceptor-pre-destroy"));
        assertTrue(InterceptorRecorder.events().contains("bean-pre-destroy"));
    }

    @Test
    @DisplayName("8 - Using @Interceptors class-level declaration is treated as non-portable behavior in CDI Lite")
    void shouldRejectClassLevelInterceptorsDeclarationAsNonPortable() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), NonPortableClassLevelInterceptorsBean.class, LegacyStyleInterceptor.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        assertThrows(NonPortableBehaviourException.class, syringe::setup);
    }

    @Test
    @DisplayName("8 - Using @Interceptors method-level declaration is treated as non-portable behavior in CDI Lite")
    void shouldRejectMethodLevelInterceptorsDeclarationAsNonPortable() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), NonPortableMethodLevelInterceptorsBean.class, LegacyStyleInterceptor.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        assertThrows(NonPortableBehaviourException.class, syringe::setup);
    }

    @Test
    @DisplayName("8.1 - Interceptor binding declared by a stereotype is inherited by a bean declaring that stereotype")
    void shouldInheritInterceptorBindingFromStereotype() {
        Syringe syringe = newSyringe(
                InterceptorRecorder.class,
                StereotypeInheritedBindingInterceptor.class,
                StereotypedInterceptedBean.class
        );

        StereotypedInterceptedBean bean = syringe.inject(StereotypedInterceptedBean.class);
        String result = bean.run();

        assertEquals("ok", result);
        assertEquals(Arrays.asList(
                "first-before",
                "stereotype-target-business",
                "first-after"
        ), InterceptorRecorder.events());
    }

    @Test
    @DisplayName("8.1 - If a stereotype declares interceptor bindings it must be defined as @Target(TYPE)")
    void shouldFailWhenStereotypeWithInterceptorBindingIsNotTypeTargeted() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidMethodTargetStereotype.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("8.2 - If an interceptor declares any scope other than @Dependent the container treats it as a definition error")
    void shouldTreatNonDependentScopedInterceptorAsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidScopedInterceptor.class, OrderedTargetBean.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        DefinitionException thrown = assertThrows(DefinitionException.class, syringe::setup);
        assertTrue(thrown instanceof NonPortableBehaviourException);
    }

    @Test
    @DisplayName("8.3 - Interceptor bindings may be used to associate interceptors with any managed bean")
    void shouldAssociateInterceptorWithManagedBean() {
        Syringe syringe = newSyringe(
                InterceptorRecorder.class,
                ManagedBeanBindingInterceptor.class,
                ManagedBeanWithMethodLevelBinding.class
        );

        ManagedBeanWithMethodLevelBinding bean = syringe.inject(ManagedBeanWithMethodLevelBinding.class);
        String value = bean.invokeBoundMethod();

        assertEquals("managed-ok", value);
        assertEquals(Arrays.asList(
                "managed-binding-before",
                "managed-bean-business",
                "managed-binding-after"
        ), InterceptorRecorder.events());
    }

    @Test
    @DisplayName("8.3 - A bean class interceptor binding replaces same binding type declared by an applied stereotype")
    void shouldReplaceStereotypeBindingWithClassLevelBindingOfSameType() {
        Syringe syringe = newSyringe(
                InterceptorRecorder.class,
                StereotypeLevelConfigurableInterceptor.class,
                BeanLevelConfigurableInterceptor.class,
                ClassOverridesStereotypeBindingBean.class
        );

        ClassOverridesStereotypeBindingBean bean = syringe.inject(ClassOverridesStereotypeBindingBean.class);
        String value = bean.run();

        assertEquals("override-ok", value);
        assertEquals(Arrays.asList(
                "bean-config-before",
                "override-target-business",
                "bean-config-after"
        ), InterceptorRecorder.events());
    }

    @Test
    @DisplayName("8.3 - Interceptor bindings on a producer method are not used to bind interceptors to the producer return value")
    void shouldNotBindProducerMethodInterceptorsToProducedReturnValue() {
        Syringe syringe = new Syringe(
                new InMemoryMessageHandler(),
                InterceptorRecorder.class,
                PriorityFirstInterceptor.class,
                ProducerWithBoundMethod.class
        );
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(NonPortableClassLevelInterceptorsBean.class);
        syringe.exclude(NonPortableMethodLevelInterceptorsBean.class);
        syringe.exclude(LegacyStyleInterceptor.class);
        syringe.exclude(InvalidScopedInterceptor.class);
        syringe.exclude(UnproxyableFinalInterceptedBean.class);
        syringe.exclude(UnproxyableFinalInterceptedConsumer.class);
        syringe.exclude(BoundInterceptorForUnproxyableType.class);
        syringe.exclude(PlainProducedService.class);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        Bean<ProducedService> producedBean = resolveBean(beanManager, ProducedService.class);
        jakarta.enterprise.context.spi.CreationalContext<ProducedService> creationalContext =
                beanManager.createCreationalContext(producedBean);
        ProducedService producedService = (ProducedService) beanManager.getReference(
                producedBean,
                ProducedService.class,
                creationalContext
        );
        InterceptorRecorder.reset();
        String value = producedService.invoke();

        assertEquals("produced-ok", value);
        assertEquals(Arrays.asList("produced-service-business"), InterceptorRecorder.events());
    }

    @Test
    @DisplayName("8.3 - Managed bean with class-level or method-level interceptor binding must be a proxyable bean type")
    void shouldFailForUnproxyableManagedBeanWithBoundInterceptor() {
        Syringe syringe = new Syringe(
                new InMemoryMessageHandler(),
                BoundInterceptorForUnproxyableType.class,
                UnproxyableFinalInterceptedBean.class,
                UnproxyableFinalInterceptedConsumer.class
        );
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("8.4 - Members annotated @Nonbinding are ignored during interceptor resolution")
    void shouldIgnoreNonbindingMemberDuringInterceptorResolution() {
        Syringe syringe = newSyringe(
                InterceptorRecorder.class,
                NonbindingAwareInterceptor.class,
                BeanWithDifferentNonbindingValue.class
        );

        BeanWithDifferentNonbindingValue bean = syringe.inject(BeanWithDifferentNonbindingValue.class);
        String value = bean.call();

        assertEquals("nonbinding-ok", value);
        assertEquals(Arrays.asList(
                "nonbinding-before",
                "nonbinding-target-business",
                "nonbinding-after"
        ), InterceptorRecorder.events());
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(NonPortableClassLevelInterceptorsBean.class);
        syringe.exclude(NonPortableMethodLevelInterceptorsBean.class);
        syringe.exclude(LegacyStyleInterceptor.class);
        syringe.exclude(InvalidScopedInterceptor.class);
        syringe.exclude(UnproxyableFinalInterceptedBean.class);
        syringe.exclude(UnproxyableFinalInterceptedConsumer.class);
        syringe.exclude(BoundInterceptorForUnproxyableType.class);
        syringe.exclude(ProducerWithBoundMethod.class);
        syringe.exclude(ProducedServiceConsumer.class);
        syringe.setup();
        return syringe;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> Bean<T> resolveBean(BeanManager beanManager, Class<T> type) {
        Set<Bean<?>> beans = beanManager.getBeans(type);
        return (Bean<T>) beanManager.resolve((Set) beans);
    }

    @InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
    public @interface OrderedBinding {
    }

    @InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
    public @interface ConstructorBinding {
    }

    @InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
    public @interface LifecycleBinding {
    }

    @InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
    public @interface StereotypeInheritedBinding {
    }

    @InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
    public @interface ConfigurableBinding {
        String value();
    }

    @InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
    public @interface ManagedBeanBinding {
    }

    @InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
    public @interface NonbindingAwareBinding {
        @Nonbinding
        String value();
    }

    @InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
    public @interface ConstructorContractBinding {
    }

    @InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
    public @interface ConstructorFailureBinding {
    }

    @Dependent
    public static class InterceptorRecorder {
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

    @OrderedBinding
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION + 10)
    public static class PriorityFirstInterceptor {
        @AroundInvoke
        Object aroundInvoke(InvocationContext context) throws Exception {
            InterceptorRecorder.record("first-before");
            try {
                return context.proceed();
            } finally {
                InterceptorRecorder.record("first-after");
            }
        }
    }

    @OrderedBinding
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION + 20)
    public static class PrioritySecondInterceptor {
        @AroundInvoke
        Object aroundInvoke(InvocationContext context) throws Exception {
            InterceptorRecorder.record("second-before");
            try {
                return context.proceed();
            } finally {
                InterceptorRecorder.record("second-after");
            }
        }
    }

    @OrderedBinding
    @Dependent
    public static class OrderedTargetBean {
        public String ping() {
            InterceptorRecorder.record("target-business");
            return "pong";
        }
    }

    @Dependent
    public static class ManagedBeanWithMethodLevelBinding {
        @ManagedBeanBinding
        public String invokeBoundMethod() {
            InterceptorRecorder.record("managed-bean-business");
            return "managed-ok";
        }
    }

    @ManagedBeanBinding
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION + 95)
    public static class ManagedBeanBindingInterceptor {
        @AroundInvoke
        Object aroundInvoke(InvocationContext context) throws Exception {
            InterceptorRecorder.record("managed-binding-before");
            try {
                return context.proceed();
            } finally {
                InterceptorRecorder.record("managed-binding-after");
            }
        }
    }

    @ConstructorBinding
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION + 30)
    public static class AroundConstructInterceptor {
        @AroundConstruct
        Object aroundConstruct(InvocationContext context) throws Exception {
            InterceptorRecorder.record("around-construct-before");
            Object instance = context.proceed();
            InterceptorRecorder.record("around-construct-after");
            return instance;
        }
    }

    @ConstructorBinding
    @Dependent
    public static class ConstructorInterceptedBean {
        boolean created;

        public ConstructorInterceptedBean() {
            created = true;
            InterceptorRecorder.record("target-constructor");
        }
    }

    @ConstructorContractBinding
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION + 31)
    public static class ConstructorContractInterceptor {
        static Object targetBeforeProceed;
        static Object proceedResult;
        static Object targetAfterProceed;
        static String parameterValueBeforeReplace;

        static void reset() {
            targetBeforeProceed = null;
            proceedResult = null;
            targetAfterProceed = null;
            parameterValueBeforeReplace = null;
        }

        @AroundConstruct
        Object aroundConstruct(InvocationContext context) throws Exception {
            targetBeforeProceed = context.getTarget();
            Object[] parameters = context.getParameters();
            if (parameters.length == 1 && parameters[0] instanceof ContractParameter) {
                parameterValueBeforeReplace = ((ContractParameter) parameters[0]).value;
            }
            context.setParameters(new Object[]{new ContractParameter("from-interceptor")});
            proceedResult = context.proceed();
            targetAfterProceed = context.getTarget();
            return proceedResult;
        }
    }

    @ConstructorContractBinding
    @Dependent
    public static class ConstructorContractBean {
        final String receivedValue;

        @Inject
        public ConstructorContractBean(ContractParameter parameter) {
            this.receivedValue = parameter.value;
        }
    }

    @Dependent
    public static class ContractParameter {
        final String value;

        public ContractParameter() {
            this("original");
        }

        public ContractParameter(String value) {
            this.value = value;
        }
    }

    @ConstructorFailureBinding
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION + 32)
    public static class ConstructorFailureInterceptor {
        @AroundConstruct
        Object aroundConstruct(InvocationContext context) throws Exception {
            return context.proceed();
        }
    }

    @ConstructorFailureBinding
    @Dependent
    public static class ConstructorFailureBean {
        public ConstructorFailureBean() {
            throw new IllegalStateException("boom");
        }
    }

    @LifecycleBinding
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION + 40)
    public static class LifecycleBindingInterceptor {
        @PostConstruct
        Object postConstruct(InvocationContext context) throws Exception {
            InterceptorRecorder.record("interceptor-post-construct");
            return context.proceed();
        }

        @PreDestroy
        Object preDestroy(InvocationContext context) throws Exception {
            InterceptorRecorder.record("interceptor-pre-destroy");
            return context.proceed();
        }
    }

    @LifecycleBinding
    @Dependent
    public static class LifecycleTargetBean {
        boolean initialized;

        @PostConstruct
        void onPostConstruct() {
            initialized = true;
            InterceptorRecorder.record("bean-post-construct");
        }

        @PreDestroy
        void onPreDestroy() {
            InterceptorRecorder.record("bean-pre-destroy");
        }
    }

    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION + 50)
    public static class LegacyStyleInterceptor {
        @AroundInvoke
        Object aroundInvoke(InvocationContext context) throws Exception {
            return context.proceed();
        }
    }

    @Dependent
    @jakarta.interceptor.Interceptors(LegacyStyleInterceptor.class)
    public static class NonPortableClassLevelInterceptorsBean {
        String run() {
            return "ok";
        }
    }

    @Dependent
    public static class NonPortableMethodLevelInterceptorsBean {
        @jakarta.interceptor.Interceptors(LegacyStyleInterceptor.class)
        String run() {
            return "ok";
        }
    }

    @Stereotype
    @Dependent
    @StereotypeInheritedBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface ActionStereotype {
    }

    @StereotypeInheritedBinding
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION + 70)
    public static class StereotypeInheritedBindingInterceptor {
        @AroundInvoke
        Object aroundInvoke(InvocationContext context) throws Exception {
            InterceptorRecorder.record("first-before");
            try {
                return context.proceed();
            } finally {
                InterceptorRecorder.record("first-after");
            }
        }
    }

    @ActionStereotype
    public static class StereotypedInterceptedBean {
        public String run() {
            InterceptorRecorder.record("stereotype-target-business");
            return "ok";
        }
    }

    @Stereotype
    @OrderedBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface InvalidMethodTargetStereotype {
    }

    @Stereotype
    @Dependent
    @ConfigurableBinding("stereotype")
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface ConfigurableActionStereotype {
    }

    @ConfigurableActionStereotype
    @ConfigurableBinding("bean")
    @Dependent
    public static class ClassOverridesStereotypeBindingBean {
        public String run() {
            InterceptorRecorder.record("override-target-business");
            return "override-ok";
        }
    }

    @ConfigurableBinding("stereotype")
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION + 110)
    public static class StereotypeLevelConfigurableInterceptor {
        @AroundInvoke
        Object aroundInvoke(InvocationContext context) throws Exception {
            InterceptorRecorder.record("stereotype-config-before");
            try {
                return context.proceed();
            } finally {
                InterceptorRecorder.record("stereotype-config-after");
            }
        }
    }

    @ConfigurableBinding("bean")
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION + 111)
    public static class BeanLevelConfigurableInterceptor {
        @AroundInvoke
        Object aroundInvoke(InvocationContext context) throws Exception {
            InterceptorRecorder.record("bean-config-before");
            try {
                return context.proceed();
            } finally {
                InterceptorRecorder.record("bean-config-after");
            }
        }
    }

    @Dependent
    public static class ProducerWithBoundMethod {
        @Produces
        @OrderedBinding
        public ProducedService produceService() {
            return new PlainProducedService();
        }
    }

    public interface ProducedService {
        String invoke();
    }

    public static class PlainProducedService implements ProducedService {
        @Override
        public String invoke() {
            InterceptorRecorder.record("produced-service-business");
            return "produced-ok";
        }
    }

    @Dependent
    public static class ProducedServiceConsumer {
        @Inject
        ProducedService service;
    }

    @NonbindingAwareBinding("interceptor-value")
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION + 130)
    public static class NonbindingAwareInterceptor {
        @AroundInvoke
        Object aroundInvoke(InvocationContext context) throws Exception {
            InterceptorRecorder.record("nonbinding-before");
            try {
                return context.proceed();
            } finally {
                InterceptorRecorder.record("nonbinding-after");
            }
        }
    }

    @NonbindingAwareBinding("bean-value")
    @Dependent
    public static class BeanWithDifferentNonbindingValue {
        public String call() {
            InterceptorRecorder.record("nonbinding-target-business");
            return "nonbinding-ok";
        }
    }

    @OrderedBinding
    @Interceptor
    @ApplicationScoped
    @Priority(Interceptor.Priority.APPLICATION + 90)
    public static class InvalidScopedInterceptor {
        @AroundInvoke
        Object aroundInvoke(InvocationContext context) throws Exception {
            return context.proceed();
        }
    }

    @OrderedBinding
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION + 120)
    public static class BoundInterceptorForUnproxyableType {
        @AroundInvoke
        Object aroundInvoke(InvocationContext context) throws Exception {
            return context.proceed();
        }
    }

    @OrderedBinding
    @Dependent
    public static final class UnproxyableFinalInterceptedBean {
        public String value() {
            return "x";
        }
    }

    @Dependent
    public static class UnproxyableFinalInterceptedConsumer {
        @Inject
        UnproxyableFinalInterceptedBean bean;
    }
}
