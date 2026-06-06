package com.threeamigos.common.util.implementations.injection.extensions;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.annotations.AnnotatedMetadataHelper;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXml;
import com.threeamigos.common.util.implementations.injection.builtinbeans.ActivateRequestContextInterceptor;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.discovery.NonPortableBehaviourException;
import com.threeamigos.common.util.implementations.injection.events.ObserverMethodInfo;
import com.threeamigos.common.util.implementations.injection.events.ObserverMethodInfoKey;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.resolution.GenericTypeResolver;
import com.threeamigos.common.util.implementations.injection.spi.SPIUtils;
import com.threeamigos.common.util.implementations.injection.spi.spievents.*;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.event.Reception;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.spi.*;
import jakarta.enterprise.inject.spi.AnnotatedType;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotatedMetadataHelper.*;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationExtractors.getPriorityValue;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.*;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.hasDecoratorAnnotation;
import static com.threeamigos.common.util.implementations.injection.spi.SPIUtils.isBeforeShutdownLifecycleEvent;
import static com.threeamigos.common.util.implementations.injection.spi.SPIUtils.isContainerLifecycleObservedType;
import static com.threeamigos.common.util.implementations.injection.types.RawTypeExtractor.extractRawClass;

/**
 *
 * @author Stefano Reksten
 */
public class ExtensionsManagerImpl implements ExtensionsManager {

    private MessageHandler messageHandler;
    private KnowledgeBase knowledgeBase;
    private BeanManager beanManager;

    /**
     * Set of extension class names to be loaded.
     * Extensions must implement jakarta.enterprise.inject.spi.Extension.
     */
    private final Set<String> extensionClassNames = new HashSet<>();

    /**
     * Explicitly registered extension instances.
     */
    private final Set<Extension> extensionInstances = new HashSet<>();

    /**
     * Loaded extension instances.
     */
    private final List<Extension> extensions = new ArrayList<>();


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
    public void addExtension(String extensionClassName) {
        extensionClassNames.add(extensionClassName);
        messageHandler.info("Queued extension: " + extensionClassName);
    }

    @Override
    public void addExtension(Extension extension) {
        if (extension == null) {
            throw new IllegalArgumentException("extension cannot be null");
        }
        extensionInstances.add(extension);
        messageHandler.info("Queued extension instance: " + extension.getClass().getName());
    }

    /**
     * Loads portable extensions via ServiceLoader and explicitly registered class names.
     *
     * <p>Extensions are discovered through:
     * <ul>
     *   <li>META-INF/services/jakarta.enterprise.inject.spi.Extension (ServiceLoader)</li>
     *   <li>Explicitly registered via {@link #addExtension(String)}</li>
     * </ul>
     */
    public void loadExtensions() {
        messageHandler.info("Loading extensions");
        Set<String> loadedExtensionClassNames = new HashSet<>();

        for (Extension extension : extensionInstances) {
            String className = extension.getClass().getName();
            if (loadedExtensionClassNames.add(className)) {
                extensions.add(extension);
                messageHandler.info("Loaded extension instance: " + className);
            } else {
                messageHandler.warn("Skipped duplicate extension registration: " + className);
            }
        }

        // Load extensions via ServiceLoader (standard CDI discovery)
        ServiceLoader<Extension> serviceLoader = ServiceLoader.load(
                Extension.class,
                Thread.currentThread().getContextClassLoader()
        );

        for (Extension extension : serviceLoader) {
            String className = extension.getClass().getName();
            if (loadedExtensionClassNames.add(className)) {
                extensions.add(extension);
                messageHandler.info("Loaded extension: " + className);
            } else {
                messageHandler.warn("Skipped duplicate extension registration: " + className);
            }
        }

        int loadedCount = extensions.size();

        // Load explicitly registered extensions
        for (String className : extensionClassNames) {
            try {
                Class<?> extensionClass = Class.forName(className);
                if (!Extension.class.isAssignableFrom(extensionClass)) {
                    knowledgeBase.addDefinitionError("Extension class " + className + " does not implement the jakarta.enterprise.inject.spi.Extension interface");
                } else {
                    if (loadedExtensionClassNames.add(className)) {
                        Extension extension = (Extension) extensionClass.getDeclaredConstructor().newInstance();
                        extensions.add(extension);
                        messageHandler.info("Loaded extension: " + className);
                    } else {
                        messageHandler.warn("Skipped duplicate extension registration: " + className);
                    }
                }
                loadedCount++;
            } catch (Exception e) {
                knowledgeBase.addDefinitionError("Failed to load extension: " + className);
                messageHandler.exception("Failed to load extension: " + className, e);
            }
        }

        messageHandler.info("Loaded " + loadedCount + " extension(s)");
    }

    @Override
    public Collection<Extension> getExtensions() {
        return extensions;
    }

    @Override
    public Collection<String> getExtensionClassNames() {
        return extensionClassNames;
    }

    @Override
    public void registerRuntimeExtensionObserverMethods() {
        if (extensions.isEmpty()) {
            return;
        }

        Set<String> existingKeys = new HashSet<>();
        for (ObserverMethodInfo info : knowledgeBase.getObserverMethodInfos()) {
            existingKeys.add(observerInfoKey(info));
        }

        for (Extension extension : extensions) {
            Bean<?> declaringBean = resolveExtensionDeclaringBean(extension);
            if (declaringBean == null) {
                continue;
            }

            for (Method method : getExtensionObserverCandidateMethods(extension.getClass())) {
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

    @Override
    public void clear() {
        extensionClassNames.clear();
        extensionInstances.clear();
        extensions.clear();
    }

    private String observerInfoKey(ObserverMethodInfo info) {
        return ObserverMethodInfoKey.of(info);
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

    private Collection<Method> getExtensionObserverCandidateMethods(Class<?> extensionClass) {
        Map<String, Method> methodsBySignature = new LinkedHashMap<>();
        Class<?> current = extensionClass;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                String signature = method.getName() + Arrays.toString(method.getParameterTypes());
                methodsBySignature.putIfAbsent(signature, method);
            }
            current = current.getSuperclass();
        }
        return methodsBySignature.values();
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
            annotatedMethod = AnnotatedMetadataHelper.findAnnotatedMethod(override, method);
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
            annotatedMethod = AnnotatedMetadataHelper.findAnnotatedMethod(override, method);
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

    /**
     * Fires an event to all registered extensions by invoking their observer methods.
     *
     * <p>This method scans each extension for methods with parameters annotated with @Observes
     * that match the event type and invokes them with the event object.
     *
     * @param event event object to fire
     * @param <T> the event type
     */
    public <T> void fireEventToExtensions(T event) {
        Class<?> eventType = event != null ? event.getClass() : Object.class;
        boolean afterDeploymentValidationEvent = SPIUtils.isAfterDeploymentValidationLifecycleEvent(eventType);
        boolean beforeShutdownEvent = isBeforeShutdownLifecycleEvent(eventType);
        List<Syringe.ExtensionObserverInvocation> invocations = new ArrayList<>();

        // Collect all matching observer methods across all extensions, with priority
        for (Extension extension : extensions) {
            collectExtensionObserverMethods(extension, event, invocations);
        }

        // Sort by effective priority (ascending). Methods without explicit @Priority
        // use the CDI default observer priority (APPLICATION + 500).
        invocations.sort(Comparator.comparingInt(inv -> inv.priority));

        for (Syringe.ExtensionObserverInvocation invocation : invocations) {
            boolean lifecycleInvocationStarted = false;
            boolean extensionAwareInvocationStarted = false;
            try {
                if (event instanceof ObserverInvocationLifecycle) {
                    ((ObserverInvocationLifecycle) event).beginObserverInvocation();
                    lifecycleInvocationStarted = true;
                }
                if (event instanceof ExtensionAwareObserverInvocation) {
                    ((ExtensionAwareObserverInvocation) event).enterObserverInvocation(invocation.extension);
                    extensionAwareInvocationStarted = true;
                }
                invocation.invoke(event);
            } catch (Exception e) {
                Throwable cause = e;
                if (e instanceof InvocationTargetException &&
                        ((InvocationTargetException) e).getTargetException() != null) {
                    cause = ((InvocationTargetException) e).getTargetException();
                }
                if (cause instanceof DefinitionException) {
                    throw (DefinitionException) cause;
                }
                if (cause instanceof NonPortableBehaviourException) {
                    throw (NonPortableBehaviourException) cause;
                }
                if (SPIUtils.isDefinitionErrorLifecycleEvent(eventType)) {
                    throw new DefinitionException("Error invoking extension " +
                            invocation.extension.getClass().getName() + " for event " +
                            eventType.getSimpleName(), cause);
                }
                if (afterDeploymentValidationEvent) {
                    String causeMessage = cause.getMessage();
                    if (causeMessage == null || causeMessage.isEmpty()) {
                        causeMessage = cause.getClass().getName();
                    }
                    knowledgeBase.addError("[AfterDeploymentValidation] Observer exception from extension " +
                            invocation.extension.getClass().getName() + ": " + causeMessage);
                    continue;
                }
                if (beforeShutdownEvent) {
                    messageHandler.exception("Ignoring BeforeShutdown observer exception from extension " +
                            invocation.extension.getClass().getName(), cause instanceof Exception ? (Exception) cause : null);
                    continue;
                }
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                throw new DefinitionException("Error invoking extension " +
                        invocation.extension.getClass().getName() + " for event " +
                        eventType.getSimpleName(), cause);
            } finally {
                if (lifecycleInvocationStarted && event instanceof ObserverInvocationLifecycle) {
                    ((ObserverInvocationLifecycle) event).endObserverInvocation();
                }
                if (extensionAwareInvocationStarted && event instanceof ExtensionAwareObserverInvocation) {
                    ((ExtensionAwareObserverInvocation) event).exitObserverInvocation();
                }
            }
        }
    }

    private void collectExtensionObserverMethods(Extension extension,
                                                 Object event,
                                                 List<Syringe.ExtensionObserverInvocation> sink) {
        Class<?> eventType = event != null ? event.getClass() : Object.class;
        for (Method method : getExtensionObserverCandidateMethods(extension.getClass())) {
            Parameter[] parameters = method.getParameters();
            int observesParameterIndex = -1;

            for (int i = 0; i < parameters.length; i++) {
                Parameter parameter = parameters[i];
                if (hasObservesAnnotation(parameter)) {
                    observesParameterIndex = i;
                    break;
                }
            }

            if (observesParameterIndex >= 0) {
                validateWithAnnotationsUsage(method, observesParameterIndex, parameters);
            }

            for (int i = 0; i < parameters.length; i++) {
                Parameter parameter = parameters[i];
                if (hasObservesAnnotation(parameter)) {
                    Class<?> observedType = parameter.getType();
                    validateExtensionObserverStaticMethod(method, parameter, observedType);
                    if (!isObservedTypeApplicableToEvent(observedType, event)) {
                        continue;
                    }
                    if (observedType.isAssignableFrom(eventType) &&
                            matchesObservedGenericEventType(parameter, event)) {
                        int priority = resolvePriority(method, parameter);
                        Set<Class<? extends Annotation>> withAnnotationsFilter =
                                resolveWithAnnotationsFilter(parameter);
                        if (withAnnotationsFilter != null &&
                                !ProcessAnnotatedType.class.isAssignableFrom(observedType)) {
                            throw new DefinitionException("@WithAnnotations is only valid on ProcessAnnotatedType observer parameters: " +
                                    method.getDeclaringClass().getName() + "." + method.getName());
                        }
                        sink.add(new Syringe.ExtensionObserverInvocation(extension, method, i, priority, beanManager,
                                messageHandler, withAnnotationsFilter));
                    }
                }
            }
        }
    }

    private void validateWithAnnotationsUsage(Method method,
                                              int observesParameterIndex,
                                              Parameter[] parameters) {
        for (int i = 0; i < parameters.length; i++) {
            if (i == observesParameterIndex) {
                continue;
            }
            if (resolveWithAnnotationsFilter(parameters[i]) != null) {
                throw new DefinitionException("@WithAnnotations may only be declared on the @Observes event parameter: " +
                        method.getDeclaringClass().getName() + "." + method.getName());
            }
        }
    }

    private int resolvePriority(Method method, Parameter observedParameter) {
        Integer parameterPriority = getPriorityValue(observedParameter);
        if (parameterPriority != null) {
            return parameterPriority;
        }
        Integer methodPriority = getPriorityValue(method);
        if (methodPriority != null) {
            return methodPriority;
        }
        return jakarta.interceptor.Interceptor.Priority.APPLICATION + 500;
    }

    private void validateExtensionObserverStaticMethod(Method method,
                                                       Parameter observedParameter,
                                                       Class<?> observedType) {
        if (!Modifier.isStatic(method.getModifiers())) {
            return;
        }

        boolean lifecycleObservedType = SPIUtils.isContainerLifecycleObservedType(observedType);
        boolean objectObservedWithNoOrAnyQualifier =
                Object.class.equals(observedType) && hasNoQualifierOrOnlyAnyQualifier(observedParameter);

        if (lifecycleObservedType || objectObservedWithNoOrAnyQualifier) {
            throw new NonPortableBehaviourException("Static extension observer method " +
                    method.getDeclaringClass().getName() + "." + method.getName() +
                    " is non-portable for observed type " + observedType.getName());
        }
    }

    private boolean isObservedTypeApplicableToEvent(Class<?> observedType, Object event) {
        if (observedType == null || event == null) {
            return true;
        }

        return !ProcessProducer.class.equals(observedType) ||
                (!(event instanceof ProcessProducerMethod<?, ?>) && !(event instanceof ProcessProducerField<?, ?>));
    }

    private boolean matchesObservedGenericEventType(Parameter observerParameter, Object event) {
        if (event == null) {
            return true;
        }

        Type parameterizedObservedType = observerParameter.getParameterizedType();
        if (!(parameterizedObservedType instanceof ParameterizedType)) {
            return true;
        }

        ParameterizedType observedParameterizedType = (ParameterizedType) parameterizedObservedType;
        Type rawType = observedParameterizedType.getRawType();
        if (!(rawType instanceof Class)) {
            return true;
        }

        Type[] observedTypeArguments = observedParameterizedType.getActualTypeArguments();
        if (observedTypeArguments.length == 2 &&
                ProcessObserverMethod.class.isAssignableFrom((Class<?>) rawType)) {
            return matchesProcessObserverMethodTypeArguments(observedTypeArguments, event);
        }

        if (observedTypeArguments.length == 2) {
            return matchesObservedBinaryEventTypeArguments((Class<?>) rawType, observedTypeArguments, event);
        }

        if (observedTypeArguments.length != 1) {
            return true;
        }

        Class<?> discoveredType = extractObservedTypeForGenericEventMatch((Class<?>) rawType, event);
        if (discoveredType == null) {
            return true;
        }

        if (ProcessBeanAttributes.class.isAssignableFrom((Class<?>) rawType)) {
            return matchesProcessBeanAttributesTypeArgument(observedTypeArguments[0], discoveredType);
        }

        return matchesObservedTypeArgument(observedTypeArguments[0], discoveredType);
    }

    private boolean matchesProcessBeanAttributesTypeArgument(Type observedTypeArgument, Class<?> discoveredType) {
        if (observedTypeArgument instanceof Class<?>) {
            return observedTypeArgument.equals(discoveredType);
        }

        if (observedTypeArgument instanceof ParameterizedType) {
            Type rawObservedType = ((ParameterizedType) observedTypeArgument).getRawType();
            return rawObservedType instanceof Class<?> && rawObservedType.equals(discoveredType);
        }

        return matchesObservedTypeArgument(observedTypeArgument, discoveredType);
    }

    private boolean matchesProcessObserverMethodTypeArguments(Type[] observedTypeArguments, Object event) {
        ObserverMethod<?> observerMethod = extractObserverMethodFromPomEvent(event);
        if (observerMethod == null) {
            return true;
        }

        Type eventObservedType = observerMethod.getObservedType();
        Class<?> eventObservedRawType = extractRawClass(eventObservedType);
        Class<?> observerBeanClass = observerMethod.getBeanClass();

        if (eventObservedRawType != null &&
                !matchesObservedTypeArgument(observedTypeArguments[0], eventObservedRawType)) {
            return false;
        }
        return observerBeanClass == null ||
                matchesObservedTypeArgument(observedTypeArguments[1], observerBeanClass);
    }

    private boolean matchesObservedBinaryEventTypeArguments(Class<?> observedRawType,
                                                            Type[] observedTypeArguments,
                                                            Object event) {
        if (ProcessProducerMethod.class.isAssignableFrom(observedRawType) &&
                event instanceof ProcessProducerMethod<?, ?>) {
            AnnotatedMethod<?> producerMethod = extractAnnotatedProducerMethodFromProcessProducerMethodEvent(event);
            if (producerMethod == null) {
                return true;
            }
            Type producedType = producerMethod.getBaseType();
            Type declaringType = extractDeclaringType(producerMethod);
            return matchesObservedTypeArgumentsPair(observedTypeArguments, producedType, declaringType);
        }

        if (ProcessProducerField.class.isAssignableFrom(observedRawType) &&
                event instanceof ProcessProducerField<?, ?>) {
            AnnotatedField<?> producerField = extractAnnotatedProducerFieldFromProcessProducerFieldEvent(event);
            if (producerField == null) {
                return true;
            }
            Type producedType = producerField.getBaseType();
            Type declaringType = extractDeclaringType(producerField);
            return matchesObservedTypeArgumentsPair(observedTypeArguments, producedType, declaringType);
        }

        if (ProcessProducer.class.isAssignableFrom(observedRawType) &&
                event instanceof ProcessProducer<?, ?>) {
            AnnotatedMember<?> annotatedMember = extractAnnotatedMemberFromProcessProducerEvent(event);
            if (annotatedMember == null) {
                return true;
            }
            Type declaringType = extractDeclaringType(annotatedMember);
            Type producedType = annotatedMember.getBaseType();
            return matchesObservedTypeArgumentsPair(observedTypeArguments, declaringType, producedType);
        }

        if (ProcessInjectionPoint.class.isAssignableFrom(observedRawType) &&
                event instanceof ProcessInjectionPoint<?, ?>) {
            InjectionPoint injectionPoint = extractInjectionPointFromPipEvent(event);
            if (injectionPoint == null) {
                return true;
            }
            Bean<?> declaringBean = injectionPoint.getBean();
            Type declaringType = declaringBean != null ? declaringBean.getBeanClass() : null;
            Type injectionType = injectionPoint.getType();
            return matchesObservedTypeArgumentsPair(observedTypeArguments, declaringType, injectionType);
        }

        return true;
    }

    private Class<?> extractObservedTypeForGenericEventMatch(Class<?> observedRawType, Object event) {
        if (ProcessAnnotatedType.class.isAssignableFrom(observedRawType) &&
                event instanceof ProcessAnnotatedType<?>) {
            AnnotatedType<?> annotatedType = extractAnnotatedTypeFromPatEvent(event);
            return annotatedType != null ? annotatedType.getJavaClass() : null;
        }

        if (ProcessBeanAttributes.class.isAssignableFrom(observedRawType) &&
                event instanceof ProcessBeanAttributes<?>) {
            Annotated annotated = extractAnnotatedFromPbaEvent(event);
            if (annotated instanceof AnnotatedType<?>) {
                return ((AnnotatedType<?>) annotated).getJavaClass();
            }
            if (annotated instanceof AnnotatedMember<?>) {
                return extractRawClass(annotated.getBaseType());
            }
        }

        if (ProcessInjectionTarget.class.isAssignableFrom(observedRawType) &&
                event instanceof ProcessInjectionTarget<?>) {
            AnnotatedType<?> annotatedType = extractAnnotatedTypeFromPitEvent(event);
            return annotatedType != null ? annotatedType.getJavaClass() : null;
        }

        if (ProcessBean.class.isAssignableFrom(observedRawType) &&
                event instanceof ProcessBean<?>) {
            Bean<?> bean = extractBeanFromProcessBeanEvent(event);
            return bean != null ? bean.getBeanClass() : null;
        }

        return null;
    }

    private boolean matchesObservedTypeArgument(Type observedTypeArgument, Class<?> discoveredType) {
        if (observedTypeArgument instanceof Class<?>) {
            Class<?> observedClass = (Class<?>) observedTypeArgument;
            return observedClass.isAssignableFrom(discoveredType);
        }

        if (observedTypeArgument instanceof ParameterizedType) {
            Type rawObservedType = ((ParameterizedType) observedTypeArgument).getRawType();
            return rawObservedType instanceof Class<?> &&
                    ((Class<?>) rawObservedType).isAssignableFrom(discoveredType);
        }

        if (observedTypeArgument instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) observedTypeArgument;

            Type[] upperBounds = wildcardType.getUpperBounds();
            for (Type upperBound : upperBounds) {
                if (upperBound instanceof Class<?> &&
                        !((Class<?>) upperBound).isAssignableFrom(discoveredType)) {
                    return false;
                }
            }

            Type[] lowerBounds = wildcardType.getLowerBounds();
            for (Type lowerBound : lowerBounds) {
                if (lowerBound instanceof Class<?> &&
                        !discoveredType.isAssignableFrom((Class<?>) lowerBound)) {
                    return false;
                }
            }

            return true;
        }

        // TypeVariable and other reflective forms are treated as unrestricted for PAT matching.
        return true;
    }

    private Bean<?> extractBeanFromProcessBeanEvent(Object event) {
        if (event instanceof ProcessBeanImpl<?>) {
            return ((ProcessBeanImpl<?>) event).getBeanInternal();
        }
        if (event instanceof ProcessSyntheticBeanImpl<?>) {
            return ((ProcessSyntheticBeanImpl<?>) event).getBeanInternal();
        }
        try {
            return ((ProcessBean<?>) event).getBean();
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private ObserverMethod<?> extractObserverMethodFromPomEvent(Object event) {
        if (event instanceof ProcessObserverMethodImpl<?, ?>) {
            return ((ProcessObserverMethodImpl<?, ?>) event).getObserverMethodInternal();
        }
        if (event instanceof ProcessSyntheticObserverMethodImpl<?, ?>) {
            return ((ProcessSyntheticObserverMethodImpl<?, ?>) event).getObserverMethodInternal();
        }
        try {
            return ((ProcessObserverMethod<?, ?>) event).getObserverMethod();
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private AnnotatedType<?> extractAnnotatedTypeFromPatEvent(Object event) {
        if (event instanceof ProcessAnnotatedTypeImpl<?>) {
            return ((ProcessAnnotatedTypeImpl<?>) event).getAnnotatedTypeInternal();
        }
        try {
            return ((ProcessAnnotatedType<?>) event).getAnnotatedType();
        } catch (IllegalStateException ignored) {
            // Guarded lifecycle event implementations may reject access outside invocation.
            return null;
        }
    }

    private AnnotatedType<?> extractAnnotatedTypeFromPitEvent(Object event) {
        if (event instanceof ProcessInjectionTargetImpl<?>) {
            return ((ProcessInjectionTargetImpl<?>) event).getAnnotatedTypeInternal();
        }
        try {
            return ((ProcessInjectionTarget<?>) event).getAnnotatedType();
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private Annotated extractAnnotatedFromPbaEvent(Object event) {
        if (event instanceof ProcessBeanAttributesImpl<?>) {
            return ((ProcessBeanAttributesImpl<?>) event).getAnnotatedInternal();
        }
        try {
            return ((ProcessBeanAttributes<?>) event).getAnnotated();
        } catch (IllegalStateException ignored) {
            // Guarded lifecycle event implementations may reject access outside invocation.
            return null;
        }
    }

    private InjectionPoint extractInjectionPointFromPipEvent(Object event) {
        if (event instanceof ProcessInjectionPointImpl<?, ?>) {
            return ((ProcessInjectionPointImpl<?, ?>) event).getInjectionPointInternal();
        }
        try {
            return ((ProcessInjectionPoint<?, ?>) event).getInjectionPoint();
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private AnnotatedMember<?> extractAnnotatedMemberFromProcessProducerEvent(Object event) {
        if (event instanceof ProcessProducerImpl<?, ?>) {
            return ((ProcessProducerImpl<?, ?>) event).getAnnotatedMember();
        }
        try {
            return ((ProcessProducer<?, ?>) event).getAnnotatedMember();
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private AnnotatedMethod<?> extractAnnotatedProducerMethodFromProcessProducerMethodEvent(Object event) {
        if (event instanceof ProcessProducerMethodImpl<?, ?>) {
            return ((ProcessProducerMethodImpl<?, ?>) event).getAnnotatedProducerMethod();
        }
        try {
            return ((ProcessProducerMethod<?, ?>) event).getAnnotatedProducerMethod();
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private AnnotatedField<?> extractAnnotatedProducerFieldFromProcessProducerFieldEvent(Object event) {
        if (event instanceof ProcessProducerFieldImpl<?, ?>) {
            return ((ProcessProducerFieldImpl<?, ?>) event).getAnnotatedProducerField();
        }
        try {
            return ((ProcessProducerField<?, ?>) event).getAnnotatedProducerField();
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private Class<?> extractDeclaringType(AnnotatedMember<?> member) {
        if (member == null) {
            return null;
        }
        AnnotatedType<?> declaringType = member.getDeclaringType();
        if (declaringType != null) {
            return declaringType.getJavaClass();
        }
        Member memberRef = member.getJavaMember();
        return memberRef != null ? memberRef.getDeclaringClass() : null;
    }

    private boolean matchesObservedTypeArgumentsPair(Type[] observedTypeArguments,
                                                     Type discoveredFirstType,
                                                     Type discoveredSecondType) {
        if (observedTypeArguments == null || observedTypeArguments.length != 2) {
            return true;
        }
        if (discoveredFirstType == null) {
            if (!matchesObservedUnknownTypeArgument(observedTypeArguments[0])) {
                return false;
            }
        } else if (!matchesObservedTypeArgumentAgainstDiscoveredType(observedTypeArguments[0], discoveredFirstType)) {
            return false;
        }
        return discoveredSecondType == null ||
                matchesObservedTypeArgumentAgainstDiscoveredType(observedTypeArguments[1], discoveredSecondType);
    }

    private boolean matchesObservedTypeArgumentAgainstDiscoveredType(Type observedTypeArgument, Type discoveredType) {
        if (discoveredType == null) {
            return matchesObservedUnknownTypeArgument(observedTypeArgument);
        }

        if (observedTypeArgument instanceof Class<?>) {
            Class<?> observedClass = (Class<?>) observedTypeArgument;
            Class<?> discoveredRawType = extractRawClass(discoveredType);
            return discoveredRawType != null && observedClass.isAssignableFrom(discoveredRawType);
        }

        if (observedTypeArgument instanceof ParameterizedType) {
            return matchesObservedParameterizedTypeArgument((ParameterizedType) observedTypeArgument, discoveredType);
        }

        if (observedTypeArgument instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) observedTypeArgument;
            Class<?> discoveredRawType = extractRawClass(discoveredType);
            if (discoveredRawType == null) {
                return false;
            }

            Type[] upperBounds = wildcardType.getUpperBounds();
            for (Type upperBound : upperBounds) {
                if (upperBound instanceof Class<?> &&
                        !((Class<?>) upperBound).isAssignableFrom(discoveredRawType)) {
                    return false;
                }
            }

            Type[] lowerBounds = wildcardType.getLowerBounds();
            for (Type lowerBound : lowerBounds) {
                if (lowerBound instanceof Class<?> &&
                        !discoveredRawType.isAssignableFrom((Class<?>) lowerBound)) {
                    return false;
                }
            }
            return true;
        }

        return true;
    }

    private boolean matchesObservedUnknownTypeArgument(Type observedTypeArgument) {
        if (observedTypeArgument == null) {
            return true;
        }
        if (observedTypeArgument instanceof Class<?>) {
            return Object.class.equals(observedTypeArgument);
        }
        if (observedTypeArgument instanceof ParameterizedType) {
            Type rawObservedType = ((ParameterizedType) observedTypeArgument).getRawType();
            return rawObservedType instanceof Class<?> && Object.class.equals(rawObservedType);
        }
        if (observedTypeArgument instanceof WildcardType) {
            return true;
        }
        return observedTypeArgument instanceof TypeVariable<?>;
    }

    private boolean matchesObservedParameterizedTypeArgument(ParameterizedType observedType, Type discoveredType) {
        Class<?> observedRawType = extractRawClass(observedType);
        Class<?> discoveredRawType = extractRawClass(discoveredType);
        if (observedRawType == null || discoveredRawType == null ||
                !observedRawType.isAssignableFrom(discoveredRawType)) {
            return false;
        }

        Type[] observedTypeArguments = observedType.getActualTypeArguments();
        if (!(discoveredType instanceof ParameterizedType)) {
            return allObservedTypeArgumentsUnrestricted(observedTypeArguments);
        }

        Type[] discoveredTypeArguments = ((ParameterizedType) discoveredType).getActualTypeArguments();
        if (observedTypeArguments.length != discoveredTypeArguments.length) {
            return false;
        }

        for (int i = 0; i < observedTypeArguments.length; i++) {
            if (!matchesObservedParameterizedTypeArgumentValue(observedTypeArguments[i], discoveredTypeArguments[i])) {
                return false;
            }
        }
        return true;
    }

    private boolean allObservedTypeArgumentsUnrestricted(Type[] observedTypeArguments) {
        if (observedTypeArguments == null) {
            return true;
        }
        for (Type observedTypeArgument : observedTypeArguments) {
            if (!(observedTypeArgument instanceof WildcardType) &&
                    !(observedTypeArgument instanceof TypeVariable<?>)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesObservedParameterizedTypeArgumentValue(Type observedTypeArgument, Type discoveredTypeArgument) {
        if (observedTypeArgument == null) {
            return discoveredTypeArgument == null;
        }
        if (observedTypeArgument instanceof TypeVariable<?>) {
            return true;
        }
        if (observedTypeArgument instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) observedTypeArgument;
            Class<?> discoveredRawType = extractRawClass(discoveredTypeArgument);
            if (discoveredRawType == null) {
                return false;
            }
            for (Type upperBound : wildcardType.getUpperBounds()) {
                if (upperBound instanceof Class<?> &&
                        !((Class<?>) upperBound).isAssignableFrom(discoveredRawType)) {
                    return false;
                }
            }
            for (Type lowerBound : wildcardType.getLowerBounds()) {
                if (lowerBound instanceof Class<?> &&
                        !discoveredRawType.isAssignableFrom((Class<?>) lowerBound)) {
                    return false;
                }
            }
            return true;
        }
        return observedTypeArgument.equals(discoveredTypeArgument);
    }

    public void fireBeforeBeanDiscovery() {
        messageHandler.info("Firing BeforeBeanDiscovery event");
        BeforeBeanDiscovery event = new BeforeBeanDiscoveryImpl(messageHandler, knowledgeBase, beanManager);
        fireEventToExtensions(event);
    }

    public void fireAfterTypeDiscovery() {
        messageHandler.info("Firing AfterTypeDiscovery event");

        List<Class<?>> alternatives = collectPriorityEnabledAlternatives();
        List<Class<?>> interceptors = collectPriorityEnabledInterceptors();
        List<Class<?>> decorators = collectPriorityEnabledDecorators();
        List<Class<?>> initialAlternatives = new ArrayList<>(alternatives);
        List<Class<?>> initialInterceptors = new ArrayList<>(interceptors);
        List<Class<?>> initialDecorators = new ArrayList<>(decorators);

        AfterTypeDiscovery event = new AfterTypeDiscoveryImpl(
                messageHandler, knowledgeBase, beanManager, alternatives, interceptors, decorators);
        fireEventToExtensions(event);
        processRegisteredAnnotatedTypes();

        knowledgeBase.setApplicationAlternativeOrder(alternatives);
        knowledgeBase.setApplicationInterceptorOrder(interceptors);
        knowledgeBase.setApplicationDecoratorOrder(decorators);
        knowledgeBase.setAfterTypeDiscoveryAlternativesCustomized(!initialAlternatives.equals(alternatives));
        knowledgeBase.setAfterTypeDiscoveryInterceptorsCustomized(!initialInterceptors.equals(interceptors));
        knowledgeBase.setAfterTypeDiscoveryDecoratorsCustomized(!initialDecorators.equals(decorators));
    }

    private List<Class<?>> collectPriorityEnabledAlternatives() {
        List<Class<?>> enabled = new ArrayList<>();
        for (Class<?> candidate : knowledgeBase.getClasses()) {
            if (!hasAlternativeAnnotation(candidate)) {
                continue;
            }
            Integer priority = knowledgeBase.getEffectivePriority(candidate);
            if (priority == null) {
                continue;
            }
            if (!enabled.contains(candidate)) {
                enabled.add(candidate);
            }
        }
        enabled.sort(Comparator
                .comparingInt((Class<?> clazz) -> {
                    Integer priority = knowledgeBase.getEffectivePriority(clazz);
                    return priority != null ? priority : Integer.MAX_VALUE;
                })
                .thenComparing(Class::getName));
        return enabled;
    }

    private List<Class<?>> collectPriorityEnabledInterceptors() {
        List<Class<?>> enabled = new ArrayList<>();
        for (Class<?> candidate : knowledgeBase.getClasses()) {
            if (!hasInterceptorAnnotation(candidate)) {
                continue;
            }
            if (ActivateRequestContextInterceptor.class.equals(candidate)) {
                continue;
            }
            Integer priority = knowledgeBase.getEffectivePriority(candidate);
            if (priority == null) {
                continue;
            }
            if (!enabled.contains(candidate)) {
                enabled.add(candidate);
            }
        }
        enabled.sort(Comparator
                .comparingInt((Class<?> clazz) -> {
                    Integer priority = knowledgeBase.getEffectivePriority(clazz);
                    return priority != null ? priority : Integer.MAX_VALUE;
                })
                .thenComparing(Class::getName));
        return enabled;
    }

    private List<Class<?>> collectPriorityEnabledDecorators() {
        List<Class<?>> enabled = new ArrayList<>();
        for (Class<?> candidate : knowledgeBase.getClasses()) {
            if (!hasDecoratorAnnotation(candidate)) {
                continue;
            }
            Integer priority = knowledgeBase.getEffectivePriority(candidate);
            if (priority == null) {
                continue;
            }
            if (!enabled.contains(candidate)) {
                enabled.add(candidate);
            }
        }
        enabled.sort(Comparator
                .comparingInt((Class<?> clazz) -> {
                    Integer priority = knowledgeBase.getEffectivePriority(clazz);
                    return priority != null ? priority : Integer.MAX_VALUE;
                })
                .thenComparing(Class::getName));
        return enabled;
    }

    @Override
    public void processRegisteredAnnotatedTypes() {
        Map<String, AnnotatedType<?>> registeredTypes = knowledgeBase.getRegisteredAnnotatedTypes();

        if (registeredTypes.isEmpty()) {
            messageHandler.info("No registered AnnotatedTypes to process");
            return;
        }

        messageHandler.info("Processing " + registeredTypes.size() + " registered AnnotatedTypes");

        for (Map.Entry<String, AnnotatedType<?>> entry : registeredTypes.entrySet()) {
            String id = entry.getKey();
            if (knowledgeBase.processedSyntheticAnnotatedTypeIds.contains(id)) {
                continue;
            }
            AnnotatedType<?> annotatedType = entry.getValue();
            Class<?> clazz = annotatedType.getJavaClass();
            boolean alreadyDiscovered = knowledgeBase.getClasses().contains(clazz);
            Extension sourceExtension = knowledgeBase.getRegisteredAnnotatedTypeSource(id);

            messageHandler.info("Processing registered AnnotatedType: " + clazz.getName() + " (ID: " + id + ")");

            if (shouldSkipProcessAnnotatedTypeEvent(clazz)) {
                if (!alreadyDiscovered) {
                    knowledgeBase.vetoType(clazz);
                }
                knowledgeBase.processedSyntheticAnnotatedTypeIds.add(id);
                continue;
            }

            @SuppressWarnings({"rawtypes", "unchecked"})
            ProcessSyntheticAnnotatedTypeImpl<?> event = new ProcessSyntheticAnnotatedTypeImpl(
                    messageHandler,
                    annotatedType,
                    sourceExtension);
            fireEventToExtensions(event);

            if (event.isVetoed()) {
                if (!alreadyDiscovered) {
                    knowledgeBase.vetoType(clazz);
                }
                knowledgeBase.processedSyntheticAnnotatedTypeIds.add(id);
                continue;
            }

            AnnotatedType<?> finalAnnotatedType = event.getAnnotatedTypeInternal();
            if (finalAnnotatedType == null) {
                finalAnnotatedType = annotatedType;
            }

            // Add the class to KnowledgeBase so it will be processed as a bean candidate.
            // Only mark it as synthetic-only when it was not already discovered on the classpath.
            if (alreadyDiscovered) {
                // CDI 4.1: addAnnotatedType() for an already discovered Java class contributes
                // an additional bean definition. Keep discovered class metadata untouched and
                // register this AnnotatedType as an extra bean during validation.
                knowledgeBase.additionalAnnotatedTypesForDiscoveredClasses.put(id, finalAnnotatedType);
                knowledgeBase.processedSyntheticAnnotatedTypeIds.add(id);
                continue;
            }
            knowledgeBase.addProgrammatic(
                    clazz,
                    resolveProgrammaticAnnotatedTypeArchiveMode(clazz, sourceExtension));
            knowledgeBase.syntheticAnnotatedTypeClasses.add(clazz);
            knowledgeBase.setAnnotatedTypeOverride(clazz, finalAnnotatedType);
            knowledgeBase.processedSyntheticAnnotatedTypeIds.add(id);
        }

        messageHandler.info("Total classes after registered types: " + knowledgeBase.getClasses().size());
    }

    private BeanArchiveMode resolveProgrammaticAnnotatedTypeArchiveMode(Class<?> clazz,
                                                                        Extension sourceExtension) {
        if (knowledgeBase.getForcedBeanArchiveMode() != null) {
            return knowledgeBase.getForcedBeanArchiveMode();
        }

        BeanArchiveMode classMode = modeOfKnownClass(clazz);
        if (classMode != null) {
            return knowledgeBase.getEffectiveBeanArchiveMode(classMode);
        }

        if (sourceExtension != null) {
            BeanArchiveMode sourceMode = modeOfKnownClass(sourceExtension.getClass());
            if (sourceMode != null) {
                return knowledgeBase.getEffectiveBeanArchiveMode(sourceMode);
            }
        }

        BeanArchiveMode beansXmlMode = resolveProgrammaticArchiveModeFromBeansXml();
        if (beansXmlMode != null) {
            return knowledgeBase.getEffectiveBeanArchiveMode(beansXmlMode);
        }

        return knowledgeBase.getEffectiveBeanArchiveMode(BeanArchiveMode.IMPLICIT);
    }

    private BeanArchiveMode modeOfKnownClass(Class<?> candidate) {
        if (candidate == null || !knowledgeBase.getClasses().contains(candidate)) {
            return null;
        }
        return knowledgeBase.getBeanArchiveMode(candidate);
    }

    private BeanArchiveMode resolveProgrammaticArchiveModeFromBeansXml() {
        BeanArchiveMode resolvedMode = null;
        for (BeansXml beansXml : knowledgeBase.getBeansXmlConfigurations()) {
            if (beansXml == null) {
                continue;
            }
            String discoveryMode = beansXml.getBeanDiscoveryMode();
            if (discoveryMode == null) {
                continue;
            }
            String normalizedMode = discoveryMode.trim().toLowerCase(Locale.ROOT);
            if ("all".equals(normalizedMode) || isLegacyAllByDefaultDescriptor(beansXml, normalizedMode)) {
                BeanArchiveMode mode = beansXml.isTrimEnabled() ? BeanArchiveMode.TRIMMED : BeanArchiveMode.EXPLICIT;
                if (BeanArchiveMode.TRIMMED.equals(mode)) {
                    return mode;
                }
                resolvedMode = mode;
            }
        }
        return resolvedMode;
    }

    private boolean isLegacyAllByDefaultDescriptor(BeansXml beansXml, String normalizedMode) {
        if (beansXml == null || !"annotated".equals(normalizedMode)) {
            return false;
        }
        // CDI 1.0 legacy descriptor semantics (java.sun namespace + no discovery mode attribute)
        // default to "all"; modern empty beans.xml defaults to "annotated".
        if (beansXml.isBeanDiscoveryModeDeclared() || beansXml.isNotLegacyJavaSunDescriptor()) {
            return false;
        }
        String version = beansXml.getVersion();
        if (version == null || version.trim().isEmpty()) {
            return true;
        }
        String trimmed = version.trim();
        if ("1".equals(trimmed) || "1.0".equals(trimmed)) {
            return true;
        }
        try {
            String[] parts = trimmed.split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return major < 1 || (major == 1 && minor == 0);
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

}
