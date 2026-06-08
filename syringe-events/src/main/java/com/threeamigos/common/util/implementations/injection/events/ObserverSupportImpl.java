package com.threeamigos.common.util.implementations.injection.events;

import com.threeamigos.common.util.implementations.injection.events.propagation.ConversationPropagationRegistry;
import com.threeamigos.common.util.implementations.injection.events.propagation.RegistryContextTokenProvider;
import com.threeamigos.common.util.implementations.injection.extensions.ExtensionsManager;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.injection.spi.spievents.ProcessObserverMethodImpl;
import com.threeamigos.common.util.implementations.injection.spi.spievents.ProcessSyntheticObserverMethodImpl;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.resolution.BeanImpl;
import com.threeamigos.common.util.implementations.injection.resolution.BeanResolver;
import com.threeamigos.common.util.implementations.injection.resolution.GenericTypeResolver;
import com.threeamigos.common.util.implementations.injection.scopes.ContextManager;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Reception;
import jakarta.enterprise.event.Shutdown;
import jakarta.enterprise.event.Startup;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.ObserverMethod;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.extractObserverQualifiers;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.findAnnotatedMethod;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.findAnnotatedParameter;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.getObservesAnnotationFrom;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.getObservesAsyncAnnotationFrom;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.getPriorityValueFromAnnotations;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.hasObservesAnnotationIn;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.hasObservesAsyncAnnotationIn;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.resolveWithAnnotationsFilter;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.getPriorityValue;
import static com.threeamigos.common.util.implementations.injection.util.BeansHelper.filterSpecializedBeans;
import static com.threeamigos.common.util.implementations.injection.spi.SPIUtils.isContainerLifecycleObservedType;
import static com.threeamigos.common.util.implementations.injection.util.BeansHelper.isBeanEnabledForObserverLifecycle;
import static com.threeamigos.common.util.implementations.injection.util.TypesHelper.extractRawClass;

/**
 * Full implementation of {@link ObserverSupport} that handles CDI event/observer processing.
 *
 * <p>This class is discovered via {@link java.util.ServiceLoader} when the {@code syringe-events}
 * module is on the classpath. It encapsulates all the observer-method scanning, SPI event firing,
 * and event dispatching logic that was previously in {@code Syringe}.
 *
 * <p>Eventually this class will be moved to the {@code syringe-events} module.
 */
public class ObserverSupportImpl implements ObserverSupport {

    private MessageHandler messageHandler;
    private KnowledgeBase knowledgeBase;
    private BeanManager beanManager;
    private ContextManager contextManager;

    @Override
    public void setMessageHandler(MessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }

    @Override
    public void setKnowledgeBase(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    @Override
    public void setBeanManager(BeanManager beanManager) {
        this.beanManager = beanManager;
    }

    @Override
    public void setContextManager(ContextManager contextManager) {
        this.contextManager = contextManager;
    }

    // -------------------------------------------------------------------------
    // Observer discovery
    // -------------------------------------------------------------------------

    @Override
    public void discoverObserverMethods(Class<?> beanClass, Bean<?> declaringBean) {
        for (Method method : collectObserverCandidateMethods(beanClass)) {
            ObserverMethodInfo info = toObserverInfoForLifecycleDispatch(method, declaringBean);
            if (info != null) {
                knowledgeBase.addObserverMethodInfo(info);
            }
        }
    }

    @Override
    public void registerRuntimeExtensionObserverMethods(Collection<Extension> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            return;
        }

        Set<String> existingKeys = new HashSet<>();
        for (ObserverMethodInfo info : toObserverMethodInfoList(knowledgeBase.getObserverMethodInfos())) {
            existingKeys.add(observerInfoKey(info));
        }

        for (Extension extension : extensions) {
            Bean<?> declaringBean = resolveExtensionDeclaringBean(extension);
            if (declaringBean == null) {
                continue;
            }

            for (Method method : collectObserverCandidateMethods(extension.getClass())) {
                Class<?> observedRawType = resolveObservedRawTypeForLifecycleObserver(method, declaringBean);
                if (observedRawType != null && isContainerLifecycleObservedType(observedRawType)) {
                    continue;
                }

                ObserverMethodInfo info = toObserverInfoForLifecycleDispatch(method, declaringBean);
                if (info == null) {
                    continue;
                }

                String key = observerInfoKey(info);
                if (!existingKeys.add(key)) {
                    continue;
                }
                knowledgeBase.addObserverMethodInfo(info);
            }
        }
    }

    // -------------------------------------------------------------------------
    // ProcessObserverMethod SPI event processing
    // -------------------------------------------------------------------------

    @Override
    public void processObserverMethodSpiEvents(ExtensionsManager extensionsManager) {
        Collection<ObserverMethodInfo> existing = toObserverMethodInfoList(knowledgeBase.getObserverMethodInfos());
        if (existing.isEmpty()) {
            // Fallback discovery so ProcessObserverMethod can still be delivered at lifecycle time
            // even when runtime observer registration is deferred to deployment validation.
            existing = discoverObserverMethodsForLifecycleDispatch();
        }
        List<ObserverMethodInfo> updated = new ArrayList<>();

        for (ObserverMethodInfo info : existing) {
            try {
                ObserverMethod<?> observer;
                AnnotatedMethod<?> annotatedMethod = null;

                if (info.isSynthetic()) {
                    observer = info.getSyntheticObserver();
                } else {
                    Method method = info.getObserverMethod();
                    AnnotatedType<?> annotatedType = beanManager.createAnnotatedType(method.getDeclaringClass());
                    annotatedMethod = findAnnotatedMethod(annotatedType, method);
                    observer = new ReflectiveObserverMethodAdapter<>(info,
                            new BeanResolver(knowledgeBase, contextManager),
                            contextManager);
                }

                if (info.isSynthetic()) {
                    ProcessSyntheticObserverMethodImpl<?, ?> event =
                            new ProcessSyntheticObserverMethodImpl<>(messageHandler, knowledgeBase, observer, null);
                    extensionsManager.fireEventToExtensions(event);
                    if (event.isVetoed()) {
                        continue;
                    }
                    ObserverMethod<?> finalObserver = event.getFinalObserverMethod();
                    if (finalObserver == observer) {
                        updated.add(info);
                    } else {
                        updated.add(toObserverMethodInfo(finalObserver, info.getDeclaringBean()));
                    }
                } else {
                    ProcessObserverMethodImpl<?, ?> event =
                            new ProcessObserverMethodImpl<>(messageHandler, knowledgeBase, observer, annotatedMethod);
                    extensionsManager.fireEventToExtensions(event);
                    if (event.isVetoed()) {
                        continue;
                    }
                    ObserverMethod<?> finalObserver = event.getFinalObserverMethod();
                    if (finalObserver == observer) {
                        updated.add(info);
                    } else {
                        updated.add(toObserverMethodInfo(finalObserver, info.getDeclaringBean()));
                    }
                }

            } catch (DefinitionException e) {
                throw e;
            } catch (Exception e) {
                throw new DefinitionException("Error processing observer method", e);
            }
        }

        knowledgeBase.getObserverMethodInfos().clear();
        List<ObserverMethodInfo> deduped = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ObserverMethodInfo info : updated) {
            String key = observerInfoKey(info);
            if (seen.add(key)) {
                deduped.add(info);
            }
        }
        knowledgeBase.getObserverMethodInfos().addAll(deduped);
    }

    // -------------------------------------------------------------------------
    // Event firing
    // -------------------------------------------------------------------------

    @Override
    public void fireEvent(Object event, Annotation... qualifiers) {
        Set<Annotation> qualifierSet = new HashSet<>();
        qualifierSet.add(Any.Literal.INSTANCE);
        if (qualifiers != null) {
            Collections.addAll(qualifierSet, qualifiers);
        }
        BeanResolver beanResolver = resolveEventBeanResolver();
        new EventImpl<>(event.getClass(), qualifierSet, knowledgeBase, beanResolver, contextManager,
                beanResolver.getTransactionServices(), getContextTokenProvider(), null, false).fire(event);
    }

    @Override
    public void fireEventAsync(Object event, Annotation... qualifiers) {
        Set<Annotation> qualifierSet = new HashSet<>();
        qualifierSet.add(Any.Literal.INSTANCE);
        if (qualifiers != null) {
            Collections.addAll(qualifierSet, qualifiers);
        }
        BeanResolver beanResolver = resolveEventBeanResolver();
        new EventImpl<>(event.getClass(), qualifierSet, knowledgeBase, beanResolver, contextManager,
                beanResolver.getTransactionServices(), getContextTokenProvider(), null, false).fireAsync(event);
    }

    @Override
    public void fireStartupEvent() {
        Set<Annotation> qualifiers = new HashSet<>();
        qualifiers.add(Any.Literal.INSTANCE);
        BeanResolver beanResolver = resolveEventBeanResolver();
        new EventImpl<Startup>(Startup.class, qualifiers, knowledgeBase, beanResolver, contextManager,
                beanResolver.getTransactionServices(), getContextTokenProvider(), null, true).fire(new Startup());
    }

    @Override
    public void fireShutdownEvent() {
        Set<Annotation> qualifiers = new HashSet<>();
        qualifiers.add(Any.Literal.INSTANCE);
        BeanResolver beanResolver = resolveEventBeanResolver();
        new EventImpl<Shutdown>(Shutdown.class, qualifiers, knowledgeBase, beanResolver, contextManager,
                beanResolver.getTransactionServices(), getContextTokenProvider(), null, true).fire(new Shutdown());
    }

    @Override
    public void fireContextInitializedEvent(Class<? extends Annotation> scopeType) {
        getRootEvent()
                .select(Object.class, jakarta.enterprise.context.Initialized.Literal.of(scopeType),
                        Any.Literal.INSTANCE)
                .fire(new Object());
    }

    @Override
    public void fireContextBeforeDestroyedEvent(Class<? extends Annotation> scopeType) {
        getRootEvent()
                .select(Object.class, jakarta.enterprise.context.BeforeDestroyed.Literal.of(scopeType),
                        Any.Literal.INSTANCE)
                .fire(new Object());
    }

    @Override
    public void fireContextDestroyedEvent(Class<? extends Annotation> scopeType) {
        getRootEvent()
                .select(Object.class, jakarta.enterprise.context.Destroyed.Literal.of(scopeType),
                        Any.Literal.INSTANCE)
                .fire(new Object());
    }

    @Override
    public Event<Object> getRootEvent() {
        Set<Annotation> qualifiers = new HashSet<>();
        qualifiers.add(jakarta.enterprise.inject.Default.Literal.INSTANCE);
        qualifiers.add(Any.Literal.INSTANCE);
        return createEvent(Object.class, qualifiers, null);
    }

    @Override
    public <T> Event<T> createEvent(Type payloadType, Set<Annotation> qualifiers) {
        return createEvent(payloadType, qualifiers, null);
    }

    @Override
    public <T> Event<T> createEvent(Type payloadType,
                                    Set<Annotation> qualifiers,
                                    InjectionPoint firingInjectionPoint) {
        BeanResolver beanResolver = resolveEventBeanResolver();
        Set<Annotation> qualifierSet = qualifiers != null ? qualifiers : Collections.emptySet();
        return new EventImpl<>(payloadType, qualifierSet, knowledgeBase, beanResolver, contextManager,
                beanResolver.getTransactionServices(), getContextTokenProvider(), firingInjectionPoint);
    }

    @Override
    public ContextTokenProvider getContextTokenProvider() {
        return new RegistryContextTokenProvider();
    }

    @Override
    public void clear() {
        EventImpl.clearStaticState();
        ConversationPropagationRegistry.clear();
    }

    private BeanResolver resolveEventBeanResolver() {
        if (beanManager instanceof BeanManagerImpl) {
            BeanResolver existing = ((BeanManagerImpl) beanManager).getBeanResolver();
            if (existing != null) {
                return existing;
            }
        }
        return new BeanResolver(knowledgeBase, contextManager);
    }

    // -------------------------------------------------------------------------
    // Fallback observer discovery helpers (used when KnowledgeBase is empty at
    // ProcessObserverMethod time, because CDI41InjectionValidator runs later)
    // -------------------------------------------------------------------------

    private Collection<ObserverMethodInfo> discoverObserverMethodsForLifecycleDispatch() {
        List<ObserverMethodInfo> out = new ArrayList<>();
        for (Bean<?> bean : filterObserverDeclaringBeansForLifecycleDispatch()) {
            Class<?> beanClass = bean.getBeanClass();
            for (Method method : collectObserverCandidateMethods(beanClass)) {
                ObserverMethodInfo info = toObserverInfoForLifecycleDispatch(method, bean);
                if (info != null) {
                    out.add(info);
                }
            }
        }
        return out;
    }

    private Set<Bean<?>> filterObserverDeclaringBeansForLifecycleDispatch() {
        Set<Class<?>> discoveredClasses = new HashSet<>(knowledgeBase.getClasses());
        Set<Bean<?>> candidates = new LinkedHashSet<>();

        for (Bean<?> bean : knowledgeBase.getBeans()) {
            if (!(bean instanceof BeanImpl<?>)) {
                continue;
            }
            Class<?> beanClass = bean.getBeanClass();
            if (beanClass == null || !discoveredClasses.contains(beanClass)) {
                continue;
            }
            if (!isBeanEnabledForObserverLifecycle(bean)) {
                continue;
            }
            candidates.add(bean);
        }

        return filterSpecializedBeans(candidates);
    }

    private List<Method> collectObserverCandidateMethods(Class<?> beanClass) {
        List<Class<?>> hierarchy = com.threeamigos.common.util.implementations.injection.util.ClassHelper
                .collectClassHierarchy(beanClass);

        Map<String, Method> bySignature = new LinkedHashMap<>();
        for (Class<?> type : hierarchy) {
            for (Method method : type.getDeclaredMethods()) {
                String signature = observerMethodSignature(method);
                bySignature.put(signature, method);
            }
        }
        return new ArrayList<>(bySignature.values());
    }

    private String observerMethodSignature(Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append(method.getName()).append('(');
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(parameterTypes[i].getName());
        }
        sb.append(')');
        return sb.toString();
    }

    private ObserverMethodInfo toObserverInfoForLifecycleDispatch(Method method, Bean<?> declaringBean) {
        int observesCount = 0;
        int observesAsyncCount = 0;
        Parameter observedParameter = null;
        Annotation[] observedParameterAnnotations = null;
        Type observedParameterBaseType = null;
        int observedParameterPosition = -1;
        AnnotatedMethod<?> annotatedMethod = null;
        AnnotatedType<?> override = declaringBean != null
                ? knowledgeBase.getAnnotatedTypeOverride(declaringBean.getBeanClass())
                : null;
        if (override != null) {
            annotatedMethod = findAnnotatedMethod(override, method);
        }

        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            AnnotatedParameter<?> annotatedParameter = annotatedMethod != null
                    ? findAnnotatedParameter(annotatedMethod, i)
                    : null;
            Annotation[] parameterAnnotations = annotatedParameter != null
                    ? annotatedParameter.getAnnotations().toArray(new Annotation[0])
                    : parameter.getAnnotations();
            Type parameterBaseType = annotatedParameter != null
                    ? annotatedParameter.getBaseType()
                    : parameter.getParameterizedType();

            if (hasObservesAnnotationIn(parameterAnnotations)) {
                observesCount++;
                observedParameter = parameter;
                observedParameterAnnotations = parameterAnnotations;
                observedParameterBaseType = parameterBaseType;
                observedParameterPosition = i;
            }
            if (hasObservesAsyncAnnotationIn(parameterAnnotations)) {
                observesAsyncCount++;
                observedParameter = parameter;
                observedParameterAnnotations = parameterAnnotations;
                observedParameterBaseType = parameterBaseType;
                observedParameterPosition = i;
            }
        }

        if (observesCount == 0 && observesAsyncCount == 0) {
            return null;
        }
        if (observesCount + observesAsyncCount != 1 || observedParameter == null) {
            return null;
        }

        if (resolveWithAnnotationsFilter(observedParameter) != null) {
            throw new DefinitionException("@WithAnnotations is only valid on extension observer parameters " +
                    "observing ProcessAnnotatedType: " +
                    method.getDeclaringClass().getName() + "." + method.getName());
        }

        boolean async = observesAsyncCount > 0;
        Type eventType = GenericTypeResolver.resolve(
                observedParameterBaseType != null ? observedParameterBaseType : observedParameter.getParameterizedType(),
                declaringBean.getBeanClass(),
                method.getDeclaringClass()
        );
        Set<Annotation> qualifiers = extractObserverQualifiers(
                observedParameterAnnotations != null ? observedParameterAnnotations : observedParameter.getAnnotations());
        Reception reception = Reception.ALWAYS;
        TransactionPhase transactionPhase = TransactionPhase.IN_PROGRESS;
        int priority = jakarta.interceptor.Interceptor.Priority.APPLICATION + 500;

        if (async) {
            jakarta.enterprise.event.ObservesAsync observesAsync = getObservesAsyncAnnotationFrom(
                    observedParameterAnnotations != null ? observedParameterAnnotations : observedParameter.getAnnotations());
            if (observesAsync != null) {
                reception = observesAsync.notifyObserver();
            }
        } else {
            jakarta.enterprise.event.Observes observes = getObservesAnnotationFrom(
                    observedParameterAnnotations != null ? observedParameterAnnotations : observedParameter.getAnnotations());
            if (observes != null) {
                reception = observes.notifyObserver();
                transactionPhase = observes.during();
            }
            Integer paramPriority = getPriorityValueFromAnnotations(
                    observedParameterAnnotations != null ? observedParameterAnnotations : observedParameter.getAnnotations());
            if (paramPriority != null) {
                priority = paramPriority;
            } else {
                Integer methodPriority = getPriorityValue(method);
                if (methodPriority != null) {
                    priority = methodPriority;
                }
            }
        }

        return new ObserverMethodInfo(
                method,
                eventType,
                qualifiers,
                reception,
                transactionPhase,
                async,
                declaringBean,
                priority,
                observedParameterPosition
        );
    }

    private ObserverMethodInfo toObserverMethodInfo(ObserverMethod<?> observer, Bean<?> declaringBean) {
        return new ObserverMethodInfo(
                observer.getObservedType(),
                observer.getObservedQualifiers(),
                observer.getReception(),
                observer.getTransactionPhase(),
                observer.getPriority(),
                observer.isAsync(),
                declaringBean,
                observer
        );
    }

    private ObserverMethodInfo toObserverMethodInfo(ObserverMethodMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        if (metadata instanceof ObserverMethodInfo) {
            return (ObserverMethodInfo) metadata;
        }
        if (metadata.isSynthetic()) {
            ObserverMethod<?> observer = metadata.getSyntheticObserver();
            if (observer == null) {
                return null;
            }
            return toObserverMethodInfo(observer, metadata.getDeclaringBean());
        }
        if (metadata.getObserverMethod() == null) {
            return null;
        }
        return new ObserverMethodInfo(
                metadata.getObserverMethod(),
                metadata.getEventType(),
                metadata.getQualifiers(),
                metadata.getReception(),
                metadata.getTransactionPhase(),
                metadata.isAsync(),
                metadata.getDeclaringBean(),
                metadata.getPriority(),
                metadata.getObservedParameterPosition()
        );
    }

    private List<ObserverMethodInfo> toObserverMethodInfoList(Collection<ObserverMethodMetadata> metadataCollection) {
        List<ObserverMethodInfo> infos = new ArrayList<>();
        if (metadataCollection == null || metadataCollection.isEmpty()) {
            return infos;
        }
        for (ObserverMethodMetadata metadata : metadataCollection) {
            ObserverMethodInfo info = toObserverMethodInfo(metadata);
            if (info != null) {
                infos.add(info);
            }
        }
        return infos;
    }

    private Bean<?> resolveExtensionDeclaringBean(Extension extension) {
        if (extension == null) {
            return null;
        }
        Class<?> extensionClass = extension.getClass();
        for (Bean<?> bean : knowledgeBase.getBeans()) {
            if (bean == null || bean.getBeanClass() == null) {
                continue;
            }
            if (bean.getBeanClass().equals(extensionClass)) {
                return bean;
            }
        }
        return null;
    }

    private Class<?> resolveObservedRawTypeForLifecycleObserver(Method method, Bean<?> declaringBean) {
        if (method == null || declaringBean == null || declaringBean.getBeanClass() == null) {
            return null;
        }

        int observesCount = 0;
        int observesAsyncCount = 0;
        Type observedParameterBaseType = null;

        AnnotatedMethod<?> annotatedMethod = null;
        AnnotatedType<?> override = knowledgeBase.getAnnotatedTypeOverride(declaringBean.getBeanClass());
        if (override != null) {
            annotatedMethod = findAnnotatedMethod(override, method);
        }

        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            AnnotatedParameter<?> annotatedParameter = annotatedMethod != null
                    ? findAnnotatedParameter(annotatedMethod, i)
                    : null;
            Annotation[] parameterAnnotations = annotatedParameter != null
                    ? annotatedParameter.getAnnotations().toArray(new Annotation[0])
                    : parameter.getAnnotations();
            Type parameterBaseType = annotatedParameter != null
                    ? annotatedParameter.getBaseType()
                    : parameter.getParameterizedType();

            if (hasObservesAnnotationIn(parameterAnnotations)) {
                observesCount++;
                observedParameterBaseType = parameterBaseType;
            }
            if (hasObservesAsyncAnnotationIn(parameterAnnotations)) {
                observesAsyncCount++;
                observedParameterBaseType = parameterBaseType;
            }
        }

        if (observesCount == 0 && observesAsyncCount == 0) {
            return null;
        }
        if (observesCount + observesAsyncCount != 1 || observedParameterBaseType == null) {
            return null;
        }

        Type resolvedObservedType = GenericTypeResolver.resolve(
                observedParameterBaseType,
                declaringBean.getBeanClass(),
                method.getDeclaringClass());
        return extractRawClass(resolvedObservedType);
    }

    private String observerInfoKey(ObserverMethodInfo info) {
        return ObserverMethodInfoKey.of(info);
    }
}
