package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter23.par236containerlifecycleevents;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter23.par236containerlifecycleevents.vetoedpat.VetoedPackagePatBean;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.discovery.NonPortableBehaviourException;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.Destroyed;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.event.Reception;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Vetoed;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.Discovery;
import jakarta.enterprise.inject.build.compatible.spi.Enhancement;
import jakarta.enterprise.inject.build.compatible.spi.Registration;
import jakarta.enterprise.inject.build.compatible.spi.SkipIfPortableExtensionPresent;
import jakarta.enterprise.inject.build.compatible.spi.Synthesis;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.inject.build.compatible.spi.Validation;
import jakarta.inject.Qualifier;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.AfterDeploymentValidation;
import jakarta.enterprise.inject.spi.AfterTypeDiscovery;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.AnnotatedMember;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanAttributes;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.BeforeShutdown;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.inject.spi.EventContext;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.InjectionTarget;
import jakarta.enterprise.inject.spi.ObserverMethod;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.ProcessBean;
import jakarta.enterprise.inject.spi.ProcessBeanAttributes;
import jakarta.enterprise.inject.spi.ProcessInjectionPoint;
import jakarta.enterprise.inject.spi.ProcessInjectionTarget;
import jakarta.enterprise.inject.spi.ProcessManagedBean;
import jakarta.enterprise.inject.spi.ProcessObserverMethod;
import jakarta.enterprise.inject.spi.ProcessProducerField;
import jakarta.enterprise.inject.spi.ProcessProducerMethod;
import jakarta.enterprise.inject.spi.ProcessProducer;
import jakarta.enterprise.inject.spi.ProcessSyntheticAnnotatedType;
import jakarta.enterprise.inject.spi.ProcessSyntheticBean;
import jakarta.enterprise.inject.spi.ProcessSyntheticObserverMethod;
import jakarta.enterprise.inject.spi.Producer;
import jakarta.enterprise.inject.spi.WithAnnotations;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("23.6 - Container lifecycle events")
@Execution(ExecutionMode.SAME_THREAD)
public class ContainerLifecycleEventsTest {

    @Test
    @DisplayName("23.6 - Container lifecycle events are delivered synchronously to extension observers and extension instance is reused")
    void shouldDeliverContainerLifecycleEventsSynchronouslyToSingleExtensionInstance() {
        LifecycleEventRecorder.reset();

        long setupThreadId = Thread.currentThread().getId();
        Syringe syringe = newSyringe();
        syringe.addExtension(LifecycleRecordingExtension.class.getName());
        syringe.setup();

        assertTrue(LifecycleEventRecorder.events.contains("BeforeBeanDiscovery"));
        assertTrue(LifecycleEventRecorder.events.contains("ProcessAnnotatedType"));
        assertTrue(LifecycleEventRecorder.events.contains("AfterBeanDiscovery"));
        assertEquals(1, LifecycleEventRecorder.extensionInstanceIds.size());
        assertEquals(1, LifecycleEventRecorder.threadIds.size());
        assertTrue(LifecycleEventRecorder.threadIds.contains(setupThreadId));
        assertNotNull(LifecycleEventRecorder.lastBeanManager);
    }

    @Test
    @DisplayName("23.6 - Lifecycle observers declared on beans are not required to receive initialization events")
    void shouldNotRequireContainerLifecycleDeliveryToBeanObservers() {
        BeanLifecycleObserver.called = 0;

        Syringe syringe = newSyringe(BeanLifecycleObserver.class);
        syringe.setup();

        assertEquals(0, BeanLifecycleObserver.called);
    }

    @Test
    @DisplayName("23.6 - Calling lifecycle event object methods outside observer invocation throws IllegalStateException")
    void shouldRejectLifecycleEventObjectUsageOutsideObserverInvocation() {
        CapturedLifecycleEventExtension.reset();

        Syringe syringe = newSyringe();
        syringe.addExtension(CapturedLifecycleEventExtension.class.getName());
        syringe.setup();

        assertNotNull(CapturedLifecycleEventExtension.capturedBeforeBeanDiscovery);
        assertThrows(IllegalStateException.class, () ->
                CapturedLifecycleEventExtension.capturedBeforeBeanDiscovery.addQualifier(LateAddedQualifier.class));
    }

    @Test
    @DisplayName("23.6 - Injecting beans into extension observer method parameters is non-portable")
    void shouldRejectBeanInjectionIntoExtensionObserverMethodParameters() {
        Syringe syringe = newSyringe(SampleBean.class);
        syringe.addExtension(NonPortableInjectedBeanInExtensionObserver.class.getName());

        assertThrows(NonPortableBehaviourException.class, syringe::setup);
    }

    @Test
    @DisplayName("23.6 - Static extension observer methods for lifecycle events are non-portable")
    void shouldRejectStaticLifecycleObserverMethodOnExtension() {
        Syringe syringe = newSyringe();
        syringe.addExtension(StaticLifecycleObserverExtension.class.getName());

        assertThrows(NonPortableBehaviourException.class, syringe::setup);
    }

    @Test
    @DisplayName("23.6 - Static extension observer method for @Observes Object without qualifiers is non-portable")
    void shouldRejectStaticObjectObserverWithoutQualifierOnExtension() {
        Syringe syringe = newSyringe();
        syringe.addExtension(StaticObjectObserverWithoutQualifierExtension.class.getName());

        assertThrows(NonPortableBehaviourException.class, syringe::setup);
    }

    @Test
    @DisplayName("23.6 - Static extension observer method for @Observes @Any Object is non-portable")
    void shouldRejectStaticObjectObserverWithAnyQualifierOnExtension() {
        Syringe syringe = newSyringe();
        syringe.addExtension(StaticObjectObserverWithAnyQualifierExtension.class.getName());

        assertThrows(NonPortableBehaviourException.class, syringe::setup);
    }

    @Test
    @DisplayName("23.6 - Extension observer method notification order follows observer ordering and @Priority on observed parameter")
    void shouldOrderExtensionObserverMethodsByPriority() {
        PriorityOrderRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addExtension(PriorityOrderedObserverExtension.class.getName());
        syringe.setup();

        assertEquals(2, PriorityOrderRecorder.events.size());
        assertEquals("before-low", PriorityOrderRecorder.events.get(0));
        assertEquals("before-high", PriorityOrderRecorder.events.get(1));
    }

    @Test
    @DisplayName("23.6 - Each service provider has an @ApplicationScoped @Default bean exposing extension types")
    void shouldExposeExtensionServiceProviderAsApplicationScopedDefaultBean() {
        Syringe syringe = newSyringe();
        syringe.addExtension(InjectablePortableExtension.class.getName());
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        Set<Bean<?>> byClass = beanManager.getBeans(InjectablePortableExtension.class, Default.Literal.INSTANCE);
        Set<Bean<?>> byInterface = beanManager.getBeans(PortableExtensionContract.class, Default.Literal.INSTANCE);
        Set<Bean<?>> byBase = beanManager.getBeans(BasePortableExtension.class, Default.Literal.INSTANCE);

        assertTrue(byClass.size() >= 1);
        assertTrue(byInterface.size() >= 1);
        assertTrue(byBase.size() >= 1);

        Bean<?> extensionBean = null;
        for (Bean<?> candidate : byClass) {
            if (InjectablePortableExtension.class.equals(candidate.getBeanClass()) &&
                    ApplicationScoped.class.equals(candidate.getScope())) {
                extensionBean = candidate;
                break;
            }
        }
        assertNotNull(extensionBean);
        assertEquals(ApplicationScoped.class, extensionBean.getScope());
        assertTrue(extensionBean.getTypes().contains(InjectablePortableExtension.class));
        assertTrue(extensionBean.getTypes().contains(PortableExtensionContract.class));
        assertTrue(extensionBean.getTypes().contains(BasePortableExtension.class));
        assertTrue(extensionBean.getTypes().contains(Extension.class));
        assertTrue(extensionBean.getQualifiers().stream()
                .anyMatch(annotation -> annotation.annotationType().equals(Default.class)));
    }

    @Test
    @DisplayName("23.6 - Service provider bean supports obtaining an injectable reference to the extension instance")
    void shouldProvideInjectableReferenceForExtensionServiceProviderBean() {
        Syringe syringe = newSyringe();
        syringe.addExtension(InjectablePortableExtension.class.getName());
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        Set<Bean<?>> candidates = beanManager.getBeans(
                InjectablePortableExtension.class,
                Default.Literal.INSTANCE
        );
        Bean<?> extensionBean = null;
        for (Bean<?> candidate : candidates) {
            if (InjectablePortableExtension.class.equals(candidate.getBeanClass()) &&
                    ApplicationScoped.class.equals(candidate.getScope())) {
                extensionBean = candidate;
                break;
            }
        }
        assertNotNull(extensionBean);

        CreationalContext<?> context = beanManager.createCreationalContext(extensionBean);
        Object reference = beanManager.getReference(extensionBean, InjectablePortableExtension.class, context);

        assertNotNull(reference);
        assertTrue(reference instanceof InjectablePortableExtension);
    }

    @Test
    @DisplayName("23.6 - Application lifecycle events fire once and bean discovery events may fire multiple times in chronological order")
    void shouldFireLifecycleEventCategoriesInExpectedOrder() {
        LifecycleCategoryRecorder.reset();
        Syringe syringe = newSyringe(DiscoveryPhaseFixture.class, DependencyForDiscoveryPhase.class);
        syringe.addExtension(LifecycleCategoryObserverExtension.class.getName());
        syringe.setup();
        syringe.shutdown();

        assertEquals(1, LifecycleCategoryRecorder.applicationCount("BeforeBeanDiscovery"));
        assertEquals(1, LifecycleCategoryRecorder.applicationCount("AfterTypeDiscovery"));
        assertEquals(1, LifecycleCategoryRecorder.applicationCount("AfterBeanDiscovery"));
        assertEquals(1, LifecycleCategoryRecorder.applicationCount("AfterDeploymentValidation"));
        assertEquals(1, LifecycleCategoryRecorder.applicationCount("BeforeShutdown"));

        assertTrue(LifecycleCategoryRecorder.discoveryCount("ProcessAnnotatedType") >= 1);
        assertTrue(LifecycleCategoryRecorder.discoveryCount("ProcessInjectionPoint") >= 1);
        assertTrue(LifecycleCategoryRecorder.discoveryCount("ProcessInjectionTarget") >= 1);
        assertTrue(LifecycleCategoryRecorder.discoveryCount("ProcessBeanAttributes") >= 1);
        assertTrue(LifecycleCategoryRecorder.discoveryCount("ProcessBean") >= 1);
        assertTrue(LifecycleCategoryRecorder.discoveryCount("ProcessProducer") >= 1);
        assertTrue(LifecycleCategoryRecorder.discoveryCount("ProcessObserverMethod") >= 1);

        assertTrue(LifecycleCategoryRecorder.indexOf("BeforeBeanDiscovery")
                < LifecycleCategoryRecorder.indexOf("AfterTypeDiscovery"));
        assertTrue(LifecycleCategoryRecorder.indexOf("AfterTypeDiscovery")
                < LifecycleCategoryRecorder.indexOf("AfterBeanDiscovery"));
        assertTrue(LifecycleCategoryRecorder.indexOf("AfterBeanDiscovery")
                < LifecycleCategoryRecorder.indexOf("AfterDeploymentValidation"));
        assertTrue(LifecycleCategoryRecorder.indexOf("AfterDeploymentValidation")
                < LifecycleCategoryRecorder.indexOf("BeforeShutdown"));
    }

    @Test
    @DisplayName("23.6 - Build compatible extensions are executed during container lifecycle phases")
    void shouldExecuteBuildCompatibleExtensionsAlongsideLifecycleEvents() {
        BuildCompatibleLifecycleRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(LifecycleTrackingBuildCompatibleExtension.class.getName());
        syringe.setup();

        assertEquals(
                asList("DISCOVERY", "ENHANCEMENT", "REGISTRATION", "SYNTHESIS", "REGISTRATION", "VALIDATION"),
                BuildCompatibleLifecycleRecorder.phases
        );
    }

    @Test
    @DisplayName("23.6 - @SkipIfPortableExtensionPresent build compatible extension is ignored in CDI Full when portable extension is present")
    void shouldSkipBuildCompatibleExtensionWhenPortableExtensionIsPresent() {
        BuildCompatibleLifecycleRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addExtension(PortablePresenceExtension.class.getName());
        syringe.addBuildCompatibleExtension(SkipWhenPortablePresentBuildCompatibleExtension.class.getName());
        syringe.setup();

        assertEquals(Collections.emptyList(), BuildCompatibleLifecycleRecorder.phases);
    }

    @Test
    @DisplayName("23.6.1 - Container fires BeforeBeanDiscovery before type discovery and observer receives BeforeBeanDiscovery event object")
    void shouldFireBeforeBeanDiscoveryBeforeTypeDiscovery() {
        BeforeDiscoveryOrderRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addExtension(BeforeDiscoveryOrderExtension.class.getName());
        syringe.setup();

        assertTrue(BeforeDiscoveryOrderRecorder.events.contains("before-bean-discovery"));
        assertTrue(BeforeDiscoveryOrderRecorder.events.contains("process-annotated-type"));
        assertNotNull(BeforeDiscoveryOrderRecorder.capturedEvent);
        assertTrue(BeforeDiscoveryOrderRecorder.capturedEvent instanceof BeforeBeanDiscovery);
        assertTrue(BeforeDiscoveryOrderRecorder.indexOf("before-bean-discovery")
                < BeforeDiscoveryOrderRecorder.indexOf("process-annotated-type"));
    }

    @Test
    @DisplayName("23.6.1 - Exception thrown by BeforeBeanDiscovery observer is treated as a definition error")
    void shouldTreatBeforeBeanDiscoveryObserverExceptionAsDefinitionError() {
        Syringe syringe = newSyringe();
        syringe.addExtension(ThrowingBeforeBeanDiscoveryExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("23.6.1 - BeforeBeanDiscovery may add qualifier, scope, stereotype, and interceptor binding types")
    void shouldSupportBeforeBeanDiscoveryMetadataRegistrationMethods() {
        DynamicMetadataRecorder.reset();
        Syringe syringe = newIsolatedSyringe(
                DynamicDefaultService.class,
                DynamicQualifiedService.class,
                DynamicQualifierConsumer.class,
                DynamicStereotypedBean.class,
                DynamicInterceptedBean.class,
                DynamicBindingInterceptor.class
        );
        syringe.addExtension(DynamicMetadataExtension.class.getName());
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        assertTrue(beanManager.isQualifier(DynamicQualifier.class));
        assertTrue(beanManager.isScope(DynamicPseudoScope.class));
        assertTrue(beanManager.isStereotype(DynamicStereotype.class));
        assertTrue(beanManager.isInterceptorBinding(DynamicBinding.class));

        DynamicQualifierConsumer consumer = syringe.inject(DynamicQualifierConsumer.class);
        assertEquals("qualified", consumer.serviceName());

        Bean<?> stereotypedBean = beanManager.getBeans(DynamicStereotypedBean.class).iterator().next();
        assertEquals(ApplicationScoped.class, stereotypedBean.getScope());

        DynamicInterceptedBean interceptedBean = syringe.inject(DynamicInterceptedBean.class);
        assertEquals("ok", interceptedBean.ping());
        assertEquals(asList("dynamic-interceptor-before", "dynamic-target", "dynamic-interceptor-after"),
                DynamicMetadataRecorder.interceptorEvents);
    }

    @Test
    @DisplayName("23.6.1 - BeforeBeanDiscovery addAnnotatedType variants add classes to bean discovery")
    void shouldAddAnnotatedTypesDuringBeforeBeanDiscovery() {
        Syringe syringe = newIsolatedSyringe();
        syringe.addExtension(AnnotatedTypeAddingExtension.class.getName());
        syringe.setup();

        assertNotNull(syringe.inject(AddedViaAnnotatedType.class));
        assertNotNull(syringe.inject(AddedViaAnnotatedTypeConfigurator.class));
    }

    @Test
    @DisplayName("23.6 - TCK parity (full.extensions.lifecycle.bbd.BeforeBeanDiscoveryTest): BeforeBeanDiscovery supports metadata registration and annotated type addition")
    void shouldMatchTckBeforeBeanDiscoveryTest() {
        shouldSupportBeforeBeanDiscoveryMetadataRegistrationMethods();
        shouldAddAnnotatedTypesDuringBeforeBeanDiscovery();
    }

    @Test
    @DisplayName("23.6 - TCK parity (full.extensions.lifecycle.bbd.broken.normalScope.AddingNormalScopeTest): adding a normal scope for an unproxyable final bean causes deployment failure")
    void shouldMatchTckAddingNormalScopeTest() {
        Syringe syringe = newIsolatedSyringe(TckNormalScopeConsumer.class, TckNormalScopeFinalBean.class);
        syringe.addExtension(TckAddingNormalScopeExtension.class.getName());

        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("23.6.1 - BeforeBeanDiscovery configurators can declare qualifier and interceptor binding types")
    void shouldSupportQualifierAndInterceptorBindingConfigurators() {
        ConfiguratorMetadataRecorder.reset();
        Syringe syringe = newIsolatedSyringe(
                ConfiguratorDefaultService.class,
                ConfiguratorQualifiedService.class,
                ConfiguratorQualifierConsumer.class,
                ConfiguratorInterceptedBean.class,
                ConfiguratorBindingInterceptor.class
        );
        syringe.addExtension(ConfiguratorMetadataExtension.class.getName());
        syringe.setup();

        ConfiguratorQualifierConsumer consumer = syringe.inject(ConfiguratorQualifierConsumer.class);
        assertEquals("configured-qualified", consumer.serviceName());

        ConfiguratorInterceptedBean interceptedBean = syringe.inject(ConfiguratorInterceptedBean.class);
        assertEquals("ok", interceptedBean.ping());
        assertEquals(asList("configured-interceptor-before", "configured-target", "configured-interceptor-after"),
                ConfiguratorMetadataRecorder.interceptorEvents);
    }

    @Test
    @DisplayName("23.6.1 - Build compatible @Discovery phase executes at BeforeBeanDiscovery time before type discovery")
    void shouldExecuteBuildCompatibleDiscoveryAtBeforeBeanDiscoveryTime() {
        BeforeDiscoveryOrderRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addExtension(BeforeDiscoveryAndPatOrderExtension.class.getName());
        syringe.addBuildCompatibleExtension(DiscoveryTimingBuildCompatibleExtension.class.getName());
        syringe.setup();

        assertTrue(BeforeDiscoveryOrderRecorder.events.contains("before-bean-discovery"));
        assertTrue(BeforeDiscoveryOrderRecorder.events.contains("build-compatible-discovery"));
        assertTrue(BeforeDiscoveryOrderRecorder.events.contains("process-annotated-type"));
        assertTrue(BeforeDiscoveryOrderRecorder.indexOf("before-bean-discovery")
                < BeforeDiscoveryOrderRecorder.indexOf("build-compatible-discovery"));
        assertTrue(BeforeDiscoveryOrderRecorder.indexOf("build-compatible-discovery")
                < BeforeDiscoveryOrderRecorder.indexOf("process-annotated-type"));
    }

    @Test
    @DisplayName("23.6.2 - Container fires AfterTypeDiscovery after type discovery and before bean discovery")
    void shouldFireAfterTypeDiscoveryBetweenTypeAndBeanDiscovery() {
        AfterTypeDiscoveryRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addExtension(AfterTypeDiscoveryOrderingExtension.class.getName());
        syringe.setup();

        assertTrue(AfterTypeDiscoveryRecorder.events.contains("process-annotated-type"));
        assertTrue(AfterTypeDiscoveryRecorder.events.contains("after-type-discovery"));
        assertTrue(AfterTypeDiscoveryRecorder.events.contains("process-bean"));
        assertNotNull(AfterTypeDiscoveryRecorder.capturedEvent);
        assertTrue(AfterTypeDiscoveryRecorder.capturedEvent instanceof AfterTypeDiscovery);
        assertTrue(AfterTypeDiscoveryRecorder.indexOf("process-annotated-type")
                < AfterTypeDiscoveryRecorder.indexOf("after-type-discovery"));
        assertTrue(AfterTypeDiscoveryRecorder.indexOf("after-type-discovery")
                < AfterTypeDiscoveryRecorder.indexOf("process-bean"));
    }

    @Test
    @DisplayName("23.6.2 - getAlternatives/getInterceptors/getDecorators expose enabled application components sorted by ascending @Priority")
    void shouldExposeOrderedEnabledListsInAfterTypeDiscovery() {
        AfterTypeDiscoveryListRecorder.reset();
        Syringe syringe = newIsolatedSyringe(
                AtdDefaultAlternativeService.class,
                AtdLowPriorityAlternativeService.class,
                AtdHighPriorityAlternativeService.class,
                AtdTargetBean.class,
                AtdLowPriorityInterceptor.class,
                AtdHighPriorityInterceptor.class,
                AtdDecoratedBase.class,
                AtdLowPriorityDecorator.class,
                AtdHighPriorityDecorator.class
        );
        syringe.addExtension(AfterTypeDiscoveryListCaptureExtension.class.getName());
        syringe.setup();

        assertEquals(
                asList(
                        AtdLowPriorityAlternativeService.class.getName(),
                        AtdHighPriorityAlternativeService.class.getName()
                ),
                AfterTypeDiscoveryListRecorder.alternatives
        );
        assertEquals(
                asList(
                        AtdLowPriorityInterceptor.class.getName(),
                        AtdHighPriorityInterceptor.class.getName()
                ),
                AfterTypeDiscoveryListRecorder.interceptors
        );
        assertEquals(
                asList(
                        AtdLowPriorityDecorator.class.getName(),
                        AtdHighPriorityDecorator.class.getName()
                ),
                AfterTypeDiscoveryListRecorder.decorators
        );
    }

    @Test
    @DisplayName("23.6.2 - Observers may modify alternatives/interceptors/decorators and container uses final values after observers complete")
    void shouldUseFinalMutatedListsAfterAfterTypeDiscoveryObservers() {
        AfterTypeDiscoveryMutationRecorder.reset();
        Syringe syringe = newIsolatedSyringe(
                AtdDefaultAlternativeService.class,
                AtdLowPriorityAlternativeService.class,
                AtdHighPriorityAlternativeService.class,
                AtdAlternativeConsumer.class,
                AtdTargetBean.class,
                AtdLowPriorityInterceptor.class,
                AtdHighPriorityInterceptor.class,
                AtdDecoratedBase.class,
                AtdLowPriorityDecorator.class,
                AtdHighPriorityDecorator.class
        );
        syringe.addExtension(AfterTypeDiscoveryMutatingExtension.class.getName());
        syringe.setup();

        AtdAlternativeConsumer consumer = syringe.inject(AtdAlternativeConsumer.class);
        assertEquals("atd-low-alternative", consumer.serviceId());

        AtdTargetBean targetBean = syringe.inject(AtdTargetBean.class);
        assertEquals("ok", targetBean.invoke());
        assertEquals(
                asList("atd-high-interceptor-before", "atd-target", "atd-high-interceptor-after"),
                AfterTypeDiscoveryMutationRecorder.interceptorEvents
        );

        AtdDecoratedContract decorated = syringe.inject(AtdDecoratedContract.class);
        assertEquals("base-atd-low-decorator", decorated.decorate());
    }

    @Test
    @DisplayName("23.6.2 - AfterTypeDiscovery addAnnotatedType variants add classes for bean discovery")
    void shouldAddAnnotatedTypesDuringAfterTypeDiscovery() {
        Syringe syringe = newIsolatedSyringe();
        syringe.addExtension(AfterTypeDiscoveryAddAnnotatedTypeExtension.class.getName());
        syringe.setup();

        assertNotNull(syringe.inject(AddedByAfterTypeDiscovery.class));
        assertNotNull(syringe.inject(AddedByAfterTypeDiscoveryConfigurator.class));
    }

    @Test
    @DisplayName("23.6 - TCK parity (full.extensions.lifecycle.atd.AfterTypeDiscoveryTest): AfterTypeDiscovery mutations and annotated type registration affect discovery outcome")
    void shouldMatchTckAfterTypeDiscoveryTest() {
        shouldUseFinalMutatedListsAfterAfterTypeDiscoveryObservers();
        shouldAddAnnotatedTypesDuringAfterTypeDiscovery();
    }

    @Test
    @DisplayName("23.6.2 - addAnnotatedType of alternative/interceptor/decorator during AfterTypeDiscovery is non-portable")
    void shouldTreatAddingAlternativeInterceptorOrDecoratorViaAfterTypeDiscoveryAddAnnotatedTypeAsNonPortable() {
        Syringe syringe = newIsolatedSyringe();
        syringe.addExtension(AfterTypeDiscoveryAddNonPortableTypesExtension.class.getName());

        assertThrows(NonPortableBehaviourException.class, syringe::setup);
    }

    @Test
    @DisplayName("23.6.2 - Exception thrown by AfterTypeDiscovery observer is treated as a definition error")
    void shouldTreatAfterTypeDiscoveryObserverExceptionAsDefinitionError() {
        Syringe syringe = newSyringe();
        syringe.addExtension(ThrowingAfterTypeDiscoveryExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("23.6.2 - Calling AfterTypeDiscovery event methods outside observer invocation throws IllegalStateException")
    void shouldRejectAfterTypeDiscoveryEventObjectUsageOutsideObserverInvocation() {
        CapturedAfterTypeDiscoveryExtension.reset();
        Syringe syringe = newSyringe();
        syringe.addExtension(CapturedAfterTypeDiscoveryExtension.class.getName());
        syringe.setup();

        assertNotNull(CapturedAfterTypeDiscoveryExtension.capturedAfterTypeDiscovery);
        assertThrows(IllegalStateException.class, () ->
                CapturedAfterTypeDiscoveryExtension.capturedAfterTypeDiscovery.getAlternatives());
    }

    @Test
    @DisplayName("23.6.3 - Container fires AfterBeanDiscovery after bean discovery and before AfterDeploymentValidation")
    void shouldFireAfterBeanDiscoveryAfterBeanDiscoveryAndBeforeAfterDeploymentValidation() {
        AfterBeanDiscoveryOrderRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addExtension(AfterBeanDiscoveryOrderingExtension.class.getName());
        syringe.setup();

        assertTrue(AfterBeanDiscoveryOrderRecorder.events.contains("process-bean"));
        assertTrue(AfterBeanDiscoveryOrderRecorder.events.contains("process-observer-method"));
        assertTrue(AfterBeanDiscoveryOrderRecorder.events.contains("after-bean-discovery"));
        assertTrue(AfterBeanDiscoveryOrderRecorder.events.contains("after-deployment-validation"));
        assertNotNull(AfterBeanDiscoveryOrderRecorder.capturedEvent);
        assertTrue(AfterBeanDiscoveryOrderRecorder.capturedEvent instanceof AfterBeanDiscovery);
        assertTrue(AfterBeanDiscoveryOrderRecorder.indexOf("process-bean")
                < AfterBeanDiscoveryOrderRecorder.indexOf("after-bean-discovery"));
        assertTrue(AfterBeanDiscoveryOrderRecorder.indexOf("process-observer-method")
                < AfterBeanDiscoveryOrderRecorder.indexOf("after-bean-discovery"));
        assertTrue(AfterBeanDiscoveryOrderRecorder.indexOf("after-bean-discovery")
                < AfterBeanDiscoveryOrderRecorder.indexOf("after-deployment-validation"));
    }

    @Test
    @DisplayName("23.6.3 - addDefinitionError() registers definition error and aborts deployment")
    void shouldAbortDeploymentWhenAfterBeanDiscoveryAddsDefinitionError() {
        Syringe syringe = newSyringe();
        syringe.addExtension(AfterBeanDiscoveryAddDefinitionErrorExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("23.6.3 - addBean() fires ProcessSyntheticBean and registers bean for injection")
    void shouldFireProcessSyntheticBeanAndRegisterBeanWhenAddedInAfterBeanDiscovery() {
        AfterBeanDiscoverySyntheticBeanRecorder.reset();
        Syringe syringe = newIsolatedSyringe(AbdSyntheticConsumer.class);
        syringe.addExtension(AfterBeanDiscoveryAddBeanExtension.class.getName());
        syringe.setup();

        AbdSyntheticConsumer consumer = syringe.inject(AbdSyntheticConsumer.class);
        assertEquals("abd-synthetic-service", consumer.serviceId());
        assertTrue(AfterBeanDiscoverySyntheticBeanRecorder.events.contains("process-synthetic-bean"));
    }

    @Test
    @DisplayName("23.6.3 - addObserverMethod() fires ProcessSyntheticObserverMethod and registers observer")
    void shouldFireProcessSyntheticObserverMethodAndRegisterObserverWhenAddedInAfterBeanDiscovery() {
        AfterBeanDiscoverySyntheticObserverRecorder.reset();
        Syringe syringe = newIsolatedSyringe();
        syringe.addExtension(AfterBeanDiscoveryAddObserverMethodExtension.class.getName());
        syringe.setup();

        syringe.getBeanManager()
                .getEvent()
                .select(AbdSyntheticEvent.class)
                .fire(new AbdSyntheticEvent("payload"));

        assertTrue(AfterBeanDiscoverySyntheticObserverRecorder.events.contains("process-synthetic-observer-method"));
        assertTrue(AfterBeanDiscoverySyntheticObserverRecorder.events.contains("synthetic-observer-notified"));
    }

    @Test
    @DisplayName("23.6.3 - ObserverMethod that overrides neither notify(T) nor notify(EventContext<T>) is a definition error")
    void shouldTreatObserverMethodWithoutNotifyOverridesAsDefinitionError() {
        Syringe syringe = newIsolatedSyringe();
        syringe.addExtension(AfterBeanDiscoveryAddInvalidObserverMethodExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("23.6.3 - ObserverMethodWithoutNotifyMethodTest: invalid ObserverMethodConfigurator observer is a definition error")
    void shouldMatchTckObserverMethodWithoutNotifyMethodDefinitionError() {
        Syringe syringe = newIsolatedSyringe();
        syringe.addExtension(AfterBeanDiscoveryAddInvalidObserverMethodExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("23.6.3 - getAnnotatedType/getAnnotatedTypes expose discovered and added annotated types")
    void shouldExposeDiscoveredAndAddedAnnotatedTypesFromAfterBeanDiscovery() {
        AfterBeanDiscoveryAnnotatedTypeRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addExtension(AfterBeanDiscoveryAnnotatedTypeLookupExtension.class.getName());
        syringe.setup();

        assertTrue(AfterBeanDiscoveryAnnotatedTypeRecorder.foundAddedById);
        assertTrue(AfterBeanDiscoveryAnnotatedTypeRecorder.foundAddedInIterable);
        assertTrue(AfterBeanDiscoveryAnnotatedTypeRecorder.foundDiscoveredInIterable);
    }

    @Test
    @DisplayName("23.6.3 - getAnnotatedType(type, null) substitutes container generated id")
    void shouldAllowNullIdLookupForAnnotatedTypeInAfterBeanDiscovery() {
        AfterBeanDiscoveryNullIdRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addExtension(AfterBeanDiscoveryNullIdLookupExtension.class.getName());
        syringe.setup();

        assertTrue(AfterBeanDiscoveryNullIdRecorder.noException);
        assertNotNull(AfterBeanDiscoveryNullIdRecorder.annotatedType);
    }

    @Test
    @DisplayName("23.6.3 - Calling AfterBeanDiscovery event methods outside observer invocation throws IllegalStateException")
    void shouldRejectAfterBeanDiscoveryEventObjectUsageOutsideObserverInvocation() {
        CapturedAfterBeanDiscoveryExtension.reset();
        Syringe syringe = newSyringe();
        syringe.addExtension(CapturedAfterBeanDiscoveryExtension.class.getName());
        syringe.setup();

        assertNotNull(CapturedAfterBeanDiscoveryExtension.capturedAfterBeanDiscovery);
        assertThrows(IllegalStateException.class, () ->
                CapturedAfterBeanDiscoveryExtension.capturedAfterBeanDiscovery.getAnnotatedTypes(LifecycleMarkerBean.class));
    }

    @Test
    @DisplayName("23.6.3 - Build compatible @Synthesis phase executes at AfterBeanDiscovery time before AfterDeploymentValidation")
    void shouldExecuteBuildCompatibleSynthesisAtAfterBeanDiscoveryTime() {
        AfterBeanDiscoverySynthesisRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addExtension(AfterBeanDiscoverySynthesisOrderingExtension.class.getName());
        syringe.addBuildCompatibleExtension(AfterBeanDiscoverySynthesisTimingBuildCompatibleExtension.class.getName());
        syringe.setup();

        assertTrue(AfterBeanDiscoverySynthesisRecorder.events.contains("after-bean-discovery"));
        assertTrue(AfterBeanDiscoverySynthesisRecorder.events.contains("build-compatible-synthesis"));
        assertTrue(AfterBeanDiscoverySynthesisRecorder.events.contains("after-deployment-validation"));
        assertTrue(AfterBeanDiscoverySynthesisRecorder.indexOf("after-bean-discovery")
                < AfterBeanDiscoverySynthesisRecorder.indexOf("build-compatible-synthesis"));
        assertTrue(AfterBeanDiscoverySynthesisRecorder.indexOf("build-compatible-synthesis")
                < AfterBeanDiscoverySynthesisRecorder.indexOf("after-deployment-validation"));
    }

    @Test
    @DisplayName("23.6.3.1 - BeanConfigurator.read(AnnotatedType) initializes bean metadata")
    void shouldInitializeBeanMetadataUsingReadAnnotatedType() {
        Syringe syringe = newIsolatedSyringe();
        syringe.addExtension(BeanConfiguratorReadAnnotatedTypeExtension.class.getName());
        syringe.setup();

        Bean<?> bean = syringe.getBeanManager().getBeans(BeanConfiguratorReadAnnotatedTypeBean.class).iterator().next();
        assertEquals(ApplicationScoped.class, bean.getScope());
        assertNotNull(syringe.inject(BeanConfiguratorReadAnnotatedTypeBean.class));
    }

    @Test
    @DisplayName("23.6.3.1 - BeanConfigurator.read(BeanAttributes) initializes bean metadata")
    void shouldInitializeBeanMetadataUsingReadBeanAttributes() {
        Syringe syringe = newIsolatedSyringe();
        syringe.addExtension(BeanConfiguratorReadBeanAttributesExtension.class.getName());
        syringe.setup();

        Bean<?> bean = syringe.getBeanManager().getBeans(BeanConfiguratorReadBeanAttributesBean.class).iterator().next();
        assertEquals(ApplicationScoped.class, bean.getScope());
        assertNotNull(syringe.inject(BeanConfiguratorReadBeanAttributesBean.class));
    }

    @Test
    @DisplayName("23.6.3.1 - BeanConfigurator beanClass and injection point APIs configure synthetic bean metadata")
    void shouldConfigureBeanClassAndInjectionPointsViaBeanConfigurator() {
        Syringe syringe = newIsolatedSyringe();
        syringe.addExtension(BeanConfiguratorInjectionPointsExtension.class.getName());
        syringe.setup();

        Bean<?> combined = syringe.getBeanManager().getBeans(BeanConfiguratorInjectionPointsBean.class).iterator().next();
        Bean<?> replaced = syringe.getBeanManager().getBeans(BeanConfiguratorReplacedInjectionPointsBean.class).iterator().next();

        assertEquals(3, combined.getInjectionPoints().size());
        assertEquals(2, replaced.getInjectionPoints().size());
    }

    @Test
    @DisplayName("23.6.3.1 - BeanConfigurator id() makes bean passivation capable and priority() participates in alternative ordering")
    void shouldSupportIdAndPriorityOnBeanConfigurator() {
        Syringe syringe = newIsolatedSyringe(BeanConfiguratorAlternativeConsumer.class);
        syringe.addExtension(BeanConfiguratorAlternativePriorityExtension.class.getName());
        syringe.setup();

        Bean<?> low = syringe.getBeanManager().getBeans(BeanConfiguratorLowPriorityAlternative.class).iterator().next();
        assertTrue(low instanceof jakarta.enterprise.inject.spi.PassivationCapable);
        assertEquals("bean-configurator-low-id", ((jakarta.enterprise.inject.spi.PassivationCapable) low).getId());

        BeanConfiguratorAlternativeConsumer consumer = syringe.inject(BeanConfiguratorAlternativeConsumer.class);
        assertEquals("high", consumer.id());
    }

    @Test
    @DisplayName("23.6.3.1 - BeanConfigurator create/destroy and produce/dispose callbacks are supported")
    void shouldSupportCreateDestroyAndProduceDisposeCallbacks() {
        BeanConfiguratorCallbacksRecorder.reset();
        Syringe syringe = newIsolatedSyringe();
        syringe.addExtension(BeanConfiguratorCallbacksExtension.class.getName());
        syringe.setup();

        Bean<?> createWithBean = syringe.getBeanManager().getBeans(BeanConfiguratorCreateWithBean.class).iterator().next();
        Bean<?> produceWithBean = syringe.getBeanManager().getBeans(BeanConfiguratorProduceWithBean.class).iterator().next();

        @SuppressWarnings("unchecked")
        Bean<Object> createWithRawBean = (Bean<Object>) createWithBean;
        CreationalContext<?> createWithContext = syringe.getBeanManager().createCreationalContext(createWithBean);
        Object createWithInstance = createWithRawBean.create((CreationalContext) createWithContext);
        createWithRawBean.destroy(createWithInstance, (CreationalContext) createWithContext);

        @SuppressWarnings("unchecked")
        Bean<Object> produceWithRawBean = (Bean<Object>) produceWithBean;
        CreationalContext<?> produceWithContext = syringe.getBeanManager().createCreationalContext(produceWithBean);
        Object produceWithInstance = produceWithRawBean.create((CreationalContext) produceWithContext);
        produceWithRawBean.destroy(produceWithInstance, (CreationalContext) produceWithContext);

        assertTrue(BeanConfiguratorCallbacksRecorder.events.contains("createWith"));
        assertTrue(BeanConfiguratorCallbacksRecorder.events.contains("destroyWith"));
        assertTrue(BeanConfiguratorCallbacksRecorder.events.contains("produceWith"));
        assertTrue(BeanConfiguratorCallbacksRecorder.events.contains("disposeWith"));
    }

    @Test
    @DisplayName("23.6.3.1 - BeanConfigurator default scope rules apply when no scope is specified")
    void shouldApplyDefaultScopeWhenBeanConfiguratorScopeIsNotSpecified() {
        Syringe syringe = newIsolatedSyringe();
        syringe.addExtension(BeanConfiguratorDefaultScopeExtension.class.getName());
        syringe.setup();

        Bean<?> bean = syringe.getBeanManager().getBeans(BeanConfiguratorDefaultScopeBean.class).iterator().next();
        assertEquals(Dependent.class, bean.getScope());
    }

    @Test
    @DisplayName("23.6.3.2 - ObserverMethodConfigurator fluent APIs configure metadata and async notify callback")
    void shouldConfigureObserverMethodUsingObserverMethodConfiguratorFluentApis() {
        ObserverMethodConfiguratorRecorder.reset();
        Syringe syringe = newIsolatedSyringe();
        syringe.addExtension(ObserverMethodConfiguratorOperationsExtension.class.getName());
        syringe.setup();

        assertEquals(ObserverMethodConfiguratorOperationsExtension.class, ObserverMethodConfiguratorRecorder.beanClass);
        assertEquals(ObserverConfiguratorEvent.class, ObserverMethodConfiguratorRecorder.observedType);
        assertEquals(Reception.IF_EXISTS, ObserverMethodConfiguratorRecorder.reception);
        assertEquals(TransactionPhase.AFTER_COMPLETION, ObserverMethodConfiguratorRecorder.transactionPhase);
        assertEquals(777, ObserverMethodConfiguratorRecorder.priority);
        assertTrue(ObserverMethodConfiguratorRecorder.async);
        assertTrue(ObserverMethodConfiguratorRecorder.qualifiers.contains(ObserverConfiguratorQualifierBLiteral.INSTANCE));
        assertTrue(!ObserverMethodConfiguratorRecorder.qualifiers.contains(ObserverConfiguratorQualifierALiteral.INSTANCE));

        syringe.getBeanManager()
                .getEvent()
                .select(ObserverConfiguratorEvent.class, ObserverConfiguratorQualifierBLiteral.INSTANCE)
                .fireAsync(new ObserverConfiguratorEvent("fluent"))
                .toCompletableFuture()
                .join();

        assertTrue(ObserverMethodConfiguratorRecorder.notifications.contains("fluent"));
    }

    @Test
    @DisplayName("23.6.3.2 - ObserverMethodConfigurator.read(Method) copies observer metadata from reflected method")
    void shouldReadObserverMetadataFromJavaLangReflectMethod() {
        ObserverMethodConfiguratorRecorder.reset();
        Syringe syringe = newIsolatedSyringe();
        syringe.addExtension(ObserverMethodConfiguratorReadMethodExtension.class.getName());
        syringe.setup();

        assertEquals(ObserverMethodReadTemplates.class, ObserverMethodConfiguratorRecorder.beanClass);
        assertEquals(ObserverConfiguratorEvent.class, ObserverMethodConfiguratorRecorder.observedType);
        assertEquals(Reception.IF_EXISTS, ObserverMethodConfiguratorRecorder.reception);
        assertEquals(TransactionPhase.BEFORE_COMPLETION, ObserverMethodConfiguratorRecorder.transactionPhase);
        assertTrue(!ObserverMethodConfiguratorRecorder.async);
        assertTrue(ObserverMethodConfiguratorRecorder.qualifiers.contains(ObserverConfiguratorQualifierALiteral.INSTANCE));

        syringe.getBeanManager()
                .getEvent()
                .select(ObserverConfiguratorEvent.class, ObserverConfiguratorQualifierALiteral.INSTANCE)
                .fire(new ObserverConfiguratorEvent("read-method"));

        assertTrue(ObserverMethodConfiguratorRecorder.notifications.contains("read-method"));
    }

    @Test
    @DisplayName("23.6.3.2 - ObserverMethodConfigurator.read(AnnotatedMethod) copies observer metadata from annotated method")
    void shouldReadObserverMetadataFromAnnotatedMethod() {
        ObserverMethodConfiguratorRecorder.reset();
        Syringe syringe = newIsolatedSyringe();
        syringe.addExtension(ObserverMethodConfiguratorReadAnnotatedMethodExtension.class.getName());
        syringe.setup();

        assertEquals(ObserverMethodReadTemplates.class, ObserverMethodConfiguratorRecorder.beanClass);
        assertEquals(ObserverConfiguratorAsyncEvent.class, ObserverMethodConfiguratorRecorder.observedType);
        assertEquals(Reception.IF_EXISTS, ObserverMethodConfiguratorRecorder.reception);
        assertEquals(TransactionPhase.IN_PROGRESS, ObserverMethodConfiguratorRecorder.transactionPhase);
        assertTrue(ObserverMethodConfiguratorRecorder.async);
        assertTrue(ObserverMethodConfiguratorRecorder.qualifiers.contains(ObserverConfiguratorQualifierBLiteral.INSTANCE));

        syringe.getBeanManager()
                .getEvent()
                .select(ObserverConfiguratorAsyncEvent.class, ObserverConfiguratorQualifierBLiteral.INSTANCE)
                .fireAsync(new ObserverConfiguratorAsyncEvent("read-annotated"))
                .toCompletableFuture()
                .join();

        assertTrue(ObserverMethodConfiguratorRecorder.notifications.contains("read-annotated"));
    }

    @Test
    @DisplayName("23.6.3.2 - ObserverMethodConfigurator.read(ObserverMethod) copies observer metadata from existing observer")
    void shouldReadObserverMetadataFromExistingObserverMethod() {
        ObserverMethodConfiguratorRecorder.reset();
        Syringe syringe = newIsolatedSyringe();
        syringe.addExtension(ObserverMethodConfiguratorReadObserverMethodExtension.class.getName());
        syringe.setup();

        assertEquals(ObserverMethodReadTemplates.class, ObserverMethodConfiguratorRecorder.beanClass);
        assertEquals(ObserverConfiguratorEvent.class, ObserverMethodConfiguratorRecorder.observedType);
        assertEquals(Reception.IF_EXISTS, ObserverMethodConfiguratorRecorder.reception);
        assertEquals(TransactionPhase.BEFORE_COMPLETION, ObserverMethodConfiguratorRecorder.transactionPhase);
        assertEquals(321, ObserverMethodConfiguratorRecorder.priority);
        assertTrue(!ObserverMethodConfiguratorRecorder.async);
        assertTrue(ObserverMethodConfiguratorRecorder.qualifiers.contains(ObserverConfiguratorQualifierALiteral.INSTANCE));

        syringe.getBeanManager()
                .getEvent()
                .select(ObserverConfiguratorEvent.class, ObserverConfiguratorQualifierALiteral.INSTANCE)
                .fire(new ObserverConfiguratorEvent("read-observer"));

        assertTrue(ObserverMethodConfiguratorRecorder.notifications.contains("read-observer"));
    }

    @Test
    @DisplayName("23.6.4 - Container fires AfterDeploymentValidation after deployment validation")
    void shouldFireAfterDeploymentValidationAfterValidation() {
        AfterDeploymentValidationOrderRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addExtension(AfterDeploymentValidationOrderingExtension.class.getName());
        syringe.setup();

        assertTrue(AfterDeploymentValidationOrderRecorder.events.contains("after-bean-discovery"));
        assertTrue(AfterDeploymentValidationOrderRecorder.events.contains("after-deployment-validation"));
        assertNotNull(AfterDeploymentValidationOrderRecorder.capturedEvent);
        assertTrue(AfterDeploymentValidationOrderRecorder.capturedEvent instanceof AfterDeploymentValidation);
        assertNotNull(AfterDeploymentValidationOrderRecorder.beanManager);
        assertTrue(AfterDeploymentValidationOrderRecorder.indexOf("after-bean-discovery")
                < AfterDeploymentValidationOrderRecorder.indexOf("after-deployment-validation"));
    }

    @Test
    @DisplayName("23.6.4 - addDeploymentProblem() aborts deployment after all observers are notified")
    void shouldAbortDeploymentWhenAfterDeploymentValidationAddsDeploymentProblem() {
        AfterDeploymentValidationProblemRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addExtension(AfterDeploymentValidationAddProblemExtension.class.getName());

        assertThrows(DeploymentException.class, syringe::setup);
        assertTrue(AfterDeploymentValidationProblemRecorder.events.contains("observer-one"));
        assertTrue(AfterDeploymentValidationProblemRecorder.events.contains("observer-two"));
    }

    @Test
    @DisplayName("23.6.4 - Observer exceptions in AfterDeploymentValidation are treated as deployment problems and do not prevent other observers")
    void shouldTreatAfterDeploymentValidationObserverExceptionAsDeploymentProblem() {
        AfterDeploymentValidationExceptionRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addExtension(AfterDeploymentValidationThrowingExtension.class.getName());

        assertThrows(DeploymentException.class, syringe::setup);
        assertTrue(AfterDeploymentValidationExceptionRecorder.events.contains("throws"));
        assertTrue(AfterDeploymentValidationExceptionRecorder.events.contains("after-throw"));
    }

    @Test
    @DisplayName("23.6.4 - Calling AfterDeploymentValidation methods outside observer invocation throws IllegalStateException")
    void shouldRejectAfterDeploymentValidationEventObjectUsageOutsideObserverInvocation() {
        CapturedAfterDeploymentValidationExtension.reset();
        Syringe syringe = newSyringe();
        syringe.addExtension(CapturedAfterDeploymentValidationExtension.class.getName());
        syringe.setup();

        assertNotNull(CapturedAfterDeploymentValidationExtension.capturedAfterDeploymentValidation);
        assertThrows(IllegalStateException.class, () ->
                CapturedAfterDeploymentValidationExtension.capturedAfterDeploymentValidation
                        .addDeploymentProblem(new IllegalStateException("outside-observer")));
    }

    @Test
    @DisplayName("23.6.4 - Requests are not processed until all AfterDeploymentValidation observers return")
    void shouldNotProcessRequestsUntilAfterDeploymentValidationObserversReturn() throws Exception {
        BlockingAfterDeploymentValidationExtension.reset();
        Syringe syringe = newSyringe();
        syringe.addExtension(BlockingAfterDeploymentValidationExtension.class.getName());

        Thread setupThread = new Thread(new Runnable() {
            @Override
            public void run() {
                syringe.setup();
            }
        });
        setupThread.start();

        assertTrue(BlockingAfterDeploymentValidationExtension.entered.await(2, TimeUnit.SECONDS));
        assertThrows(IllegalStateException.class, () -> syringe.inject(LifecycleMarkerBean.class));

        BlockingAfterDeploymentValidationExtension.release.countDown();
        setupThread.join(5000);
        assertTrue(!setupThread.isAlive());
        assertNotNull(syringe.inject(LifecycleMarkerBean.class));
    }

    @Test
    @DisplayName("23.6.4 - Build compatible @Validation phase executes at AfterDeploymentValidation time")
    void shouldExecuteBuildCompatibleValidationAtAfterDeploymentValidationTime() {
        AfterDeploymentValidationBuildCompatibleRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addExtension(AfterDeploymentValidationBuildCompatibleOrderingExtension.class.getName());
        syringe.addBuildCompatibleExtension(AfterDeploymentValidationTimingBuildCompatibleExtension.class.getName());
        syringe.setup();

        assertTrue(AfterDeploymentValidationBuildCompatibleRecorder.events.contains("after-bean-discovery"));
        assertTrue(AfterDeploymentValidationBuildCompatibleRecorder.events.contains("build-compatible-validation"));
        assertTrue(AfterDeploymentValidationBuildCompatibleRecorder.events.contains("after-deployment-validation"));
        assertTrue(AfterDeploymentValidationBuildCompatibleRecorder.indexOf("after-bean-discovery")
                < AfterDeploymentValidationBuildCompatibleRecorder.indexOf("build-compatible-validation"));
        assertTrue(AfterDeploymentValidationBuildCompatibleRecorder.indexOf("build-compatible-validation")
                < AfterDeploymentValidationBuildCompatibleRecorder.indexOf("after-deployment-validation"));
    }

    @Test
    @DisplayName("23.6.5 - Container fires BeforeShutdown as final event after contexts are destroyed")
    void shouldFireBeforeShutdownAfterContextsDestroyed() {
        BeforeShutdownRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addExtension(BeforeShutdownOrderingExtension.class.getName());
        syringe.setup();
        syringe.shutdown();

        assertTrue(BeforeShutdownRecorder.events.contains("application-destroyed"));
        assertTrue(BeforeShutdownRecorder.events.contains("before-shutdown"));
        assertTrue(BeforeShutdownRecorder.applicationDestroyedAtBeforeShutdown);
        assertNotNull(BeforeShutdownRecorder.capturedEvent);
        assertNotNull(BeforeShutdownRecorder.beanManager);
        assertTrue(BeforeShutdownRecorder.indexOf("application-destroyed")
                < BeforeShutdownRecorder.indexOf("before-shutdown"));
    }

    @Test
    @DisplayName("23.6.5 - Exceptions thrown by BeforeShutdown observers are ignored")
    void shouldIgnoreExceptionsThrownByBeforeShutdownObservers() {
        BeforeShutdownExceptionRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addExtension(BeforeShutdownThrowingExtension.class.getName());
        syringe.setup();

        syringe.shutdown();

        assertTrue(BeforeShutdownExceptionRecorder.events.contains("throws"));
        assertTrue(BeforeShutdownExceptionRecorder.events.contains("after-throw"));
    }

    @Test
    @DisplayName("23.6.6 - ProcessAnnotatedType is fired for class/interface/enum and skipped for annotation and @Vetoed types")
    void shouldFireProcessAnnotatedTypeForEligibleDiscoveredTypes() {
        ProcessAnnotatedTypeRecorder.reset();
        Syringe syringe = newIsolatedSyringe(
                PatClass.class,
                PatInterface.class,
                PatEnum.class,
                PatAnnotation.class,
                PatVetoedClass.class,
                VetoedPackagePatBean.class
        );
        syringe.addExtension(ProcessAnnotatedTypeCaptureExtension.class.getName());
        syringe.setup();

        assertTrue(ProcessAnnotatedTypeRecorder.types.contains(PatClass.class.getName()));
        assertTrue(ProcessAnnotatedTypeRecorder.types.contains(PatInterface.class.getName()));
        assertTrue(ProcessAnnotatedTypeRecorder.types.contains(PatEnum.class.getName()));
        assertTrue(!ProcessAnnotatedTypeRecorder.types.contains(PatAnnotation.class.getName()));
        assertTrue(!ProcessAnnotatedTypeRecorder.types.contains(PatVetoedClass.class.getName()));
        assertTrue(!ProcessAnnotatedTypeRecorder.types.contains(VetoedPackagePatBean.class.getName()));
    }

    @Test
    @DisplayName("23.6.6 - ProcessSyntheticAnnotatedType is fired for types added by BeforeBeanDiscovery and AfterTypeDiscovery")
    void shouldFireProcessSyntheticAnnotatedTypeForExtensionAddedTypes() {
        ProcessSyntheticAnnotatedTypeRecorder.reset();
        Syringe syringe = newIsolatedSyringe();
        syringe.addExtension(SyntheticPatAddingExtension.class.getName());
        syringe.setup();

        assertTrue(ProcessSyntheticAnnotatedTypeRecorder.types.contains(SyntheticBeforeType.class.getName()));
        assertTrue(ProcessSyntheticAnnotatedTypeRecorder.types.contains(SyntheticAfterType.class.getName()));
        assertTrue(ProcessSyntheticAnnotatedTypeRecorder.sources.contains(SyntheticPatAddingExtension.class.getName()));
    }

    @Test
    @DisplayName("23.6.6 - @WithAnnotations filters ProcessAnnotatedType delivery based on type/member/parameter/meta-annotation")
    void shouldFilterProcessAnnotatedTypeObserversUsingWithAnnotations() {
        WithAnnotationsFilterRecorder.reset();
        Syringe syringe = newIsolatedSyringe(
                WithAnnotatedTypeTarget.class,
                WithAnnotatedFieldTarget.class,
                WithAnnotatedParameterTarget.class,
                WithAnnotatedMetaTarget.class,
                WithoutAnnotatedTarget.class
        );
        syringe.addExtension(WithAnnotationsFilterExtension.class.getName());
        syringe.setup();

        assertTrue(WithAnnotationsFilterRecorder.types.contains(WithAnnotatedTypeTarget.class.getName()));
        assertTrue(WithAnnotationsFilterRecorder.types.contains(WithAnnotatedFieldTarget.class.getName()));
        assertTrue(WithAnnotationsFilterRecorder.types.contains(WithAnnotatedParameterTarget.class.getName()));
        assertTrue(WithAnnotationsFilterRecorder.types.contains(WithAnnotatedMetaTarget.class.getName()));
        assertTrue(!WithAnnotationsFilterRecorder.types.contains(WithoutAnnotatedTarget.class.getName()));
    }

    @Test
    @DisplayName("23.6.6 - @WithAnnotations on non-observer parameter is treated as definition error")
    void shouldTreatInvalidWithAnnotationsPlacementAsDefinitionError() {
        Syringe syringe = newSyringe();
        syringe.addExtension(InvalidWithAnnotationsPlacementExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("23.6.6 - ProcessAnnotatedType methods called outside observer invocation throw IllegalStateException")
    void shouldRejectProcessAnnotatedTypeUsageOutsideObserverInvocation() {
        CapturedProcessAnnotatedTypeExtension.reset();
        Syringe syringe = newSyringe();
        syringe.addExtension(CapturedProcessAnnotatedTypeExtension.class.getName());
        syringe.setup();

        assertNotNull(CapturedProcessAnnotatedTypeExtension.capturedEvent);
        assertThrows(IllegalStateException.class, () -> CapturedProcessAnnotatedTypeExtension.capturedEvent.getAnnotatedType());
    }

    @Test
    @DisplayName("23.6.6 - Calling setAnnotatedType and configureAnnotatedType in same observer throws IllegalStateException")
    void shouldFailWhenSetAndConfigureAnnotatedTypeAreBothUsed() {
        Syringe syringe = newSyringe();
        syringe.addExtension(SetAndConfigurePatExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("23.6.6 - Exception thrown by ProcessAnnotatedType observer is treated as definition error")
    void shouldTreatProcessAnnotatedTypeObserverExceptionAsDefinitionError() {
        Syringe syringe = newSyringe();
        syringe.addExtension(ThrowingProcessAnnotatedTypeExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("23.6.6 - Build compatible @Enhancement phase executes at ProcessAnnotatedType time before AfterTypeDiscovery")
    void shouldExecuteBuildCompatibleEnhancementDuringProcessAnnotatedTypePhase() {
        ProcessAnnotatedTypeEnhancementOrderRecorder.reset();
        Syringe syringe = newIsolatedSyringe(PatEnhancementTarget.class);
        syringe.addExtension(ProcessAnnotatedTypeEnhancementOrderExtension.class.getName());
        syringe.addBuildCompatibleExtension(ProcessAnnotatedTypeEnhancementBuildCompatibleExtension.class.getName());
        syringe.setup();

        assertTrue(ProcessAnnotatedTypeEnhancementOrderRecorder.events.contains("process-annotated-type"));
        assertTrue(ProcessAnnotatedTypeEnhancementOrderRecorder.events.contains("build-compatible-enhancement"));
        assertTrue(ProcessAnnotatedTypeEnhancementOrderRecorder.events.contains("after-type-discovery"));
        assertTrue(ProcessAnnotatedTypeEnhancementOrderRecorder.indexOf("process-annotated-type")
                < ProcessAnnotatedTypeEnhancementOrderRecorder.indexOf("build-compatible-enhancement"));
        assertTrue(ProcessAnnotatedTypeEnhancementOrderRecorder.indexOf("build-compatible-enhancement")
                < ProcessAnnotatedTypeEnhancementOrderRecorder.indexOf("after-type-discovery"));
    }

    @Test
    @DisplayName("23.6.7 - ProcessInjectionPoint is fired for bean, interceptor and decorator injection points")
    void shouldFireProcessInjectionPointForBeanInterceptorAndDecoratorInjectionPoints() {
        ProcessInjectionPointRecorder.reset();
        Syringe syringe = newIsolatedSyringe(
                PipDependency.class,
                PipContract.class,
                PipBean.class,
                PipDecorator.class,
                PipBindingInterceptor.class,
                PipTargetBean.class
        );
        syringe.addExtension(ProcessInjectionPointCaptureExtension.class.getName());
        syringe.setup();

        assertTrue(ProcessInjectionPointRecorder.beanClasses.contains(PipBean.class.getName()),
                ProcessInjectionPointRecorder.beanClasses.toString());
        assertTrue(ProcessInjectionPointRecorder.beanClasses.contains(PipBindingInterceptor.class.getName()),
                ProcessInjectionPointRecorder.beanClasses.toString());
        assertTrue(ProcessInjectionPointRecorder.beanClasses.contains(PipDecorator.class.getName()),
                ProcessInjectionPointRecorder.beanClasses.toString());
    }

    @Test
    @DisplayName("23.6.7 - configureInjectionPoint returns same configurator instance")
    void shouldReturnSameInjectionPointConfiguratorInstance() {
        ProcessInjectionPointConfiguratorRecorder.reset();
        Syringe syringe = newIsolatedSyringe(PipDependency.class, PipBean.class);
        syringe.addExtension(ProcessInjectionPointConfiguratorExtension.class.getName());
        syringe.setup();

        assertTrue(ProcessInjectionPointConfiguratorRecorder.sameInstance);
    }

    @Test
    @DisplayName("23.6.7 - Calling setInjectionPoint and configureInjectionPoint in same observer throws IllegalStateException")
    void shouldFailWhenSetAndConfigureInjectionPointAreBothUsed() {
        Syringe syringe = newIsolatedSyringe(PipDependency.class, PipBean.class);
        syringe.addExtension(SetAndConfigureProcessInjectionPointExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("23.6.7 - addDefinitionError on ProcessInjectionPoint aborts deployment")
    void shouldAbortDeploymentWhenProcessInjectionPointAddsDefinitionError() {
        Syringe syringe = newIsolatedSyringe(PipDependency.class, PipBean.class);
        syringe.addExtension(ProcessInjectionPointAddDefinitionErrorExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("23.6.7 - Exception thrown by ProcessInjectionPoint observer is treated as definition error")
    void shouldTreatProcessInjectionPointObserverExceptionAsDefinitionError() {
        Syringe syringe = newIsolatedSyringe(PipDependency.class, PipBean.class);
        syringe.addExtension(ThrowingProcessInjectionPointExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("23.6.7 - ProcessInjectionPoint methods outside observer invocation throw IllegalStateException")
    void shouldRejectProcessInjectionPointUsageOutsideObserverInvocation() {
        CapturedProcessInjectionPointExtension.reset();
        Syringe syringe = newIsolatedSyringe(PipDependency.class, PipBean.class);
        syringe.addExtension(CapturedProcessInjectionPointExtension.class.getName());
        syringe.setup();

        assertNotNull(CapturedProcessInjectionPointExtension.capturedEvent);
        assertThrows(IllegalStateException.class, () ->
                CapturedProcessInjectionPointExtension.capturedEvent.getInjectionPoint());
    }

    @Test
    @DisplayName("23.6.7.1 - InjectionPointConfigurator supports type/qualifier/delegate/transientField operations")
    void shouldApplyInjectionPointConfiguratorOperations() {
        ProcessInjectionPointConfiguratorOperationsRecorder.reset();
        Syringe syringe = newIsolatedSyringe(
                PipDependency.class,
                PipBean.class,
                PipDecorator.class,
                PipBindingInterceptor.class,
                PipTargetBean.class
        );
        syringe.addExtension(ProcessInjectionPointConfiguratorOperationsExtension.class.getName());
        syringe.setup();

        assertTrue(ProcessInjectionPointConfiguratorOperationsRecorder.captured);
        assertEquals(String.class, ProcessInjectionPointConfiguratorOperationsRecorder.type);
        assertTrue(ProcessInjectionPointConfiguratorOperationsRecorder.delegate);
        assertTrue(ProcessInjectionPointConfiguratorOperationsRecorder.transientField);
        assertTrue(ProcessInjectionPointConfiguratorOperationsRecorder.qualifierTypes.contains(
                PipConfiguredQualifierA.class.getName()));
        assertTrue(ProcessInjectionPointConfiguratorOperationsRecorder.qualifierTypes.contains(
                PipConfiguredQualifierB.class.getName()));
        assertTrue(ProcessInjectionPointConfiguratorOperationsRecorder.qualifierTypes.contains(
                PipConfiguredQualifierC.class.getName()));
        assertTrue(ProcessInjectionPointConfiguratorOperationsRecorder.qualifierTypes.contains(
                PipConfiguredQualifierD.class.getName()));
    }

    @Test
    @DisplayName("23.6.8 - ProcessInjectionTarget is fired for bean, interceptor and decorator")
    void shouldFireProcessInjectionTargetForBeanInterceptorAndDecorator() {
        ProcessInjectionTargetRecorder.reset();
        Syringe syringe = newIsolatedSyringe(
                PitDependency.class,
                PitBean.class,
                PitDecorator.class,
                PitBindingInterceptor.class,
                PitTargetBean.class
        );
        syringe.addExtension(ProcessInjectionTargetCaptureExtension.class.getName());
        syringe.setup();

        assertTrue(ProcessInjectionTargetRecorder.beanClasses.contains(PitBean.class.getName()),
                ProcessInjectionTargetRecorder.beanClasses.toString());
        assertTrue(ProcessInjectionTargetRecorder.beanClasses.contains(PitBindingInterceptor.class.getName()),
                ProcessInjectionTargetRecorder.beanClasses.toString());
        assertTrue(ProcessInjectionTargetRecorder.beanClasses.contains(PitDecorator.class.getName()),
                ProcessInjectionTargetRecorder.beanClasses.toString());
    }

    @Test
    @DisplayName("23.6.8 - setInjectionTarget final value is used for managed bean injection lifecycle")
    void shouldUseFinalProcessInjectionTargetValueForManagedBeanLifecycle() {
        ProcessInjectionTargetReplacementRecorder.reset();
        Syringe syringe = newIsolatedSyringe(PitDependency.class, PitBean.class);
        syringe.addExtension(ProcessInjectionTargetFinalReplacementExtension.class.getName());
        syringe.setup();

        assertNotNull(syringe.inject(PitBean.class));
        assertTrue(ProcessInjectionTargetReplacementRecorder.finalReplacementUsed);
        assertTrue(!ProcessInjectionTargetReplacementRecorder.firstReplacementUsed);
    }

    @Test
    @DisplayName("23.6.8 - addDefinitionError on ProcessInjectionTarget aborts deployment")
    void shouldAbortDeploymentWhenProcessInjectionTargetAddsDefinitionError() {
        Syringe syringe = newIsolatedSyringe(PitDependency.class, PitBean.class);
        syringe.addExtension(ProcessInjectionTargetAddDefinitionErrorExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("23.6.8 - Exception thrown by ProcessInjectionTarget observer is treated as definition error")
    void shouldTreatProcessInjectionTargetObserverExceptionAsDefinitionError() {
        Syringe syringe = newIsolatedSyringe(PitDependency.class, PitBean.class);
        syringe.addExtension(ThrowingProcessInjectionTargetExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("23.6.8 - ProcessInjectionTarget methods outside observer invocation throw IllegalStateException")
    void shouldRejectProcessInjectionTargetUsageOutsideObserverInvocation() {
        CapturedProcessInjectionTargetExtension.reset();
        Syringe syringe = newIsolatedSyringe(PitDependency.class, PitBean.class);
        syringe.addExtension(CapturedProcessInjectionTargetExtension.class.getName());
        syringe.setup();

        assertNotNull(CapturedProcessInjectionTargetExtension.capturedEvent);
        assertThrows(IllegalStateException.class, () ->
                CapturedProcessInjectionTargetExtension.capturedEvent.getAnnotatedType());
        assertThrows(IllegalStateException.class, () ->
                CapturedProcessInjectionTargetExtension.capturedEvent.getInjectionTarget());
        assertThrows(IllegalStateException.class, () ->
                CapturedProcessInjectionTargetExtension.capturedEvent.addDefinitionError(new RuntimeException("late")));
    }

    @Test
    @DisplayName("23.6.9 - ProcessBeanAttributes is fired for managed/producer/interceptor/decorator and excludes built-in and synthetic beans")
    void shouldFireProcessBeanAttributesForExpectedBeanKindsOnly() {
        ProcessBeanAttributesRecorder.reset();
        Syringe syringe = newIsolatedSyringe(
                PbaDependency.class,
                PbaManagedBean.class,
                PbaProducerHost.class,
                PbaProducedByMethod.class,
                PbaProducedByField.class,
                PbaDecorator.class,
                PbaBindingInterceptor.class,
                PbaDecoratedBean.class
        );
        syringe.addExtension(ProcessBeanAttributesCaptureAndSyntheticExtension.class.getName());
        syringe.setup();

        assertTrue(ProcessBeanAttributesRecorder.sawManagedBean);
        assertTrue(ProcessBeanAttributesRecorder.sawInterceptor);
        assertTrue(ProcessBeanAttributesRecorder.sawDecorator);
        assertTrue(ProcessBeanAttributesRecorder.sawProducerMethod);
        assertTrue(ProcessBeanAttributesRecorder.sawProducerField);
        assertTrue(!ProcessBeanAttributesRecorder.sawBuiltInBean);
        assertTrue(!ProcessBeanAttributesRecorder.sawSyntheticBean);
    }

    @Test
    @DisplayName("23.6.9 - configureBeanAttributes returns same configurator and final configured BeanAttributes are applied")
    void shouldApplyConfiguredBeanAttributesAndReturnSameConfigurator() {
        ProcessBeanAttributesConfiguratorRecorder.reset();
        Syringe syringe = newIsolatedSyringe(PbaDependency.class, PbaManagedBean.class);
        syringe.addExtension(ProcessBeanAttributesConfigureExtension.class.getName());
        syringe.setup();

        assertTrue(ProcessBeanAttributesConfiguratorRecorder.sameInstance);
        Bean<?> bean = syringe.getBeanManager().getBeans(PbaManagedBean.class).iterator().next();
        assertEquals(ApplicationScoped.class, bean.getScope());
    }

    @Test
    @DisplayName("23.6.9 - setBeanAttributes replaces BeanAttributes used by container")
    void shouldReplaceBeanAttributesUsingSetBeanAttributes() {
        Syringe syringe = newIsolatedSyringe(PbaDependency.class, PbaManagedBean.class);
        syringe.addExtension(ProcessBeanAttributesSetExtension.class.getName());
        syringe.setup();

        Bean<?> bean = syringe.getBeanManager().getBeans(PbaManagedBean.class).iterator().next();
        assertEquals(ApplicationScoped.class, bean.getScope());
    }

    @Test
    @DisplayName("23.6.9 - Calling setBeanAttributes and configureBeanAttributes in same observer throws IllegalStateException")
    void shouldFailWhenSetAndConfigureBeanAttributesAreBothUsed() {
        Syringe syringe = newIsolatedSyringe(PbaDependency.class, PbaManagedBean.class);
        syringe.addExtension(SetAndConfigureProcessBeanAttributesExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("23.6.9 - addDefinitionError on ProcessBeanAttributes aborts deployment")
    void shouldAbortDeploymentWhenProcessBeanAttributesAddsDefinitionError() {
        Syringe syringe = newIsolatedSyringe(PbaDependency.class, PbaManagedBean.class);
        syringe.addExtension(ProcessBeanAttributesAddDefinitionErrorExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("23.6 - TCK parity (full.extensions.lifecycle.processBeanAttributes.broken.AddDefinitionErrorTest): addDefinitionError during ProcessBeanAttributes fails deployment")
    void shouldMatchTckProcessBeanAttributesAddDefinitionErrorTest() {
        shouldAbortDeploymentWhenProcessBeanAttributesAddsDefinitionError();
    }

    @Test
    @DisplayName("23.6.9 - veto on ProcessBeanAttributes removes bean")
    void shouldVetoBeanFromProcessBeanAttributes() {
        Syringe syringe = newIsolatedSyringe(PbaDependency.class, PbaManagedBean.class);
        syringe.addExtension(ProcessBeanAttributesVetoExtension.class.getName());
        syringe.setup();

        assertTrue(syringe.getBeanManager().getBeans(PbaManagedBean.class).isEmpty());
    }

    @Test
    @DisplayName("23.6.9 - Exception thrown by ProcessBeanAttributes observer is treated as definition error")
    void shouldTreatProcessBeanAttributesObserverExceptionAsDefinitionError() {
        Syringe syringe = newIsolatedSyringe(PbaDependency.class, PbaManagedBean.class);
        syringe.addExtension(ThrowingProcessBeanAttributesExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("23.6.9 - ProcessBeanAttributes methods outside observer invocation throw IllegalStateException")
    void shouldRejectProcessBeanAttributesUsageOutsideObserverInvocation() {
        CapturedProcessBeanAttributesExtension.reset();
        Syringe syringe = newIsolatedSyringe(PbaDependency.class, PbaManagedBean.class);
        syringe.addExtension(CapturedProcessBeanAttributesExtension.class.getName());
        syringe.setup();

        assertNotNull(CapturedProcessBeanAttributesExtension.capturedEvent);
        assertThrows(IllegalStateException.class, () ->
                CapturedProcessBeanAttributesExtension.capturedEvent.getAnnotated());
        assertThrows(IllegalStateException.class, () ->
                CapturedProcessBeanAttributesExtension.capturedEvent.getBeanAttributes());
        assertThrows(IllegalStateException.class, () ->
                CapturedProcessBeanAttributesExtension.capturedEvent.configureBeanAttributes());
        assertThrows(IllegalStateException.class, () ->
                CapturedProcessBeanAttributesExtension.capturedEvent.veto());
    }

    @Test
    @DisplayName("23.6.9 - ignoreFinalMethods allows otherwise unproxyable final methods")
    void shouldAllowIgnoreFinalMethodsOnProcessBeanAttributes() {
        Syringe syringe = newIsolatedSyringe(PbaFinalMethodBean.class, PbaFinalMethodConsumer.class);
        syringe.addExtension(ProcessBeanAttributesIgnoreFinalMethodsExtension.class.getName());

        syringe.setup();
        assertNotNull(syringe.inject(PbaFinalMethodConsumer.class));
    }

    @Test
    @DisplayName("23.6.9.1 - BeanAttributesConfigurator addType and addTransitiveTypeClosure are applied")
    void shouldApplyBeanAttributesConfiguratorTypeOperations() {
        ProcessBeanAttributesConfigurator91Recorder.reset();
        Syringe syringe = newIsolatedSyringe(Pba91ManagedBean.class);
        syringe.addExtension(ProcessBeanAttributesConfiguratorTypesExtension.class.getName());
        syringe.setup();

        Bean<?> bean = syringe.getBeanManager().getBeans(Pba91ManagedBean.class).iterator().next();
        assertTrue(ProcessBeanAttributesConfigurator91Recorder.sameInstance);
        assertTrue(bean.getTypes().contains(Pba91ExtraType.class));
        assertTrue(bean.getTypes().contains(Pba91HierarchyChild.class));
        assertTrue(bean.getTypes().contains(Pba91HierarchyParent.class));
        assertTrue(bean.getTypes().contains(Pba91HierarchyContract.class));
    }

    @Test
    @DisplayName("23.6.9.1 - BeanAttributesConfigurator metadata operations are applied")
    void shouldApplyBeanAttributesConfiguratorMetadataOperations() {
        ProcessBeanAttributesConfigurator91Recorder.reset();
        Syringe syringe = newIsolatedSyringe(Pba91ManagedBean.class);
        syringe.addExtension(ProcessBeanAttributesConfiguratorMetadataExtension.class.getName());
        syringe.setup();

        assertTrue(ProcessBeanAttributesConfigurator91Recorder.metadataCaptured);
        assertTrue(ProcessBeanAttributesConfigurator91Recorder.types.contains(Pba91ManagedBean.class));
        assertTrue(ProcessBeanAttributesConfigurator91Recorder.types.contains(Pba91ConfiguredContract.class));
        assertTrue(!ProcessBeanAttributesConfigurator91Recorder.types.contains(Pba91RemovedType.class));
        assertEquals(ApplicationScoped.class, ProcessBeanAttributesConfigurator91Recorder.scope);
        assertEquals("pba91Managed", ProcessBeanAttributesConfigurator91Recorder.name);
        assertTrue(ProcessBeanAttributesConfigurator91Recorder.qualifierTypes.contains(Pba91QualifierA.class.getName()));
        assertTrue(ProcessBeanAttributesConfigurator91Recorder.qualifierTypes.contains(Pba91QualifierB.class.getName()));
        assertTrue(ProcessBeanAttributesConfigurator91Recorder.qualifierTypes.contains(Pba91QualifierC.class.getName()));
        assertTrue(ProcessBeanAttributesConfigurator91Recorder.qualifierTypes.contains(Pba91QualifierD.class.getName()));
        assertTrue(ProcessBeanAttributesConfigurator91Recorder.stereotypes.contains(Pba91StereotypeA.class));
        assertTrue(ProcessBeanAttributesConfigurator91Recorder.stereotypes.contains(Pba91StereotypeB.class));
        assertTrue(ProcessBeanAttributesConfigurator91Recorder.stereotypes.contains(Pba91StereotypeC.class));
        assertTrue(ProcessBeanAttributesConfigurator91Recorder.alternative);
    }

    @Test
    @DisplayName("23.6.10 - Specialized ProcessBean event types are fired for managed, producer and synthetic beans")
    void shouldFireSpecializedProcessBeanEventsForBeanKinds() {
        ProcessBean10Recorder.reset();
        Syringe syringe = newIsolatedSyringe(
                Pb10Dependency.class,
                Pb10ManagedBean.class,
                Pb10ProducerHost.class,
                Pb10ProducedByMethod.class,
                Pb10ProducedByField.class,
                Pb10Decorator.class,
                Pb10BindingInterceptor.class,
                Pb10DecoratedBean.class
        );
        syringe.addExtension(ProcessBean10CaptureAndSyntheticExtension.class.getName());
        syringe.setup();

        assertTrue(ProcessBean10Recorder.sawManagedBean);
        assertTrue(ProcessBean10Recorder.sawInterceptorManagedBean);
        assertTrue(ProcessBean10Recorder.sawDecoratorManagedBean);
        assertTrue(ProcessBean10Recorder.sawProducerMethod);
        assertTrue(ProcessBean10Recorder.sawProducerField);
        assertTrue(ProcessBean10Recorder.sawSyntheticBean);
        assertTrue(!ProcessBean10Recorder.sawBuiltInBean);
        assertTrue(ProcessBean10Recorder.sawPbaBeforeProcessBeanForManaged);
    }

    @Test
    @DisplayName("23.6.10 - ProcessManagedBean, ProcessProducerMethod, ProcessProducerField and ProcessSyntheticBean API values are exposed")
    void shouldExposeSpecializedProcessBeanApis() {
        ProcessBean10ApiRecorder.reset();
        Syringe syringe = newIsolatedSyringe(
                Pb10Dependency.class,
                Pb10ManagedBean.class,
                Pb10ProducerHost.class,
                Pb10ProducedByMethod.class,
                Pb10ProducedByField.class
        );
        syringe.addExtension(ProcessBean10ApiExtension.class.getName());
        syringe.setup();

        assertTrue(ProcessBean10ApiRecorder.managedSeen);
        assertTrue(ProcessBean10ApiRecorder.managedAnnotatedBeanClassMatches);
        assertTrue(ProcessBean10ApiRecorder.managedInvokerBuilderCreated);
        assertTrue(ProcessBean10ApiRecorder.producerMethodSeen);
        assertTrue(ProcessBean10ApiRecorder.producerMethodAnnotatedMatches);
        assertTrue(ProcessBean10ApiRecorder.producerMethodDisposedParameterNull);
        assertTrue(ProcessBean10ApiRecorder.producerFieldSeen);
        assertTrue(ProcessBean10ApiRecorder.producerFieldAnnotatedMatches);
        assertTrue(ProcessBean10ApiRecorder.producerFieldDisposedParameterNull);
        assertTrue(ProcessBean10ApiRecorder.syntheticSeen);
        assertTrue(ProcessBean10ApiRecorder.syntheticSourceCaptured);
    }

    @Test
    @DisplayName("23.6.10 - addDefinitionError on ProcessBean aborts deployment")
    void shouldAbortDeploymentWhenProcessBeanAddsDefinitionError() {
        Syringe syringe = newIsolatedSyringe(Pb10Dependency.class, Pb10ManagedBean.class);
        syringe.addExtension(ProcessBean10AddDefinitionErrorExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("23.6.10 - Exception thrown by ProcessBean observer is treated as definition error")
    void shouldTreatProcessBeanObserverExceptionAsDefinitionError() {
        Syringe syringe = newIsolatedSyringe(Pb10Dependency.class, Pb10ManagedBean.class);
        syringe.addExtension(ThrowingProcessBean10Extension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("23.6.10 - ProcessBean methods outside observer invocation throw IllegalStateException")
    void shouldRejectProcessBeanUsageOutsideObserverInvocation() {
        CapturedProcessBean10Extension.reset();
        Syringe syringe = newIsolatedSyringe(
                Pb10Dependency.class,
                Pb10ManagedBean.class,
                Pb10ProducerHost.class,
                Pb10ProducedByMethod.class,
                Pb10ProducedByField.class
        );
        syringe.addExtension(CapturedProcessBean10Extension.class.getName());
        syringe.setup();

        assertNotNull(CapturedProcessBean10Extension.capturedProcessBean);
        assertThrows(IllegalStateException.class, () ->
                CapturedProcessBean10Extension.capturedProcessBean.getAnnotated());
        assertThrows(IllegalStateException.class, () ->
                CapturedProcessBean10Extension.capturedProcessBean.getBean());
        assertThrows(IllegalStateException.class, () ->
                CapturedProcessBean10Extension.capturedProcessBean.addDefinitionError(new RuntimeException("late")));

        assertNotNull(CapturedProcessBean10Extension.capturedProcessManagedBean);
        assertNotNull(CapturedProcessBean10Extension.capturedProcessProducerMethod);
        assertNotNull(CapturedProcessBean10Extension.capturedProcessProducerField);
        assertNotNull(CapturedProcessBean10Extension.capturedProcessSyntheticBean);
    }

    @Test
    @DisplayName("23.6.11 - ProcessProducer is fired for producer methods and fields")
    void shouldFireProcessProducerForMethodAndFieldProducers() {
        ProcessProducer11Recorder.reset();
        Syringe syringe = newIsolatedSyringe(
                Pp11ProducerHost.class,
                Pp11ProducedByMethod.class,
                Pp11ProducedByField.class
        );
        syringe.addExtension(ProcessProducer11CaptureExtension.class.getName());
        syringe.setup();

        assertTrue(ProcessProducer11Recorder.sawMethodProducerEvent);
        assertTrue(ProcessProducer11Recorder.sawFieldProducerEvent);
    }

    @Test
    @DisplayName("23.6.11 - setProducer final value is used by container for production")
    void shouldUseFinalProcessProducerValueForProduction() {
        ProcessProducer11ReplacementRecorder.reset();
        Syringe syringe = newIsolatedSyringe(
                Pp11ProducerHost.class,
                Pp11ProducedByMethod.class,
                Pp11ProducedByField.class,
                Pp11Consumer.class
        );
        syringe.addExtension(ProcessProducer11FinalReplacementExtension.class.getName());
        syringe.setup();

        Pp11Consumer consumer = syringe.inject(Pp11Consumer.class);
        assertNotNull(consumer);
        assertNotNull(consumer.byMethod);
        assertEquals("final-method", consumer.byMethod.source);
        assertTrue(!ProcessProducer11ReplacementRecorder.firstReplacementUsed);
        assertTrue(ProcessProducer11ReplacementRecorder.finalReplacementUsed);
    }

    @Test
    @DisplayName("23.6.11 - configureProducer returns same configurator and configured producer is applied")
    void shouldApplyConfiguredProducerAndReturnSameConfigurator() {
        ProcessProducer11ConfiguratorRecorder.reset();
        Syringe syringe = newIsolatedSyringe(
                Pp11ProducerHost.class,
                Pp11ProducedByMethod.class,
                Pp11ProducedByField.class,
                Pp11Consumer.class
        );
        syringe.addExtension(ProcessProducer11ConfigureExtension.class.getName());
        syringe.setup();

        Pp11Consumer consumer = syringe.inject(Pp11Consumer.class);
        assertNotNull(consumer);
        assertNotNull(consumer.byField);
        assertEquals("configured-field", consumer.byField.source);
        assertTrue(ProcessProducer11ConfiguratorRecorder.sameInstance);
    }

    @Test
    @DisplayName("23.6.11 - Calling setProducer and configureProducer in same observer throws IllegalStateException")
    void shouldFailWhenSetAndConfigureProducerAreBothUsed() {
        Syringe syringe = newIsolatedSyringe(
                Pp11ProducerHost.class,
                Pp11ProducedByMethod.class,
                Pp11ProducedByField.class
        );
        syringe.addExtension(SetAndConfigureProcessProducer11Extension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("23.6.11 - addDefinitionError on ProcessProducer aborts deployment")
    void shouldAbortDeploymentWhenProcessProducerAddsDefinitionError() {
        Syringe syringe = newIsolatedSyringe(
                Pp11ProducerHost.class,
                Pp11ProducedByMethod.class,
                Pp11ProducedByField.class
        );
        syringe.addExtension(ProcessProducer11AddDefinitionErrorExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("23.6.11 - Exception thrown by ProcessProducer observer is treated as definition error")
    void shouldTreatProcessProducerObserverExceptionAsDefinitionError() {
        Syringe syringe = newIsolatedSyringe(
                Pp11ProducerHost.class,
                Pp11ProducedByMethod.class,
                Pp11ProducedByField.class
        );
        syringe.addExtension(ThrowingProcessProducer11Extension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("23.6.11 - ProcessProducer methods outside observer invocation throw IllegalStateException")
    void shouldRejectProcessProducerUsageOutsideObserverInvocation() {
        CapturedProcessProducer11Extension.reset();
        Syringe syringe = newIsolatedSyringe(
                Pp11ProducerHost.class,
                Pp11ProducedByMethod.class,
                Pp11ProducedByField.class
        );
        syringe.addExtension(CapturedProcessProducer11Extension.class.getName());
        syringe.setup();

        assertNotNull(CapturedProcessProducer11Extension.capturedEvent);
        assertThrows(IllegalStateException.class, () ->
                CapturedProcessProducer11Extension.capturedEvent.getAnnotatedMember());
        assertThrows(IllegalStateException.class, () ->
                CapturedProcessProducer11Extension.capturedEvent.getProducer());
        assertThrows(IllegalStateException.class, () ->
                CapturedProcessProducer11Extension.capturedEvent.configureProducer());
        assertThrows(IllegalStateException.class, () -> {
            @SuppressWarnings({"rawtypes", "unchecked"})
            ProcessProducer raw = CapturedProcessProducer11Extension.capturedEvent;
            raw.setProducer(new DummyProducer<Object>());
        });
        assertThrows(IllegalStateException.class, () ->
                CapturedProcessProducer11Extension.capturedEvent.addDefinitionError(new RuntimeException("late")));
    }

    @Test
    @DisplayName("23.6.11.1 - ProducerConfigurator.produceWith sets the production callback")
    void shouldUseProducerConfiguratorProduceWithCallback() {
        ProcessProducer111ConfiguratorRecorder.reset();
        Syringe syringe = newIsolatedSyringe(
                Pp111ProducerHost.class,
                Pp111Produced.class,
                Pp111Consumer.class
        );
        syringe.addExtension(ProcessProducer111ProduceWithExtension.class.getName());
        syringe.setup();

        Pp111Consumer consumer = syringe.inject(Pp111Consumer.class);
        assertNotNull(consumer);
        assertNotNull(consumer.value);
        assertEquals("configured-produce", consumer.value.source);
        assertEquals(1, ProcessProducer111ConfiguratorRecorder.configuredProduceCalls);
        assertEquals(0, ProcessProducer111ConfiguratorRecorder.originalProduceCalls);
    }

    @Test
    @DisplayName("23.6.11.1 - ProducerConfigurator.disposeWith sets the dispose callback")
    void shouldUseProducerConfiguratorDisposeWithCallback() {
        ProcessProducer111ConfiguratorRecorder.reset();
        Syringe syringe = newIsolatedSyringe(
                Pp111ProducerHost.class,
                Pp111Produced.class
        );
        syringe.addExtension(ProcessProducer111DisposeWithExtension.class.getName());
        syringe.setup();

        Bean<?> bean = syringe.getBeanManager().getBeans(Pp111Produced.class).iterator().next();
        @SuppressWarnings("unchecked")
        Bean<Object> rawBean = (Bean<Object>) bean;
        CreationalContext<?> context = syringe.getBeanManager().createCreationalContext(bean);
        Object instance = rawBean.create((CreationalContext) context);
        rawBean.destroy(instance, (CreationalContext) context);

        assertEquals(1, ProcessProducer111ConfiguratorRecorder.configuredDisposeCalls);
        assertEquals(0, ProcessProducer111ConfiguratorRecorder.originalDisposeCalls);
    }

    @Test
    @DisplayName("23.6.12 - ProcessObserverMethod is fired for observer methods of enabled beans")
    void shouldFireProcessObserverMethodForEnabledBeanObserverMethods() {
        ProcessObserverMethod12Recorder.reset();
        Syringe syringe = newIsolatedSyringe(Pom12ObservedBean.class);
        syringe.addExtension(ProcessObserverMethod12CaptureExtension.class.getName());
        syringe.setup();

        assertTrue(ProcessObserverMethod12Recorder.sawManagedObserverEvent);
        assertTrue(ProcessObserverMethod12Recorder.annotatedMethodPresent);
        assertTrue(ProcessObserverMethod12Recorder.observerMethodPresent);
    }

    @Test
    @DisplayName("23.6.12 - setObserverMethod final value is used for observer resolution")
    void shouldUseFinalProcessObserverMethodValueForObserverResolution() {
        ProcessObserverMethod12Recorder.reset();
        Syringe syringe = newIsolatedSyringe(Pom12ObservedBean.class);
        syringe.addExtension(ProcessObserverMethod12FinalReplacementExtension.class.getName());
        syringe.setup();

        syringe.getBeanManager().getEvent().select(Pom12Event.class).fire(new Pom12Event("evt"));

        assertTrue(ProcessObserverMethod12Recorder.finalReplacementNotified);
        assertTrue(!ProcessObserverMethod12Recorder.firstReplacementNotified);
        assertTrue(!ProcessObserverMethod12Recorder.originalManagedObserverNotified);
    }

    @Test
    @DisplayName("23.6.12 - configureObserverMethod returns same configurator and configured observer is applied")
    void shouldApplyConfiguredObserverMethodAndReturnSameConfigurator() {
        ProcessObserverMethod12Recorder.reset();
        Syringe syringe = newIsolatedSyringe(Pom12ObservedBean.class);
        syringe.addExtension(ProcessObserverMethod12ConfigureExtension.class.getName());
        syringe.setup();

        syringe.getBeanManager().getEvent().select(Pom12Event.class).fire(new Pom12Event("evt"));

        assertTrue(ProcessObserverMethod12Recorder.sameConfiguratorInstance);
        assertTrue(ProcessObserverMethod12Recorder.configuredObserverNotified);
        assertTrue(!ProcessObserverMethod12Recorder.originalManagedObserverNotified);
    }

    @Test
    @DisplayName("23.6.12 - Calling setObserverMethod and configureObserverMethod in same observer throws IllegalStateException")
    void shouldFailWhenSetAndConfigureObserverMethodAreBothUsed() {
        Syringe syringe = newIsolatedSyringe(Pom12ObservedBean.class);
        syringe.addExtension(SetAndConfigureProcessObserverMethod12Extension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("23.6.12 - veto() forces the container to ignore the observer method")
    void shouldVetoProcessObserverMethod() {
        ProcessObserverMethod12Recorder.reset();
        Syringe syringe = newIsolatedSyringe(Pom12ObservedBean.class);
        syringe.addExtension(ProcessObserverMethod12VetoExtension.class.getName());
        syringe.setup();

        syringe.getBeanManager().getEvent().select(Pom12Event.class).fire(new Pom12Event("evt"));
        assertTrue(!ProcessObserverMethod12Recorder.originalManagedObserverNotified);
    }

    @Test
    @DisplayName("23.6.12 - addDefinitionError on ProcessObserverMethod aborts deployment")
    void shouldAbortDeploymentWhenProcessObserverMethodAddsDefinitionError() {
        Syringe syringe = newIsolatedSyringe(Pom12ObservedBean.class);
        syringe.addExtension(ProcessObserverMethod12AddDefinitionErrorExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("23.6.12 - Exception thrown by ProcessObserverMethod observer is treated as definition error")
    void shouldTreatProcessObserverMethodObserverExceptionAsDefinitionError() {
        Syringe syringe = newIsolatedSyringe(Pom12ObservedBean.class);
        syringe.addExtension(ThrowingProcessObserverMethod12Extension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("23.6.12 - ProcessObserverMethod methods outside observer invocation throw IllegalStateException")
    void shouldRejectProcessObserverMethodUsageOutsideObserverInvocation() {
        CapturedProcessObserverMethod12Extension.reset();
        Syringe syringe = newIsolatedSyringe(Pom12ObservedBean.class);
        syringe.addExtension(CapturedProcessObserverMethod12Extension.class.getName());
        syringe.setup();

        assertNotNull(CapturedProcessObserverMethod12Extension.capturedEvent);
        assertThrows(IllegalStateException.class, () ->
                CapturedProcessObserverMethod12Extension.capturedEvent.getAnnotatedMethod());
        assertThrows(IllegalStateException.class, () ->
                CapturedProcessObserverMethod12Extension.capturedEvent.getObserverMethod());
        assertThrows(IllegalStateException.class, () ->
                CapturedProcessObserverMethod12Extension.capturedEvent.configureObserverMethod());
        assertThrows(IllegalStateException.class, () ->
                CapturedProcessObserverMethod12Extension.capturedEvent.addDefinitionError(new RuntimeException("late")));
        assertThrows(IllegalStateException.class, () ->
                CapturedProcessObserverMethod12Extension.capturedEvent.veto());
        assertThrows(IllegalStateException.class, () ->
                ((ProcessObserverMethod) CapturedProcessObserverMethod12Extension.capturedEvent)
                        .setObserverMethod(new Pom12FixedObserverMethod("late")));
    }

    @Test
    @DisplayName("23.6.12 - ProcessSyntheticObserverMethod is fired for custom ObserverMethod and source is available")
    void shouldFireProcessSyntheticObserverMethodForCustomObserverAndExposeSource() {
        ProcessObserverMethod12Recorder.reset();
        Syringe syringe = newIsolatedSyringe();
        syringe.addExtension(ProcessObserverMethod12SyntheticCaptureExtension.class.getName());
        syringe.setup();

        syringe.getBeanManager().getEvent().select(Pom12Event.class).fire(new Pom12Event("evt"));
        assertTrue(ProcessObserverMethod12Recorder.syntheticEventSeen);
        assertTrue(ProcessObserverMethod12Recorder.syntheticSourceCaptured);
        assertTrue(ProcessObserverMethod12Recorder.syntheticObserverNotified);
    }

    @Test
    @DisplayName("23.6.12 - ProcessSyntheticObserverMethod.getAnnotatedMethod is non-portable and throws NonPortableBehaviourException")
    void shouldThrowNonPortableWhenAccessingAnnotatedMethodOnProcessSyntheticObserverMethod() {
        Syringe syringe = newIsolatedSyringe();
        syringe.addExtension(ProcessObserverMethod12SyntheticAnnotatedMethodAccessExtension.class.getName());

        assertThrows(NonPortableBehaviourException.class, syringe::setup);
    }

    private Syringe newSyringe(Class<?>... classes) {
        Class<?>[] effectiveClasses = classes;
        if (effectiveClasses == null || effectiveClasses.length == 0) {
            effectiveClasses = new Class<?>[]{LifecycleMarkerBean.class};
        }
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), effectiveClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        exclude23_6_1Fixtures(syringe);
        exclude23_6_2Fixtures(syringe);
        exclude23_6_3Fixtures(syringe);
        exclude23_6_6Fixtures(syringe);
        exclude23_6_7Fixtures(syringe);
        exclude23_6_8Fixtures(syringe);
        exclude23_6_9Fixtures(syringe);
        exclude23_6_10Fixtures(syringe);
        exclude23_6_11Fixtures(syringe);
        exclude23_6_11_1Fixtures(syringe);
        exclude23_6_12Fixtures(syringe);
        return syringe;
    }

    private Syringe newIsolatedSyringe(Class<?>... classes) {
        Class<?>[] effectiveClasses = classes;
        if (effectiveClasses == null || effectiveClasses.length == 0) {
            effectiveClasses = new Class<?>[]{LifecycleMarkerBean.class};
        }
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), effectiveClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        excludeLifecycleFixturesNotExplicitlyIncluded(syringe, effectiveClasses);
        return syringe;
    }

    private void exclude23_6_1Fixtures(Syringe syringe) {
        syringe.exclude(DynamicDefaultService.class);
        syringe.exclude(DynamicQualifiedService.class);
        syringe.exclude(DynamicQualifierConsumer.class);
        syringe.exclude(DynamicStereotypedBean.class);
        syringe.exclude(DynamicInterceptedBean.class);
        syringe.exclude(DynamicBindingInterceptor.class);
        syringe.exclude(AddedViaAnnotatedType.class);
        syringe.exclude(AddedViaAnnotatedTypeConfigurator.class);
        syringe.exclude(ConfiguratorDefaultService.class);
        syringe.exclude(ConfiguratorQualifiedService.class);
        syringe.exclude(ConfiguratorQualifierConsumer.class);
        syringe.exclude(ConfiguratorInterceptedBean.class);
        syringe.exclude(ConfiguratorBindingInterceptor.class);
    }

    private void exclude23_6_2Fixtures(Syringe syringe) {
        syringe.exclude(AtdDefaultAlternativeService.class);
        syringe.exclude(AtdLowPriorityAlternativeService.class);
        syringe.exclude(AtdHighPriorityAlternativeService.class);
        syringe.exclude(AtdAlternativeConsumer.class);
        syringe.exclude(AtdTargetBean.class);
        syringe.exclude(AtdLowPriorityInterceptor.class);
        syringe.exclude(AtdHighPriorityInterceptor.class);
        syringe.exclude(AtdDecoratedBase.class);
        syringe.exclude(AtdLowPriorityDecorator.class);
        syringe.exclude(AtdHighPriorityDecorator.class);
        syringe.exclude(AddedByAfterTypeDiscovery.class);
        syringe.exclude(AddedByAfterTypeDiscoveryConfigurator.class);
        syringe.exclude(NonPortableAtdAlternative.class);
        syringe.exclude(NonPortableAtdInterceptor.class);
        syringe.exclude(NonPortableAtdDecorator.class);
    }

    private void exclude23_6_3Fixtures(Syringe syringe) {
        syringe.exclude(AbdSyntheticConsumer.class);
        syringe.exclude(AbdSyntheticServiceImpl.class);
        syringe.exclude(AbdAddedAnnotatedType.class);
        syringe.exclude(BeanConfiguratorReadAnnotatedTypeBean.class);
        syringe.exclude(BeanConfiguratorReadBeanAttributesBean.class);
        syringe.exclude(BeanConfiguratorInjectionPointsBean.class);
        syringe.exclude(BeanConfiguratorReplacedInjectionPointsBean.class);
        syringe.exclude(BeanConfiguratorLowPriorityAlternative.class);
        syringe.exclude(BeanConfiguratorHighPriorityAlternative.class);
        syringe.exclude(BeanConfiguratorAlternativeConsumer.class);
        syringe.exclude(BeanConfiguratorCreateWithBean.class);
        syringe.exclude(BeanConfiguratorProduceWithBean.class);
        syringe.exclude(BeanConfiguratorDefaultScopeBean.class);
        syringe.exclude(ObserverMethodReadTemplates.class);
    }

    private void exclude23_6_6Fixtures(Syringe syringe) {
        syringe.exclude(PatClass.class);
        syringe.exclude(PatInterface.class);
        syringe.exclude(PatEnum.class);
        syringe.exclude(PatVetoedClass.class);
        syringe.exclude(VetoedPackagePatBean.class);
        syringe.exclude(WithAnnotatedTypeTarget.class);
        syringe.exclude(WithAnnotatedFieldTarget.class);
        syringe.exclude(WithAnnotatedParameterTarget.class);
        syringe.exclude(WithAnnotatedMetaTarget.class);
        syringe.exclude(WithoutAnnotatedTarget.class);
        syringe.exclude(SyntheticBeforeType.class);
        syringe.exclude(SyntheticAfterType.class);
        syringe.exclude(PatEnhancementTarget.class);
    }

    private void exclude23_6_7Fixtures(Syringe syringe) {
        syringe.exclude(PipDependency.class);
        syringe.exclude(PipBean.class);
        syringe.exclude(PipDecorator.class);
        syringe.exclude(PipBindingInterceptor.class);
        syringe.exclude(PipTargetBean.class);
    }

    private void exclude23_6_8Fixtures(Syringe syringe) {
        syringe.exclude(PitDependency.class);
        syringe.exclude(PitBean.class);
        syringe.exclude(PitDecorator.class);
        syringe.exclude(PitBindingInterceptor.class);
        syringe.exclude(PitTargetBean.class);
    }

    private void exclude23_6_9Fixtures(Syringe syringe) {
        syringe.exclude(PbaDependency.class);
        syringe.exclude(PbaManagedBean.class);
        syringe.exclude(PbaProducerHost.class);
        syringe.exclude(PbaProducedByMethod.class);
        syringe.exclude(PbaProducedByField.class);
        syringe.exclude(PbaSyntheticBean.class);
        syringe.exclude(PbaDecorator.class);
        syringe.exclude(PbaBindingInterceptor.class);
        syringe.exclude(PbaDecoratedBean.class);
        syringe.exclude(PbaFinalMethodBean.class);
        syringe.exclude(PbaFinalMethodConsumer.class);
        syringe.exclude(Pba91ManagedBean.class);
        syringe.exclude(Pba91HierarchyParent.class);
        syringe.exclude(Pba91HierarchyChild.class);
    }

    private void exclude23_6_10Fixtures(Syringe syringe) {
        syringe.exclude(Pb10Dependency.class);
        syringe.exclude(Pb10ManagedBean.class);
        syringe.exclude(Pb10ProducerHost.class);
        syringe.exclude(Pb10ProducedByMethod.class);
        syringe.exclude(Pb10ProducedByField.class);
        syringe.exclude(Pb10SyntheticBean.class);
        syringe.exclude(Pb10Decorator.class);
        syringe.exclude(Pb10BindingInterceptor.class);
        syringe.exclude(Pb10DecoratedBean.class);
    }

    private void exclude23_6_11Fixtures(Syringe syringe) {
        syringe.exclude(Pp11ProducerHost.class);
        syringe.exclude(Pp11ProducedByMethod.class);
        syringe.exclude(Pp11ProducedByField.class);
        syringe.exclude(Pp11Consumer.class);
    }

    private void exclude23_6_11_1Fixtures(Syringe syringe) {
        syringe.exclude(Pp111ProducerHost.class);
        syringe.exclude(Pp111Produced.class);
        syringe.exclude(Pp111Consumer.class);
    }

    private void exclude23_6_12Fixtures(Syringe syringe) {
        syringe.exclude(Pom12ObservedBean.class);
    }

    private void excludeLifecycleFixturesNotExplicitlyIncluded(Syringe syringe, Class<?>... includedClasses) {
        Set<Class<?>> included = new HashSet<Class<?>>();
        if (includedClasses != null) {
            Collections.addAll(included, includedClasses);
        }

        Class<?>[] fixtureTypes = new Class<?>[] {
                DynamicDefaultService.class,
                DynamicQualifiedService.class,
                DynamicQualifierConsumer.class,
                DynamicStereotypedBean.class,
                DynamicInterceptedBean.class,
                DynamicBindingInterceptor.class,
                AddedViaAnnotatedType.class,
                AddedViaAnnotatedTypeConfigurator.class,
                ConfiguratorDefaultService.class,
                ConfiguratorQualifiedService.class,
                ConfiguratorQualifierConsumer.class,
                ConfiguratorInterceptedBean.class,
                ConfiguratorBindingInterceptor.class,
                AtdDefaultAlternativeService.class,
                AtdLowPriorityAlternativeService.class,
                AtdHighPriorityAlternativeService.class,
                AtdAlternativeConsumer.class,
                AtdTargetBean.class,
                AtdLowPriorityInterceptor.class,
                AtdHighPriorityInterceptor.class,
                AtdDecoratedBase.class,
                AtdLowPriorityDecorator.class,
                AtdHighPriorityDecorator.class,
                AddedByAfterTypeDiscovery.class,
                AddedByAfterTypeDiscoveryConfigurator.class,
                NonPortableAtdAlternative.class,
                NonPortableAtdInterceptor.class,
                NonPortableAtdDecorator.class,
                AbdSyntheticConsumer.class,
                AbdSyntheticServiceImpl.class,
                AbdAddedAnnotatedType.class,
                BeanConfiguratorReadAnnotatedTypeBean.class,
                BeanConfiguratorReadBeanAttributesBean.class,
                BeanConfiguratorInjectionPointsBean.class,
                BeanConfiguratorReplacedInjectionPointsBean.class,
                BeanConfiguratorLowPriorityAlternative.class,
                BeanConfiguratorHighPriorityAlternative.class,
                BeanConfiguratorAlternativeConsumer.class,
                BeanConfiguratorCreateWithBean.class,
                BeanConfiguratorProduceWithBean.class,
                BeanConfiguratorDefaultScopeBean.class,
                ObserverMethodReadTemplates.class,
                PatClass.class,
                PatInterface.class,
                PatEnum.class,
                PatVetoedClass.class,
                VetoedPackagePatBean.class,
                WithAnnotatedTypeTarget.class,
                WithAnnotatedFieldTarget.class,
                WithAnnotatedParameterTarget.class,
                WithAnnotatedMetaTarget.class,
                WithoutAnnotatedTarget.class,
                SyntheticBeforeType.class,
                SyntheticAfterType.class,
                PatEnhancementTarget.class,
                PipDependency.class,
                PipBean.class,
                PipDecorator.class,
                PipBindingInterceptor.class,
                PipTargetBean.class,
                PitDependency.class,
                PitBean.class,
                PitDecorator.class,
                PitBindingInterceptor.class,
                PitTargetBean.class,
                PbaDependency.class,
                PbaManagedBean.class,
                PbaProducerHost.class,
                PbaProducedByMethod.class,
                PbaProducedByField.class,
                PbaSyntheticBean.class,
                PbaDecorator.class,
                PbaBindingInterceptor.class,
                PbaDecoratedBean.class,
                PbaFinalMethodBean.class,
                PbaFinalMethodConsumer.class,
                Pba91ManagedBean.class,
                Pba91HierarchyParent.class,
                Pba91HierarchyChild.class,
                Pb10Dependency.class,
                Pb10ManagedBean.class,
                Pb10ProducerHost.class,
                Pb10ProducedByMethod.class,
                Pb10ProducedByField.class,
                Pb10SyntheticBean.class,
                Pb10Decorator.class,
                Pb10BindingInterceptor.class,
                Pb10DecoratedBean.class,
                Pp11ProducerHost.class,
                Pp11ProducedByMethod.class,
                Pp11ProducedByField.class,
                Pp11Consumer.class,
                Pp111ProducerHost.class,
                Pp111Produced.class,
                Pp111Consumer.class,
                Pom12ObservedBean.class
        };

        for (Class<?> fixtureType : fixtureTypes) {
            if (!included.contains(fixtureType)) {
                syringe.exclude(fixtureType);
            }
        }
    }

    public static class LifecycleRecordingExtension implements Extension {
        public void before(@Observes BeforeBeanDiscovery event, BeanManager beanManager) {
            LifecycleEventRecorder.record("BeforeBeanDiscovery", this, beanManager);
        }

        public void process(@Observes ProcessAnnotatedType<?> event, BeanManager beanManager) {
            LifecycleEventRecorder.record("ProcessAnnotatedType", this, beanManager);
        }

        public void after(@Observes AfterBeanDiscovery event, BeanManager beanManager) {
            LifecycleEventRecorder.record("AfterBeanDiscovery", this, beanManager);
        }
    }

    @Dependent
    public static class BeanLifecycleObserver {
        static int called = 0;

        void before(@Observes BeforeBeanDiscovery ignored) {
            called++;
        }
    }

    public static class CapturedLifecycleEventExtension implements Extension {
        static BeforeBeanDiscovery capturedBeforeBeanDiscovery;

        static void reset() {
            capturedBeforeBeanDiscovery = null;
        }

        public void before(@Observes BeforeBeanDiscovery event) {
            capturedBeforeBeanDiscovery = event;
        }
    }

    public static class NonPortableInjectedBeanInExtensionObserver implements Extension {
        public void before(@Observes BeforeBeanDiscovery event, BeanManager beanManager, SampleBean bean) {
            // Non-portable by spec: extension observers should not receive injected beans besides BeanManager.
        }
    }

    @Dependent
    public static class SampleBean {
    }

    @Dependent
    public static class LifecycleMarkerBean {
    }

    public static class StaticLifecycleObserverExtension implements Extension {
        public static void before(@Observes BeforeBeanDiscovery event) {
        }
    }

    public static class StaticObjectObserverWithoutQualifierExtension implements Extension {
        public static void observe(@Observes Object event) {
        }
    }

    public static class StaticObjectObserverWithAnyQualifierExtension implements Extension {
        public static void observe(@Observes @Any Object event) {
        }
    }

    public static class PriorityOrderedObserverExtension implements Extension {
        public void first(@Observes @Priority(Interceptor.Priority.LIBRARY_BEFORE + 10) BeforeBeanDiscovery event) {
            PriorityOrderRecorder.events.add("before-low");
        }

        public void second(@Observes @Priority(Interceptor.Priority.LIBRARY_BEFORE + 100) BeforeBeanDiscovery event) {
            PriorityOrderRecorder.events.add("before-high");
        }
    }

    public interface PortableExtensionContract {
    }

    public static class BasePortableExtension implements Extension {
    }

    public static class InjectablePortableExtension extends BasePortableExtension implements PortableExtensionContract {
    }

    @Dependent
    public static class DependencyForDiscoveryPhase {
    }

    @Dependent
    public static class DiscoveryPhaseFixture {
        @jakarta.inject.Inject
        DependencyForDiscoveryPhase fieldInjection;

        @jakarta.inject.Inject
        void init(DependencyForDiscoveryPhase dependency) {
        }

        @jakarta.enterprise.inject.Produces
        String producer() {
            return "discovery";
        }

        void observe(@Observes String payload) {
            // force observer discovery
        }
    }

    public static class LifecycleCategoryObserverExtension implements Extension {
        public void before(@Observes BeforeBeanDiscovery event) {
            LifecycleCategoryRecorder.recordApplication("BeforeBeanDiscovery");
        }

        public void afterType(@Observes AfterTypeDiscovery event) {
            LifecycleCategoryRecorder.recordApplication("AfterTypeDiscovery");
        }

        public void afterBean(@Observes AfterBeanDiscovery event) {
            LifecycleCategoryRecorder.recordApplication("AfterBeanDiscovery");
        }

        public void afterDeployment(@Observes AfterDeploymentValidation event) {
            LifecycleCategoryRecorder.recordApplication("AfterDeploymentValidation");
        }

        public void beforeShutdown(@Observes BeforeShutdown event) {
            LifecycleCategoryRecorder.recordApplication("BeforeShutdown");
        }

        public void pat(@Observes ProcessAnnotatedType<?> event) {
            LifecycleCategoryRecorder.recordDiscovery("ProcessAnnotatedType");
        }

        public void pip(@Observes ProcessInjectionPoint<?, ?> event) {
            LifecycleCategoryRecorder.recordDiscovery("ProcessInjectionPoint");
        }

        public void pit(@Observes ProcessInjectionTarget<?> event) {
            LifecycleCategoryRecorder.recordDiscovery("ProcessInjectionTarget");
        }

        public void pba(@Observes ProcessBeanAttributes<?> event) {
            LifecycleCategoryRecorder.recordDiscovery("ProcessBeanAttributes");
        }

        public void pb(@Observes ProcessBean<?> event) {
            LifecycleCategoryRecorder.recordDiscovery("ProcessBean");
        }

        public void pp(@Observes ProcessProducer<?, ?> event) {
            LifecycleCategoryRecorder.recordDiscovery("ProcessProducer");
        }

        public void pom(@Observes ProcessObserverMethod event) {
            LifecycleCategoryRecorder.recordDiscovery("ProcessObserverMethod");
        }
    }

    public static class LifecycleTrackingBuildCompatibleExtension implements BuildCompatibleExtension {
        @Discovery
        public void discovery(Types types) {
            BuildCompatibleLifecycleRecorder.phases.add("DISCOVERY");
        }

        @Enhancement(types = LifecycleMarkerBean.class, withSubtypes = false)
        public void enhancement(jakarta.enterprise.inject.build.compatible.spi.ClassConfig classConfig) {
            BuildCompatibleLifecycleRecorder.phases.add("ENHANCEMENT");
        }

        @Registration(types = Object.class)
        public void registration() {
            BuildCompatibleLifecycleRecorder.phases.add("REGISTRATION");
        }

        @Synthesis
        public void synthesis(jakarta.enterprise.inject.build.compatible.spi.SyntheticComponents syntheticComponents) {
            BuildCompatibleLifecycleRecorder.phases.add("SYNTHESIS");
        }

        @Validation
        public void validation() {
            BuildCompatibleLifecycleRecorder.phases.add("VALIDATION");
        }
    }

    public static class PortablePresenceExtension implements Extension {
    }

    @SkipIfPortableExtensionPresent(PortablePresenceExtension.class)
    public static class SkipWhenPortablePresentBuildCompatibleExtension implements BuildCompatibleExtension {
        @Discovery
        public void discovery(Types types) {
            BuildCompatibleLifecycleRecorder.phases.add("DISCOVERY");
        }
    }

    public static class BeforeDiscoveryOrderExtension implements Extension {
        public void before(@Observes BeforeBeanDiscovery event) {
            BeforeDiscoveryOrderRecorder.capturedEvent = event;
            BeforeDiscoveryOrderRecorder.events.add("before-bean-discovery");
        }

        public void pat(@Observes ProcessAnnotatedType<?> event) {
            BeforeDiscoveryOrderRecorder.events.add("process-annotated-type");
        }
    }

    public static class ThrowingBeforeBeanDiscoveryExtension implements Extension {
        public void before(@Observes BeforeBeanDiscovery ignored) {
            throw new IllegalStateException("forced-before-bean-discovery-failure");
        }
    }

    public static class DynamicMetadataExtension implements Extension {
        public void before(@Observes BeforeBeanDiscovery event) {
            event.addQualifier(DynamicQualifier.class);
            event.addScope(DynamicPseudoScope.class, false, false);
            event.addStereotype(DynamicStereotype.class, ApplicationScoped.Literal.INSTANCE);
            event.addInterceptorBinding(DynamicBinding.class);
        }
    }

    public static class AnnotatedTypeAddingExtension implements Extension {
        public void before(@Observes BeforeBeanDiscovery event, BeanManager beanManager) {
            event.addAnnotatedType(beanManager.createAnnotatedType(AddedViaAnnotatedType.class), "added-via-annotated-type");
            event.addAnnotatedType(AddedViaAnnotatedTypeConfigurator.class, "added-via-configurator")
                    .add(Dependent.Literal.INSTANCE);
        }
    }

    public static class ConfiguratorMetadataExtension implements Extension {
        public void before(@Observes BeforeBeanDiscovery event) {
            event.configureQualifier(ConfiguratorQualifier.class);
            event.configureInterceptorBinding(ConfiguratorBinding.class);
        }
    }

    public static class BeforeDiscoveryAndPatOrderExtension implements Extension {
        public void before(@Observes BeforeBeanDiscovery event) {
            BeforeDiscoveryOrderRecorder.events.add("before-bean-discovery");
        }

        public void pat(@Observes ProcessAnnotatedType<?> event) {
            BeforeDiscoveryOrderRecorder.events.add("process-annotated-type");
        }
    }

    public static class DiscoveryTimingBuildCompatibleExtension implements BuildCompatibleExtension {
        @Discovery
        public void discovery(Types types) {
            BeforeDiscoveryOrderRecorder.events.add("build-compatible-discovery");
        }
    }

    public static class AfterTypeDiscoveryOrderingExtension implements Extension {
        public void pat(@Observes ProcessAnnotatedType<?> event) {
            AfterTypeDiscoveryRecorder.events.add("process-annotated-type");
        }

        public void atd(@Observes AfterTypeDiscovery event) {
            AfterTypeDiscoveryRecorder.capturedEvent = event;
            AfterTypeDiscoveryRecorder.events.add("after-type-discovery");
        }

        public void pb(@Observes ProcessBean<?> event) {
            AfterTypeDiscoveryRecorder.events.add("process-bean");
        }
    }

    public static class AfterTypeDiscoveryListCaptureExtension implements Extension {
        public void atd(@Observes AfterTypeDiscovery event) {
            AfterTypeDiscoveryListRecorder.capture(event);
        }
    }

    public static class AfterTypeDiscoveryMutatingExtension implements Extension {
        public void first(@Observes @Priority(Interceptor.Priority.LIBRARY_BEFORE + 10) AfterTypeDiscovery event) {
            event.getAlternatives().clear();
            event.getAlternatives().add(AtdHighPriorityAlternativeService.class);
            event.getInterceptors().clear();
            event.getInterceptors().add(AtdLowPriorityInterceptor.class);
            event.getDecorators().clear();
            event.getDecorators().add(AtdHighPriorityDecorator.class);
        }

        public void second(@Observes @Priority(Interceptor.Priority.LIBRARY_BEFORE + 100) AfterTypeDiscovery event) {
            event.getAlternatives().clear();
            event.getAlternatives().add(AtdLowPriorityAlternativeService.class);
            event.getInterceptors().clear();
            event.getInterceptors().add(AtdHighPriorityInterceptor.class);
            event.getDecorators().clear();
            event.getDecorators().add(AtdLowPriorityDecorator.class);
        }
    }

    public static class AfterTypeDiscoveryAddAnnotatedTypeExtension implements Extension {
        public void atd(@Observes AfterTypeDiscovery event, BeanManager beanManager) {
            event.addAnnotatedType(beanManager.createAnnotatedType(AddedByAfterTypeDiscovery.class), "atd-added-annotated-type");
            event.addAnnotatedType(AddedByAfterTypeDiscoveryConfigurator.class, "atd-added-configurator")
                    .add(Dependent.Literal.INSTANCE);
        }
    }

    public static class AfterTypeDiscoveryAddNonPortableTypesExtension implements Extension {
        public void atd(@Observes AfterTypeDiscovery event, BeanManager beanManager) {
            event.addAnnotatedType(beanManager.createAnnotatedType(NonPortableAtdAlternative.class), "atd-non-portable-alt");
            event.addAnnotatedType(beanManager.createAnnotatedType(NonPortableAtdInterceptor.class), "atd-non-portable-int");
            event.addAnnotatedType(beanManager.createAnnotatedType(NonPortableAtdDecorator.class), "atd-non-portable-dec");
        }
    }

    public static class ThrowingAfterTypeDiscoveryExtension implements Extension {
        public void atd(@Observes AfterTypeDiscovery ignored) {
            throw new IllegalStateException("forced-after-type-discovery-failure");
        }
    }

    public static class CapturedAfterTypeDiscoveryExtension implements Extension {
        static AfterTypeDiscovery capturedAfterTypeDiscovery;

        static void reset() {
            capturedAfterTypeDiscovery = null;
        }

        public void atd(@Observes AfterTypeDiscovery event) {
            capturedAfterTypeDiscovery = event;
        }
    }

    public static class AfterBeanDiscoveryOrderingExtension implements Extension {
        public void processBean(@Observes ProcessBean<?> event) {
            AfterBeanDiscoveryOrderRecorder.events.add("process-bean");
        }

        public void processObserverMethod(@Observes ProcessObserverMethod<?, ?> event) {
            AfterBeanDiscoveryOrderRecorder.events.add("process-observer-method");
        }

        public void after(@Observes AfterBeanDiscovery event) {
            AfterBeanDiscoveryOrderRecorder.capturedEvent = event;
            AfterBeanDiscoveryOrderRecorder.events.add("after-bean-discovery");
        }

        public void afterDeployment(@Observes AfterDeploymentValidation event) {
            AfterBeanDiscoveryOrderRecorder.events.add("after-deployment-validation");
        }
    }

    public static class AfterBeanDiscoveryAddDefinitionErrorExtension implements Extension {
        public void after(@Observes AfterBeanDiscovery event) {
            event.addDefinitionError(new IllegalStateException("forced-after-bean-discovery-definition-error"));
        }
    }

    public interface AbdSyntheticService {
        String id();
    }

    @Dependent
    public static class AbdSyntheticConsumer {
        @Inject
        AbdSyntheticService service;

        String serviceId() {
            return service.id();
        }
    }

    @Dependent
    public static class AbdSyntheticServiceImpl implements AbdSyntheticService {
        @Override
        public String id() {
            return "abd-synthetic-service";
        }
    }

    public static class AfterBeanDiscoveryAddBeanExtension implements Extension {
        public void after(@Observes AfterBeanDiscovery event) {
            event.addBean(new AbdSyntheticServiceBean());
        }

        public void synthetic(@Observes ProcessSyntheticBean<?> event) {
            AfterBeanDiscoverySyntheticBeanRecorder.events.add("process-synthetic-bean");
        }
    }

    private static class AbdSyntheticServiceBean implements Bean<AbdSyntheticService> {
        @Override
        public Class<?> getBeanClass() {
            return AbdSyntheticServiceImpl.class;
        }

        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            return Collections.emptySet();
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public Set<Annotation> getQualifiers() {
            Set<Annotation> qualifiers = new HashSet<Annotation>();
            qualifiers.add(Default.Literal.INSTANCE);
            qualifiers.add(Any.Literal.INSTANCE);
            return qualifiers;
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
            Set<Type> types = new HashSet<Type>();
            types.add(Object.class);
            types.add(AbdSyntheticService.class);
            return types;
        }

        @Override
        public boolean isAlternative() {
            return false;
        }

        @Override
        public AbdSyntheticService create(CreationalContext<AbdSyntheticService> context) {
            return new AbdSyntheticServiceImpl();
        }

        @Override
        public void destroy(AbdSyntheticService instance, CreationalContext<AbdSyntheticService> context) {
            if (context != null) {
                context.release();
            }
        }
    }

    public static class AbdSyntheticEvent {
        private final String payload;

        AbdSyntheticEvent(String payload) {
            this.payload = payload;
        }

        String payload() {
            return payload;
        }
    }

    public static class AfterBeanDiscoveryAddObserverMethodExtension implements Extension {
        public void after(@Observes AfterBeanDiscovery event) {
            event.addObserverMethod(new AbdSyntheticObserverMethod());
        }

        public void synthetic(@Observes ProcessSyntheticObserverMethod<?, ?> event) {
            AfterBeanDiscoverySyntheticObserverRecorder.events.add("process-synthetic-observer-method");
        }
    }

    private static class AbdSyntheticObserverMethod implements ObserverMethod<AbdSyntheticEvent> {
        @Override
        public Class<?> getBeanClass() {
            return AfterBeanDiscoveryAddObserverMethodExtension.class;
        }

        @Override
        public Type getObservedType() {
            return AbdSyntheticEvent.class;
        }

        @Override
        public Set<Annotation> getObservedQualifiers() {
            Set<Annotation> qualifiers = new HashSet<Annotation>();
            qualifiers.add(Default.Literal.INSTANCE);
            qualifiers.add(Any.Literal.INSTANCE);
            return qualifiers;
        }

        @Override
        public jakarta.enterprise.event.Reception getReception() {
            return jakarta.enterprise.event.Reception.ALWAYS;
        }

        @Override
        public jakarta.enterprise.event.TransactionPhase getTransactionPhase() {
            return jakarta.enterprise.event.TransactionPhase.IN_PROGRESS;
        }

        @Override
        public int getPriority() {
            return ObserverMethod.DEFAULT_PRIORITY;
        }

        @Override
        public void notify(EventContext<AbdSyntheticEvent> eventContext) {
            AfterBeanDiscoverySyntheticObserverRecorder.events.add("synthetic-observer-notified");
            assertEquals("payload", eventContext.getEvent().payload());
        }
    }

    public static class AfterBeanDiscoveryAddInvalidObserverMethodExtension implements Extension {
        public void after(@Observes AfterBeanDiscovery event) {
            event.addObserverMethod(new AbdInvalidObserverMethod());
        }
    }

    private static class AbdInvalidObserverMethod implements ObserverMethod<AbdSyntheticEvent> {
        @Override
        public Class<?> getBeanClass() {
            return AfterBeanDiscoveryAddInvalidObserverMethodExtension.class;
        }

        @Override
        public Type getObservedType() {
            return AbdSyntheticEvent.class;
        }

        @Override
        public Set<Annotation> getObservedQualifiers() {
            Set<Annotation> qualifiers = new HashSet<Annotation>();
            qualifiers.add(Default.Literal.INSTANCE);
            qualifiers.add(Any.Literal.INSTANCE);
            return qualifiers;
        }

        @Override
        public jakarta.enterprise.event.Reception getReception() {
            return jakarta.enterprise.event.Reception.ALWAYS;
        }

        @Override
        public jakarta.enterprise.event.TransactionPhase getTransactionPhase() {
            return jakarta.enterprise.event.TransactionPhase.IN_PROGRESS;
        }
    }

    public static class AfterBeanDiscoveryAnnotatedTypeLookupExtension implements Extension {
        public void before(@Observes BeforeBeanDiscovery event, BeanManager beanManager) {
            event.addAnnotatedType(beanManager.createAnnotatedType(AbdAddedAnnotatedType.class), "abd-added-type-id");
        }

        public void after(@Observes AfterBeanDiscovery event) {
            AfterBeanDiscoveryAnnotatedTypeRecorder.foundAddedById =
                    event.getAnnotatedType(AbdAddedAnnotatedType.class, "abd-added-type-id") != null;

            for (AnnotatedType<AbdAddedAnnotatedType> ignored : event.getAnnotatedTypes(AbdAddedAnnotatedType.class)) {
                AfterBeanDiscoveryAnnotatedTypeRecorder.foundAddedInIterable = true;
                break;
            }

            for (AnnotatedType<LifecycleMarkerBean> ignored : event.getAnnotatedTypes(LifecycleMarkerBean.class)) {
                AfterBeanDiscoveryAnnotatedTypeRecorder.foundDiscoveredInIterable = true;
                break;
            }
        }
    }

    public static class AfterBeanDiscoveryNullIdLookupExtension implements Extension {
        public void after(@Observes AfterBeanDiscovery event) {
            try {
                AfterBeanDiscoveryNullIdRecorder.annotatedType = event.getAnnotatedType(LifecycleMarkerBean.class, null);
                AfterBeanDiscoveryNullIdRecorder.noException = true;
            } catch (Throwable throwable) {
                AfterBeanDiscoveryNullIdRecorder.noException = false;
            }
        }
    }

    public static class CapturedAfterBeanDiscoveryExtension implements Extension {
        static AfterBeanDiscovery capturedAfterBeanDiscovery;

        static void reset() {
            capturedAfterBeanDiscovery = null;
        }

        public void after(@Observes AfterBeanDiscovery event) {
            capturedAfterBeanDiscovery = event;
        }
    }

    public static class AfterBeanDiscoverySynthesisOrderingExtension implements Extension {
        public void after(@Observes AfterBeanDiscovery event) {
            AfterBeanDiscoverySynthesisRecorder.events.add("after-bean-discovery");
        }

        public void afterDeployment(@Observes AfterDeploymentValidation event) {
            AfterBeanDiscoverySynthesisRecorder.events.add("after-deployment-validation");
        }
    }

    public static class AfterBeanDiscoverySynthesisTimingBuildCompatibleExtension implements BuildCompatibleExtension {
        @Synthesis
        public void synthesis(jakarta.enterprise.inject.build.compatible.spi.SyntheticComponents syntheticComponents) {
            AfterBeanDiscoverySynthesisRecorder.events.add("build-compatible-synthesis");
        }
    }

    @Dependent
    public static class AbdAddedAnnotatedType {
    }

    @ApplicationScoped
    public static class BeanConfiguratorReadAnnotatedTypeBean {
    }

    @ApplicationScoped
    public static class BeanConfiguratorReadBeanAttributesBean {
    }

    @Dependent
    public static class BeanConfiguratorInjectionPointsBean {
    }

    @Dependent
    public static class BeanConfiguratorReplacedInjectionPointsBean {
    }

    public interface BeanConfiguratorAlternativeService {
        String id();
    }

    @Dependent
    public static class BeanConfiguratorLowPriorityAlternative implements BeanConfiguratorAlternativeService {
        @Override
        public String id() {
            return "low";
        }
    }

    @Dependent
    public static class BeanConfiguratorHighPriorityAlternative implements BeanConfiguratorAlternativeService {
        @Override
        public String id() {
            return "high";
        }
    }

    @Dependent
    public static class BeanConfiguratorAlternativeConsumer {
        @Inject
        BeanConfiguratorAlternativeService service;

        String id() {
            return service.id();
        }
    }

    @Dependent
    public static class BeanConfiguratorCreateWithBean {
    }

    @Dependent
    public static class BeanConfiguratorProduceWithBean {
    }

    @Dependent
    public static class BeanConfiguratorDefaultScopeBean {
    }

    public static class BeanConfiguratorReadAnnotatedTypeExtension implements Extension {
        public void after(@Observes AfterBeanDiscovery event, BeanManager manager) {
            AnnotatedType<BeanConfiguratorReadAnnotatedTypeBean> annotatedType =
                    manager.createAnnotatedType(BeanConfiguratorReadAnnotatedTypeBean.class);
            event.<BeanConfiguratorReadAnnotatedTypeBean>addBean()
                    .read(annotatedType)
                    .createWith(ctx -> new BeanConfiguratorReadAnnotatedTypeBean());
        }
    }

    public static class BeanConfiguratorReadBeanAttributesExtension implements Extension {
        public void after(@Observes AfterBeanDiscovery event, BeanManager manager) {
            AnnotatedType<BeanConfiguratorReadBeanAttributesBean> annotatedType =
                    manager.createAnnotatedType(BeanConfiguratorReadBeanAttributesBean.class);
            event.<BeanConfiguratorReadBeanAttributesBean>addBean()
                    .read(manager.createBeanAttributes(annotatedType))
                    .beanClass(BeanConfiguratorReadBeanAttributesBean.class)
                    .createWith(ctx -> new BeanConfiguratorReadBeanAttributesBean());
        }
    }

    public static class BeanConfiguratorInjectionPointsExtension implements Extension {
        public void after(@Observes AfterBeanDiscovery event) {
            event.<BeanConfiguratorInjectionPointsBean>addBean()
                    .beanClass(BeanConfiguratorInjectionPointsBean.class)
                    .types(BeanConfiguratorInjectionPointsBean.class, Object.class)
                    .addInjectionPoint(new TestInjectionPoint("one"))
                    .addInjectionPoints(new TestInjectionPoint("two"), new TestInjectionPoint("three"))
                    .createWith(ctx -> new BeanConfiguratorInjectionPointsBean());

            event.<BeanConfiguratorReplacedInjectionPointsBean>addBean()
                    .beanClass(BeanConfiguratorReplacedInjectionPointsBean.class)
                    .types(BeanConfiguratorReplacedInjectionPointsBean.class, Object.class)
                    .addInjectionPoint(new TestInjectionPoint("initial"))
                    .injectionPoints(new TestInjectionPoint("replaced-one"))
                    .addInjectionPoints(Collections.singleton(new TestInjectionPoint("replaced-two")))
                    .createWith(ctx -> new BeanConfiguratorReplacedInjectionPointsBean());
        }
    }

    public static class BeanConfiguratorAlternativePriorityExtension implements Extension {
        public void after(@Observes AfterBeanDiscovery event) {
            event.<BeanConfiguratorLowPriorityAlternative>addBean()
                    .beanClass(BeanConfiguratorLowPriorityAlternative.class)
                    .types(BeanConfiguratorLowPriorityAlternative.class, BeanConfiguratorAlternativeService.class, Object.class)
                    .id("bean-configurator-low-id")
                    .alternative(true)
                    .priority(100)
                    .createWith(ctx -> new BeanConfiguratorLowPriorityAlternative());

            event.<BeanConfiguratorHighPriorityAlternative>addBean()
                    .beanClass(BeanConfiguratorHighPriorityAlternative.class)
                    .types(BeanConfiguratorHighPriorityAlternative.class, BeanConfiguratorAlternativeService.class, Object.class)
                    .id("bean-configurator-high-id")
                    .alternative(true)
                    .priority(200)
                    .createWith(ctx -> new BeanConfiguratorHighPriorityAlternative());
        }
    }

    public static class BeanConfiguratorCallbacksExtension implements Extension {
        public void after(@Observes AfterBeanDiscovery event) {
            event.<BeanConfiguratorCreateWithBean>addBean()
                    .beanClass(BeanConfiguratorCreateWithBean.class)
                    .types(BeanConfiguratorCreateWithBean.class, Object.class)
                    .createWith(ctx -> {
                        BeanConfiguratorCallbacksRecorder.events.add("createWith");
                        return new BeanConfiguratorCreateWithBean();
                    })
                    .destroyWith((instance, ctx) -> BeanConfiguratorCallbacksRecorder.events.add("destroyWith"));

            event.<BeanConfiguratorProduceWithBean>addBean()
                    .beanClass(BeanConfiguratorProduceWithBean.class)
                    .types(BeanConfiguratorProduceWithBean.class, Object.class)
                    .produceWith(instance -> {
                        BeanConfiguratorCallbacksRecorder.events.add("produceWith");
                        return new BeanConfiguratorProduceWithBean();
                    })
                    .disposeWith((instance, provider) -> BeanConfiguratorCallbacksRecorder.events.add("disposeWith"));
        }
    }

    public static class BeanConfiguratorDefaultScopeExtension implements Extension {
        public void after(@Observes AfterBeanDiscovery event) {
            event.<BeanConfiguratorDefaultScopeBean>addBean()
                    .beanClass(BeanConfiguratorDefaultScopeBean.class)
                    .types(BeanConfiguratorDefaultScopeBean.class, Object.class)
                    .createWith(ctx -> new BeanConfiguratorDefaultScopeBean());
        }
    }

    public static class ObserverMethodConfiguratorOperationsExtension implements Extension {
        public void after(@Observes AfterBeanDiscovery event) {
            event.<ObserverConfiguratorEvent>addObserverMethod()
                    .beanClass(ObserverMethodConfiguratorOperationsExtension.class)
                    .observedType(ObserverConfiguratorEvent.class)
                    .addQualifier(ObserverConfiguratorQualifierALiteral.INSTANCE)
                    .addQualifiers(Any.Literal.INSTANCE)
                    .qualifiers(ObserverConfiguratorQualifierBLiteral.INSTANCE)
                    .reception(Reception.IF_EXISTS)
                    .transactionPhase(TransactionPhase.AFTER_COMPLETION)
                    .priority(777)
                    .async(true)
                    .notifyWith(context ->
                            ObserverMethodConfiguratorRecorder.notifications.add(context.getEvent().id));
        }

        public void synthetic(@Observes ProcessSyntheticObserverMethod<?, ?> event) {
            ObserverMethod<?> observerMethod = event.getObserverMethod();
            ObserverMethodConfiguratorRecorder.beanClass = observerMethod.getBeanClass();
            ObserverMethodConfiguratorRecorder.observedType = observerMethod.getObservedType();
            ObserverMethodConfiguratorRecorder.qualifiers = new HashSet<Annotation>(observerMethod.getObservedQualifiers());
            ObserverMethodConfiguratorRecorder.reception = observerMethod.getReception();
            ObserverMethodConfiguratorRecorder.transactionPhase = observerMethod.getTransactionPhase();
            ObserverMethodConfiguratorRecorder.priority = observerMethod.getPriority();
            ObserverMethodConfiguratorRecorder.async = observerMethod.isAsync();
        }
    }

    public static class ObserverMethodConfiguratorReadMethodExtension implements Extension {
        public void after(@Observes AfterBeanDiscovery event) {
            try {
                Method method = ObserverMethodReadTemplates.class.getDeclaredMethod(
                        "readFromMethodTemplate",
                        String.class,
                        ObserverConfiguratorEvent.class
                );
                event.<ObserverConfiguratorEvent>addObserverMethod()
                        .read(method)
                        .notifyWith(context ->
                                ObserverMethodConfiguratorRecorder.notifications.add(context.getEvent().id));
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        public void synthetic(@Observes ProcessSyntheticObserverMethod<?, ?> event) {
            ObserverMethod<?> observerMethod = event.getObserverMethod();
            ObserverMethodConfiguratorRecorder.beanClass = observerMethod.getBeanClass();
            ObserverMethodConfiguratorRecorder.observedType = observerMethod.getObservedType();
            ObserverMethodConfiguratorRecorder.qualifiers = new HashSet<Annotation>(observerMethod.getObservedQualifiers());
            ObserverMethodConfiguratorRecorder.reception = observerMethod.getReception();
            ObserverMethodConfiguratorRecorder.transactionPhase = observerMethod.getTransactionPhase();
            ObserverMethodConfiguratorRecorder.priority = observerMethod.getPriority();
            ObserverMethodConfiguratorRecorder.async = observerMethod.isAsync();
        }
    }

    public static class ObserverMethodConfiguratorReadAnnotatedMethodExtension implements Extension {
        public void after(@Observes AfterBeanDiscovery event, BeanManager beanManager) {
            AnnotatedType<ObserverMethodReadTemplates> type =
                    beanManager.createAnnotatedType(ObserverMethodReadTemplates.class);
            jakarta.enterprise.inject.spi.AnnotatedMethod<? super ObserverMethodReadTemplates> target = null;
            for (jakarta.enterprise.inject.spi.AnnotatedMethod<? super ObserverMethodReadTemplates> method : type.getMethods()) {
                if ("readFromAnnotatedMethodTemplate".equals(method.getJavaMember().getName())) {
                    target = method;
                    break;
                }
            }
            if (target == null) {
                throw new IllegalStateException("Unable to find template annotated method");
            }
            event.<ObserverConfiguratorAsyncEvent>addObserverMethod()
                    .read(target)
                    .notifyWith(context ->
                            ObserverMethodConfiguratorRecorder.notifications.add(context.getEvent().id));
        }

        public void synthetic(@Observes ProcessSyntheticObserverMethod<?, ?> event) {
            ObserverMethod<?> observerMethod = event.getObserverMethod();
            ObserverMethodConfiguratorRecorder.beanClass = observerMethod.getBeanClass();
            ObserverMethodConfiguratorRecorder.observedType = observerMethod.getObservedType();
            ObserverMethodConfiguratorRecorder.qualifiers = new HashSet<Annotation>(observerMethod.getObservedQualifiers());
            ObserverMethodConfiguratorRecorder.reception = observerMethod.getReception();
            ObserverMethodConfiguratorRecorder.transactionPhase = observerMethod.getTransactionPhase();
            ObserverMethodConfiguratorRecorder.priority = observerMethod.getPriority();
            ObserverMethodConfiguratorRecorder.async = observerMethod.isAsync();
        }
    }

    public static class ObserverMethodConfiguratorReadObserverMethodExtension implements Extension {
        public void after(@Observes AfterBeanDiscovery event) {
            event.<ObserverConfiguratorEvent>addObserverMethod()
                    .read(new ExistingTemplateObserverMethod())
                    .notifyWith(context ->
                            ObserverMethodConfiguratorRecorder.notifications.add(context.getEvent().id));
        }

        public void synthetic(@Observes ProcessSyntheticObserverMethod<?, ?> event) {
            ObserverMethod<?> observerMethod = event.getObserverMethod();
            ObserverMethodConfiguratorRecorder.beanClass = observerMethod.getBeanClass();
            ObserverMethodConfiguratorRecorder.observedType = observerMethod.getObservedType();
            ObserverMethodConfiguratorRecorder.qualifiers = new HashSet<Annotation>(observerMethod.getObservedQualifiers());
            ObserverMethodConfiguratorRecorder.reception = observerMethod.getReception();
            ObserverMethodConfiguratorRecorder.transactionPhase = observerMethod.getTransactionPhase();
            ObserverMethodConfiguratorRecorder.priority = observerMethod.getPriority();
            ObserverMethodConfiguratorRecorder.async = observerMethod.isAsync();
        }
    }

    public static class AfterDeploymentValidationOrderingExtension implements Extension {
        public void afterBean(@Observes AfterBeanDiscovery event) {
            AfterDeploymentValidationOrderRecorder.events.add("after-bean-discovery");
        }

        public void afterDeployment(@Observes AfterDeploymentValidation event, BeanManager manager) {
            AfterDeploymentValidationOrderRecorder.capturedEvent = event;
            AfterDeploymentValidationOrderRecorder.beanManager = manager;
            AfterDeploymentValidationOrderRecorder.events.add("after-deployment-validation");
        }
    }

    public static class AfterDeploymentValidationAddProblemExtension implements Extension {
        public void first(@Observes @Priority(10) AfterDeploymentValidation event) {
            AfterDeploymentValidationProblemRecorder.events.add("observer-one");
            event.addDeploymentProblem(new IllegalStateException("forced-after-deployment-validation-problem"));
        }

        public void second(@Observes @Priority(20) AfterDeploymentValidation event) {
            AfterDeploymentValidationProblemRecorder.events.add("observer-two");
        }
    }

    public static class AfterDeploymentValidationThrowingExtension implements Extension {
        public void first(@Observes @Priority(10) AfterDeploymentValidation event) {
            AfterDeploymentValidationExceptionRecorder.events.add("throws");
            throw new IllegalStateException("forced-after-deployment-validation-exception");
        }

        public void second(@Observes @Priority(20) AfterDeploymentValidation event) {
            AfterDeploymentValidationExceptionRecorder.events.add("after-throw");
        }
    }

    public static class CapturedAfterDeploymentValidationExtension implements Extension {
        static AfterDeploymentValidation capturedAfterDeploymentValidation;

        static void reset() {
            capturedAfterDeploymentValidation = null;
        }

        public void afterDeployment(@Observes AfterDeploymentValidation event) {
            capturedAfterDeploymentValidation = event;
        }
    }

    public static class BlockingAfterDeploymentValidationExtension implements Extension {
        static CountDownLatch entered = new CountDownLatch(1);
        static CountDownLatch release = new CountDownLatch(1);

        static void reset() {
            entered = new CountDownLatch(1);
            release = new CountDownLatch(1);
        }

        public void afterDeployment(@Observes AfterDeploymentValidation event) {
            entered.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }

    public static class AfterDeploymentValidationBuildCompatibleOrderingExtension implements Extension {
        public void afterBean(@Observes AfterBeanDiscovery event) {
            AfterDeploymentValidationBuildCompatibleRecorder.events.add("after-bean-discovery");
        }

        public void afterDeployment(@Observes AfterDeploymentValidation event) {
            AfterDeploymentValidationBuildCompatibleRecorder.events.add("after-deployment-validation");
        }
    }

    public static class AfterDeploymentValidationTimingBuildCompatibleExtension implements BuildCompatibleExtension {
        @Validation
        public void validation() {
            AfterDeploymentValidationBuildCompatibleRecorder.events.add("build-compatible-validation");
        }
    }

    public static class BeforeShutdownOrderingExtension implements Extension {
        public void applicationDestroyed(@Observes @Destroyed(ApplicationScoped.class) Object event) {
            BeforeShutdownRecorder.applicationDestroyedObserved = true;
            BeforeShutdownRecorder.events.add("application-destroyed");
        }

        public void beforeShutdown(@Observes BeforeShutdown event, BeanManager manager) {
            BeforeShutdownRecorder.capturedEvent = event;
            BeforeShutdownRecorder.beanManager = manager;
            BeforeShutdownRecorder.applicationDestroyedAtBeforeShutdown = BeforeShutdownRecorder.applicationDestroyedObserved;
            BeforeShutdownRecorder.events.add("before-shutdown");
        }
    }

    public static class BeforeShutdownThrowingExtension implements Extension {
        public void first(@Observes @Priority(10) BeforeShutdown event) {
            BeforeShutdownExceptionRecorder.events.add("throws");
            throw new IllegalStateException("forced-before-shutdown-failure");
        }

        public void second(@Observes @Priority(20) BeforeShutdown event) {
            BeforeShutdownExceptionRecorder.events.add("after-throw");
        }
    }

    private static class TestInjectionPoint implements InjectionPoint {
        private final String id;

        private TestInjectionPoint(String id) {
            this.id = id;
        }

        @Override
        public Type getType() {
            return Object.class;
        }

        @Override
        public Set<Annotation> getQualifiers() {
            Set<Annotation> qualifiers = new HashSet<Annotation>();
            qualifiers.add(Default.Literal.INSTANCE);
            qualifiers.add(Any.Literal.INSTANCE);
            return qualifiers;
        }

        @Override
        public Bean<?> getBean() {
            return null;
        }

        @Override
        public Member getMember() {
            return null;
        }

        @Override
        public Annotated getAnnotated() {
            return null;
        }

        @Override
        public boolean isDelegate() {
            return false;
        }

        @Override
        public boolean isTransient() {
            return false;
        }

        @Override
        public int hashCode() {
            return id == null ? 0 : id.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TestInjectionPoint)) {
                return false;
            }
            TestInjectionPoint that = (TestInjectionPoint) other;
            if (id == null) {
                return that.id == null;
            }
            return id.equals(that.id);
        }
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    public @interface ObserverConfiguratorQualifierA {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    public @interface ObserverConfiguratorQualifierB {
    }

    public static class ObserverConfiguratorQualifierALiteral implements ObserverConfiguratorQualifierA {
        static final ObserverConfiguratorQualifierALiteral INSTANCE = new ObserverConfiguratorQualifierALiteral();

        @Override
        public Class<? extends Annotation> annotationType() {
            return ObserverConfiguratorQualifierA.class;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ObserverConfiguratorQualifierA;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

    public static class ObserverConfiguratorQualifierBLiteral implements ObserverConfiguratorQualifierB {
        static final ObserverConfiguratorQualifierBLiteral INSTANCE = new ObserverConfiguratorQualifierBLiteral();

        @Override
        public Class<? extends Annotation> annotationType() {
            return ObserverConfiguratorQualifierB.class;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ObserverConfiguratorQualifierB;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

    public static class ObserverConfiguratorEvent {
        private final String id;

        ObserverConfiguratorEvent(String id) {
            this.id = id;
        }
    }

    public static class ObserverConfiguratorAsyncEvent {
        private final String id;

        ObserverConfiguratorAsyncEvent(String id) {
            this.id = id;
        }
    }

    public static class ObserverMethodReadTemplates {
        public void readFromMethodTemplate(
                String ignored,
                @Observes(notifyObserver = Reception.IF_EXISTS, during = TransactionPhase.BEFORE_COMPLETION)
                @ObserverConfiguratorQualifierA
                ObserverConfiguratorEvent event) {
            // Template method for ObserverMethodConfigurator.read(Method)
        }

        public void readFromAnnotatedMethodTemplate(
                @ObservesAsync(notifyObserver = Reception.IF_EXISTS)
                @ObserverConfiguratorQualifierB
                ObserverConfiguratorAsyncEvent event) {
            // Template method for ObserverMethodConfigurator.read(AnnotatedMethod)
        }
    }

    public static class ExistingTemplateObserverMethod implements ObserverMethod<ObserverConfiguratorEvent> {
        @Override
        public Class<?> getBeanClass() {
            return ObserverMethodReadTemplates.class;
        }

        @Override
        public Type getObservedType() {
            return ObserverConfiguratorEvent.class;
        }

        @Override
        public Set<Annotation> getObservedQualifiers() {
            Set<Annotation> qualifiers = new HashSet<Annotation>();
            qualifiers.add(ObserverConfiguratorQualifierALiteral.INSTANCE);
            return qualifiers;
        }

        @Override
        public Reception getReception() {
            return Reception.IF_EXISTS;
        }

        @Override
        public TransactionPhase getTransactionPhase() {
            return TransactionPhase.BEFORE_COMPLETION;
        }

        @Override
        public int getPriority() {
            return 321;
        }

        @Override
        public boolean isAsync() {
            return false;
        }

        @Override
        public void notify(ObserverConfiguratorEvent event) {
            // no-op template
        }
    }

    public interface AtdAlternativeService {
        String id();
    }

    @Dependent
    public static class AtdDefaultAlternativeService implements AtdAlternativeService {
        @Override
        public String id() {
            return "atd-default-alternative";
        }
    }

    @Alternative
    @Priority(100)
    @Dependent
    public static class AtdLowPriorityAlternativeService implements AtdAlternativeService {
        @Override
        public String id() {
            return "atd-low-alternative";
        }
    }

    @Alternative
    @Priority(200)
    @Dependent
    public static class AtdHighPriorityAlternativeService implements AtdAlternativeService {
        @Override
        public String id() {
            return "atd-high-alternative";
        }
    }

    @Dependent
    public static class AtdAlternativeConsumer {
        @Inject
        AtdAlternativeService service;

        String serviceId() {
            return service.id();
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @jakarta.interceptor.InterceptorBinding
    public @interface AtdBinding {
    }

    @Dependent
    public static class AtdTargetBean {
        @AtdBinding
        public String invoke() {
            AfterTypeDiscoveryMutationRecorder.interceptorEvents.add("atd-target");
            return "ok";
        }
    }

    @AtdBinding
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION + 100)
    public static class AtdLowPriorityInterceptor {
        @AroundInvoke
        public Object around(InvocationContext ctx) throws Exception {
            AfterTypeDiscoveryMutationRecorder.interceptorEvents.add("atd-low-interceptor-before");
            try {
                return ctx.proceed();
            } finally {
                AfterTypeDiscoveryMutationRecorder.interceptorEvents.add("atd-low-interceptor-after");
            }
        }
    }

    @AtdBinding
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION + 200)
    public static class AtdHighPriorityInterceptor {
        @AroundInvoke
        public Object around(InvocationContext ctx) throws Exception {
            AfterTypeDiscoveryMutationRecorder.interceptorEvents.add("atd-high-interceptor-before");
            try {
                return ctx.proceed();
            } finally {
                AfterTypeDiscoveryMutationRecorder.interceptorEvents.add("atd-high-interceptor-after");
            }
        }
    }

    public interface AtdDecoratedContract {
        String decorate();
    }

    @Dependent
    public static class AtdDecoratedBase implements AtdDecoratedContract {
        @Override
        public String decorate() {
            return "base";
        }
    }

    @Decorator
    @Priority(100)
    public static class AtdLowPriorityDecorator implements AtdDecoratedContract {
        @Inject
        @Delegate
        AtdDecoratedContract delegate;

        @Override
        public String decorate() {
            return delegate.decorate() + "-atd-low-decorator";
        }
    }

    @Decorator
    @Priority(200)
    public static class AtdHighPriorityDecorator implements AtdDecoratedContract {
        @Inject
        @Delegate
        AtdDecoratedContract delegate;

        @Override
        public String decorate() {
            return delegate.decorate() + "-atd-high-decorator";
        }
    }

    @Dependent
    public static class AddedByAfterTypeDiscovery {
    }

    public static class AddedByAfterTypeDiscoveryConfigurator {
    }

    @Alternative
    @Dependent
    public static class NonPortableAtdAlternative {
    }

    @Interceptor
    @Dependent
    public static class NonPortableAtdInterceptor {
    }

    @Decorator
    public abstract static class NonPortableAtdDecorator {
        @Inject
        @Delegate
        AtdDecoratedContract delegate;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    public @interface DynamicQualifier {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface DynamicPseudoScope {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface DynamicStereotype {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface DynamicBinding {
    }

    public interface DynamicService {
        String id();
    }

    @Dependent
    public static class DynamicDefaultService implements DynamicService {
        @Override
        public String id() {
            return "default";
        }
    }

    @DynamicQualifier
    @Dependent
    public static class DynamicQualifiedService implements DynamicService {
        @Override
        public String id() {
            return "qualified";
        }
    }

    @Dependent
    public static class DynamicQualifierConsumer {
        @Inject
        @DynamicQualifier
        DynamicService service;

        String serviceName() {
            return service.id();
        }
    }

    @DynamicStereotype
    public static class DynamicStereotypedBean {
    }

    @Dependent
    public static class DynamicInterceptedBean {
        @DynamicBinding
        public String ping() {
            DynamicMetadataRecorder.interceptorEvents.add("dynamic-target");
            return "ok";
        }
    }

    @DynamicBinding
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION + 50)
    public static class DynamicBindingInterceptor {
        @AroundInvoke
        public Object around(InvocationContext ctx) throws Exception {
            DynamicMetadataRecorder.interceptorEvents.add("dynamic-interceptor-before");
            try {
                return ctx.proceed();
            } finally {
                DynamicMetadataRecorder.interceptorEvents.add("dynamic-interceptor-after");
            }
        }
    }

    @Dependent
    public static class AddedViaAnnotatedType {
    }

    public static class AddedViaAnnotatedTypeConfigurator {
    }

    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface TckAddedNormalScope {
    }

    @TckAddedNormalScope
    public static final class TckNormalScopeFinalBean {
    }

    public static class TckNormalScopeConsumer {
        @Inject
        TckNormalScopeFinalBean bean;
    }

    public static class TckAddingNormalScopeExtension implements Extension {
        public void before(@Observes BeforeBeanDiscovery event) {
            event.addScope(TckAddedNormalScope.class, true, false);
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    public @interface ConfiguratorQualifier {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface ConfiguratorBinding {
    }

    public interface ConfiguratorService {
        String id();
    }

    @Dependent
    public static class ConfiguratorDefaultService implements ConfiguratorService {
        @Override
        public String id() {
            return "configured-default";
        }
    }

    @ConfiguratorQualifier
    @Dependent
    public static class ConfiguratorQualifiedService implements ConfiguratorService {
        @Override
        public String id() {
            return "configured-qualified";
        }
    }

    @Dependent
    public static class ConfiguratorQualifierConsumer {
        @Inject
        @ConfiguratorQualifier
        ConfiguratorService service;

        String serviceName() {
            return service.id();
        }
    }

    @Dependent
    public static class ConfiguratorInterceptedBean {
        @ConfiguratorBinding
        public String ping() {
            ConfiguratorMetadataRecorder.interceptorEvents.add("configured-target");
            return "ok";
        }
    }

    @ConfiguratorBinding
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION + 60)
    public static class ConfiguratorBindingInterceptor {
        @AroundInvoke
        public Object around(InvocationContext ctx) throws Exception {
            ConfiguratorMetadataRecorder.interceptorEvents.add("configured-interceptor-before");
            try {
                return ctx.proceed();
            } finally {
                ConfiguratorMetadataRecorder.interceptorEvents.add("configured-interceptor-after");
            }
        }
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    public @interface LateAddedQualifier {
    }

    @Dependent
    public static class PatClass {
    }

    public interface PatInterface {
    }

    public enum PatEnum {
        VALUE
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface PatAnnotation {
    }

    @Vetoed
    public static class PatVetoedClass {
    }

    public static class ProcessAnnotatedTypeCaptureExtension implements Extension {
        public void pat(@Observes ProcessAnnotatedType<?> event) {
            ProcessAnnotatedTypeRecorder.types.add(event.getAnnotatedType().getJavaClass().getName());
        }
    }

    public static class SyntheticPatAddingExtension implements Extension {
        public void before(@Observes BeforeBeanDiscovery event, BeanManager beanManager) {
            event.addAnnotatedType(beanManager.createAnnotatedType(SyntheticBeforeType.class), "synthetic-before");
        }

        public void afterType(@Observes AfterTypeDiscovery event, BeanManager beanManager) {
            event.addAnnotatedType(beanManager.createAnnotatedType(SyntheticAfterType.class), "synthetic-after");
        }

        public void syntheticPat(@Observes ProcessSyntheticAnnotatedType<?> event) {
            ProcessSyntheticAnnotatedTypeRecorder.types.add(event.getAnnotatedType().getJavaClass().getName());
            ProcessSyntheticAnnotatedTypeRecorder.sources.add(event.getSource().getClass().getName());
        }
    }

    @Dependent
    public static class SyntheticBeforeType {
    }

    @Dependent
    public static class SyntheticAfterType {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
    public @interface PatImportant {
    }

    @PatImportant
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    public @interface PatMetaImportant {
    }

    @PatImportant
    @Dependent
    public static class WithAnnotatedTypeTarget {
    }

    @Dependent
    public static class WithAnnotatedFieldTarget {
        @PatImportant
        String value;
    }

    @Dependent
    public static class WithAnnotatedParameterTarget {
        void ping(@PatImportant String input) {
        }
    }

    @PatMetaImportant
    @Dependent
    public static class WithAnnotatedMetaTarget {
    }

    @Dependent
    public static class WithoutAnnotatedTarget {
    }

    public static class WithAnnotationsFilterExtension implements Extension {
        public void pat(@Observes @WithAnnotations(PatImportant.class) ProcessAnnotatedType<?> event) {
            WithAnnotationsFilterRecorder.types.add(event.getAnnotatedType().getJavaClass().getName());
        }
    }

    public static class InvalidWithAnnotationsPlacementExtension implements Extension {
        public void invalid(@Observes BeforeBeanDiscovery event,
                            @WithAnnotations(PatImportant.class) BeanManager beanManager) {
        }
    }

    public static class CapturedProcessAnnotatedTypeExtension implements Extension {
        static ProcessAnnotatedType<?> capturedEvent;

        static void reset() {
            capturedEvent = null;
        }

        public void pat(@Observes ProcessAnnotatedType<?> event) {
            if (capturedEvent == null) {
                capturedEvent = event;
            }
        }
    }

    public static class SetAndConfigurePatExtension implements Extension {
        @SuppressWarnings({"rawtypes", "unchecked"})
        public void pat(@Observes ProcessAnnotatedType<?> event) {
            ProcessAnnotatedType raw = event;
            raw.setAnnotatedType(event.getAnnotatedType());
            event.configureAnnotatedType();
        }
    }

    public static class ThrowingProcessAnnotatedTypeExtension implements Extension {
        public void pat(@Observes ProcessAnnotatedType<?> event) {
            throw new IllegalStateException("forced-process-annotated-type-failure");
        }
    }

    @Dependent
    public static class PatEnhancementTarget {
    }

    public static class ProcessAnnotatedTypeEnhancementOrderExtension implements Extension {
        public void pat(@Observes ProcessAnnotatedType<?> event) {
            if (PatEnhancementTarget.class.equals(event.getAnnotatedType().getJavaClass())) {
                ProcessAnnotatedTypeEnhancementOrderRecorder.events.add("process-annotated-type");
            }
        }

        public void atd(@Observes AfterTypeDiscovery event) {
            ProcessAnnotatedTypeEnhancementOrderRecorder.events.add("after-type-discovery");
        }
    }

    public static class ProcessAnnotatedTypeEnhancementBuildCompatibleExtension implements BuildCompatibleExtension {
        @Enhancement(types = PatEnhancementTarget.class, withSubtypes = false)
        public void enhancement(jakarta.enterprise.inject.build.compatible.spi.ClassConfig classConfig) {
            ProcessAnnotatedTypeEnhancementOrderRecorder.events.add("build-compatible-enhancement");
        }
    }

    @Dependent
    public static class PipDependency {
    }

    public interface PipContract {
        String ping();
    }

    @Dependent
    public static class PipBean {
        @Inject
        PipDependency dependency;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @jakarta.interceptor.InterceptorBinding
    public @interface PipBinding {
    }

    @PipBinding
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION + 70)
    public static class PipBindingInterceptor {
        @Inject
        PipDependency dependency;

        @AroundInvoke
        public Object around(InvocationContext ctx) throws Exception {
            return ctx.proceed();
        }
    }

    @Decorator
    @Priority(Interceptor.Priority.APPLICATION + 80)
    public abstract static class PipDecorator implements PipContract {
        @Inject
        @Delegate
        PipContract delegate;

        @Inject
        PipDependency dependency;

        @Override
        public String ping() {
            return delegate.ping();
        }
    }

    @Dependent
    public static class PipTargetBean implements PipContract {
        @PipBinding
        @Override
        public String ping() {
            return "ok";
        }
    }

    public static class ProcessInjectionPointCaptureExtension implements Extension {
        public void pip(@Observes ProcessInjectionPoint<?, ?> event) {
            InjectionPoint injectionPoint = event.getInjectionPoint();
            if (injectionPoint.getMember() != null && injectionPoint.getMember().getDeclaringClass() != null) {
                ProcessInjectionPointRecorder.beanClasses.add(injectionPoint.getMember().getDeclaringClass().getName());
            }
        }
    }

    public static class ProcessInjectionPointConfiguratorExtension implements Extension {
        public void pip(@Observes ProcessInjectionPoint<?, ?> event) {
            ProcessInjectionPointConfiguratorRecorder.sameInstance =
                    event.configureInjectionPoint() == event.configureInjectionPoint();
        }
    }

    public static class SetAndConfigureProcessInjectionPointExtension implements Extension {
        @SuppressWarnings({"rawtypes", "unchecked"})
        public void pip(@Observes ProcessInjectionPoint<?, ?> event) {
            ProcessInjectionPoint raw = event;
            raw.setInjectionPoint(event.getInjectionPoint());
            event.configureInjectionPoint();
        }
    }

    public static class ProcessInjectionPointAddDefinitionErrorExtension implements Extension {
        public void pip(@Observes ProcessInjectionPoint<?, ?> event) {
            event.addDefinitionError(new IllegalStateException("forced-process-injection-point-definition-error"));
        }
    }

    public static class ThrowingProcessInjectionPointExtension implements Extension {
        public void pip(@Observes ProcessInjectionPoint<?, ?> event) {
            throw new IllegalStateException("forced-process-injection-point-failure");
        }
    }

    public static class CapturedProcessInjectionPointExtension implements Extension {
        static ProcessInjectionPoint<?, ?> capturedEvent;

        static void reset() {
            capturedEvent = null;
        }

        public void pip(@Observes ProcessInjectionPoint<?, ?> event) {
            if (capturedEvent == null) {
                capturedEvent = event;
            }
        }
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    public @interface PipConfiguredQualifierA {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    public @interface PipConfiguredQualifierB {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    public @interface PipConfiguredQualifierC {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    public @interface PipConfiguredQualifierD {
    }

    public static class PipConfiguredQualifierALiteral implements PipConfiguredQualifierA {
        static final PipConfiguredQualifierALiteral INSTANCE = new PipConfiguredQualifierALiteral();

        @Override
        public Class<? extends Annotation> annotationType() {
            return PipConfiguredQualifierA.class;
        }
    }

    public static class PipConfiguredQualifierBLiteral implements PipConfiguredQualifierB {
        static final PipConfiguredQualifierBLiteral INSTANCE = new PipConfiguredQualifierBLiteral();

        @Override
        public Class<? extends Annotation> annotationType() {
            return PipConfiguredQualifierB.class;
        }
    }

    public static class PipConfiguredQualifierCLiteral implements PipConfiguredQualifierC {
        static final PipConfiguredQualifierCLiteral INSTANCE = new PipConfiguredQualifierCLiteral();

        @Override
        public Class<? extends Annotation> annotationType() {
            return PipConfiguredQualifierC.class;
        }
    }

    public static class PipConfiguredQualifierDLiteral implements PipConfiguredQualifierD {
        static final PipConfiguredQualifierDLiteral INSTANCE = new PipConfiguredQualifierDLiteral();

        @Override
        public Class<? extends Annotation> annotationType() {
            return PipConfiguredQualifierD.class;
        }
    }

    public static class ProcessInjectionPointConfiguratorOperationsExtension implements Extension {
        public void configure(@Observes @Priority(100) ProcessInjectionPoint<?, ?> event) {
            InjectionPoint injectionPoint = event.getInjectionPoint();
            if (!matchesDecoratorDependencyField(injectionPoint)) {
                return;
            }
            Set<Annotation> extra = new HashSet<Annotation>();
            extra.add(PipConfiguredQualifierDLiteral.INSTANCE);
            event.configureInjectionPoint()
                    .type(String.class)
                    .qualifiers(PipConfiguredQualifierALiteral.INSTANCE)
                    .addQualifier(PipConfiguredQualifierBLiteral.INSTANCE)
                    .addQualifiers(PipConfiguredQualifierCLiteral.INSTANCE, null)
                    .addQualifiers(extra)
                    .delegate(true)
                    .transientField(true);
        }

        public void verify(@Observes @Priority(200) ProcessInjectionPoint<?, ?> event) {
            InjectionPoint injectionPoint = event.getInjectionPoint();
            if (!matchesDecoratorDependencyField(injectionPoint)) {
                return;
            }

            ProcessInjectionPointConfiguratorOperationsRecorder.captured = true;
            ProcessInjectionPointConfiguratorOperationsRecorder.type = injectionPoint.getType();
            ProcessInjectionPointConfiguratorOperationsRecorder.delegate = injectionPoint.isDelegate();
            ProcessInjectionPointConfiguratorOperationsRecorder.transientField = injectionPoint.isTransient();
            for (Annotation qualifier : injectionPoint.getQualifiers()) {
                ProcessInjectionPointConfiguratorOperationsRecorder.qualifierTypes
                        .add(qualifier.annotationType().getName());
            }
        }

        private boolean matchesDecoratorDependencyField(InjectionPoint injectionPoint) {
            Member member = injectionPoint.getMember();
            if (member == null) {
                return false;
            }
            return member.getDeclaringClass().equals(PipDecorator.class) && "dependency".equals(member.getName());
        }
    }

    @Dependent
    public static class PitDependency {
    }

    public interface PitContract {
        String ping();
    }

    @Dependent
    public static class PitBean {
        @Inject
        PitDependency dependency;
    }

    @jakarta.interceptor.InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface PitBinding {
    }

    @PitBinding
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION + 90)
    public static class PitBindingInterceptor {
        @Inject
        PitDependency dependency;

        @AroundInvoke
        public Object around(InvocationContext ctx) throws Exception {
            return ctx.proceed();
        }
    }

    @Decorator
    @Priority(Interceptor.Priority.APPLICATION + 91)
    public abstract static class PitDecorator implements PitContract {
        @Inject
        @Delegate
        PitContract delegate;

        @Inject
        PitDependency dependency;

        @Override
        public String ping() {
            return delegate.ping();
        }
    }

    @Dependent
    public static class PitTargetBean implements PitContract {
        @PitBinding
        @Override
        public String ping() {
            return "pit-ok";
        }
    }

    public static class ProcessInjectionTargetCaptureExtension implements Extension {
        public void pit(@Observes ProcessInjectionTarget<?> event) {
            AnnotatedType<?> annotatedType = event.getAnnotatedType();
            if (annotatedType != null && annotatedType.getJavaClass() != null) {
                ProcessInjectionTargetRecorder.beanClasses.add(annotatedType.getJavaClass().getName());
            }
            assertNotNull(event.getInjectionTarget());
        }
    }

    public static class ProcessInjectionTargetFinalReplacementExtension implements Extension {
        public void first(@Observes @Priority(100) ProcessInjectionTarget<?> event) {
            if (!event.getAnnotatedType().getJavaClass().equals(PitBean.class)) {
                return;
            }
            InjectionTarget<?> original = event.getInjectionTarget();
            ProcessInjectionTargetReplacementRecorder.originalTarget = original;
            @SuppressWarnings({"rawtypes", "unchecked"})
            ProcessInjectionTarget rawEvent = event;
            rawEvent.setInjectionTarget(new RecordingInjectionTarget<Object>((InjectionTarget<Object>) original, "first"));
        }

        public void second(@Observes @Priority(200) ProcessInjectionTarget<?> event) {
            if (!event.getAnnotatedType().getJavaClass().equals(PitBean.class)) {
                return;
            }
            @SuppressWarnings("unchecked")
            InjectionTarget<Object> original = (InjectionTarget<Object>) ProcessInjectionTargetReplacementRecorder.originalTarget;
            @SuppressWarnings({"rawtypes", "unchecked"})
            ProcessInjectionTarget rawEvent = event;
            rawEvent.setInjectionTarget(new RecordingInjectionTarget<Object>(original, "final"));
        }
    }

    public static class ProcessInjectionTargetAddDefinitionErrorExtension implements Extension {
        public void pit(@Observes ProcessInjectionTarget<?> event) {
            event.addDefinitionError(new IllegalStateException("forced-process-injection-target-definition-error"));
        }
    }

    public static class ThrowingProcessInjectionTargetExtension implements Extension {
        public void pit(@Observes ProcessInjectionTarget<?> event) {
            throw new IllegalStateException("forced-process-injection-target-failure");
        }
    }

    public static class CapturedProcessInjectionTargetExtension implements Extension {
        static ProcessInjectionTarget<?> capturedEvent;

        static void reset() {
            capturedEvent = null;
        }

        public void pit(@Observes ProcessInjectionTarget<?> event) {
            if (capturedEvent == null) {
                capturedEvent = event;
            }
        }
    }

    private static class RecordingInjectionTarget<T> implements InjectionTarget<T> {
        private final InjectionTarget<T> delegate;
        private final String marker;

        private RecordingInjectionTarget(InjectionTarget<T> delegate, String marker) {
            this.delegate = delegate;
            this.marker = marker;
        }

        @Override
        public T produce(CreationalContext<T> ctx) {
            mark();
            return delegate.produce(ctx);
        }

        @Override
        public void inject(T instance, CreationalContext<T> ctx) {
            mark();
            delegate.inject(instance, ctx);
        }

        @Override
        public void postConstruct(T instance) {
            mark();
            delegate.postConstruct(instance);
        }

        @Override
        public void preDestroy(T instance) {
            delegate.preDestroy(instance);
        }

        @Override
        public void dispose(T instance) {
            delegate.dispose(instance);
        }

        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            return delegate.getInjectionPoints();
        }

        private void mark() {
            if ("first".equals(marker)) {
                ProcessInjectionTargetReplacementRecorder.firstReplacementUsed = true;
            }
            if ("final".equals(marker)) {
                ProcessInjectionTargetReplacementRecorder.finalReplacementUsed = true;
            }
        }
    }

    @Dependent
    public static class PbaDependency {
    }

    @Dependent
    public static class PbaManagedBean {
        @Inject
        PbaDependency dependency;
    }

    @Dependent
    public static class PbaProducerHost {
        @Produces
        PbaProducedByMethod produceByMethod() {
            return new PbaProducedByMethod();
        }

        @Produces
        PbaProducedByField producedField = new PbaProducedByField();
    }

    @Dependent
    public static class PbaProducedByMethod {
    }

    @Dependent
    public static class PbaProducedByField {
    }

    public interface PbaContract {
        String ping();
    }

    @jakarta.interceptor.InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface PbaBinding {
    }

    @PbaBinding
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION + 92)
    public static class PbaBindingInterceptor {
        @Inject
        PbaDependency dependency;

        @AroundInvoke
        public Object around(InvocationContext ctx) throws Exception {
            return ctx.proceed();
        }
    }

    @Decorator
    @Priority(Interceptor.Priority.APPLICATION + 93)
    public abstract static class PbaDecorator implements PbaContract {
        @Inject
        @Delegate
        PbaContract delegate;

        @Override
        public String ping() {
            return delegate.ping();
        }
    }

    @Dependent
    public static class PbaDecoratedBean implements PbaContract {
        @PbaBinding
        @Override
        public String ping() {
            return "pba";
        }
    }

    public static class PbaSyntheticBean {
    }

    @ApplicationScoped
    public static class PbaFinalMethodBean {
        public final String ping() {
            return "final";
        }
    }

    @Dependent
    public static class PbaFinalMethodConsumer {
        @Inject
        PbaFinalMethodBean bean;
    }

    public interface Pba91ExtraType {
    }

    public interface Pba91HierarchyContract {
    }

    public static class Pba91HierarchyParent {
    }

    public static class Pba91HierarchyChild extends Pba91HierarchyParent implements Pba91HierarchyContract {
    }

    public interface Pba91ConfiguredContract {
    }

    public interface Pba91RemovedType {
    }

    @Dependent
    public static class Pba91ManagedBean {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    public @interface Pba91QualifierA {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    public @interface Pba91QualifierB {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    public @interface Pba91QualifierC {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    public @interface Pba91QualifierD {
    }

    public static class Pba91QualifierALiteral implements Pba91QualifierA {
        static final Pba91QualifierALiteral INSTANCE = new Pba91QualifierALiteral();

        @Override
        public Class<? extends Annotation> annotationType() {
            return Pba91QualifierA.class;
        }
    }

    public static class Pba91QualifierBLiteral implements Pba91QualifierB {
        static final Pba91QualifierBLiteral INSTANCE = new Pba91QualifierBLiteral();

        @Override
        public Class<? extends Annotation> annotationType() {
            return Pba91QualifierB.class;
        }
    }

    public static class Pba91QualifierCLiteral implements Pba91QualifierC {
        static final Pba91QualifierCLiteral INSTANCE = new Pba91QualifierCLiteral();

        @Override
        public Class<? extends Annotation> annotationType() {
            return Pba91QualifierC.class;
        }
    }

    public static class Pba91QualifierDLiteral implements Pba91QualifierD {
        static final Pba91QualifierDLiteral INSTANCE = new Pba91QualifierDLiteral();

        @Override
        public Class<? extends Annotation> annotationType() {
            return Pba91QualifierD.class;
        }
    }

    @jakarta.enterprise.inject.Stereotype
    @Dependent
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Pba91StereotypeA {
    }

    @jakarta.enterprise.inject.Stereotype
    @Dependent
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Pba91StereotypeB {
    }

    @jakarta.enterprise.inject.Stereotype
    @Dependent
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Pba91StereotypeC {
    }

    public static class ProcessBeanAttributesConfiguratorTypesExtension implements Extension {
        public void pba(@Observes ProcessBeanAttributes<?> event) {
            if (!(event.getAnnotated() instanceof AnnotatedType<?>)) {
                return;
            }
            Class<?> beanClass = ((AnnotatedType<?>) event.getAnnotated()).getJavaClass();
            if (!Pba91ManagedBean.class.equals(beanClass)) {
                return;
            }
            ProcessBeanAttributesConfigurator91Recorder.sameInstance =
                    event.configureBeanAttributes() == event.configureBeanAttributes();
            event.configureBeanAttributes()
                    .addType(Pba91ExtraType.class)
                    .addTransitiveTypeClosure(Pba91HierarchyChild.class);
        }
    }

    public static class ProcessBeanAttributesConfiguratorMetadataExtension implements Extension {
        public void pba(@Observes @Priority(100) ProcessBeanAttributes<?> event) {
            if (!(event.getAnnotated() instanceof AnnotatedType<?>)) {
                return;
            }
            Class<?> beanClass = ((AnnotatedType<?>) event.getAnnotated()).getJavaClass();
            if (!Pba91ManagedBean.class.equals(beanClass)) {
                return;
            }

            Set<Annotation> qualifiers = new HashSet<Annotation>();
            qualifiers.add(Pba91QualifierDLiteral.INSTANCE);
            Set<Class<? extends Annotation>> initialStereotypes = new HashSet<Class<? extends Annotation>>();
            initialStereotypes.add(Pba91StereotypeA.class);
            Set<Class<? extends Annotation>> extraStereotypes = new HashSet<Class<? extends Annotation>>();
            extraStereotypes.add(Pba91StereotypeC.class);

            event.configureBeanAttributes()
                    .addType(Pba91RemovedType.class)
                    .types(Pba91ManagedBean.class, Pba91ConfiguredContract.class, Object.class)
                    .scope(ApplicationScoped.class)
                    .qualifiers(Pba91QualifierALiteral.INSTANCE)
                    .addQualifier(Pba91QualifierBLiteral.INSTANCE)
                    .addQualifiers(Pba91QualifierCLiteral.INSTANCE, null)
                    .addQualifiers(qualifiers)
                    .stereotypes(initialStereotypes)
                    .addStereotype(Pba91StereotypeB.class)
                    .addStereotypes(extraStereotypes)
                    .name("pba91Managed")
                    .alternative(true);
        }

        public void verify(@Observes @Priority(200) ProcessBeanAttributes<?> event) {
            if (!(event.getAnnotated() instanceof AnnotatedType<?>)) {
                return;
            }
            Class<?> beanClass = ((AnnotatedType<?>) event.getAnnotated()).getJavaClass();
            if (!Pba91ManagedBean.class.equals(beanClass)) {
                return;
            }
            BeanAttributes<?> beanAttributes = event.getBeanAttributes();
            ProcessBeanAttributesConfigurator91Recorder.metadataCaptured = true;
            ProcessBeanAttributesConfigurator91Recorder.scope = beanAttributes.getScope();
            ProcessBeanAttributesConfigurator91Recorder.name = beanAttributes.getName();
            ProcessBeanAttributesConfigurator91Recorder.alternative = beanAttributes.isAlternative();
            ProcessBeanAttributesConfigurator91Recorder.types.clear();
            ProcessBeanAttributesConfigurator91Recorder.types.addAll(beanAttributes.getTypes());
            ProcessBeanAttributesConfigurator91Recorder.stereotypes.clear();
            ProcessBeanAttributesConfigurator91Recorder.stereotypes.addAll(beanAttributes.getStereotypes());
            ProcessBeanAttributesConfigurator91Recorder.qualifierTypes.clear();
            for (Annotation qualifier : beanAttributes.getQualifiers()) {
                ProcessBeanAttributesConfigurator91Recorder.qualifierTypes
                        .add(qualifier.annotationType().getName());
            }
        }
    }

    @Dependent
    public static class Pb10Dependency {
    }

    @Dependent
    public static class Pb10ManagedBean {
        @Inject
        Pb10Dependency dependency;

        public String ping() {
            return "pb10";
        }
    }

    @Dependent
    public static class Pb10ProducerHost {
        @Produces
        Pb10ProducedByMethod produceByMethod() {
            return new Pb10ProducedByMethod();
        }

        @Produces
        Pb10ProducedByField producedField = new Pb10ProducedByField();
    }

    @Dependent
    public static class Pb10ProducedByMethod {
    }

    @Dependent
    public static class Pb10ProducedByField {
    }

    public interface Pb10Contract {
        String ping();
    }

    @jakarta.interceptor.InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface Pb10Binding {
    }

    @Pb10Binding
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION + 94)
    public static class Pb10BindingInterceptor {
        @Inject
        Pb10Dependency dependency;

        @AroundInvoke
        public Object around(InvocationContext ctx) throws Exception {
            return ctx.proceed();
        }
    }

    @Decorator
    @Priority(Interceptor.Priority.APPLICATION + 95)
    public abstract static class Pb10Decorator implements Pb10Contract {
        @Inject
        @Delegate
        Pb10Contract delegate;

        @Override
        public String ping() {
            return delegate.ping();
        }
    }

    @Dependent
    public static class Pb10DecoratedBean implements Pb10Contract {
        @Pb10Binding
        @Override
        public String ping() {
            return "pb10-decorated";
        }
    }

    public static class Pb10SyntheticBean {
    }

    private static class Pb10SyntheticCustomBean implements Bean<Pb10SyntheticBean> {
        @Override
        public Class<?> getBeanClass() {
            return Pb10SyntheticBean.class;
        }

        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            return Collections.emptySet();
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public Set<Annotation> getQualifiers() {
            Set<Annotation> qualifiers = new HashSet<Annotation>();
            qualifiers.add(Default.Literal.INSTANCE);
            qualifiers.add(Any.Literal.INSTANCE);
            return qualifiers;
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
            Set<Type> types = new HashSet<Type>();
            types.add(Pb10SyntheticBean.class);
            types.add(Object.class);
            return types;
        }

        @Override
        public boolean isAlternative() {
            return false;
        }

        @Override
        public Pb10SyntheticBean create(CreationalContext<Pb10SyntheticBean> context) {
            return new Pb10SyntheticBean();
        }

        @Override
        public void destroy(Pb10SyntheticBean instance, CreationalContext<Pb10SyntheticBean> context) {
            if (context != null) {
                context.release();
            }
        }
    }

    public static class ProcessBean10CaptureAndSyntheticExtension implements Extension {
        public void pba(@Observes ProcessBeanAttributes<?> event) {
            if (!(event.getAnnotated() instanceof AnnotatedType<?>)) {
                return;
            }
            Class<?> beanClass = ((AnnotatedType<?>) event.getAnnotated()).getJavaClass();
            if (Pb10ManagedBean.class.equals(beanClass)) {
                ProcessBean10Recorder.events.add("pba-managed");
            }
        }

        public void managed(@Observes ProcessManagedBean<?> event) {
            Class<?> beanClass = event.getBean().getBeanClass();
            if (Pb10ManagedBean.class.equals(beanClass)) {
                ProcessBean10Recorder.sawManagedBean = true;
            }
            if (Pb10BindingInterceptor.class.equals(beanClass)) {
                ProcessBean10Recorder.sawInterceptorManagedBean = true;
            }
            if (Pb10Decorator.class.equals(beanClass)) {
                ProcessBean10Recorder.sawDecoratorManagedBean = true;
            }
        }

        public void producerMethod(@Observes ProcessProducerMethod<?, ?> event) {
            if (event.getAnnotatedProducerMethod() != null &&
                    Pb10ProducerHost.class.equals(event.getAnnotatedProducerMethod().getJavaMember().getDeclaringClass()) &&
                    "produceByMethod".equals(event.getAnnotatedProducerMethod().getJavaMember().getName())) {
                ProcessBean10Recorder.sawProducerMethod = true;
            }
        }

        public void producerField(@Observes ProcessProducerField<?, ?> event) {
            if (event.getAnnotatedProducerField() != null &&
                    Pb10ProducerHost.class.equals(event.getAnnotatedProducerField().getJavaMember().getDeclaringClass()) &&
                    "producedField".equals(event.getAnnotatedProducerField().getJavaMember().getName())) {
                ProcessBean10Recorder.sawProducerField = true;
            }
        }

        public void processBean(@Observes ProcessBean<?> event) {
            Bean<?> bean = event.getBean();
            Class<?> beanClass = bean.getBeanClass();
            if (Pb10ManagedBean.class.equals(beanClass)) {
                ProcessBean10Recorder.events.add("pb-managed");
                ProcessBean10Recorder.sawPbaBeforeProcessBeanForManaged =
                        ProcessBean10Recorder.events.indexOf("pba-managed") >= 0 &&
                        ProcessBean10Recorder.events.indexOf("pba-managed") <
                                ProcessBean10Recorder.events.indexOf("pb-managed");
            }
            if (BeanManager.class.equals(beanClass) ||
                    InjectionPoint.class.equals(beanClass) ||
                    jakarta.enterprise.context.Conversation.class.equals(beanClass) ||
                    jakarta.enterprise.context.control.RequestContextController.class.equals(beanClass)) {
                ProcessBean10Recorder.sawBuiltInBean = true;
            }
        }

        public void synthetic(@Observes ProcessSyntheticBean<?> event) {
            if (Pb10SyntheticBean.class.equals(event.getBean().getBeanClass())) {
                ProcessBean10Recorder.sawSyntheticBean = true;
            }
        }

        public void after(@Observes AfterBeanDiscovery event) {
            event.addBean(new Pb10SyntheticCustomBean());
        }
    }

    public static class ProcessBean10ApiExtension implements Extension {
        public void managed(@Observes ProcessManagedBean<?> event) {
            if (!Pb10ManagedBean.class.equals(event.getBean().getBeanClass())) {
                return;
            }
            ProcessBean10ApiRecorder.managedSeen = true;
            ProcessBean10ApiRecorder.managedAnnotatedBeanClassMatches =
                    Pb10ManagedBean.class.equals(event.getAnnotatedBeanClass().getJavaClass());
            for (jakarta.enterprise.inject.spi.AnnotatedMethod<?> method : event.getAnnotatedBeanClass().getMethods()) {
                if ("ping".equals(method.getJavaMember().getName())) {
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    ProcessManagedBean raw = event;
                    ProcessBean10ApiRecorder.managedInvokerBuilderCreated =
                            raw.createInvoker((jakarta.enterprise.inject.spi.AnnotatedMethod) method) != null;
                    break;
                }
            }
        }

        public void producerMethod(@Observes ProcessProducerMethod<?, ?> event) {
            if (event.getAnnotatedProducerMethod() == null) {
                return;
            }
            Method method = event.getAnnotatedProducerMethod().getJavaMember();
            if (Pb10ProducerHost.class.equals(method.getDeclaringClass()) && "produceByMethod".equals(method.getName())) {
                ProcessBean10ApiRecorder.producerMethodSeen = true;
                ProcessBean10ApiRecorder.producerMethodAnnotatedMatches = true;
                ProcessBean10ApiRecorder.producerMethodDisposedParameterNull =
                        event.getAnnotatedDisposedParameter() == null;
            }
        }

        public void producerField(@Observes ProcessProducerField<?, ?> event) {
            if (event.getAnnotatedProducerField() == null) {
                return;
            }
            Field field = event.getAnnotatedProducerField().getJavaMember();
            if (Pb10ProducerHost.class.equals(field.getDeclaringClass()) && "producedField".equals(field.getName())) {
                ProcessBean10ApiRecorder.producerFieldSeen = true;
                ProcessBean10ApiRecorder.producerFieldAnnotatedMatches = true;
                ProcessBean10ApiRecorder.producerFieldDisposedParameterNull =
                        event.getAnnotatedDisposedParameter() == null;
            }
        }

        public void synthetic(@Observes ProcessSyntheticBean<?> event) {
            if (!Pb10SyntheticBean.class.equals(event.getBean().getBeanClass())) {
                return;
            }
            ProcessBean10ApiRecorder.syntheticSeen = true;
            ProcessBean10ApiRecorder.syntheticSourceCaptured = event.getSource() != null;
        }

        public void after(@Observes AfterBeanDiscovery event) {
            event.addBean(new Pb10SyntheticCustomBean());
        }
    }

    public static class ProcessBean10AddDefinitionErrorExtension implements Extension {
        public void processBean(@Observes ProcessBean<?> event) {
            if (Pb10ManagedBean.class.equals(event.getBean().getBeanClass())) {
                event.addDefinitionError(new IllegalStateException("forced-process-bean-definition-error"));
            }
        }
    }

    public static class ThrowingProcessBean10Extension implements Extension {
        public void processBean(@Observes ProcessBean<?> event) {
            if (Pb10ManagedBean.class.equals(event.getBean().getBeanClass())) {
                throw new IllegalStateException("forced-process-bean-failure");
            }
        }
    }

    public static class CapturedProcessBean10Extension implements Extension {
        static ProcessBean<?> capturedProcessBean;
        static ProcessManagedBean<?> capturedProcessManagedBean;
        static ProcessProducerMethod<?, ?> capturedProcessProducerMethod;
        static ProcessProducerField<?, ?> capturedProcessProducerField;
        static ProcessSyntheticBean<?> capturedProcessSyntheticBean;

        static void reset() {
            capturedProcessBean = null;
            capturedProcessManagedBean = null;
            capturedProcessProducerMethod = null;
            capturedProcessProducerField = null;
            capturedProcessSyntheticBean = null;
        }

        public void processBean(@Observes ProcessBean<?> event) {
            if (capturedProcessBean == null) {
                capturedProcessBean = event;
            }
        }

        public void managed(@Observes ProcessManagedBean<?> event) {
            if (capturedProcessManagedBean == null && Pb10ManagedBean.class.equals(event.getBean().getBeanClass())) {
                capturedProcessManagedBean = event;
            }
        }

        public void producerMethod(@Observes ProcessProducerMethod<?, ?> event) {
            if (capturedProcessProducerMethod == null &&
                    event.getAnnotatedProducerMethod() != null &&
                    Pb10ProducerHost.class.equals(event.getAnnotatedProducerMethod().getJavaMember().getDeclaringClass())) {
                capturedProcessProducerMethod = event;
            }
        }

        public void producerField(@Observes ProcessProducerField<?, ?> event) {
            if (capturedProcessProducerField == null &&
                    event.getAnnotatedProducerField() != null &&
                    Pb10ProducerHost.class.equals(event.getAnnotatedProducerField().getJavaMember().getDeclaringClass())) {
                capturedProcessProducerField = event;
            }
        }

        public void synthetic(@Observes ProcessSyntheticBean<?> event) {
            if (capturedProcessSyntheticBean == null &&
                    Pb10SyntheticBean.class.equals(event.getBean().getBeanClass())) {
                capturedProcessSyntheticBean = event;
            }
        }

        public void after(@Observes AfterBeanDiscovery event) {
            event.addBean(new Pb10SyntheticCustomBean());
        }
    }

    @Vetoed
    public static class Pp11ProducedByMethod {
        String source;

        Pp11ProducedByMethod() {
            this("original-method");
        }

        Pp11ProducedByMethod(String source) {
            this.source = source;
        }
    }

    @Vetoed
    public static class Pp11ProducedByField {
        String source;

        Pp11ProducedByField() {
            this("original-field");
        }

        Pp11ProducedByField(String source) {
            this.source = source;
        }
    }

    @Dependent
    public static class Pp11ProducerHost {
        @Produces
        Pp11ProducedByMethod produceByMethod() {
            return new Pp11ProducedByMethod();
        }

        @Produces
        Pp11ProducedByField producedField = new Pp11ProducedByField();
    }

    @Dependent
    public static class Pp11Consumer {
        @Inject
        Pp11ProducedByMethod byMethod;

        @Inject
        Pp11ProducedByField byField;
    }

    private static class ProducerReplacingWrapper<T> implements Producer<T> {
        private final Producer<T> delegate;
        private final T replacement;
        private final String marker;

        private ProducerReplacingWrapper(Producer<T> delegate, T replacement, String marker) {
            this.delegate = delegate;
            this.replacement = replacement;
            this.marker = marker;
        }

        @Override
        public T produce(CreationalContext<T> ctx) {
            if ("first".equals(marker)) {
                ProcessProducer11ReplacementRecorder.firstReplacementUsed = true;
            }
            if ("final".equals(marker)) {
                ProcessProducer11ReplacementRecorder.finalReplacementUsed = true;
            }
            return replacement;
        }

        @Override
        public void dispose(T instance) {
            delegate.dispose(instance);
        }

        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            return delegate.getInjectionPoints();
        }
    }

    private static class DummyProducer<T> implements Producer<T> {
        @Override
        public T produce(CreationalContext<T> ctx) {
            return null;
        }

        @Override
        public void dispose(T instance) {
        }

        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            return Collections.emptySet();
        }
    }

    public static class ProcessProducer11CaptureExtension implements Extension {
        public void pp(@Observes ProcessProducer<?, ?> event) {
            AnnotatedMember<?> member = event.getAnnotatedMember();
            if (member instanceof jakarta.enterprise.inject.spi.AnnotatedMethod<?>) {
                Method method = ((jakarta.enterprise.inject.spi.AnnotatedMethod<?>) member).getJavaMember();
                if (Pp11ProducerHost.class.equals(method.getDeclaringClass()) &&
                        "produceByMethod".equals(method.getName())) {
                    ProcessProducer11Recorder.sawMethodProducerEvent = true;
                }
            }
            if (member instanceof jakarta.enterprise.inject.spi.AnnotatedField<?>) {
                Field field = ((jakarta.enterprise.inject.spi.AnnotatedField<?>) member).getJavaMember();
                if (Pp11ProducerHost.class.equals(field.getDeclaringClass()) &&
                        "producedField".equals(field.getName())) {
                    ProcessProducer11Recorder.sawFieldProducerEvent = true;
                }
            }
        }
    }

    public static class ProcessProducer11FinalReplacementExtension implements Extension {
        @SuppressWarnings({"rawtypes", "unchecked"})
        public void first(@Observes @Priority(100) ProcessProducer<?, ?> event) {
            AnnotatedMember<?> member = event.getAnnotatedMember();
            if (!(member instanceof jakarta.enterprise.inject.spi.AnnotatedMethod<?>)) {
                return;
            }
            Method method = ((jakarta.enterprise.inject.spi.AnnotatedMethod<?>) member).getJavaMember();
            if (!Pp11ProducerHost.class.equals(method.getDeclaringClass()) ||
                    !"produceByMethod".equals(method.getName())) {
                return;
            }
            ProcessProducer raw = event;
            Producer current = raw.getProducer();
            raw.setProducer(new ProducerReplacingWrapper(current, new Pp11ProducedByMethod("first-method"), "first"));
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        public void second(@Observes @Priority(200) ProcessProducer<?, ?> event) {
            AnnotatedMember<?> member = event.getAnnotatedMember();
            if (!(member instanceof jakarta.enterprise.inject.spi.AnnotatedMethod<?>)) {
                return;
            }
            Method method = ((jakarta.enterprise.inject.spi.AnnotatedMethod<?>) member).getJavaMember();
            if (!Pp11ProducerHost.class.equals(method.getDeclaringClass()) ||
                    !"produceByMethod".equals(method.getName())) {
                return;
            }
            ProcessProducer raw = event;
            Producer current = raw.getProducer();
            raw.setProducer(new ProducerReplacingWrapper(current, new Pp11ProducedByMethod("final-method"), "final"));
        }
    }

    public static class ProcessProducer11ConfigureExtension implements Extension {
        @SuppressWarnings({"rawtypes", "unchecked"})
        public void configure(@Observes ProcessProducer<?, ?> event) {
            AnnotatedMember<?> member = event.getAnnotatedMember();
            if (!(member instanceof jakarta.enterprise.inject.spi.AnnotatedField<?>)) {
                return;
            }
            Field field = ((jakarta.enterprise.inject.spi.AnnotatedField<?>) member).getJavaMember();
            if (!Pp11ProducerHost.class.equals(field.getDeclaringClass()) ||
                    !"producedField".equals(field.getName())) {
                return;
            }
            ProcessProducer11ConfiguratorRecorder.sameInstance =
                    event.configureProducer() == event.configureProducer();
            ProcessProducer raw = event;
            raw.configureProducer().produceWith(ctx -> new Pp11ProducedByField("configured-field"));
        }
    }

    public static class SetAndConfigureProcessProducer11Extension implements Extension {
        @SuppressWarnings({"rawtypes", "unchecked"})
        public void pp(@Observes ProcessProducer<?, ?> event) {
            AnnotatedMember<?> member = event.getAnnotatedMember();
            if (!(member instanceof jakarta.enterprise.inject.spi.AnnotatedMethod<?>)) {
                return;
            }
            Method method = ((jakarta.enterprise.inject.spi.AnnotatedMethod<?>) member).getJavaMember();
            if (!Pp11ProducerHost.class.equals(method.getDeclaringClass()) ||
                    !"produceByMethod".equals(method.getName())) {
                return;
            }
            ProcessProducer raw = event;
            raw.setProducer(raw.getProducer());
            raw.configureProducer();
        }
    }

    public static class ProcessProducer11AddDefinitionErrorExtension implements Extension {
        public void pp(@Observes ProcessProducer<?, ?> event) {
            event.addDefinitionError(new IllegalStateException("forced-process-producer-definition-error"));
        }
    }

    public static class ThrowingProcessProducer11Extension implements Extension {
        public void pp(@Observes ProcessProducer<?, ?> event) {
            throw new IllegalStateException("forced-process-producer-failure");
        }
    }

    public static class CapturedProcessProducer11Extension implements Extension {
        static ProcessProducer<?, ?> capturedEvent;

        static void reset() {
            capturedEvent = null;
        }

        public void pp(@Observes ProcessProducer<?, ?> event) {
            if (capturedEvent == null) {
                capturedEvent = event;
            }
        }
    }

    @Vetoed
    public static class Pp111Produced {
        String source;

        Pp111Produced() {
            this("original-produce");
        }

        Pp111Produced(String source) {
            this.source = source;
        }
    }

    @Dependent
    public static class Pp111ProducerHost {
        @Produces
        Pp111Produced produce() {
            ProcessProducer111ConfiguratorRecorder.originalProduceCalls++;
            return new Pp111Produced("original-produce");
        }

        void dispose(@Disposes Pp111Produced produced) {
            if (produced != null) {
                ProcessProducer111ConfiguratorRecorder.originalDisposeCalls++;
            }
        }
    }

    @Dependent
    public static class Pp111Consumer {
        @Inject
        Pp111Produced value;
    }

    public static class ProcessProducer111ProduceWithExtension implements Extension {
        @SuppressWarnings({"rawtypes", "unchecked"})
        public void pp(@Observes ProcessProducer<?, ?> event) {
            AnnotatedMember<?> member = event.getAnnotatedMember();
            if (!(member instanceof jakarta.enterprise.inject.spi.AnnotatedMethod<?>)) {
                return;
            }
            Method method = ((jakarta.enterprise.inject.spi.AnnotatedMethod<?>) member).getJavaMember();
            if (!Pp111ProducerHost.class.equals(method.getDeclaringClass()) ||
                    !"produce".equals(method.getName())) {
                return;
            }
            ProcessProducer raw = event;
            raw.configureProducer().produceWith(ctx -> {
                ProcessProducer111ConfiguratorRecorder.configuredProduceCalls++;
                return new Pp111Produced("configured-produce");
            });
        }
    }

    public static class ProcessProducer111DisposeWithExtension implements Extension {
        @SuppressWarnings({"rawtypes", "unchecked"})
        public void pp(@Observes ProcessProducer<?, ?> event) {
            AnnotatedMember<?> member = event.getAnnotatedMember();
            if (!(member instanceof jakarta.enterprise.inject.spi.AnnotatedMethod<?>)) {
                return;
            }
            Method method = ((jakarta.enterprise.inject.spi.AnnotatedMethod<?>) member).getJavaMember();
            if (!Pp111ProducerHost.class.equals(method.getDeclaringClass()) ||
                    !"produce".equals(method.getName())) {
                return;
            }
            ProcessProducer raw = event;
            raw.configureProducer().disposeWith(instance ->
                    ProcessProducer111ConfiguratorRecorder.configuredDisposeCalls++);
        }
    }

    public static class Pom12Event {
        final String id;

        Pom12Event(String id) {
            this.id = id;
        }
    }

    @Dependent
    public static class Pom12ObservedBean {
        void observe(@Observes Pom12Event event) {
            if (event != null) {
                ProcessObserverMethod12Recorder.originalManagedObserverNotified = true;
            }
        }
    }

    private static class Pom12FixedObserverMethod implements ObserverMethod<Pom12Event> {
        private final String marker;

        private Pom12FixedObserverMethod(String marker) {
            this.marker = marker;
        }

        @Override
        public Class<?> getBeanClass() {
            return Pom12ObservedBean.class;
        }

        @Override
        public Type getObservedType() {
            return Pom12Event.class;
        }

        @Override
        public Set<Annotation> getObservedQualifiers() {
            Set<Annotation> qualifiers = new HashSet<Annotation>();
            qualifiers.add(Any.Literal.INSTANCE);
            return qualifiers;
        }

        @Override
        public Reception getReception() {
            return Reception.ALWAYS;
        }

        @Override
        public TransactionPhase getTransactionPhase() {
            return TransactionPhase.IN_PROGRESS;
        }

        @Override
        public int getPriority() {
            return ObserverMethod.DEFAULT_PRIORITY;
        }

        @Override
        public void notify(EventContext<Pom12Event> eventContext) {
            if ("first".equals(marker)) {
                ProcessObserverMethod12Recorder.firstReplacementNotified = true;
            } else if ("final".equals(marker)) {
                ProcessObserverMethod12Recorder.finalReplacementNotified = true;
            } else if ("synthetic".equals(marker)) {
                ProcessObserverMethod12Recorder.syntheticObserverNotified = true;
            }
        }
    }

    public static class ProcessObserverMethod12CaptureExtension implements Extension {
        public void pom(@Observes ProcessObserverMethod<?, ?> event) {
            jakarta.enterprise.inject.spi.AnnotatedMethod<?> method = event.getAnnotatedMethod();
            if (method == null) {
                return;
            }
            Method javaMethod = method.getJavaMember();
            if (Pom12ObservedBean.class.equals(javaMethod.getDeclaringClass()) &&
                    "observe".equals(javaMethod.getName())) {
                ProcessObserverMethod12Recorder.sawManagedObserverEvent = true;
                ProcessObserverMethod12Recorder.annotatedMethodPresent = event.getAnnotatedMethod() != null;
                ProcessObserverMethod12Recorder.observerMethodPresent = event.getObserverMethod() != null;
            }
        }
    }

    public static class ProcessObserverMethod12FinalReplacementExtension implements Extension {
        @SuppressWarnings({"rawtypes", "unchecked"})
        public void first(@Observes @Priority(100) ProcessObserverMethod<?, ?> event) {
            jakarta.enterprise.inject.spi.AnnotatedMethod<?> method = event.getAnnotatedMethod();
            if (method == null) {
                return;
            }
            Method javaMethod = method.getJavaMember();
            if (!Pom12ObservedBean.class.equals(javaMethod.getDeclaringClass()) ||
                    !"observe".equals(javaMethod.getName())) {
                return;
            }
            ProcessObserverMethod raw = event;
            raw.setObserverMethod(new Pom12FixedObserverMethod("first"));
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        public void second(@Observes @Priority(200) ProcessObserverMethod<?, ?> event) {
            jakarta.enterprise.inject.spi.AnnotatedMethod<?> method = event.getAnnotatedMethod();
            if (method == null) {
                return;
            }
            Method javaMethod = method.getJavaMember();
            if (!Pom12ObservedBean.class.equals(javaMethod.getDeclaringClass()) ||
                    !"observe".equals(javaMethod.getName())) {
                return;
            }
            ProcessObserverMethod raw = event;
            raw.setObserverMethod(new Pom12FixedObserverMethod("final"));
        }
    }

    public static class ProcessObserverMethod12ConfigureExtension implements Extension {
        public void pom(@Observes ProcessObserverMethod<Pom12Event, ?> event) {
            jakarta.enterprise.inject.spi.AnnotatedMethod<?> method = event.getAnnotatedMethod();
            if (method == null) {
                return;
            }
            Method javaMethod = method.getJavaMember();
            if (!Pom12ObservedBean.class.equals(javaMethod.getDeclaringClass()) ||
                    !"observe".equals(javaMethod.getName())) {
                return;
            }
            ProcessObserverMethod12Recorder.sameConfiguratorInstance =
                    event.configureObserverMethod() == event.configureObserverMethod();
            event.configureObserverMethod().notifyWith(context ->
                    ProcessObserverMethod12Recorder.configuredObserverNotified = true);
        }
    }

    public static class SetAndConfigureProcessObserverMethod12Extension implements Extension {
        @SuppressWarnings({"rawtypes", "unchecked"})
        public void pom(@Observes ProcessObserverMethod<?, ?> event) {
            jakarta.enterprise.inject.spi.AnnotatedMethod<?> method = event.getAnnotatedMethod();
            if (method == null) {
                return;
            }
            Method javaMethod = method.getJavaMember();
            if (!Pom12ObservedBean.class.equals(javaMethod.getDeclaringClass()) ||
                    !"observe".equals(javaMethod.getName())) {
                return;
            }
            ProcessObserverMethod raw = event;
            raw.setObserverMethod(raw.getObserverMethod());
            raw.configureObserverMethod();
        }
    }

    public static class ProcessObserverMethod12VetoExtension implements Extension {
        public void pom(@Observes ProcessObserverMethod<?, ?> event) {
            jakarta.enterprise.inject.spi.AnnotatedMethod<?> method = event.getAnnotatedMethod();
            if (method == null) {
                return;
            }
            Method javaMethod = method.getJavaMember();
            if (Pom12ObservedBean.class.equals(javaMethod.getDeclaringClass()) &&
                    "observe".equals(javaMethod.getName())) {
                event.veto();
            }
        }
    }

    public static class ProcessObserverMethod12AddDefinitionErrorExtension implements Extension {
        public void pom(@Observes ProcessObserverMethod<?, ?> event) {
            event.addDefinitionError(new IllegalStateException("forced-process-observer-method-definition-error"));
        }
    }

    public static class ThrowingProcessObserverMethod12Extension implements Extension {
        public void pom(@Observes ProcessObserverMethod<?, ?> event) {
            throw new IllegalStateException("forced-process-observer-method-failure");
        }
    }

    public static class CapturedProcessObserverMethod12Extension implements Extension {
        static ProcessObserverMethod<?, ?> capturedEvent;

        static void reset() {
            capturedEvent = null;
        }

        public void pom(@Observes ProcessObserverMethod<?, ?> event) {
            if (capturedEvent == null) {
                capturedEvent = event;
            }
        }
    }

    public static class ProcessObserverMethod12SyntheticCaptureExtension implements Extension {
        public void after(@Observes AfterBeanDiscovery event) {
            event.addObserverMethod(new Pom12FixedObserverMethod("synthetic"));
        }

        public void synthetic(@Observes ProcessSyntheticObserverMethod<?, ?> event) {
            ProcessObserverMethod12Recorder.syntheticEventSeen = true;
            ProcessObserverMethod12Recorder.syntheticSourceCaptured = event.getSource() == this;
        }
    }

    public static class ProcessObserverMethod12SyntheticAnnotatedMethodAccessExtension implements Extension {
        public void after(@Observes AfterBeanDiscovery event) {
            event.addObserverMethod(new Pom12FixedObserverMethod("synthetic"));
        }

        public void synthetic(@Observes ProcessSyntheticObserverMethod<?, ?> event) {
            event.getAnnotatedMethod();
        }
    }

    public static class ProcessBeanAttributesCaptureAndSyntheticExtension implements Extension {
        public void pba(@Observes ProcessBeanAttributes<?> event) {
            Annotated annotated = event.getAnnotated();
            if (annotated instanceof AnnotatedType<?>) {
                Class<?> javaClass = ((AnnotatedType<?>) annotated).getJavaClass();
                String typeName = javaClass.getName();
                if (PbaManagedBean.class.getName().equals(typeName)) {
                    ProcessBeanAttributesRecorder.sawManagedBean = true;
                }
                if (PbaBindingInterceptor.class.getName().equals(typeName)) {
                    ProcessBeanAttributesRecorder.sawInterceptor = true;
                }
                if (PbaDecorator.class.getName().equals(typeName)) {
                    ProcessBeanAttributesRecorder.sawDecorator = true;
                }
                if (PbaSyntheticBean.class.getName().equals(typeName)) {
                    ProcessBeanAttributesRecorder.sawSyntheticBean = true;
                }
                if (BeanManager.class.getName().equals(typeName) ||
                        InjectionPoint.class.getName().equals(typeName) ||
                        jakarta.enterprise.context.Conversation.class.getName().equals(typeName) ||
                        jakarta.enterprise.context.control.RequestContextController.class.getName().equals(typeName)) {
                    ProcessBeanAttributesRecorder.sawBuiltInBean = true;
                }
            } else if (annotated instanceof jakarta.enterprise.inject.spi.AnnotatedMethod<?>) {
                Method method = ((jakarta.enterprise.inject.spi.AnnotatedMethod<?>) annotated).getJavaMember();
                if (PbaProducerHost.class.equals(method.getDeclaringClass()) &&
                        "produceByMethod".equals(method.getName())) {
                    ProcessBeanAttributesRecorder.sawProducerMethod = true;
                }
            } else if (annotated instanceof jakarta.enterprise.inject.spi.AnnotatedField<?>) {
                Field field = ((jakarta.enterprise.inject.spi.AnnotatedField<?>) annotated).getJavaMember();
                if (PbaProducerHost.class.equals(field.getDeclaringClass()) &&
                        "producedField".equals(field.getName())) {
                    ProcessBeanAttributesRecorder.sawProducerField = true;
                }
            }
        }

        public void after(@Observes AfterBeanDiscovery event) {
            event.<PbaSyntheticBean>addBean()
                    .beanClass(PbaSyntheticBean.class)
                    .types(PbaSyntheticBean.class, Object.class)
                    .createWith(ctx -> new PbaSyntheticBean());
        }
    }

    public static class ProcessBeanAttributesConfigureExtension implements Extension {
        public void pba(@Observes ProcessBeanAttributes<?> event) {
            if (!(event.getAnnotated() instanceof AnnotatedType<?>)) {
                return;
            }
            Class<?> beanClass = ((AnnotatedType<?>) event.getAnnotated()).getJavaClass();
            if (!PbaManagedBean.class.equals(beanClass)) {
                return;
            }
            ProcessBeanAttributesConfiguratorRecorder.sameInstance =
                    event.configureBeanAttributes() == event.configureBeanAttributes();
            event.configureBeanAttributes().scope(ApplicationScoped.class);
        }
    }

    public static class ProcessBeanAttributesSetExtension implements Extension {
        @SuppressWarnings({"rawtypes", "unchecked"})
        public void pba(@Observes ProcessBeanAttributes<?> event) {
            if (!(event.getAnnotated() instanceof AnnotatedType<?>)) {
                return;
            }
            Class<?> beanClass = ((AnnotatedType<?>) event.getAnnotated()).getJavaClass();
            if (!PbaManagedBean.class.equals(beanClass)) {
                return;
            }
            BeanAttributes<?> original = event.getBeanAttributes();
            ProcessBeanAttributes raw = event;
            raw.setBeanAttributes(new ScopeOverridingBeanAttributes(original, ApplicationScoped.class));
        }
    }

    public static class SetAndConfigureProcessBeanAttributesExtension implements Extension {
        @SuppressWarnings({"rawtypes", "unchecked"})
        public void pba(@Observes ProcessBeanAttributes<?> event) {
            if (!(event.getAnnotated() instanceof AnnotatedType<?>)) {
                return;
            }
            Class<?> beanClass = ((AnnotatedType<?>) event.getAnnotated()).getJavaClass();
            if (!PbaManagedBean.class.equals(beanClass)) {
                return;
            }
            ProcessBeanAttributes raw = event;
            raw.setBeanAttributes(event.getBeanAttributes());
            event.configureBeanAttributes();
        }
    }

    public static class ProcessBeanAttributesAddDefinitionErrorExtension implements Extension {
        public void pba(@Observes ProcessBeanAttributes<?> event) {
            event.addDefinitionError(new IllegalStateException("forced-process-bean-attributes-definition-error"));
        }
    }

    public static class ProcessBeanAttributesVetoExtension implements Extension {
        public void pba(@Observes ProcessBeanAttributes<?> event) {
            if (!(event.getAnnotated() instanceof AnnotatedType<?>)) {
                return;
            }
            Class<?> beanClass = ((AnnotatedType<?>) event.getAnnotated()).getJavaClass();
            if (PbaManagedBean.class.equals(beanClass)) {
                event.veto();
            }
        }
    }

    public static class ThrowingProcessBeanAttributesExtension implements Extension {
        public void pba(@Observes ProcessBeanAttributes<?> event) {
            throw new IllegalStateException("forced-process-bean-attributes-failure");
        }
    }

    public static class CapturedProcessBeanAttributesExtension implements Extension {
        static ProcessBeanAttributes<?> capturedEvent;

        static void reset() {
            capturedEvent = null;
        }

        public void pba(@Observes ProcessBeanAttributes<?> event) {
            if (capturedEvent == null) {
                capturedEvent = event;
            }
        }
    }

    public static class ProcessBeanAttributesIgnoreFinalMethodsExtension implements Extension {
        public void pba(@Observes ProcessBeanAttributes<?> event) {
            if (!(event.getAnnotated() instanceof AnnotatedType<?>)) {
                return;
            }
            Class<?> beanClass = ((AnnotatedType<?>) event.getAnnotated()).getJavaClass();
            if (PbaFinalMethodBean.class.equals(beanClass)) {
                event.ignoreFinalMethods();
            }
        }
    }

    private static class ScopeOverridingBeanAttributes<T> implements BeanAttributes<T> {
        private final BeanAttributes<T> delegate;
        private final Class<? extends Annotation> scope;

        ScopeOverridingBeanAttributes(BeanAttributes<T> delegate, Class<? extends Annotation> scope) {
            this.delegate = delegate;
            this.scope = scope;
        }

        @Override
        public Set<Type> getTypes() {
            return delegate.getTypes();
        }

        @Override
        public Set<Annotation> getQualifiers() {
            return delegate.getQualifiers();
        }

        @Override
        public Class<? extends Annotation> getScope() {
            return scope;
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public Set<Class<? extends Annotation>> getStereotypes() {
            return delegate.getStereotypes();
        }

        @Override
        public boolean isAlternative() {
            return delegate.isAlternative();
        }
    }

    private static class LifecycleEventRecorder {
        private static final List<String> events = new ArrayList<String>();
        private static final Set<Long> threadIds = new HashSet<Long>();
        private static final Set<Integer> extensionInstanceIds = new HashSet<Integer>();
        private static BeanManager lastBeanManager;

        static synchronized void reset() {
            events.clear();
            threadIds.clear();
            extensionInstanceIds.clear();
            lastBeanManager = null;
        }

        static synchronized void record(String eventName, Object extension, BeanManager beanManager) {
            events.add(eventName);
            threadIds.add(Thread.currentThread().getId());
            extensionInstanceIds.add(System.identityHashCode(extension));
            lastBeanManager = beanManager;
        }
    }

    private static class PriorityOrderRecorder {
        private static final List<String> events = new ArrayList<String>();

        static synchronized void reset() {
            events.clear();
        }
    }

    private static class LifecycleCategoryRecorder {
        private static final List<String> chronology = new ArrayList<String>();
        private static final java.util.Map<String, Integer> appCounters = new java.util.HashMap<String, Integer>();
        private static final java.util.Map<String, Integer> discoveryCounters = new java.util.HashMap<String, Integer>();

        static synchronized void reset() {
            chronology.clear();
            appCounters.clear();
            discoveryCounters.clear();
        }

        static synchronized void recordApplication(String eventName) {
            chronology.add(eventName);
            Integer current = appCounters.get(eventName);
            appCounters.put(eventName, current == null ? 1 : current + 1);
        }

        static synchronized void recordDiscovery(String eventName) {
            Integer current = discoveryCounters.get(eventName);
            discoveryCounters.put(eventName, current == null ? 1 : current + 1);
        }

        static synchronized int applicationCount(String eventName) {
            Integer count = appCounters.get(eventName);
            return count == null ? 0 : count;
        }

        static synchronized int discoveryCount(String eventName) {
            Integer count = discoveryCounters.get(eventName);
            return count == null ? 0 : count;
        }

        static synchronized int indexOf(String eventName) {
            return chronology.indexOf(eventName);
        }
    }

    private static class BuildCompatibleLifecycleRecorder {
        private static final List<String> phases = new ArrayList<String>();

        static synchronized void reset() {
            phases.clear();
        }
    }

    private static class BeforeDiscoveryOrderRecorder {
        private static final List<String> events = new ArrayList<String>();
        private static BeforeBeanDiscovery capturedEvent;

        static synchronized void reset() {
            events.clear();
            capturedEvent = null;
        }

        static synchronized int indexOf(String marker) {
            return events.indexOf(marker);
        }
    }

    private static class DynamicMetadataRecorder {
        private static final List<String> interceptorEvents = new ArrayList<String>();

        static synchronized void reset() {
            interceptorEvents.clear();
        }
    }

    private static class ConfiguratorMetadataRecorder {
        private static final List<String> interceptorEvents = new ArrayList<String>();

        static synchronized void reset() {
            interceptorEvents.clear();
        }
    }

    private static class AfterTypeDiscoveryRecorder {
        private static final List<String> events = new ArrayList<String>();
        private static AfterTypeDiscovery capturedEvent;

        static synchronized void reset() {
            events.clear();
            capturedEvent = null;
        }

        static synchronized int indexOf(String marker) {
            return events.indexOf(marker);
        }
    }

    private static class AfterTypeDiscoveryListRecorder {
        private static final List<String> alternatives = new ArrayList<String>();
        private static final List<String> interceptors = new ArrayList<String>();
        private static final List<String> decorators = new ArrayList<String>();

        static synchronized void reset() {
            alternatives.clear();
            interceptors.clear();
            decorators.clear();
        }

        static synchronized void capture(AfterTypeDiscovery event) {
            alternatives.clear();
            interceptors.clear();
            decorators.clear();

            for (Class<?> candidate : event.getAlternatives()) {
                alternatives.add(candidate.getName());
            }
            for (Class<?> candidate : event.getInterceptors()) {
                interceptors.add(candidate.getName());
            }
            for (Class<?> candidate : event.getDecorators()) {
                decorators.add(candidate.getName());
            }
        }
    }

    private static class AfterTypeDiscoveryMutationRecorder {
        private static final List<String> interceptorEvents = new ArrayList<String>();

        static synchronized void reset() {
            interceptorEvents.clear();
        }
    }

    private static class AfterBeanDiscoveryOrderRecorder {
        private static final List<String> events = new ArrayList<String>();
        private static AfterBeanDiscovery capturedEvent;

        static synchronized void reset() {
            events.clear();
            capturedEvent = null;
        }

        static synchronized int indexOf(String marker) {
            return events.indexOf(marker);
        }
    }

    private static class AfterBeanDiscoverySyntheticBeanRecorder {
        private static final List<String> events = new ArrayList<String>();

        static synchronized void reset() {
            events.clear();
        }
    }

    private static class AfterBeanDiscoverySyntheticObserverRecorder {
        private static final List<String> events = new ArrayList<String>();

        static synchronized void reset() {
            events.clear();
        }
    }

    private static class AfterBeanDiscoveryAnnotatedTypeRecorder {
        private static boolean foundAddedById;
        private static boolean foundAddedInIterable;
        private static boolean foundDiscoveredInIterable;

        static synchronized void reset() {
            foundAddedById = false;
            foundAddedInIterable = false;
            foundDiscoveredInIterable = false;
        }
    }

    private static class AfterBeanDiscoveryNullIdRecorder {
        private static boolean noException;
        private static AnnotatedType<LifecycleMarkerBean> annotatedType;

        static synchronized void reset() {
            noException = false;
            annotatedType = null;
        }
    }

    private static class AfterBeanDiscoverySynthesisRecorder {
        private static final List<String> events = new ArrayList<String>();

        static synchronized void reset() {
            events.clear();
        }

        static synchronized int indexOf(String marker) {
            return events.indexOf(marker);
        }
    }

    private static class BeanConfiguratorCallbacksRecorder {
        private static final List<String> events = new ArrayList<String>();

        static synchronized void reset() {
            events.clear();
        }
    }

    private static class ObserverMethodConfiguratorRecorder {
        private static Class<?> beanClass;
        private static Type observedType;
        private static Set<Annotation> qualifiers = new HashSet<Annotation>();
        private static Reception reception;
        private static TransactionPhase transactionPhase;
        private static int priority;
        private static boolean async;
        private static final List<String> notifications = new ArrayList<String>();

        static synchronized void reset() {
            beanClass = null;
            observedType = null;
            qualifiers = new HashSet<Annotation>();
            reception = null;
            transactionPhase = null;
            priority = 0;
            async = false;
            notifications.clear();
        }
    }

    private static class AfterDeploymentValidationOrderRecorder {
        private static final List<String> events = new ArrayList<String>();
        private static AfterDeploymentValidation capturedEvent;
        private static BeanManager beanManager;

        static synchronized void reset() {
            events.clear();
            capturedEvent = null;
            beanManager = null;
        }

        static synchronized int indexOf(String marker) {
            return events.indexOf(marker);
        }
    }

    private static class AfterDeploymentValidationProblemRecorder {
        private static final List<String> events = new ArrayList<String>();

        static synchronized void reset() {
            events.clear();
        }
    }

    private static class AfterDeploymentValidationExceptionRecorder {
        private static final List<String> events = new ArrayList<String>();

        static synchronized void reset() {
            events.clear();
        }
    }

    private static class AfterDeploymentValidationBuildCompatibleRecorder {
        private static final List<String> events = new ArrayList<String>();

        static synchronized void reset() {
            events.clear();
        }

        static synchronized int indexOf(String marker) {
            return events.indexOf(marker);
        }
    }

    private static class BeforeShutdownRecorder {
        private static final List<String> events = new ArrayList<String>();
        private static boolean applicationDestroyedObserved;
        private static boolean applicationDestroyedAtBeforeShutdown;
        private static BeforeShutdown capturedEvent;
        private static BeanManager beanManager;

        static synchronized void reset() {
            events.clear();
            applicationDestroyedObserved = false;
            applicationDestroyedAtBeforeShutdown = false;
            capturedEvent = null;
            beanManager = null;
        }

        static synchronized int indexOf(String marker) {
            return events.indexOf(marker);
        }
    }

    private static class BeforeShutdownExceptionRecorder {
        private static final List<String> events = new ArrayList<String>();

        static synchronized void reset() {
            events.clear();
        }
    }

    private static class ProcessAnnotatedTypeRecorder {
        private static final List<String> types = new ArrayList<String>();

        static synchronized void reset() {
            types.clear();
        }
    }

    private static class ProcessSyntheticAnnotatedTypeRecorder {
        private static final List<String> types = new ArrayList<String>();
        private static final List<String> sources = new ArrayList<String>();

        static synchronized void reset() {
            types.clear();
            sources.clear();
        }
    }

    private static class WithAnnotationsFilterRecorder {
        private static final List<String> types = new ArrayList<String>();

        static synchronized void reset() {
            types.clear();
        }
    }

    private static class ProcessAnnotatedTypeEnhancementOrderRecorder {
        private static final List<String> events = new ArrayList<String>();

        static synchronized void reset() {
            events.clear();
        }

        static synchronized int indexOf(String marker) {
            return events.indexOf(marker);
        }
    }

    private static class ProcessInjectionPointRecorder {
        private static final Set<String> beanClasses = new HashSet<String>();

        static synchronized void reset() {
            beanClasses.clear();
        }
    }

    private static class ProcessInjectionPointConfiguratorRecorder {
        private static boolean sameInstance;

        static synchronized void reset() {
            sameInstance = false;
        }
    }

    private static class ProcessInjectionPointConfiguratorOperationsRecorder {
        private static boolean captured;
        private static Type type;
        private static boolean delegate;
        private static boolean transientField;
        private static final Set<String> qualifierTypes = new HashSet<String>();

        static synchronized void reset() {
            captured = false;
            type = null;
            delegate = false;
            transientField = false;
            qualifierTypes.clear();
        }
    }

    private static class ProcessInjectionTargetRecorder {
        private static final Set<String> beanClasses = new HashSet<String>();

        static synchronized void reset() {
            beanClasses.clear();
        }
    }

    private static class ProcessInjectionTargetReplacementRecorder {
        private static InjectionTarget<?> originalTarget;
        private static boolean firstReplacementUsed;
        private static boolean finalReplacementUsed;

        static synchronized void reset() {
            originalTarget = null;
            firstReplacementUsed = false;
            finalReplacementUsed = false;
        }
    }

    private static class ProcessBeanAttributesRecorder {
        private static boolean sawManagedBean;
        private static boolean sawInterceptor;
        private static boolean sawDecorator;
        private static boolean sawProducerMethod;
        private static boolean sawProducerField;
        private static boolean sawBuiltInBean;
        private static boolean sawSyntheticBean;

        static synchronized void reset() {
            sawManagedBean = false;
            sawInterceptor = false;
            sawDecorator = false;
            sawProducerMethod = false;
            sawProducerField = false;
            sawBuiltInBean = false;
            sawSyntheticBean = false;
        }
    }

    private static class ProcessBeanAttributesConfiguratorRecorder {
        private static boolean sameInstance;

        static synchronized void reset() {
            sameInstance = false;
        }
    }

    private static class ProcessBeanAttributesConfigurator91Recorder {
        private static boolean sameInstance;
        private static boolean metadataCaptured;
        private static Set<Type> types = new HashSet<Type>();
        private static Class<? extends Annotation> scope;
        private static String name;
        private static Set<String> qualifierTypes = new HashSet<String>();
        private static Set<Class<? extends Annotation>> stereotypes = new HashSet<Class<? extends Annotation>>();
        private static boolean alternative;

        static synchronized void reset() {
            sameInstance = false;
            metadataCaptured = false;
            types.clear();
            scope = null;
            name = null;
            qualifierTypes.clear();
            stereotypes.clear();
            alternative = false;
        }
    }

    private static class ProcessBean10Recorder {
        private static boolean sawManagedBean;
        private static boolean sawInterceptorManagedBean;
        private static boolean sawDecoratorManagedBean;
        private static boolean sawProducerMethod;
        private static boolean sawProducerField;
        private static boolean sawSyntheticBean;
        private static boolean sawBuiltInBean;
        private static boolean sawPbaBeforeProcessBeanForManaged;
        private static final List<String> events = new ArrayList<String>();

        static synchronized void reset() {
            sawManagedBean = false;
            sawInterceptorManagedBean = false;
            sawDecoratorManagedBean = false;
            sawProducerMethod = false;
            sawProducerField = false;
            sawSyntheticBean = false;
            sawBuiltInBean = false;
            sawPbaBeforeProcessBeanForManaged = false;
            events.clear();
        }
    }

    private static class ProcessBean10ApiRecorder {
        private static boolean managedSeen;
        private static boolean managedAnnotatedBeanClassMatches;
        private static boolean managedInvokerBuilderCreated;
        private static boolean producerMethodSeen;
        private static boolean producerMethodAnnotatedMatches;
        private static boolean producerMethodDisposedParameterNull;
        private static boolean producerFieldSeen;
        private static boolean producerFieldAnnotatedMatches;
        private static boolean producerFieldDisposedParameterNull;
        private static boolean syntheticSeen;
        private static boolean syntheticSourceCaptured;

        static synchronized void reset() {
            managedSeen = false;
            managedAnnotatedBeanClassMatches = false;
            managedInvokerBuilderCreated = false;
            producerMethodSeen = false;
            producerMethodAnnotatedMatches = false;
            producerMethodDisposedParameterNull = false;
            producerFieldSeen = false;
            producerFieldAnnotatedMatches = false;
            producerFieldDisposedParameterNull = false;
            syntheticSeen = false;
            syntheticSourceCaptured = false;
        }
    }

    private static class ProcessProducer11Recorder {
        private static boolean sawMethodProducerEvent;
        private static boolean sawFieldProducerEvent;

        static synchronized void reset() {
            sawMethodProducerEvent = false;
            sawFieldProducerEvent = false;
        }
    }

    private static class ProcessProducer11ReplacementRecorder {
        private static boolean firstReplacementUsed;
        private static boolean finalReplacementUsed;

        static synchronized void reset() {
            firstReplacementUsed = false;
            finalReplacementUsed = false;
        }
    }

    private static class ProcessProducer11ConfiguratorRecorder {
        private static boolean sameInstance;

        static synchronized void reset() {
            sameInstance = false;
        }
    }

    private static class ProcessProducer111ConfiguratorRecorder {
        private static int originalProduceCalls;
        private static int configuredProduceCalls;
        private static int originalDisposeCalls;
        private static int configuredDisposeCalls;

        static synchronized void reset() {
            originalProduceCalls = 0;
            configuredProduceCalls = 0;
            originalDisposeCalls = 0;
            configuredDisposeCalls = 0;
        }
    }

    private static class ProcessObserverMethod12Recorder {
        private static boolean sawManagedObserverEvent;
        private static boolean annotatedMethodPresent;
        private static boolean observerMethodPresent;
        private static boolean originalManagedObserverNotified;
        private static boolean firstReplacementNotified;
        private static boolean finalReplacementNotified;
        private static boolean sameConfiguratorInstance;
        private static boolean configuredObserverNotified;
        private static boolean syntheticEventSeen;
        private static boolean syntheticSourceCaptured;
        private static boolean syntheticObserverNotified;

        static synchronized void reset() {
            sawManagedObserverEvent = false;
            annotatedMethodPresent = false;
            observerMethodPresent = false;
            originalManagedObserverNotified = false;
            firstReplacementNotified = false;
            finalReplacementNotified = false;
            sameConfiguratorInstance = false;
            configuredObserverNotified = false;
            syntheticEventSeen = false;
            syntheticSourceCaptured = false;
            syntheticObserverNotified = false;
        }
    }

    private static List<String> asList(String... values) {
        List<String> out = new ArrayList<String>();
        for (String value : values) {
            out.add(value);
        }
        return out;
    }
}
