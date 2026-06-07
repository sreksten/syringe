package com.threeamigos.common.util.implementations.injection.interceptors;

import com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper;
import com.threeamigos.common.util.implementations.injection.knowledgebase.InterceptorInfo;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import jakarta.enterprise.inject.spi.InterceptionType;
import jakarta.enterprise.inject.spi.AnnotatedType;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.hasActivateRequestContextAnnotation;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.hasInterceptorBindingAnnotation;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.isInterceptorBindingMetaAnnotation;

/**
 * Resolves interceptors for target beans, methods, and constructors.
 *
 * <p>This class is responsible for determining which interceptors should be applied to a given
 * bean or method based on:
 * <ul>
 *   <li>Interceptor bindings present on the target class/method</li>
 *   <li>Interceptor bindings inherited from stereotypes</li>
 *   <li>The type of interception (AROUND_INVOKE, AROUND_CONSTRUCT, POST_CONSTRUCT, PRE_DESTROY)</li>
 *   <li>Priority ordering</li>
 * </ul>
 *
 * <p><b>CDI 4.1 Interceptor Resolution Rules:</b>
 * <ul>
 *   <li>Class-level bindings apply to all business methods</li>
 *   <li>Method-level bindings override class-level bindings (not merge)</li>
 *   <li>The bean class inherits stereotype bindings</li>
 *   <li>An interceptor matches if ALL of its bindings are present on the target</li>
 *   <li>Interceptors are sorted by priority (lower value = earlier execution)</li>
 * </ul>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * InterceptorResolver resolver = new InterceptorResolver(knowledgeBase);
 *
 * // Resolve interceptors for a business method
 * List<InterceptorInfo> interceptors = resolver.resolve(
 *     BankAccount.class,
 *     withdrawMethod,
 *     InterceptionType.AROUND_INVOKE
 * );
 * // Returns: [TransactionalInterceptor, LoggingInterceptor, SecurityInterceptor]
 * }</pre>
 *
 * @see InterceptorInfo
 * @see KnowledgeBase
 * @see InterceptionType
 */
public class InterceptorResolver {

    private final KnowledgeBase knowledgeBase;

    /**
     * Creates an interceptor resolver.
     *
     * @param knowledgeBase the knowledge base containing interceptor metadata
     */
    public InterceptorResolver(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = Objects.requireNonNull(knowledgeBase, "knowledgeBase cannot be null");
    }

    /**
     * Resolves interceptors for a target bean class/method.
     *
     * <p>This is the primary resolution method that considers both class-level and method-level
     * interceptor bindings.
     *
     * <p><b>Resolution Strategy:</b>
     * <ol>
     *   <li>If the method is not null and has interceptor bindings → use method bindings only</li>
     *   <li>Otherwise → use class bindings (including inherited from stereotypes)</li>
     *   <li>Query KnowledgeBase for matching interceptors</li>
     *   <li>Return a sorted list (already sorted by priority)</li>
     * </ol>
     *
     * @param targetClass the bean class being intercepted
     * @param method the method being intercepted (null for constructor/lifecycle interception)
     * @param interceptionType the type of interception
     * @return sorted list of matching interceptors (can be empty, never null)
     */
    public List<InterceptorInfo> resolve(
            Class<?> targetClass,
            Method method,
            InterceptionType interceptionType) {
        return resolve(targetClass, method, null, interceptionType);
    }

    public List<InterceptorInfo> resolve(
            Class<?> targetClass,
            Method method,
            AnnotatedType<?> annotatedTypeOverride,
            InterceptionType interceptionType) {

        Objects.requireNonNull(targetClass, "targetClass cannot be null");
        Objects.requireNonNull(interceptionType, "interceptionType cannot be null");

        Set<Annotation> targetBindings = resolveBindings(targetClass, method, annotatedTypeOverride);

        // If no bindings, no interceptors apply
        if (targetBindings.isEmpty()) {
            return Collections.emptyList();
        }

        // Query KnowledgeBase (already sorted by priority)
        return knowledgeBase.getInterceptorsByBindingsAndType(interceptionType, targetBindings);
    }

    public Set<Annotation> resolveBindings(Class<?> targetClass, Method method) {
        return resolveBindings(targetClass, method, null);
    }

    public Set<Annotation> resolveBindings(Class<?> targetClass,
                                           Method method,
                                           AnnotatedType<?> annotatedTypeOverride) {
        Objects.requireNonNull(targetClass, "targetClass cannot be null");

        Set<Annotation> classBindings = extractInterceptorBindings(targetClass, targetClass, annotatedTypeOverride);
        Set<Annotation> targetBindings = method != null
                ? mergeBindings(classBindings, extractInterceptorBindings(method, targetClass, annotatedTypeOverride))
                : classBindings;
        targetBindings = filterOutInterceptorBindingMetaAnnotations(targetBindings);

        if (targetBindings.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new HashSet<>(targetBindings));
    }

    /**
     * Resolves interceptors for a target class only (no method-specific bindings).
     *
     * <p>This is used for constructor interception and lifecycle callbacks, where there
     * is no method to check for method-level bindings.
     *
     * @param targetClass the bean class
     * @param interceptionType the type of interception
     * @return sorted list of matching interceptors
     */
    public List<InterceptorInfo> resolveForClass(Class<?> targetClass, InterceptionType interceptionType) {
        return resolve(targetClass, null, interceptionType);
    }

    public List<InterceptorInfo> resolveForConstructor(
            Class<?> targetClass,
            Constructor<?> constructor,
            AnnotatedType<?> annotatedTypeOverride,
            InterceptionType interceptionType) {

        Objects.requireNonNull(targetClass, "targetClass cannot be null");
        Objects.requireNonNull(interceptionType, "interceptionType cannot be null");

        Set<Annotation> targetBindings = resolveBindingsForConstructor(targetClass, constructor, annotatedTypeOverride);

        if (targetBindings.isEmpty()) {
            return Collections.emptyList();
        }

        return knowledgeBase.getInterceptorsByBindingsAndType(interceptionType, targetBindings);
    }

    public Set<Annotation> resolveBindingsForConstructor(Class<?> targetClass,
                                                         Constructor<?> constructor,
                                                         AnnotatedType<?> annotatedTypeOverride) {
        Objects.requireNonNull(targetClass, "targetClass cannot be null");

        Set<Annotation> classBindings = extractInterceptorBindings(targetClass, targetClass, annotatedTypeOverride);
        Set<Annotation> constructorBindings = constructor != null
                ? extractInterceptorBindings(constructor, targetClass, annotatedTypeOverride)
                : Collections.emptySet();
        Set<Annotation> targetBindings = mergeBindings(classBindings, constructorBindings);
        targetBindings = filterOutInterceptorBindingMetaAnnotations(targetBindings);

        if (targetBindings.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new HashSet<>(targetBindings));
    }

    private Set<Annotation> extractInterceptorBindings(AnnotatedElement element,
                                                       Class<?> targetClass,
                                                       AnnotatedType<?> explicitOverride) {
        if (element instanceof Class<?>) {
            return extractInterceptorBindingsFromHierarchy((Class<?>) element, explicitOverride);
        }

        Annotation[] annotations = annotationsOf(element, targetClass, explicitOverride);
        if (annotations == null || annotations.length == 0) {
            return Collections.emptySet();
        }

        Map<Class<? extends Annotation>, Annotation> bindings = new LinkedHashMap<>();
        for (Annotation annotation : annotations) {
            if (isInterceptorBinding(annotation.annotationType())) {
                addBindingWithTransitives(annotation, bindings, new HashSet<>(), true);
            }
        }
        return new HashSet<>(bindings.values());
    }

    /**
     * Extracts interceptor bindings from a stereotype annotation.
     *
     * <p>Stereotypes can declare interceptor bindings, which are then inherited by beans
     * that use the stereotype.
     *
     * @param stereotypeClass the stereotype annotation class
     * @return set of interceptor bindings declared on the stereotype
     */
    private Set<Annotation> extractInterceptorBindingsFromStereotype(Class<? extends Annotation> stereotypeClass) {
        Map<Class<? extends Annotation>, Annotation> bindings = new LinkedHashMap<>();

        for (Annotation annotation : stereotypeClass.getAnnotations()) {
            if (isInterceptorBinding(annotation.annotationType())) {
                addBindingWithTransitives(annotation, bindings, new HashSet<>(), true);
            }

            // Handle nested stereotypes (stereotype on stereotype)
            if (AnnotationsHelper.hasStereotypeAnnotation(annotation.annotationType())) {
                for (Annotation nestedBinding : extractInterceptorBindingsFromStereotype(annotation.annotationType())) {
                    bindings.putIfAbsent(nestedBinding.annotationType(), nestedBinding);
                }
            }
        }

        return new HashSet<>(bindings.values());
    }

    /**
     * Collects interceptor bindings using class-level Java inheritance semantics.
     *
     * <p>Class#getAnnotations() already applies {@code @Inherited} rules and override behavior:
     * only inherited bindings are visible, and a nearer superclass declaration of the same binding
     * type hides farther ancestors. Interface annotations are not inherited.
     */
    private Set<Annotation> extractInterceptorBindingsFromHierarchy(Class<?> type,
                                                                    AnnotatedType<?> explicitOverride) {
        Map<Class<? extends Annotation>, Annotation> bindingsByType = new LinkedHashMap<>();
        if (type == null) {
            return new HashSet<>();
        }

        Annotation[] classAnnotations = annotationsOf(type, type, explicitOverride);

        // 1) Direct class-level interceptor bindings (including inherited Java @Inherited ones)
        // take precedence over bindings declared on stereotypes.
        for (Annotation annotation : classAnnotations) {
            if (isInterceptorBinding(annotation.annotationType())) {
                addBindingWithTransitives(annotation, bindingsByType, new HashSet<>(), true);
            }
        }

        // 2) Add stereotype-declared bindings only when the bean class doesn't already
        // declare the same interceptor binding type.
        for (Annotation annotation : classAnnotations) {
            if (AnnotationsHelper.hasStereotypeAnnotation(annotation.annotationType())) {
                Set<Annotation> stereotypeBindings = extractInterceptorBindingsFromStereotype(annotation.annotationType());
                for (Annotation stereotypeBinding : stereotypeBindings) {
                    if (!bindingsByType.containsKey(stereotypeBinding.annotationType())) {
                        bindingsByType.put(stereotypeBinding.annotationType(), stereotypeBinding);
                    }
                }
            }
        }

        return new HashSet<>(bindingsByType.values());
    }

    private Annotation[] annotationsOf(AnnotatedElement element,
                                       Class<?> targetClass,
                                       AnnotatedType<?> explicitOverride) {
        if (element instanceof Class<?>) {
            Class<?> clazz = (Class<?>) element;
            if (explicitOverride != null && clazz.equals(explicitOverride.getJavaClass())) {
                return explicitOverride.getAnnotations().toArray(new Annotation[0]);
            }
            AnnotatedType<?> override = knowledgeBase.getAnnotatedTypeOverride(clazz);
            if (override != null) {
                return override.getAnnotations().toArray(new Annotation[0]);
            }
            return clazz.getAnnotations();
        }

        Class<?> declaringClass = targetClass;
        if (declaringClass == null) {
            if (element instanceof Method) {
                declaringClass = ((Method) element).getDeclaringClass();
            } else if (element instanceof Constructor<?>) {
                declaringClass = ((Constructor<?>) element).getDeclaringClass();
            }
        }
        AnnotatedType<?> override = null;
        if (declaringClass != null && explicitOverride != null
                && declaringClass.equals(explicitOverride.getJavaClass())) {
            override = explicitOverride;
        } else if (declaringClass != null) {
            override = knowledgeBase.getAnnotatedTypeOverride(declaringClass);
        }
        return AnnotationsHelper.annotationsOf(override, element);
    }

    private Set<Annotation> mergeBindings(Set<Annotation> inheritedBindings, Set<Annotation> overridingBindings) {
        Map<Class<? extends Annotation>, Annotation> merged = new LinkedHashMap<>();
        for (Annotation annotation : inheritedBindings) {
            merged.put(annotation.annotationType(), annotation);
        }
        for (Annotation annotation : overridingBindings) {
            merged.put(annotation.annotationType(), annotation);
        }
        return new HashSet<>(merged.values());
    }

    private void addBindingWithTransitives(Annotation binding,
                                           Map<Class<? extends Annotation>, Annotation> sink,
                                           Set<Class<? extends Annotation>> visiting,
                                           boolean overrideExisting) {
        Class<? extends Annotation> bindingType = binding.annotationType();
        if (!isInterceptorBinding(bindingType) || isInterceptorBindingMetaAnnotation(bindingType)) {
            return;
        }

        if (overrideExisting || !sink.containsKey(bindingType)) {
            sink.put(bindingType, binding);
        }

        if (!visiting.add(bindingType)) {
            return;
        }
        try {
            for (Annotation metaAnnotation : bindingType.getAnnotations()) {
                if (isInterceptorBinding(metaAnnotation.annotationType())) {
                    addBindingWithTransitives(metaAnnotation, sink, visiting, false);
                }
            }

            Set<Annotation> dynamicDefinition = knowledgeBase.getInterceptorBindingDefinition(bindingType);
            if (dynamicDefinition != null) {
                for (Annotation metaAnnotation : dynamicDefinition) {
                    if (isInterceptorBinding(metaAnnotation.annotationType())) {
                        addBindingWithTransitives(metaAnnotation, sink, visiting, false);
                    }
                }
            }
        } finally {
            visiting.remove(bindingType);
        }
    }

    /**
     * Checks if an annotation type is an interceptor binding.
     *
     * <p>An interceptor binding is an annotation annotated with @InterceptorBinding.
     *
     * @param annotationType the annotation type to check
     * @return true if the annotation is an interceptor binding
     */
    private boolean isInterceptorBinding(Class<? extends Annotation> annotationType) {
        return hasActivateRequestContextAnnotation(annotationType) ||
               hasInterceptorBindingAnnotation(annotationType) ||
               knowledgeBase.isRegisteredInterceptorBinding(annotationType);
    }

    private Set<Annotation> filterOutInterceptorBindingMetaAnnotations(Set<Annotation> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Annotation> filtered = new HashSet<>();
        for (Annotation binding : bindings) {
            if (binding != null && !isInterceptorBindingMetaAnnotation(binding.annotationType())) {
                filtered.add(binding);
            }
        }
        return filtered;
    }

    @Override
    public String toString() {
        return "InterceptorResolver{" +
                "totalInterceptors=" + knowledgeBase.getInterceptorInfos().size() +
                ", bindingTypes=" + knowledgeBase.getAllInterceptorBindingTypes().size() +
                '}';
    }
}
