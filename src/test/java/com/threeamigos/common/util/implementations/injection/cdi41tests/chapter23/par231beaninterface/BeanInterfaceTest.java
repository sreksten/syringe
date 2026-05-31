package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter23.par231beaninterface;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.events.ObserverMethodInfo;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.event.Reception;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Stereotype;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.EventContext;
import jakarta.enterprise.inject.spi.EventMetadata;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.InterceptionType;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.ObserverMethod;
import jakarta.enterprise.inject.spi.PassivationCapable;
import jakarta.enterprise.inject.spi.Prioritized;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Qualifier;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("23.1 The Bean Interface")
@Execution(ExecutionMode.SAME_THREAD)
public class BeanInterfaceTest {

    private static final Class<?>[] FIXTURE_CLASSES = new Class<?>[]{
            NamedStereotypedBean.class,
            ExampleStereotype.class,
            SimpleDependency.class,
            InjectionPointBean.class,
            RegularService.class,
            PriorityAlternativeService.class,
            ProducerHolder.class,
            ProducedContract.class,
            DecoratedContract.class,
            DecoratedBean.class,
            AuditDecorator.class,
            InterceptorContract.class,
            InterceptedBean.class,
            AuditInterceptor.class,
            AuditBinding.class,
            ObserverMetadataBean.class,
            ObserverPayload.class,
            ExtensionAnchor.class
    };

    @Test
    @DisplayName("23.1 - BeanAttributes exposes types qualifiers scope name stereotypes and alternative flag")
    void shouldExposeBeanAttributesOfManagedBean() {
        Syringe syringe = newSyringe(NamedStereotypedBean.class);
        syringe.setup();

        Bean<?> bean = resolveBean(syringe.getBeanManager(), NamedStereotypedBean.class);

        assertTrue(bean.getTypes().contains(NamedStereotypedBean.class));
        assertTrue(bean.getTypes().contains(Object.class));
        assertTrue(hasQualifier(bean, Default.class));
        assertTrue(hasQualifier(bean, Any.class));
        assertEquals(ApplicationScoped.class, bean.getScope());
        assertEquals("namedStereotypedBean", bean.getName());
        assertTrue(bean.getStereotypes().contains(ExampleStereotype.class));
        assertFalse(bean.isAlternative());
    }

    @Test
    @DisplayName("23.1 - isAlternative returns true for alternative beans and false otherwise")
    void shouldExposeAlternativeFlagThroughBeanAttributes() {
        Syringe syringe = newSyringe(RegularService.class, PriorityAlternativeService.class);
        syringe.setup();

        Bean<?> regular = resolveBean(syringe.getBeanManager(), RegularService.class);
        Bean<?> alternative = resolveBean(syringe.getBeanManager(), PriorityAlternativeService.class);

        assertFalse(regular.isAlternative());
        assertTrue(alternative.isAlternative());
    }

    @Test
    @DisplayName("23.1 - Bean.getBeanClass returns bean class of managed bean and producer declaring bean")
    void shouldExposeBeanClassForManagedAndProducerBeans() {
        Syringe syringe = newSyringe(ProducerHolder.class);
        syringe.setup();

        Bean<?> managed = resolveBean(syringe.getBeanManager(), ProducerHolder.class);
        Bean<?> producedByMethod = resolveBean(
                syringe.getBeanManager(),
                ProducedContract.class,
                ProducedByMethodLiteral.INSTANCE
        );
        Bean<?> producedByField = resolveBean(
                syringe.getBeanManager(),
                ProducedContract.class,
                ProducedByFieldLiteral.INSTANCE
        );

        assertEquals(ProducerHolder.class, managed.getBeanClass());
        assertEquals(ProducerHolder.class, producedByMethod.getBeanClass());
        assertEquals(ProducerHolder.class, producedByField.getBeanClass());
    }

    @Test
    @DisplayName("23.1 - Bean.getInjectionPoints returns injection points that are validated at initialization")
    void shouldExposeAndValidateInjectionPoints() {
        Syringe syringe = newSyringe(InjectionPointBean.class, SimpleDependency.class);
        syringe.setup();

        Bean<?> bean = resolveBean(syringe.getBeanManager(), InjectionPointBean.class);
        Set<InjectionPoint> injectionPoints = bean.getInjectionPoints();

        assertEquals(2, injectionPoints.size());
        Set<Type> types = new HashSet<Type>();
        for (InjectionPoint injectionPoint : injectionPoints) {
            types.add(injectionPoint.getType());
        }
        assertTrue(types.contains(SimpleDependency.class));
    }

    @Test
    @DisplayName("23.1 - An instance of Bean exists for every enabled bean")
    void shouldProvideBeanInstanceForEachEnabledBean() {
        Syringe syringe = newSyringe(RegularService.class, PriorityAlternativeService.class, ProducerHolder.class);
        syringe.setup();

        assertNotNull(resolveBean(syringe.getBeanManager(), RegularService.class));
        assertNotNull(resolveBean(syringe.getBeanManager(), PriorityAlternativeService.class));
        assertNotNull(resolveBean(syringe.getBeanManager(), ProducerHolder.class));
    }

    @Test
    @DisplayName("23.1 - Portable extensions may register custom Bean implementations in AfterBeanDiscovery")
    void shouldAllowPortableExtensionToRegisterCustomBean() {
        PortableCustomBeanExtension.reset();
        Syringe syringe = newSyringe(ExtensionAnchor.class);
        syringe.addExtension(PortableCustomBeanExtension.class.getName());
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        Bean<?> bean = resolveBean(beanManager, ExtensionAddedService.class, SyntheticBeanLiteral.INSTANCE);
        assertEquals(ExtensionAddedService.class, bean.getBeanClass());
        assertNotNull(PortableCustomBeanExtension.REGISTERED);
        assertTrue(bean instanceof PassivationCapable);
        assertNotNull(beanManager.getPassivationCapableBean(PortableCustomBeanExtension.REGISTERED.getId()));
    }

    @Test
    @DisplayName("23.1.1 - Bean object for an enabled decorator implements jakarta.enterprise.inject.spi.Decorator")
    void shouldExposeEnabledDecoratorAsDecoratorSpiBean() {
        Syringe syringe = newSyringe(DecoratedContract.class, DecoratedBean.class, AuditDecorator.class);
        syringe.setup();

        List<jakarta.enterprise.inject.spi.Decorator<?>> decorators = syringe.getBeanManager().resolveDecorators(
                Collections.<Type>singleton(DecoratedContract.class),
                Default.Literal.INSTANCE
        );

        assertTrue(decorators.stream().anyMatch(d -> d.getBeanClass().equals(AuditDecorator.class)));
    }

    @Test
    @DisplayName("23.1.1 - Decorator SPI metadata exposes decorated types, delegate type and delegate qualifiers")
    void shouldExposeDecoratorMetadataThroughDecoratorInterface() {
        Syringe syringe = newSyringe(DecoratedContract.class, DecoratedBean.class, AuditDecorator.class);
        syringe.setup();

        List<jakarta.enterprise.inject.spi.Decorator<?>> decorators = syringe.getBeanManager().resolveDecorators(
                Collections.<Type>singleton(DecoratedContract.class),
                Default.Literal.INSTANCE
        );

        jakarta.enterprise.inject.spi.Decorator<?> decorator = decorators.stream()
                .filter(d -> d.getBeanClass().equals(AuditDecorator.class))
                .findFirst()
                .orElseThrow(() -> new AssertionError("AuditDecorator should be resolved as enabled decorator"));

        assertTrue(decorator.getDecoratedTypes().contains(DecoratedContract.class));

        Type delegateType = decorator.getDelegateType();
        if (delegateType instanceof ParameterizedType) {
            assertEquals(DecoratedContract.class, ((ParameterizedType) delegateType).getRawType());
        } else {
            assertEquals(DecoratedContract.class, delegateType);
        }
        assertTrue(decorator.getDelegateQualifiers().stream()
                .anyMatch(annotation -> annotation.annotationType().equals(Any.class)));
    }

    @Test
    @DisplayName("23.1.2 - Bean object for enabled interceptor implements jakarta.enterprise.inject.spi.Interceptor")
    void shouldExposeEnabledInterceptorAsInterceptorSpiBean() {
        Syringe syringe = newSyringe(InterceptedBean.class, AuditInterceptor.class);
        syringe.setup();

        List<jakarta.enterprise.inject.spi.Interceptor<?>> interceptors =
                syringe.getBeanManager().resolveInterceptors(InterceptionType.AROUND_INVOKE, AuditBindingLiteral.INSTANCE);

        assertTrue(interceptors.stream().anyMatch(i -> i.getBeanClass().equals(AuditInterceptor.class)));
    }

    @Test
    @DisplayName("23.1.2 - Interceptor SPI exposes bindings, intercepts() and intercept() behavior")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldExposeInterceptorSpiMetadataAndInvocation() throws Exception {
        Syringe syringe = newSyringe(InterceptedBean.class, AuditInterceptor.class);
        syringe.setup();

        jakarta.enterprise.inject.spi.Interceptor interceptor = (jakarta.enterprise.inject.spi.Interceptor) syringe.getBeanManager()
                .resolveInterceptors(InterceptionType.AROUND_INVOKE, AuditBindingLiteral.INSTANCE)
                .stream()
                .filter(i -> i.getBeanClass().equals(AuditInterceptor.class))
                .findFirst()
                .orElseThrow(() -> new AssertionError("AuditInterceptor should be resolved"));

        boolean bindingFound = false;
        for (Object binding : interceptor.getInterceptorBindings()) {
            if (binding instanceof Annotation &&
                    ((Annotation) binding).annotationType().equals(AuditBinding.class)) {
                bindingFound = true;
                break;
            }
        }
        assertTrue(bindingFound);
        assertTrue(interceptor.intercepts(InterceptionType.AROUND_INVOKE));
        assertFalse(interceptor.intercepts(InterceptionType.POST_CONSTRUCT));

        AuditInterceptor instance = new AuditInterceptor();
        InvocationContext invocationContext = new NoopInvocationContext();
        Object result = interceptor.intercept(InterceptionType.AROUND_INVOKE, instance, invocationContext);

        assertEquals("proceed", result);
        assertTrue(instance.invoked);
    }

    @Test
    @DisplayName("23.1.3 - ObserverMethod exposes bean class, declaring bean, observed type/qualifiers, reception, transaction phase, priority and async metadata")
    void shouldExposeObserverMethodMetadata() {
        Syringe syringe = newSyringe(ObserverMetadataBean.class);
        syringe.setup();

        Set<ObserverMethod<? super ObserverPayload>> defaultObservers =
                syringe.getBeanManager().resolveObserverMethods(new ObserverPayload("default"));
        Set<ObserverMethod<? super ObserverPayload>> qualifiedObservers =
                syringe.getBeanManager().resolveObserverMethods(new ObserverPayload("qualified"), new ObservedKindLiteral("gold"));

        ObserverMethod<?> always = defaultObservers.stream()
                .filter(o -> o.getReception() == Reception.ALWAYS
                        && o.getTransactionPhase() == TransactionPhase.IN_PROGRESS
                        && o.getPriority() == jakarta.interceptor.Interceptor.Priority.APPLICATION + 500)
                .findFirst()
                .orElseThrow(() -> new AssertionError("ALWAYS observer with default priority should be present"));

        ObserverMethod<?> conditional = defaultObservers.stream()
                .filter(o -> o.getReception() == Reception.IF_EXISTS)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Conditional observer should be present"));

        ObserverMethod<?> transactional = defaultObservers.stream()
                .filter(o -> o.getTransactionPhase() == TransactionPhase.AFTER_COMPLETION)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Transactional observer should be present"));

        ObserverMethod<?> qualified = qualifiedObservers.stream()
                .filter(o -> o.getPriority() == 7)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Qualified observer should be present"));
        ObserverMethod<?> asyncObserver = createObserverMethodFromInfo(
                (BeanManagerImpl) syringe.getBeanManager(),
                observerInfoByMethodName(syringe, "onAsync")
        );

        assertEquals(ObserverMetadataBean.class, always.getBeanClass());
        assertNotNull(always.getDeclaringBean());
        assertEquals(ObserverMetadataBean.class, always.getDeclaringBean().getBeanClass());
        assertEquals(ObserverPayload.class, always.getObservedType());
        assertEquals(Reception.ALWAYS, always.getReception());
        assertEquals(TransactionPhase.IN_PROGRESS, always.getTransactionPhase());
        assertEquals(jakarta.interceptor.Interceptor.Priority.APPLICATION + 500, always.getPriority());
        assertFalse(always.isAsync());

        assertEquals(Reception.IF_EXISTS, conditional.getReception());
        assertEquals(TransactionPhase.AFTER_COMPLETION, transactional.getTransactionPhase());
        assertFalse(conditional.isAsync());
        assertFalse(transactional.isAsync());
        assertTrue(asyncObserver.isAsync());
        assertTrue(qualified.getObservedQualifiers().stream()
                .anyMatch(annotation -> annotation.annotationType().equals(ObservedKind.class)));
        assertEquals(7, qualified.getPriority());
    }

    @Test
    @DisplayName("23.1.3 - ObserverMethod notify(event) and notify(eventContext) invoke observer notification")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldInvokeObserverThroughBothNotifyOverloads() {
        ObserverRecorder.reset();

        Syringe syringe = newSyringe(ObserverMetadataBean.class);
        syringe.setup();

        ObserverMethod observer = syringe.getBeanManager()
                .resolveObserverMethods(new ObserverPayload("lookup"))
                .stream()
                .filter(o -> o.getReception() == Reception.ALWAYS
                        && o.getTransactionPhase() == TransactionPhase.IN_PROGRESS)
                .findFirst()
                .orElseThrow(() -> new AssertionError("ALWAYS observer should be resolvable"));

        observer.notify(new ObserverPayload("direct"));
        observer.notify(new EventContext<ObserverPayload>() {
            @Override
            public ObserverPayload getEvent() {
                return new ObserverPayload("context");
            }

            @Override
            public EventMetadata getMetadata() {
                return new EventMetadata() {
                    @Override
                    public InjectionPoint getInjectionPoint() {
                        return null;
                    }

                    @Override
                    public Type getType() {
                        return ObserverPayload.class;
                    }

                    @Override
                    public Set<Annotation> getQualifiers() {
                        return Collections.<Annotation>emptySet();
                    }
                };
            }
        });

        assertTrue(ObserverRecorder.values().contains("always:direct"));
        assertTrue(ObserverRecorder.values().contains("always:context"));
    }

    @Test
    @DisplayName("23.1.3 - An ObserverMethod instance exists for every observer method of every enabled bean")
    void shouldProvideObserverMethodInstanceForEachEnabledBeanObserverMethod() {
        ObserverRecorder.reset();
        Syringe syringe = newSyringe(ObserverMetadataBean.class);
        syringe.setup();

        Set<ObserverMethod<? super ObserverPayload>> defaultObservers =
                syringe.getBeanManager().resolveObserverMethods(new ObserverPayload("default"));
        Set<ObserverMethod<? super ObserverPayload>> qualifiedObservers =
                syringe.getBeanManager().resolveObserverMethods(new ObserverPayload("qualified"), new ObservedKindLiteral("gold"));

        assertEquals(5, observerInfosForBean(syringe, ObserverMetadataBean.class).size());
        assertTrue(defaultObservers.size() >= 3);
        assertTrue(qualifiedObservers.stream().anyMatch(o -> o.getPriority() == 7));

        syringe.getBeanManager().getEvent().select(ObserverPayload.class)
                .fireAsync(new ObserverPayload("async-check"))
                .toCompletableFuture()
                .join();
        assertTrue(ObserverRecorder.values().contains("async:async-check"));
    }

    @Test
    @DisplayName("23.1.4 - Prioritized interface defines programmatic priority via getPriority()")
    void shouldExposePrioritizedContract() throws Exception {
        Method getPriority = Prioritized.class.getMethod("getPriority");
        assertEquals(int.class, getPriority.getReturnType());
        assertTrue(Modifier.isPublic(getPriority.getModifiers()));

        Prioritized prioritized = new CustomPrioritized(77);
        assertEquals(77, prioritized.getPriority());
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        Set<Class<?>> included = new HashSet<Class<?>>(Arrays.asList(beanClasses));
        for (Class<?> fixture : FIXTURE_CLASSES) {
            if (!included.contains(fixture)) {
                syringe.exclude(fixture);
            }
        }
        return syringe;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Bean<?> resolveBean(BeanManager beanManager, Class<?> type, Annotation... qualifiers) {
        Set<Bean<?>> beans = beanManager.getBeans(type, qualifiers);
        return beanManager.resolve((Set) beans);
    }

    private List<ObserverMethodInfo> observerInfosForBean(Syringe syringe, Class<?> beanClass) {
        List<ObserverMethodInfo> infos = new ArrayList<ObserverMethodInfo>();
        for (ObserverMethodInfo info : syringe.getKnowledgeBase().getObserverMethodInfos()) {
            Method method = info.getObserverMethod();
            if (method != null && beanClass.equals(method.getDeclaringClass())) {
                infos.add(info);
            }
        }
        return infos;
    }

    private ObserverMethodInfo observerInfoByMethodName(Syringe syringe, String methodName) {
        for (ObserverMethodInfo info : observerInfosForBean(syringe, ObserverMetadataBean.class)) {
            Method method = info.getObserverMethod();
            if (method != null && methodName.equals(method.getName())) {
                return info;
            }
        }
        throw new AssertionError("ObserverMethodInfo not found for method: " + methodName);
    }

    @SuppressWarnings("unchecked")
    private ObserverMethod<?> createObserverMethodFromInfo(BeanManagerImpl beanManager, ObserverMethodInfo info) {
        try {
            Method factory = BeanManagerImpl.class.getDeclaredMethod("createObserverMethod", ObserverMethodInfo.class);
            factory.setAccessible(true);
            return (ObserverMethod<?>) factory.invoke(beanManager, info);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new AssertionError("Unable to create ObserverMethod wrapper from metadata", e);
        }
    }

    private boolean hasQualifier(Bean<?> bean, Class<? extends Annotation> qualifierType) {
        for (Annotation qualifier : bean.getQualifiers()) {
            if (qualifierType.equals(qualifier.annotationType())) {
                return true;
            }
        }
        return false;
    }

    static class CustomPrioritized implements Prioritized {
        private final int priority;

        CustomPrioritized(int priority) {
            this.priority = priority;
        }

        @Override
        public int getPriority() {
            return priority;
        }
    }

    @Stereotype
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @ApplicationScoped
    public @interface ExampleStereotype {
    }

    @ExampleStereotype
    @Named("namedStereotypedBean")
    public static class NamedStereotypedBean {
    }

    @ApplicationScoped
    public static class SimpleDependency {
    }

    @ApplicationScoped
    public static class InjectionPointBean {
        @Inject
        SimpleDependency fieldDependency;

        @Inject
        public void init(SimpleDependency methodDependency) {
            // no-op
        }
    }

    @ApplicationScoped
    public static class RegularService {
    }

    @Alternative
    @Priority(1)
    @ApplicationScoped
    public static class PriorityAlternativeService {
    }

    public interface ProducedContract {
        String value();
    }

    @Qualifier
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ProducedByMethod {
    }

    @Qualifier
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ProducedByField {
    }

    public static final class ProducedByMethodLiteral
            extends AnnotationLiteral<ProducedByMethod> implements ProducedByMethod {
        public static final ProducedByMethodLiteral INSTANCE = new ProducedByMethodLiteral();
    }

    public static final class ProducedByFieldLiteral
            extends AnnotationLiteral<ProducedByField> implements ProducedByField {
        public static final ProducedByFieldLiteral INSTANCE = new ProducedByFieldLiteral();
    }

    @ApplicationScoped
    public static class ProducerHolder {
        @Produces
        @ProducedByMethod
        ProducedContract producedByMethod() {
            return new ProducedValue("method");
        }

        @Produces
        @ProducedByField
        ProducedContract producedByField = new ProducedValue("field");
    }

    public static class ProducedValue implements ProducedContract, Serializable {
        private final String value;

        public ProducedValue(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }
    }

    public static class ExtensionAnchor {
    }

    public interface DecoratedContract {
        String call();
    }

    @ApplicationScoped
    public static class DecoratedBean implements DecoratedContract {
        @Override
        public String call() {
            return "bean";
        }
    }

    @jakarta.decorator.Decorator
    @Priority(1)
    public static class AuditDecorator implements DecoratedContract {
        @Inject
        @jakarta.decorator.Delegate
        @Any
        DecoratedContract delegate;

        @Override
        public String call() {
            return "decorated:" + delegate.call();
        }
    }

    @InterceptorBinding
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface AuditBinding {
    }

    public static final class AuditBindingLiteral
            extends AnnotationLiteral<AuditBinding> implements AuditBinding {
        public static final AuditBindingLiteral INSTANCE = new AuditBindingLiteral();
    }

    public interface InterceptorContract {
        String run();
    }

    @ApplicationScoped
    @AuditBinding
    public static class InterceptedBean implements InterceptorContract {
        @Override
        public String run() {
            return "business";
        }
    }

    @jakarta.interceptor.Interceptor
    @AuditBinding
    @Priority(1)
    public static class AuditInterceptor {
        boolean invoked;

        @AroundInvoke
        public Object around(InvocationContext ctx) throws Exception {
            invoked = true;
            return ctx.proceed();
        }
    }

    private static class NoopInvocationContext implements InvocationContext {
        @Override
        public Object getTarget() {
            return null;
        }

        @Override
        public Method getMethod() {
            return null;
        }

        @Override
        public Constructor<?> getConstructor() {
            return null;
        }

        @Override
        public Object[] getParameters() {
            return new Object[0];
        }

        @Override
        public void setParameters(Object[] params) {
            // no-op
        }

        @Override
        public Map<String, Object> getContextData() {
            return Collections.emptyMap();
        }

        @Override
        public Object getTimer() {
            return null;
        }

        @Override
        public Object proceed() {
            return "proceed";
        }
    }

    public static class ObserverPayload {
        private final String value;

        public ObserverPayload(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    @Qualifier
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ObservedKind {
        String value();
    }

    public static final class ObservedKindLiteral
            extends AnnotationLiteral<ObservedKind> implements ObservedKind {
        private final String value;

        public ObservedKindLiteral(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }
    }

    @ApplicationScoped
    public static class ObserverMetadataBean {
        void onAlways(@Observes ObserverPayload payload) {
            ObserverRecorder.record("always:" + payload.value());
        }

        void onConditional(@Observes(notifyObserver = Reception.IF_EXISTS) ObserverPayload payload) {
            ObserverRecorder.record("conditional:" + payload.value());
        }

        void onTransactional(@Observes(during = TransactionPhase.AFTER_COMPLETION) ObserverPayload payload) {
            ObserverRecorder.record("transactional:" + payload.value());
        }

        void onAsync(@ObservesAsync ObserverPayload payload) {
            ObserverRecorder.record("async:" + payload.value());
        }

        @Priority(7)
        void onQualified(@Observes @ObservedKind("gold") ObserverPayload payload) {
            ObserverRecorder.record("qualified:" + payload.value());
        }
    }

    public static class ObserverRecorder {
        private static final List<String> VALUES = Collections.synchronizedList(new ArrayList<String>());

        static void reset() {
            VALUES.clear();
        }

        static void record(String value) {
            VALUES.add(value);
        }

        static List<String> values() {
            return new ArrayList<String>(VALUES);
        }
    }

    public static class PortableCustomBeanExtension implements Extension {
        static volatile ExtensionAddedBean REGISTERED;

        static void reset() {
            REGISTERED = null;
        }

        public void afterBeanDiscovery(@Observes AfterBeanDiscovery afterBeanDiscovery) {
            ExtensionAddedBean bean = new ExtensionAddedBean();
            REGISTERED = bean;
            afterBeanDiscovery.addBean(bean);
        }
    }

    public static class ExtensionAddedService implements Serializable {
        private final String id = UUID.randomUUID().toString();

        public String id() {
            return id;
        }
    }

    @Qualifier
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface SyntheticBeanQualifier {
    }

    public static final class SyntheticBeanLiteral
            extends AnnotationLiteral<SyntheticBeanQualifier> implements SyntheticBeanQualifier {
        public static final SyntheticBeanLiteral INSTANCE = new SyntheticBeanLiteral();
    }

    static class ExtensionAddedBean implements Bean<ExtensionAddedService>, PassivationCapable {
        private static final Set<Type> TYPES;
        private static final Set<Annotation> QUALIFIERS;

        static {
            Set<Type> types = new HashSet<Type>();
            types.add(ExtensionAddedService.class);
            types.add(Object.class);
            TYPES = Collections.unmodifiableSet(types);

            Set<Annotation> qualifiers = new HashSet<Annotation>();
            qualifiers.add(Any.Literal.INSTANCE);
            qualifiers.add(SyntheticBeanLiteral.INSTANCE);
            QUALIFIERS = Collections.unmodifiableSet(qualifiers);
        }

        private final String id = getClass().getPackage().getName() + ".ExtensionAddedBean#" +
                UUID.randomUUID().toString();

        @Override
        public String getId() {
            return id;
        }

        @Override
        public ExtensionAddedService create(jakarta.enterprise.context.spi.CreationalContext<ExtensionAddedService> context) {
            return new ExtensionAddedService();
        }

        @Override
        public void destroy(ExtensionAddedService instance,
                            jakarta.enterprise.context.spi.CreationalContext<ExtensionAddedService> context) {
            if (context != null) {
                context.release();
            }
        }

        @Override
        public Class<?> getBeanClass() {
            return ExtensionAddedService.class;
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
            return QUALIFIERS;
        }

        @Override
        public Class<? extends Annotation> getScope() {
            return ApplicationScoped.class;
        }

        @Override
        public Set<Class<? extends Annotation>> getStereotypes() {
            return Collections.emptySet();
        }

        @Override
        public Set<Type> getTypes() {
            return TYPES;
        }

        @Override
        public boolean isAlternative() {
            return false;
        }
    }
}
