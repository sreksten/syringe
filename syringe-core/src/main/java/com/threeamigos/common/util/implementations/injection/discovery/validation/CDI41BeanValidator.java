package com.threeamigos.common.util.implementations.injection.discovery.validation;

import com.threeamigos.common.util.implementations.injection.annotations.*;
import com.threeamigos.common.util.implementations.injection.*;
import com.threeamigos.common.util.implementations.injection.discovery.*;
import com.threeamigos.common.util.implementations.injection.discovery.validation.bean.*;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.knowledgebase.ScopeMetadata;
import com.threeamigos.common.util.implementations.injection.scopes.InjectionPointImpl;
import com.threeamigos.common.util.implementations.injection.resolution.BeanImpl;
import com.threeamigos.common.util.implementations.injection.resolution.ProducerBean;
import com.threeamigos.common.util.implementations.injection.types.TypeHelper;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Prioritized;
import jakarta.enterprise.inject.spi.InjectionPoint;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsEnum.*;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.*;
import static com.threeamigos.common.util.implementations.injection.types.TypeHelper.*;
import static com.threeamigos.common.util.implementations.injection.types.TypeClosureHelper.parameterizedDeclarationOf;

/**
 * Validates that a Java class is a CDI Managed Bean, according to CDI 4.1 rules.
 *
 * <p><b>IMPORTANT: Alternative Bean Enabling (CDI 4.1 Section 5.1.3)</b>
 * <ul>
 *   <li>Without beans.xml support, alternatives MUST have {@literal @}Priority to be enabled</li>
 *   <li>Alternatives without {@literal @}Priority are NOT enabled and will be skipped</li>
 *   <li>This matches CDI 4.1 spec: alternatives enabled via beans.xml OR {@literal @}Priority</li>
 * </ul>
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Validate bean class eligibility and constructor rules</li>
 *   <li>Validate injection points (@Inject fields / initializer methods / parameters)</li>
 *   <li>Validate producer fields/methods (@Produces) and ensure illegal combinations are rejected</li>
 *   <li>Check if alternatives are properly enabled (must have {@literal @}Priority without beans.xml)</li>
 *   <li>Report problems to {@link KnowledgeBase} as definition errors or injection errors</li>
 *   <li>Produce a {@link BeanImpl} and register it in the {@link KnowledgeBase} on success</li>
 * </ul>
 */
public class CDI41BeanValidator {

    private final KnowledgeBase knowledgeBase;
    private final BeanTypesExtractor beanTypesExtractor;
    private final TypeHelper typeHelper;
    private final StereotypePriorityValidator stereotypePriorityValidator;
    private final BeanClassEligibilityValidator beanClassEligibilityValidator;
    private final BeanAttributesExtractor beanAttributesExtractor;
    private final InterceptorDecoratorDefinitionValidator interceptorDecoratorDefinitionValidator;
    private final InjectionMetadataValidator injectionMetadataValidator;
    private final ProducerDisposerValidator producerDisposerValidator;
    private final BeanRegistrationService beanRegistrationService;
    private final boolean cdiFullLegacyInterceptionEnabled;
    private final Map<Class<?>, Class<?>> specializingBeansBySuperclass = new HashMap<>();
    private final Map<String, Method> specializingProducerMethodsBySpecializedSignature = new HashMap<>();
    private final Map<String, ProducerBean<?>> producerBeansByMethodSignature = new HashMap<>();
    private final Set<String> suppressedSpecializedProducerMethodSignatures = new HashSet<>();
    private Annotation[] overrideAnnotations;
    private Class<?> overrideAnnotationsClass;
    private AnnotatedType<?> currentAnnotatedTypeOverride;

    public CDI41BeanValidator(KnowledgeBase knowledgeBase) {
        this(knowledgeBase, false);
    }

    public CDI41BeanValidator(KnowledgeBase knowledgeBase, boolean cdiFullLegacyInterceptionEnabled) {
        this.knowledgeBase = Objects.requireNonNull(knowledgeBase, "knowledgeBase cannot be null");
        this.beanTypesExtractor = new BeanTypesExtractor();
        this.typeHelper = new TypeHelper();
        this.stereotypePriorityValidator = new StereotypePriorityValidator(this::isScopeAnnotationType);
        this.beanClassEligibilityValidator = new BeanClassEligibilityValidator(this);
        this.beanAttributesExtractor = new BeanAttributesExtractor(knowledgeBase, this);
        this.interceptorDecoratorDefinitionValidator = new InterceptorDecoratorDefinitionValidator(knowledgeBase, this);
        this.injectionMetadataValidator = new InjectionMetadataValidator(knowledgeBase, this);
        this.producerDisposerValidator = new ProducerDisposerValidator(
                knowledgeBase,
                typeHelper,
                this,
                specializingProducerMethodsBySpecializedSignature);
        this.beanRegistrationService = new BeanRegistrationService(
                knowledgeBase,
                beanTypesExtractor,
                this,
                producerBeansByMethodSignature,
                suppressedSpecializedProducerMethodSignatures);
        this.cdiFullLegacyInterceptionEnabled = cdiFullLegacyInterceptionEnabled;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public BeanImpl<?> validateAndRegisterRaw(Class<?> clazz, BeanArchiveMode beanArchiveMode, AnnotatedType<?> annotatedTypeOverride) {
        return validateAndRegister((Class) clazz, beanArchiveMode, (AnnotatedType) annotatedTypeOverride);
    }

    <T> BeanImpl<T> validateAndRegister(Class<T> clazz,
                                        BeanArchiveMode beanArchiveMode,
                                        AnnotatedType<T> annotatedTypeOverride) {
        Objects.requireNonNull(clazz, "Class cannot be null");

        boolean valid = true;
        try {
            currentAnnotatedTypeOverride = annotatedTypeOverride;
            initializeAnnotatedTypeMemberViews(annotatedTypeOverride);
            overrideAnnotations = annotatedTypeOverride != null
                    ? annotatedTypeOverride.getAnnotations().toArray(new Annotation[0])
                    : null;
            overrideAnnotationsClass = annotatedTypeOverride != null ? clazz : null;

        // CDI 4.1 §2.4.2: scope types with attributes are non-portable.
        validateNonPortableScopeTypes(clazz);
        // CDI 4.1 §5.2.6: qualifier members that are array/annotation-valued should be @Nonbinding.
        validateNonPortableQualifierMembers(clazz);
        // CDI 4.1 §2.8: a stereotype may declare at most one scope.
        validateStereotypeScopeDeclaration(clazz);
        // CDI 4.1 §2.8.1.3: non-empty @Named on stereotype is a definition error.
        validateStereotypeNamedDeclaration(clazz);
        // CDI 4.1 §2.8: stereotype qualifier/@Typed misuse is non-portable.
        validateStereotypeNonPortableDeclarations(clazz);
        // CDI 4.1 §2.8.1.6: target compatibility for stereotypes-with-stereotypes.
        validateStereotypeTargetCompatibility(clazz);
        // CDI Lite §8: only interceptor-binding-based interception is portable.
        validateNonPortableInterceptionForms(clazz);
        // Interceptor spec: conflicting interceptor binding values (including transitive/stereotype)
        // are definition errors.
        validateConflictingInterceptorBindings(clazz);

        // 1) Bean class eligibility (managed bean type)
        if (!isCandidateBeanClass(clazz, beanArchiveMode)) {
            // Not necessarily an error; just not a bean (CDI scans lots of classes).
            return null;
        }

        // CDI 4.1 §2.8.1.5: if stereotypes declare different priorities,
        // the bean must explicitly declare @Priority.
        validateStereotypePriorityDeclaration(clazz);

        if (hasVetoedAnnotation(clazz)) {
            return null;
        }
        if (knowledgeBase.isTypeVetoed(clazz)) {
            return null;
        }

        // 2) Basic structural constraints
        if (clazz.isInterface() || clazz.isAnnotation() || clazz.isEnum() || clazz.isPrimitive() || clazz.isArray()) {
            knowledgeBase.addDefinitionError(clazz.getName() + ": is not a valid CDI bean class type");
            return null;
        }

        boolean isDecorator = hasDecoratorAnnotation(clazz);
        if (Modifier.isAbstract(clazz.getModifiers()) && !isDecorator) {
            // In explicit archives, abstract helper superclasses can be discovered while still not being beans.
            // Only report an abstract bean-class definition error when the class is explicitly bean-defining.
            boolean explicitlyBeanDefining = hasBeanDefiningAnnotation(clazz)
                    || hasAlternativeAnnotation(clazz)
                    || hasInterceptorAnnotation(clazz);
            boolean abstractProducerOrDisposerFound = false;
            for (Method method : clazz.getDeclaredMethods()) {
                if (!Modifier.isAbstract(method.getModifiers())) {
                    continue;
                }
                if (hasProducesAnnotation(method)) {
                    knowledgeBase.addDefinitionError(fmtMethod(method) + ": producer method must not be abstract");
                    abstractProducerOrDisposerFound = true;
                }
                if (hasDisposesParameter(method)) {
                    knowledgeBase.addDefinitionError(fmtMethod(method) + ": disposer method must not be abstract");
                    abstractProducerOrDisposerFound = true;
                }
            }
            if (explicitlyBeanDefining && !hasConcreteDiscoveredSubtype(clazz)) {
                knowledgeBase.addDefinitionError(clazz.getName() + ": bean class must not be abstract");
            } else if (!abstractProducerOrDisposerFound) {
                return null;
            }
            return null;
        }

        if (clazz.isLocalClass() || clazz.isAnonymousClass() || clazz.isSynthetic()) {
            // Compiler-generated helper classes can appear during discovery and must be ignored.
            return null;
        }

        if (clazz.isMemberClass() && !Modifier.isStatic(clazz.getModifiers())) {
            knowledgeBase.addDefinitionError(clazz.getName() + ": non-static inner classes are not valid CDI beans");
            return null;
        }

        validateManagedBeanSpecializationConstraint(clazz, beanArchiveMode);

        // 3) Scope sanity (at most one scope) + capture it for bean construction
        Class<? extends Annotation> beanScope = null;
        try {
            beanScope = validateScopeAnnotations(clazz);
        } catch (DefinitionException e) {
            knowledgeBase.addDefinitionError(clazz.getName() + ": " + e.getMessage());
            valid = false;
        }

        boolean isInterceptor = hasInterceptorAnnotation(clazz);
        if (isInterceptor && isDecorator) {
            knowledgeBase.addDefinitionError(clazz.getName() +
                    ": bean class may not be annotated with both @Interceptor and @Decorator");
            return null;
        }

        if ((isInterceptor || isDecorator) && hasSpecializesAnnotation(clazz)) {
            throw new NonPortableBehaviourException(clazz.getName() +
                    ": interceptor or decorator annotated @Specializes is non-portable");
        }

        // 4) Validate producers and injection points on fields/methods
        boolean hasInjectionPoints = false;

        for (Field field : clazz.getDeclaredFields()) {
            boolean inject = hasInjectAnnotation(field);
            boolean produces = hasProducesAnnotation(field);

            if (inject) {
                hasInjectionPoints = true;
                valid &= validateInjectField(field);
            }

            if (produces) {
                if (isInterceptor) {
                    knowledgeBase.addDefinitionError(fmtField(field) +
                            ": interceptor may not declare producer fields");
                    valid = false;
                } else if (isDecorator) {
                    knowledgeBase.addDefinitionError(fmtField(field) +
                            ": decorator may not declare producer fields");
                    valid = false;
                } else {
                    valid &= validateProducerField(field);

                    // Create and register ProducerBean for this producer field
                    createAndRegisterProducerBean(clazz, null, field);
                }
            }

            // Disallow illegal combos proactively
            if (inject && produces) {
                knowledgeBase.addDefinitionError(fmtField(field) + ": may not declare both @Inject and @Produces");
                valid = false;
            }
        }

        for (Method method : clazz.getDeclaredMethods()) {
            boolean inject = hasInjectAnnotation(method);
            boolean produces = hasProducesAnnotation(method);
            boolean disposes = hasDisposesParameter(method);

            if (inject) {
                hasInjectionPoints = true;
                valid &= validateInitializerMethod(method);
            }

            if (produces) {
                if (isInterceptor) {
                    knowledgeBase.addDefinitionError(fmtMethod(method) +
                            ": interceptor may not declare producer methods");
                    valid = false;
                } else if (isDecorator) {
                    knowledgeBase.addDefinitionError(fmtMethod(method) +
                            ": decorator may not declare producer methods");
                    valid = false;
                } else {
                    valid &= validateProducerMethod(method);

                    // Create and register ProducerBean for this producer method
                    createAndRegisterProducerBean(clazz, method, null);
                }
            }

            if (disposes) {
                if (isInterceptor) {
                    knowledgeBase.addDefinitionError(fmtMethod(method) +
                            ": interceptor may not declare disposer methods");
                    valid = false;
                } else if (isDecorator) {
                    knowledgeBase.addDefinitionError(fmtMethod(method) +
                            ": decorator may not declare disposer methods");
                    valid = false;
                } else if (!produces) {
                    valid &= validateDisposerMethod(method);
                    valid &= validateDisposerMethodHasMatchingProducer(clazz, method);
                }
            }

            if (inject && produces) {
                knowledgeBase.addDefinitionError(fmtMethod(method) + ": may not declare both @Inject and @Produces");
                valid = false;
            }
        }

        // 5) Constructor parameter annotations that are never legal on bean constructors.
        valid &= validateIllegalConstructorParameterAnnotations(clazz);

        // 6) Constructor rules (only if it is a bean OR it has injection points / producers)
        boolean hasInjectConstructor = Arrays.stream(clazz.getDeclaredConstructors())
                .anyMatch(AnnotationsHelper::hasInjectAnnotation);
        boolean relevantClass = hasInjectionPoints || hasAnyProducer(clazz) || hasInjectConstructor;
        if (relevantClass) {
            @SuppressWarnings("unchecked")
            Constructor<T> ctor = (Constructor<T>) findBeanConstructor(clazz);
            if (ctor == null) {
                valid = false;
            } else {
                knowledgeBase.addConstructor(clazz, ctor);
            }
        }

        // 7) Check for @Interceptor and @Decorator (not managed beans)
        if (isInterceptor) {
            if (isAlternativeDeclared(clazz)) {
                throw new NonPortableBehaviourException(clazz.getName() +
                        ": interceptor is declared as @Alternative. Alternative interceptors are non-portable.");
            }
            String interceptorName = extractBeanName(clazz);
            if (interceptorName != null && !interceptorName.isEmpty()) {
                throw new NonPortableBehaviourException(clazz.getName() +
                        ": interceptor declares bean name '" + interceptorName +
                        "'. Named interceptors are non-portable.");
            }
            if (beanScope != null && !hasDependentAnnotation(beanScope)) {
                throw new NonPortableBehaviourException(clazz.getName() +
                        ": interceptor declares scope @" + beanScope.getSimpleName() +
                        ". Interceptors with any scope other than @Dependent are non-portable.");
            }
            knowledgeBase.addInterceptor(clazz);
            // Validate and register interceptor metadata
            validateAndRegisterInterceptor(clazz);
            // Interceptors are not managed beans - return null (no bean to register)
            return null;
        }

        if (isDecorator) {
            if (isAlternativeDeclared(clazz)) {
                throw new NonPortableBehaviourException(clazz.getName() +
                        ": decorator is declared as @Alternative. Alternative decorators are non-portable.");
            }
            String decoratorName = extractBeanName(clazz);
            if (decoratorName != null && !decoratorName.isEmpty()) {
                throw new NonPortableBehaviourException(clazz.getName() +
                        ": decorator declares bean name '" + decoratorName +
                        "'. Named decorators are non-portable.");
            }
            if (beanScope != null && !hasDependentAnnotation(beanScope)) {
                throw new NonPortableBehaviourException(clazz.getName() +
                        ": decorator declares scope @" + beanScope.getSimpleName() +
                        ". Decorators with any scope other than @Dependent are non-portable.");
            }
            // Validate decorator-specific rules before registering
            // Per CDI spec: A decorator must have exactly one @Delegate injection point
            validateDecoratorDelegateInjectionPoints(clazz);
            validateDecoratorDoesNotDeclareObserverMethods(clazz);

            knowledgeBase.addDecorator(clazz);
            // Validate and register decorator metadata
            validateAndRegisterDecorator(clazz);
            // Decorators are not managed beans - return null (no bean to register)
            return null;
        }

        if (hasAnyDelegateAnnotation(clazz)) {
            knowledgeBase.addDefinitionError(clazz.getName() +
                    ": bean is not a @Decorator but declares @Delegate injection point(s)");
            valid = false;
        }

        // 8) Check if this is an alternative bean
        boolean alternative = isAlternativeDeclared(clazz);
        boolean alternativeEnabled = isAlternativeEnabled(clazz, clazz, alternative);

        return beanRegistrationService.createAndRegisterManagedBean(
                clazz,
                beanArchiveMode,
                annotatedTypeOverride,
                currentAnnotatedTypeOverride,
                alternative,
                alternativeEnabled,
                valid,
                beanScope);
        } finally {
            currentAnnotatedTypeOverride = null;
            overrideAnnotations = null;
            overrideAnnotationsClass = null;
        }
    }

    private void initializeAnnotatedTypeMemberViews(AnnotatedType<?> annotatedTypeOverride) {
        if (annotatedTypeOverride == null) {
            return;
        }
        // Force member views through replacement AnnotatedType so lifecycle processing
        // consistently consumes extension-provided metadata across constructors, fields and methods.
        annotatedTypeOverride.getConstructors();
        annotatedTypeOverride.getFields();
        annotatedTypeOverride.getMethods();
    }

    public void addGenericSelfTypeForManagedBean(BeanImpl<?> bean, Class<?> beanClass) {
        if (bean == null || beanClass == null) {
            return;
        }
        if (beanClass.getTypeParameters().length == 0) {
            return;
        }
        Set<Type> updatedTypes = new LinkedHashSet<>(bean.getTypes());
        updatedTypes.remove(beanClass);
        updatedTypes.add(parameterizedDeclarationOf(beanClass));
        bean.setTypes(updatedTypes);
    }

    @SuppressWarnings("unchecked")
    private void validateNonPortableScopeTypes(Class<?> clazz) {
        // Validate scope type declaration itself.
        if (clazz.isAnnotation()) {
            Class<? extends Annotation> annotationType = (Class<? extends Annotation>) clazz;
            if (isScopeAnnotationType(annotationType) && annotationType.getDeclaredMethods().length > 0) {
                throw new NonPortableBehaviourException(annotationType.getName() +
                        ": scope type declares attributes. Scope types with attributes are non-portable.");
            }
        }

        // Validate any scope used by this class.
        for (Annotation annotation : annotationsOf(clazz)) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (isScopeAnnotationType(annotationType) && annotationType.getDeclaredMethods().length > 0) {
                throw new NonPortableBehaviourException(clazz.getName() +
                        ": declares non-portable scope type @" + annotationType.getSimpleName() +
                        " which has attributes.");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void validateNonPortableQualifierMembers(Class<?> clazz) {
        if (!clazz.isAnnotation()) {
            return;
        }

        Class<? extends Annotation> annotationType = (Class<? extends Annotation>) clazz;
        if (!hasQualifierAnnotation(annotationType)) {
            return;
        }

        for (Method member : annotationType.getDeclaredMethods()) {
            if (hasNonbindingAnnotation(member)) {
                continue;
            }

            Class<?> returnType = member.getReturnType();
            if (returnType.isArray() || returnType.isAnnotation()) {
                throw new NonPortableBehaviourException(annotationType.getName() +
                        ": qualifier member '" + member.getName() + "' has non-portable type " +
                        returnType.getTypeName() + " and must be annotated @Nonbinding");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void validateStereotypeScopeDeclaration(Class<?> clazz) {
        stereotypePriorityValidator.validateStereotypeScopeDeclaration(clazz);
    }

    @SuppressWarnings("unchecked")
    private void validateStereotypeNamedDeclaration(Class<?> clazz) {
        stereotypePriorityValidator.validateStereotypeNamedDeclaration(clazz);
    }

    private void validateNonPortableInterceptionForms(Class<?> clazz) {
        if (cdiFullLegacyInterceptionEnabled) {
            return;
        }
        if (hasLegacyInterceptorDeclaration(annotationsOf(clazz))) {
            throw new NonPortableBehaviourException(clazz.getName() +
                    ": uses @Interceptors/@ExcludeClassInterceptors/@ExcludeDefaultInterceptors. " +
                    "These interception forms are non-portable in CDI Lite.");
        }

        final Method[] declaredMethods;
        try {
            declaredMethods = clazz.getDeclaredMethods();
        } catch (LinkageError e) {
            // Some third-party classes on the classpath reference optional or inaccessible types.
            // These classes are not CDI beans and should not fail validation of non-portable
            // interception forms.
            return;
        } catch (TypeNotPresentException e) {
            return;
        }

        for (Method method : declaredMethods) {
            if (hasLegacyInterceptorDeclaration(method.getAnnotations())) {
                throw new NonPortableBehaviourException(fmtMethod(method) +
                        ": uses @Interceptors/@ExcludeClassInterceptors/@ExcludeDefaultInterceptors. " +
                        "These interception forms are non-portable in CDI Lite.");
            }
        }
    }

    private boolean hasLegacyInterceptorDeclaration(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (INTERCEPTORS.matches(annotationType) ||
                EXCLUDE_CLASS_INTERCEPTORS.matches(annotationType) ||
                EXCLUDE_DEFAULT_INTERCEPTORS.matches(annotationType)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasConcreteDiscoveredSubtype(Class<?> abstractType) {
        if (abstractType == null) {
            return false;
        }
        for (Class<?> candidate : knowledgeBase.getClasses()) {
            if (candidate == null || candidate.equals(abstractType)) {
                continue;
            }
            if (Modifier.isAbstract(candidate.getModifiers())) {
                continue;
            }
            if (!abstractType.isAssignableFrom(candidate)) {
                continue;
            }
            BeanArchiveMode candidateMode = knowledgeBase.getBeanArchiveMode(candidate);
            if (isCandidateBeanClass(candidate, candidateMode)) {
                return true;
            }
        }
        return false;
    }

    private boolean isLegacyClassInterceptorLifecycleMethod(Class<?> declaringClass, Method lifecycleMethod) {
        if (lifecycleMethod.getParameterCount() != 1) {
            return false;
        }
        Class<?> parameterType = lifecycleMethod.getParameterTypes()[0];
        if (!jakarta.interceptor.InvocationContext.class.isAssignableFrom(parameterType)) {
            return false;
        }
        return isReferencedByInterceptorsAnnotation(declaringClass);
    }

    private boolean isCdiInterceptorLifecycleMethod(Class<?> rootClass, Method lifecycleMethod) {
        if (lifecycleMethod.getParameterCount() != 1) {
            return false;
        }
        Class<?> parameterType = lifecycleMethod.getParameterTypes()[0];
        if (!jakarta.interceptor.InvocationContext.class.isAssignableFrom(parameterType)) {
            return false;
        }

        if (isInterceptorClass(rootClass)) {
            return true;
        }

        Class<?> declaringClass = lifecycleMethod.getDeclaringClass();
        if (isInterceptorClass(declaringClass)) {
            return true;
        }

        // Superclasses in interceptor hierarchies may declare lifecycle callback methods
        // with InvocationContext. Treat them as interceptor lifecycle methods even if the
        // superclass itself is not annotated with @Interceptor.
        return isInInterceptorHierarchy(rootClass) || isInInterceptorHierarchy(declaringClass);
    }

    private boolean isInInterceptorHierarchy(Class<?> candidateType) {
        if (candidateType == null) {
            return false;
        }
        Collection<Class<?>> classes = knowledgeBase.getClasses();
        if (classes == null || classes.isEmpty()) {
            return false;
        }
        for (Class<?> discoveredClass : classes) {
            if (discoveredClass == null) {
                continue;
            }
            if (!isInterceptorClass(discoveredClass)) {
                continue;
            }
            if (candidateType.isAssignableFrom(discoveredClass)) {
                return true;
            }
        }
        return false;
    }

    private boolean isReferencedByInterceptorsAnnotation(Class<?> interceptorClass) {
        Collection<Class<?>> classes = knowledgeBase.getClasses();
        if (classes == null || classes.isEmpty()) {
            return false;
        }
        for (Class<?> candidate : classes) {
            if (candidate == null) {
                continue;
            }
            if (declaresInterceptorsAnnotation(candidate.getDeclaredAnnotations(), interceptorClass)) {
                return true;
            }
            for (Method method : candidate.getDeclaredMethods()) {
                if (declaresInterceptorsAnnotation(method.getDeclaredAnnotations(), interceptorClass)) {
                    return true;
                }
            }
            for (Constructor<?> constructor : candidate.getDeclaredConstructors()) {
                if (declaresInterceptorsAnnotation(constructor.getDeclaredAnnotations(), interceptorClass)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean declaresInterceptorsAnnotation(Annotation[] annotations, Class<?> interceptorClass) {
        for (Annotation annotation : annotations) {
            if (!INTERCEPTORS.matches(annotation.annotationType())) {
                continue;
            }
            try {
                Method valueMethod = annotation.annotationType().getMethod("value");
                Object rawValue = valueMethod.invoke(annotation);
                if (!(rawValue instanceof Class[])) {
                    continue;
                }
                Class<?>[] interceptorClasses = (Class<?>[]) rawValue;
                for (Class<?> declaredInterceptorClass : interceptorClasses) {
                    if (interceptorClass.equals(declaredInterceptorClass)) {
                        return true;
                    }
                }
            } catch (Exception ignored) {
                // Best-effort lookup only; validation handles malformed annotations elsewhere.
            }
        }
        return false;
    }

    /**
     * CDI 4.1 §2.8.1.5:
     * If a bean has multiple stereotypes (directly/indirectly/transitively) that declare
     * different priority values, the bean must explicitly declare @Priority.
     */
    private void validateStereotypePriorityDeclaration(Class<?> clazz) {
        stereotypePriorityValidator.validateStereotypePriorityDeclaration(
                clazz,
                extractDeclaredPriorityFromClass(clazz),
                annotationsOf(clazz)
        );
    }

    /**
     * CDI 4.1 §2.8.1.6:
     * Stereotypes declared @Target(TYPE) may not be applied to stereotypes that can target
     * METHOD and/or FIELD.
     */
    @SuppressWarnings("unchecked")
    private void validateStereotypeTargetCompatibility(Class<?> clazz) {
        stereotypePriorityValidator.validateStereotypeTargetCompatibility(clazz);
    }

    @SuppressWarnings("unchecked")
    private void validateStereotypeNonPortableDeclarations(Class<?> clazz) {
        stereotypePriorityValidator.validateStereotypeNonPortableDeclarations(clazz);
    }

    public Integer extractEffectivePriority(Class<?> clazz) {
        Integer explicitPriority = extractDeclaredPriorityFromClass(clazz);
        if (explicitPriority != null) {
            return explicitPriority;
        }

        Set<Integer> stereotypePriorities = stereotypePriorityValidator.collectStereotypePriorityValues(annotationsOf(clazz));
        if (stereotypePriorities.size() == 1) {
            return stereotypePriorities.iterator().next();
        }

        return extractPrioritizedInterfacePriority(clazz);
    }

    /**
     * Supports CDI custom component style priority declaration via {@link Prioritized}.
     * When present, this priority enables alternatives for the whole application.
     */
    private Integer extractPrioritizedInterfacePriority(Class<?> clazz) {
        if (clazz == null || !Prioritized.class.isAssignableFrom(clazz)) {
            return null;
        }

        if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
            return null;
        }

        try {
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            if (!Modifier.isPublic(constructor.getModifiers()) || !Modifier.isPublic(clazz.getModifiers())) {
                constructor.setAccessible(true);
            }
            Prioritized prioritized = (Prioritized) constructor.newInstance();
            return prioritized.getPriority();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private Integer extractDeclaredPriorityFromClass(Class<?> clazz) {
        return extractDeclaredPriority(annotationsOf(clazz));
    }

    private Integer extractDeclaredPriority(Annotation[] annotations) {
        if (annotations == null) {
            return null;
        }

        for (Annotation annotation : annotations) {
            if (PRIORITY.matches(annotation.annotationType())) {
                return readPriorityValue(annotation);
            }
        }

        return null;
    }

    private Integer readPriorityValue(Annotation priorityAnnotation) {
        try {
            Method valueMethod = priorityAnnotation.annotationType().getMethod("value");
            Object value = valueMethod.invoke(priorityAnnotation);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } catch (ReflectiveOperationException ignored) {
            // Ignore malformed annotation implementations and treat as absent.
        }
        return null;
    }

    public Annotation[] annotationsOf(Class<?> clazz) {
        if (overrideAnnotations != null && overrideAnnotationsClass != null &&
                overrideAnnotationsClass.equals(clazz)) {
            return overrideAnnotations;
        }
        return clazz.getAnnotations();
    }

    public Annotation[] declaredAnnotationsOf(Class<?> clazz) {
        if (overrideAnnotations != null && overrideAnnotationsClass != null &&
                overrideAnnotationsClass.equals(clazz)) {
            // ProcessAnnotatedType#setAnnotatedType replaces metadata; declared annotation
            // checks must use the replacement exactly, not merged reflection annotations.
            return overrideAnnotations;
        }
        return clazz.getDeclaredAnnotations();
    }

    public Annotation[] annotationsOf(AnnotatedElement element) {
        if (element == null) {
            return new Annotation[0];
        }
        if (element instanceof Class<?>) {
            return annotationsOf((Class<?>) element);
        }
        return AnnotationsHelper.annotationsOf(currentAnnotatedTypeOverride, element);
    }

    public Type baseTypeOf(Field field) {
        return AnnotationsHelper.baseTypeOf(currentAnnotatedTypeOverride, field);
    }

    public Type baseTypeOf(Method method) {
        return AnnotationsHelper.baseTypeOf(currentAnnotatedTypeOverride, method);
    }

    public Type baseTypeOf(Parameter parameter) {
        return AnnotationsHelper.baseTypeOf(currentAnnotatedTypeOverride, parameter);
    }

    public Set<Type> typeClosureOf(Method method) {
        return AnnotationsHelper.typeClosureOf(currentAnnotatedTypeOverride, method);
    }

    public Set<Type> typeClosureOf(Field field) {
        return AnnotationsHelper.typeClosureOf(currentAnnotatedTypeOverride, field);
    }

    public Annotated annotatedOf(AnnotatedElement element) {
        return AnnotationsHelper.annotatedOf(currentAnnotatedTypeOverride, element);
    }

    public boolean hasInjectAnnotation(AnnotatedElement element) {
        return hasAnnotation(element, INJECT);
    }

    public boolean hasProducesAnnotation(AnnotatedElement element) {
        return hasAnnotation(element, PRODUCES);
    }

    public boolean hasDisposesAnnotation(AnnotatedElement element) {
        return hasAnnotation(element, DISPOSES);
    }

    public boolean hasObservesAnnotation(AnnotatedElement element) {
        return hasAnnotation(element, OBSERVES);
    }

    public boolean hasObservesAsyncAnnotation(AnnotatedElement element) {
        return hasAnnotation(element, OBSERVES_ASYNC);
    }

    public boolean hasPostConstructAnnotation(AnnotatedElement element) {
        return hasAnnotation(element, POST_CONSTRUCT);
    }

    public boolean hasPreDestroyAnnotation(AnnotatedElement element) {
        return hasAnnotation(element, PRE_DESTROY);
    }

    public boolean hasAroundInvokeAnnotation(AnnotatedElement element) {
        return hasAnnotation(element, AROUND_INVOKE);
    }

    public boolean hasAroundConstructAnnotation(AnnotatedElement element) {
        return hasAnnotation(element, AROUND_CONSTRUCT);
    }

    public boolean hasAlternativeAnnotation(AnnotatedElement element) {
        return AnnotationsHelper.hasAlternativeAnnotation(element);
    }

    public boolean hasDecoratorAnnotation(Class<?> clazz) {
        return AnnotationsHelper.hasDecoratorAnnotation(clazz);
    }

    public boolean hasSpecializesAnnotation(AnnotatedElement element) {
        return AnnotationsHelper.hasSpecializesAnnotation(element);
    }

    public Integer getPriorityValue(AnnotatedElement element) {
        return AnnotationsHelper.getPriorityValue(element);
    }

    private boolean hasAnnotation(AnnotatedElement element, AnnotationsEnum annotation) {
        for (Annotation candidate : annotationsOf(element)) {
            if (annotation.matches(candidate.annotationType())) {
                return true;
            }
        }
        return false;
    }

    public String extractBeanName(Class<?> clazz) {
        return beanAttributesExtractor.extractBeanName(clazz);
    }

    public Set<Annotation> extractBeanQualifiers(Class<?> clazz) {
        return beanAttributesExtractor.extractBeanQualifiers(clazz);
    }

    public Class<? extends Annotation> extractBeanScope(Class<?> clazz) {
        return beanAttributesExtractor.extractBeanScope(clazz);
    }

    public Set<Class<? extends Annotation>> extractBeanStereotypes(Class<?> clazz) {
        return beanAttributesExtractor.extractBeanStereotypes(clazz);
    }

    public Class<? extends Annotation> extractScopeFromStereotype(Class<? extends Annotation> stereotypeClass) {
        return beanAttributesExtractor.extractScopeFromStereotype(stereotypeClass);
    }

    private String readNamedValue(Annotation namedAnnotation) {
        try {
            Method value = namedAnnotation.annotationType().getMethod("value");
            Object raw = value.invoke(namedAnnotation);
            return raw == null ? "" : raw.toString();
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }


    // -----------------------
    // Validation: constructors
    // -----------------------

    private Constructor<?> findBeanConstructor(Class<?> clazz) {
        return injectionMetadataValidator.findBeanConstructor(clazz);
    }

    private boolean validateIllegalConstructorParameterAnnotations(Class<?> clazz) {
        return injectionMetadataValidator.validateIllegalConstructorParameterAnnotations(clazz);
    }

    // -----------------------
    // Validation: @Inject
    // -----------------------

    private boolean validateInjectField(Field field) {
        return injectionMetadataValidator.validateInjectField(field);
    }

    private boolean validateInitializerMethod(Method method) {
        return injectionMetadataValidator.validateInitializerMethod(method);
    }

    private boolean hasNotValidInjectionParameters(Parameter[] parameters, String owner) {
        return injectionMetadataValidator.hasNotValidInjectionParameters(parameters, owner);
    }

    // -----------------------
    // Validation: producers
    // -----------------------

    private boolean validateProducerField(Field field) {
        return producerDisposerValidator.validateProducerField(field);
    }

    private boolean validateProducerMethod(Method method) {
        return producerDisposerValidator.validateProducerMethod(method);
    }

    public boolean isNotValidInterceptionFactoryInjectionPointUsage(AnnotatedElement element,
                                                                     boolean producerMethodParameter) {
        return injectionMetadataValidator.isNotValidInterceptionFactoryInjectionPointUsage(
                element, producerMethodParameter);
    }

    /**
     * CDI 4.1 §15.2: a producer method annotated @Specializes must be non-static and directly override
     * another producer method.
     */
    private boolean validateSpecializingProducerMethodConstraint(Method method) {
        return producerDisposerValidator.validateSpecializingProducerMethodConstraint(method);
    }

    public Method resolveDirectlyOverriddenProducerMethod(Method method) {
        return producerDisposerValidator.resolveDirectlyOverriddenProducerMethod(method);
    }

    public boolean isSpecializingProducerMethodEnabled(Method method) {
        return producerDisposerValidator.isSpecializingProducerMethodEnabled(method);
    }

    public String producerMethodSpecializationSignature(Method method) {
        return producerDisposerValidator.producerMethodSpecializationSignature(method);
    }

    /**
     * Validates a disposer method according to CDI 4.1 Section 3.4.
     * <p>
     * CDI 4.1 Disposer Method Rules:
     * <ul>
     *   <li>Must have exactly one parameter annotated with @Disposes</li>
     *   <li>Must not be annotated with @Produces or @Inject</li>
     *   <li>Must not be abstract</li>
     *   <li>Must not declare type parameters (not a generic method)</li>
     *   <li>The @Disposes parameter type must match a producer method's return type</li>
     *   <li>Other parameters are treated as injection points</li>
     *   <li>@Disposes parameter qualifiers must match corresponding producer qualifiers</li>
     * </ul>
     *
     * @param method the method to validate
     * @return true if valid, false otherwise
     */
    private boolean validateDisposerMethod(Method method) {
        return producerDisposerValidator.validateDisposerMethod(method);
    }

    public boolean isNotValidInjectionPointMetadataUsage(AnnotatedElement injectionPoint, boolean disposerParameter) {
        return injectionMetadataValidator.isNotValidInjectionPointMetadataUsage(injectionPoint, disposerParameter);
    }

    public Type findDecoratorDelegateType(Class<?> decoratorClass) {
        if (decoratorClass == null) {
            return null;
        }

        for (Field field : decoratorClass.getDeclaredFields()) {
            if (hasDelegateAnnotation(field)) {
                return baseTypeOf(field);
            }
        }

        for (Constructor<?> constructor : decoratorClass.getDeclaredConstructors()) {
            for (Parameter parameter : constructor.getParameters()) {
                if (hasDelegateAnnotation(parameter)) {
                    return baseTypeOf(parameter);
                }
            }
        }

        for (Method method : decoratorClass.getDeclaredMethods()) {
            if (!hasInjectAnnotation(method)) {
                continue;
            }
            for (Parameter parameter : method.getParameters()) {
                if (hasDelegateAnnotation(parameter)) {
                    return baseTypeOf(parameter);
                }
            }
        }
        return null;
    }

    public boolean isInterceptorClass(Class<?> clazz) {
        for (Annotation annotation : annotationsOf(clazz)) {
            if (hasInterceptorAnnotation(annotation.annotationType())) {
                return true;
            }
        }
        return false;
    }

    public boolean hasQualifier(Annotation[] annotations, AnnotationsEnum qualifierType) {
        if (qualifierType == null) {
            return false;
        }
        Set<Annotation> qualifiers = AnnotationsHelper.extractQualifierAnnotations(annotations);
        for (Annotation qualifier : qualifiers) {
            if (qualifierType.matches(qualifier.annotationType())) {
                return true;
            }
        }
        return false;
    }

    public boolean declaresNonDependentScope(Class<?> clazz) {
        for (Annotation annotation : annotationsOf(clazz)) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (hasDependentAnnotation(annotationType)) {
                continue;
            }
            if (isScopeAnnotationType(annotationType)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasDefaultQualifier(Annotation[] annotations) {
        Set<Annotation> qualifiers = AnnotationsHelper.extractQualifierAnnotations(annotations);
        if (qualifiers.isEmpty()) {
            return true;
        }
        for (Annotation qualifier : qualifiers) {
            if (hasDefaultAnnotation(qualifier.annotationType())) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAnyProducer(Class<?> clazz) {
        for (Field f : clazz.getDeclaredFields()) {
            if (hasProducesAnnotation(f)) return true;
        }
        for (Method m : clazz.getDeclaredMethods()) {
            if (hasProducesAnnotation(m)) return true;
        }
        return false;
    }

    public boolean hasAnyDisposer(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (hasDisposesParameter(method)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasOnlyStaticProducersAndDisposers(Class<?> clazz) {
        boolean foundProducerOrDisposer = false;

        for (Field field : clazz.getDeclaredFields()) {
            if (!hasProducesAnnotation(field)) {
                continue;
            }
            foundProducerOrDisposer = true;
            if (!Modifier.isStatic(field.getModifiers())) {
                return false;
            }
        }

        for (Method method : clazz.getDeclaredMethods()) {
            if (!hasProducesAnnotation(method) && !hasDisposesParameter(method)) {
                continue;
            }
            foundProducerOrDisposer = true;
            if (!Modifier.isStatic(method.getModifiers())) {
                return false;
            }
        }

        return foundProducerOrDisposer;
    }

    /**
     * Returns true when a class is declared as an alternative directly or via stereotype.
     */
    public boolean isAlternativeDeclared(Class<?> clazz) {
        if (hasAlternativeAnnotation(clazz)) {
            return true;
        }

        Set<Class<? extends Annotation>> visited = new HashSet<>();
        for (Annotation annotation : annotationsOf(clazz)) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (isStereotypeAnnotationType(annotationType) &&
                stereotypeDeclaresAlternative(annotationType, visited)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns true when an annotated element (for example, a producer method/field)
     * declares an alternative directly or via an applied stereotype.
     */
    public boolean isAlternativeDeclared(AnnotatedElement element) {
        if (element == null) {
            return false;
        }

        if (hasAlternativeAnnotation(element)) {
            return true;
        }

        Set<Class<? extends Annotation>> visited = new HashSet<>();
        for (Annotation annotation : annotationsOf(element)) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (isStereotypeAnnotationType(annotationType) &&
                stereotypeDeclaresAlternative(annotationType, visited)) {
                return true;
            }
        }

        return false;
    }

    private boolean stereotypeDeclaresAlternative(Class<? extends Annotation> stereotypeType,
                                                  Set<Class<? extends Annotation>> visited) {
        if (!visited.add(stereotypeType)) {
            return false;
        }

        if (hasAlternativeAnnotation(stereotypeType)) {
            return true;
        }

        for (Annotation meta : stereotypeType.getAnnotations()) {
            Class<? extends Annotation> metaType = meta.annotationType();
            if (isStereotypeAnnotationType(metaType) &&
                stereotypeDeclaresAlternative(metaType, visited)) {
                return true;
            }
        }

        return false;
    }

    /**
     * CDI 4.1 alternative enabling helper usable for bean classes and producer members.
     *
     * <p>Alternatives can be enabled by:
     * <ul>
     *   <li>{@code @Priority}</li>
     *   <li>beans.xml {@code <alternatives>}</li>
     *   <li>programmatic enablement via {@link Syringe#enableAlternative(Class)}</li>
     * </ul>
     */
    public boolean isAlternativeEnabled(AnnotatedElement element,
                                         Class<?> declaringClass,
                                         boolean alternativeDeclared) {
        if (!alternativeDeclared) {
            return false;
        }

        // Producer member-level @Priority enables member alternatives.
        Integer priority = getPriorityValue(element);
        if (priority != null) {
            return true;
        }

        // Class-level checks (bean class itself or producer declaring class).
        if (isAlternativeEnabledForClass(declaringClass)) {
            return true;
        }

        if (isAlternativeEnabledByElementStereotype(element)) {
            return true;
        }

        // Fallback for class elements when declaringClass is null.
        if (element instanceof Class) {
            return isAlternativeEnabledForClass((Class<?>) element);
        }

        return false;
    }

    private boolean isAlternativeEnabledByElementStereotype(AnnotatedElement element) {
        if (element == null) {
            return false;
        }
        for (Annotation annotation : annotationsOf(element)) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (!isStereotypeAnnotationType(annotationType)) {
                continue;
            }
            if (isAlternativeEnabledForClass(annotationType)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAlternativeEnabledForClass(Class<?> clazz) {
        if (clazz == null) {
            return false;
        }

        if (knowledgeBase.hasAfterTypeDiscoveryAlternativesCustomized()) {
            if (knowledgeBase.getApplicationAlternativeOrder(clazz) >= 0) {
                return true;
            }
            if (extractEffectivePriority(clazz) != null) {
                return false;
            }
        }

        if (extractEffectivePriority(clazz) != null) {
            return true;
        }

        if (knowledgeBase.isAlternativeEnabledProgrammatically(clazz.getName())) {
            return true;
        }

        if (knowledgeBase.isAlternativeEnabledInBeansXml(clazz.getName())) {
            return true;
        }

        for (Annotation annotation : annotationsOf(clazz)) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (isStereotypeAnnotationType(annotationType)) {
                if (knowledgeBase.isAlternativeEnabledProgrammatically(annotationType.getName()) ||
                    knowledgeBase.isAlternativeEnabledInBeansXml(annotationType.getName())) {
                    return true;
                }
            }
        }

        return false;
    }

    // -----------------------
    // Type / annotation checks
    // -----------------------

    /**
     * Validates that the class declares at most one scope annotation and returns it.
     *
     * @return the discovered scope annotation type, or null if none is present.
     * @throws DefinitionException if more than one scope annotation is present.
     */
    private Class<? extends Annotation> validateScopeAnnotations(Class<?> clazz) {
        List<Class<? extends Annotation>> scopes = Arrays.stream(declaredAnnotationsOf(clazz))
                .map(Annotation::annotationType)
                .filter(this::isScopeAnnotationType)
                .collect(Collectors.toList());

        if (scopes.size() > 1) {
            String scopeNames = scopes.stream().map(s -> "@" + s.getSimpleName()).collect(Collectors.joining(", "));
            throw new DefinitionException("declares multiple scope annotations: " + scopeNames);
        }

        return scopes.isEmpty() ? null : scopes.get(0);
    }

    public Class<? extends Annotation> extractBeanScope(Class<?> clazz, Class<? extends Annotation> discoveredScope) {
        if (discoveredScope != null) {
            return normalizeSingletonToApplicationScoped(discoveredScope);
        }

        // No explicit scope: resolve default scope from stereotypes (if any), otherwise @Dependent.
        return extractBeanScope(clazz);
    }

    /**
     * CDI 4.1 §3.1: A managed bean with a non-static public field must declare a pseudo-scope.
     * A normal scope on such a bean is a definition error.
     */
    public void validateManagedBeanPublicFieldScopeConstraint(Class<?> clazz,
                                                               Class<? extends Annotation> scopeAnnotation) {
        // Applies to managed beans; types without a bean constructor are not managed beans.
        if (isNotManagedBeanConstructorCandidate(clazz)) {
            return;
        }

        if (declaresSingletonPseudoScope(clazz)) {
            return;
        }

        if (!isNormalScope(scopeAnnotation)) {
            return;
        }

        List<String> invalidFields = Arrays.stream(clazz.getFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .filter(field -> !field.isSynthetic())
                .map(field -> field.getDeclaringClass().getName() + "#" + field.getName())
                .collect(Collectors.toList());

        if (!invalidFields.isEmpty()) {
            throw new DefinitionException(clazz.getName() +
                    ": declares normal scope @" + scopeAnnotation.getSimpleName() +
                    " and non-static public field(s) " + String.join(", ", invalidFields) +
                    ". Such beans must declare a pseudo-scope (e.g. @Dependent or @Singleton).");
        }
    }

    private boolean declaresSingletonPseudoScope(Class<?> clazz) {
        if (hasSingletonAnnotation(clazz)) {
            return true;
        }
        for (Annotation annotation : annotationsOf(clazz)) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (isStereotypeAnnotationType(annotationType) &&
                    stereotypeDeclaresSingleton(annotationType, new HashSet<>())) {
                return true;
            }
        }
        return false;
    }

    private boolean stereotypeDeclaresSingleton(Class<? extends Annotation> stereotypeType,
                                                Set<Class<? extends Annotation>> visited) {
        if (stereotypeType == null || !visited.add(stereotypeType)) {
            return false;
        }
        for (Annotation meta : stereotypeType.getAnnotations()) {
            Class<? extends Annotation> metaType = meta.annotationType();
            if (hasSingletonAnnotation(metaType)) {
                return true;
            }
            if (isStereotypeAnnotationType(metaType) &&
                    stereotypeDeclaresSingleton(metaType, visited)) {
                return true;
            }
        }
        return false;
    }

    /**
     * CDI 4.1 §3.1: a managed bean with a parameterized bean class must have @Dependent scope.
     */
    public void validateManagedBeanGenericTypeScopeConstraint(Class<?> clazz,
                                                               Class<? extends Annotation> scopeAnnotation) {
        // Applies to managed beans; types without a bean constructor are not managed beans.
        if (isNotManagedBeanConstructorCandidate(clazz)) {
            return;
        }

        if (clazz.getTypeParameters().length == 0) {
            return;
        }

        if (scopeAnnotation == null || hasDependentAnnotation(scopeAnnotation)) {
            return;
        }

        throw new DefinitionException(clazz.getName() +
                ": managed bean class is generic and declares scope @" +
                scopeAnnotation.getSimpleName() +
                ". Generic managed beans must have @Dependent scope.");
    }

    private boolean isNotManagedBeanConstructorCandidate(Class<?> clazz) {
        return !hasNoArgsConstructor(clazz) && hasNotInjectConstructor(clazz);
    }

    private boolean isNormalScope(Class<? extends Annotation> scopeAnnotation) {
        if (scopeAnnotation == null) {
            return false;
        }

        ScopeMetadata registeredScope = knowledgeBase.getScopeMetadata(scopeAnnotation);
        if (registeredScope != null) {
            return registeredScope.isNormal();
        }

        if (hasBuiltInNormalScopeAnnotation(scopeAnnotation)) {
            return true;
        }

        return hasNormalScopeAnnotation(scopeAnnotation);
    }

    public void validateProgrammaticPassivatingScopeConstraint(Class<?> clazz,
                                                                Class<? extends Annotation> scopeAnnotation) {
        if (clazz == null || scopeAnnotation == null) {
            return;
        }

        ScopeMetadata registeredScope = knowledgeBase.getScopeMetadata(scopeAnnotation);
        if (registeredScope == null || !registeredScope.isPassivating()) {
            return;
        }

        if (Serializable.class.isAssignableFrom(clazz)) {
            return;
        }

        knowledgeBase.addError(clazz.getName() +
                ": bean with passivating scope @" + scopeAnnotation.getSimpleName() +
                " must implement java.io.Serializable");
    }

    public void applySpecializationInheritance(BeanImpl<?> bean, Class<?> clazz, BeanArchiveMode beanArchiveMode) {
        if (!hasSpecializesAnnotation(clazz)) {
            return;
        }

        List<Class<?>> specializedAncestors = specializationAncestors(clazz, beanArchiveMode);
        if (specializedAncestors.isEmpty()) {
            return;
        }

        Set<Annotation> mergedQualifiers = new HashSet<>(bean.getQualifiers());
        String inheritedName = "";
        boolean inheritedDefaultQualifier = false;

        for (Class<?> ancestor : specializedAncestors) {
            Set<Annotation> ancestorQualifiers = extractBeanQualifiers(ancestor);
            mergedQualifiers.addAll(ancestorQualifiers);
            if (!inheritedDefaultQualifier) {
                for (Annotation qualifier : ancestorQualifiers) {
                    if (qualifier != null && hasDefaultAnnotation(qualifier.annotationType())) {
                        inheritedDefaultQualifier = true;
                        break;
                    }
                }
            }
            if (inheritedName.isEmpty()) {
                String ancestorName = extractBeanName(ancestor);
                if (ancestorName != null && !ancestorName.isEmpty()) {
                    inheritedName = ancestorName;
                }
            }

            BeanTypesExtractor.ExtractionResult ancestorTypes = beanTypesExtractor.extractManagedBeanTypes(ancestor);
            Set<Type> missingTypes = new HashSet<>(ancestorTypes.getTypes());
            missingTypes.removeAll(bean.getTypes());
            if (!missingTypes.isEmpty()) {
                knowledgeBase.addDefinitionError(clazz.getName() +
                        ": specializing bean does not have all bean types of specialized bean " +
                        ancestor.getName() + ". Missing: " + missingTypes);
            }
        }

        Set<Annotation> normalizedQualifiers = AnnotationsHelper.normalizeBeanQualifiers(mergedQualifiers);
        if (inheritedDefaultQualifier) {
            normalizedQualifiers.add(jakarta.enterprise.inject.Default.Literal.INSTANCE);
        }
        bean.setQualifiers(synchronizeNamedQualifier(normalizedQualifiers, bean.getName()));

        if (!inheritedName.isEmpty()) {
            if (declaresBeanNameExplicitly(clazz)) {
                knowledgeBase.addDefinitionError(clazz.getName() +
                        ": specializing bean may not explicitly declare @Named when specialized bean has name '" +
                        inheritedName + "'");
            } else {
                bean.setName(inheritedName);
            }
        }
        bean.setQualifiers(synchronizeNamedQualifier(bean.getQualifiers(), bean.getName()));
    }

    private List<Class<?>> specializationAncestors(Class<?> clazz, BeanArchiveMode beanArchiveMode) {
        List<Class<?>> ancestors = new ArrayList<>();
        Class<?> direct = clazz.getSuperclass();
        if (direct == null || Object.class.equals(direct)) {
            return ancestors;
        }

        BeanArchiveMode directMode = knowledgeBase.getBeanArchiveMode(direct);
        if (directMode == null) {
            directMode = beanArchiveMode;
        }
        if (!isCandidateBeanClass(direct, directMode)
                || Modifier.isAbstract(direct.getModifiers())
                || hasInterceptorAnnotation(direct)
                || hasDecoratorAnnotation(direct)
                || !hasValidBeanConstructorSignature(direct)) {
            return ancestors;
        }

        ancestors.add(direct);
        Class<?> current = direct;
        while (hasSpecializesAnnotation(current)) {
            Class<?> parent = current.getSuperclass();
            if (parent == null || Object.class.equals(parent)) {
                break;
            }
            BeanArchiveMode mode = knowledgeBase.getBeanArchiveMode(parent);
            if (mode == null) {
                mode = beanArchiveMode;
            }
            if (!isCandidateBeanClass(parent, mode)
                    || Modifier.isAbstract(parent.getModifiers())
                    || hasInterceptorAnnotation(parent)
                    || hasDecoratorAnnotation(parent)
                    || !hasValidBeanConstructorSignature(parent)) {
                break;
            }
            ancestors.add(parent);
            current = parent;
        }
        return ancestors;
    }

    private boolean declaresBeanNameExplicitly(Class<?> clazz) {
        for (Annotation annotation : declaredAnnotationsOf(clazz)) {
            if (hasNamedAnnotation(annotation.annotationType())) {
                return true;
            }
        }
        return false;
    }

    /**
     * CDI 4.1 §15.1: a managed bean annotated @Specializes must directly extend another managed bean.
     * If this is not true, the container must treat it as a definition error.
     */
    private void validateManagedBeanSpecializationConstraint(Class<?> clazz, BeanArchiveMode beanArchiveMode) {
        if (!hasSpecializesAnnotation(clazz)) {
            return;
        }
        if (!isSpecializingBeanEnabled(clazz)) {
            return;
        }

        Class<?> directSuperclass = clazz.getSuperclass();
        if (directSuperclass == null || Object.class.equals(directSuperclass)) {
            knowledgeBase.addDefinitionError(clazz.getName() +
                    ": declares @Specializes but does not directly extend another managed bean");
            return;
        }

        BeanArchiveMode superMode = knowledgeBase.getBeanArchiveMode(directSuperclass);
        if (superMode == null) {
            superMode = beanArchiveMode;
        }

        boolean managedBeanSuperclass = isCandidateBeanClass(directSuperclass, superMode)
                && !Modifier.isAbstract(directSuperclass.getModifiers())
                && !hasInterceptorAnnotation(directSuperclass)
                && !hasDecoratorAnnotation(directSuperclass)
                && hasValidBeanConstructorSignature(directSuperclass);

        if (!managedBeanSuperclass) {
            knowledgeBase.addDefinitionError(clazz.getName() +
                    ": declares @Specializes but direct superclass " + directSuperclass.getName() +
                    " is not a managed bean");
            return;
        }

        Class<?> previousSpecializingClass = specializingBeansBySuperclass.putIfAbsent(directSuperclass, clazz);
        if (previousSpecializingClass != null && !previousSpecializingClass.equals(clazz)) {
            knowledgeBase.addError(clazz.getName() +
                    ": inconsistent specialization. Both " + previousSpecializingClass.getName() +
                    " and " + clazz.getName() + " specialize " + directSuperclass.getName());
        }
    }

    private boolean isSpecializingBeanEnabled(Class<?> clazz) {
        boolean alternativeDeclared = isAlternativeDeclared(clazz);
        if (!alternativeDeclared) {
            return true;
        }
        return isAlternativeEnabled(clazz, clazz, true);
    }

    private boolean hasValidBeanConstructorSignature(Class<?> clazz) {
        List<Constructor<?>> injectConstructors = Arrays.stream(clazz.getDeclaredConstructors())
                .filter(AnnotationsHelper::hasInjectAnnotation)
                .collect(Collectors.toList());

        if (injectConstructors.size() > 1) {
            return false;
        }

        if (injectConstructors.size() == 1) {
            return !Modifier.isPrivate(injectConstructors.get(0).getModifiers());
        }

        return Arrays.stream(clazz.getDeclaredConstructors())
                .anyMatch(constructor ->
                        constructor.getParameterCount() == 0 && !Modifier.isPrivate(constructor.getModifiers()));
    }

    public boolean isScopeAnnotationType(Class<? extends Annotation> at) {
        // Scope meta-annotations themselves are not bean scope types.
        if (SCOPE.matches(at) || NORMAL_SCOPE.matches(at)) {
            return false;
        }
        // CDI scopes are meta-annotated with @Scope or @NormalScope
        return hasScopeAnnotation(at)
                || hasNormalScopeAnnotation(at)
                || knowledgeBase.isRegisteredScope(at)
                // plus common built-ins
                || DEPENDENT.matches(at)
                || APPLICATION_SCOPED.matches(at)
                || REQUEST_SCOPED.matches(at)
                || SESSION_SCOPED.matches(at)
                || CONVERSATION_SCOPED.matches(at);
    }

    public void validateQualifiers(Annotation[] annotations, String location) {
        // CDI allows multiple qualifiers, but they must actually be qualifiers (meta-annotated @Qualifier),
        // and you can't repeat the *same* qualifier type twice.
        List<Annotation> qualifiers = Arrays.stream(annotations)
                .filter(a -> isQualifierAnnotationType(a.annotationType()))
                .collect(Collectors.toList());

        Map<Class<? extends Annotation>, Long> counts = qualifiers.stream()
                .collect(Collectors.groupingBy(Annotation::annotationType, Collectors.counting()));

        List<String> duplicates = counts.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(e -> "@" + e.getKey().getSimpleName())
                .collect(Collectors.toList());

        if (!duplicates.isEmpty()) {
            throw new DefinitionException(location + ": duplicate qualifier annotations: " + String.join(", ", duplicates));
        }
    }

    /**
     * CDI 4.1 §3.9:
     * - If an injected field declares @Named with no value, the field name is assumed.
     * - Any other injection point declaring @Named with no value is a definition error.
     */
    public boolean isNotValidNamedInjectionPointUsage(AnnotatedElement injectionPoint) {
        return injectionMetadataValidator.isNotValidNamedInjectionPointUsage(injectionPoint);
    }

    private String describeInjectionPoint(AnnotatedElement injectionPoint) {
        if (injectionPoint instanceof Parameter) {
            return fmtParameter((Parameter) injectionPoint);
        }
        if (injectionPoint instanceof Field) {
            return fmtField((Field) injectionPoint);
        }
        if (injectionPoint instanceof Method) {
            return fmtMethod((Method) injectionPoint);
        }
        if (injectionPoint instanceof Constructor) {
            return fmtConstructor((Constructor<?>) injectionPoint);
        }
        return injectionPoint.toString();
    }

    public boolean isQualifierAnnotationType(Class<? extends Annotation> at) {
        return hasQualifierAnnotation(at) || knowledgeBase.isRegisteredQualifier(at);
    }

    /**
     * Validates that a producer type is legal, according to CDI 4.1 Section 3.3.2.
     * <p>
     * Rules:
     * <ul>
     *   <li>The type itself cannot be a wildcard or type variable</li>
     *   <li>Array producer types cannot have a type-variable component type</li>
     *   <li>Producer types cannot contain wildcard type parameters</li>
     * </ul>
     *
     * @param type the producer return/field type to validate
     * @throws DefinitionException if the type is invalid
     */
    public void checkProducerTypeValidity(Type type) {
        checkProducerTypeValidity(type, true);
    }

    public void checkProducerTypeValidity(Type type, boolean topLevel) {
        if (type instanceof WildcardType) {
            throw new DefinitionException("type may not contain a wildcard (" + type.getTypeName() + ")");
        }
        if (type instanceof TypeVariable) {
            if (topLevel) {
                throw new DefinitionException("type may not be a type variable (" + type.getTypeName() + ")");
            }
            return;
        }
        if (type instanceof GenericArrayType) {
            Type componentType = ((GenericArrayType) type).getGenericComponentType();
            if (componentType instanceof TypeVariable) {
                throw new DefinitionException("array component type may not be a type variable (" +
                        componentType.getTypeName() + ")");
            }
            checkProducerTypeValidity(componentType, false);
            return;
        }
        if (type instanceof Class) {
            Class<?> clazz = (Class<?>) type;
            if (clazz.isArray()) {
                checkProducerTypeValidity(clazz.getComponentType(), false);
            }
            return;
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;

            Type rawType = parameterizedType.getRawType();
            if (rawType instanceof Class) {
                Class<?> rawClass = (Class<?>) rawType;
                if (rawClass.getTypeParameters().length != parameterizedType.getActualTypeArguments().length) {
                    throw new DefinitionException("parameterized type must specify all type parameters (" +
                            type.getTypeName() + ")");
                }
            }

            // CDI 4.1: each type parameter of a parameterized producer return type must be specified
            // either as an actual type argument or as a type variable. Raw generic arguments are invalid.
            for (Type typeArgument : parameterizedType.getActualTypeArguments()) {
                if (typeArgument instanceof Class &&
                        ((Class<?>) typeArgument).getTypeParameters().length > 0) {
                    throw new DefinitionException("parameterized producer type contains raw generic argument (" +
                            typeArgument.getTypeName() + ")");
                }

                // Nested parameterized arguments are validated recursively.
                checkProducerTypeValidity(typeArgument, false);
            }
        }
    }

    /**
     * CDI 4.1 §3.2: a producer method whose return type is a parameterized type that contains
     * a type variable must declare @Dependent scope.
     */
    public void validateProducerMethodTypeVariableScopeConstraint(Method method) {
        Type returnType = baseTypeOf(method);
        if (!(returnType instanceof ParameterizedType)) {
            return;
        }

        if (!containsTypeVariable(((ParameterizedType) returnType).getActualTypeArguments())) {
            return;
        }

        Class<? extends Annotation> scope = extractScope(method);
        if (scope == null || hasDependentAnnotation(scope)) {
            return;
        }

        throw new DefinitionException("producer method with parameterized return type containing a type variable " +
                "must declare @Dependent scope, but declares @" + scope.getSimpleName());
    }

    /**
     * CDI 4.1 §3.3: a producer field whose type is a parameterized type that contains
     * a type variable must declare @Dependent scope.
     */
    public void validateProducerFieldTypeVariableScopeConstraint(Field field) {
        Type fieldType = baseTypeOf(field);
        if (!(fieldType instanceof ParameterizedType)) {
            return;
        }

        if (!containsTypeVariable(((ParameterizedType) fieldType).getActualTypeArguments())) {
            return;
        }

        Class<? extends Annotation> scope = extractScope(field);
        if (scope == null || hasDependentAnnotation(scope)) {
            return;
        }

        throw new DefinitionException("producer field with parameterized type containing a type variable " +
                "must declare @Dependent scope, but declares @" + scope.getSimpleName());
    }

    public void checkInjectionTypeValidity(Type type) {
        checkInjectionTypeValidity(type, false, null);
    }

    public void checkInjectionTypeValidity(Type type,
                                            boolean allowTypeVariableArguments,
                                            Class<?> declaringBeanClass) {
        if (type instanceof Class && jakarta.enterprise.inject.Instance.class.equals(type)) {
            throw new DefinitionException("injection point of raw type Instance is not allowed");
        }
        if (type instanceof Class && jakarta.enterprise.event.Event.class.equals(type)) {
            throw new DefinitionException("injection point of raw type Event is not allowed");
        }

        if (type instanceof TypeVariable) {
            throw new DefinitionException("injection point may not be a type variable (" + type.getTypeName() + ")");
        }

        Class<?> raw = getRawType(type);

        if (raw.isEnum()) {
            throw new IllegalArgumentException("cannot inject an enum");
        }
        if (raw.isSynthetic()) {
            throw new IllegalArgumentException("cannot inject a synthetic class");
        }
        if (raw.isLocalClass()) {
            throw new IllegalArgumentException("cannot inject a local class");
        }
        if (raw.isAnonymousClass()) {
            throw new IllegalArgumentException("cannot inject an anonymous class");
        }
        if (raw.isMemberClass() && !Modifier.isStatic(raw.getModifiers())) {
            throw new IllegalArgumentException("cannot inject a non-static inner class");
        }

        if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            for (Type arg : pt.getActualTypeArguments()) {
                if (!allowTypeVariableArguments && arg instanceof TypeVariable) {
                    if (declaringBeanClass != null &&
                            isTypeVariableDeclaredByClassOrEnclosing(declaringBeanClass, (TypeVariable<?>) arg)) {
                        continue;
                    }
                    throw new DefinitionException("injection point may not contain type variable arguments (" + arg.getTypeName() + ")");
                }
            }
        }
    }

    public boolean allowsBeanClassTypeVariableArguments(Type injectionType, Class<?> declaringBeanClass) {
        if (!(injectionType instanceof ParameterizedType) || declaringBeanClass == null) {
            return false;
        }
        for (Type arg : ((ParameterizedType) injectionType).getActualTypeArguments()) {
            if (arg instanceof TypeVariable &&
                    isTypeVariableDeclaredByClassOrEnclosing(declaringBeanClass, (TypeVariable<?>) arg)) {
                return true;
            }
        }
        return false;
    }

    private boolean isTypeVariableDeclaredByClassOrEnclosing(Class<?> declaringBeanClass, TypeVariable<?> typeVariable) {
        GenericDeclaration genericDeclaration = typeVariable.getGenericDeclaration();
        if (!(genericDeclaration instanceof Class)) {
            return false;
        }
        Class<?> current = declaringBeanClass;
        while (current != null) {
            if (current.equals(genericDeclaration)) {
                return true;
            }
            current = current.getDeclaringClass();
        }
        return false;
    }

    // -----------------------
    // "Is it a bean?" helpers
    // -----------------------

    private boolean isCandidateBeanClass(Class<?> clazz, BeanArchiveMode beanArchiveMode) {
        return beanClassEligibilityValidator.isCandidateBeanClass(clazz, beanArchiveMode);
    }

    public boolean hasBeanDefiningAnnotation(Class<?> clazz) {
        return hasBeanDefiningAnnotationFrom(annotationsOf(clazz), declaredAnnotationsOf(clazz));
    }

    public boolean hasBeanDefiningAnnotationFromReflection(Class<?> clazz) {
        return hasBeanDefiningAnnotationFrom(clazz.getAnnotations(), clazz.getDeclaredAnnotations());
    }

    private boolean hasBeanDefiningAnnotationFrom(Annotation[] effectiveAnnotations, Annotation[] declaredAnnotations) {
        Set<Class<? extends Annotation>> declaredTypes = new HashSet<>();
        if (declaredAnnotations != null) {
            for (Annotation declared : declaredAnnotations) {
                if (declared != null) {
                    declaredTypes.add(declared.annotationType());
                }
            }
        }

        // Bean-defining annotations can be inherited via Java @Inherited semantics.
        // Use the effective annotation view so inherited stereotype/scope metadata
        // participates in candidate discovery.
        if (effectiveAnnotations == null) {
            return false;
        }
        for (Annotation annotation : effectiveAnnotations) {
            if (annotation == null) {
                continue;
            }
            Class<? extends Annotation> annotationType = annotation.annotationType();

            // Inherited @Dependent must not, by itself, turn a subtype into a discovered bean.
            if (DEPENDENT.matches(annotationType) && !declaredTypes.contains(annotationType)) {
                continue;
            }

            if (isBeanDefiningAnnotationType(annotationType)) {
                return true;
            }
        }
        return false;
    }

    public boolean isCurrentValidatedTypeOverridden(Class<?> clazz) {
        return currentAnnotatedTypeOverride != null
                && overrideAnnotationsClass != null
                && overrideAnnotationsClass.equals(clazz);
    }

    private boolean isBeanDefiningAnnotationType(Class<? extends Annotation> annotationType) {
        // Explicitly listed built-ins
        if (DEPENDENT.matches(annotationType)
                || APPLICATION_SCOPED.matches(annotationType)
                || REQUEST_SCOPED.matches(annotationType)) {
            return true;
        }

        // "all other normal scope types"
        // Pseudo-scopes (except @Dependent) are NOT bean-defining annotations.
        if (hasNormalScopeAnnotation(annotationType)) {
            return true;
        }

        // @Interceptor
        if (INTERCEPTOR.matches(annotationType)) {
            return true;
        }

        // "all stereotype annotations"
        return isStereotypeAnnotationType(annotationType);
    }

    /**
     * Checks if the class has a no-args constructor (public, protected, package-private, or private).
     *
     * @param clazz the class to check
     * @return true if the class has a no-args constructor
     */
    public boolean hasNoArgsConstructor(Class<?> clazz) {
        try {
            // Try to get any no-args constructor (public, protected, package, or private)
            Constructor<?>[] constructors = clazz.getDeclaredConstructors();
            for (Constructor<?> constructor : constructors) {
                if (constructor.getParameterCount() == 0) {
                    return true;
                }
            }
            // If no explicit constructors are defined, Java provides a default no-args constructor
            return constructors.length == 0;
        } catch (SecurityException e) {
            // If we can't access constructors due to security restrictions, assume no no-args constructor
            return false;
        }
    }

    public boolean hasNotInjectConstructor(Class<?> clazz) {
        for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            if (hasInjectAnnotation(constructor)) {
                return false;
            }
        }
        return true;
    }

    public boolean hasResolvableInjectConstructor(Class<?> clazz) {
        for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            if (!hasInjectAnnotation(constructor)) {
                continue;
            }

            boolean resolvable = true;
            for (Parameter parameter : constructor.getParameters()) {
                Class<?> rawType = getRawType(baseTypeOf(parameter));
                if (rawType == null) {
                    resolvable = false;
                    break;
                }
                if (InjectionPoint.class.equals(rawType) || BeanManager.class.equals(rawType)) {
                    continue;
                }
                if (knowledgeBase.getClasses().contains(rawType)) {
                    continue;
                }
                resolvable = false;
                break;
            }

            if (resolvable) {
                return true;
            }
        }
        return false;
    }

    // -----------------------
    // Formatting
    // -----------------------

    public String fmtField(Field field) {
        return "Field " + field.getName() + " of class " + field.getDeclaringClass().getName();
    }

    public String fmtMethod(Method method) {
        return "Method " + method.getName() + " of class " + method.getDeclaringClass().getName();
    }

    public String fmtConstructor(Constructor<?> c) {
        return "Constructor of class " + c.getDeclaringClass().getName();
    }

    public String fmtParameter(Parameter parameter) {
        Executable ex = parameter.getDeclaringExecutable();
        return "Parameter " + safeParamName(parameter) + " of " + ex.getName() + " of class " + ex.getDeclaringClass().getName();
    }

    public String safeParamName(Parameter p) {
        // Parameter names may be synthetic unless compiled with -parameters
        return (p.isNamePresent() ? p.getName() : "<param>");
    }

    /**
     * Adds a validation error for a class to the knowledge base.
     */
    public void addValidationError(Class<?> clazz, String message) {
        knowledgeBase.addDefinitionError(clazz.getName() + ": " + message);
    }

    // -----------------------
    // Annotation utilities
    // -----------------------

    public boolean hasAnyParameterWithDisposesAnnotation(Method method) {
        for (Parameter p : method.getParameters()) {
            if (hasDisposesAnnotation(p)) return true;
        }
        return false;
    }

    /**
     * Validates that a decorator has exactly one @Delegate injection point.
     *
     * <p><b>CDI 4.1 Decorator Rules (Section 8.3):</b>
     * <ul>
     *   <li>A decorator must have exactly one @Delegate injection point</li>
     *   <li>The @Delegate injection point must be an @Inject field, initializer method parameter, or constructor parameter</li>
     *   <li>The @Delegate injection point defines which types the decorator can decorate</li>
     * </ul>
     *
     * <p><b>Example valid decorator:</b>
     * <pre>{@code
     * @Decorator
     * public class LoggingDecorator implements MyService {
     *     @Inject @Delegate
     *     private MyService delegate; // Exactly one @Delegate injection point
     *
     *     public void doWork() {
     *         log("Before");
     *         delegate.doWork();
     *         log("After");
     *     }
     * }
     * }</pre>
     *
     * @param clazz the decorator class to validate
     */
    private void validateDecoratorDelegateInjectionPoints(Class<?> clazz) {
        interceptorDecoratorDefinitionValidator.validateDecoratorDelegateInjectionPoints(clazz);
    }

    private void validateDecoratorDoesNotDeclareObserverMethods(Class<?> clazz) {
        interceptorDecoratorDefinitionValidator.validateDecoratorDoesNotDeclareObserverMethods(clazz);
    }

    /**
     * Checks if an annotated element (field or parameter) has @Delegate annotation.
     * A @Delegate can be either a jakarta.decorator.Delegate or a javax.decorator.Delegate.
     *
     * @param element the field or parameter to check
     * @return true if @Delegate annotation is present
     */
    public boolean hasDelegateAnnotation(AnnotatedElement element) {
        for (Annotation ann : annotationsOf(element)) {
            if (AnnotationsHelper.hasDelegateAnnotation(ann.annotationType())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnyDelegateAnnotation(Class<?> clazz) {
        return interceptorDecoratorDefinitionValidator.hasAnyDelegateAnnotation(clazz);
    }

    /**
     * Creates and registers a ProducerBean for a producer method or field.
     *
     * @param declaringClass the class containing the producer
     * @param producerMethod the producer method (null if this is a field producer)
     * @param producerField the producer field (null if this is a method producer)
     */
    private void createAndRegisterProducerBean(Class<?> declaringClass, Method producerMethod, Field producerField) {
        beanRegistrationService.createAndRegisterProducerBean(
                declaringClass, producerMethod, producerField, currentAnnotatedTypeOverride);
    }

    /**
     * Extracts producer name from @Named annotation, or returns empty string.
     */
    public String extractProducerName(AnnotatedElement element) {
        return beanRegistrationService.extractProducerName(element);
    }

    /**
     * Extracts qualifiers from an annotated element.
     */
    public Set<Annotation> extractQualifiers(AnnotatedElement element) {
        return beanRegistrationService.extractQualifiers(element);
    }

    private Set<Annotation> synchronizeNamedQualifier(Set<Annotation> qualifiers, String beanName) {
        return beanRegistrationService.synchronizeNamedQualifier(qualifiers, beanName);
    }

    /**
     * Extracts stereotypes declared on a producer member.
     */
    private Set<Class<? extends Annotation>> extractStereotypes(AnnotatedElement element) {
        return beanRegistrationService.extractStereotypes(element);
    }

    /**
     * Extracts scope from an annotated element, or returns default scope.
     */
    private Class<? extends Annotation> extractScope(AnnotatedElement element) {
        return beanRegistrationService.extractScope(element);
    }

    /**
     * Finds the disposer method for a producer member by matching a disposed parameter type and qualifiers.
     */
    public Method findDisposerForProducer(Class<?> clazz,
                                           Set<Type> producerTypes,
                                           Set<Annotation> producerQualifiers) {
        return producerDisposerValidator.findDisposerForProducer(clazz, producerTypes, producerQualifiers);
    }

    private boolean validateDisposerMethodHasMatchingProducer(Class<?> clazz, Method disposerMethod) {
        return producerDisposerValidator.validateDisposerMethodHasMatchingProducer(clazz, disposerMethod);
    }

    private Parameter getDisposesParameter(Method method) {
        return producerDisposerValidator.getDisposesParameter(method);
    }

    /**
     * Checks if a method has a parameter annotated with @Disposes.
     */
    private boolean hasDisposesParameter(Method method) {
        return producerDisposerValidator.hasDisposesParameter(method);
    }

    // -----------------------
    /**
     * Populates injection metadata in BeanImpl for use during bean creation.
     * This includes:
     * - @Inject constructor (or no-args constructor)
     * - @Inject fields (from the entire class hierarchy)
     * - @Inject methods (from the entire class hierarchy, excluding overridden)
     * - @PostConstruct method
     * - @PreDestroy method
     */
    public <T> void populateInjectionMetadata(BeanImpl<T> bean, Class<T> clazz) {
        // 1. Find and set a constructor per JSR-330 rules (only in the bean class itself)
        // - If there's an @Inject constructor, use it
        // - Otherwise, leave null (BeanImpl will use no-args constructor)
        Constructor<T> injectConstructor = null;

        // Look for @Inject constructor
        for (Constructor<?> c : clazz.getDeclaredConstructors()) {
            if (hasInjectAnnotation(c)) {
                @SuppressWarnings("unchecked")
                Constructor<T> typedConstructor = (Constructor<T>) c;
                injectConstructor = typedConstructor;
                break;
            }
        }

        bean.setInjectConstructor(injectConstructor);

        // 2. Collect @Inject fields from the entire hierarchy (superclass → subclass)
        // Fields are inherited, so we need to collect from all classes in the hierarchy
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            for (Field field : currentClass.getDeclaredFields()) {
                if (hasInjectAnnotation(field)) {
                    bean.addInjectField(field);
                }
            }
            currentClass = currentClass.getSuperclass();
        }

        // 3. Collect @Inject methods from the entire hierarchy (superclass → subclass)
        // Methods can be inherited and overridden, so collect from all classes
        currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            for (Method method : currentClass.getDeclaredMethods()) {
                if (hasInjectAnnotation(method)) {
                    bean.addInjectMethod(method);
                }
            }
            currentClass = currentClass.getSuperclass();
        }

        // 4. Find all @PostConstruct methods in the hierarchy (superclass → subclass order)
        // Per Interceptors Specification 1.2+: All @PostConstruct methods in the hierarchy are invoked
        // unless overridden by a subclass
        findAllLifecycleMethods(clazz, bean, true);

        // 5. Find all @PreDestroy methods in the hierarchy (superclass → subclass order during discovery)
        // Per Interceptors Specification 1.2+: All @PreDestroy methods in the hierarchy are invoked
        // unless overridden by a subclass. They will be executed in reverse order (subclass → superclass).
        findAllLifecycleMethods(clazz, bean, false);

        // 6. Find all @PrePassivate methods in the hierarchy for passivating scopes
        // NOTE: @PrePassivate is an EJB annotation (jakarta.ejb), NOT CDI 4.1 standard.
        // This is optional support for EJB integration. CDI 4.1 uses writeObject()/readObject().
        findAllPassivationMethods(clazz, bean, true);

        // 7. Find all @PostActivate methods in the hierarchy for passivating scopes
        // NOTE: @PostActivate is an EJB annotation (jakarta.ejb), NOT CDI 4.1 standard.
        // This is optional support for EJB integration. CDI 4.1 uses writeObject()/readObject().
        findAllPassivationMethods(clazz, bean, false);
    }

    /**
     * Finds all lifecycle methods (@PostConstruct or @PreDestroy) in the class hierarchy.
     * <p>
     * <b>Interceptors Specification 1.2+ / CDI 4.1 Section 7.1:</b>
     * <ul>
     *   <li>Lifecycle methods are discovered in superclass → subclass order</li>
     *   <li>If a subclass overrides a superclass lifecycle method, only the overriding method is invoked</li>
     *   <li>Multiple lifecycle methods can exist in the hierarchy (one per class level)</li>
     *   <li>@PostConstruct: executed superclass → subclass</li>
     *   <li>@PreDestroy: executed subclass → superclass (reversed at invocation time)</li>
     * </ul>
     *
     * @param clazz the bean class
     * @param bean the bean being populated
     * @param isPostConstruct true if @PostConstruct, false if @PreDestroy
     */
    private void findAllLifecycleMethods(Class<?> clazz,
                                         BeanImpl<?> bean,
                                         boolean isPostConstruct) {
        String lifecycleAnnotationName = isPostConstruct ? "PostConstruct" : "PreDestroy";
        // Build class hierarchy: superclass → subclass
        List<Class<?>> hierarchy = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            hierarchy.add(0, current); // Add at beginning to get superclass → subclass order
            current = current.getSuperclass();
        }

        // Process in superclass → subclass order
        for (int i = 0; i < hierarchy.size(); i++) {
            Class<?> currentClass = hierarchy.get(i);
            Method foundMethod = null;

            for (Method method : currentClass.getDeclaredMethods()) {
                boolean hasLifecycle = isPostConstruct ?
                    hasPostConstructAnnotation(method) :
                    hasPreDestroyAnnotation(method);

                if (hasLifecycle) {
                    // Validate lifecycle method rules:
                    // - Must have no parameters
                    // - Must not be static
                    // - Return type is ignored (can be void or any type)
                    if (method.getParameterCount() != 0) {
                        if (isLegacyClassInterceptorLifecycleMethod(currentClass, method)) {
                            // Methods with InvocationContext on lifecycle callbacks are valid for
                            // classes declared via @Interceptors, but they are not bean lifecycle callbacks.
                            continue;
                        }
                        if (isCdiInterceptorLifecycleMethod(clazz, method)) {
                            // CDI interceptor lifecycle callback methods use InvocationContext
                            // and are validated separately by interceptor-specific validation.
                            continue;
                        }
                        knowledgeBase.addDefinitionError(
                            fmtMethod(method) + ": " + lifecycleAnnotationName +
                            " method must have no parameters"
                        );
                        return;
                    }
                    if (Modifier.isStatic(method.getModifiers())) {
                        knowledgeBase.addDefinitionError(
                            fmtMethod(method) + ": " + lifecycleAnnotationName +
                            " method must not be static"
                        );
                        return;
                    }

                    // Check if multiple lifecycle methods in same class (not allowed)
                    if (foundMethod != null) {
                        knowledgeBase.addDefinitionError(
                            currentClass.getName() + ": multiple " +
                            lifecycleAnnotationName +
                            " methods found in same class (only one allowed per class)"
                        );
                        return;
                    }

                    foundMethod = method;
                }
            }

            if (foundMethod != null) {
                // A lifecycle method is inherited only if no subclass overrides it.
                if (!isOverriddenBySubclass(foundMethod, hierarchy, i + 1)) {
                    if (isPostConstruct) {
                        bean.addPostConstructMethod(foundMethod);
                    } else {
                        bean.addPreDestroyMethod(foundMethod);
                    }
                }
            }
        }
    }

    private boolean isOverriddenBySubclass(Method method, List<Class<?>> hierarchy, int startIndex) {
        if (Modifier.isPrivate(method.getModifiers())) {
            return false;
        }

        for (int i = startIndex; i < hierarchy.size(); i++) {
            Class<?> subclass = hierarchy.get(i);
            Method candidate = findDeclaredMethod(subclass, method.getName(), method.getParameterTypes());
            if (candidate == null) {
                continue;
            }

            if (Modifier.isStatic(candidate.getModifiers())) {
                continue;
            }

            if (isOverridableFromSubclass(method, subclass)) {
                return true;
            }
        }

        return false;
    }

    private Method findDeclaredMethod(Class<?> clazz, String methodName, Class<?>[] parameterTypes) {
        try {
            return clazz.getDeclaredMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private boolean isOverridableFromSubclass(Method method, Class<?> subclass) {
        int modifiers = method.getModifiers();

        if (Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)) {
            return true;
        }

        if (Modifier.isPrivate(modifiers)) {
            return false;
        }

        // package-private: only overridable in the same package
        return packageName(method.getDeclaringClass()).equals(packageName(subclass));
    }

    private String packageName(Class<?> clazz) {
        Package pkg = clazz.getPackage();
        return pkg == null ? "" : pkg.getName();
    }

    /**
     * Finds all passivation lifecycle methods (@PrePassivate or @PostActivate) in the class hierarchy.
     * <p>
     * <b>IMPORTANT:</b> @PrePassivate and @PostActivate are EJB annotations (jakarta.ejb), NOT CDI 4.1 standard.
     * CDI 4.1 Section 6.6 only requires beans in passivating scopes to implement Serializable and relies on
     * Java's standard {@code writeObject()}/{@code readObject()} methods. This method provides optional support
     * for EJB-style callbacks as a convenience for applications using both CDI and EJB.
     * <p>
     * <b>CDI 4.1 Standard Approach:</b> Beans should use Java's serialization callbacks instead:
     * <ul>
     *   <li>{@code private void writeObject(ObjectOutputStream)} instead of @PrePassivate</li>
     *   <li>{@code private void readObject(ObjectInputStream)} instead of @PostActivate</li>
     * </ul>
     * <p>
     * <b>EJB Callback Behavior (when jakarta.ejb is on the classpath):</b>
     * <ul>
     *   <li>@PrePassivate methods are invoked before the session is serialized</li>
     *   <li>@PostActivate methods are invoked after the session is deserialized</li>
     *   <li>Execution order: superclass → subclass (same as @PostConstruct)</li>
     *   <li>Override detection: if a subclass overrides a superclass method, only the overriding method is invoked</li>
     * </ul>
     * <p>
     * These callbacks allow beans to:
     * <ul>
     *   <li>Close non-serializable resources before passivation (database connections, file handles)</li>
     *   <li>Re-open resources after activation</li>
     *   <li>Transform state to serializable form</li>
     * </ul>
     *
     * @param clazz the bean class
     * @param bean the bean implementation to populate with lifecycle methods
     * @param isPrePassivate true for @PrePassivate, false for @PostActivate
     */
    private void findAllPassivationMethods(Class<?> clazz, BeanImpl<?> bean, boolean isPrePassivate) {
        AnnotationsEnum passivationAnnotation = isPrePassivate ? PRE_PASSIVATE : POST_ACTIVATE;
        if (passivationAnnotation.getAnnotations().isEmpty()) {
            return;
        }

        // Build class hierarchy: superclass → subclass
        List<Class<?>> hierarchy = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            hierarchy.add(0, current); // Add at beginning for superclass-first order
            current = current.getSuperclass();
        }

        // Track seen signatures for override detection
        Set<String> seenSignatures = new HashSet<>();

        // Process in superclass → subclass order
        for (Class<?> currentClass : hierarchy) {
            Method foundMethod = findPassivationMethodInClass(
                    currentClass,
                    passivationAnnotation,
                    isPrePassivate ? "@PrePassivate" : "@PostActivate"
            );

            if (foundMethod != null) {
                String signature = getMethodSignature(foundMethod);

                // Skip if overridden by subclass
                if (!seenSignatures.contains(signature)) {
                    seenSignatures.add(signature);

                    // Validate method signature
                    if (foundMethod.getParameterCount() != 0) {
                        addValidationError(currentClass,
                            (isPrePassivate ? "@PrePassivate" : "@PostActivate") +
                            " method must have no parameters: " + foundMethod.getName());
                        continue;
                    }

                    if (Modifier.isStatic(foundMethod.getModifiers())) {
                        addValidationError(currentClass,
                            (isPrePassivate ? "@PrePassivate" : "@PostActivate") +
                            " method cannot be static: " + foundMethod.getName());
                        continue;
                    }

                    // Add to bean's passivation method list
                    if (isPrePassivate) {
                        bean.addPrePassivateMethod(foundMethod);
                    } else {
                        bean.addPostActivateMethod(foundMethod);
                    }
                }
            }
        }
    }

    /**
     * Finds a passivation lifecycle method in a single class (not hierarchy).
     *
     * @param clazz the class to search
     * @param passivationAnnotation the passivation annotation enum
     * @param annotationLabel annotation label for diagnostics
     * @return the found method, or null if none found
     */
    private Method findPassivationMethodInClass(Class<?> clazz,
                                                AnnotationsEnum passivationAnnotation,
                                                String annotationLabel) {
        Method found = null;
        int count = 0;

        for (Method method : clazz.getDeclaredMethods()) {
            if (passivationAnnotation.isPresent(method)) {
                found = method;
                count++;
            }
        }

        // Validate: at most one passivation method per class
        if (count > 1) {
            addValidationError(clazz,
                "Class cannot have more than one " + annotationLabel + " method, found " + count);
            return null;
        }

        return found;
    }

    /**
     * Gets a method signature for override detection.
     * Format: "methodName(paramType1,paramType2,...)"
     *
     * @param method the method
     * @return the method signature string
     */
    private String getMethodSignature(Method method) {
        StringBuilder sb = new StringBuilder(method.getName());
        sb.append("(");
        Class<?>[] paramTypes = method.getParameterTypes();
        for (int i = 0; i < paramTypes.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(paramTypes[i].getName());
        }
        sb.append(")");
        return sb.toString();
    }

    public List<Class<?>> collectClassHierarchy(Class<?> leafClass) {
        List<Class<?>> hierarchy = new ArrayList<>();
        Class<?> current = leafClass;
        while (current != null && current != Object.class) {
            hierarchy.add(0, current);
            current = current.getSuperclass();
        }
        return hierarchy;
    }

    public boolean isOverriddenForInjectionMetadata(Method superMethod, Class<?> leafClass) {
        if (Modifier.isPrivate(superMethod.getModifiers())) {
            return false;
        }
        if (superMethod.getDeclaringClass().equals(leafClass)) {
            return false;
        }

        Class<?> current = leafClass;
        while (current != null && current != superMethod.getDeclaringClass()) {
            try {
                Method subMethod = current.getDeclaredMethod(superMethod.getName(), superMethod.getParameterTypes());
                if (!subMethod.equals(superMethod)) {
                    if (Modifier.isStatic(subMethod.getModifiers())) {
                        current = current.getSuperclass();
                        continue;
                    }
                    boolean superPackagePrivate = !Modifier.isPublic(superMethod.getModifiers()) &&
                            !Modifier.isProtected(superMethod.getModifiers()) &&
                            !Modifier.isPrivate(superMethod.getModifiers());
                    if (superPackagePrivate) {
                        return packageName(superMethod.getDeclaringClass())
                                .equals(packageName(subMethod.getDeclaringClass()));
                    }
                    return true;
                }
            } catch (NoSuchMethodException ignored) {
                // continue climbing hierarchy
            }
            current = current.getSuperclass();
        }
        return false;
    }

    public InjectionPoint resolvedInjectionPoint(InjectionPoint delegate, Type resolvedType) {
        if (delegate == null) {
            return null;
        }
        return new ResolvedInjectionPoint(delegate, normalizeResolvedType(resolvedType));
    }

    // InjectionPoint best-effort creation
    // -----------------------

    public InjectionPoint tryCreateInjectionPoint(AnnotatedElement element, Bean<?> owningBean) {
        try {
            if (element instanceof Field) {
                Field field = (Field) element;
                return new InjectionPointImpl<>(field, owningBean, baseTypeOf(field), annotationsOf(field),
                        annotatedOf(field));
            }
            if (element instanceof Parameter) {
                Parameter parameter = (Parameter) element;
                return new InjectionPointImpl<>(parameter, owningBean, baseTypeOf(parameter), annotationsOf(parameter),
                        annotatedOf(parameter));
            }
        } catch (Throwable ignored) {
            // Best-effort only. Bean can still be registered without concrete injection point objects.
        }
        return null;
    }

    // -----------------------
    // Interceptor Validation
    // -----------------------

    /**
     * Validates and registers interceptor metadata.
     *
     * <p><b>CDI 4.1 Interceptor Requirements (Section 9):</b>
     * <ul>
     *   <li>Must have @Interceptor annotation</li>
     *   <li>Must have at least one interceptor binding annotation</li>
     *   <li>Must have exactly one @AroundInvoke, @AroundConstruct, @PostConstruct, or @PreDestroy method</li>
     *   <li>Can optionally have @Priority for ordering (defaults to Integer.MAX_VALUE)</li>
     * </ul>
     *
     * @param clazz the interceptor class to validate
     */
    private void validateAndRegisterInterceptor(Class<?> clazz) {
        interceptorDecoratorDefinitionValidator.validateAndRegisterInterceptor(clazz);
    }

    public boolean isStereotypeAnnotationType(Class<? extends Annotation> annotationType) {
        return hasStereotypeAnnotation(annotationType) ||
                knowledgeBase.isRegisteredStereotype(annotationType);
    }

    private void validateConflictingInterceptorBindings(Class<?> clazz) {
        interceptorDecoratorDefinitionValidator.validateConflictingInterceptorBindings(clazz);
    }

    private void validateAndRegisterDecorator(Class<?> clazz) {
        interceptorDecoratorDefinitionValidator.validateAndRegisterDecorator(clazz);
    }
}
