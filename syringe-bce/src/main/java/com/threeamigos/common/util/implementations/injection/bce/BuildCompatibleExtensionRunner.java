package com.threeamigos.common.util.implementations.injection.bce;

import com.threeamigos.common.util.implementations.injection.discovery.NonPortableBehaviourException;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.BuildServices;
import jakarta.enterprise.inject.build.compatible.spi.ClassConfig;
import jakarta.enterprise.inject.build.compatible.spi.DisposerInfo;
import jakarta.enterprise.inject.build.compatible.spi.Enhancement;
import jakarta.enterprise.inject.build.compatible.spi.FieldConfig;
import jakarta.enterprise.inject.build.compatible.spi.BeanInfo;
import jakarta.enterprise.inject.build.compatible.spi.InterceptorInfo;
import jakarta.enterprise.inject.build.compatible.spi.InjectionPointInfo;
import jakarta.enterprise.inject.build.compatible.spi.MethodConfig;
import jakarta.enterprise.inject.build.compatible.spi.ParameterConfig;
import jakarta.enterprise.inject.build.compatible.spi.Messages;
import jakarta.enterprise.inject.build.compatible.spi.MetaAnnotations;
import jakarta.enterprise.inject.build.compatible.spi.ObserverInfo;
import jakarta.enterprise.inject.build.compatible.spi.Registration;
import jakarta.enterprise.inject.build.compatible.spi.ScannedClasses;
import jakarta.enterprise.inject.build.compatible.spi.ScopeInfo;
import jakarta.enterprise.inject.build.compatible.spi.StereotypeInfo;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.inject.build.compatible.spi.InvokerFactory;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticComponents;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.declarations.FieldInfo;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.configurator.AnnotatedFieldConfigurator;
import jakarta.enterprise.inject.spi.configurator.AnnotatedMethodConfigurator;
import jakarta.enterprise.inject.spi.configurator.AnnotatedParameterConfigurator;
import jakarta.enterprise.lang.model.declarations.ParameterInfo;
import jakarta.interceptor.Interceptor;
import com.threeamigos.common.util.implementations.injection.resolution.ProducerBean;
import com.threeamigos.common.util.implementations.injection.spi.configurators.AnnotatedTypeConfiguratorImpl;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.hasRequiredEnhancementAnnotation;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.*;
import static com.threeamigos.common.util.implementations.injection.spi.SPIUtils.*;

/**
 * Minimal phase executor for Build Compatible Extensions.
 *
 * <p>Step 2 scope: detect methods annotated with supported BCE phase annotations
 * and jakarta.enterprise.invoke no-arg methods in deterministic order.
 */
public class BuildCompatibleExtensionRunner {

    private final MessageHandler messageHandler;
    private final KnowledgeBase knowledgeBase;
    private final BeanManager beanManager;
    private final BceInvokerRegistry invokerRegistry;
    private final BceInvokerFactoryImpl invokerFactory;
    private final BceRegistrationContext registrationContext;
    private final BuildServices buildServices;
    private final Types types;
    private final Messages messages;
    private final MetaAnnotations metaAnnotations;
    private final ScannedClasses scannedClasses;
    private final Map<Method, Set<String>> deliveredRegistrationModels = new HashMap<>();

    public BuildCompatibleExtensionRunner(MessageHandler messageHandler,
                                          KnowledgeBase knowledgeBase,
                                          BeanManager beanManager,
                                          BceInvokerRegistry invokerRegistry) {
        this.messageHandler = messageHandler;
        this.knowledgeBase = knowledgeBase;
        this.beanManager = beanManager;
        this.invokerRegistry = invokerRegistry;

        this.invokerFactory = new BceInvokerFactoryImpl(knowledgeBase, beanManager, messageHandler, invokerRegistry);
        this.registrationContext = new BceRegistrationContext(knowledgeBase);
        this.buildServices = new BceBuildServices();
        this.types = new BceTypes();
        this.messages = new BceMessages(messageHandler, knowledgeBase);
        this.metaAnnotations = new BceMetaAnnotations(knowledgeBase, messageHandler);
        this.scannedClasses = new BceScannedClasses(knowledgeBase, messageHandler);
    }

    public void runPhase(BceSupportedPhase phase,
                         List<BuildCompatibleExtension> extensions) {
        if (phase == null || extensions == null || extensions.isEmpty()) {
            return;
        }

        List<PhaseMethodInvocation> invocations = new ArrayList<>();
        for (BuildCompatibleExtension extension : extensions) {
            collectPhaseMethods(extension, phase, invocations);
        }

        invocations.sort(Comparator
            .comparingInt((PhaseMethodInvocation i) -> priorityOf(i.method))
            .thenComparing(i -> i.extension.getClass().getName())
            .thenComparing(i -> i.method.getName()));

        BceSyntheticComponents syntheticComponents = null;
        EnhancementModelState enhancementModelState = null;
        if (phase == BceSupportedPhase.SYNTHESIS) {
            syntheticComponents = new BceSyntheticComponents(knowledgeBase, beanManager, invokerRegistry);
        } else if (phase == BceSupportedPhase.ENHANCEMENT) {
            enhancementModelState = new EnhancementModelState();
        }

        try (BceBuildServicesScope ignored = new BceBuildServicesScope(buildServices)) {
            for (PhaseMethodInvocation invocation : invocations) {
                invokePhaseMethod(invocation, phase, syntheticComponents, enhancementModelState);
            }
        }

        if (enhancementModelState != null) {
            applyEnhancementModelState(enhancementModelState);
        }

        if (syntheticComponents != null) {
            syntheticComponents.complete();
        }
    }

    private void collectPhaseMethods(BuildCompatibleExtension extension,
                                     BceSupportedPhase phase,
                                     List<PhaseMethodInvocation> sink) {
        for (Method method : extension.getClass().getDeclaredMethods()) {
            if (matchesPhaseAnnotation(method, phase)) {
                sink.add(new PhaseMethodInvocation(extension, method));
            }
        }
    }

    private boolean matchesPhaseAnnotation(Method method,
                                           BceSupportedPhase phase) {
        switch (phase) {
            case DISCOVERY:
                return hasDiscoveryAnnotation(method);
            case ENHANCEMENT:
                return hasEnhancementAnnotation(method);
            case REGISTRATION:
                return hasRegistrationAnnotation(method);
            case SYNTHESIS:
                return hasSynthesisAnnotation(method);
            case VALIDATION:
                return hasValidationAnnotation(method);
            default:
                return false;
        }
    }

    private void invokePhaseMethod(PhaseMethodInvocation invocation,
                                   BceSupportedPhase phase,
                                   BceSyntheticComponents syntheticComponents,
                                   EnhancementModelState enhancementModelState) {
        Method method = invocation.method;
        validatePhaseMethodSignature(method, phase);

        if (phase == BceSupportedPhase.ENHANCEMENT &&
            hasEnhancementModelParameter(method)) {
            invokeEnhancementModelMethods(invocation, enhancementModelState);
            return;
        }
        if ((phase == BceSupportedPhase.REGISTRATION ||
            phase == BceSupportedPhase.VALIDATION) &&
            hasRegistrationOrValidationModelParameter(method)) {
            invokeRegistrationOrValidationModelMethods(invocation, phase);
            return;
        }

        try {
            method.setAccessible(true);
            Object[] args = resolvePhaseMethodArguments(method, phase, syntheticComponents);
            method.invoke(invocation.extension, args);
            messageHandler.info("[Syringe] Invoked BCE phase method: " +
                invocation.extension.getClass().getSimpleName() + "." + method.getName() +
                " (" + phase + ")");
        } catch (IllegalAccessException e) {
            throw new DefinitionException("Cannot access BCE phase method " +
                invocation.extension.getClass().getName() + "." + method.getName(), e);
        } catch (InvocationTargetException e) {
            Throwable target = e.getTargetException();
            if (target instanceof DefinitionException) {
                throw (DefinitionException) target;
            }
            if (isCdiCurrentAccessFailure(target)) {
                throw new NonPortableBehaviourException(
                    "Calling CDI.current() from a build compatible extension method is non-portable", target);
            }
            throw new DefinitionException("Error invoking BCE phase method " +
                invocation.extension.getClass().getName() + "." + method.getName(), target);
        }
    }

    private int priorityOf(Method method) {
        Integer priority = getPriorityValue(method);
        return priority != null ? priority : Interceptor.Priority.APPLICATION + 500;
    }

    private void validatePhaseMethodSignature(Method method,
                                              BceSupportedPhase phase) {
        if (!Modifier.isPublic(method.getModifiers())) {
            throw new DefinitionException("Invalid BCE " + phase + " method " +
                method.getDeclaringClass().getName() + "." + method.getName() +
                ": method must be public.");
        }

        if (Modifier.isStatic(method.getModifiers())) {
            throw new DefinitionException("Invalid BCE " + phase + " method " +
                method.getDeclaringClass().getName() + "." + method.getName() +
                ": method must not be static.");
        }

        if (method.getTypeParameters().length > 0) {
            throw new DefinitionException("Invalid BCE " + phase + " method " +
                method.getDeclaringClass().getName() + "." + method.getName() +
                ": type parameters are not allowed.");
        }

        int phaseAnnotationCount = getPhaseAnnotationCount(method);
        if (phaseAnnotationCount != 1) {
            throw new DefinitionException("Invalid BCE method " + method.getDeclaringClass().getName() +
                "." + method.getName() + ": method must declare exactly one BCE phase annotation.");
        }

        if (!void.class.equals(method.getReturnType())) {
            throw new DefinitionException("Invalid BCE " + phase + " method " +
                method.getDeclaringClass().getName() + "." + method.getName() +
                ": return type must be void.");
        }

        Class<?>[] parameterTypes = method.getParameterTypes();
        switch (phase) {
            case DISCOVERY:
                if (!areSupportedParameterTypes(parameterTypes
                )) {
                    throw new DefinitionException("Invalid BCE " + phase + " method " +
                        method.getDeclaringClass().getName() + "." + method.getName() +
                        ": supported parameters are from {Types, Messages, MetaAnnotations, ScannedClasses}.");
                }
                break;
            case ENHANCEMENT:
                validateEnhancementSignature(method);
                break;
            case VALIDATION:
            case REGISTRATION:
                validateRegistrationOrValidationSignature(method, phase);
                break;
            case SYNTHESIS:
                validateSynthesisSignature(method, parameterTypes);
                break;
            default:
                break;
        }
    }

    private static void validateSynthesisSignature(Method method, Class<?>[] parameterTypes) {
        if (parameterTypes.length > 3) {
            throw new DefinitionException("Invalid BCE SYNTHESIS method " +
                method.getDeclaringClass().getName() + "." + method.getName() +
                ": too many parameters.");
        }
        boolean seenSyntheticComponents = false;
        boolean seenTypes = false;
        boolean seenMessages = false;
        for (Class<?> parameterType : parameterTypes) {
            if (SyntheticComponents.class.isAssignableFrom(parameterType)) {
                if (seenSyntheticComponents) {
                    throw new DefinitionException("Invalid BCE SYNTHESIS method " +
                        method.getDeclaringClass().getName() + "." + method.getName() +
                        ": duplicate SyntheticComponents parameter.");
                }
                seenSyntheticComponents = true;
                continue;
            }
            if (Types.class.isAssignableFrom(parameterType)) {
                if (seenTypes) {
                    throw new DefinitionException("Invalid BCE SYNTHESIS method " +
                        method.getDeclaringClass().getName() + "." + method.getName() +
                        ": duplicate Types parameter.");
                }
                seenTypes = true;
                continue;
            }
            if (Messages.class.isAssignableFrom(parameterType)) {
                if (seenMessages) {
                    throw new DefinitionException("Invalid BCE SYNTHESIS method " +
                        method.getDeclaringClass().getName() + "." + method.getName() +
                        ": duplicate Messages parameter.");
                }
                seenMessages = true;
                continue;
            }
            throw new DefinitionException("Invalid BCE SYNTHESIS method " +
                method.getDeclaringClass().getName() + "." + method.getName() +
                ": unsupported parameter type " + parameterType.getName());
        }
    }

    private static int getPhaseAnnotationCount(Method method) {
        int phaseAnnotationCount = 0;
        if (hasDiscoveryAnnotation(method)) {
            phaseAnnotationCount++;
        }
        if (hasEnhancementAnnotation(method)) {
            phaseAnnotationCount++;
        }
        if (hasRegistrationAnnotation(method)) {
            phaseAnnotationCount++;
        }
        if (hasSynthesisAnnotation(method)) {
            phaseAnnotationCount++;
        }
        if (hasValidationAnnotation(method)) {
            phaseAnnotationCount++;
        }
        return phaseAnnotationCount;
    }

    private void validateEnhancementSignature(Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        int modelParamCount = 0;
        boolean seenTypes = false;
        boolean seenMessages = false;
        for (Class<?> parameterType : parameterTypes) {
            if (Types.class.isAssignableFrom(parameterType)) {
                if (seenTypes) {
                    throw new DefinitionException("Invalid BCE ENHANCEMENT method " +
                        method.getDeclaringClass().getName() + "." + method.getName() +
                        ": duplicate Types parameter.");
                }
                seenTypes = true;
                continue;
            }
            if (Messages.class.isAssignableFrom(parameterType)) {
                if (seenMessages) {
                    throw new DefinitionException("Invalid BCE ENHANCEMENT method " +
                        method.getDeclaringClass().getName() + "." + method.getName() +
                        ": duplicate Messages parameter.");
                }
                seenMessages = true;
                continue;
            }
            if (isEnhancementModelType(parameterType)) {
                modelParamCount++;
                continue;
            }
            throw new DefinitionException("Invalid BCE ENHANCEMENT method " +
                method.getDeclaringClass().getName() + "." + method.getName() +
                ": unsupported parameter type " + parameterType.getName());
        }
        if (modelParamCount > 1) {
            throw new DefinitionException("Invalid BCE ENHANCEMENT method " +
                method.getDeclaringClass().getName() + "." + method.getName() +
                ": at most one model/config parameter is allowed from {ClassInfo, ClassConfig, MethodInfo, MethodConfig, FieldInfo, FieldConfig}.");
        }
        if (modelParamCount != 1) {
            throw new DefinitionException("Invalid BCE ENHANCEMENT method " +
                method.getDeclaringClass().getName() + "." + method.getName() +
                ": exactly one model/config parameter is required from {ClassInfo, ClassConfig, MethodInfo, MethodConfig, FieldInfo, FieldConfig}.");
        }
    }

    private Object[] resolvePhaseMethodArguments(Method method,
                                                 BceSupportedPhase phase,
                                                 BceSyntheticComponents syntheticComponents) {
        if (method.getParameterCount() == 0) {
            return new Object[0];
        }

        if (phase == BceSupportedPhase.SYNTHESIS) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            Object[] args = new Object[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                Class<?> parameterType = parameterTypes[i];
                if (SyntheticComponents.class.isAssignableFrom(parameterType)) {
                    args[i] = syntheticComponents;
                } else {
                    Object commonService = resolveCommonServiceArgument(parameterType);
                    if (commonService != null) {
                        args[i] = commonService;
                    } else {
                        throw new DefinitionException("Unsupported synthesis parameter type " +
                            parameterType.getName() + " for method " +
                            method.getDeclaringClass().getName() + "." + method.getName());
                    }
                }
            }
            return args;
        }
        if (phase == BceSupportedPhase.REGISTRATION) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            Object[] args = new Object[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                Class<?> parameterType = parameterTypes[i];
                if (InvokerFactory.class.isAssignableFrom(parameterType)) {
                    args[i] = invokerFactory;
                } else if (BceRegistrationContext.class.isAssignableFrom(parameterType)) {
                    args[i] = registrationContext;
                } else {
                    Object commonService = resolveCommonServiceArgument(parameterType);
                    if (commonService != null) {
                        args[i] = commonService;
                    } else {
                        throw new DefinitionException("Unsupported registration parameter type " +
                            parameterType.getName() + " for method " +
                            method.getDeclaringClass().getName() + "." + method.getName());
                    }
                }
            }
            return args;
        }
        if (phase == BceSupportedPhase.DISCOVERY ||
            phase == BceSupportedPhase.ENHANCEMENT ||
            phase == BceSupportedPhase.VALIDATION) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            Object[] args = new Object[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                Object commonService = resolveCommonServiceArgument(parameterTypes[i]);
                if (commonService == null) {
                    throw new DefinitionException("Unsupported " + phase + " parameter type " +
                        parameterTypes[i].getName() + " for method " +
                        method.getDeclaringClass().getName() + "." + method.getName());
                }
                args[i] = commonService;
            }
            return args;
        }

        throw new DefinitionException("Unsupported BCE " + phase + " method signature: " +
            method.getDeclaringClass().getName() + "." + method.getName() +
            " - currently supported signatures are no-arg for Discovery/Enhancement/Validation, " +
            "@Registration methods with parameters from {BeanInfo, ObserverInfo, InvokerFactory, BceRegistrationContext, Types, Messages}, " +
            "@Validation methods with parameters from {BeanInfo, ObserverInfo, Types, Messages}, and " +
            "@Synthesis methods with optional parameters from {SyntheticComponents, Types, Messages}.");
    }

    private void validateRegistrationOrValidationSignature(Method method,
                                                           BceSupportedPhase phase) {
        Class<?>[] parameterTypes = getClasses(method, phase);
        boolean seenInvokerFactory = false;
        boolean seenContext = false;
        boolean seenTypes = false;
        boolean seenMessages = false;
        int modelCount = 0;
        for (Class<?> parameterType : parameterTypes) {
            if (InvokerFactory.class.isAssignableFrom(parameterType)) {
                if (phase != BceSupportedPhase.REGISTRATION || seenInvokerFactory) {
                    throw invalidRegistrationOrValidationParameter(method, phase, parameterType, "duplicate/illegal InvokerFactory");
                }
                seenInvokerFactory = true;
                continue;
            }
            if (BceRegistrationContext.class.isAssignableFrom(parameterType)) {
                if (phase != BceSupportedPhase.REGISTRATION || seenContext) {
                    throw invalidRegistrationOrValidationParameter(method, phase, parameterType, "duplicate/illegal BceRegistrationContext");
                }
                seenContext = true;
                continue;
            }
            if (Types.class.isAssignableFrom(parameterType)) {
                if (seenTypes) {
                    throw invalidRegistrationOrValidationParameter(method, phase, parameterType, "duplicate Types");
                }
                seenTypes = true;
                continue;
            }
            if (Messages.class.isAssignableFrom(parameterType)) {
                if (seenMessages) {
                    throw invalidRegistrationOrValidationParameter(method, phase, parameterType, "duplicate Messages");
                }
                seenMessages = true;
                continue;
            }
            if (isRegistrationOrValidationModelType(parameterType)) {
                modelCount++;
                continue;
            }
            throw invalidRegistrationOrValidationParameter(method, phase, parameterType, "unsupported parameter");
        }
        if (modelCount > 1) {
            throw new DefinitionException("Invalid BCE " + phase + " method " +
                method.getDeclaringClass().getName() + "." + method.getName() +
                ": at most one model parameter is allowed from {BeanInfo, ObserverInfo, InterceptorInfo, InjectionPointInfo, DisposerInfo, ScopeInfo, StereotypeInfo}.");
        }
        if (phase == BceSupportedPhase.REGISTRATION && modelCount == 0 &&
            !isRegistrationWithoutModelAllowed(method, seenInvokerFactory, seenContext)) {
            throw new DefinitionException("Invalid BCE " + phase + " method " +
                method.getDeclaringClass().getName() + "." + method.getName() +
                ": exactly one model parameter is required from {BeanInfo, ObserverInfo, InterceptorInfo, InjectionPointInfo, DisposerInfo, ScopeInfo, StereotypeInfo}.");
        }
    }

    private boolean isRegistrationWithoutModelAllowed(Method method,
                                                      boolean seenInvokerFactory,
                                                      boolean seenContext) {
        // Service-driven registration callbacks are allowed without a direct model parameter.
        if (seenInvokerFactory || seenContext) {
            return true;
        }

        Registration registration = getRegistrationAnnotation(method);
        Class<?>[] acceptedTypes = registration != null ? registration.types() : new Class<?>[0];

        // Parameterless registration callback is only meaningful as a broad registration checkpoint.

        // Types/Messages-only signatures are accepted only for broad Object-filtered registration callbacks.
        return containsObjectRegistrationType(acceptedTypes);
    }

    private boolean containsObjectRegistrationType(Class<?>[] acceptedTypes) {
        if (acceptedTypes == null) {
            return false;
        }
        for (Class<?> acceptedType : acceptedTypes) {
            if (Object.class.equals(acceptedType)) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    private static Class<?>[] getClasses(Method method, BceSupportedPhase phase) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (phase == BceSupportedPhase.REGISTRATION && parameterTypes.length > 7) {
            throw new DefinitionException("Invalid BCE REGISTRATION method " +
                method.getDeclaringClass().getName() + "." + method.getName() +
                ": too many parameters.");
        }
        if (phase == BceSupportedPhase.VALIDATION && parameterTypes.length > 5) {
            throw new DefinitionException("Invalid BCE VALIDATION method " +
                method.getDeclaringClass().getName() + "." + method.getName() +
                ": too many parameters.");
        }
        return parameterTypes;
    }

    private DefinitionException invalidRegistrationOrValidationParameter(Method method,
                                                                         BceSupportedPhase phase,
                                                                         Class<?> parameterType,
                                                                         String reason) {
        return new DefinitionException("Invalid BCE " + phase + " method " +
            method.getDeclaringClass().getName() + "." + method.getName() +
            ": " + reason + " (" + parameterType.getName() + ").");
    }

    private boolean hasRegistrationOrValidationModelParameter(Method method) {
        for (Class<?> parameterType : method.getParameterTypes()) {
            if (isRegistrationOrValidationModelType(parameterType)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRegistrationOrValidationModelType(Class<?> parameterType) {
        return isBeanInfo(parameterType) ||
            isObserverInfo(parameterType) ||
            isInterceptorInfo(parameterType) ||
            isInjectionPointInfo(parameterType) ||
            isDisposerInfo(parameterType) ||
            isScopeInfo(parameterType) ||
            isStereotypeInfo(parameterType);
    }

    private void invokeRegistrationOrValidationModelMethods(PhaseMethodInvocation invocation,
                                                            BceSupportedPhase phase) {
        Method phaseMethod = invocation.method;
        Class<?> modelType = null;
        for (Class<?> parameterType : phaseMethod.getParameterTypes()) {
            if (isRegistrationOrValidationModelType(parameterType)) {
                modelType = parameterType;
                break;
            }
        }
        if (modelType == null) {
            return;
        }

        if (isObserverInfo(modelType)) {
            List<ObserverInfo> observers = collectObserverInfosForPhase(phaseMethod, phase);
            for (ObserverInfo observerInfo : observers) {
                if (shouldNotDeliverRegistrationModel(phaseMethod, phase, registrationModelKey(observerInfo))) {
                    continue;
                }
                invokeRegistrationOrValidationForModel(invocation, phase, null, observerInfo, null, null, null, null, null);
            }
            return;
        }
        if (isInterceptorInfo(modelType)) {
            List<InterceptorInfo> interceptors = collectInterceptorInfosForPhase(phaseMethod, phase);
            for (InterceptorInfo interceptorInfo : interceptors) {
                if (shouldNotDeliverRegistrationModel(phaseMethod, phase, registrationModelKey(interceptorInfo))) {
                    continue;
                }
                invokeRegistrationOrValidationForModel(invocation, phase, null, null, interceptorInfo, null, null, null, null);
            }
            return;
        }
        if (isBeanInfo(modelType)) {
            List<BeanInfo> beans = collectBeanInfosForPhase(phaseMethod, phase);
            for (BeanInfo beanInfo : beans) {
                if (shouldNotDeliverRegistrationModel(phaseMethod, phase, registrationModelKey(beanInfo))) {
                    continue;
                }
                invokeRegistrationOrValidationForModel(invocation, phase, beanInfo, null, null, null, null, null, null);
            }
            return;
        }
        if (isInjectionPointInfo(modelType)) {
            List<InjectionPointInfo> injectionPoints = collectInjectionPointInfosForPhase(phaseMethod, phase);
            for (InjectionPointInfo injectionPointInfo : injectionPoints) {
                if (shouldNotDeliverRegistrationModel(phaseMethod, phase, registrationModelKey(injectionPointInfo))) {
                    continue;
                }
                invokeRegistrationOrValidationForModel(
                    invocation, phase, null, null, null, injectionPointInfo, null, null, null);
            }
            return;
        }
        if (isDisposerInfo(modelType)) {
            List<DisposerInfo> disposers = collectDisposerInfosForPhase(phaseMethod, phase);
            for (DisposerInfo disposerInfo : disposers) {
                if (shouldNotDeliverRegistrationModel(phaseMethod, phase, registrationModelKey(disposerInfo))) {
                    continue;
                }
                invokeRegistrationOrValidationForModel(
                    invocation, phase, null, null, null, null, disposerInfo, null, null);
            }
            return;
        }
        if (isScopeInfo(modelType)) {
            List<ScopeInfo> scopes = collectScopeInfosForPhase(phaseMethod, phase);
            for (ScopeInfo scopeInfo : scopes) {
                if (shouldNotDeliverRegistrationModel(phaseMethod, phase, registrationModelKey(scopeInfo))) {
                    continue;
                }
                invokeRegistrationOrValidationForModel(
                    invocation, phase, null, null, null, null, null, scopeInfo, null);
            }
            return;
        }
        if (isStereotypeInfo(modelType)) {
            List<StereotypeInfo> stereotypes = collectStereotypeInfosForPhase(phaseMethod, phase);
            for (StereotypeInfo stereotypeInfo : stereotypes) {
                if (shouldNotDeliverRegistrationModel(phaseMethod, phase, registrationModelKey(stereotypeInfo))) {
                    continue;
                }
                invokeRegistrationOrValidationForModel(
                    invocation, phase, null, null, null, null, null, null, stereotypeInfo);
            }
        }
    }

    private List<BeanInfo> collectBeanInfosForPhase(Method phaseMethod,
                                                    BceSupportedPhase phase) {
        List<BeanInfo> beans = new ArrayList<>();
        Set<String> seenModelKeys = new LinkedHashSet<>();
        if (phase == BceSupportedPhase.REGISTRATION) {
            Registration registration = getRegistrationAnnotation(phaseMethod);
            Class<?>[] acceptedTypes = registration != null ? registration.types() : new Class<?>[0];
            for (Bean<?> bean : knowledgeBase.getBeans()) {
                if (bean == null || bean.getBeanClass() == null) {
                    continue;
                }
                if (acceptedTypes.length > 0 && !isBeanAcceptedByRegistrationTypes(bean, acceptedTypes)) {
                    continue;
                }
                BeanInfo beanInfo = BceMetadata.beanInfo(bean);
                if (acceptedTypes.length > 0 && !isBeanInfoAcceptedByRegistrationTypes(beanInfo, acceptedTypes)) {
                    continue;
                }
                if (seenModelKeys.add(registrationModelKey(beanInfo))) {
                    beans.add(beanInfo);
                }
            }
            for (InterceptorInfo interceptorInfo : collectInterceptorInfosForPhase(phaseMethod, phase)) {
                BeanInfo beanInfo = interceptorInfo;
                if (seenModelKeys.add(registrationModelKey(beanInfo))) {
                    beans.add(beanInfo);
                }
            }
        } else {
            for (Class<?> clazz : knowledgeBase.getClasses()) {
                beans.add(BceMetadata.beanInfo(clazz));
            }
        }
        beans.sort(Comparator.comparing(bean -> bean.declaringClass().name()));
        return beans;
    }

    private List<ObserverInfo> collectObserverInfosForPhase(Method phaseMethod,
                                                            BceSupportedPhase phase) {
        List<ObserverInfo> out = new ArrayList<>(BceObserverInfo.from(knowledgeBase.getObserverMethodInfos()));
        out.addAll(BceObserverInfo.fromSynthetic(knowledgeBase.getSyntheticObserverMethods()));
        if (phase == BceSupportedPhase.REGISTRATION) {
            Registration registration = getRegistrationAnnotation(phaseMethod);
            Class<?>[] acceptedTypes = registration != null ? registration.types() : new Class<?>[0];
            if (acceptedTypes.length > 0) {
                List<ObserverInfo> filtered = new ArrayList<>();
                for (ObserverInfo observerInfo : out) {
                    if (isObserverAcceptedByRegistrationTypes(observerInfo, acceptedTypes)) {
                        filtered.add(observerInfo);
                    }
                }
                out = filtered;
            }
        }
        out.sort(Comparator.comparing(observer -> observer.declaringClass().name()));
        return out;
    }

    private List<InterceptorInfo> collectInterceptorInfosForPhase(Method phaseMethod,
                                                                  BceSupportedPhase phase) {
        List<InterceptorInfo> out = new ArrayList<>(BceInterceptorInfo.from(knowledgeBase.getInterceptorInfos()));
        if (phase == BceSupportedPhase.REGISTRATION) {
            Registration registration = getRegistrationAnnotation(phaseMethod);
            Class<?>[] acceptedTypes = registration != null ? registration.types() : new Class<?>[0];
            if (acceptedTypes.length > 0) {
                List<InterceptorInfo> filtered = new ArrayList<>();
                for (InterceptorInfo interceptorInfo : out) {
                    Class<?> declaringClass = BceMetadata.unwrapClassInfo(interceptorInfo.declaringClass());
                    if (isClassAcceptedByRegistrationTypes(declaringClass, acceptedTypes)) {
                        filtered.add(interceptorInfo);
                    }
                }
                out = filtered;
            }
        }
        out.sort(Comparator.comparing(interceptor -> interceptor.declaringClass().name()));
        return out;
    }

    private List<InjectionPointInfo> collectInjectionPointInfosForPhase(Method phaseMethod,
                                                                        BceSupportedPhase phase) {
        List<InjectionPointInfo> out = new ArrayList<>();
        List<BeanInfo> beans = collectBeanInfosForPhase(phaseMethod, phase);
        for (BeanInfo beanInfo : beans) {
            Collection<InjectionPointInfo> points = beanInfo.injectionPoints();
            if (points != null) {
                out.addAll(points);
            }
        }
        out.sort(Comparator.comparing(this::injectionPointSortKey));
        return out;
    }

    private List<DisposerInfo> collectDisposerInfosForPhase(Method phaseMethod,
                                                            BceSupportedPhase phase) {
        List<DisposerInfo> out = new ArrayList<>();
        Collection<ProducerBean<?>> producerBeans = knowledgeBase.getProducerBeans();
        if (producerBeans == null || producerBeans.isEmpty()) {
            return out;
        }
        Class<?>[] acceptedTypes = new Class<?>[0];
        if (phase == BceSupportedPhase.REGISTRATION) {
            Registration registration = getRegistrationAnnotation(phaseMethod);
            acceptedTypes = registration != null ? registration.types() : new Class<?>[0];
        }
        for (ProducerBean<?> producerBean : producerBeans) {
            Class<?> declaringClass = producerBean.getDeclaringClass();
            if (declaringClass == null) {
                continue;
            }
            if (acceptedTypes.length > 0 && !isClassAcceptedByRegistrationTypes(declaringClass, acceptedTypes)) {
                continue;
            }
            DisposerInfo disposerInfo = BceDisposerInfo.from(producerBean);
            if (disposerInfo != null) {
                out.add(disposerInfo);
            }
        }
        out.sort(Comparator.comparing(this::disposerSortKey));
        return out;
    }

    private List<ScopeInfo> collectScopeInfosForPhase(Method phaseMethod,
                                                      BceSupportedPhase phase) {
        List<BeanInfo> beans = collectBeanInfosForPhase(phaseMethod, phase);
        List<ScopeInfo> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (BeanInfo beanInfo : beans) {
            ScopeInfo scopeInfo = beanInfo.scope();
            if (scopeInfo == null || scopeInfo.annotation() == null) {
                continue;
            }
            String key = scopeInfo.annotation().name();
            if (key == null || seen.contains(key)) {
                continue;
            }
            seen.add(key);
            out.add(scopeInfo);
        }
        out.sort(Comparator.comparing(scopeInfo -> scopeInfo.annotation().name()));
        return out;
    }

    private List<StereotypeInfo> collectStereotypeInfosForPhase(Method phaseMethod,
                                                                BceSupportedPhase phase) {
        List<BeanInfo> beans = collectBeanInfosForPhase(phaseMethod, phase);
        List<StereotypeInfo> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (BeanInfo beanInfo : beans) {
            Collection<StereotypeInfo> stereotypes = beanInfo.stereotypes();
            if (stereotypes == null) {
                continue;
            }
            for (StereotypeInfo stereotypeInfo : stereotypes) {
                if (stereotypeInfo == null) {
                    continue;
                }
                String key = stereotypeSortKey(stereotypeInfo);
                if (seen.contains(key)) {
                    continue;
                }
                seen.add(key);
                out.add(stereotypeInfo);
            }
        }
        out.sort(Comparator.comparing(this::stereotypeSortKey));
        return out;
    }

    private String stereotypeSortKey(StereotypeInfo stereotypeInfo) {
        if (stereotypeInfo == null) {
            return "";
        }
        ScopeInfo defaultScope = stereotypeInfo.defaultScope();
        String scopeKey = defaultScope != null && defaultScope.annotation() != null
            ? defaultScope.annotation().name() : "";
        return scopeKey + "|" + stereotypeInfo.isAlternative() + "|" + stereotypeInfo.isNamed();
    }

    private String disposerSortKey(DisposerInfo disposerInfo) {
        if (disposerInfo == null || disposerInfo.disposerMethod() == null) {
            return "";
        }
        return disposerInfo.disposerMethod().declaringClass().name() + "#" + disposerInfo.disposerMethod().name();
    }

    private String injectionPointSortKey(InjectionPointInfo injectionPointInfo) {
        if (injectionPointInfo == null || injectionPointInfo.declaration() == null) {
            return "";
        }
        if (injectionPointInfo.declaration() instanceof FieldInfo) {
            FieldInfo field = (FieldInfo) injectionPointInfo.declaration();
            return "F:" + field.declaringClass().name() + "#" + field.name();
        }
        if (injectionPointInfo.declaration() instanceof ParameterInfo) {
            ParameterInfo parameter = (ParameterInfo) injectionPointInfo.declaration();
            return "P:" + parameter.declaringMethod().declaringClass().name() + "#" +
                parameter.declaringMethod().name() + ":" + parameter.name();
        }
        return "Z:" + injectionPointInfo.declaration().kind().name();
    }

    private boolean shouldNotDeliverRegistrationModel(Method phaseMethod,
                                                      BceSupportedPhase phase,
                                                      String modelKey) {
        if (phase != BceSupportedPhase.REGISTRATION) {
            return false;
        }
        synchronized (deliveredRegistrationModels) {
            Set<String> delivered = deliveredRegistrationModels.computeIfAbsent(phaseMethod, k -> new LinkedHashSet<>());
            return !delivered.add(modelKey);
        }
    }

    private String registrationModelKey(BeanInfo beanInfo) {
        if (beanInfo == null) {
            return "bean:null";
        }
        if (beanInfo.isProducerMethod() && beanInfo.producerMethod() != null) {
            return "bean:producer-method:" +
                beanInfo.producerMethod().declaringClass().name() + "#" + beanInfo.producerMethod().name() +
                "|q=" + qualifierKey(beanInfo.qualifiers());
        }
        if (beanInfo.isProducerField() && beanInfo.producerField() != null) {
            return "bean:producer-field:" +
                beanInfo.producerField().declaringClass().name() + "#" + beanInfo.producerField().name() +
                "|q=" + qualifierKey(beanInfo.qualifiers());
        }
        String declaring = beanInfo.declaringClass() != null ? beanInfo.declaringClass().name() : "unknown";
        return "bean:class:" + declaring + "|q=" + qualifierKey(beanInfo.qualifiers());
    }

    private String registrationModelKey(ObserverInfo observerInfo) {
        if (observerInfo == null) {
            return "observer:null";
        }
        String declaring = observerInfo.declaringClass() != null ? observerInfo.declaringClass().name() : "unknown";
        String method = observerInfo.observerMethod() != null ? observerInfo.observerMethod().name() : "unknown";
        String eventType = observerInfo.eventType() != null ? observerInfo.eventType().toString() : "unknown";
        return "observer:" + declaring + "#" + method + ":" + eventType;
    }

    private String registrationModelKey(InterceptorInfo interceptorInfo) {
        if (interceptorInfo == null || interceptorInfo.declaringClass() == null) {
            return "interceptor:null";
        }
        return "interceptor:" + interceptorInfo.declaringClass().name();
    }

    private String registrationModelKey(InjectionPointInfo injectionPointInfo) {
        return "ip:" + injectionPointSortKey(injectionPointInfo);
    }

    private String registrationModelKey(DisposerInfo disposerInfo) {
        return "disposer:" + disposerSortKey(disposerInfo);
    }

    private String registrationModelKey(ScopeInfo scopeInfo) {
        if (scopeInfo == null || scopeInfo.annotation() == null) {
            return "scope:null";
        }
        return "scope:" + scopeInfo.annotation().name();
    }

    private String registrationModelKey(StereotypeInfo stereotypeInfo) {
        return "stereotype:" + stereotypeSortKey(stereotypeInfo);
    }

    private String qualifierKey(Collection<AnnotationInfo> qualifiers) {
        if (qualifiers == null || qualifiers.isEmpty()) {
            return "";
        }
        List<String> out = new ArrayList<>();
        for (AnnotationInfo qualifier : qualifiers) {
            if (qualifier == null) {
                continue;
            }
            String name = qualifier.name() != null ? qualifier.name() : "";
            out.add(name + "|" + qualifier.members().toString());
        }
        Collections.sort(out);
        return String.join(",", out);
    }

    private boolean isClassAcceptedByRegistrationTypes(Class<?> candidate, Class<?>[] acceptedTypes) {
        for (Class<?> acceptedType : acceptedTypes) {
            if (acceptedType.isAssignableFrom(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBeanAcceptedByRegistrationTypes(Bean<?> bean, Class<?>[] acceptedTypes) {
        if (bean == null) {
            return false;
        }
        for (java.lang.reflect.Type beanType : bean.getTypes()) {
            if (!(beanType instanceof Class<?>)) {
                continue;
            }
            Class<?> beanTypeClass = (Class<?>) beanType;
            for (Class<?> acceptedType : acceptedTypes) {
                if (acceptedType.isAssignableFrom(beanTypeClass)) {
                    return true;
                }
            }
        }
        Class<?> beanClass = bean.getBeanClass();
        return beanClass != null && isClassAcceptedByRegistrationTypes(beanClass, acceptedTypes);
    }

    private boolean isBeanInfoAcceptedByRegistrationTypes(BeanInfo beanInfo, Class<?>[] acceptedTypes) {
        if (beanInfo == null) {
            return false;
        }
        Collection<jakarta.enterprise.lang.model.types.Type> modelTypes = beanInfo.types();
        if (modelTypes != null) {
            for (jakarta.enterprise.lang.model.types.Type modelType : modelTypes) {
                try {
                    Class<?> candidate = BceMetadata.unwrapType(modelType);
                    if (isClassAcceptedByRegistrationTypes(candidate, acceptedTypes)) {
                        return true;
                    }
                } catch (RuntimeException ignored) {
                    // Best effort model filtering.
                }
            }
        }
        if (beanInfo.declaringClass() != null) {
            try {
                Class<?> declaringClass = BceMetadata.unwrapClassInfo(beanInfo.declaringClass());
                if (isClassAcceptedByRegistrationTypes(declaringClass, acceptedTypes)) {
                    return true;
                }
            } catch (RuntimeException ignored) {
                // Best effort model filtering.
            }
        }
        return false;
    }

    private boolean isObserverAcceptedByRegistrationTypes(ObserverInfo observerInfo, Class<?>[] acceptedTypes) {
        if (observerInfo == null) {
            return false;
        }
        try {
            Class<?> eventType = BceMetadata.unwrapType(observerInfo.eventType());
            if (eventType == null) {
                return false;
            }
            for (Class<?> acceptedType : acceptedTypes) {
                if (acceptedType.isAssignableFrom(eventType)) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void invokeRegistrationOrValidationForModel(PhaseMethodInvocation invocation,
                                                        BceSupportedPhase phase,
                                                        BeanInfo beanInfo,
                                                        ObserverInfo observerInfo,
                                                        InterceptorInfo interceptorInfo,
                                                        InjectionPointInfo injectionPointInfo,
                                                        DisposerInfo disposerInfo,
                                                        ScopeInfo scopeInfo,
                                                        StereotypeInfo stereotypeInfo) {
        try {
            Method phaseMethod = invocation.method;
            phaseMethod.setAccessible(true);
            Class<?>[] parameterTypes = phaseMethod.getParameterTypes();
            Object[] args = new Object[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                Class<?> parameterType = parameterTypes[i];
                if (isInterceptorInfo(parameterType)) {
                    args[i] = interceptorInfo;
                    continue;
                }
                if (isObserverInfo(parameterType)) {
                    args[i] = observerInfo;
                    continue;
                }
                if (isBeanInfo(parameterType)) {
                    args[i] = beanInfo;
                    continue;
                }
                if (isInvokerFactory(parameterType)) {
                    args[i] = invokerFactory;
                    continue;
                }
                if (BceRegistrationContext.class.isAssignableFrom(parameterType)) {
                    args[i] = registrationContext;
                    continue;
                }
                if (isInjectionPointInfo(parameterType)) {
                    args[i] = injectionPointInfo;
                    continue;
                }
                if (isDisposerInfo(parameterType)) {
                    args[i] = disposerInfo;
                    continue;
                }
                if (isScopeInfo(parameterType)) {
                    args[i] = scopeInfo;
                    continue;
                }
                if (isStereotypeInfo(parameterType)) {
                    args[i] = stereotypeInfo;
                    continue;
                }
                Object common = resolveCommonServiceArgument(parameterType);
                if (common == null) {
                    throw new DefinitionException("Unsupported BCE " + phase + " parameter type " +
                        parameterType.getName() + " for method " +
                        phaseMethod.getDeclaringClass().getName() + "." + phaseMethod.getName());
                }
                args[i] = common;
            }
            phaseMethod.invoke(invocation.extension, args);
            messageHandler.info("[Syringe] Invoked BCE " + phase + " method: " +
                invocation.extension.getClass().getSimpleName() + "." + phaseMethod.getName());
        } catch (IllegalAccessException e) {
            throw new DefinitionException("Cannot access BCE " + phase + " method " +
                invocation.extension.getClass().getName() + "." + invocation.method.getName(), e);
        } catch (InvocationTargetException e) {
            Throwable target = e.getTargetException();
            if (target instanceof DefinitionException) {
                throw (DefinitionException) target;
            }
            if (isCdiCurrentAccessFailure(target)) {
                throw new NonPortableBehaviourException(
                    "Calling CDI.current() from a build compatible extension method is non-portable", target);
            }
            throw new DefinitionException("Error invoking BCE " + phase + " method " +
                invocation.extension.getClass().getName() + "." + invocation.method.getName(), target);
        }
    }

    private void invokeEnhancementModelMethods(PhaseMethodInvocation invocation,
                                               EnhancementModelState enhancementModelState) {
        Method phaseMethod = invocation.method;
        Class<?> modelType = findEnhancementModelParameterType(phaseMethod);
        if (modelType == null) {
            invokePhaseMethod(invocation, BceSupportedPhase.ENHANCEMENT, null,
                enhancementModelState);
            return;
        }

        if (isClassInfo(modelType) || isClassConfig(modelType)) {
            for (Class<?> clazz : getEnhancedClasses(phaseMethod)) {
                invokeEnhancementForTarget(invocation, clazz, null, null, null, enhancementModelState);
            }
            return;
        }
        if (isMethodInfo(modelType) || isMethodConfig(modelType)) {
            for (Class<?> clazz : getEnhancedClasses(phaseMethod)) {
                List<Method> methods = new ArrayList<>(Arrays.asList(clazz.getDeclaredMethods()));
                methods.sort(Comparator.comparing(Method::getName).thenComparingInt(Method::getParameterCount));
                for (Method method : methods) {
                    if (matchesEnhancementAnnotationFilter(method, phaseMethod)) {
                        invokeEnhancementForTarget(invocation, clazz, method, null, null, enhancementModelState);
                    }
                }
                List<Constructor<?>> constructors = new ArrayList<>(Arrays.asList(clazz.getDeclaredConstructors()));
                constructors.sort(Comparator.comparingInt(Constructor::getParameterCount));
                for (Constructor<?> constructor : constructors) {
                    if (matchesEnhancementAnnotationFilter(constructor, phaseMethod)) {
                        invokeEnhancementForTarget(invocation, clazz, null, constructor, null, enhancementModelState);
                    }
                }
            }
            return;
        }
        if (isFieldInfo(modelType) || isFieldConfig(modelType)) {
            for (Class<?> clazz : getEnhancedClasses(phaseMethod)) {
                List<Field> fields = new ArrayList<>(Arrays.asList(clazz.getDeclaredFields()));
                fields.sort(Comparator.comparing(Field::getName));
                for (Field field : fields) {
                    if (matchesEnhancementAnnotationFilter(field, phaseMethod)) {
                        invokeEnhancementForTarget(invocation, clazz, null, null, field, enhancementModelState);
                    }
                }
            }
        }
    }

    private void invokeEnhancementForTarget(PhaseMethodInvocation invocation,
                                            Class<?> clazz,
                                            Method method,
                                            Constructor<?> constructor,
                                            Field field,
                                            EnhancementModelState enhancementModelState) {
        try {
            Method phaseMethod = invocation.method;
            phaseMethod.setAccessible(true);
            Class<?>[] parameterTypes = phaseMethod.getParameterTypes();
            Object[] args = new Object[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                Class<?> parameterType = parameterTypes[i];
                Object mapped = mapEnhancementParameter(
                    parameterType, clazz, method, constructor, field, enhancementModelState);
                if (mapped == null) {
                    throw new DefinitionException("Unsupported ENHANCEMENT parameter type " +
                        parameterType.getName() + " for method " +
                        phaseMethod.getDeclaringClass().getName() + "." + phaseMethod.getName());
                }
                args[i] = mapped;
            }
            phaseMethod.invoke(invocation.extension, args);
            messageHandler.info("[Syringe] Invoked BCE ENHANCEMENT method: " +
                invocation.extension.getClass().getSimpleName() + "." + phaseMethod.getName());
        } catch (IllegalAccessException e) {
            throw new DefinitionException("Cannot access BCE ENHANCEMENT method " +
                invocation.extension.getClass().getName() + "." + invocation.method.getName(), e);
        } catch (InvocationTargetException e) {
            Throwable target = e.getTargetException();
            if (target instanceof DefinitionException) {
                throw (DefinitionException) target;
            }
            if (isCdiCurrentAccessFailure(target)) {
                throw new NonPortableBehaviourException(
                    "Calling CDI.current() from a build compatible extension method is non-portable", target);
            }
            throw new DefinitionException("Error invoking BCE ENHANCEMENT method " +
                invocation.extension.getClass().getName() + "." + invocation.method.getName(), target);
        }
    }

    private boolean isCdiCurrentAccessFailure(Throwable target) {
        if (!(target instanceof IllegalStateException)) {
            return false;
        }
        String message = target.getMessage();
        if ("Unable to access CDI".equals(message)) {
            return true;
        }
        for (StackTraceElement element : target.getStackTrace()) {
            if ("jakarta.enterprise.inject.spi.CDI".equals(element.getClassName()) &&
                "current".equals(element.getMethodName())) {
                return true;
            }
        }
        return false;
    }

    private Object mapEnhancementParameter(Class<?> parameterType,
                                           Class<?> clazz,
                                           Method method,
                                           Constructor<?> constructor,
                                           Field field,
                                           EnhancementModelState enhancementModelState) {
        if (isClassInfo(parameterType)) {
            if (enhancementModelState != null && enhancementModelState.classConfigs.containsKey(clazz)) {
                return enhancementModelState.classConfigs.get(clazz).info();
            }
            return BceMetadata.classInfo(clazz);
        }
        if (isClassConfig(parameterType)) {
            if (enhancementModelState == null) {
                return BceEnhancementModels.classConfig(clazz);
            }
            return enhancementModelState.classConfig(clazz);
        }
        if (isMethodInfo(parameterType)) {
            if (method == null && constructor == null) {
                return null;
            }
            if (method != null &&
                enhancementModelState != null &&
                enhancementModelState.methodConfigs.containsKey(method)) {
                return enhancementModelState.methodConfigs.get(method).info();
            }
            if (method != null) {
                return BceMetadata.methodInfo(method);
            }
            return BceMetadata.methodInfo(constructor);
        }
        if (isMethodConfig(parameterType)) {
            if (method == null && constructor == null) {
                return null;
            }
            if (method != null) {
                if (enhancementModelState == null) {
                    return BceEnhancementModels.methodConfig(method);
                }
                return enhancementModelState.methodConfig(method);
            }
            return BceEnhancementModels.methodConfig(constructor);
        }
        if (isFieldInfo(parameterType)) {
            if (field == null) {
                return null;
            }
            if (enhancementModelState != null && enhancementModelState.fieldConfigs.containsKey(field)) {
                return enhancementModelState.fieldConfigs.get(field).info();
            }
            return BceMetadata.fieldInfo(field);
        }
        if (isFieldConfig(parameterType)) {
            if (field == null) {
                return null;
            }
            if (enhancementModelState == null) {
                return BceEnhancementModels.fieldConfig(field);
            }
            return enhancementModelState.fieldConfig(field);
        }
        return resolveCommonServiceArgument(parameterType);
    }

    private static final class EnhancementModelState {
        private final Map<Class<?>, ClassConfig> classConfigs = new HashMap<>();
        private final Map<Method, MethodConfig> methodConfigs = new HashMap<>();
        private final Map<Field, FieldConfig> fieldConfigs = new HashMap<>();

        private ClassConfig classConfig(Class<?> clazz) {
            if (!classConfigs.containsKey(clazz)) {
                classConfigs.put(clazz, BceEnhancementModels.classConfig(clazz, methodConfigs, fieldConfigs));
            }
            return classConfigs.get(clazz);
        }

        private MethodConfig methodConfig(Method method) {
            if (!methodConfigs.containsKey(method)) {
                methodConfigs.put(method, BceEnhancementModels.methodConfig(method));
            }
            return methodConfigs.get(method);
        }

        private FieldConfig fieldConfig(Field field) {
            if (!fieldConfigs.containsKey(field)) {
                fieldConfigs.put(field, BceEnhancementModels.fieldConfig(field));
            }
            return fieldConfigs.get(field);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void applyEnhancementModelState(EnhancementModelState enhancementModelState) {
        Set<Class<?>> affectedClasses = new LinkedHashSet<>(enhancementModelState.classConfigs.keySet());
        for (Method method : enhancementModelState.methodConfigs.keySet()) {
            affectedClasses.add(method.getDeclaringClass());
        }
        for (Field field : enhancementModelState.fieldConfigs.keySet()) {
            affectedClasses.add(field.getDeclaringClass());
        }

        for (Class<?> clazz : affectedClasses) {
            jakarta.enterprise.inject.spi.AnnotatedType<?> baseType = knowledgeBase.getAnnotatedTypeOverride((Class) clazz);
            if (baseType == null) {
                baseType = beanManager.createAnnotatedType((Class) clazz);
            }

            AnnotatedTypeConfiguratorImpl typeConfigurator = new AnnotatedTypeConfiguratorImpl(baseType);
            boolean changed = false;

            ClassConfig classConfig = enhancementModelState.classConfigs.get(clazz);
            if (classConfig != null) {
                replaceTypeAnnotations(typeConfigurator, classConfig.info().annotations());
                changed = true;
            }

            for (Map.Entry<Field, FieldConfig> entry : enhancementModelState.fieldConfigs.entrySet()) {
                Field field = entry.getKey();
                if (!clazz.equals(field.getDeclaringClass())) {
                    continue;
                }
                if (replaceFieldAnnotations(typeConfigurator, field, entry.getValue().info().annotations())) {
                    changed = true;
                }
            }

            for (Map.Entry<Method, MethodConfig> entry : enhancementModelState.methodConfigs.entrySet()) {
                Method method = entry.getKey();
                if (!clazz.equals(method.getDeclaringClass())) {
                    continue;
                }
                if (replaceMethodAnnotations(typeConfigurator, method, entry.getValue())) {
                    changed = true;
                }
            }

            if (changed) {
                knowledgeBase.setAnnotatedTypeOverride(clazz, typeConfigurator.complete());
            }
        }
    }

    private void replaceTypeAnnotations(AnnotatedTypeConfiguratorImpl<?> typeConfigurator,
                                        Collection<AnnotationInfo> annotations) {
        typeConfigurator.removeAll();
        addAnnotationsToType(typeConfigurator, annotations);
    }

    private boolean replaceFieldAnnotations(AnnotatedTypeConfiguratorImpl<?> typeConfigurator,
                                            Field field,
                                            Collection<AnnotationInfo> annotations) {
        for (AnnotatedFieldConfigurator<?> fieldConfigurator : typeConfigurator.fields()) {
            if (field.equals(fieldConfigurator.getAnnotated().getJavaMember())) {
                fieldConfigurator.removeAll();
                addAnnotationsToField(fieldConfigurator, annotations);
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean replaceMethodAnnotations(AnnotatedTypeConfiguratorImpl<?> typeConfigurator,
                                             Method method,
                                             MethodConfig methodConfig) {
        for (AnnotatedMethodConfigurator<?> methodConfigurator : typeConfigurator.methods()) {
            if (!method.equals(methodConfigurator.getAnnotated().getJavaMember())) {
                continue;
            }

            methodConfigurator.removeAll();
            addAnnotationsToMethod(methodConfigurator, methodConfig.info().annotations());

            List<ParameterConfig> parameterConfigs = methodConfig.parameters();
            List<AnnotatedParameterConfigurator<?>> parameterConfigurators =
                    (List<AnnotatedParameterConfigurator<?>>) (List<?>) methodConfigurator.params();
            int parameterCount = Math.min(parameterConfigs.size(), parameterConfigurators.size());
            for (int i = 0; i < parameterCount; i++) {
                AnnotatedParameterConfigurator<?> parameterConfigurator = parameterConfigurators.get(i);
                parameterConfigurator.removeAll();
                addAnnotationsToParameter(parameterConfigurator, parameterConfigs.get(i).info().annotations());
            }

            return true;
        }
        return false;
    }

    private void addAnnotationsToType(AnnotatedTypeConfiguratorImpl<?> typeConfigurator,
                                      Collection<AnnotationInfo> annotations) {
        for (AnnotationInfo annotationInfo : annotations) {
            typeConfigurator.add(BceMetadata.unwrapAnnotationInfo(annotationInfo));
        }
    }

    @SuppressWarnings("rawtypes")
    private void addAnnotationsToField(AnnotatedFieldConfigurator fieldConfigurator,
                                       Collection<AnnotationInfo> annotations) {
        for (AnnotationInfo annotationInfo : annotations) {
            fieldConfigurator.add(BceMetadata.unwrapAnnotationInfo(annotationInfo));
        }
    }

    @SuppressWarnings("rawtypes")
    private void addAnnotationsToMethod(AnnotatedMethodConfigurator methodConfigurator,
                                        Collection<AnnotationInfo> annotations) {
        for (AnnotationInfo annotationInfo : annotations) {
            methodConfigurator.add(BceMetadata.unwrapAnnotationInfo(annotationInfo));
        }
    }

    @SuppressWarnings("rawtypes")
    private void addAnnotationsToParameter(AnnotatedParameterConfigurator parameterConfigurator,
                                           Collection<AnnotationInfo> annotations) {
        for (AnnotationInfo annotationInfo : annotations) {
            parameterConfigurator.add(BceMetadata.unwrapAnnotationInfo(annotationInfo));
        }
    }

    private List<Class<?>> getEnhancedClasses(Method phaseMethod) {
        Enhancement enhancement = getEnhancementAnnotation(phaseMethod);
        if (enhancement == null) {
            return Collections.emptyList();
        }
        List<Class<?>> classes = new ArrayList<>();
        for (Class<?> clazz : knowledgeBase.getClasses()) {
            if (matchesEnhancementTypeFilter(clazz, enhancement) &&
                matchesEnhancementAnnotationFilter(clazz, phaseMethod)) {
                classes.add(clazz);
            }
        }
        classes.sort(Comparator.comparing(Class::getName));
        return classes;
    }

    private boolean matchesEnhancementTypeFilter(Class<?> clazz, Enhancement enhancement) {
        Class<?>[] acceptedTypes = enhancement.types();
        if (acceptedTypes == null || acceptedTypes.length == 0) {
            return true;
        }
        for (Class<?> accepted : acceptedTypes) {
            if (enhancement.withSubtypes()) {
                if (accepted.isAssignableFrom(clazz)) {
                    return true;
                }
            } else if (accepted.equals(clazz)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesEnhancementAnnotationFilter(java.lang.reflect.AnnotatedElement element,
                                                       Method phaseMethod) {
        Enhancement enhancement = getEnhancementAnnotation(phaseMethod);
        if (enhancement == null) {
            return false;
        }
        Class<? extends Annotation>[] requiredAnnotations = enhancement.withAnnotations();
        if (requiredAnnotations == null || requiredAnnotations.length == 0) {
            return true;
        }
        return hasRequiredEnhancementAnnotation(element, requiredAnnotations);
    }

    private boolean hasEnhancementModelParameter(Method method) {
        return findEnhancementModelParameterType(method) != null;
    }

    private Class<?> findEnhancementModelParameterType(Method method) {
        for (Class<?> parameterType : method.getParameterTypes()) {
            if (isEnhancementModelType(parameterType)) {
                return parameterType;
            }
        }
        return null;
    }

    private boolean isEnhancementModelType(Class<?> parameterType) {
        return isClassInfo(parameterType) ||
            isClassConfig(parameterType) ||
            isMethodInfo(parameterType) ||
            isMethodConfig(parameterType) ||
            isFieldInfo(parameterType) ||
            isFieldConfig(parameterType);
    }

    private boolean areSupportedParameterTypes(Class<?>[] parameterTypes) {
        List<Class<?>> seen = new ArrayList<>();
        for (Class<?> parameterType : parameterTypes) {
            Class<?> matched = null;
            for (Class<?> allowedType : new Class<?>[]{Types.class, Messages.class, MetaAnnotations.class, ScannedClasses.class}) {
                if (allowedType.isAssignableFrom(parameterType)) {
                    matched = allowedType;
                    break;
                }
            }
            if (matched == null) {
                return false;
            }
            if (seen.contains(matched)) {
                return false;
            }
            seen.add(matched);
        }
        return true;
    }

    private Object resolveCommonServiceArgument(Class<?> parameterType) {
        if (isTypes(parameterType)) {
            return types;
        }
        if (isMessages(parameterType)) {
            return messages;
        }
        if (isMetaAnnotations(parameterType)) {
            return metaAnnotations;
        }
        if (isScannedClasses(parameterType)) {
            return scannedClasses;
        }
        return null;
    }

    private static class PhaseMethodInvocation {
        private final BuildCompatibleExtension extension;
        private final Method method;

        private PhaseMethodInvocation(BuildCompatibleExtension extension, Method method) {
            this.extension = extension;
            this.method = method;
        }
    }
}
