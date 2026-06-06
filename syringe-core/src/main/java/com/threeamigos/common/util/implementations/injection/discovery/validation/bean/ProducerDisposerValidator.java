package com.threeamigos.common.util.implementations.injection.discovery.validation.bean;

import com.threeamigos.common.util.implementations.injection.discovery.validation.CDI41BeanValidator;
import com.threeamigos.common.util.implementations.injection.annotations.QualifiersHelper;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.resolution.TypeChecker;
import jakarta.enterprise.inject.spi.DefinitionException;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.hasNamedAnnotation;

/**
 * Extracted producer/disposer validation rules and specialization matching.
 */
public class ProducerDisposerValidator {
    private final KnowledgeBase knowledgeBase;
    private final TypeChecker typeChecker;
    private final CDI41BeanValidator validator;
    private final Map<String, Method> specializingProducerMethodsBySpecializedSignature;

    public ProducerDisposerValidator(KnowledgeBase knowledgeBase,
                              TypeChecker typeChecker,
                              CDI41BeanValidator validator,
                              Map<String, Method> specializingProducerMethodsBySpecializedSignature) {
        this.knowledgeBase = Objects.requireNonNull(knowledgeBase, "knowledgeBase cannot be null");
        this.typeChecker = Objects.requireNonNull(typeChecker, "typeChecker cannot be null");
        this.validator = Objects.requireNonNull(validator, "validator cannot be null");
        this.specializingProducerMethodsBySpecializedSignature = Objects.requireNonNull(
                specializingProducerMethodsBySpecializedSignature,
                "specializingProducerMethodsBySpecializedSignature cannot be null");
    }

    public boolean validateProducerField(Field field) {
        boolean valid = true;

        if (validator.hasInjectAnnotation(field)) {
            knowledgeBase.addDefinitionError(validator.fmtField(field) + ": producer field may not be annotated @Inject");
            valid = false;
        }

        try {
            validator.checkProducerTypeValidity(validator.baseTypeOf(field));
        } catch (DefinitionException e) {
            knowledgeBase.addDefinitionError(validator.fmtField(field) + ": " + e.getMessage());
            valid = false;
        }

        try {
            validator.validateProducerFieldTypeVariableScopeConstraint(field);
        } catch (DefinitionException e) {
            knowledgeBase.addDefinitionError(validator.fmtField(field) + ": " + e.getMessage());
            valid = false;
        }

        return valid;
    }

    public boolean validateProducerMethod(Method method) {
        boolean valid = true;

        if (Modifier.isAbstract(method.getModifiers())) {
            knowledgeBase.addDefinitionError(validator.fmtMethod(method) + ": producer method must not be abstract");
            valid = false;
        }

        if (method.getTypeParameters().length > 0) {
            knowledgeBase.addDefinitionError(validator.fmtMethod(method) + ": producer method must not be generic");
            valid = false;
        }

        if (method.isVarArgs()) {
            knowledgeBase.addDefinitionError(validator.fmtMethod(method) + ": producer method must not be varargs");
            valid = false;
        }

        if (validator.hasInjectAnnotation(method)) {
            knowledgeBase.addDefinitionError(validator.fmtMethod(method) + ": producer method must not be annotated @Inject");
            valid = false;
        }

        if (!validateSpecializingProducerMethodConstraint(method)) {
            valid = false;
        }

        try {
            validator.checkProducerTypeValidity(validator.baseTypeOf(method));
        } catch (DefinitionException e) {
            knowledgeBase.addDefinitionError(validator.fmtMethod(method) + ": " + e.getMessage());
            valid = false;
        }

        try {
            validator.validateProducerMethodTypeVariableScopeConstraint(method);
        } catch (DefinitionException e) {
            knowledgeBase.addDefinitionError(validator.fmtMethod(method) + ": " + e.getMessage());
            valid = false;
        }

        for (Parameter parameter : method.getParameters()) {
            if (validator.hasDisposesAnnotation(parameter)) {
                knowledgeBase.addDefinitionError(validator.fmtParameter(parameter) +
                        ": producer method parameter may not be annotated @Disposes");
                valid = false;
                continue;
            }
            if (validator.hasObservesAnnotation(parameter)) {
                knowledgeBase.addDefinitionError(validator.fmtParameter(parameter) +
                        ": producer method parameter may not be annotated @Observes");
                valid = false;
                continue;
            }
            if (validator.hasObservesAsyncAnnotation(parameter)) {
                knowledgeBase.addDefinitionError(validator.fmtParameter(parameter) +
                        ": producer method parameter may not be annotated @ObservesAsync");
                valid = false;
                continue;
            }

            try {
                validator.checkInjectionTypeValidity(validator.baseTypeOf(parameter));
            } catch (IllegalArgumentException e) {
                knowledgeBase.addInjectionError(validator.fmtParameter(parameter) + ": " + e.getMessage());
                valid = false;
            } catch (DefinitionException e) {
                knowledgeBase.addDefinitionError(validator.fmtParameter(parameter) + ": " + e.getMessage());
                valid = false;
            }
            try {
                validator.validateQualifiers(validator.annotationsOf(parameter), validator.fmtMethod(method));
            } catch (DefinitionException e) {
                knowledgeBase.addDefinitionError(validator.fmtParameter(parameter) + ": " + e.getMessage());
                valid = false;
            }

            if (validator.isNotValidNamedInjectionPointUsage(parameter)) {
                valid = false;
            }

            if (validator.isNotValidInjectionPointMetadataUsage(parameter, false)) {
                valid = false;
            }
            if (validator.isNotValidInterceptionFactoryInjectionPointUsage(parameter, true)) {
                valid = false;
            }
        }

        return valid;
    }

    public boolean validateDisposerMethod(Method method) {
        boolean valid = true;

        if (Modifier.isAbstract(method.getModifiers())) {
            knowledgeBase.addDefinitionError(validator.fmtMethod(method) + ": disposer method must not be abstract");
            valid = false;
        }

        if (method.getTypeParameters().length > 0) {
            knowledgeBase.addDefinitionError(validator.fmtMethod(method) + ": disposer method must not be generic");
            valid = false;
        }

        if (validator.hasProducesAnnotation(method)) {
            knowledgeBase.addDefinitionError(validator.fmtMethod(method) + ": disposer method may not be annotated @Produces");
            valid = false;
        }

        if (validator.hasInjectAnnotation(method)) {
            knowledgeBase.addDefinitionError(validator.fmtMethod(method) + ": disposer method may not be annotated @Inject");
            valid = false;
        }

        int disposesCount = 0;
        Parameter disposesParam = null;
        for (Parameter parameter : method.getParameters()) {
            if (validator.hasDisposesAnnotation(parameter)) {
                disposesCount++;
                disposesParam = parameter;
            }
        }

        if (disposesCount == 0) {
            knowledgeBase.addDefinitionError(validator.fmtMethod(method) + ": disposer method must have exactly one @Disposes parameter (found 0)");
            valid = false;
        } else if (disposesCount > 1) {
            knowledgeBase.addDefinitionError(validator.fmtMethod(method) + ": disposer method must have exactly one @Disposes parameter (found " + disposesCount + ")");
            valid = false;
        }

        if (disposesParam != null) {
            try {
                validator.checkInjectionTypeValidity(validator.baseTypeOf(disposesParam));
            } catch (IllegalArgumentException e) {
                knowledgeBase.addInjectionError(validator.fmtParameter(disposesParam) + ": " + e.getMessage());
                valid = false;
            } catch (DefinitionException e) {
                knowledgeBase.addDefinitionError(validator.fmtParameter(disposesParam) + ": " + e.getMessage());
                valid = false;
            }
        }

        for (Parameter parameter : method.getParameters()) {
            if (validator.hasObservesAnnotation(parameter)) {
                knowledgeBase.addDefinitionError(validator.fmtParameter(parameter) +
                        ": disposer method parameter may not be annotated @Observes");
                valid = false;
            }
            if (validator.hasObservesAsyncAnnotation(parameter)) {
                knowledgeBase.addDefinitionError(validator.fmtParameter(parameter) +
                        ": disposer method parameter may not be annotated @ObservesAsync");
                valid = false;
            }
        }

        for (Parameter parameter : method.getParameters()) {
            if (!validator.hasDisposesAnnotation(parameter)) {
                try {
                    validator.checkInjectionTypeValidity(validator.baseTypeOf(parameter));
                } catch (IllegalArgumentException e) {
                    knowledgeBase.addInjectionError(validator.fmtParameter(parameter) + ": " + e.getMessage());
                    valid = false;
                } catch (DefinitionException e) {
                    knowledgeBase.addDefinitionError(validator.fmtParameter(parameter) + ": " + e.getMessage());
                    valid = false;
                }

                try {
                    validator.validateQualifiers(validator.annotationsOf(parameter), validator.fmtMethod(method));
                } catch (DefinitionException e) {
                    knowledgeBase.addDefinitionError(validator.fmtParameter(parameter) + ": " + e.getMessage());
                    valid = false;
                }

                if (validator.isNotValidNamedInjectionPointUsage(parameter)) {
                    valid = false;
                }

                if (validator.isNotValidInjectionPointMetadataUsage(parameter, true)) {
                    valid = false;
                }
            }
        }

        return valid;
    }

    public boolean validateSpecializingProducerMethodConstraint(Method method) {
        if (!validator.hasSpecializesAnnotation(method)) {
            return true;
        }

        if (Modifier.isStatic(method.getModifiers())) {
            knowledgeBase.addDefinitionError(validator.fmtMethod(method) +
                    ": producer method annotated @Specializes must be non-static");
            return false;
        }

        Class<?> declaringClass = method.getDeclaringClass();
        Class<?> directSuperclass = declaringClass.getSuperclass();
        if (directSuperclass == null || Object.class.equals(directSuperclass)) {
            knowledgeBase.addDefinitionError(validator.fmtMethod(method) +
                    ": producer method annotated @Specializes must directly override another producer method");
            return false;
        }

        Method overridden = resolveDirectlyOverriddenProducerMethod(method);
        if (overridden == null) {
            knowledgeBase.addDefinitionError(validator.fmtMethod(method) +
                    ": producer method annotated @Specializes must directly override another producer method");
            return false;
        }

        String inheritedName = validator.extractProducerName(overridden);
        if (!inheritedName.isEmpty() && declaresBeanNameExplicitly(method)) {
            knowledgeBase.addDefinitionError(validator.fmtMethod(method) +
                    ": specializing producer method may not explicitly declare @Named when specialized producer has name '" +
                    inheritedName + "'");
            return false;
        }

        if (isSpecializingProducerMethodEnabled(method)) {
            String specializedSignature = producerMethodSpecializationSignature(overridden);
            Method previousSpecializer = specializingProducerMethodsBySpecializedSignature.get(specializedSignature);
            if (previousSpecializer != null && !previousSpecializer.equals(method)) {
                knowledgeBase.addError(validator.fmtMethod(method) +
                        ": inconsistent specialization. Both " + validator.fmtMethod(previousSpecializer) +
                        " and " + validator.fmtMethod(method) + " specialize " + validator.fmtMethod(overridden));
                return false;
            }
            specializingProducerMethodsBySpecializedSignature.put(specializedSignature, method);
        }

        return true;
    }

    public Method resolveDirectlyOverriddenProducerMethod(Method method) {
        if (method == null) {
            return null;
        }
        Class<?> declaringClass = method.getDeclaringClass();
        Class<?> directSuperclass = declaringClass.getSuperclass();
        if (directSuperclass == null || Object.class.equals(directSuperclass)) {
            return null;
        }
        try {
            Method overridden = directSuperclass.getDeclaredMethod(method.getName(), method.getParameterTypes());
            if (!validator.hasProducesAnnotation(overridden)) {
                return null;
            }
            return overridden;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    public boolean isSpecializingProducerMethodEnabled(Method method) {
        if (method == null) {
            return false;
        }
        Class<?> declaringClass = method.getDeclaringClass();
        boolean methodAlternative = validator.isAlternativeDeclared(method);
        boolean classAlternative = validator.isAlternativeDeclared(declaringClass);
        if (!methodAlternative && !classAlternative) {
            return true;
        }
        AnnotatedElement enablementElement = methodAlternative ? method : declaringClass;
        return validator.isAlternativeEnabled(enablementElement, declaringClass, true);
    }

    public String producerMethodSpecializationSignature(Method method) {
        StringBuilder signature = new StringBuilder();
        signature.append(method.getDeclaringClass().getName())
                .append("#")
                .append(method.getName())
                .append("(");
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) {
                signature.append(",");
            }
            signature.append(parameterTypes[i].getName());
        }
        signature.append(")");
        return signature.toString();
    }

    public Method findDisposerForProducer(Class<?> clazz,
                                          Set<Type> producerTypes,
                                          Set<Annotation> producerQualifiers) {
        List<Method> matches = new ArrayList<>();

        for (Method method : clazz.getDeclaredMethods()) {
            Parameter disposesParameter = getDisposesParameter(method);
            if (disposesParameter == null) {
                continue;
            }

            for (Type producerType : producerTypes) {
                if (matchesDisposesParameter(disposesParameter, producerType, producerQualifiers)) {
                    matches.add(method);
                    break;
                }
            }
        }

        if (matches.size() <= 1) {
            return matches.isEmpty() ? null : matches.get(0);
        }

        knowledgeBase.addDefinitionError(clazz.getName() +
                ": multiple disposer methods match producer types " + producerTypes +
                " and qualifiers " + formatQualifiers(producerQualifiers));
        return matches.get(0);
    }

    public boolean validateDisposerMethodHasMatchingProducer(Class<?> clazz, Method disposerMethod) {
        Parameter disposesParameter = getDisposesParameter(disposerMethod);
        if (disposesParameter == null) {
            return false;
        }

        Type disposesType = validator.baseTypeOf(disposesParameter);
        Set<Annotation> requiredQualifiers = QualifiersHelper.extractQualifiers(validator.annotationsOf(disposesParameter));

        for (Method producerMethod : clazz.getDeclaredMethods()) {
            if (!validator.hasProducesAnnotation(producerMethod)) {
                continue;
            }
            for (Type producerType : validator.typeClosureOf(producerMethod)) {
                if (matchesDisposesParameter(disposesParameter, producerType, validator.extractQualifiers(producerMethod))) {
                    return true;
                }
            }
        }

        for (Field producerField : clazz.getDeclaredFields()) {
            if (!validator.hasProducesAnnotation(producerField)) {
                continue;
            }
            for (Type producerType : validator.typeClosureOf(producerField)) {
                if (matchesDisposesParameter(disposesParameter, producerType, validator.extractQualifiers(producerField))) {
                    return true;
                }
            }
        }

        knowledgeBase.addDefinitionError(validator.fmtMethod(disposerMethod) +
                ": @Disposes parameter type/qualifiers do not match any producer method return type or producer field type " +
                "(type=" + disposesType.getTypeName() + ", qualifiers=" + formatQualifiers(requiredQualifiers) + ")");
        return false;
    }

    public Parameter getDisposesParameter(Method method) {
        for (Parameter param : method.getParameters()) {
            if (validator.hasDisposesAnnotation(param)) {
                return param;
            }
        }
        return null;
    }

    public boolean hasDisposesParameter(Method method) {
        for (Parameter param : method.getParameters()) {
            if (validator.hasDisposesAnnotation(param)) {
                return true;
            }
        }
        return false;
    }

    private boolean declaresBeanNameExplicitly(AnnotatedElement element) {
        if (element == null) {
            return false;
        }
        for (Annotation annotation : validator.annotationsOf(element)) {
            if (annotation != null && hasNamedAnnotation(annotation.annotationType())) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesDisposesParameter(Parameter disposesParameter,
                                             Type producerType,
                                             Set<Annotation> producerQualifiers) {
        Set<Annotation> requiredQualifiers = QualifiersHelper.extractQualifiers(validator.annotationsOf(disposesParameter));
        if (!QualifiersHelper.qualifiersMatch(requiredQualifiers, producerQualifiers)) {
            return false;
        }

        Type disposesType = validator.baseTypeOf(disposesParameter);
        try {
            return typeChecker.isAssignable(disposesType, producerType);
        } catch (DefinitionException e) {
            return false;
        } catch (IllegalStateException e) {
            knowledgeBase.addDefinitionError(validator.fmtParameter(disposesParameter) +
                    ": failed to compare @Disposes type against producer type " + producerType.getTypeName() +
                    " (" + e.getMessage() + ")");
            return false;
        }
    }

    private String formatQualifiers(Set<Annotation> qualifiers) {
        return qualifiers.stream()
                .map(q -> "@" + q.annotationType().getSimpleName())
                .sorted()
                .collect(Collectors.joining(", "));
    }
}
