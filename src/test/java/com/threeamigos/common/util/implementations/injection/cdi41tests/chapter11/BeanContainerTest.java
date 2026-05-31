package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter11;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.discovery.NonPortableBehaviourException;
import com.threeamigos.common.util.implementations.injection.spi.SyringeCDIProvider;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.inject.Stereotype;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.Discovery;
import jakarta.enterprise.inject.build.compatible.spi.MetaAnnotations;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanContainer;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.CDIProvider;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.ObserverMethod;
import jakarta.enterprise.inject.spi.InterceptionType;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;
import jakarta.inject.Qualifier;
import jakarta.enterprise.context.NormalScope;
import jakarta.enterprise.context.spi.AlterableContext;
import jakarta.enterprise.context.spi.Contextual;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("11.1 - The Bean Container")
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
public class BeanContainerTest {

    private CdiStateSnapshot cdiStateSnapshot;

    @BeforeEach
    void captureCdiState() throws Exception {
        cdiStateSnapshot = CdiStateSnapshot.capture();
    }

    @AfterEach
    void restoreCdiState() throws Exception {
        if (cdiStateSnapshot != null) {
            cdiStateSnapshot.restore();
        }
    }

    @Test
    @DisplayName("11.1 - BeanManager extends BeanContainer and adds additional methods")
    void shouldExposeBeanManagerAsSupersetOfBeanContainer() {
        assertTrue(BeanContainer.class.isAssignableFrom(BeanManager.class));

        Set<String> beanContainerMethods = Arrays.stream(BeanContainer.class.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        Set<String> beanManagerMethods = Arrays.stream(BeanManager.class.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertTrue(beanManagerMethods.containsAll(beanContainerMethods));
        assertTrue(beanManagerMethods.size() > beanContainerMethods.size());
    }

    @Test
    @DisplayName("11.1 - Container provides built-in BeanContainer bean with @Dependent scope, @Default qualifier and no name")
    void shouldProvideBuiltinBeanContainerBeanWithExpectedMetadata() {
        Syringe syringe = newSyringe(ConsumerBean.class, PlainService.class);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        Set<Bean<?>> beans = beanManager.getBeans(BeanContainer.class);

        assertEquals(1, beans.size());
        Bean<?> bean = beans.iterator().next();
        assertEquals(Dependent.class, bean.getScope());
        assertNull(bean.getName());
        assertTrue(bean.getTypes().contains(BeanContainer.class));
        assertTrue(bean.getQualifiers().contains(Default.Literal.INSTANCE));
    }

    @Test
    @DisplayName("11.1 - Any bean can inject BeanContainer and use it to obtain contextual references")
    void shouldInjectBeanContainerAndUseProgrammaticOperations() {
        Syringe syringe = newSyringe(ConsumerBean.class, PlainService.class);
        syringe.setup();

        ConsumerBean consumer = getBeanInstance(syringe, ConsumerBean.class);
        assertNotNull(consumer.container);

        String value = consumer.lookupServiceName();
        assertEquals("plain-service", value);
    }

    @Test
    @DisplayName("11.1 - BeanContainer operations can be invoked at runtime during application execution")
    void shouldAllowBeanContainerOperationsDuringRuntimeExecution() {
        Syringe syringe = newSyringe(ConsumerBean.class, PlainService.class);
        syringe.setup();

        BeanContainer container = getBeanInstance(syringe, ConsumerBean.class).container;
        Set<Bean<?>> beans = container.getBeans(PlainService.class);
        Bean<? extends Object> resolved = container.resolve((Set) beans);
        CreationalContext<?> context = container.createCreationalContext(resolved);
        PlainService ref = (PlainService) container.getReference(resolved, PlainService.class, context);

        assertNotNull(ref);
        assertEquals("plain-service", ref.name());
        context.release();
    }

    @Test
    @DisplayName("11.1.1 - CDI.current() returns the CDI object from CDIProvider.getCDI()")
    void shouldObtainCurrentContainerFromConfiguredCdiProvider() {
        Syringe syringe = newSyringe(ConsumerBean.class, PlainService.class);
        syringe.setup();

        CDI<Object> expected = syringe.getCDI();
        RecordingProvider provider = new RecordingProvider(expected);
        CDI.setCDIProvider(provider);

        CDI<Object> current = CDI.current();
        assertSame(expected, current);
        assertTrue(provider.calls.get() >= 1);
    }

    @Test
    @DisplayName("11.1.1 - CDI.getBeanContainer() may be used to access BeanContainer operations at runtime")
    void shouldExposeBeanContainerFromCdiAndAllowRuntimeOperations() {
        Syringe syringe = newSyringe(ConsumerBean.class, PlainService.class);
        syringe.setup();

        CDI<Object> cdi = syringe.getCDI();
        CDI.setCDIProvider(new RecordingProvider(cdi));
        BeanContainer container = CDI.current().getBeanContainer();

        Set<Bean<?>> beans = container.getBeans(PlainService.class);
        Bean<? extends Object> resolved = container.resolve((Set) beans);
        CreationalContext<?> context = container.createCreationalContext(resolved);
        PlainService service = (PlainService) container.getReference(resolved, PlainService.class, context);
        try {
            assertEquals("plain-service", service.name());
        } finally {
            context.release();
        }
    }

    @Test
    @DisplayName("11.1.1 - CDI.select() without qualifiers assumes @Default")
    void shouldAssumeDefaultQualifierWhenNoQualifierIsPassedToSelect() {
        Syringe syringe = newSyringe(DefaultOnlyBean.class);
        syringe.setup();

        CDI.setCDIProvider(new RecordingProvider(syringe.getCDI()));
        DefaultOnlyBean bean = CDI.current().select(DefaultOnlyBean.class).get();

        assertNotNull(bean);
        assertEquals("default-only", bean.value());
    }

    @Test
    @DisplayName("11.1.1 - Managed bootstrap registers global CDI so CDI.current().select works without thread-local setup")
    void shouldAllowCdiCurrentSelectInManagedBootstrapWithoutThreadLocalContext() {
        Syringe syringe = new Syringe();
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        try {
            syringe.initialize();
            syringe.addDiscoveredClass(DefaultOnlyBean.class, BeanArchiveMode.EXPLICIT);
            syringe.start();

            SyringeCDIProvider.unregisterThreadLocalCDI();
            DefaultOnlyBean bean = CDI.current().select(DefaultOnlyBean.class).get();

            assertNotNull(bean);
            assertEquals("default-only", bean.value());
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("11.1.1 - CDI.setCDIProvider rejects null provider")
    void shouldRejectNullCdiProvider() {
        assertThrows(IllegalArgumentException.class, () -> CDI.setCDIProvider(null));
    }

    @Test
    @DisplayName("11.1.1 - If no provider is available CDI.current() throws IllegalStateException")
    void shouldThrowWhenNoProviderIsAvailable() throws Exception {
        CDI.setCDIProvider(new RecordingProvider(null));
        setDiscoveredProviders(Collections.<CDIProvider>emptySet());
        setConfiguredProvider(null);

        assertThrows(IllegalStateException.class, CDI::current);
    }

    @Test
    @DisplayName("11.1.1 - CDIProvider default priority is 0 when getPriority() is not overridden")
    void shouldUseDefaultCdiProviderPriorityWhenNotOverridden() {
        CDIProvider provider = new DefaultPriorityProvider();
        assertEquals(0, provider.getPriority());
    }

    @Test
    @DisplayName("11.1.2 - BeanContainer.getReference returns a contextual reference for the given bean and bean type")
    void shouldReturnContextualReferenceForValidBeanAndBeanType() {
        Syringe syringe = newSyringe(ApplicationService.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        Set<Bean<?>> beans = container.getBeans(ApplicationService.class);
        Bean<? extends Object> bean = container.resolve((Set) beans);
        CreationalContext<?> ctx = container.createCreationalContext(bean);
        Object reference = container.getReference(bean, ApplicationService.class, ctx);

        assertNotNull(reference);
        assertTrue(reference instanceof ApplicationService);
    }

    @Test
    @DisplayName("11.1.2 - BeanContainer.getReference throws IllegalArgumentException when beanType is not a bean type of bean")
    void shouldRejectBeanTypeThatIsNotInBeanTypes() {
        Syringe syringe = newSyringe(PlainService.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        Set<Bean<?>> beans = container.getBeans(PlainService.class);
        Bean<? extends Object> bean = container.resolve((Set) beans);
        CreationalContext<?> ctx = container.createCreationalContext(bean);

        assertThrows(IllegalArgumentException.class, () -> container.getReference(bean, String.class, ctx));
    }

    @Test
    @DisplayName("11.1.3 - BeanContainer.createCreationalContext returns a CreationalContext for contextual instances")
    void shouldCreateCreationalContextForContextual() {
        Syringe syringe = newSyringe(PlainService.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        Set<Bean<?>> beans = container.getBeans(PlainService.class);
        Bean<PlainService> bean = (Bean<PlainService>) container.resolve((Set) beans);

        CreationalContext<PlainService> ctx = container.createCreationalContext(bean);
        assertNotNull(ctx);
    }

    @Test
    @DisplayName("11.1.3 - BeanContainer.createCreationalContext accepts null for non-contextual objects")
    void shouldCreateCreationalContextForNonContextualObjectWithNullContextual() {
        Syringe syringe = newSyringe(PlainService.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        CreationalContext<Object> ctx = container.createCreationalContext(null);

        assertNotNull(ctx);
    }

    @Test
    @DisplayName("11.1.4 - BeanContainer.getBeans(type) assumes @Default when no qualifiers are passed")
    void shouldAssumeDefaultQualifierWhenNoQualifiersArePassedToGetBeans() {
        Syringe syringe = newSyringe(DefaultContractBean.class, BlueContractBean.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        Set<Bean<?>> beans = container.getBeans(QualifiedContract.class);

        assertEquals(1, beans.size());
        assertEquals(DefaultContractBean.class, beans.iterator().next().getBeanClass());
    }

    @Test
    @DisplayName("11.1.4 - BeanContainer.getBeans(type, qualifiers) filters candidates by required qualifiers")
    void shouldFilterBeansByRequiredQualifiers() {
        Syringe syringe = newSyringe(DefaultContractBean.class, BlueContractBean.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        Set<Bean<?>> beans = container.getBeans(QualifiedContract.class, new BlueLiteral());

        assertEquals(1, beans.size());
        assertEquals(BlueContractBean.class, beans.iterator().next().getBeanClass());
    }

    @Test
    @DisplayName("11.1.4 - BeanContainer.getBeans throws IllegalArgumentException for type variables")
    void shouldRejectTypeVariableInGetBeans() throws Exception {
        Syringe syringe = newSyringe(PlainService.class);
        syringe.setup();

        Type typeVariable = GenericTypeHolder.class.getDeclaredField("value").getGenericType();
        BeanContainer container = syringe.getBeanManager();

        assertThrows(IllegalArgumentException.class, () -> container.getBeans(typeVariable));
    }

    @Test
    @DisplayName("11.1.4 - BeanContainer.getBeans throws IllegalArgumentException for duplicate non-repeating qualifiers")
    void shouldRejectDuplicateNonRepeatingQualifiersInGetBeans() {
        Syringe syringe = newSyringe(DefaultContractBean.class, BlueContractBean.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();

        assertThrows(IllegalArgumentException.class, () ->
                container.getBeans(QualifiedContract.class, new BlueLiteral(), new BlueLiteral()));
    }

    @Test
    @DisplayName("11.1.4 - BeanContainer.getBeans throws IllegalArgumentException for non-qualifier annotations")
    void shouldRejectNonQualifierAnnotationInGetBeans() {
        Syringe syringe = newSyringe(DefaultContractBean.class, BlueContractBean.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();

        assertThrows(IllegalArgumentException.class, () ->
                container.getBeans(QualifiedContract.class, new NotQualifierLiteral()));
    }

    @Test
    @DisplayName("11.1.5 - BeanContainer.getBeans(String) returns beans with the given bean name")
    void shouldReturnBeansByGivenName() {
        Syringe syringe = newSyringe(NamedService.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        Set<Bean<?>> beans = container.getBeans("namedService");

        assertEquals(1, beans.size());
        assertEquals(NamedService.class, beans.iterator().next().getBeanClass());
    }

    @Test
    @DisplayName("11.1.5 - BeanContainer.getBeans(String) returns only beans available for injection")
    void shouldReturnOnlyBeansAvailableForInjectionByName() {
        Syringe syringe = newSyringe(EnabledNamedService.class, DisabledAlternativeNamedService.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        Set<Bean<?>> beans = container.getBeans("sharedName");

        assertEquals(1, beans.size());
        assertEquals(EnabledNamedService.class, beans.iterator().next().getBeanClass());
    }

    @Test
    @DisplayName("11.1.5 - BeanContainer.getBeans(String) returns empty set when no bean has the given name")
    void shouldReturnEmptySetWhenNoBeanMatchesGivenName() {
        Syringe syringe = newSyringe(NamedService.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        Set<Bean<?>> beans = container.getBeans("missingBeanName");

        assertTrue(beans.isEmpty());
    }

    @Test
    @DisplayName("11.1.6 - BeanContainer.resolve applies ambiguous dependency resolution and throws when ambiguity remains")
    void shouldThrowAmbiguousResolutionExceptionWhenDependencyRemainsAmbiguous() {
        Syringe syringe = newSyringe(AmbiguousBeanA.class, AmbiguousBeanB.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        Set<Bean<?>> beans = container.getBeans(AmbiguousContract.class);

        assertThrows(AmbiguousResolutionException.class, () -> container.resolve((Set) beans));
    }

    @Test
    @DisplayName("11.1.6 - BeanContainer.resolve returns null when null is passed")
    void shouldReturnNullWhenResolveReceivesNullSet() {
        Syringe syringe = newSyringe(PlainService.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        assertNull(container.resolve(null));
    }

    @Test
    @DisplayName("11.1.6 - BeanContainer.resolve returns null when no beans are passed")
    void shouldReturnNullWhenResolveReceivesEmptySet() {
        Syringe syringe = newSyringe(PlainService.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        assertNull(container.resolve(Collections.<Bean<?>>emptySet()));
    }

    @Test
    @DisplayName("11.1.7 - BeanContainer.getEvent() returns Event<Object> with specified qualifier @Default")
    void shouldProvideDefaultQualifiedEventObject() {
        EventRecorder.reset();
        Syringe syringe = newSyringe(DefaultEventObserverBean.class, QualifiedEventObserverBean.class, EventClientBean.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        Event<Object> event = container.getEvent();
        event.fire(new ContainerEventPayload("plain"));

        assertEquals(1, EventRecorder.defaultObserverCalls);
        assertEquals(0, EventRecorder.blueObserverCalls);
    }

    @Test
    @DisplayName("11.1.7 - BeanContainer.getEvent() can be used like a standard Event via select(type, qualifiers)")
    void shouldUseReturnedEventAsStandardEvent() {
        EventRecorder.reset();
        Syringe syringe = newSyringe(DefaultEventObserverBean.class, QualifiedEventObserverBean.class, EventClientBean.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        Event<Object> event = container.getEvent();
        event.select(ContainerEventPayload.class, new BlueLiteral()).fire(new ContainerEventPayload("blue"));

        assertEquals(1, EventRecorder.defaultObserverCalls);
        assertEquals(1, EventRecorder.blueObserverCalls);
    }

    @Test
    @DisplayName("11.1.8 - BeanContainer.resolveObserverMethods resolves matching observer methods by event type and qualifiers")
    void shouldResolveObserverMethodsByTypeAndQualifiers() {
        Syringe syringe = newSyringe(DefaultResolutionObserverBean.class, QualifiedResolutionObserverBean.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        Set<ObserverMethod<? super ResolutionEventPayload>> observers =
                container.resolveObserverMethods(new ResolutionEventPayload("ok"), new BlueLiteral());

        assertEquals(2, observers.size());
    }

    @Test
    @DisplayName("11.1.8 - BeanContainer.resolveObserverMethods throws IllegalArgumentException when runtime event type contains a type variable")
    void shouldRejectEventRuntimeTypeContainingTypeVariable() {
        Syringe syringe = newSyringe(DefaultResolutionObserverBean.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        assertThrows(IllegalArgumentException.class,
                () -> container.resolveObserverMethods(new GenericRuntimeEvent<String>("x")));
    }

    @Test
    @DisplayName("11.1.8 - BeanContainer.resolveObserverMethods throws IllegalArgumentException for duplicate non-repeating qualifiers")
    void shouldRejectDuplicateNonRepeatingQualifiersInResolveObserverMethods() {
        Syringe syringe = newSyringe(DefaultResolutionObserverBean.class, QualifiedResolutionObserverBean.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        assertThrows(IllegalArgumentException.class, () ->
                container.resolveObserverMethods(new ResolutionEventPayload("x"), new BlueLiteral(), new BlueLiteral()));
    }

    @Test
    @DisplayName("11.1.8 - BeanContainer.resolveObserverMethods throws IllegalArgumentException for non-qualifier annotations")
    void shouldRejectNonQualifierAnnotationInResolveObserverMethods() {
        Syringe syringe = newSyringe(DefaultResolutionObserverBean.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        assertThrows(IllegalArgumentException.class, () ->
                container.resolveObserverMethods(new ResolutionEventPayload("x"), new NotQualifierLiteral()));
    }

    @Test
    @DisplayName("11.1.9 - BeanContainer.resolveInterceptors returns enabled interceptors ordered by interceptor resolution rules")
    void shouldResolveEnabledInterceptorsInOrder() {
        Syringe syringe = newSyringe(FirstTraceInterceptor.class, SecondTraceInterceptor.class, TracedService.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        List<jakarta.enterprise.inject.spi.Interceptor<?>> interceptors =
                container.resolveInterceptors(InterceptionType.AROUND_INVOKE, new TraceLiteral());

        assertEquals(2, interceptors.size());
        assertEquals(FirstTraceInterceptor.class, interceptors.get(0).getBeanClass());
        assertEquals(SecondTraceInterceptor.class, interceptors.get(1).getBeanClass());
    }

    @Test
    @DisplayName("11.1.9 - BeanContainer.resolveInterceptors throws IllegalArgumentException for duplicate non-repeating interceptor bindings")
    void shouldRejectDuplicateInterceptorBindingsInResolveInterceptors() {
        Syringe syringe = newSyringe(FirstTraceInterceptor.class, SecondTraceInterceptor.class, TracedService.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        assertThrows(IllegalArgumentException.class, () ->
                container.resolveInterceptors(InterceptionType.AROUND_INVOKE, new TraceLiteral(), new TraceLiteral()));
    }

    @Test
    @DisplayName("11.1.9 - BeanContainer.resolveInterceptors throws IllegalArgumentException when no interceptor binding is given")
    void shouldRejectMissingInterceptorBindingsInResolveInterceptors() {
        Syringe syringe = newSyringe(FirstTraceInterceptor.class, SecondTraceInterceptor.class, TracedService.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        assertThrows(IllegalArgumentException.class, () -> container.resolveInterceptors(InterceptionType.AROUND_INVOKE));
    }

    @Test
    @DisplayName("11.1.9 - BeanContainer.resolveInterceptors throws IllegalArgumentException for annotations that are not interceptor bindings")
    void shouldRejectNonInterceptorBindingAnnotationsInResolveInterceptors() {
        Syringe syringe = newSyringe(FirstTraceInterceptor.class, SecondTraceInterceptor.class, TracedService.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        assertThrows(IllegalArgumentException.class, () ->
                container.resolveInterceptors(InterceptionType.AROUND_INVOKE, new NotQualifierLiteral()));
    }

    @Test
    @DisplayName("11.1.10 - BeanContainer identifies qualifier, scope, normal scope, stereotype and interceptor binding types")
    void shouldIdentifyAnnotationKinds() {
        Syringe syringe = newSyringe(PlainService.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        assertTrue(container.isQualifier(Blue.class));
        assertTrue(container.isScope(Dependent.class));
        assertTrue(container.isScope(ApplicationScoped.class));
        assertTrue(container.isNormalScope(ApplicationScoped.class));
        assertTrue(!container.isNormalScope(Dependent.class));
        assertTrue(container.isInterceptorBinding(Trace.class));
        assertTrue(container.isStereotype(ServiceStereotype.class));
    }

    @Test
    @DisplayName("11.1.10 - BeanContainer returns false for annotations that are not qualifier/scope/stereotype/interceptor binding types")
    void shouldReturnFalseForNonMatchingAnnotationKinds() {
        Syringe syringe = newSyringe(PlainService.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        assertTrue(!container.isQualifier(NotQualifier.class));
        assertTrue(!container.isScope(NotQualifier.class));
        assertTrue(!container.isNormalScope(NotQualifier.class));
        assertTrue(!container.isInterceptorBinding(NotQualifier.class));
        assertTrue(!container.isStereotype(NotQualifier.class));
    }

    @Test
    @DisplayName("11.1.12 - BeanContainer.getContexts(scope) returns context objects associated with the given scope")
    void shouldReturnContextsForGivenScope() {
        Syringe syringe = newSyringe(PlainService.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        java.util.Collection<Context> contexts = container.getContexts(ApplicationScoped.class);

        assertEquals(1, contexts.size());
        Context context = contexts.iterator().next();
        assertEquals(ApplicationScoped.class, context.getScope());
        assertTrue(context.isActive());
    }

    @Test
    @DisplayName("11.1.12 - BeanContainer.getContexts(scope) includes inactive contexts for the given scope")
    void shouldReturnInactiveContextObjectsForGivenScope() {
        Syringe syringe = newSyringe(PlainService.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        java.util.Collection<Context> contexts = container.getContexts(RequestScoped.class);

        assertEquals(1, contexts.size());
        Context context = contexts.iterator().next();
        assertEquals(RequestScoped.class, context.getScope());
        assertTrue(!context.isActive());
    }

    @Test
    @DisplayName("11.1.13 - BeanContainer.createInstance returns an Instance<Object> for programmatic bean lookup")
    void shouldCreateInstanceForProgrammaticLookup() {
        Syringe syringe = newSyringe(PlainService.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        Instance<Object> instance = container.createInstance();

        assertNotNull(instance);
        PlainService service = instance.select(PlainService.class).get();
        assertEquals("plain-service", service.name());
    }

    @Test
    @DisplayName("11.1.13 - If no qualifier is passed to Instance.select the @Default qualifier is assumed")
    void shouldAssumeDefaultWhenNoQualifierPassedToInstanceSelect() {
        Syringe syringe = newSyringe(DefaultContractBean.class, BlueContractBean.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        Instance<Object> instance = container.createInstance();
        QualifiedContract resolved = instance.select(QualifiedContract.class).get();

        assertEquals(DefaultContractBean.class, resolved.getClass());
    }

    @Test
    @DisplayName("11.1.13 - Returned Instance can only access beans available for injection")
    void shouldResolveOnlyAvailableBeansFromCreateInstance() {
        Syringe syringe = newSyringe(EnabledNamedService.class, DisabledAlternativeNamedService.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        Instance<Object> instance = container.createInstance();
        EnabledNamedService service = instance.select(EnabledNamedService.class).get();

        assertEquals("enabled", service.value());
    }

    @Test
    @DisplayName("11.1.13 - Dependent instances obtained from createInstance can be explicitly released via Instance.destroy")
    void shouldDestroyDependentInstanceViaInstanceDestroy() {
        DestroyableDependentBean.reset();
        Syringe syringe = newSyringe(DestroyableDependentBean.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        Instance<Object> instance = container.createInstance();
        DestroyableDependentBean bean = instance.select(DestroyableDependentBean.class).get();

        assertEquals(0, DestroyableDependentBean.preDestroyCalls);
        instance.destroy(bean);
        assertEquals(1, DestroyableDependentBean.preDestroyCalls);
    }

    @Test
    @DisplayName("11.1.14 - BeanContainer.isMatchingBean returns true when bean types and qualifiers satisfy required type and qualifiers")
    void shouldMatchBeanByTypeAndQualifiers() {
        Syringe syringe = newSyringe(PlainService.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        Set<java.lang.reflect.Type> beanTypes = new HashSet<java.lang.reflect.Type>();
        beanTypes.add(PlainService.class);
        beanTypes.add(Object.class);
        Set<java.lang.annotation.Annotation> beanQualifiers = new HashSet<java.lang.annotation.Annotation>();
        beanQualifiers.add(Default.Literal.INSTANCE);

        Set<java.lang.annotation.Annotation> requiredQualifiers = new HashSet<java.lang.annotation.Annotation>();
        requiredQualifiers.add(Default.Literal.INSTANCE);

        boolean matches = container.isMatchingBean(beanTypes, beanQualifiers, PlainService.class, requiredQualifiers);
        assertTrue(matches);
    }

    @Test
    @DisplayName("11.1.14 - BeanContainer.isMatchingBean returns false when qualifiers do not satisfy required qualifiers")
    void shouldNotMatchBeanWhenQualifiersDoNotMatch() {
        Syringe syringe = newSyringe(PlainService.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        Set<java.lang.reflect.Type> beanTypes = new HashSet<java.lang.reflect.Type>();
        beanTypes.add(PlainService.class);
        Set<java.lang.annotation.Annotation> beanQualifiers = new HashSet<java.lang.annotation.Annotation>();
        beanQualifiers.add(new BlueLiteral());

        Set<java.lang.annotation.Annotation> requiredQualifiers = new HashSet<java.lang.annotation.Annotation>();
        requiredQualifiers.add(Default.Literal.INSTANCE);

        boolean matches = container.isMatchingBean(beanTypes, beanQualifiers, PlainService.class, requiredQualifiers);
        assertTrue(!matches);
    }

    @Test
    @DisplayName("11.1.14 - BeanContainer.isMatchingEvent returns true when event type/qualifiers match observed event type/qualifiers")
    void shouldMatchEventByTypeAndQualifierSubset() {
        Syringe syringe = newSyringe(PlainService.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        Set<java.lang.annotation.Annotation> eventQualifiers = new HashSet<java.lang.annotation.Annotation>();
        eventQualifiers.add(new BlueLiteral());

        Set<java.lang.annotation.Annotation> observedQualifiers = new HashSet<java.lang.annotation.Annotation>();
        observedQualifiers.add(new BlueLiteral());

        boolean matches = container.isMatchingEvent(
                ResolutionEventPayload.class,
                eventQualifiers,
                ResolutionEventPayload.class,
                observedQualifiers
        );
        assertTrue(matches);
    }

    @Test
    @DisplayName("11.1.14 - BeanContainer.isMatchingEvent returns false when observed qualifiers are not satisfied")
    void shouldNotMatchEventWhenObservedQualifiersAreNotSatisfied() {
        Syringe syringe = newSyringe(PlainService.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        Set<java.lang.annotation.Annotation> eventQualifiers = new HashSet<java.lang.annotation.Annotation>();
        eventQualifiers.add(Default.Literal.INSTANCE);

        Set<java.lang.annotation.Annotation> observedQualifiers = new HashSet<java.lang.annotation.Annotation>();
        observedQualifiers.add(new BlueLiteral());

        boolean matches = container.isMatchingEvent(
                ResolutionEventPayload.class,
                eventQualifiers,
                Object.class,
                observedQualifiers
        );
        assertTrue(!matches);
    }

    @Test
    @DisplayName("11.1.12 - BeanContainer.getContexts(scope) returns BCE-registered context implementations and supports Singleton scope lookup")
    void shouldReturnBceRegisteredContextsAndSingletonFallback() {
        Syringe syringe = newSyringe(PlainService.class);
        syringe.addBuildCompatibleExtension(ContextRegisteringExtension.class.getName());
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();

        java.util.Collection<Context> customContexts = container.getContexts(CustomScoped.class);
        assertEquals(2, customContexts.size());

        java.util.Collection<Context> noImplContexts = container.getContexts(NoImplScoped.class);
        assertEquals(0, noImplContexts.size());

        java.util.Collection<Context> singletonContexts = container.getContexts(Singleton.class);
        assertTrue(singletonContexts.size() >= 1);
    }

    @Test
    @DisplayName("11.1.14 - BeanContainer.isMatchingBean validates null arguments and non-qualifier annotations")
    void shouldValidateIsMatchingBeanArgumentsAndQualifierTypes() {
        Syringe syringe = newSyringe(PlainService.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        Set<Type> beanTypes = new HashSet<Type>();
        beanTypes.add(PlainService.class);
        Set<Annotation> beanQualifiers = new HashSet<Annotation>();
        Set<Annotation> requiredQualifiers = new HashSet<Annotation>();

        assertThrows(IllegalArgumentException.class,
                () -> container.isMatchingBean(null, beanQualifiers, PlainService.class, requiredQualifiers));
        assertThrows(IllegalArgumentException.class,
                () -> container.isMatchingBean(beanTypes, null, PlainService.class, requiredQualifiers));
        assertThrows(IllegalArgumentException.class,
                () -> container.isMatchingBean(beanTypes, beanQualifiers, null, requiredQualifiers));
        assertThrows(IllegalArgumentException.class,
                () -> container.isMatchingBean(beanTypes, beanQualifiers, PlainService.class, null));

        beanQualifiers.add(new NotQualifierLiteral());
        assertThrows(IllegalArgumentException.class,
                () -> container.isMatchingBean(beanTypes, beanQualifiers, PlainService.class, requiredQualifiers));
    }

    @Test
    @DisplayName("11.1.14 - BeanContainer.isMatchingBean applies @Default/@Any semantics and ignores illegal bean types")
    void shouldApplyMatchingBeanDefaultAndAnySemanticsAndIgnoreIllegalBeanTypes() {
        Syringe syringe = newSyringe(PlainService.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();
        Set<Type> beanTypes = new HashSet<Type>();
        beanTypes.add(PlainService.class);
        beanTypes.add(Object.class);
        beanTypes.add(new TypeLiteral<List<?>>() {}.getType());

        Set<Annotation> emptyQualifiers = new HashSet<Annotation>();
        Set<Annotation> anyRequired = new HashSet<Annotation>();
        anyRequired.add(Any.Literal.INSTANCE);
        Set<Annotation> defaultRequired = new HashSet<Annotation>();
        defaultRequired.add(Default.Literal.INSTANCE);

        assertTrue(container.isMatchingBean(beanTypes, emptyQualifiers, PlainService.class, defaultRequired));
        assertTrue(container.isMatchingBean(beanTypes, emptyQualifiers, PlainService.class, anyRequired));
        assertTrue(!container.isMatchingBean(beanTypes, emptyQualifiers, new TypeLiteral<List<?>>() {}.getType(), emptyQualifiers));
    }

    @Test
    @DisplayName("11.1.14 - BeanContainer.isMatchingEvent validates null/type-variable/non-qualifier arguments and applies @Default/@Any semantics")
    void shouldValidateAndMatchEventsPerBeanContainerRules() {
        Syringe syringe = newSyringe(PlainService.class);
        syringe.setup();

        BeanContainer container = syringe.getBeanManager();

        Set<Annotation> eventQualifiers = new HashSet<Annotation>();
        Set<Annotation> observedQualifiers = new HashSet<Annotation>();
        observedQualifiers.add(Default.Literal.INSTANCE);
        assertTrue(container.isMatchingEvent(ResolutionEventPayload.class, eventQualifiers, ResolutionEventPayload.class, observedQualifiers));

        eventQualifiers.add(new BlueLiteral());
        assertTrue(!container.isMatchingEvent(ResolutionEventPayload.class, eventQualifiers, ResolutionEventPayload.class, observedQualifiers));

        Set<Annotation> observedAny = new HashSet<Annotation>();
        observedAny.add(Any.Literal.INSTANCE);
        assertTrue(container.isMatchingEvent(ResolutionEventPayload.class, eventQualifiers, ResolutionEventPayload.class, observedAny));

        assertThrows(IllegalArgumentException.class,
                () -> container.isMatchingEvent(null, new HashSet<Annotation>(), ResolutionEventPayload.class, new HashSet<Annotation>()));
        assertThrows(IllegalArgumentException.class,
                () -> container.isMatchingEvent(ResolutionEventPayload.class, null, ResolutionEventPayload.class, new HashSet<Annotation>()));
        assertThrows(IllegalArgumentException.class,
                () -> container.isMatchingEvent(ResolutionEventPayload.class, new HashSet<Annotation>(), null, new HashSet<Annotation>()));
        assertThrows(IllegalArgumentException.class,
                () -> container.isMatchingEvent(ResolutionEventPayload.class, new HashSet<Annotation>(), ResolutionEventPayload.class, null));

        Set<Annotation> nonQualifierSet = new HashSet<Annotation>();
        nonQualifierSet.add(new NotQualifierLiteral());
        assertThrows(IllegalArgumentException.class,
                () -> container.isMatchingEvent(ResolutionEventPayload.class, nonQualifierSet, ResolutionEventPayload.class, new HashSet<Annotation>()));

        Type typeVariableEventType = typeVariableRuntimeEventType();
        assertThrows(IllegalArgumentException.class,
                () -> container.isMatchingEvent(typeVariableEventType, new HashSet<Annotation>(), Object.class, new HashSet<Annotation>()));
    }

    private <X> Type typeVariableRuntimeEventType() {
        return new TypeLiteral<GenericRuntimeEvent<X>>() {}.getType();
    }

    @Test
    @DisplayName("11.1 - In CDI Lite mode, invoking BeanManager methods not inherited from BeanContainer is non-portable")
    void shouldRejectNonBeanContainerBeanManagerMethodsInCdiLiteMode() {
        Syringe syringe = newSyringe(DefaultOnlyBean.class);
        syringe.forceCdiLiteMode(true);
        syringe.setup();

        CDI.setCDIProvider(new RecordingProvider(syringe.getCDI()));
        BeanManager beanManager = CDI.current().getBeanManager();

        assertThrows(NonPortableBehaviourException.class, () -> beanManager.getPassivationCapableBean("id"));
    }

    @Test
    @DisplayName("11.1.1 - Calling CDI methods after shutdown starts is non-portable")
    void shouldRejectCdiMethodsOutsidePortableLifecycleWindow() {
        Syringe syringe = newSyringe(DefaultOnlyBean.class);
        syringe.setup();
        CDI<Object> cdi = syringe.getCDI();
        CDI.setCDIProvider(new RecordingProvider(cdi));

        syringe.shutdown();

        assertThrows(NonPortableBehaviourException.class, () -> CDI.current().getBeanContainer());
        assertThrows(NonPortableBehaviourException.class, () -> CDI.current().select(DefaultOnlyBean.class).get());
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        Set<Class<?>> included = new HashSet<Class<?>>(Arrays.asList(beanClasses));
        Class<?>[] allFixtures = new Class<?>[]{
                ConsumerBean.class,
                PlainService.class,
                DefaultOnlyBean.class,
                ApplicationService.class,
                DefaultContractBean.class,
                BlueContractBean.class,
                NamedService.class,
                EnabledNamedService.class,
                DisabledAlternativeNamedService.class,
                AmbiguousBeanA.class,
                AmbiguousBeanB.class,
                DefaultEventObserverBean.class,
                QualifiedEventObserverBean.class,
                EventClientBean.class,
                DefaultResolutionObserverBean.class,
                QualifiedResolutionObserverBean.class,
                FirstTraceInterceptor.class,
                SecondTraceInterceptor.class,
                TracedService.class
                , DestroyableDependentBean.class
        };
        for (Class<?> fixture : allFixtures) {
            if (!included.contains(fixture)) {
                syringe.exclude(fixture);
            }
        }
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        return syringe;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> T getBeanInstance(Syringe syringe, Class<T> beanClass) {
        BeanManager beanManager = syringe.getBeanManager();
        Set<Bean<?>> beans = beanManager.getBeans(beanClass);
        Bean<T> bean = (Bean<T>) beanManager.resolve((Set) beans);
        return (T) beanManager.getContext(bean.getScope()).get(bean, beanManager.createCreationalContext(bean));
    }

    private static void setDiscoveredProviders(Set<CDIProvider> providers) throws Exception {
        Field field = CDI.class.getDeclaredField("discoveredProviders");
        field.setAccessible(true);
        field.set(null, providers);
    }

    private static void setConfiguredProvider(CDIProvider provider) throws Exception {
        Field field = CDI.class.getDeclaredField("configuredProvider");
        field.setAccessible(true);
        field.set(null, provider);
    }

    @Dependent
    public static class ConsumerBean {
        @Inject
        BeanContainer container;

        public String lookupServiceName() {
            Set<Bean<?>> beans = container.getBeans(PlainService.class);
            Bean<? extends Object> bean = container.resolve((Set) beans);
            CreationalContext<?> ctx = container.createCreationalContext(bean);
            PlainService service = (PlainService) container.getReference(bean, PlainService.class, ctx);
            try {
                return service.name();
            } finally {
                ctx.release();
            }
        }
    }

    @Dependent
    public static class PlainService {
        public String name() {
            return "plain-service";
        }
    }

    @Dependent
    public static class DefaultOnlyBean {
        public String value() {
            return "default-only";
        }
    }

    @ApplicationScoped
    public static class ApplicationService {
        public String value() {
            return "app-service";
        }
    }

    public interface QualifiedContract {
        String id();
    }

    @Dependent
    public static class DefaultContractBean implements QualifiedContract {
        @Override
        public String id() {
            return "default";
        }
    }

    @Blue
    @Dependent
    public static class BlueContractBean implements QualifiedContract {
        @Override
        public String id() {
            return "blue";
        }
    }

    public static class GenericTypeHolder<T> {
        T value;
    }

    @Qualifier
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Blue {
    }

    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface NotQualifier {
    }

    public static final class BlueLiteral extends AnnotationLiteral<Blue> implements Blue {
        private static final long serialVersionUID = 1L;
    }

    public static final class NotQualifierLiteral extends AnnotationLiteral<NotQualifier> implements NotQualifier {
        private static final long serialVersionUID = 1L;
    }

    @NormalScope
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface CustomScoped {
    }

    @NormalScope
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface NoImplScoped {
    }

    public static class ContextRegisteringExtension implements BuildCompatibleExtension {
        @Discovery
        public void discovery(MetaAnnotations metaAnnotations) {
            metaAnnotations.addContext(CustomScoped.class, CustomContextImpl1.class);
            metaAnnotations.addContext(CustomScoped.class, CustomContextImpl2.class);
        }
    }

    public static class CustomContextImpl1 implements AlterableContext {
        @Override
        public void destroy(Contextual<?> contextual) {
        }

        @Override
        public Class<? extends Annotation> getScope() {
            return CustomScoped.class;
        }

        @Override
        public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
            return null;
        }

        @Override
        public <T> T get(Contextual<T> contextual) {
            return null;
        }

        @Override
        public boolean isActive() {
            return true;
        }
    }

    public static class CustomContextImpl2 implements AlterableContext {
        @Override
        public void destroy(Contextual<?> contextual) {
        }

        @Override
        public Class<? extends Annotation> getScope() {
            return CustomScoped.class;
        }

        @Override
        public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
            return null;
        }

        @Override
        public <T> T get(Contextual<T> contextual) {
            return null;
        }

        @Override
        public boolean isActive() {
            return false;
        }
    }

    @Named("namedService")
    @Dependent
    public static class NamedService {
        public String value() {
            return "named";
        }
    }

    @Named("sharedName")
    @Dependent
    public static class EnabledNamedService {
        public String value() {
            return "enabled";
        }
    }

    @Named("sharedName")
    @Alternative
    @Dependent
    public static class DisabledAlternativeNamedService {
        public String value() {
            return "disabled-alternative";
        }
    }

    public interface AmbiguousContract {
        String value();
    }

    @Dependent
    public static class AmbiguousBeanA implements AmbiguousContract {
        @Override
        public String value() {
            return "A";
        }
    }

    @Dependent
    public static class AmbiguousBeanB implements AmbiguousContract {
        @Override
        public String value() {
            return "B";
        }
    }

    public static class ContainerEventPayload {
        final String value;

        public ContainerEventPayload(String value) {
            this.value = value;
        }
    }

    @Dependent
    public static class DefaultEventObserverBean {
        public void observe(@Observes ContainerEventPayload payload) {
            EventRecorder.defaultObserverCalls++;
        }
    }

    @Dependent
    public static class QualifiedEventObserverBean {
        public void observe(@Observes @Blue ContainerEventPayload payload) {
            EventRecorder.blueObserverCalls++;
        }
    }

    @Dependent
    public static class EventClientBean {
        @Inject
        Event<Object> event;
    }

    public static final class EventRecorder {
        static int defaultObserverCalls;
        static int blueObserverCalls;

        static void reset() {
            defaultObserverCalls = 0;
            blueObserverCalls = 0;
        }
    }

    public static class ResolutionEventPayload {
        final String value;

        public ResolutionEventPayload(String value) {
            this.value = value;
        }
    }

    public static class GenericRuntimeEvent<T> {
        final T value;

        public GenericRuntimeEvent(T value) {
            this.value = value;
        }
    }

    @Dependent
    public static class DefaultResolutionObserverBean {
        public void observe(@Observes ResolutionEventPayload payload) {
            // no-op
        }
    }

    @Dependent
    public static class QualifiedResolutionObserverBean {
        public void observe(@Observes @Blue ResolutionEventPayload payload) {
            // no-op
        }
    }

    @InterceptorBinding
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Trace {
    }

    @Stereotype
    @Dependent
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ServiceStereotype {
    }

    public static final class TraceLiteral extends AnnotationLiteral<Trace> implements Trace {
        private static final long serialVersionUID = 1L;
    }

    @Trace
    @Interceptor
    @Priority(100)
    public static class FirstTraceInterceptor {
        @AroundInvoke
        public Object around(InvocationContext ctx) throws Exception {
            return ctx.proceed();
        }
    }

    @Trace
    @Interceptor
    @Priority(200)
    public static class SecondTraceInterceptor {
        @AroundInvoke
        public Object around(InvocationContext ctx) throws Exception {
            return ctx.proceed();
        }
    }

    @Trace
    @Dependent
    public static class TracedService {
        public String ping() {
            return "pong";
        }
    }

    @Dependent
    public static class DestroyableDependentBean {
        static int preDestroyCalls;

        static void reset() {
            preDestroyCalls = 0;
        }

        @jakarta.annotation.PreDestroy
        void onDestroy() {
            preDestroyCalls++;
        }
    }

    private static final class RecordingProvider implements CDIProvider {
        private final AtomicInteger calls = new AtomicInteger();
        private final CDI<Object> cdi;

        private RecordingProvider(CDI<Object> cdi) {
            this.cdi = cdi;
        }

        @Override
        public CDI<Object> getCDI() {
            calls.incrementAndGet();
            return cdi;
        }

        @Override
        public int getPriority() {
            return 1000;
        }
    }

    private static final class DefaultPriorityProvider implements CDIProvider {
        @Override
        public CDI<Object> getCDI() {
            return null;
        }
    }

    private static final class CdiStateSnapshot {
        private final boolean providerSetManually;
        private final Set<CDIProvider> discoveredProviders;
        private final CDIProvider configuredProvider;

        private CdiStateSnapshot(boolean providerSetManually, Set<CDIProvider> discoveredProviders, CDIProvider configuredProvider) {
            this.providerSetManually = providerSetManually;
            this.discoveredProviders = discoveredProviders;
            this.configuredProvider = configuredProvider;
        }

        private static CdiStateSnapshot capture() throws Exception {
            Field providerSetManuallyField = CDI.class.getDeclaredField("providerSetManually");
            Field discoveredProvidersField = CDI.class.getDeclaredField("discoveredProviders");
            Field configuredProviderField = CDI.class.getDeclaredField("configuredProvider");
            providerSetManuallyField.setAccessible(true);
            discoveredProvidersField.setAccessible(true);
            configuredProviderField.setAccessible(true);
            boolean providerSetManually = providerSetManuallyField.getBoolean(null);
            @SuppressWarnings("unchecked")
            Set<CDIProvider> discoveredProviders = (Set<CDIProvider>) discoveredProvidersField.get(null);
            CDIProvider configuredProvider = (CDIProvider) configuredProviderField.get(null);
            return new CdiStateSnapshot(providerSetManually, discoveredProviders, configuredProvider);
        }

        private void restore() throws Exception {
            Field providerSetManuallyField = CDI.class.getDeclaredField("providerSetManually");
            Field discoveredProvidersField = CDI.class.getDeclaredField("discoveredProviders");
            Field configuredProviderField = CDI.class.getDeclaredField("configuredProvider");
            providerSetManuallyField.setAccessible(true);
            discoveredProvidersField.setAccessible(true);
            configuredProviderField.setAccessible(true);
            providerSetManuallyField.setBoolean(null, providerSetManually);
            discoveredProvidersField.set(null, discoveredProviders);
            configuredProviderField.set(null, configuredProvider);
        }
    }
}
