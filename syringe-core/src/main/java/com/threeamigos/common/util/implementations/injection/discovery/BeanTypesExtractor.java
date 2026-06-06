package com.threeamigos.common.util.implementations.injection.discovery;

import com.threeamigos.common.util.implementations.injection.util.SimpleParameterizedType;
import com.threeamigos.common.util.implementations.injection.types.TypeClosureHelper;
import com.threeamigos.common.util.implementations.injection.types.RawTypeExtractor;

import java.lang.annotation.Annotation;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationExtractors.getTypedAnnotation;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.hasTypedAnnotation;

/**
 * Extracts bean type sets for managed beans and producers.
 *
 * <p>Designed as a stateless component: create one instance per validation flow
 * or reuse safely across threads.
 */
public final class BeanTypesExtractor {

    /**
     * Extracts resulting bean types for a managed bean class.
     *
     * <p>The result is the unrestricted managed-bean type set optionally restricted by
     * {@code @Typed}, with illegal bean types removed.
     */
    public ExtractionResult extractManagedBeanTypes(Class<?> beanClass) {
        Objects.requireNonNull(beanClass, "beanClass cannot be null");

        List<String> definitionErrors = new ArrayList<>();
        Set<Type> unrestrictedTypes = TypeClosureHelper.extractTypesFromClass(beanClass);
        Set<Type> resultingTypes = unrestrictedTypes;
        if (hasTypedAnnotation(beanClass)) {
            Annotation typedAnnotation = getTypedAnnotation(beanClass);
            if (typedAnnotation != null) {
                resultingTypes = computeTypedBeanTypes(beanClass, typedAnnotation, unrestrictedTypes, definitionErrors);
            }
        }
        Set<Type> legalTypes = keepLegalBeanTypes(resultingTypes);
        return new ExtractionResult(legalTypes, definitionErrors);
    }

    /**
     * Extracts resulting bean types for a producer method/field type.
     *
     * <p>The result is the unrestricted producer type set with illegal bean types removed.
     */
    public ExtractionResult extractProducerBeanTypes(Type producerType) {
        return extractProducerBeanTypes(producerType, null);
    }

    /**
     * Extracts resulting bean types for a producer method/field type with optional producer-member annotations.
     */
    public ExtractionResult extractProducerBeanTypes(Type producerType, AnnotatedElement producerElement) {
        Objects.requireNonNull(producerType, "producerType cannot be null");

        List<String> definitionErrors = new ArrayList<>();
        Set<Type> unrestrictedTypes = TypeClosureHelper.extractTypesFromType(producerType);
        Class<?> producerRawType = RawTypeExtractor.getRawType(producerType);
        if (producerType instanceof ParameterizedType
                && shouldAddRawProducerType((ParameterizedType) producerType, producerRawType)) {
            unrestrictedTypes.add(producerRawType);
        }
        Set<Type> resultingTypes = unrestrictedTypes;
        if (hasTypedAnnotation(producerElement)) {
            Annotation typedAnnotation = getTypedAnnotation(producerElement);
            if (typedAnnotation != null) {
                resultingTypes = computeTypedBeanTypes(producerRawType, typedAnnotation, unrestrictedTypes, definitionErrors);
            }
        }
        Set<Type> legalTypes = keepLegalBeanTypes(resultingTypes);
        legalTypes = pruneSyntheticArrayParameterizedSupertypes(legalTypes);
        return new ExtractionResult(legalTypes, definitionErrors);
    }

    private boolean shouldAddRawProducerType(ParameterizedType producerType, Class<?> producerRawType) {
        if (producerRawType == null) {
            return false;
        }
        for (Type argument : producerType.getActualTypeArguments()) {
            if (containsWildcard(argument)) {
                return true;
            }
            if (containsTypeVariable(argument)) {
                return false;
            }
        }
        return true;
    }

    private boolean containsTypeVariable(Type type) {
        if (type instanceof TypeVariable) {
            return true;
        }
        if (type instanceof GenericArrayType) {
            return containsTypeVariable(((GenericArrayType) type).getGenericComponentType());
        }
        if (type instanceof Class && ((Class<?>) type).isArray()) {
            return containsTypeVariable(((Class<?>) type).getComponentType());
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            for (Type argument : parameterizedType.getActualTypeArguments()) {
                if (containsTypeVariable(argument)) {
                    return true;
                }
            }
            Type owner = parameterizedType.getOwnerType();
            return containsTypeVariable(owner);
        }
        if (type instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) type;
            for (Type bound : wildcardType.getUpperBounds()) {
                if (containsTypeVariable(bound)) {
                    return true;
                }
            }
            for (Type bound : wildcardType.getLowerBounds()) {
                if (containsTypeVariable(bound)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsWildcard(Type type) {
        if (type instanceof WildcardType) {
            return true;
        }
        if (type instanceof GenericArrayType) {
            return containsWildcard(((GenericArrayType) type).getGenericComponentType());
        }
        if (type instanceof Class && ((Class<?>) type).isArray()) {
            return containsWildcard(((Class<?>) type).getComponentType());
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            for (Type argument : parameterizedType.getActualTypeArguments()) {
                if (containsWildcard(argument)) {
                    return true;
                }
            }
            Type owner = parameterizedType.getOwnerType();
            return containsWildcard(owner);
        }
        return false;
    }

    private Set<Type> pruneSyntheticArrayParameterizedSupertypes(Set<Type> types) {
        Set<Type> pruned = new LinkedHashSet<>();
        for (Type type : types) {
            if (type instanceof ParameterizedType
                    && isTypeClosureSyntheticParameterizedType(type)
                    && hasArrayTypeArgument((ParameterizedType) type)) {
                continue;
            }
            pruned.add(type);
        }
        return pruned;
    }

    private boolean isTypeClosureSyntheticParameterizedType(Type type) {
        return type != null && SimpleParameterizedType.class.getName().equals(type.getClass().getName());
    }

    private boolean hasArrayTypeArgument(ParameterizedType parameterizedType) {
        for (Type argument : parameterizedType.getActualTypeArguments()) {
            if (argument instanceof GenericArrayType) {
                return true;
            }
            if (argument instanceof Class && ((Class<?>) argument).isArray()) {
                return true;
            }
        }
        return false;
    }

    private Set<Type> computeTypedBeanTypes(Class<?> beanClass, Annotation typedAnnotation, Set<Type> unrestrictedTypes, List<String> definitionErrors) {
        Set<Type> types = new LinkedHashSet<>();

        try {
            Method valueMethod = typedAnnotation.annotationType().getMethod("value");
            Class<?>[] typedClasses = (Class<?>[]) valueMethod.invoke(typedAnnotation);

            if (typedClasses.length == 0) {
                types.add(Object.class);
                return types;
            }

            for (Class<?> typedClass : typedClasses) {
                if (!typedClass.isAssignableFrom(beanClass)) {
                    definitionErrors.add("@Typed specifies type " + typedClass.getName()
                            + " which is not a type of bean class " + beanClass.getName());
                    continue;
                }
                boolean matched = false;
                for (Type unrestricted : unrestrictedTypes) {
                    Class<?> unrestrictedRaw = RawTypeExtractor.getRawType(unrestricted);
                    if (typedClass.equals(unrestrictedRaw)) {
                        types.add(unrestricted);
                        matched = true;
                    }
                }
                if (!matched) {
                    types.add(typedClass);
                }
            }
            types.add(Object.class);
            return types;
        } catch (ReflectiveOperationException | ClassCastException e) {
            definitionErrors.add("Failed to extract @Typed annotation values: " + e.getMessage());
            return types;
        }
    }

    private Set<Type> keepLegalBeanTypes(Set<Type> candidateTypes) {
        Set<Type> legalTypes = new LinkedHashSet<>();
        for (Type candidate : candidateTypes) {
            if (isLegalBeanType(candidate)) {
                legalTypes.add(candidate);
            }
        }
        return legalTypes;
    }

    private boolean isLegalBeanType(Type type) {
        if (type instanceof TypeVariable) {
            return false;
        }
        if (type instanceof WildcardType) {
            return false;
        }
        if (type instanceof GenericArrayType) {
            GenericArrayType genericArrayType = (GenericArrayType) type;
            return isLegalBeanType(genericArrayType.getGenericComponentType());
        }
        if (type instanceof Class) {
            Class<?> klass = (Class<?>) type;
            if (klass.isArray()) {
                return isLegalBeanType(klass.getComponentType());
            }
            return true;
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            for (Type typeArgument : parameterizedType.getActualTypeArguments()) {
                if (typeArgument instanceof WildcardType) {
                    return false;
                }
                if (typeArgument instanceof GenericArrayType) {
                    if (!isLegalBeanType(((GenericArrayType) typeArgument).getGenericComponentType())) {
                        return false;
                    }
                    continue;
                }
                if (typeArgument instanceof Class && ((Class<?>) typeArgument).isArray()) {
                    if (!isLegalBeanType(((Class<?>) typeArgument).getComponentType())) {
                        return false;
                    }
                    continue;
                }
                if (typeArgument instanceof ParameterizedType) {
                    if (!isLegalBeanType(typeArgument)) {
                        return false;
                    }
                    continue;
                }
                if (typeArgument instanceof TypeVariable) {
                    continue;
                }
                if (!(typeArgument instanceof Class)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Immutable extraction result.
     */
    public static final class ExtractionResult {
        private final Set<Type> types;
        private final List<String> definitionErrors;

        public ExtractionResult(Set<Type> types, List<String> definitionErrors) {
            this.types = Collections.unmodifiableSet(new LinkedHashSet<>(types));
            this.definitionErrors = Collections.unmodifiableList(new ArrayList<>(definitionErrors));
        }

        public Set<Type> getTypes() {
            return types;
        }

        public List<String> getDefinitionErrors() {
            return definitionErrors;
        }

        public boolean hasDefinitionErrors() {
            return !definitionErrors.isEmpty();
        }
    }
}
