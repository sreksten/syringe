package com.threeamigos.common.util.implementations.injection.resolution;

import com.threeamigos.common.util.implementations.injection.annotations.AnnotationsEnum;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.injection.util.LifecycleMethodHelper;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.spi.AlterableContext;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.*;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.util.TypeLiteral;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.*;
import java.util.function.Function;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsEnum.PRE_DESTROY;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.*;
import static com.threeamigos.common.util.implementations.injection.types.TypesHelper.getRawType;

/**
 * Generic wrapper implementing CDI {@link Instance} interface for lazy and programmatic
 * bean resolution. This class provides a reusable implementation that can work with
 * different dependency resolution strategies.
 *
 * <p>The wrapper supports all Instance operations:
 * <ul>
 *   <li>{@link #get()} - Lazily retrieves an instance</li>
 *   <li>{@link #select(Annotation...)} - Refines selection with additional qualifiers</li>
 *   <li>{@link #isAmbiguous()} - Checks if multiple implementations exist</li>
 *   <li>{@link #isUnsatisfied()} - Checks if no implementations exist</li>
 *   <li>{@link #iterator()} - Iterates over all matching implementations</li>
 *   <li>{@link #destroy(Object)} - Explicitly invokes {@link jakarta.annotation.PreDestroy} on an instance</li>
 *   <li>{@link #getHandle()} - Returns a Handle for explicit lifecycle management</li>
 *   <li>{@link #handles()} - Returns all Handles for matching beans</li>
 * </ul>
 *
 * <p>This implementation is generic and delegates actual bean resolution to a
 * {@link ResolutionStrategy} provided at construction time. This allows it to work
 * with both InjectorImpl and BeanResolver approaches.
 *
 * @param <T> the type of instances this Instance provides
 * @author Stefano Reksten
 * @see Instance
 */
public class InstanceImpl<T> implements Instance<T>, Serializable {

    private static final long serialVersionUID = 1L;

    private final Class<T> type;
    private final Type requiredType;
    private final Collection<Annotation> qualifiers;
    private final String beanManagerId;
    private transient ResolutionStrategy<T> resolutionStrategy;
    private transient Function<Class<? extends T>, Bean<? extends T>> beanLookup;
    private transient Set<Object> trackedDependentInstances;

    @SuppressWarnings("unchecked")
    private <U extends T> Function<Class<? extends U>, Bean<? extends U>> adaptBeanLookup() {
        Function<Class<? extends T>, Bean<? extends T>> lookup = beanLookup;
        if (lookup == null) {
            return null;
        }
        return clazz -> {
            Bean<? extends T> bean = lookup.apply(clazz);
            return (Bean<? extends U>) bean;
        };
    }

    /**
     * Strategy interface for resolving beans and instances.
     * This allows InstanceWrapper to work with different resolution mechanisms.
     *
     * @param <T> the type being resolved
     */
    public interface ResolutionStrategy<T> {
        /**
         * Resolves and creates a single instance of the specified type with qualifiers.
         *
         * @param type the class to resolve
         * @param qualifiers the qualifiers to match
         * @return a fully injected instance
         * @throws Exception if resolution fails
         */
        T resolveInstance(Class<T> type, Collection<Annotation> qualifiers) throws Exception;

        /**
         * Resolves and creates a single instance using a possibly parameterized type.
         * The default implementation falls back to raw type resolution.
         */
        @SuppressWarnings("unchecked")
        default T resolveInstance(Type type, Collection<Annotation> qualifiers) throws Exception {
            return resolveInstance((Class<T>) getRawType(type), qualifiers);
        }

        /**
         * Resolves all implementation classes that match the type and qualifiers.
         *
         * @param type the type to resolve
         * @param qualifiers the qualifiers to match
         * @return collection of matching implementation classes
         * @throws Exception if resolution fails
         */
        Collection<Class<? extends T>> resolveImplementations(Class<T> type, Collection<Annotation> qualifiers) throws Exception;

        /**
         * Resolves all implementation classes using a possibly parameterized type.
         * The default implementation falls back to raw type resolution.
         */
        @SuppressWarnings("unchecked")
        default Collection<Class<? extends T>> resolveImplementations(Type type,
                                                                      Collection<Annotation> qualifiers) throws Exception {
            return resolveImplementations((Class<T>) getRawType(type), qualifiers);
        }

        /**
         * Invokes @PreDestroy lifecycle methods on the given instance.
         *
         * @param instance the instance to destroy
         * @throws InvocationTargetException if @PreDestroy method throws
         * @throws IllegalAccessException if the @PreDestroy method cannot be accessed
         */
        void invokePreDestroy(T instance) throws InvocationTargetException, IllegalAccessException;
    }

    /**
     * Creates an Instance wrapper with the specified resolution strategy.
     *
     * @param type the class of instances to provide
     * @param qualifiers the qualifiers to use for instance resolution
     * @param resolutionStrategy the strategy for resolving beans
     */
    public InstanceImpl(Class<T> type,
                 Collection<Annotation> qualifiers,
                 ResolutionStrategy<T> resolutionStrategy) {
        this(type, type, qualifiers, resolutionStrategy, null, null);
    }

    public InstanceImpl(Class<T> type,
                 Collection<Annotation> qualifiers,
                 ResolutionStrategy<T> resolutionStrategy,
                 Function<Class<? extends T>, Bean<? extends T>> beanLookup) {
        this(type, type, qualifiers, resolutionStrategy, beanLookup, null);
    }

    public InstanceImpl(Class<T> type,
                 Collection<Annotation> qualifiers,
                 ResolutionStrategy<T> resolutionStrategy,
                 Function<Class<? extends T>, Bean<? extends T>> beanLookup,
                 BeanManagerImpl owningBeanManager) {
        this(type, type, qualifiers, resolutionStrategy, beanLookup, owningBeanManager);
    }

    public InstanceImpl(Class<T> type,
                 Type requiredType,
                 Collection<Annotation> qualifiers,
                 ResolutionStrategy<T> resolutionStrategy,
                 Function<Class<? extends T>, Bean<? extends T>> beanLookup,
                 BeanManagerImpl owningBeanManager) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.requiredType = Objects.requireNonNull(requiredType, "requiredType cannot be null");
        this.qualifiers = new ArrayList<>(Objects.requireNonNull(qualifiers, "qualifiers cannot be null"));
        this.resolutionStrategy = Objects.requireNonNull(resolutionStrategy, "resolutionStrategy cannot be null");
        this.beanLookup = beanLookup;
        this.beanManagerId = owningBeanManager != null ? owningBeanManager.getBeanManagerId() : null;
        this.trackedDependentInstances = createIdentitySet();
    }

    @Override
    public T get() {
        try {
            T instance = resolveInstanceWithRequiredType();
            trackDependentResult(instance);
            return instance;
        } catch (UnsatisfiedResolutionException | AmbiguousResolutionException e) {
            throw e;
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause instanceof UnsatisfiedResolutionException) {
                throw (UnsatisfiedResolutionException) cause;
            }
            if (cause instanceof AmbiguousResolutionException) {
                throw (AmbiguousResolutionException) cause;
            }
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject " + type.getName(), e);
        }
    }

    @Override
    public Instance<T> select(Annotation... annotations) {
        validateSelectAnnotations(annotations);
        return new InstanceImpl<>(type, requiredType, mergeQualifiers(qualifiers, annotations), strategy(), beanLookup,
                BeanManagerImpl.getRegisteredBeanManager(beanManagerId));
    }

    @Override
    public <U extends T> Instance<U> select(Class<U> subtype, Annotation... annotations) {
        validateSelectAnnotations(annotations);
        @SuppressWarnings("unchecked")
        ResolutionStrategy<U> castStrategy = (ResolutionStrategy<U>) strategy();
        return new InstanceImpl<>(subtype, subtype, mergeQualifiers(qualifiers, annotations), castStrategy, adaptBeanLookup(),
                BeanManagerImpl.getRegisteredBeanManager(beanManagerId));
    }

    @Override
    public <U extends T> Instance<U> select(TypeLiteral<U> subtype, Annotation... annotations) {
        validateSelectAnnotations(annotations);
        // Extract the raw class from the TypeLiteral to maintain compatibility
        @SuppressWarnings("unchecked")
        Class<U> rawType = (Class<U>) getRawType(subtype.getType());
        @SuppressWarnings("unchecked")
        ResolutionStrategy<U> castStrategy = (ResolutionStrategy<U>) strategy();
        return new InstanceImpl<>(rawType, subtype.getType(), mergeQualifiers(qualifiers, annotations), castStrategy, adaptBeanLookup(),
                BeanManagerImpl.getRegisteredBeanManager(beanManagerId));
    }

    @Override
    public boolean isUnsatisfied() {
        try {
            return resolveImplementationsWithRequiredType().isEmpty();
        } catch (Exception e) {
            return true; // treating an Exception as unsatisfied
        }
    }

    @Override
    public boolean isAmbiguous() {
        try {
            return resolveImplementationsWithRequiredType().size() > 1;
        } catch (Exception e) {
            return false; // If we can't resolve the class, it's not ambiguous (it's unsatisfied)
        }
    }

    @Override
    public void destroy(T instance) {
        Objects.requireNonNull(instance, "instance cannot be null");
        try {
            trackedDependentInstances().remove(instance);
            if (!destroyViaBeanManager(instance)) {
                strategy().invokePreDestroy(instance);
            }
        } catch (UnsupportedOperationException e) {
            throw e;
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause instanceof UnsupportedOperationException) {
                throw (UnsupportedOperationException) cause;
            }
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke @PreDestroy on " + type.getName(), e);
        }
    }

    public void close() {
        destroyTrackedDependentInstances();
    }

    @SuppressWarnings("unchecked")
    public void destroyTrackedDependentInstances() {
        Set<Object> tracked = trackedDependentInstances();
        if (tracked.isEmpty()) {
            return;
        }

        List<Object> snapshot = new ArrayList<>(tracked);
        tracked.clear();
        for (Object instance : snapshot) {
            if (instance == null) {
                continue;
            }
            try {
                destroy((T) instance);
            } catch (RuntimeException ignored) {
                // Best-effort cleanup of observer-scoped programmatic lookups.
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean destroyViaBeanManager(T instance) {
        BeanManagerImpl beanManager = BeanManagerImpl.getRegisteredBeanManager(beanManagerId);
        if (beanManager == null) {
            return false;
        }

        try {
            if (beanManager.destroyOwnedTransientReference(instance)) {
                return true;
            }

            Annotation[] qualifierArray = qualifiers.toArray(new Annotation[0]);
            Set<Bean<?>> beans = beanManager.getBeans(requiredType, qualifierArray);
            if (beans == null || beans.isEmpty()) {
                return false;
            }

            Bean<?> resolved = beanManager.resolve(beans);
            if (resolved == null) {
                return false;
            }

            Class<? extends Annotation> scope = resolved.getScope();
            if (scope != null && beanManager.isNormalScope(scope)) {
                Context context = beanManager.getContext(scope);
                if (!(context instanceof AlterableContext)) {
                    throw new UnsupportedOperationException(
                            "Cannot destroy instance of normal-scoped bean " + resolved.getBeanClass().getName() +
                                    " because context @" + scope.getSimpleName() +
                                    " does not support AlterableContext.destroy(Contextual)");
                }
                ((AlterableContext) context).destroy(resolved);
                return true;
            }

            CreationalContext creationalContext = beanManager.createCreationalContext((Contextual) resolved);
            ((Bean) resolved).destroy(instance, creationalContext);
            return true;
        } catch (UnsupportedOperationException e) {
            throw e;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public Handle<T> getHandle() {
        // Per CDI spec: get the single bean or throw exception if ambiguous/unsatisfied
        try {
            Collection<Class<? extends T>> implementations = resolveImplementationsWithRequiredType();
            

            if (implementations.isEmpty()) {
                throw new UnsatisfiedResolutionException(
                    "No bean found for type " + type.getName() + " with qualifiers " + qualifiers);
            }

            if (implementations.size() > 1) {
                throw new AmbiguousResolutionException(
                    "Multiple beans found for type " + type.getName() + " with qualifiers " + qualifiers);
            }

            Class<? extends T> resolvedClass = implementations.iterator().next();
            return createHandle(resolvedClass);

        } catch (UnsatisfiedResolutionException | AmbiguousResolutionException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get handle for " + type.getName(), e);
        }
    }

    @Override
    public Iterable<? extends Handle<T>> handles() {
        // Per CDI spec this iterable is stateless: each iterator() call creates a fresh handle set.
        return new Iterable<Handle<T>>() {
            @Override
            public @Nonnull Iterator<Handle<T>> iterator() {
                try {
                    Collection<Class<? extends T>> implementations = resolveImplementationsWithRequiredType();

                    List<Handle<T>> handleList = new ArrayList<>();
                    for (Class<? extends T> implClass : implementations) {
                        handleList.add(createHandle(implClass));
                    }
                    return handleList.iterator();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to get handles for " + type.getName(), e);
                }
            }
        };
    }

    /**
     * Creates a Handle implementation for a specific bean class.
     * The Handle provides lazy access to the bean instance and lifecycle management.
     */
    private Handle<T> createHandle(Class<? extends T> beanClass) {
        return new Handle<T>() {
            private T instance;
            private boolean destroyed = false;
            private Bean<T> handleBean;
            private CreationalContext<T> handleCreationalContext;

            @Override
            public T get() {
                if (destroyed) {
                    throw new IllegalStateException("Handle has been destroyed for " + beanClass.getName());
                }

                // Lazy initialization - create instance only when first needed
                if (instance == null) {
                    try {
                        @SuppressWarnings("unchecked")
                        Class<T> classToResolve = (Class<T>) beanClass;
                        Bean<T> resolvedBean = null;
                        Function<Class<? extends T>, Bean<? extends T>> lookup = InstanceImpl.this.beanLookup;
                        if (lookup != null) {
                            @SuppressWarnings("unchecked")
                            Bean<T> lookupBean = (Bean<T>) lookup.apply(beanClass);
                            resolvedBean = lookupBean;
                        }

                        BeanManagerImpl beanManager = BeanManagerImpl.getRegisteredBeanManager(beanManagerId);
                        if (resolvedBean != null &&
                                beanManager != null &&
                                (resolvedBean.getScope() == null ||
                                        hasDependentAnnotation(resolvedBean.getScope()))) {
                            CreationalContext<T> creationalContext = beanManager.createCreationalContext(resolvedBean);
                            @SuppressWarnings("unchecked")
                            T resolvedInstance = (T) beanManager.getReference(resolvedBean, classToResolve, creationalContext);
                            instance = resolvedInstance;
                            handleBean = resolvedBean;
                            handleCreationalContext = creationalContext;
                        } else {
                            instance = strategy().resolveInstance(classToResolve, qualifiers);
                            handleBean = resolvedBean;
                            handleCreationalContext = null;
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to create instance of " + beanClass.getName(), e);
                    }
                }

                return instance;
            }

            @Override
            public Bean<T> getBean() {
                Function<Class<? extends T>, Bean<? extends T>> lookup = beanLookup;
                if (lookup != null) {
                    @SuppressWarnings("unchecked")
                    Bean<T> bean = (Bean<T>) lookup.apply(beanClass);
                    if (bean != null) {
                        return bean;
                    }
                }

                // Fallback: synthesize a lightweight BeanImpl with available metadata
                @SuppressWarnings("unchecked")
                BeanImpl<T> fallback = new BeanImpl<>((Class<T>) beanClass, false);
                if (!qualifiers.isEmpty()) {
                    fallback.setQualifiers(new HashSet<>(qualifiers));
                }

                Set<Type> types = new HashSet<>();
                types.add(beanClass);
                fallback.setTypes(types);
                return fallback;
            }

            @Override
            public void destroy() {
                if (!destroyed && instance != null) {
                    try {
                        Bean<T> bean = handleBean;
                        if (bean != null) {
                            bean.destroy(instance, handleCreationalContext);
                        } else {
                            strategy().invokePreDestroy(instance);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to destroy instance of " + beanClass.getName(), e);
                    }
                    instance = null;
                    handleBean = null;
                    handleCreationalContext = null;
                    destroyed = true;
                }
                // Per spec: multiple calls to destroy() are no-ops
            }

            @Override
            public void close() {
                // Per spec: close() delegates to destroy()
                destroy();
            }
        };
    }

    @Override
    public @Nonnull Iterator<T> iterator() {
        try {
            BeanManager beanManager = BeanManagerImpl.getRegisteredBeanManager(beanManagerId);
            if (beanManager != null) {
                Annotation[] qualifierArray = qualifiers.toArray(new Annotation[0]);
                Set<Bean<?>> beans = beanManager.getBeans(requiredType, qualifierArray);
                Collection<Bean<?>> iteratorBeans = applyIteratorAmbiguityElimination(beans);
                @SuppressWarnings("unchecked")
                Class<T> referenceType = (Class<T>) getRawType(requiredType);
                List<T> instances = new ArrayList<>();
                for (Bean<?> bean : iteratorBeans) {
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    CreationalContext<Object> creationalContext =
                            (CreationalContext<Object>) beanManager.createCreationalContext((Bean) bean);
                    @SuppressWarnings({"unchecked"})
                    T instance = (T) beanManager.getReference(bean, referenceType, creationalContext);
                    trackDependentResult(instance);
                    instances.add(instance);
                }
                return instances.iterator();
            }

            Collection<Class<? extends T>> classes = resolveImplementationsWithRequiredType();

            List<T> instances = new ArrayList<>();
            for (Class<? extends T> clazz : classes) {
                @SuppressWarnings("unchecked")
                Class<T> castedClass = (Class<T>) clazz;
                instances.add(strategy().resolveInstance(castedClass, qualifiers));
            }
            return instances.iterator();
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve implementations", e);
        }
    }

    /**
     * Applies CDI Instance iterator()/stream() ambiguity elimination rules:
     * - Unsatisfied => empty set
     * - Ambiguous and alternatives present => eliminate non-alternatives
     * - If remaining alternatives all declare explicit priority => keep highest-priority subset
     */
    private Collection<Bean<?>> applyIteratorAmbiguityElimination(Collection<Bean<?>> candidates) {
        if (candidates == null || candidates.size() <= 1) {
            return candidates == null ? Collections.emptyList() : candidates;
        }

        boolean hasAlternative = false;
        for (Bean<?> candidate : candidates) {
            if (candidate != null && candidate.isAlternative()) {
                hasAlternative = true;
                break;
            }
        }
        if (!hasAlternative) {
            return candidates;
        }

        List<Bean<?>> remaining = new ArrayList<>();
        for (Bean<?> candidate : candidates) {
            if (candidate != null && candidate.isAlternative()) {
                remaining.add(candidate);
            }
        }
        if (remaining.size() <= 1) {
            return remaining;
        }

        Map<Bean<?>, Integer> priorities = new IdentityHashMap<>();
        boolean allHaveExplicitPriority = true;
        for (Bean<?> bean : remaining) {
            Integer priority = getExplicitAlternativePriority(bean);
            priorities.put(bean, priority);
            if (priority == null) {
                allHaveExplicitPriority = false;
            }
        }
        if (!allHaveExplicitPriority) {
            return remaining;
        }

        int highestPriority = Integer.MIN_VALUE;
        for (Integer priority : priorities.values()) {
            if (priority != null && priority > highestPriority) {
                highestPriority = priority;
            }
        }

        List<Bean<?>> highestPriorityBeans = new ArrayList<>();
        for (Bean<?> bean : remaining) {
            Integer priority = priorities.get(bean);
            if (priority != null && priority == highestPriority) {
                highestPriorityBeans.add(bean);
            }
        }
        return highestPriorityBeans;
    }

    private Integer getExplicitAlternativePriority(Bean<?> bean) {
        if (bean instanceof ProducerBean) {
            ProducerBean<?> producerBean = (ProducerBean<?>) bean;
            Integer memberPriority = extractPriorityFromProducerMember(producerBean);
            if (memberPriority != null) {
                return memberPriority;
            }

            Integer producerPriority = producerBean.getPriority();
            if (producerPriority != null) {
                return producerPriority;
            }

            return extractPriorityFromClass(producerBean.getDeclaringClass());
        }

        if (bean instanceof BeanImpl) {
            Integer beanPriority = ((BeanImpl<?>) bean).getPriority();
            if (beanPriority != null) {
                return beanPriority;
            }
        }

        return extractPriorityFromClass(bean.getBeanClass());
    }

    private Integer extractPriorityFromProducerMember(ProducerBean<?> producerBean) {
        if (producerBean.getProducerMethod() != null) {
            return extractPriorityFromAnnotations(producerBean.getProducerMethod().getAnnotations());
        }
        if (producerBean.getProducerField() != null) {
            return extractPriorityFromAnnotations(producerBean.getProducerField().getAnnotations());
        }
        return null;
    }

    private Integer extractPriorityFromClass(Class<?> beanClass) {
        if (beanClass == null) {
            return null;
        }
        return extractPriorityFromAnnotations(beanClass.getAnnotations());
    }

    private Integer extractPriorityFromAnnotations(Annotation[] annotations) {
        if (annotations == null) {
            return null;
        }
        for (Annotation annotation : annotations) {
            if (AnnotationsEnum.PRIORITY
                    .matches(annotation.annotationType())) {
                try {
                    Method valueMethod = annotation.annotationType().getMethod("value");
                    Object value = valueMethod.invoke(annotation);
                    if (value instanceof Integer) {
                        return (Integer) value;
                    }
                } catch (ReflectiveOperationException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private T resolveInstanceWithRequiredType() throws Exception {
        if (requiredType instanceof Class<?>) {
            return strategy().resolveInstance((Class<T>) requiredType, qualifiers);
        }
        return strategy().resolveInstance(requiredType, qualifiers);
    }

    @SuppressWarnings("unchecked")
    private Collection<Class<? extends T>> resolveImplementationsWithRequiredType() throws Exception {
        if (requiredType instanceof Class<?>) {
            return strategy().resolveImplementations((Class<T>) requiredType, qualifiers);
        }
        return strategy().resolveImplementations(requiredType, qualifiers);
    }

    /**
     * Merges qualifier annotations, giving precedence to new annotations. This is used when
     * {@link Instance#select(Annotation...)} is called to refine the qualifier set.
     *
     * <p>Merge Rules:
     * <ul>
     *   <li>New annotations override existing ones of the same type</li>
     *   <li>Existing qualifiers are preserved unless replaced by same-type annotations</li>
     *   <li>Returns the existing collection unchanged if no new annotations are provided</li>
     * </ul>
     *
     * @param existing the existing qualifier annotations
     * @param newAnnotations new qualifier annotations to add/override
     * @return merged collection of qualifiers
     */
    private Collection<Annotation> mergeQualifiers(Collection<Annotation> existing, Annotation... newAnnotations) {
        if (newAnnotations == null || newAnnotations.length == 0) {
            return existing;
        }

        List<Annotation> merged = new ArrayList<>();
        boolean hasDefault = false;
        boolean hasAny = false;
        if (existing != null) {
            merged.addAll(existing);
            for (Annotation qualifier : existing) {
                if (qualifier == null) {
                    continue;
                }
                Class<? extends Annotation> qualifierType = qualifier.annotationType();
                if (hasDefaultAnnotation(qualifierType)) {
                    hasDefault = true;
                }
                if (hasAnyAnnotation(qualifierType)) {
                    hasAny = true;
                }
            }
        }

        Map<Class<? extends Annotation>, Annotation> replacements = new LinkedHashMap<>();
        Set<Class<? extends Annotation>> replacedNonRepeatableTypes = new HashSet<>();
        boolean hasExplicitAdditionalQualifier = false;

        for (Annotation annotation : newAnnotations) {
            if (annotation == null) {
                continue;
            }
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (!hasDefaultAnnotation(annotationType)
                    && !hasAnyAnnotation(annotationType)) {
                hasExplicitAdditionalQualifier = true;
            }
            if (hasRepeatableAnnotation(annotationType)) {
                if (!merged.contains(annotation)) {
                    merged.add(annotation);
                }
                continue;
            }
            replacements.put(annotationType, annotation);
            replacedNonRepeatableTypes.add(annotationType);
        }

        if (!replacedNonRepeatableTypes.isEmpty()) {
            merged.removeIf(existingQualifier ->
                    existingQualifier != null &&
                            replacedNonRepeatableTypes.contains(existingQualifier.annotationType()) &&
                            !hasRepeatableAnnotation(existingQualifier.annotationType()));
        }

        // BeanManager#createInstance() starts from @Default + @Any; when explicit qualifiers are added
        // through select(...), keep @Any but drop inherited @Default to avoid over-constraining resolution.
        if (hasDefault && hasAny && hasExplicitAdditionalQualifier) {
            merged.removeIf(existingQualifier -> existingQualifier != null &&
                    hasDefaultAnnotation(existingQualifier.annotationType()));
        }

        merged.addAll(replacements.values());
        return merged;
    }

    private void validateSelectAnnotations(Annotation... annotations) {
        if (annotations == null || annotations.length == 0) {
            return;
        }

        Map<Class<? extends Annotation>, Integer> counts = new HashMap<>();
        for (Annotation annotation : annotations) {
            if (annotation == null) {
                throw new IllegalArgumentException("select() qualifier cannot be null");
            }

            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (!isQualifierType(annotationType)) {
                throw new IllegalArgumentException(
                        "Annotation @" + annotationType.getName() + " is not a qualifier type");
            }

            int count = counts.getOrDefault(annotationType, 0) + 1;
            counts.put(annotationType, count);
            if (count > 1 && !hasRepeatableAnnotation(annotationType)) {
                throw new IllegalArgumentException(
                        "Duplicate non-repeating qualifier type passed to select(): @" + annotationType.getName());
            }
        }
    }

    private boolean isQualifierType(Class<? extends Annotation> annotationType) {
        if (annotationType == null) {
            return false;
        }
        if (hasQualifierAnnotation(annotationType)) {
            return true;
        }

        BeanManagerImpl beanManager = resolveBeanManagerForQualifierValidation(annotationType);
        return beanManager != null && beanManager.isQualifier(annotationType);
    }

    private BeanManagerImpl resolveBeanManagerForQualifierValidation(Class<? extends Annotation> annotationType) {
        BeanManagerImpl beanManager = BeanManagerImpl.getRegisteredBeanManager(beanManagerId);
        if (beanManager == null && annotationType != null) {
            beanManager = BeanManagerImpl.getRegisteredBeanManager(annotationType.getClassLoader());
        }
        if (beanManager == null) {
            beanManager = BeanManagerImpl.getRegisteredBeanManager(type.getClassLoader());
        }
        if (beanManager == null) {
            beanManager = BeanManagerImpl.getRegisteredBeanManager(Thread.currentThread().getContextClassLoader());
        }
        if (beanManager == null) {
            beanManager = BeanManagerImpl.getRegisteredBeanManager(InstanceImpl.class.getClassLoader());
        }
        return beanManager;
    }

    private ResolutionStrategy<T> strategy() {
        ResolutionStrategy<T> current = resolutionStrategy;
        if (current == null) {
            initializeTransientState();
            current = resolutionStrategy;
        }
        if (current == null) {
            throw new IllegalStateException("Cannot resolve Instance strategy for type " + type.getName());
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private void initializeTransientState() {
        BeanManagerImpl beanManager = BeanManagerImpl.getRegisteredBeanManager(beanManagerId);
        if (beanManager == null) {
            beanManager = BeanManagerImpl.getRegisteredBeanManager(type.getClassLoader());
        }
        if (beanManager == null) {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            beanManager = BeanManagerImpl.getRegisteredBeanManager(contextClassLoader);
        }
        if (beanManager == null) {
            beanManager = BeanManagerImpl.getRegisteredBeanManager(InstanceImpl.class.getClassLoader());
        }
        if (beanManager == null) {
            throw new IllegalStateException(
                    "Cannot restore Instance for " + type.getName() + ": no BeanManager registered for classloader");
        }
        BeanManagerImpl resolvedBeanManager = beanManager;

        resolutionStrategy = new ResolutionStrategy<T>() {
            @Override
            public T resolveInstance(Class<T> typeToResolve, Collection<Annotation> quals) {
                Annotation[] qualArray = quals.toArray(new Annotation[0]);
                return (T) resolvedBeanManager.createInstance().select((Class<?>) typeToResolve, qualArray).get();
            }

            @Override
            public Collection<Class<? extends T>> resolveImplementations(Class<T> typeToResolve, Collection<Annotation> quals) {
                Annotation[] qualArray = quals.toArray(new Annotation[0]);
                Set<Bean<?>> beans = resolvedBeanManager.getBeans(typeToResolve, qualArray);
                List<Class<? extends T>> implementations = new ArrayList<>();
                for (Bean<?> bean : beans) {
                    implementations.add((Class<? extends T>) bean.getBeanClass());
                }
                return implementations;
            }

            @Override
            public void invokePreDestroy(T instance) throws InvocationTargetException, IllegalAccessException {
                LifecycleMethodHelper.invokeLifecycleMethod(instance, PRE_DESTROY);
            }
        };

        beanLookup = beanClass -> {
            Set<Bean<?>> beans = resolvedBeanManager.getBeans(beanClass);
            for (Bean<?> bean : beans) {
                if (beanClass.equals(bean.getBeanClass())) {
                    return (Bean<? extends T>) bean;
                }
            }
            return null;
        };
    }

    private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
        trackedDependentInstances = createIdentitySet();
    }

    private void trackDependentResult(T instance) {
        if (instance == null) {
            return;
        }
        BeanManager beanManager = BeanManagerImpl.getRegisteredBeanManager(beanManagerId);
        if (beanManager == null) {
            return;
        }

        try {
            Annotation[] qualifierArray = qualifiers.toArray(new Annotation[0]);
            Set<Bean<?>> beans = beanManager.getBeans(requiredType, qualifierArray);
            if (beans == null || beans.isEmpty()) {
                return;
            }
            Bean<?> resolved = beanManager.resolve(beans);
            if (resolved == null || resolved.getScope() == null) {
                return;
            }
            if (!hasDependentAnnotation(resolved.getScope())) {
                return;
            }
            trackedDependentInstances().add(instance);
        } catch (Throwable ignored) {
            // Tracking is best-effort only.
        }
    }

    private Set<Object> trackedDependentInstances() {
        if (trackedDependentInstances == null) {
            trackedDependentInstances = createIdentitySet();
        }
        return trackedDependentInstances;
    }

    private Set<Object> createIdentitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }
}
