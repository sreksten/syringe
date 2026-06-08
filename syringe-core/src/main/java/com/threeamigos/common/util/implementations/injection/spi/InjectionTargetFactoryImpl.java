package com.threeamigos.common.util.implementations.injection.spi;

import com.threeamigos.common.util.implementations.injection.resolution.BeanImpl;
import com.threeamigos.common.util.implementations.injection.scopes.InjectionPointImpl;
import com.threeamigos.common.util.implementations.injection.resolution.GenericTypeResolver;
import com.threeamigos.common.util.implementations.injection.spi.support.SpiSupport;
import com.threeamigos.common.util.implementations.injection.spi.support.SpiSupportLoader;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.CreationException;
import jakarta.enterprise.inject.spi.*;
import jakarta.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.*;
import static com.threeamigos.common.util.implementations.injection.util.ClassHelper.collectClassHierarchy;

/**
 * Factory for creating InjectionTarget instances.
 *
 * <p>This factory creates InjectionTarget objects that handle the lifecycle
 * of a bean instance: instantiation, dependency injection, and lifecycle callbacks.
 *
 * <p><b>Usage in Portable Extensions:</b>
 * <pre>{@code
 * public class MyExtension implements Extension {
 *     void processAnnotatedType(@Observes ProcessAnnotatedType<MyBean> event, BeanManager bm) {
 *         AnnotatedType<MyBean> type = event.getAnnotatedType();
 *         InjectionTargetFactory<MyBean> factory = bm.getInjectionTargetFactory(type);
 *
 *         // Optionally configure before creating
 *         factory.configure().add(new MyQualifierLiteral());
 *
 *         // Create injection target
 *         InjectionTarget<MyBean> it = factory.createInjectionTarget(null);
 *     }
 * }
 * }</pre>
 *
 * @param <T> the bean type
 * @author Stefano Reksten
 */
public class InjectionTargetFactoryImpl<T> implements InjectionTargetFactory<T> {

    private static final ThreadLocal<Boolean> CONTEXTUAL_PRODUCE_IN_PROGRESS = new ThreadLocal<>();

    private final AnnotatedType<T> annotatedType;
    private final BeanManager beanManager;
    private final SpiSupport spiSupport;
    private AnnotatedTypeConfigurator<T> configurator;
    private boolean injectionTargetCreated;

    /**
     * Creates an injection target factory.
     *
     * @param annotatedType the annotated type
     * @param beanManager the bean manager
     */
    public InjectionTargetFactoryImpl(AnnotatedType<T> annotatedType, BeanManager beanManager) {
        this.annotatedType = annotatedType;
        this.beanManager = beanManager;
        this.spiSupport = beanManager instanceof BeanManagerImpl
                ? ((BeanManagerImpl) beanManager).getSpiSupport()
                : SpiSupportLoader.load();
    }

    public static void beginContextualProduce() {
        CONTEXTUAL_PRODUCE_IN_PROGRESS.set(Boolean.TRUE);
    }

    public static void endContextualProduce() {
        CONTEXTUAL_PRODUCE_IN_PROGRESS.remove();
    }

    /**
     * Creates an InjectionTarget for the configured type.
     *
     * <p>The InjectionTarget handles:
     * <ul>
     *   <li>Instance creation via constructor</li>
     *   <li>Field and method injection</li>
     *   <li>@PostConstruct callbacks</li>
     *   <li>@PreDestroy callbacks</li>
     * </ul>
     *
     * @param bean the bean (can be null for synthetic beans)
     * @return the injection target
     */
    @Override
    public InjectionTarget<T> createInjectionTarget(Bean<T> bean) {
        injectionTargetCreated = true;
        // Get the configured type (if configure() was called)
        AnnotatedType<T> finalType = annotatedType;
        if (configurator != null) {
            finalType = spiSupport.completeAnnotatedTypeConfigurator(configurator);
        }

        return new InjectionTargetImpl<>(finalType, beanManager, bean);
    }

    /**
     * Returns a configurator for modifying the annotated type before creating the injection target.
     *
     * <p>This allows extensions to add/remove annotations before the InjectionTarget is created.
     *
     * @return the configurator
     */
    @Override
    public AnnotatedTypeConfigurator<T> configure() {
        if (injectionTargetCreated) {
            throw new IllegalStateException("configure() cannot be called after createInjectionTarget()");
        }
        if (configurator == null) {
            configurator = spiSupport.createAnnotatedTypeConfigurator(annotatedType);
        }
        return configurator;
    }

    /**
     * Implementation of InjectionTarget that handles bean lifecycle.
     */
    private static class InjectionTargetImpl<T> implements InjectionTarget<T> {
        private final AnnotatedType<T> annotatedType;
        private final BeanManager beanManager;
        private final Bean<T> bean;
        private final Set<Constructor<?>> injectableConstructors;
        private final Set<Field> injectableFields;
        private final Set<Method> injectableMethods;
        private final Set<InjectionPoint> injectionPoints;
        private final Map<Object, Object> wrappedInstanceTargets =
                Collections.synchronizedMap(new WeakHashMap<>());

        public InjectionTargetImpl(AnnotatedType<T> annotatedType, BeanManager beanManager, Bean<T> bean) {
            this.annotatedType = annotatedType;
            this.beanManager = beanManager;
            this.bean = bean;
            this.injectableConstructors = discoverInjectableConstructors();
            this.injectableFields = discoverInjectableFields();
            this.injectableMethods = discoverInjectableMethods();
            this.injectionPoints = discoverInjectionPoints();
        }

        /**
         * Produces a new instance by calling the constructor.
         *
         * @param ctx the creational context
         * @return the new instance
         */
        @Override
        public T produce(CreationalContext<T> ctx) {
            try {
                Class<T> javaClass = annotatedType.getJavaClass();

                // Find constructor (prefer @Inject, fallback to no-args)
                Constructor<T> constructor = findConstructor(javaClass);
                constructor.setAccessible(true);

                // Resolve constructor parameters
                Object[] args = resolveConstructorParameters(constructor, ctx);

                // Create instance
                T instance = constructor.newInstance(args);
                return applyProducedInstanceWrapping(instance, ctx);

            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                Throwable cause = e;
                if (e instanceof java.lang.reflect.InvocationTargetException) {
                    Throwable target = ((java.lang.reflect.InvocationTargetException) e).getTargetException();
                    cause = target != null ? target : e;
                }
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                if (cause instanceof Error) {
                    throw (Error) cause;
                }
                throw new CreationException("Failed to produce instance of " +
                    annotatedType.getJavaClass().getName(), cause);
            }
        }

        /**
         * Injects dependencies into an existing instance.
         *
         * @param instance the instance
         * @param ctx the creational context
         */
        @Override
        public void inject(T instance, CreationalContext<T> ctx) {
            try {
                T targetInstance = unwrapProxyTarget(instance);
                List<Class<?>> hierarchy = collectClassHierarchy(targetInstance.getClass());

                // Inject fields
            for (Class<?> clazz : hierarchy) {
                for (Field field : clazz.getDeclaredFields()) {
                    if (isInjectableField(field)) {
                        field.setAccessible(true);
                        Type resolvedFieldType = GenericTypeResolver.resolve(
                                field.getGenericType(),
                                targetInstance.getClass(),
                                field.getDeclaringClass()
                            );
                            Object value = beanManager.getInjectableReference(
                                createInjectionPoint(field, resolvedFieldType), ctx);
                            if (value == null && field.getType().isPrimitive()) {
                                value = defaultPrimitiveValue(field.getType());
                            }
                            field.set(targetInstance, value);
                        }
                    }
                }

                // Inject methods
            for (Class<?> clazz : hierarchy) {
                for (Method method : clazz.getDeclaredMethods()) {
                    if (isInjectableMethod(method)) {
                        if (isOverridden(method, targetInstance.getClass())) {
                            continue;
                        }
                        method.setAccessible(true);
                        Object[] args = resolveMethodParameters(method, ctx);
                            method.invoke(targetInstance, args);
                        }
                    }
                }

            } catch (Exception e) {
                throw new RuntimeException("Failed to inject dependencies into " +
                    instance.getClass().getName(), e);
            }
        }

        /**
         * Invokes @PostConstruct callback.
         *
         * @param instance the instance
         */
        @Override
        public void postConstruct(T instance) {
            try {
                T targetInstance = unwrapProxyTarget(instance);
                for (Method method : collectLifecycleMethods(targetInstance.getClass(), true)) {
                    method.setAccessible(true);
                    method.invoke(targetInstance);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to jakarta.enterprise.invoke @PostConstruct on " +
                    instance.getClass().getName(), e);
            }
        }

        /**
         * Invokes @PreDestroy callback.
         *
         * @param instance the instance
         */
        @Override
        public void preDestroy(T instance) {
            try {
                T targetInstance = unwrapProxyTarget(instance);
                List<Method> methods = collectLifecycleMethods(targetInstance.getClass(), false);
                Collections.reverse(methods);
                for (Method method : methods) {
                    method.setAccessible(true);
                    method.invoke(targetInstance);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to jakarta.enterprise.invoke @PreDestroy on " +
                    instance.getClass().getName(), e);
            }
        }

        /**
         * Disposes of the instance (no-op for managed beans).
         *
         * @param instance the instance
         */
        @Override
        public void dispose(T instance) {
            // For managed beans, disposal is handled via preDestroy
            // For producers, this would call the disposer method
        }

        @SuppressWarnings("unchecked")
        private T unwrapProxyTarget(T instance) {
            Object candidate = instance;
            candidate = unwrapInterceptorProxyTarget(candidate);
            candidate = resolveWrappedTarget(candidate);
            if (candidate != null && annotatedType.getJavaClass().isInstance(candidate)) {
                return (T) candidate;
            }
            return instance;
        }

        private Object unwrapInterceptorProxyTarget(Object candidate) {
            if (candidate == null) {
                return null;
            }
            try {
                Method getter = candidate.getClass().getMethod("$$_getTargetInstance");
                Object target = getter.invoke(candidate);
                return target != null ? target : candidate;
            } catch (NoSuchMethodException ignored) {
                return candidate;
            } catch (Exception ignored) {
                return candidate;
            }
        }

        private Object resolveWrappedTarget(Object instance) {
            Object current = instance;
            while (current != null) {
                Object next;
                synchronized (wrappedInstanceTargets) {
                    next = wrappedInstanceTargets.get(current);
                }
                if (next == null || next == current) {
                    return current;
                }
                current = next;
            }
            return instance;
        }

        private void trackWrappedTarget(Object wrappedInstance, Object rawTarget) {
            if (wrappedInstance == null || rawTarget == null || wrappedInstance == rawTarget) {
                return;
            }
            synchronized (wrappedInstanceTargets) {
                wrappedInstanceTargets.put(wrappedInstance, rawTarget);
            }
        }

        private T wrapWithBusinessMethodInterceptionIfRequired(T instance, CreationalContext<T> creationalContext) {
            // Only non-contextual instances (bean == null) should be enhanced here.
            // Contextual bean instances are interceptor/decorator wrapped by regular bean/context lifecycle.
            if (bean != null) {
                return instance;
            }
            Set<Annotation> additionalClassBindings = getClassLevelInterceptorBindingsFromAnnotatedType();
            if (additionalClassBindings.isEmpty() && !hasBusinessMethodInterceptors(annotatedType.getJavaClass())) {
                return instance;
            }
            InterceptionFactory<T> interceptionFactory =
                    beanManager.createInterceptionFactory(creationalContext, annotatedType.getJavaClass());
            if (!additionalClassBindings.isEmpty()) {
                AnnotatedTypeConfigurator<T> configurator = interceptionFactory.configure();
                for (Annotation binding : additionalClassBindings) {
                    configurator.add(binding);
                }
            }
            return interceptionFactory.createInterceptedInstance(instance);
        }

        private T applyProducedInstanceWrapping(T instance, CreationalContext<T> creationalContext) {
            if (instance == null) {
                return null;
            }

            // When produce() is called as part of BeanImpl#create(), BeanImpl applies wrapping
            // after full lifecycle callbacks. Avoid wrapping twice in that path.
            if (Boolean.TRUE.equals(CONTEXTUAL_PRODUCE_IN_PROGRESS.get())) {
                return instance;
            }

            if (!(bean instanceof BeanImpl<?>)) {
                return wrapWithBusinessMethodInterceptionIfRequired(instance, creationalContext);
            }

            BeanImpl<T> beanImpl = (BeanImpl<T>) bean;
            T rawTarget = instance;
            T wrapped = instance;

            if (beanImpl.hasDecorators()) {
                wrapped = beanImpl.createDecoratorChain(wrapped, creationalContext);
                trackWrappedTarget(wrapped, rawTarget);
            }

            Class<? extends Annotation> scope = beanImpl.getScope();
            boolean dependent = scope == null || Dependent.class.equals(scope);
            if (beanImpl.hasInterceptors() && dependent) {
                wrapped = beanImpl.createInterceptorAwareProxy(wrapped);
                trackWrappedTarget(wrapped, rawTarget);
            }

            return wrapped;
        }

        private Set<Annotation> getClassLevelInterceptorBindingsFromAnnotatedType() {
            Set<Annotation> bindings = new HashSet<>();
            for (Annotation annotation : annotatedType.getAnnotations()) {
                if (beanManager.isInterceptorBinding(annotation.annotationType())) {
                    bindings.add(annotation);
                }
            }
            return bindings;
        }

        private boolean hasBusinessMethodInterceptors(Class<?> beanClass) {
            if (beanClass == null) {
                return false;
            }

            for (Annotation annotation : beanClass.getAnnotations()) {
                if (beanManager.isInterceptorBinding(annotation.annotationType())) {
                    return true;
                }
            }

            for (Method method : beanClass.getMethods()) {
                for (Annotation annotation : method.getAnnotations()) {
                    if (beanManager.isInterceptorBinding(annotation.annotationType())) {
                        return true;
                    }
                }
            }

            return false;
        }

        /**
         * Returns all injection points for this target.
         *
         * @return set of injection points
         */
        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            return injectionPoints;
        }

        // Helper methods

        private Set<InjectionPoint> discoverInjectionPoints() {
            Set<InjectionPoint> points = new HashSet<>();
            Class<T> javaClass = annotatedType.getJavaClass();
            List<Class<?>> hierarchy = collectClassHierarchy(javaClass);

            // Constructor parameters
            for (Constructor<?> constructor : javaClass.getDeclaredConstructors()) {
                if (isInjectableConstructor(constructor)) {
                    for (Parameter param : constructor.getParameters()) {
                        points.add(new InjectionPointImpl<>(param, bean));
                    }
                }
            }

            // Fields
            for (Class<?> clazz : hierarchy) {
                for (Field field : clazz.getDeclaredFields()) {
                    if (isInjectableField(field)) {
                        points.add(new InjectionPointImpl<>(field, bean));
                    }
                }
            }

            // Method parameters
            for (Class<?> clazz : hierarchy) {
                for (Method method : clazz.getDeclaredMethods()) {
                    if (isInjectableMethod(method)) {
                        for (Parameter param : method.getParameters()) {
                            points.add(new InjectionPointImpl<>(param, bean));
                        }
                    }
                }
            }

            return points;
        }

        @SuppressWarnings("unchecked")
        private Constructor<T> findConstructor(Class<T> clazz) throws NoSuchMethodException {
            // Look for @Inject constructor
            for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
                if (isInjectableConstructor(ctor)) {
                    return (Constructor<T>) ctor;
                }
            }

            // Fallback to no-args constructor
            return clazz.getDeclaredConstructor();
        }

        private Object[] resolveConstructorParameters(Constructor<T> constructor, CreationalContext<T> ctx) {
            Parameter[] params = constructor.getParameters();
            Object[] args = new Object[params.length];

            for (int i = 0; i < params.length; i++) {
                Type resolvedType = GenericTypeResolver.resolve(
                        params[i].getParameterizedType(),
                        annotatedType.getJavaClass(),
                        constructor.getDeclaringClass()
                );
                InjectionPoint ip = createInjectionPoint(params[i], resolvedType);
                args[i] = beanManager.getInjectableReference(ip, ctx);
                if (args[i] == null && params[i].getType().isPrimitive()) {
                    args[i] = defaultPrimitiveValue(params[i].getType());
                }
            }

            return args;
        }

        private Object[] resolveMethodParameters(Method method, CreationalContext<T> ctx) {
            Parameter[] params = method.getParameters();
            Object[] args = new Object[params.length];

            for (int i = 0; i < params.length; i++) {
                Type resolvedType = GenericTypeResolver.resolve(
                        params[i].getParameterizedType(),
                        annotatedType.getJavaClass(),
                        method.getDeclaringClass()
                );
                InjectionPoint ip = createInjectionPoint(params[i], resolvedType);
                args[i] = beanManager.getInjectableReference(ip, ctx);
                if (args[i] == null && params[i].getType().isPrimitive()) {
                    args[i] = defaultPrimitiveValue(params[i].getType());
                }
            }

            return args;
        }

        private InjectionPoint createInjectionPoint(Field field, Type resolvedType) {
            return new ResolvedInjectionPoint(new InjectionPointImpl<>(field, bean), resolvedType);
        }

        private InjectionPoint createInjectionPoint(Parameter parameter, Type resolvedType) {
            return new ResolvedInjectionPoint(new InjectionPointImpl<>(parameter, bean), resolvedType);
        }

        private Object defaultPrimitiveValue(Class<?> primitiveType) {
            if (primitiveType == boolean.class) return false;
            if (primitiveType == byte.class) return (byte) 0;
            if (primitiveType == short.class) return (short) 0;
            if (primitiveType == int.class) return 0;
            if (primitiveType == long.class) return 0L;
            if (primitiveType == float.class) return 0f;
            if (primitiveType == double.class) return 0d;
            if (primitiveType == char.class) return '\u0000';
            return null;
        }

        private List<Method> collectLifecycleMethods(Class<?> beanClass, boolean postConstruct) {
            List<Class<?>> hierarchy = collectClassHierarchy(beanClass);
            List<Method> lifecycleMethods = new ArrayList<>();

            for (int i = 0; i < hierarchy.size(); i++) {
                Class<?> clazz = hierarchy.get(i);
                for (Method method : clazz.getDeclaredMethods()) {
                    boolean matchesLifecycle = postConstruct
                            ? hasPostConstructAnnotation(method)
                            : hasPreDestroyAnnotation(method);
                    if (!matchesLifecycle) {
                        continue;
                    }

                    if (!isOverriddenBySubclass(method, hierarchy, i + 1)) {
                        lifecycleMethods.add(method);
                    }
                }
            }

            return lifecycleMethods;
        }

        private boolean isOverriddenBySubclass(Method method, List<Class<?>> hierarchy, int startIndex) {
            if (java.lang.reflect.Modifier.isPrivate(method.getModifiers())) {
                return false;
            }

            for (int i = startIndex; i < hierarchy.size(); i++) {
                Class<?> subclass = hierarchy.get(i);
                Method candidate = findDeclaredMethod(subclass, method.getName(), method.getParameterTypes());
                if (candidate == null) {
                    continue;
                }

                if (java.lang.reflect.Modifier.isStatic(candidate.getModifiers())) {
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
            if (java.lang.reflect.Modifier.isPublic(modifiers) || java.lang.reflect.Modifier.isProtected(modifiers)) {
                return true;
            }
            if (java.lang.reflect.Modifier.isPrivate(modifiers)) {
                return false;
            }
            return packageName(method.getDeclaringClass()).equals(packageName(subclass));
        }

        private String packageName(Class<?> type) {
            Package pkg = type.getPackage();
            return pkg == null ? "" : pkg.getName();
        }

        private boolean isOverridden(Method superMethod, Class<?> leafClass) {
            if (java.lang.reflect.Modifier.isPrivate(superMethod.getModifiers())) {
                return false;
            }
            if (superMethod.getDeclaringClass().equals(leafClass)) {
                return false;
            }

            Class<?> current = leafClass;
            while (current != null && current != superMethod.getDeclaringClass()) {
                Method subMethod = findDeclaredMethod(current, superMethod.getName(), superMethod.getParameterTypes());
                if (subMethod != null && !subMethod.equals(superMethod)) {
                    if (java.lang.reflect.Modifier.isStatic(subMethod.getModifiers())) {
                        current = current.getSuperclass();
                        continue;
                    }

                    int superModifiers = superMethod.getModifiers();
                    boolean superPackagePrivate = !java.lang.reflect.Modifier.isPublic(superModifiers) &&
                            !java.lang.reflect.Modifier.isProtected(superModifiers) &&
                            !java.lang.reflect.Modifier.isPrivate(superModifiers);

                    if (superPackagePrivate) {
                        return packageName(superMethod.getDeclaringClass())
                                .equals(packageName(subMethod.getDeclaringClass()));
                    }

                    return true;
                }
                current = current.getSuperclass();
            }

            return false;
        }

        private Set<Constructor<?>> discoverInjectableConstructors() {
            Set<Constructor<?>> constructors = new LinkedHashSet<>();
            for (AnnotatedConstructor<T> constructor : annotatedType.getConstructors()) {
                if (hasInjectAnnotation(constructor.getAnnotations())) {
                    constructors.add(constructor.getJavaMember());
                }
            }
            return constructors;
        }

        private Set<Field> discoverInjectableFields() {
            Set<Field> fields = new LinkedHashSet<>();
            for (AnnotatedField<? super T> field : annotatedType.getFields()) {
                if (hasInjectAnnotation(field.getAnnotations())) {
                    fields.add(field.getJavaMember());
                }
            }
            return fields;
        }

        private Set<Method> discoverInjectableMethods() {
            Set<Method> methods = new LinkedHashSet<>();
            for (AnnotatedMethod<? super T> method : annotatedType.getMethods()) {
                if (hasInjectAnnotation(method.getAnnotations())) {
                    methods.add(method.getJavaMember());
                }
            }
            return methods;
        }

        private boolean isInjectableConstructor(Constructor<?> constructor) {
            return injectableConstructors.contains(constructor) || hasInjectAnnotation(constructor);
        }

        private boolean isInjectableField(Field field) {
            return injectableFields.contains(field) || hasInjectAnnotation(field);
        }

        private boolean isInjectableMethod(Method method) {
            return injectableMethods.contains(method) || hasInjectAnnotation(method);
        }

        private static final class ResolvedInjectionPoint implements InjectionPoint, java.io.Serializable {
            private static final long serialVersionUID = 1L;
            private final InjectionPoint delegate;
            private final Type resolvedType;

            private ResolvedInjectionPoint(InjectionPoint delegate, Type resolvedType) {
                this.delegate = delegate;
                this.resolvedType = resolvedType;
            }

            @Override
            public Type getType() {
                return resolvedType != null ? resolvedType : delegate.getType();
            }

            @Override
            public Set<Annotation> getQualifiers() {
                return delegate.getQualifiers();
            }

            @Override
            public Bean<?> getBean() {
                return delegate.getBean();
            }

            @Override
            public Member getMember() {
                return delegate.getMember();
            }

            @Override
            public Annotated getAnnotated() {
                return delegate.getAnnotated();
            }

            @Override
            public boolean isDelegate() {
                return delegate.isDelegate();
            }

            @Override
            public boolean isTransient() {
                return delegate.isTransient();
            }
        }
    }
}
