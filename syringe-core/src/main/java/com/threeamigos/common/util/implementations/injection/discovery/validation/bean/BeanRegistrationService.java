package com.threeamigos.common.util.implementations.injection.discovery.validation.bean;

import com.threeamigos.common.util.implementations.injection.discovery.validation.CDI41BeanValidator;
import com.threeamigos.common.util.implementations.injection.annotations.QualifiersHelper;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.discovery.BeanTypesExtractor;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.resolution.BeanImpl;
import com.threeamigos.common.util.implementations.injection.resolution.GenericTypeResolver;
import com.threeamigos.common.util.implementations.injection.resolution.ProducerBean;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Named;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationExtractors.getPriorityValue;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.findNamedQualifier;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.hasNamedAnnotation;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.normalizeSingletonToApplicationScoped;
import static com.threeamigos.common.util.implementations.injection.types.ClassHelper.normalizeBeanName;

/**
 * Extracted bean/producer registration logic from CDI41BeanValidator.
 */
public class BeanRegistrationService {
    private final KnowledgeBase knowledgeBase;
    private final BeanTypesExtractor beanTypesExtractor;
    private final CDI41BeanValidator validator;
    private final Map<String, ProducerBean<?>> producerBeansByMethodSignature;
    private final Set<String> suppressedSpecializedProducerMethodSignatures;

    public BeanRegistrationService(KnowledgeBase knowledgeBase,
                                   BeanTypesExtractor beanTypesExtractor,
                                   CDI41BeanValidator validator,
                                   Map<String, ProducerBean<?>> producerBeansByMethodSignature,
                                   Set<String> suppressedSpecializedProducerMethodSignatures) {
        this.knowledgeBase = Objects.requireNonNull(knowledgeBase, "knowledgeBase cannot be null");
        this.beanTypesExtractor = Objects.requireNonNull(beanTypesExtractor, "beanTypesExtractor cannot be null");
        this.validator = Objects.requireNonNull(validator, "validator cannot be null");
        this.producerBeansByMethodSignature = Objects.requireNonNull(
                producerBeansByMethodSignature, "producerBeansByMethodSignature cannot be null");
        this.suppressedSpecializedProducerMethodSignatures = Objects.requireNonNull(
                suppressedSpecializedProducerMethodSignatures, "suppressedSpecializedProducerMethodSignatures cannot be null");
    }

    public void createAndRegisterProducerBean(Class<?> declaringClass,
                                              Method producerMethod,
                                              Field producerField,
                                              AnnotatedType<?> currentAnnotatedTypeOverride) {
        AnnotatedElement element = (producerMethod != null) ? producerMethod : producerField;

        boolean annotatedAlternative = validator.isAlternativeDeclared(element);
        boolean classAlternative = validator.isAlternativeDeclared(declaringClass);
        boolean producerAlternativeDeclared = annotatedAlternative || classAlternative;
        boolean alternativeEnabled = true;
        if (producerAlternativeDeclared) {
            AnnotatedElement enablementElement = annotatedAlternative ? element : declaringClass;
            alternativeEnabled = validator.isAlternativeEnabled(enablementElement, declaringClass, true);
        }

        ProducerBean<?> producerBean;
        if (producerMethod != null) {
            producerBean = new ProducerBean<>(declaringClass, producerMethod, annotatedAlternative);
            producerBean.setAlternativeEnabled(alternativeEnabled);

            Method specializedProducerMethod = validator.resolveDirectlyOverriddenProducerMethod(producerMethod);
            boolean specializesProducerMethod = validator.hasSpecializesAnnotation(producerMethod) && specializedProducerMethod != null;

            String producerName = extractProducerName(producerMethod);
            Set<Annotation> producerQualifiers = extractQualifiers(producerMethod);

            if (specializesProducerMethod) {
                String inheritedName = extractProducerName(specializedProducerMethod);
                if (producerName.isEmpty() && !inheritedName.isEmpty()) {
                    producerName = inheritedName;
                }
                producerQualifiers.addAll(extractQualifiers(specializedProducerMethod));
            }

            producerBean.setName(producerName);
            producerBean.setQualifiers(synchronizeNamedQualifier(
                    QualifiersHelper.normalizeBeanQualifiers(producerQualifiers), producerName));
            producerBean.setScope(extractScope(producerMethod));
            producerBean.setStereotypes(extractStereotypes(producerMethod));

            BeanTypesExtractor.ExtractionResult producerTypes =
                    beanTypesExtractor.extractProducerBeanTypes(validator.baseTypeOf(producerMethod), producerMethod);
            for (String error : producerTypes.getDefinitionErrors()) {
                knowledgeBase.addDefinitionError(validator.fmtMethod(producerMethod) + ": " + error);
            }
            Set<Type> resolvedProducerTypes = new LinkedHashSet<>(
                    resolveProducerMethodBeanTypes(producerMethod, producerTypes.getTypes(), currentAnnotatedTypeOverride));
            if (specializesProducerMethod) {
                BeanTypesExtractor.ExtractionResult specializedProducerTypes =
                        beanTypesExtractor.extractProducerBeanTypes(validator.baseTypeOf(specializedProducerMethod), specializedProducerMethod);
                for (String error : specializedProducerTypes.getDefinitionErrors()) {
                    knowledgeBase.addDefinitionError(validator.fmtMethod(specializedProducerMethod) + ": " + error);
                }
                resolvedProducerTypes.addAll(
                        resolveProducerMethodBeanTypes(
                                specializedProducerMethod,
                                specializedProducerTypes.getTypes(),
                                currentAnnotatedTypeOverride));
            }
            producerBean.setTypes(resolvedProducerTypes);

            for (Parameter param : producerMethod.getParameters()) {
                if (!validator.hasDisposesAnnotation(param)) {
                    InjectionPoint ip = validator.tryCreateInjectionPoint(param, producerBean);
                    if (ip != null) {
                        producerBean.addInjectionPoint(ip);
                    }
                }
            }
        } else if (producerField != null) {
            producerBean = new ProducerBean<>(declaringClass, producerField, annotatedAlternative);
            producerBean.setAlternativeEnabled(alternativeEnabled);

            String producerName = extractProducerName(producerField);
            producerBean.setName(producerName);
            producerBean.setQualifiers(synchronizeNamedQualifier(
                    QualifiersHelper.normalizeBeanQualifiers(extractQualifiers(producerField)), producerName));
            producerBean.setScope(extractScope(producerField));
            producerBean.setStereotypes(extractStereotypes(producerField));

            BeanTypesExtractor.ExtractionResult producerTypes =
                    beanTypesExtractor.extractProducerBeanTypes(validator.baseTypeOf(producerField), producerField);
            for (String error : producerTypes.getDefinitionErrors()) {
                knowledgeBase.addDefinitionError(validator.fmtField(producerField) + ": " + error);
            }
            producerBean.setTypes(resolveProducerFieldBeanTypes(producerField, producerTypes.getTypes(), currentAnnotatedTypeOverride));
        } else {
            throw new IllegalArgumentException("Either producerMethod or producerField must be non-null");
        }

        Method disposer = validator.findDisposerForProducer(declaringClass, producerBean.getTypes(), producerBean.getQualifiers());
        if (disposer != null) {
            producerBean.setDisposerMethod(disposer);
            Set<Integer> disposerPositions = new LinkedHashSet<>();
            Parameter[] disposerParameters = disposer.getParameters();
            for (int i = 0; i < disposerParameters.length; i++) {
                Parameter parameter = disposerParameters[i];
                if (validator.hasDisposesAnnotation(parameter)) {
                    disposerPositions.add(i);
                    continue;
                }
                InjectionPoint ip = validator.tryCreateInjectionPoint(parameter, producerBean);
                if (ip != null) {
                    producerBean.addInjectionPoint(ip);
                }
            }
            producerBean.setDisposerParameterPositions(disposerPositions);
        }

        if (knowledgeBase.isTypeVetoed(declaringClass)) {
            producerBean.setVetoed(true);
            System.out.println("[CDI41BeanValidator] Producer bean marked as vetoed (declaring class vetoed): " +
                    declaringClass.getName() + " -> " +
                    (producerMethod != null ? producerMethod.getName() : producerField.getName()));
        }

        Integer priorityValue = getPriorityValue(element);
        if (priorityValue == null) {
            priorityValue = validator.extractEffectivePriority(declaringClass);
        }
        if (priorityValue != null) {
            producerBean.setPriority(priorityValue);
        }

        if (producerMethod != null) {
            String producerMethodSignature = validator.producerMethodSpecializationSignature(producerMethod);
            producerBeansByMethodSignature.put(producerMethodSignature, producerBean);

            if (suppressedSpecializedProducerMethodSignatures.contains(producerMethodSignature)) {
                return;
            }

            Method specializedProducerMethod = validator.resolveDirectlyOverriddenProducerMethod(producerMethod);
            if (validator.hasSpecializesAnnotation(producerMethod)
                    && specializedProducerMethod != null
                    && validator.isSpecializingProducerMethodEnabled(producerMethod)) {
                String specializedSignature = validator.producerMethodSpecializationSignature(specializedProducerMethod);
                suppressedSpecializedProducerMethodSignatures.add(specializedSignature);
                ProducerBean<?> specializedProducerBean = producerBeansByMethodSignature.get(specializedSignature);
                if (specializedProducerBean != null) {
                    knowledgeBase.getProducerBeans().remove(specializedProducerBean);
                    knowledgeBase.getBeans().remove(specializedProducerBean);
                }
            }
        }

        knowledgeBase.addProducerBean(producerBean);
    }

    public <T> BeanImpl<T> createAndRegisterManagedBean(Class<T> clazz,
                                                        BeanArchiveMode beanArchiveMode,
                                                        AnnotatedType<T> annotatedTypeOverride,
                                                        AnnotatedType<?> currentAnnotatedTypeOverride,
                                                        boolean alternative,
                                                        boolean alternativeEnabled,
                                                        boolean valid,
                                                        Class<? extends Annotation> discoveredBeanScope) {
        BeanImpl<T> bean = new BeanImpl<>(clazz, alternative);
        if (annotatedTypeOverride != null) {
            bean.setAnnotatedTypeMetadata(annotatedTypeOverride);
        }
        bean.setAlternativeEnabled(alternativeEnabled);
        bean.setPriority(validator.extractEffectivePriority(clazz));

        if (!valid) {
            bean.setHasValidationErrors(true);
        }

        if (knowledgeBase.isTypeVetoed(clazz)) {
            bean.setVetoed(true);
            System.out.println("[CDI41BeanValidator] Bean marked as vetoed: " + clazz.getName());
        }

        String beanName = validator.extractBeanName(clazz);
        bean.setName(beanName);
        bean.setQualifiers(synchronizeNamedQualifier(validator.extractBeanQualifiers(clazz), beanName));
        Class<? extends Annotation> effectiveBeanScope = validator.extractBeanScope(clazz, discoveredBeanScope);
        validator.validateManagedBeanPublicFieldScopeConstraint(clazz, effectiveBeanScope);
        validator.validateManagedBeanGenericTypeScopeConstraint(clazz, effectiveBeanScope);
        validator.validateProgrammaticPassivatingScopeConstraint(clazz, effectiveBeanScope);
        bean.setScope(effectiveBeanScope);

        BeanTypesExtractor.ExtractionResult managedBeanTypes = beanTypesExtractor.extractManagedBeanTypes(clazz);
        for (String error : managedBeanTypes.getDefinitionErrors()) {
            validator.addValidationError(clazz, error);
        }
        Set<Type> managedTypes = managedBeanTypes.getTypes();
        if (currentAnnotatedTypeOverride != null && currentAnnotatedTypeOverride.getJavaClass().equals(clazz)) {
            Set<Type> overrideTypes = new LinkedHashSet<>(currentAnnotatedTypeOverride.getTypeClosure());
            if (!overrideTypes.isEmpty()) {
                managedTypes = overrideTypes;
            }
        }
        bean.setTypes(managedTypes);
        validator.addGenericSelfTypeForManagedBean(bean, clazz);
        validator.applySpecializationInheritance(bean, clazz, beanArchiveMode);

        bean.setStereotypes(validator.extractBeanStereotypes(clazz));
        validator.populateInjectionMetadata(bean, clazz);

        List<Class<?>> hierarchy = validator.collectClassHierarchy(clazz);
        for (Class<?> declaringClass : hierarchy) {
            for (Field field : declaringClass.getDeclaredFields()) {
                if (validator.hasInjectAnnotation(field)) {
                    InjectionPoint ip = validator.resolvedInjectionPoint(
                            validator.tryCreateInjectionPoint(field, bean),
                            GenericTypeResolver.resolve(validator.baseTypeOf(field), clazz, field.getDeclaringClass()));
                    if (ip != null) {
                        bean.addInjectionPoint(ip);
                    }
                }
            }
        }
        for (Class<?> declaringClass : hierarchy) {
            for (Method method : declaringClass.getDeclaredMethods()) {
                if (!validator.hasInjectAnnotation(method)) {
                    continue;
                }
                if (validator.isOverriddenForInjectionMetadata(method, clazz)) {
                    continue;
                }
                for (Parameter parameter : method.getParameters()) {
                    InjectionPoint ip = validator.resolvedInjectionPoint(
                            validator.tryCreateInjectionPoint(parameter, bean),
                            GenericTypeResolver.resolve(validator.baseTypeOf(parameter), clazz, method.getDeclaringClass()));
                    if (ip != null) {
                        bean.addInjectionPoint(ip);
                    }
                }
            }
        }
        for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            if (validator.hasInjectAnnotation(constructor)) {
                for (Parameter parameter : constructor.getParameters()) {
                    InjectionPoint ip = validator.resolvedInjectionPoint(
                            validator.tryCreateInjectionPoint(parameter, bean),
                            GenericTypeResolver.resolve(validator.baseTypeOf(parameter), clazz, constructor.getDeclaringClass()));
                    if (ip != null) {
                        bean.addInjectionPoint(ip);
                    }
                }
            }
        }

        knowledgeBase.addBean(bean);
        return bean;
    }

    public String extractProducerName(AnnotatedElement element) {
        Annotation named = findNamedQualifier(validator.annotationsOf(element));
        if (named != null) {
            String value = readNamedValue(named);
            if (value != null && !value.isEmpty()) {
                return value;
            }
            if (element instanceof Field) {
                return ((Field) element).getName();
            } else if (element instanceof Method) {
                String methodName = ((Method) element).getName();
                if (methodName.startsWith("get") && methodName.length() > 3) {
                    return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
                }
                return methodName;
            }
        }
        return "";
    }

    public Set<Annotation> extractQualifiers(AnnotatedElement element) {
        Annotation[] annotations = validator.annotationsOf(element);
        Set<Annotation> qualifiers = QualifiersHelper.extractQualifierAnnotations(annotations);
        for (Annotation annotation : annotations) {
            if (validator.isQualifierAnnotationType(annotation.annotationType())) {
                qualifiers.add(annotation);
            }
        }
        return QualifiersHelper.normalizeBeanQualifiers(qualifiers);
    }

    public Set<Annotation> synchronizeNamedQualifier(Set<Annotation> qualifiers, String beanName) {
        Set<Annotation> normalized = new LinkedHashSet<>();
        Annotation existingNamed = null;
        if (qualifiers != null) {
            for (Annotation qualifier : qualifiers) {
                if (qualifier == null) {
                    continue;
                }
                if (hasNamedAnnotation(qualifier.annotationType())) {
                    if (existingNamed == null) {
                        existingNamed = qualifier;
                    }
                    continue;
                }
                normalized.add(qualifier);
            }
        }

        if (existingNamed == null) {
            return normalized;
        }

        String resolvedName = normalizeBeanName(beanName);
        if (resolvedName == null) {
            normalized.add(existingNamed);
            return normalized;
        }

        Class<? extends Annotation> namedType = existingNamed.annotationType();
        normalized.add(createNamedQualifier(namedType, resolvedName));
        return normalized;
    }

    public Class<? extends Annotation> extractScope(AnnotatedElement element) {
        List<Class<? extends Annotation>> directScopes = new ArrayList<>();
        for (Annotation ann : validator.annotationsOf(element)) {
            Class<? extends Annotation> annotationType = ann.annotationType();
            if (validator.isScopeAnnotationType(annotationType)) {
                directScopes.add(annotationType);
            }
        }

        if (directScopes.size() > 1) {
            String scopeNames = directScopes.stream()
                    .map(scope -> "@" + scope.getSimpleName())
                    .collect(Collectors.joining(", "));
            knowledgeBase.addDefinitionError(describeAnnotatedElement(element) +
                    ": declares multiple scope annotations: " + scopeNames);
        }

        if (!directScopes.isEmpty()) {
            return normalizeSingletonToApplicationScoped(directScopes.get(0));
        }

        Class<? extends Annotation> inheritedScope = null;
        for (Annotation ann : validator.annotationsOf(element)) {
            Class<? extends Annotation> annotationType = ann.annotationType();
            if (!validator.isStereotypeAnnotationType(annotationType)) {
                continue;
            }

            Class<? extends Annotation> stereotypeScope = validator.extractScopeFromStereotype(annotationType);
            if (stereotypeScope == null) {
                continue;
            }

            if (inheritedScope == null) {
                inheritedScope = stereotypeScope;
            } else if (!inheritedScope.equals(stereotypeScope)) {
                knowledgeBase.addDefinitionError(describeAnnotatedElement(element) +
                        ": conflicting scopes inherited from stereotypes (" +
                        inheritedScope.getName() + " vs " + stereotypeScope.getName() + ")");
            }
        }

        if (inheritedScope != null) {
            return normalizeSingletonToApplicationScoped(inheritedScope);
        }

        return Dependent.class;
    }

    public Set<Class<? extends Annotation>> extractStereotypes(AnnotatedElement element) {
        Set<Class<? extends Annotation>> stereotypes = new HashSet<>();
        for (Annotation annotation : validator.annotationsOf(element)) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (validator.isStereotypeAnnotationType(annotationType)) {
                stereotypes.add(annotationType);
            }
        }
        return stereotypes;
    }

    private Set<Type> resolveProducerMethodBeanTypes(Method producerMethod,
                                                     Set<Type> extractedTypes,
                                                     AnnotatedType<?> currentAnnotatedTypeOverride) {
        if (producerMethod == null || currentAnnotatedTypeOverride == null) {
            return extractedTypes;
        }
        Annotated annotated = validator.annotatedOf(producerMethod);
        if (annotated == null) {
            return extractedTypes;
        }
        Set<Type> overrideTypes = validator.typeClosureOf(producerMethod);
        if (overrideTypes.isEmpty()) {
            return extractedTypes;
        }
        Set<Type> resolved = new LinkedHashSet<>(overrideTypes);
        resolved.add(Object.class);
        return resolved;
    }

    private Set<Type> resolveProducerFieldBeanTypes(Field producerField,
                                                    Set<Type> extractedTypes,
                                                    AnnotatedType<?> currentAnnotatedTypeOverride) {
        if (producerField == null || currentAnnotatedTypeOverride == null) {
            return extractedTypes;
        }
        Annotated annotated = validator.annotatedOf(producerField);
        if (annotated == null) {
            return extractedTypes;
        }
        Set<Type> overrideTypes = validator.typeClosureOf(producerField);
        if (overrideTypes.isEmpty()) {
            return extractedTypes;
        }
        return new LinkedHashSet<>(overrideTypes);
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

    private Annotation createNamedQualifier(Class<? extends Annotation> namedType, String value) {
        final Class<? extends Annotation> effectiveType = namedType == null ? Named.class : namedType;
        InvocationHandler handler = (proxy, method, args) -> {
            String methodName = method.getName();
            switch (methodName) {
                case "annotationType":
                    return effectiveType;
                case "value":
                    return value;
                case "equals":
                    Object other = args == null || args.length == 0 ? null : args[0];
                    if (!effectiveType.isInstance(other)) {
                        return false;
                    }
                    try {
                        Method otherValueMethod = effectiveType.getMethod("value");
                        Object otherValue = otherValueMethod.invoke(other);
                        return Objects.equals(value, otherValue);
                    } catch (ReflectiveOperationException ignored) {
                        return false;
                    }
                case "hashCode":
                    return (127 * "value".hashCode()) ^ Objects.hashCode(value);
                case "toString":
                    return "@" + effectiveType.getName() + "(value=" + value + ")";
            }
            throw new UnsupportedOperationException("Unsupported @Named literal method: " + methodName);
        };
        return (Annotation) Proxy.newProxyInstance(
                effectiveType.getClassLoader(),
                new Class<?>[]{effectiveType},
                handler);
    }

    private String describeAnnotatedElement(AnnotatedElement element) {
        if (element instanceof Field) {
            return validator.fmtField((Field) element);
        }
        if (element instanceof Method) {
            return validator.fmtMethod((Method) element);
        }
        if (element instanceof Class) {
            return ((Class<?>) element).getName();
        }
        return element.toString();
    }
}
