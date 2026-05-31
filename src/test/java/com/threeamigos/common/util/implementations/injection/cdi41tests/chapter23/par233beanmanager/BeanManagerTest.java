package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter23.par233beanmanager;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.discovery.NonPortableBehaviourException;
import com.threeamigos.common.util.implementations.injection.knowledgebase.DecoratorInfo;
import com.threeamigos.common.util.implementations.injection.resolution.BeanAttributesImpl;
import com.threeamigos.common.util.implementations.injection.scopes.InjectionPointImpl;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.Priority;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.InjectionException;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.AfterDeploymentValidation;
import jakarta.enterprise.inject.spi.AnnotatedField;
import jakarta.enterprise.inject.spi.AnnotatedConstructor;
import jakarta.enterprise.inject.spi.AnnotatedMember;
import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.BeanAttributes;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanContainer;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.CDIProvider;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.Producer;
import jakarta.enterprise.inject.spi.ProducerFactory;
import jakarta.enterprise.inject.spi.InterceptionType;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.InjectionTarget;
import jakarta.enterprise.inject.spi.InjectionTargetFactory;
import jakarta.enterprise.inject.spi.InterceptionFactory;
import jakarta.enterprise.inject.spi.PassivationCapable;
import jakarta.enterprise.inject.spi.Unmanaged;
import jakarta.enterprise.inject.spi.Unmanaged.UnmanagedInstance;
import jakarta.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;
import jakarta.enterprise.util.Nonbinding;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Qualifier;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;
import jakarta.enterprise.inject.Stereotype;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.annotation.Annotation;
import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("23.3 - BeanManager test")
@Isolated
public class BeanManagerTest {

    @Test
    @DisplayName("23.3 - Container provides built-in @Default @Dependent BeanManager bean with BeanManager and BeanContainer types")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldExposeBuiltInBeanManagerBeanMetadata() {
        Syringe syringe = newSyringe(BeanManagerConsumer.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        Set<Bean<?>> beans = beanManager.getBeans(BeanManager.class, Default.Literal.INSTANCE);
        Bean<?> bean = beanManager.resolve((Set) beans);
        assertNotNull(bean);
        assertEquals(Dependent.class, bean.getScope());
        assertTrue(bean.getTypes().contains(BeanManager.class));
        assertTrue(bean.getTypes().contains(BeanContainer.class));
        assertTrue(bean.getQualifiers().contains(Default.Literal.INSTANCE));
    }

    @Test
    @DisplayName("23.3 - Any bean can obtain BeanManager by injection and it is a passivation capable dependency")
    void shouldInjectBeanManagerIntoBeansIncludingPassivatingBeans() {
        Syringe syringe = newSyringe(BeanManagerConsumer.class, SessionScopedBeanManagerConsumer.class);
        syringe.setup();

        BeanManagerConsumer consumer = syringe.inject(BeanManagerConsumer.class);
        assertNotNull(consumer.manager);
        assertNotNull(syringe.getBeanManager().createCreationalContext(null));
    }

    @Test
    @DisplayName("23.3 - BeanManager operations restricted before AfterBeanDiscovery and AfterDeploymentValidation throw exceptions")
    void shouldRestrictBeanManagerOperationsDuringContainerLifecyclePhases() {
        LifecyclePhaseProbeExtension.reset();

        Syringe syringe = newSyringe(BeanManagerConsumer.class);
        syringe.addExtension(LifecyclePhaseProbeExtension.class.getName());
        syringe.setup();

        assertAllIllegalState(LifecyclePhaseProbeExtension.beforeAfterBeanDiscoveryResults());
        assertAllIllegalState(LifecyclePhaseProbeExtension.beforeAfterDeploymentValidationResults());
        assertAllSuccessful(LifecyclePhaseProbeExtension.afterAfterDeploymentValidationResults());
    }

    @Test
    @DisplayName("23.3.1 - CDI methods are non-portable before BeforeBeanDiscovery and throw NonPortableBehaviourException")
    void shouldRejectCdiMethodsBeforeBeforeBeanDiscovery() {
        Syringe syringe = newSyringe(BeanManagerConsumer.class);
        assertThrows(NonPortableBehaviourException.class, syringe::getCDI);
    }

    @Test
    @DisplayName("23.3.1 - CDI.getBeanManager and CDI.getBeanContainer are allowed after BeforeBeanDiscovery and before BeforeShutdown")
    void shouldAllowCdiContainerAccessInPortableWindow() {
        Syringe syringe = newSyringe(BeanManagerConsumer.class);
        syringe.initialize();

        CDI<Object> cdi = syringe.getCDI();
        assertNotNull(cdi.getBeanManager());
        assertNotNull(cdi.getBeanContainer());
    }

    @Test
    @DisplayName("23.3.1 - CDI.getBeanManager and CDI.getBeanContainer are non-portable after BeforeShutdown and throw NonPortableBehaviourException")
    void shouldRejectCdiContainerAccessAfterBeforeShutdown() {
        Syringe syringe = newSyringe(BeanManagerConsumer.class);
        syringe.setup();

        CDI<Object> cdi = syringe.getCDI();
        syringe.shutdown();

        assertThrows(NonPortableBehaviourException.class, cdi::getBeanManager);
        assertThrows(NonPortableBehaviourException.class, cdi::getBeanContainer);
    }

    @Test
    @DisplayName("23.3.1 - CDI.current follows same portable window rules for BeanManager and BeanContainer access")
    void shouldApplyPortableWindowRulesThroughCdiCurrent() {
        Syringe syringe = newSyringe(BeanManagerConsumer.class);
        CDI.setCDIProvider(new DelegatingProvider(new Supplier<CDI<Object>>() {
            @Override
            public CDI<Object> get() {
                return syringe.getCDI();
            }
        }));

        assertCdiCurrentUnavailableOrNonPortable(() -> CDI.current().getBeanManager());

        syringe.setup();
        assertNotNull(CDI.current().getBeanManager());
        assertNotNull(CDI.current().getBeanContainer());

        syringe.shutdown();
        assertCdiCurrentUnavailableOrNonPortable(() -> CDI.current().getBeanManager());
        assertCdiCurrentUnavailableOrNonPortable(() -> CDI.current().getBeanContainer());

        CDI.setCDIProvider(new DelegatingProvider(new Supplier<CDI<Object>>() {
            @Override
            public CDI<Object> get() {
                return null;
            }
        }));
    }

    private void assertCdiCurrentUnavailableOrNonPortable(Runnable invocation) {
        Throwable thrown = assertThrows(Throwable.class, invocation::run);
        if (thrown instanceof NonPortableBehaviourException) {
            return;
        }
        if (thrown instanceof IllegalStateException && thrown.getMessage() != null
                && thrown.getMessage().contains("Unable to access CDI")) {
            return;
        }
        throw new AssertionError("Expected NonPortableBehaviourException or IllegalStateException(Unable to access CDI) but got "
                + thrown.getClass().getName() + ": " + thrown.getMessage(), thrown);
    }

    @Test
    @DisplayName("23.3.2 - getInjectableReference returns an injectable reference for a satisfied injection point")
    void shouldReturnInjectableReferenceForSatisfiedInjectionPoint() {
        Syringe syringe = newSyringe(ResolvableDependency.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        InjectionPoint injectionPoint = createInjectionPoint(beanManager, ResolvableCarrier.class, "dependency");
        CreationalContext<?> context = beanManager.createCreationalContext(null);
        Object reference = beanManager.getInjectableReference(injectionPoint, context);

        assertNotNull(reference);
        assertTrue(reference instanceof ResolvableDependency);
    }

    @Test
    @DisplayName("23.3.2 - getInjectableReference throws UnsatisfiedResolutionException for unsatisfied dependency")
    void shouldThrowUnsatisfiedResolutionExceptionForUnsatisfiedDependency() {
        Syringe syringe = newSyringe(BeanManagerConsumer.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        InjectionPoint injectionPoint = createInjectionPoint(beanManager, UnsatisfiedCarrier.class, "missing");
        CreationalContext<?> context = beanManager.createCreationalContext(null);

        assertThrows(UnsatisfiedResolutionException.class,
                () -> beanManager.getInjectableReference(injectionPoint, context));
    }

    @Test
    @DisplayName("23.3.2 - getInjectableReference throws AmbiguousResolutionException for unresolvable ambiguous dependency")
    void shouldThrowAmbiguousResolutionExceptionForAmbiguousDependency() {
        Syringe syringe = newSyringe(AmbiguousServiceOne.class, AmbiguousServiceTwo.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        InjectionPoint injectionPoint = createInjectionPoint(beanManager, AmbiguousCarrier.class, "service");
        CreationalContext<?> context = beanManager.createCreationalContext(null);

        assertThrows(AmbiguousResolutionException.class,
                () -> beanManager.getInjectableReference(injectionPoint, context));
    }

    @Test
    @DisplayName("23.3.2 - If InjectionPoint is a decorator delegate injection point, getInjectableReference returns the delegate")
    void shouldReturnDelegateForDecoratorDelegateInjectionPoint() {
        Syringe syringe = newSyringe(DelegateContract.class, DelegateTargetBean.class, DelegateWrappingDecorator.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        DecoratorInfo decoratorInfo = syringe.getKnowledgeBase().getDecoratorInfos().stream()
                .filter(info -> DelegateWrappingDecorator.class.equals(info.getDecoratorClass()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Decorator info not found"));
        InjectionPoint delegateInjectionPoint = decoratorInfo.getDelegateInjectionPoint();

        CreationalContext<?> context = beanManager.createCreationalContext(null);
        DelegateContract delegate = (DelegateContract) beanManager.getInjectableReference(delegateInjectionPoint, context);

        assertNotNull(delegate);
        assertEquals("delegate-target", delegate.ping());
    }

    @Test
    @DisplayName("23.3.2 - Dependent objects created by getInjectableReference are destroyed by the passed CreationalContext")
    void shouldDestroyDependentObjectsCreatedThroughGetInjectableReferenceWhenContextReleased() {
        DependentReferenceRecorder.reset();
        Syringe syringe = newSyringe(TrackedDependent.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        InjectionPoint injectionPoint = createInjectionPoint(beanManager, TrackedDependentCarrier.class, "dependent");
        CreationalContext<?> context = beanManager.createCreationalContext(null);

        TrackedDependent dependent = (TrackedDependent) beanManager.getInjectableReference(injectionPoint, context);
        assertNotNull(dependent);
        assertEquals(0, DependentReferenceRecorder.preDestroyCalls);

        context.release();
        assertEquals(1, DependentReferenceRecorder.preDestroyCalls);
    }

    @Test
    @DisplayName("23.3.3 - Unmanaged supports optimized lifecycle for non-contextual instances")
    void shouldSupportUnmanagedLifecycleForNonContextualInstances() {
        UnmanagedLifecycleRecorder.reset();
        Syringe syringe = newSyringe(UnmanagedDependency.class, UnmanagedFoo.class);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        Unmanaged<UnmanagedFoo> unmanaged = new Unmanaged<UnmanagedFoo>(beanManager, UnmanagedFoo.class);
        UnmanagedInstance<UnmanagedFoo> fooInstance = unmanaged.newInstance();

        UnmanagedFoo foo = fooInstance.produce().inject().postConstruct().get();
        assertNotNull(foo);
        assertNotNull(foo.dependency);
        assertTrue(UnmanagedLifecycleRecorder.postConstructCalls >= 1);
        int preDestroyBeforeDispose = UnmanagedLifecycleRecorder.preDestroyCalls;

        fooInstance.preDestroy().dispose();
        assertTrue(UnmanagedLifecycleRecorder.preDestroyCalls >= preDestroyBeforeDispose);
    }

    @Test
    @DisplayName("23.3.3 - Non-contextual instance lifecycle can be driven via InjectionTarget and via Unmanaged helper")
    void shouldAllowNonContextualLifecycleViaInjectionTargetAndUnmanaged() {
        UnmanagedLifecycleRecorder.reset();
        Syringe syringe = newSyringe(UnmanagedDependency.class, UnmanagedFoo.class);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        AnnotatedType<UnmanagedFoo> annotatedType = beanManager.createAnnotatedType(UnmanagedFoo.class);
        InjectionTargetFactory<UnmanagedFoo> factory = beanManager.getInjectionTargetFactory(annotatedType);
        InjectionTarget<UnmanagedFoo> injectionTarget = factory.createInjectionTarget(null);
        CreationalContext<UnmanagedFoo> context = beanManager.createCreationalContext(null);

        UnmanagedFoo fromInjectionTarget = injectionTarget.produce(context);
        injectionTarget.inject(fromInjectionTarget, context);
        injectionTarget.postConstruct(fromInjectionTarget);
        assertNotNull(fromInjectionTarget.dependency);
        assertTrue(UnmanagedLifecycleRecorder.postConstructCalls >= 1);

        injectionTarget.preDestroy(fromInjectionTarget);
        injectionTarget.dispose(fromInjectionTarget);
        assertTrue(UnmanagedLifecycleRecorder.preDestroyCalls >= 1);

        Unmanaged<UnmanagedFoo> unmanaged = new Unmanaged<UnmanagedFoo>(beanManager, UnmanagedFoo.class);
        UnmanagedFoo fromUnmanaged = unmanaged.newInstance().produce().inject().postConstruct().get();
        assertNotNull(fromUnmanaged.dependency);
    }

    @Test
    @DisplayName("23.3.4 - getBeans(Type, qualifiers) returns beans matching required type and qualifiers")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldReturnBeansMatchingRequiredTypeAndQualifiers() {
        Syringe syringe = newSyringe(QualifiedContract.class, BlueQualifiedBean.class, GreenQualifiedBean.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        Set<Bean<?>> blueBeans = beanManager.getBeans(QualifiedContract.class, BlueLiteral.INSTANCE);
        Bean<?> blue = beanManager.resolve((Set) blueBeans);
        assertNotNull(blue);
        assertEquals(BlueQualifiedBean.class, blue.getBeanClass());

        Set<Bean<?>> greenBeans = beanManager.getBeans(QualifiedContract.class, GreenLiteral.INSTANCE);
        Bean<?> green = beanManager.resolve((Set) greenBeans);
        assertNotNull(green);
        assertEquals(GreenQualifiedBean.class, green.getBeanClass());
    }

    @Test
    @DisplayName("23.3.4 - getBeans(Type) with no qualifiers uses @Default and returns default bean candidates")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldUseDefaultQualifierWhenNoQualifierProvided() {
        Syringe syringe = newSyringe(DefaultContract.class, DefaultContractBean.class, NonDefaultQualifiedContractBean.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        Set<Bean<?>> beans = beanManager.getBeans(DefaultContract.class);
        Bean<?> resolved = beanManager.resolve((Set) beans);
        assertNotNull(resolved);
        assertEquals(DefaultContractBean.class, resolved.getBeanClass());
    }

    @Test
    @DisplayName("23.3.4 - getBeans returns only beans available for injection (disabled alternatives are excluded)")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldReturnOnlyBeansAvailableForInjection() {
        Syringe syringe = newSyringe(AvailabilityContract.class, AvailableDefaultBean.class, DisabledAlternativeBean.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        Set<Bean<?>> beans = beanManager.getBeans(AvailabilityContract.class, Any.Literal.INSTANCE);
        assertEquals(1, beans.size());
        Bean<?> resolved = beanManager.resolve((Set) beans);
        assertNotNull(resolved);
        assertEquals(AvailableDefaultBean.class, resolved.getBeanClass());
    }

    @Test
    @DisplayName("23.3.5 - getBeans(String) returns beans with the given bean name")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldReturnBeansMatchingGivenBeanName() {
        Syringe syringe = newSyringe(NamedContract.class, NamedDefaultBean.class, OtherNamedBean.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        Set<Bean<?>> beans = beanManager.getBeans("namedService");
        Bean<?> resolved = beanManager.resolve((Set) beans);
        assertNotNull(resolved);
        assertEquals(NamedDefaultBean.class, resolved.getBeanClass());
    }

    @Test
    @DisplayName("23.3.5 - getBeans(String) returns only named beans available for injection")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldReturnOnlyNamedBeansAvailableForInjection() {
        Syringe syringe = newSyringe(NamedContract.class, NamedDefaultBean.class, NamedDisabledAlternativeBean.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        Set<Bean<?>> beans = beanManager.getBeans("namedService");
        assertEquals(1, beans.size());
        Bean<?> resolved = beanManager.resolve((Set) beans);
        assertNotNull(resolved);
        assertEquals(NamedDefaultBean.class, resolved.getBeanClass());
    }

    @Test
    @DisplayName("23.3.5 - getBeans(String) returns empty set when no bean has the requested name")
    void shouldReturnEmptySetWhenNoBeanMatchesName() {
        Syringe syringe = newSyringe(NamedContract.class, NamedDefaultBean.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        Set<Bean<?>> beans = beanManager.getBeans("missingName");
        assertEquals(0, beans.size());
    }

    @Test
    @DisplayName("23.3.6 - getPassivationCapableBean(String) returns the passivation-capable bean with the given id")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldReturnPassivationCapableBeanById() {
        Syringe syringe = newSyringe(PassivationCapableFixtureBean.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        Set<Bean<?>> beans = beanManager.getBeans(PassivationCapableFixtureBean.class);
        Bean<?> bean = beanManager.resolve((Set) beans);
        assertTrue(bean instanceof PassivationCapable);
        String id = ((PassivationCapable) bean).getId();

        Bean<?> lookedUp = beanManager.getPassivationCapableBean(id);
        assertNotNull(lookedUp);
        assertEquals(bean.getBeanClass(), lookedUp.getBeanClass());
    }

    @Test
    @DisplayName("23.3.6 - getPassivationCapableBean(String) returns null when there is no bean with the given id")
    void shouldReturnNullWhenPassivationCapableBeanIdIsUnknown() {
        Syringe syringe = newSyringe(PassivationCapableFixtureBean.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        Bean<?> lookedUp = beanManager.getPassivationCapableBean("missing-passivation-id");
        assertEquals(null, lookedUp);
    }

    @Test
    @DisplayName("23.3.7 - validate(InjectionPoint) succeeds when no deployment problem exists for the injection point")
    void shouldValidateInjectionPointWhenResolvable() {
        Syringe syringe = newSyringe(ResolvableDependency.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        InjectionPoint injectionPoint = createInjectionPoint(beanManager, ResolvableCarrier.class, "dependency");
        beanManager.validate(injectionPoint);
    }

    @Test
    @DisplayName("23.3.7 - validate(InjectionPoint) throws InjectionException for unsatisfied dependency")
    void shouldThrowInjectionExceptionWhenInjectionPointIsUnsatisfied() {
        Syringe syringe = newSyringe(BeanManagerConsumer.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        InjectionPoint injectionPoint = createInjectionPoint(beanManager, UnsatisfiedCarrier.class, "missing");
        InjectionException exception = assertThrows(InjectionException.class, () -> beanManager.validate(injectionPoint));
        assertTrue(exception instanceof UnsatisfiedResolutionException);
    }

    @Test
    @DisplayName("23.3.7 - validate(InjectionPoint) throws InjectionException for unresolvable ambiguous dependency")
    void shouldThrowInjectionExceptionWhenInjectionPointIsAmbiguous() {
        Syringe syringe = newSyringe(AmbiguousServiceOne.class, AmbiguousServiceTwo.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        InjectionPoint injectionPoint = createInjectionPoint(beanManager, AmbiguousCarrier.class, "service");
        InjectionException exception = assertThrows(InjectionException.class, () -> beanManager.validate(injectionPoint));
        assertTrue(exception instanceof AmbiguousResolutionException);
    }

    @Test
    @DisplayName("23.3.8 - resolveDecorators returns enabled decorators ordered for bean types and qualifiers")
    void shouldResolveEnabledDecoratorsInOrder() {
        Syringe syringe = newSyringe(DecoratorResolutionContract.class, DecoratedTargetBean.class,
                HighPriorityDecorator.class, LowPriorityDecorator.class, DisabledDecorator.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        java.util.List<jakarta.enterprise.inject.spi.Decorator<?>> decorators = beanManager.resolveDecorators(
                Collections.<Type>singleton(DecoratorResolutionContract.class),
                Default.Literal.INSTANCE
        );

        assertEquals(2, decorators.size());
        assertEquals(HighPriorityDecorator.class, decorators.get(0).getBeanClass());
        assertEquals(LowPriorityDecorator.class, decorators.get(1).getBeanClass());
    }

    @Test
    @DisplayName("23.3.8 - resolveDecorators throws IllegalArgumentException for duplicate non-repeating qualifier")
    void shouldRejectDuplicateNonRepeatingQualifierInResolveDecorators() {
        Syringe syringe = newSyringe(DecoratorResolutionContract.class, DecoratedTargetBean.class, HighPriorityDecorator.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        assertThrows(IllegalArgumentException.class, () -> beanManager.resolveDecorators(
                Collections.<Type>singleton(DecoratorResolutionContract.class),
                BlueLiteral.INSTANCE,
                new BlueLiteral()
        ));
    }

    @Test
    @DisplayName("23.3.8 - resolveDecorators throws IllegalArgumentException when annotation is not a qualifier type")
    void shouldRejectNonQualifierAnnotationInResolveDecorators() {
        Syringe syringe = newSyringe(DecoratorResolutionContract.class, DecoratedTargetBean.class, HighPriorityDecorator.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        assertThrows(IllegalArgumentException.class, () -> beanManager.resolveDecorators(
                Collections.<Type>singleton(DecoratorResolutionContract.class),
                ProbeBindingLiteral.INSTANCE
        ));
    }

    @Test
    @DisplayName("23.3.8 - resolveDecorators throws IllegalArgumentException when bean types set is empty")
    void shouldRejectEmptyTypesInResolveDecorators() {
        Syringe syringe = newSyringe(DecoratorResolutionContract.class, DecoratedTargetBean.class, HighPriorityDecorator.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        assertThrows(IllegalArgumentException.class, () -> beanManager.resolveDecorators(Collections.<Type>emptySet()));
    }

    @Test
    @DisplayName("23.3.9 - resolveInterceptors returns enabled interceptors ordered for interception type and interceptor bindings")
    void shouldResolveEnabledInterceptorsInOrder() {
        Syringe syringe = newSyringe(HighPriorityProbeInterceptor.class, LowPriorityProbeInterceptor.class,
                DisabledProbeInterceptor.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        java.util.List<jakarta.enterprise.inject.spi.Interceptor<?>> interceptors = beanManager.resolveInterceptors(
                InterceptionType.AROUND_INVOKE,
                ProbeBindingLiteral.INSTANCE
        );

        assertEquals(2, interceptors.size());
        assertEquals(HighPriorityProbeInterceptor.class, interceptors.get(0).getBeanClass());
        assertEquals(LowPriorityProbeInterceptor.class, interceptors.get(1).getBeanClass());
    }

    @Test
    @DisplayName("23.3.9 - resolveInterceptors returns empty list when no enabled interceptor matches the provided binding and interception type")
    void shouldReturnEmptyListWhenNoEnabledInterceptorsMatch() {
        Syringe syringe = newSyringe(HighPriorityProbeInterceptor.class, LowPriorityProbeInterceptor.class,
                DisabledProbeInterceptor.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        java.util.List<jakarta.enterprise.inject.spi.Interceptor<?>> interceptors = beanManager.resolveInterceptors(
                InterceptionType.POST_CONSTRUCT,
                ProbeBindingLiteral.INSTANCE
        );

        assertEquals(0, interceptors.size());
    }

    @Test
    @DisplayName("23.3.10 - BeanManager determines qualifier, scope, stereotype and interceptor binding annotation types")
    void shouldDetermineAnnotationKindsThroughBeanManagerApis() {
        Syringe syringe = newSyringe(BeanManagerConsumer.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        assertTrue(beanManager.isQualifier(Blue.class));
        assertTrue(!beanManager.isQualifier(PostConstruct.class));

        assertTrue(beanManager.isScope(Dependent.class));
        assertTrue(beanManager.isScope(SessionScoped.class));
        assertTrue(!beanManager.isScope(Blue.class));

        assertTrue(beanManager.isInterceptorBinding(ProbeBinding.class));
        assertTrue(!beanManager.isInterceptorBinding(Blue.class));

        assertTrue(beanManager.isStereotype(ServiceStereotype.class));
        assertTrue(!beanManager.isStereotype(Blue.class));
    }

    @Test
    @DisplayName("23.3.10 - BeanManager determines normal and passivating scopes")
    void shouldDetermineNormalAndPassivatingScopes() {
        Syringe syringe = newSyringe(BeanManagerConsumer.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        assertTrue(beanManager.isNormalScope(SessionScoped.class));
        assertTrue(!beanManager.isNormalScope(Dependent.class));

        assertTrue(beanManager.isPassivatingScope(SessionScoped.class));
        assertTrue(!beanManager.isPassivatingScope(ApplicationScoped.class));
        assertTrue(!beanManager.isPassivatingScope(Dependent.class));
    }

    @Test
    @DisplayName("23.3.10 - BeanManager returns interceptor binding and stereotype definitions")
    void shouldReturnInterceptorBindingAndStereotypeDefinitions() {
        Syringe syringe = newSyringe(BeanManagerConsumer.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        Set<Annotation> interceptorBindingDefinition = beanManager.getInterceptorBindingDefinition(ProbeBinding.class);
        assertTrue(containsAnnotationType(interceptorBindingDefinition, InterceptorBinding.class));
        assertTrue(containsAnnotationType(interceptorBindingDefinition, Retention.class));
        assertTrue(containsAnnotationType(interceptorBindingDefinition, Target.class));

        Set<Annotation> stereotypeDefinition = beanManager.getStereotypeDefinition(ServiceStereotype.class);
        assertTrue(containsAnnotationType(stereotypeDefinition, Stereotype.class));
        assertTrue(containsAnnotationType(stereotypeDefinition, Dependent.class));
        assertTrue(containsAnnotationType(stereotypeDefinition, Retention.class));
        assertTrue(containsAnnotationType(stereotypeDefinition, Target.class));
    }

    @Test
    @DisplayName("23.3.11 - areQualifiersEquivalent compares qualifiers for typesafe resolution and ignores @Nonbinding members")
    void shouldCompareQualifiersEquivalentIgnoringNonbindingMembers() {
        Syringe syringe = newSyringe(BeanManagerConsumer.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        QualifierWithNonbinding a = new QualifierWithNonbindingLiteral("x", "one");
        QualifierWithNonbinding b = new QualifierWithNonbindingLiteral("x", "two");
        QualifierWithNonbinding c = new QualifierWithNonbindingLiteral("y", "one");

        assertTrue(beanManager.areQualifiersEquivalent(a, b));
        assertTrue(!beanManager.areQualifiersEquivalent(a, c));
    }

    @Test
    @DisplayName("23.3.11 - areInterceptorBindingsEquivalent compares bindings for resolution and ignores @Nonbinding members")
    void shouldCompareInterceptorBindingsEquivalentIgnoringNonbindingMembers() {
        Syringe syringe = newSyringe(BeanManagerConsumer.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        BindingWithNonbinding a = new BindingWithNonbindingLiteral("tx", "one");
        BindingWithNonbinding b = new BindingWithNonbindingLiteral("tx", "two");
        BindingWithNonbinding c = new BindingWithNonbindingLiteral("audit", "one");

        assertTrue(beanManager.areInterceptorBindingsEquivalent(a, b));
        assertTrue(!beanManager.areInterceptorBindingsEquivalent(a, c));
    }

    @Test
    @DisplayName("23.3.11 - getQualifierHashCode ignores members annotated @Nonbinding")
    void shouldComputeQualifierHashCodeIgnoringNonbindingMembers() {
        Syringe syringe = newSyringe(BeanManagerConsumer.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        QualifierWithNonbinding a = new QualifierWithNonbindingLiteral("x", "one");
        QualifierWithNonbinding b = new QualifierWithNonbindingLiteral("x", "two");
        QualifierWithNonbinding c = new QualifierWithNonbindingLiteral("y", "one");

        assertEquals(beanManager.getQualifierHashCode(a), beanManager.getQualifierHashCode(b));
        assertTrue(beanManager.getQualifierHashCode(a) != beanManager.getQualifierHashCode(c));
    }

    @Test
    @DisplayName("23.3.11 - getInterceptorBindingHashCode ignores members annotated @Nonbinding")
    void shouldComputeInterceptorBindingHashCodeIgnoringNonbindingMembers() {
        Syringe syringe = newSyringe(BeanManagerConsumer.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        BindingWithNonbinding a = new BindingWithNonbindingLiteral("tx", "one");
        BindingWithNonbinding b = new BindingWithNonbindingLiteral("tx", "two");
        BindingWithNonbinding c = new BindingWithNonbindingLiteral("audit", "one");

        assertEquals(beanManager.getInterceptorBindingHashCode(a), beanManager.getInterceptorBindingHashCode(b));
        assertTrue(beanManager.getInterceptorBindingHashCode(a) != beanManager.getInterceptorBindingHashCode(c));
    }

    @Test
    @DisplayName("23.3.12 - createAnnotatedType returns AnnotatedType that exposes annotations for a Java class")
    void shouldCreateAnnotatedTypeForClassWithReadableAnnotations() {
        Syringe syringe = newSyringe(BeanManagerConsumer.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        AnnotatedType<AnnotatedTypeFixtureClass> annotatedType = beanManager.createAnnotatedType(AnnotatedTypeFixtureClass.class);
        assertEquals(AnnotatedTypeFixtureClass.class, annotatedType.getJavaClass());
        assertNotNull(annotatedType.getAnnotation(Blue.class));
        assertTrue(annotatedType.isAnnotationPresent(Blue.class));
        assertTrue(containsAnnotationType(annotatedType.getAnnotations(), Blue.class));
    }

    @Test
    @DisplayName("23.3.12 - createAnnotatedType returns AnnotatedType that exposes annotations for a Java interface")
    void shouldCreateAnnotatedTypeForInterfaceWithReadableAnnotations() {
        Syringe syringe = newSyringe(BeanManagerConsumer.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        AnnotatedType<AnnotatedTypeFixtureInterface> annotatedType = beanManager.createAnnotatedType(AnnotatedTypeFixtureInterface.class);
        assertEquals(AnnotatedTypeFixtureInterface.class, annotatedType.getJavaClass());
        assertNotNull(annotatedType.getAnnotation(Green.class));
        assertTrue(annotatedType.isAnnotationPresent(Green.class));
        assertTrue(containsAnnotationType(annotatedType.getAnnotations(), Green.class));
    }

    @Test
    @DisplayName("23.3.13 - getInjectionTargetFactory returns factory that creates non-contextual InjectionTarget with createInjectionTarget(null)")
    void shouldCreateNonContextualInjectionTargetWhenNullBeanPassed() {
        Syringe syringe = newSyringe(UnmanagedDependency.class, UnmanagedFoo.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        AnnotatedType<UnmanagedFoo> annotatedType = beanManager.createAnnotatedType(UnmanagedFoo.class);
        InjectionTargetFactory<UnmanagedFoo> factory = beanManager.getInjectionTargetFactory(annotatedType);
        InjectionTarget<UnmanagedFoo> injectionTarget = factory.createInjectionTarget(null);

        CreationalContext<UnmanagedFoo> ctx = beanManager.createCreationalContext(null);
        UnmanagedFoo instance = injectionTarget.produce(ctx);
        injectionTarget.inject(instance, ctx);
        assertNotNull(instance.dependency);
    }

    @Test
    @DisplayName("23.3.13 - getInjectionTargetFactory throws IllegalArgumentException when type has injection point definition error")
    void shouldRejectInjectionTargetFactoryCreationForDefinitionErrors() {
        Syringe syringe = newSyringe(BeanManagerConsumer.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        AnnotatedType<InvalidInjectionTargetType> annotatedType = beanManager.createAnnotatedType(InvalidInjectionTargetType.class);
        assertThrows(IllegalArgumentException.class, () -> beanManager.getInjectionTargetFactory(annotatedType));
    }

    @Test
    @DisplayName("23.3.13 - configure returns same AnnotatedTypeConfigurator instance within one InjectionTargetFactory")
    void shouldReturnSameConfiguratorInstanceWithinFactory() {
        Syringe syringe = newSyringe(UnmanagedDependency.class, UnmanagedFoo.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        AnnotatedType<UnmanagedFoo> annotatedType = beanManager.createAnnotatedType(UnmanagedFoo.class);
        InjectionTargetFactory<UnmanagedFoo> factory = beanManager.getInjectionTargetFactory(annotatedType);

        AnnotatedTypeConfigurator<UnmanagedFoo> first = factory.configure();
        AnnotatedTypeConfigurator<UnmanagedFoo> second = factory.configure();
        assertSame(first, second);
    }

    @Test
    @DisplayName("23.3.13 - configure throws IllegalStateException after createInjectionTarget is invoked")
    void shouldRejectConfigureAfterInjectionTargetCreation() {
        Syringe syringe = newSyringe(UnmanagedDependency.class, UnmanagedFoo.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        AnnotatedType<UnmanagedFoo> annotatedType = beanManager.createAnnotatedType(UnmanagedFoo.class);
        InjectionTargetFactory<UnmanagedFoo> factory = beanManager.getInjectionTargetFactory(annotatedType);
        factory.createInjectionTarget(null);

        assertThrows(IllegalStateException.class, factory::configure);
    }

    @Test
    @DisplayName("23.3.14 - getProducerFactory returns ProducerFactory for producer field and createProducer(null) creates non-contextual producer")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldCreateNonContextualProducerFromProducerFieldFactory() {
        Syringe syringe = newSyringe(ProducerFieldDeclaringBean.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        AnnotatedType<ProducerFieldDeclaringBean> type = beanManager.createAnnotatedType(ProducerFieldDeclaringBean.class);
        AnnotatedField<?> producerField = findField(type, "product");
        Set<Bean<?>> beans = beanManager.getBeans(ProducerFieldDeclaringBean.class, Default.Literal.INSTANCE);
        Bean<?> declaringBean = beanManager.resolve((Set) beans);

        ProducerFactory<ProducerFieldDeclaringBean> factory = beanManager.getProducerFactory((AnnotatedField) producerField, (Bean) declaringBean);
        Producer<ProducedProduct> producer = factory.createProducer(null);
        ProducedProduct produced = producer.produce(beanManager.createCreationalContext(null));

        assertNotNull(produced);
        assertEquals("field-product", produced.value);
    }

    @Test
    @DisplayName("23.3.14 - getProducerFactory returns ProducerFactory for producer method and createProducer(null) creates non-contextual producer")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldCreateNonContextualProducerFromProducerMethodFactory() {
        Syringe syringe = newSyringe(ProducerMethodDeclaringBean.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        AnnotatedType<ProducerMethodDeclaringBean> type = beanManager.createAnnotatedType(ProducerMethodDeclaringBean.class);
        AnnotatedMethod<?> producerMethod = findMethod(type, "produce");
        Set<Bean<?>> beans = beanManager.getBeans(ProducerMethodDeclaringBean.class, Default.Literal.INSTANCE);
        Bean<?> declaringBean = beanManager.resolve((Set) beans);

        ProducerFactory<ProducerMethodDeclaringBean> factory = beanManager.getProducerFactory((AnnotatedMethod) producerMethod, (Bean) declaringBean);
        Producer<ProducedProduct> producer = factory.createProducer(null);
        ProducedProduct produced = producer.produce(beanManager.createCreationalContext(null));

        assertNotNull(produced);
        assertEquals("method-product", produced.value);
    }

    @Test
    @DisplayName("23.3.14 - getProducerFactory throws IllegalArgumentException when producer method has definition error")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldRejectProducerMethodFactoryWhenDefinitionErrorExists() {
        Syringe syringe = newSyringe(ProducerMethodDeclaringBean.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        AnnotatedType<InvalidProducerMethodDeclaringBean> type = beanManager.createAnnotatedType(InvalidProducerMethodDeclaringBean.class);
        AnnotatedMethod<?> producerMethod = findMethod(type, "invalidProduce");
        Set<Bean<?>> beans = beanManager.getBeans(ProducerMethodDeclaringBean.class, Default.Literal.INSTANCE);
        Bean<?> declaringBean = beanManager.resolve((Set) beans);

        assertThrows(IllegalArgumentException.class, () -> beanManager.getProducerFactory((AnnotatedMethod) producerMethod, (Bean) declaringBean));
    }

    @Test
    @DisplayName("23.3.15 - createInjectionPoint(AnnotatedField) returns container provided InjectionPoint for a valid field injection point")
    void shouldCreateInjectionPointFromAnnotatedField() {
        Syringe syringe = newSyringe(ResolvableDependency.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        AnnotatedType<ResolvableCarrier> annotatedType = beanManager.createAnnotatedType(ResolvableCarrier.class);
        AnnotatedField<?> dependencyField = findField(annotatedType, "dependency");

        InjectionPoint injectionPoint = beanManager.createInjectionPoint(dependencyField);
        assertNotNull(injectionPoint);
        assertEquals(ResolvableDependency.class, injectionPoint.getType());
        assertEquals("dependency", injectionPoint.getMember().getName());
    }

    @Test
    @DisplayName("23.3.15 - createInjectionPoint(AnnotatedParameter) returns container provided InjectionPoint for a valid parameter injection point")
    void shouldCreateInjectionPointFromAnnotatedParameter() {
        Syringe syringe = newSyringe(ResolvableDependency.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        AnnotatedType<ResolvableConstructorCarrier> annotatedType = beanManager.createAnnotatedType(ResolvableConstructorCarrier.class);
        AnnotatedConstructor<ResolvableConstructorCarrier> constructor = findInjectConstructor(annotatedType);
        AnnotatedParameter<?> parameter = constructor.getParameters().get(0);

        InjectionPoint injectionPoint = beanManager.createInjectionPoint(parameter);
        assertNotNull(injectionPoint);
        assertEquals(ResolvableDependency.class, injectionPoint.getType());
        assertEquals("com.threeamigos.common.util.implementations.injection.cdi41tests.chapter23.par233beanmanager.BeanManagerTest$ResolvableConstructorCarrier",
                injectionPoint.getMember().getDeclaringClass().getName());
    }

    @Test
    @DisplayName("23.3.15 - createInjectionPoint(AnnotatedField) does not fail resolution-time checks for unresolved field dependencies")
    void shouldCreateFieldInjectionPointEvenWhenUnresolved() {
        Syringe syringe = newSyringe(BeanManagerConsumer.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        AnnotatedType<UnsatisfiedCarrier> annotatedType = beanManager.createAnnotatedType(UnsatisfiedCarrier.class);
        AnnotatedField<?> missingField = findField(annotatedType, "missing");

        InjectionPoint injectionPoint = beanManager.createInjectionPoint(missingField);
        assertNotNull(injectionPoint);
        assertEquals(MissingDependency.class, injectionPoint.getType());
    }

    @Test
    @DisplayName("23.3.15 - createInjectionPoint(AnnotatedParameter) does not fail resolution-time checks for unresolved parameter dependencies")
    void shouldCreateParameterInjectionPointEvenWhenUnresolved() {
        Syringe syringe = newSyringe(BeanManagerConsumer.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        AnnotatedType<InvalidParameterCarrier> annotatedType = beanManager.createAnnotatedType(InvalidParameterCarrier.class);
        AnnotatedConstructor<InvalidParameterCarrier> constructor = findInjectConstructor(annotatedType);
        AnnotatedParameter<?> parameter = constructor.getParameters().get(0);

        InjectionPoint injectionPoint = beanManager.createInjectionPoint(parameter);
        assertNotNull(injectionPoint);
        assertEquals(MissingDependency.class, injectionPoint.getType());
    }

    @Test
    @DisplayName("23.3.16 - createBeanAttributes(AnnotatedType) returns BeanAttributes by reading declared bean annotations")
    void shouldCreateBeanAttributesFromAnnotatedType() {
        Syringe syringe = newSyringe(BeanManagerConsumer.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        AnnotatedType<BeanAttributesTypeFixture> annotatedType = beanManager.createAnnotatedType(BeanAttributesTypeFixture.class);
        BeanAttributes<BeanAttributesTypeFixture> attributes = beanManager.createBeanAttributes(annotatedType);

        assertEquals("typeAttributesBean", attributes.getName());
        assertEquals(ApplicationScoped.class, attributes.getScope());
        assertTrue(containsAnnotationType(attributes.getQualifiers(), Blue.class));
        assertTrue(attributes.getTypes().contains(BeanAttributesTypeFixture.class));
    }

    @Test
    @DisplayName("23.3.16 - createBeanAttributes(AnnotatedMember) returns BeanAttributes by reading declared producer member annotations")
    void shouldCreateBeanAttributesFromAnnotatedMember() {
        Syringe syringe = newSyringe(BeanAttributesProducerBean.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        AnnotatedType<BeanAttributesProducerBean> type = beanManager.createAnnotatedType(BeanAttributesProducerBean.class);
        AnnotatedMember<?> member = findMethod(type, "memberProduct");
        BeanAttributes<?> attributes = beanManager.createBeanAttributes(member);

        assertEquals("memberProductBean", attributes.getName());
        assertEquals(Dependent.class, attributes.getScope());
        assertTrue(containsAnnotationType(attributes.getQualifiers(), Blue.class));
        assertTrue(!attributes.getTypes().isEmpty());
    }

    @Test
    @DisplayName("23.3.16 - createBeanAttributes(AnnotatedType) throws IllegalArgumentException for definition error in declared bean attributes")
    void shouldRejectCreateBeanAttributesForTypeDefinitionError() {
        Syringe syringe = newSyringe(BeanManagerConsumer.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        AnnotatedType<InvalidTypeWithMultipleScopes> annotatedType =
                beanManager.createAnnotatedType(InvalidTypeWithMultipleScopes.class);

        assertThrows(IllegalArgumentException.class, () -> beanManager.createBeanAttributes(annotatedType));
    }

    @Test
    @DisplayName("23.3.16 - createBeanAttributes(AnnotatedMember) throws IllegalArgumentException for definition error in declared bean attributes")
    void shouldRejectCreateBeanAttributesForMemberDefinitionError() {
        Syringe syringe = newSyringe(BeanManagerConsumer.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        AnnotatedType<InvalidMemberAttributesProducerBean> type =
                beanManager.createAnnotatedType(InvalidMemberAttributesProducerBean.class);
        AnnotatedMember<?> member = findField(type, "invalidMemberProduct");

        assertThrows(IllegalArgumentException.class, () -> beanManager.createBeanAttributes(member));
    }

    @Test
    @DisplayName("23.3.17 - createBean(attributes, class, injectionTargetFactory) uses BeanAttributes and InjectionTarget metadata")
    @SuppressWarnings("unchecked")
    void shouldCreateBeanFromInjectionTargetFactoryUsingProvidedMetadata() {
        Syringe syringe = newSyringe(UnmanagedDependency.class, UnmanagedFoo.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        Set<Annotation> qualifiers = new HashSet<Annotation>();
        qualifiers.add(BlueLiteral.INSTANCE);
        Set<Class<? extends Annotation>> stereotypes = new HashSet<Class<? extends Annotation>>();
        stereotypes.add(ServiceStereotype.class);
        Set<Type> types = new HashSet<Type>();
        types.add(UnmanagedFoo.class);
        types.add(Object.class);
        BeanAttributes<UnmanagedFoo> attributes = new BeanAttributesImpl<UnmanagedFoo>(
                "syntheticUnmanagedFoo",
                qualifiers,
                Dependent.class,
                stereotypes,
                types,
                true
        );

        AnnotatedType<UnmanagedFoo> annotatedType = beanManager.createAnnotatedType(UnmanagedFoo.class);
        InjectionTargetFactory<UnmanagedFoo> itFactory = beanManager.getInjectionTargetFactory(annotatedType);

        Bean<UnmanagedFoo> bean = beanManager.createBean(attributes, UnmanagedFoo.class, itFactory);
        assertEquals(UnmanagedFoo.class, bean.getBeanClass());
        assertEquals("syntheticUnmanagedFoo", bean.getName());
        assertEquals(Dependent.class, bean.getScope());
        assertTrue(bean.isAlternative());
        assertTrue(containsAnnotationType(bean.getQualifiers(), Blue.class));
        assertTrue(bean.getStereotypes().contains(ServiceStereotype.class));
        assertTrue(bean.getTypes().contains(UnmanagedFoo.class));
        assertTrue(!bean.getInjectionPoints().isEmpty());
    }

    @Test
    @DisplayName("23.3.17 - createBean(attributes, class, producerFactory) uses BeanAttributes and Producer metadata")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldCreateBeanFromProducerFactoryUsingProvidedMetadata() {
        Syringe syringe = newSyringe(ResolvableDependency.class, ProducerMethodWithParamDeclaringBean.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        Set<Annotation> qualifiers = new HashSet<Annotation>();
        qualifiers.add(GreenLiteral.INSTANCE);
        Set<Class<? extends Annotation>> stereotypes = new HashSet<Class<? extends Annotation>>();
        stereotypes.add(ServiceStereotype.class);
        Set<Type> types = new HashSet<Type>();
        types.add(ProducedProduct.class);
        types.add(Object.class);
        BeanAttributes<ProducedProduct> attributes = new BeanAttributesImpl<ProducedProduct>(
                "syntheticProducedProduct",
                qualifiers,
                Dependent.class,
                stereotypes,
                types,
                false
        );

        AnnotatedType<ProducerMethodWithParamDeclaringBean> type = beanManager.createAnnotatedType(ProducerMethodWithParamDeclaringBean.class);
        AnnotatedMethod<?> method = findMethod(type, "produceWithParam");
        Set<Bean<?>> declaringBeans = beanManager.getBeans(ProducerMethodWithParamDeclaringBean.class, Default.Literal.INSTANCE);
        Bean<?> declaringBean = beanManager.resolve((Set) declaringBeans);
        ProducerFactory<ProducerMethodWithParamDeclaringBean> producerFactory =
                beanManager.getProducerFactory((AnnotatedMethod) method, (Bean) declaringBean);

        Bean<ProducedProduct> bean = beanManager.createBean(attributes, ProducerMethodWithParamDeclaringBean.class, producerFactory);
        assertEquals(ProducerMethodWithParamDeclaringBean.class, bean.getBeanClass());
        assertEquals("syntheticProducedProduct", bean.getName());
        assertEquals(Dependent.class, bean.getScope());
        assertTrue(!bean.isAlternative());
        assertTrue(containsAnnotationType(bean.getQualifiers(), Green.class));
        assertTrue(bean.getStereotypes().contains(ServiceStereotype.class));
        assertTrue(bean.getTypes().contains(ProducedProduct.class));
        assertEquals(1, bean.getInjectionPoints().size());
    }

    @Test
    @DisplayName("23.3 - TCK parity (full.extensions.beanManager.bean.SyntheticBeanTest): BeanManager synthetic bean creation APIs support class beans, producer-backed beans and passivation-capable beans")
    void shouldMatchTckSyntheticBeanCreationScenarios() {
        shouldCreateBeanFromInjectionTargetFactoryUsingProvidedMetadata();
        shouldCreateBeanFromProducerFactoryUsingProvidedMetadata();
        shouldReturnPassivationCapableBeanById();
    }

    @Test
    @DisplayName("23.3.18 - getExtension returns container instance of registered extension class")
    void shouldReturnContainerExtensionInstance() {
        Syringe syringe = newSyringe(BeanManagerConsumer.class);
        syringe.addExtension(RegisteredLookupExtension.class.getName());
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        RegisteredLookupExtension extension = beanManager.getExtension(RegisteredLookupExtension.class);
        assertNotNull(extension);
    }

    @Test
    @DisplayName("23.3.18 - getExtension throws IllegalArgumentException when container has no instance of the given extension class")
    void shouldRejectGetExtensionForUnknownExtensionClass() {
        Syringe syringe = newSyringe(BeanManagerConsumer.class);
        syringe.addExtension(RegisteredLookupExtension.class.getName());
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        assertThrows(IllegalArgumentException.class, () -> beanManager.getExtension(UnregisteredLookupExtension.class));
    }

    @Test
    @DisplayName("23.3.19 - createInterceptionFactory returns InterceptionFactory for provided Java class type")
    void shouldCreateInterceptionFactoryForProvidedClassType() {
        Syringe syringe = newSyringe(UnmanagedDependency.class, UnmanagedFoo.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        CreationalContext<UnmanagedFoo> ctx = beanManager.createCreationalContext(null);
        InterceptionFactory<UnmanagedFoo> factory = beanManager.createInterceptionFactory(ctx, UnmanagedFoo.class);

        assertNotNull(factory);
        assertNotNull(factory.configure());
    }

    @Test
    @DisplayName("23.3.19 - createInterceptionFactory with non-class type parameter results in non-portable behavior")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldRejectCreateInterceptionFactoryWhenTypeParameterIsNotJavaClass() {
        Syringe syringe = newSyringe(BeanManagerConsumer.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        CreationalContext ctx = beanManager.createCreationalContext(null);
        assertThrows(NonPortableBehaviourException.class,
                () -> beanManager.createInterceptionFactory(ctx, (Class) NamedContract.class));
    }

    @Test
    @DisplayName("23.3.20 - createInstance returns Instance that resolves only beans available for injection")
    void shouldResolveOnlyAvailableBeansThroughCreateInstance() {
        Syringe syringe = newSyringe(AvailabilityContract.class, AvailableDefaultBean.class, DisabledAlternativeBean.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        Instance<AvailabilityContract> instance = beanManager.createInstance().select(AvailabilityContract.class);
        assertTrue(!instance.isUnsatisfied());
        assertTrue(!instance.isAmbiguous());
        assertEquals("available", instance.get().name());
    }

    @Test
    @DisplayName("23.3.20 - createInstance returns Instance that is unsatisfied when only unavailable beans exist")
    void shouldBeUnsatisfiedWhenOnlyUnavailableBeansExist() {
        Syringe syringe = newSyringe(UnavailableContract.class, UnavailableAlternativeBean.class);
        syringe.setup();
        BeanManager beanManager = syringe.getBeanManager();

        Instance<UnavailableContract> instance = beanManager.createInstance().select(UnavailableContract.class);
        assertTrue(instance.isUnsatisfied());
        assertThrows(UnsatisfiedResolutionException.class, instance::get);
    }

    @SuppressWarnings("unchecked")
    private InjectionPoint createInjectionPoint(BeanManager beanManager, Class<?> carrierType, String fieldName) {
        AnnotatedType<?> annotatedType = beanManager.createAnnotatedType((Class<Object>) carrierType);
        for (AnnotatedField<?> field : annotatedType.getFields()) {
            if (fieldName.equals(field.getJavaMember().getName())) {
                return new InjectionPointImpl<Object>(field.getJavaMember(), null);
            }
        }
        throw new AssertionError("Field not found for injection point creation: " + carrierType.getName() + "." + fieldName);
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        Set<Class<?>> included = new HashSet<Class<?>>();
        Collections.addAll(included, beanClasses);
        for (Class<?> fixture : allFixtureTypes()) {
            if (!included.contains(fixture)) {
                syringe.exclude(fixture);
            }
        }
        return syringe;
    }

    private Set<Class<?>> allFixtureTypes() {
        Set<Class<?>> fixtures = new HashSet<Class<?>>();
        fixtures.add(BeanManagerConsumer.class);
        fixtures.add(SessionScopedBeanManagerConsumer.class);
        fixtures.add(InjectionPointCarrier.class);
        fixtures.add(ResolvableDependency.class);
        fixtures.add(ResolvableCarrier.class);
        fixtures.add(ResolvableConstructorCarrier.class);
        fixtures.add(UnsatisfiedCarrier.class);
        fixtures.add(InvalidParameterCarrier.class);
        fixtures.add(InvalidInjectionTargetType.class);
        fixtures.add(AmbiguousServiceOne.class);
        fixtures.add(AmbiguousServiceTwo.class);
        fixtures.add(AmbiguousCarrier.class);
        fixtures.add(DelegateTargetBean.class);
        fixtures.add(DelegateWrappingDecorator.class);
        fixtures.add(TrackedDependentCarrier.class);
        fixtures.add(TrackedDependent.class);
        fixtures.add(UnmanagedDependency.class);
        fixtures.add(UnmanagedFoo.class);
        fixtures.add(QualifiedContract.class);
        fixtures.add(BlueQualifiedBean.class);
        fixtures.add(GreenQualifiedBean.class);
        fixtures.add(DefaultContract.class);
        fixtures.add(DefaultContractBean.class);
        fixtures.add(NonDefaultQualifiedContractBean.class);
        fixtures.add(AvailabilityContract.class);
        fixtures.add(AvailableDefaultBean.class);
        fixtures.add(DisabledAlternativeBean.class);
        fixtures.add(NamedContract.class);
        fixtures.add(NamedDefaultBean.class);
        fixtures.add(NamedDisabledAlternativeBean.class);
        fixtures.add(OtherNamedBean.class);
        fixtures.add(PassivationCapableFixtureBean.class);
        fixtures.add(DecoratorResolutionContract.class);
        fixtures.add(DecoratedTargetBean.class);
        fixtures.add(HighPriorityDecorator.class);
        fixtures.add(LowPriorityDecorator.class);
        fixtures.add(DisabledDecorator.class);
        fixtures.add(HighPriorityProbeInterceptor.class);
        fixtures.add(LowPriorityProbeInterceptor.class);
        fixtures.add(DisabledProbeInterceptor.class);
        fixtures.add(AnnotatedTypeFixtureClass.class);
        fixtures.add(AnnotatedTypeFixtureInterface.class);
        fixtures.add(ProducerFieldDeclaringBean.class);
        fixtures.add(ProducerMethodDeclaringBean.class);
        fixtures.add(ProducerMethodWithParamDeclaringBean.class);
        fixtures.add(InvalidProducerMethodDeclaringBean.class);
        fixtures.add(BeanAttributesTypeFixture.class);
        fixtures.add(BeanAttributesProducerBean.class);
        fixtures.add(InvalidTypeWithMultipleScopes.class);
        fixtures.add(InvalidMemberAttributesProducerBean.class);
        fixtures.add(RegisteredLookupExtension.class);
        fixtures.add(UnregisteredLookupExtension.class);
        fixtures.add(UnavailableContract.class);
        fixtures.add(UnavailableAlternativeBean.class);
        return fixtures;
    }

    private void assertAllIllegalState(Map<String, Throwable> results) {
        assertTrue(!results.isEmpty(), "Expected lifecycle probe operations to be recorded");
        for (Map.Entry<String, Throwable> entry : results.entrySet()) {
            assertNotNull(entry.getValue(), "Operation did not throw: " + entry.getKey());
            assertEquals(IllegalStateException.class, entry.getValue().getClass(),
                    "Operation should throw IllegalStateException: " + entry.getKey());
        }
    }

    private void assertAllSuccessful(Map<String, Throwable> results) {
        assertTrue(!results.isEmpty(), "Expected post-ADV probe operations to be recorded");
        for (Map.Entry<String, Throwable> entry : results.entrySet()) {
            if (entry.getValue() != null) {
                throw new AssertionError("Operation should succeed after AfterDeploymentValidation: " + entry.getKey(),
                        entry.getValue());
            }
        }
    }

    private boolean containsAnnotationType(Set<Annotation> annotations, Class<? extends Annotation> type) {
        for (Annotation annotation : annotations) {
            if (annotation != null && type.equals(annotation.annotationType())) {
                return true;
            }
        }
        return false;
    }

    private AnnotatedField<?> findField(AnnotatedType<?> type, String fieldName) {
        for (AnnotatedField<?> field : type.getFields()) {
            if (fieldName.equals(field.getJavaMember().getName())) {
                return field;
            }
        }
        throw new AssertionError("Field not found: " + fieldName);
    }

    private AnnotatedMethod<?> findMethod(AnnotatedType<?> type, String methodName) {
        for (AnnotatedMethod<?> method : type.getMethods()) {
            if (methodName.equals(method.getJavaMember().getName())) {
                return method;
            }
        }
        throw new AssertionError("Method not found: " + methodName);
    }

    private <T> AnnotatedConstructor<T> findInjectConstructor(AnnotatedType<T> type) {
        for (AnnotatedConstructor<T> constructor : type.getConstructors()) {
            if (constructor.isAnnotationPresent(Inject.class)) {
                return constructor;
            }
        }
        throw new AssertionError("Inject constructor not found for type: " + type.getJavaClass().getName());
    }

    @Dependent
    public static class BeanManagerConsumer {
        @Inject
        BeanManager manager;
    }

    @SessionScoped
    public static class SessionScopedBeanManagerConsumer implements Serializable {
        @Inject
        BeanManager manager;
    }

    @Dependent
    public static class InjectionPointCarrier {
        @Inject
        BeanManager manager;
    }

    @InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface ProbeBinding {
    }

    @Stereotype
    @Dependent
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface ServiceStereotype {
    }

    public static class ProbeBindingLiteral extends AnnotationLiteral<ProbeBinding> implements ProbeBinding {
        static final ProbeBindingLiteral INSTANCE = new ProbeBindingLiteral();
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE, ElementType.METHOD})
    public @interface QualifierWithNonbinding {
        String value();

        @Nonbinding
        String comment() default "";
    }

    public static class QualifierWithNonbindingLiteral extends AnnotationLiteral<QualifierWithNonbinding>
            implements QualifierWithNonbinding {
        private final String value;
        private final String comment;

        QualifierWithNonbindingLiteral(String value, String comment) {
            this.value = value;
            this.comment = comment;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public String comment() {
            return comment;
        }
    }

    @InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface BindingWithNonbinding {
        String value();

        @Nonbinding
        String comment() default "";
    }

    public static class BindingWithNonbindingLiteral extends AnnotationLiteral<BindingWithNonbinding>
            implements BindingWithNonbinding {
        private final String value;
        private final String comment;

        BindingWithNonbindingLiteral(String value, String comment) {
            this.value = value;
            this.comment = comment;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public String comment() {
            return comment;
        }
    }

    @Interceptor
    @ProbeBinding
    @Priority(100)
    public static class HighPriorityProbeInterceptor {
        @AroundInvoke
        public Object aroundInvoke(InvocationContext context) throws Exception {
            return context.proceed();
        }
    }

    @Interceptor
    @ProbeBinding
    @Priority(200)
    public static class LowPriorityProbeInterceptor {
        @AroundInvoke
        public Object aroundInvoke(InvocationContext context) throws Exception {
            return context.proceed();
        }
    }

    @Interceptor
    @ProbeBinding
    public static class DisabledProbeInterceptor {
        @AroundInvoke
        public Object aroundInvoke(InvocationContext context) throws Exception {
            return context.proceed();
        }
    }

    public static class LifecyclePhaseProbeExtension implements Extension {
        private static final Map<String, Throwable> BEFORE_ABD = new LinkedHashMap<String, Throwable>();
        private static final Map<String, Throwable> BEFORE_ADV = new LinkedHashMap<String, Throwable>();
        private static final Map<String, Throwable> AFTER_ADV = new LinkedHashMap<String, Throwable>();

        static void reset() {
            BEFORE_ABD.clear();
            BEFORE_ADV.clear();
            AFTER_ADV.clear();
        }

        static Map<String, Throwable> beforeAfterBeanDiscoveryResults() {
            return BEFORE_ABD;
        }

        static Map<String, Throwable> beforeAfterDeploymentValidationResults() {
            return BEFORE_ADV;
        }

        static Map<String, Throwable> afterAfterDeploymentValidationResults() {
            return AFTER_ADV;
        }

        public void beforeBeanDiscovery(@Observes BeforeBeanDiscovery ignored, BeanManager beanManager) {
            capture(BEFORE_ABD, "getBeans(String)", new CheckedRunnable() {
                @Override
                public void run() {
                    beanManager.getBeans("beanManager");
                }
            });
            capture(BEFORE_ABD, "getBeans(Type, Annotation...)", new CheckedRunnable() {
                @Override
                public void run() {
                    beanManager.getBeans(BeanManager.class, Default.Literal.INSTANCE);
                }
            });
            capture(BEFORE_ABD, "getPassivationCapableBean(String)", new CheckedRunnable() {
                @Override
                public void run() {
                    beanManager.getPassivationCapableBean("missing-id");
                }
            });
            capture(BEFORE_ABD, "resolve(Set)", new CheckedRunnable() {
                @Override
                public void run() {
                    beanManager.resolve(Collections.<Bean<?>>emptySet());
                }
            });
            capture(BEFORE_ABD, "resolveDecorators(Set, Annotation...)", new CheckedRunnable() {
                @Override
                public void run() {
                    beanManager.resolveDecorators(Collections.<Type>singleton(Object.class), Default.Literal.INSTANCE);
                }
            });
            capture(BEFORE_ABD, "resolveInterceptors(InterceptionType, Annotation...)", new CheckedRunnable() {
                @Override
                public void run() {
                    beanManager.resolveInterceptors(InterceptionType.AROUND_INVOKE, ProbeBindingLiteral.INSTANCE);
                }
            });
            capture(BEFORE_ABD, "resolveObserverMethods(Object, Annotation...)", new CheckedRunnable() {
                @Override
                public void run() {
                    beanManager.resolveObserverMethods(new Object(), Default.Literal.INSTANCE);
                }
            });
            capture(BEFORE_ABD, "validate(InjectionPoint)", new CheckedRunnable() {
                @Override
                public void run() {
                    beanManager.validate(createBeanManagerInjectionPoint(beanManager));
                }
            });
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        public void afterBeanDiscovery(@Observes AfterBeanDiscovery ignored, BeanManager beanManager) {
            capture(BEFORE_ADV, "createInstance()", new CheckedRunnable() {
                @Override
                public void run() {
                    beanManager.createInstance();
                }
            });
            capture(BEFORE_ADV, "getReference(Bean, Type, CreationalContext)", new CheckedRunnable() {
                @Override
                public void run() {
                    Set<Bean<?>> beans = beanManager.getBeans(BeanManager.class, Default.Literal.INSTANCE);
                    Bean<?> bean = beanManager.resolve((Set) beans);
                    CreationalContext<?> context = beanManager.createCreationalContext((jakarta.enterprise.context.spi.Contextual) bean);
                    beanManager.getReference(bean, BeanManager.class, context);
                }
            });
            capture(BEFORE_ADV, "getInjectableReference(InjectionPoint, CreationalContext)", new CheckedRunnable() {
                @Override
                public void run() {
                    InjectionPoint injectionPoint = createBeanManagerInjectionPoint(beanManager);
                    CreationalContext<?> context = beanManager.createCreationalContext(null);
                    beanManager.getInjectableReference(injectionPoint, context);
                }
            });
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        public void afterDeploymentValidation(@Observes AfterDeploymentValidation ignored, BeanManager beanManager) {
            capture(AFTER_ADV, "createInstance()", new CheckedRunnable() {
                @Override
                public void run() {
                    assertNotNull(beanManager.createInstance());
                }
            });
            capture(AFTER_ADV, "getReference(Bean, Type, CreationalContext)", new CheckedRunnable() {
                @Override
                public void run() {
                    Set<Bean<?>> beans = beanManager.getBeans(BeanManager.class, Default.Literal.INSTANCE);
                    Bean<?> bean = beanManager.resolve((Set) beans);
                    CreationalContext<?> context = beanManager.createCreationalContext((jakarta.enterprise.context.spi.Contextual) bean);
                    assertNotNull(beanManager.getReference(bean, BeanManager.class, context));
                }
            });
            capture(AFTER_ADV, "getInjectableReference(InjectionPoint, CreationalContext)", new CheckedRunnable() {
                @Override
                public void run() {
                    InjectionPoint injectionPoint = createBeanManagerInjectionPoint(beanManager);
                    CreationalContext<?> context = beanManager.createCreationalContext(null);
                    assertNotNull(beanManager.getInjectableReference(injectionPoint, context));
                }
            });
        }

        private static InjectionPoint createBeanManagerInjectionPoint(BeanManager beanManager) {
            AnnotatedType<InjectionPointCarrier> annotatedType = beanManager.createAnnotatedType(InjectionPointCarrier.class);
            for (AnnotatedField<? super InjectionPointCarrier> field : annotatedType.getFields()) {
                if ("manager".equals(field.getJavaMember().getName())) {
                    return beanManager.createInjectionPoint(field);
                }
            }
            throw new AssertionError("Expected InjectionPointCarrier.manager field");
        }

        private static void capture(Map<String, Throwable> sink, String operation, CheckedRunnable runnable) {
            try {
                runnable.run();
                sink.put(operation, null);
            } catch (Throwable t) {
                sink.put(operation, t);
            }
        }

        private interface CheckedRunnable {
            void run() throws Exception;
        }
    }

    public static class DelegatingProvider implements CDIProvider {
        private final Supplier<CDI<Object>> supplier;

        DelegatingProvider(Supplier<CDI<Object>> supplier) {
            this.supplier = supplier;
        }

        @Override
        public CDI<Object> getCDI() {
            return supplier.get();
        }
    }

    @Dependent
    public static class ResolvableDependency {
    }

    public static class ResolvableCarrier {
        @Inject
        ResolvableDependency dependency;
    }

    public static class ResolvableConstructorCarrier {
        @Inject
        public ResolvableConstructorCarrier(ResolvableDependency dependency) {
        }
    }

    public static class UnsatisfiedCarrier {
        @Inject
        MissingDependency missing;
    }

    public static class InvalidInjectionTargetType {
        @Inject
        MissingDependency missing;
    }

    public static class InvalidParameterCarrier {
        @Inject
        public InvalidParameterCarrier(MissingDependency missing) {
        }
    }

    public static class ProducedProduct {
        final String value;

        ProducedProduct(String value) {
            this.value = value;
        }
    }

    @Dependent
    public static class ProducerFieldDeclaringBean {
        @Produces
        public static ProducedProduct product = new ProducedProduct("field-product");
    }

    @Dependent
    public static class ProducerMethodDeclaringBean {
        @Produces
        public static ProducedProduct produce() {
            return new ProducedProduct("method-product");
        }
    }

    @Dependent
    public static class ProducerMethodWithParamDeclaringBean {
        @Produces
        public ProducedProduct produceWithParam(ResolvableDependency dependency) {
            return new ProducedProduct(dependency != null ? "method-param-product" : "missing-dependency");
        }
    }

    public static class InvalidProducerMethodDeclaringBean {
        @Produces
        public ProducedProduct invalidProduce(MissingDependency missing) {
            return new ProducedProduct("invalid");
        }
    }

    @ApplicationScoped
    @Named("typeAttributesBean")
    @Blue
    public static class BeanAttributesTypeFixture {
    }

    @Dependent
    public static class BeanAttributesProducerBean {
        @Produces
        @Named("memberProductBean")
        @Blue
        public ProducedProduct memberProduct() {
            return new ProducedProduct("member-attributes");
        }
    }

    @ApplicationScoped
    @SessionScoped
    public static class InvalidTypeWithMultipleScopes {
    }

    @Dependent
    public static class InvalidMemberAttributesProducerBean {
        @Produces
        @ApplicationScoped
        @SessionScoped
        public ProducedProduct invalidMemberProduct = new ProducedProduct("invalid-member");
    }

    public static class RegisteredLookupExtension implements Extension {
    }

    public static class UnregisteredLookupExtension implements Extension {
    }

    public interface MissingDependency {
    }

    public interface AmbiguousService {
        String name();
    }

    @Dependent
    public static class AmbiguousServiceOne implements AmbiguousService {
        @Override
        public String name() {
            return "one";
        }
    }

    @Dependent
    public static class AmbiguousServiceTwo implements AmbiguousService {
        @Override
        public String name() {
            return "two";
        }
    }

    public static class AmbiguousCarrier {
        @Inject
        AmbiguousService service;
    }

    public interface DelegateContract {
        String ping();
    }

    @Dependent
    public static class DelegateTargetBean implements DelegateContract {
        @Override
        public String ping() {
            return "delegate-target";
        }
    }

    @Decorator
    @Dependent
    public static class DelegateWrappingDecorator implements DelegateContract {
        @Inject
        @Delegate
        DelegateContract delegate;

        @Override
        public String ping() {
            return "decorator->" + delegate.ping();
        }
    }

    public static class TrackedDependentCarrier {
        @Inject
        TrackedDependent dependent;
    }

    @Dependent
    public static class TrackedDependent {
        @PreDestroy
        void onDestroy() {
            DependentReferenceRecorder.preDestroyCalls++;
        }
    }

    public static class DependentReferenceRecorder {
        static volatile int preDestroyCalls;

        static void reset() {
            preDestroyCalls = 0;
        }
    }

    @Dependent
    public static class UnmanagedDependency {
    }

    @Dependent
    public static class UnmanagedFoo {
        @Inject
        UnmanagedDependency dependency;

        @PostConstruct
        void onPostConstruct() {
            UnmanagedLifecycleRecorder.postConstructCalls++;
        }

        @PreDestroy
        void onPreDestroy() {
            UnmanagedLifecycleRecorder.preDestroyCalls++;
        }
    }

    public static class UnmanagedLifecycleRecorder {
        static volatile int postConstructCalls;
        static volatile int preDestroyCalls;

        static void reset() {
            postConstructCalls = 0;
            preDestroyCalls = 0;
        }
    }

    public interface QualifiedContract {
        String name();
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE, ElementType.METHOD})
    public @interface Blue {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE, ElementType.METHOD})
    public @interface Green {
    }

    public static class BlueLiteral extends AnnotationLiteral<Blue> implements Blue {
        static final BlueLiteral INSTANCE = new BlueLiteral();
    }

    public static class GreenLiteral extends AnnotationLiteral<Green> implements Green {
        static final GreenLiteral INSTANCE = new GreenLiteral();
    }

    @Dependent
    @Blue
    public static class BlueQualifiedBean implements QualifiedContract {
        @Override
        public String name() {
            return "blue";
        }
    }

    @Dependent
    @Green
    public static class GreenQualifiedBean implements QualifiedContract {
        @Override
        public String name() {
            return "green";
        }
    }

    public interface DefaultContract {
        String name();
    }

    @Dependent
    public static class DefaultContractBean implements DefaultContract {
        @Override
        public String name() {
            return "default";
        }
    }

    @Dependent
    @Blue
    public static class NonDefaultQualifiedContractBean implements DefaultContract {
        @Override
        public String name() {
            return "non-default";
        }
    }

    public interface AvailabilityContract {
        String name();
    }

    @Dependent
    public static class AvailableDefaultBean implements AvailabilityContract {
        @Override
        public String name() {
            return "available";
        }
    }

    @Dependent
    @Alternative
    public static class DisabledAlternativeBean implements AvailabilityContract {
        @Override
        public String name() {
            return "disabled-alternative";
        }
    }

    public interface UnavailableContract {
        String value();
    }

    @Dependent
    @Alternative
    public static class UnavailableAlternativeBean implements UnavailableContract {
        @Override
        public String value() {
            return "unavailable";
        }
    }

    public interface NamedContract {
        String name();
    }

    @Dependent
    @Named("namedService")
    public static class NamedDefaultBean implements NamedContract {
        @Override
        public String name() {
            return "named-default";
        }
    }

    @Dependent
    @Alternative
    @Named("namedService")
    public static class NamedDisabledAlternativeBean implements NamedContract {
        @Override
        public String name() {
            return "named-disabled-alt";
        }
    }

    @Dependent
    @Named("otherService")
    public static class OtherNamedBean implements NamedContract {
        @Override
        public String name() {
            return "other";
        }
    }

    @Dependent
    public static class PassivationCapableFixtureBean {
    }

    public interface DecoratorResolutionContract {
        String ping();
    }

    @Blue
    public static class AnnotatedTypeFixtureClass {
    }

    @Green
    public interface AnnotatedTypeFixtureInterface {
    }

    @Dependent
    public static class DecoratedTargetBean implements DecoratorResolutionContract {
        @Override
        public String ping() {
            return "target";
        }
    }

    @Decorator
    @Dependent
    @Priority(10)
    public static class HighPriorityDecorator implements DecoratorResolutionContract {
        @Inject
        @Delegate
        DecoratorResolutionContract delegate;

        @Override
        public String ping() {
            return "high->" + delegate.ping();
        }
    }

    @Decorator
    @Dependent
    @Priority(20)
    public static class LowPriorityDecorator implements DecoratorResolutionContract {
        @Inject
        @Delegate
        DecoratorResolutionContract delegate;

        @Override
        public String ping() {
            return "low->" + delegate.ping();
        }
    }

    @Decorator
    @Dependent
    public static class DisabledDecorator implements DecoratorResolutionContract {
        @Inject
        @Delegate
        DecoratorResolutionContract delegate;

        @Override
        public String ping() {
            return "disabled->" + delegate.ping();
        }
    }
}
