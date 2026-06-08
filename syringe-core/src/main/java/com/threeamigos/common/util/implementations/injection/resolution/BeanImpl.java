package com.threeamigos.common.util.implementations.injection.resolution;

import com.threeamigos.common.util.implementations.injection.annotations.AnnotationsEnum;
import com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper;
import com.threeamigos.common.util.implementations.injection.decorators.DecoratorSupport;
import com.threeamigos.common.util.implementations.injection.interceptors.InterceptorChainModel;
import com.threeamigos.common.util.implementations.injection.interceptors.InterceptorSupport;
import com.threeamigos.common.util.implementations.injection.knowledgebase.InterceptorInfo;
import com.threeamigos.common.util.implementations.injection.knowledgebase.DecoratorInfo;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.injection.spi.InjectionTargetFactoryImpl;
import com.threeamigos.common.util.implementations.injection.scopes.ContextManager;
import com.threeamigos.common.util.implementations.injection.scopes.InjectionPointImpl;
import com.threeamigos.common.util.implementations.injection.scopes.ScopeContext;
import com.threeamigos.common.util.implementations.injection.util.LifecycleMethodHelper;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.CreationException;
import jakarta.enterprise.inject.spi.*;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.hasAroundInvokeAnnotation;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.hasExcludeClassInterceptorsAnnotation;
import static com.threeamigos.common.util.implementations.injection.util.ClassHelper.collectClassHierarchyFromObject;
import static com.threeamigos.common.util.implementations.injection.util.ClassHelper.packageName;
import static com.threeamigos.common.util.implementations.injection.util.TypesHelper.defaultPrimitiveValue;

/**
 * CDI 4.1 - 2 - A <i>bean</i> is a source of contextual objects that define application state and/or logic.
 * These objects are called <i>contextual instances</i> of the bean. The container creates and destroys these instances
 * and associates them with the appropriate context. Contextual instances of a bean may be injected into other objects
 * (including other bean instances) that execute in the same context. A bean may bear metadata defining its lifecycle
 * and interactions with other beans.
 * <p>A bean comprises the following attributes:
 * <ul>
 * <li>A (nonempty) set of bean types</li>
 * <li>A (nonempty) set of qualifiers</li>
 * <li>A scope</li>
 * <li>Optionally, a bean name</li>
 * <li>A set of interceptor bindings</li>
 * <li>A bean implementation</li>
 * </ul>
 * Furthermore, a bean may or may not be an alternative.
 * @param <T> type of the bean
 */
public class BeanImpl<T> implements Bean<T>, PassivationCapable, Serializable {
    private static final long serialVersionUID = 1L;
    private static final ThreadLocal<Deque<InjectionPoint>> INJECTION_POINT_CONTEXT =
            ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * CDI 4.1 - 2.2 - A bean type defines a client-visible type of the bean. A bean may have multiple bean types.
     * All beans have the bean type java.lang.Object. Almost any Java type may be a bean type of a bean:
     * <ul>
     * <li>A bean type may be an interface, a concrete class or an abstract class, may be declared sealed or
     * non-sealed or final, and may have final methods.</li>
     * <li>A bean type may be a parameterized type with actual type parameters and type variables.</li>
     * <li>A bean type may be an array type. Two array types are considered identical only if the element
     * type is identical</li>
     * <li>A bean type may be a primitive type. Primitive types are considered to be identical to their
     * corresponding wrapper types in java.lang.</li>
     * <li>A bean type may be a raw type.</li>
     * </ul>
     * However, some Java types are not legal bean types:
     * <ul>
     * <li>A type variable is not a legal bean type.</li>
     * <li>A parameterized type that contains a wildcard type parameter is not a legal bean type.</li>
     * <li>An array type whose component type is not a legal bean type.</li>
     * </ul>
     * CDI 4.1 - 3.1.2 - Bean types of a managed bean:
     * <ul>
     * <li>The unrestricted set of bean types for a managed bean contains the bean class, every superclass,
     * and all interfaces it implements directly or indirectly.</li>
     * <li>The resulting set of bean types for a managed bean consists only of legal bean types, all other types
     * are removed from the set of bean types.</li>
     * </ul>
     * Note that certain additional restrictions are specified in Unproxyable bean types for beans with a
     * normal scope as per 3.10.
     */
    private final Set<Type> types = new HashSet<>();

    /**
     * CDI 4.1 - 2.3.1 - Qualifiers of a bean:
     * <ul>
     * <li>Every bean has the built-in @Any qualifier, even if it does not explicitly declare it.</li>
     * <li>Every bean has the @Named qualifier if it is a named bean.</li>
     * <li>If a bean does not explicitly declare a qualifier other than @Named or @Any, the bean has exactly one
     * additional qualifier, of type @Default. This is called the default qualifier.</li>
     * </ul>
     */
    private final Set<Annotation> qualifiers = new HashSet<>();

    /**
     * CDI 4.1 - 2.4 - Scope of the bean
     */
    private Class<? extends Annotation> scope;

    /**
     * CDI 4.1 - 2.6 - Name of the bean (optional)
     */
    private String name;

    /**
     * CDI 4.1 - 2.7 - Alternative flag.<br/>
     * To be enabled, the Alternative must declare a @Priority or be present in the META-INF/beans.xml file.
     */
    private boolean alternative;
    private final String passivationId;
    /**
     * Alternative enabled flag - NONSTANDARD feature. It permits enabling an Alternative programmatically.
     */
    private boolean alternativeEnabled;
    /**
     * Effective alternative priority, resolved from bean @Priority or stereotype @Priority.
     */
    private Integer priority;

    /**
     * CDI 4.1 - 2.8 - Stereotypes of a bean<br/>
     * In many systems, the use of architectural patterns produces a set of recurring bean roles. A stereotype
     * allows a framework developer to identify such a role and declare some common metadata for
     * beans with that role in a central place.<br/>
     * A stereotype encapsulates any combination of:
     * <ul>
     * <li>a default scope</li>
     * <li>a set of interceptor bindings</li>
     * </ul>
     * A stereotype may also specify that:
     * <ul>
     * <li>all beans with the stereotype have defaulted bean names, or</li>
     * <li>all beans with the stereotype are alternatives, or</li>
     * <li>all beans with the stereotype have predefined @Priority.</li>
     * </ul>
     * A bean may declare zero, one, or multiple stereotypes.
     */
    private final Set<Class<? extends Annotation>> stereotypes = new HashSet<>();

    /**
     * CDI 4.1 - 3.1 - Managed beans<br/>
     * A managed bean is a bean implemented by a Java class. This class is called the bean class of
     * the managed bean.
     */
    private final Class<T> beanClass;

    private final Set<InjectionPoint> injectionPoints = new HashSet<>();

    // Validation state
    private boolean hasValidationErrors = false;

    // Extension veto state
    private boolean vetoed = false;
    // ProcessBeanAttributes#ignoreFinalMethods marker
    private boolean ignoreFinalMethods = false;

    // Injection metadata (set by validator)
    private Constructor<T> injectConstructor;
    private final Set<Field> injectFields = new HashSet<>();
    private final Set<Method> injectMethods = new HashSet<>();

    // Lifecycle methods - can have multiple in hierarchy (per Interceptors spec)
    private final List<Method> postConstructMethods = new ArrayList<>();
    private final List<Method> preDestroyMethods = new ArrayList<>();

    // Passivation lifecycle methods for @SessionScoped beans
    // NOTE: @PrePassivate/@PostActivate are EJB annotations (jakarta.ejb), NOT CDI 4.1 standard.
    // CDI 4.1 relies on Java's Serializable (writeObject/readObject). This is optional support
    // for applications using both CDI and EJB. If jakarta.ejb is not on the classpath, these remain empty.
    private final List<Method> prePassivateMethods = new ArrayList<>();
    private final List<Method> postActivateMethods = new ArrayList<>();

    // Dependency resolver (set during container initialization)
    private DependencyResolver dependencyResolver;

    // ====================================================================================
    // PHASE 2: Interceptor Support - Business Method Interception (@AroundInvoke)
    // ====================================================================================

    /**
     * InterceptorSupport SPI facade for interceptor resolution/proxying/lifecycle invocation.
     * Set during container initialization.
     */
    private InterceptorSupport interceptorSupport;

    /**
     * KnowledgeBase - provides access to interceptor metadata.
     * Set during container initialization.
     */
    private KnowledgeBase knowledgeBase;

    /**
     * Map of methods to their interceptor chains.
     * Built once during bean initialization and cached for performance.
     * Key: Method object from beanClass
     * Value: Pre-built InterceptorChainModel for that method
     * Only contains entries for methods that have interceptors.
     * Methods without interceptors are not in this map (for memory efficiency).
     */
    private Map<Method, InterceptorChainModel> methodInterceptorChains;

    /**
     * PHASE 3: Constructor interceptor chain (@AroundConstruct).
     * Built once during bean initialization if the bean has constructor interceptors.
     * Null if no constructor interceptors are present.
     */
    private InterceptorChainModel constructorInterceptorChain;

    /**
     * PHASE 4: @PostConstruct interceptor chain.
     * Built once during bean initialization if the bean has @PostConstruct interceptors.
     * Null if no @PostConstruct interceptors are present.
     */
    private InterceptorChainModel postConstructInterceptorChain;

    /**
     * PHASE 4: @PreDestroy interceptor chain.
     * Built once during bean initialization if the bean has @PreDestroy interceptors.
     * Null if no @PreDestroy interceptors are present.
     */
    private InterceptorChainModel preDestroyInterceptorChain;
    private List<Method> targetClassAroundInvokeMethods = Collections.emptyList();

    /**
     * Cache of interceptor instances.
     * Key: Interceptor class
     * Value: Interceptor instance
     * Global fallback cache used when no interception target is available.
     */
    private final Map<Class<?>, Object> interceptorInstanceCache = new ConcurrentHashMap<>();
    private final Map<Object, Map<Class<?>, Object>> interceptorTargetInstanceCache =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private transient ThreadLocal<Map<Class<?>, Object>> constructionInterceptorInstanceCache =
            new ThreadLocal<>();
    private final Map<Class<?>, Bean<?>> interceptorMetadataBeanCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, InterceptorInfo> interceptorInfoByClass = new ConcurrentHashMap<>();

    // ====================================================================================
    // PHASE 3: Decorator Support - Bean-level Decoration
    // ====================================================================================

    /**
     * Decorator support SPI facade for decorator resolution/proxying.
     * Set during container initialization.
     */
    private DecoratorSupport decoratorSupport;

    /**
     * BeanManager - needed for creating decorator instances.
     * Set during container initialization by InjectorImpl2.
     */
    private BeanManager beanManager;

    /**
     * Optional InjectionTarget provided/modified by ProcessInjectionTarget extension events.
     * When present, lifecycle operations delegate to this InjectionTarget instead of the
     * internal implementation.
     */
    private InjectionTarget<T> customInjectionTarget;
    /**
     * Optional per-bean AnnotatedType metadata.
     * Used when multiple bean definitions originate from the same Java class with different
     * type-level metadata (for example, via addAnnotatedType/setAnnotatedType).
     */
    private jakarta.enterprise.inject.spi.AnnotatedType<T> annotatedTypeMetadata;

    public BeanImpl(Class<T> beanClass, boolean alternative) {
        this.beanClass = beanClass;
        this.name = null;
        this.scope = null;
        this.alternative = alternative;
        String packageName = (beanClass != null && beanClass.getPackage() != null)
                ? beanClass.getPackage().getName()
                : "unknown";
        String className = (beanClass != null) ? beanClass.getName() : "unknown";
        this.passivationId = packageName + ".BeanImpl#" + className + "#" + UUID.randomUUID();
        this.alternativeEnabled = !alternative;
        this.priority = null;
    }

    @Override
    public String getId() {
        return passivationId;
    }

    @Override
    public Class<?> getBeanClass() {
        return beanClass;
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return Collections.unmodifiableSet(injectionPoints);
    }

    public void addInjectionPoint(InjectionPoint injectionPoint) {
        injectionPoints.add(injectionPoint);
    }

    public void replaceInjectionPoint(InjectionPoint oldIp, InjectionPoint newIp) {
        if (oldIp != null) {
            injectionPoints.remove(oldIp);
        }
        if (newIp != null) {
            injectionPoints.add(newIp);
        }
    }

    public void setCustomInjectionTarget(InjectionTarget<T> injectionTarget) {
        this.customInjectionTarget = injectionTarget;
    }

    public void setAnnotatedTypeMetadata(jakarta.enterprise.inject.spi.AnnotatedType<T> annotatedTypeMetadata) {
        this.annotatedTypeMetadata = annotatedTypeMetadata;
    }

    public jakarta.enterprise.inject.spi.AnnotatedType<T> getAnnotatedTypeMetadata() {
        return annotatedTypeMetadata;
    }

    @Override
    public String getName() {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        return name;
    }

    public void setName(String name) {
        if (name == null) {
            this.name = null;
            return;
        }
        String trimmed = name.trim();
        this.name = trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    public Set<Annotation> getQualifiers() {
        return Collections.unmodifiableSet(qualifiers);
    }

    public void setQualifiers(Set<Annotation> qualifiers) {
        this.qualifiers.clear();
        if (qualifiers != null) {
            this.qualifiers.addAll(qualifiers);
        }
    }

    public void addQualifier(Annotation qualifier) {
        if (qualifier != null) {
            this.qualifiers.add(qualifier);
        }
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return scope;
    }

    public void setScope(Class<? extends Annotation> scope) {
        this.scope = scope;
    }

    @Override
    public Set<Class<? extends Annotation>> getStereotypes() {
        return Collections.unmodifiableSet(stereotypes);
    }

    public void setStereotypes(Set<Class<? extends Annotation>> stereotypes) {
        this.stereotypes.clear();
        if (stereotypes != null) {
            this.stereotypes.addAll(stereotypes);
        }
    }

    public void addStereotype(Class<? extends Annotation> stereotype) {
        if (stereotype != null) {
            this.stereotypes.add(stereotype);
        }
    }

    @Override
    public Set<Type> getTypes() {
        return Collections.unmodifiableSet(types);
    }

    public void setTypes(Set<Type> types) {
        this.types.clear();
        if (types != null) {
            this.types.addAll(types);
        }
    }

    public void addType(Type type) {
        if (type != null) {
            this.types.add(type);
        }
    }

    @Override
    public boolean isAlternative() {
        return alternative;
    }

    public void setAlternative(boolean alternative) {
        this.alternative = alternative;
    }

    public boolean isAlternativeEnabled() {
        return !alternative || alternativeEnabled;
    }

    public void setAlternativeEnabled(boolean alternativeEnabled) {
        this.alternativeEnabled = alternativeEnabled;
    }

    public boolean isIgnoreFinalMethods() {
        return ignoreFinalMethods;
    }

    public void setIgnoreFinalMethods(boolean ignoreFinalMethods) {
        this.ignoreFinalMethods = ignoreFinalMethods;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    @Override
    public T create(CreationalContext<T> creationalContext) {
        try {
            if (customInjectionTarget != null) {
                // Delegate lifecycle to custom InjectionTarget provided by extensions
                T instance;
                if (constructorInterceptorChain != null) {
                    // AroundConstruct interception must wrap bean construction.
                    // InjectionTarget.produce() hides constructor invocation, so use an internal
                    // construction path when constructor interceptors are present.
                    instance = createInstance(creationalContext);
                    customInjectionTarget.inject(instance, creationalContext);
                } else {
                    InjectionTargetFactoryImpl.beginContextualProduce();
                    try {
                        instance = customInjectionTarget.produce(creationalContext);
                    } finally {
                        InjectionTargetFactoryImpl.endContextualProduce();
                    }
                    customInjectionTarget.inject(instance, creationalContext);
                }
                invokeCustomInjectionTargetPostConstructWithRequestContext(instance);
                initializeTargetInterceptorInstances(instance);

                if (hasInterceptors() && isDependent()) {
                    instance = createDecoratorChain(instance, creationalContext);
                    instance = createInterceptorAwareProxy(instance);
                } else if (hasDecorators()) {
                    instance = createDecoratorChain(instance, creationalContext);
                } else if (hasInterceptors()) {
                    instance = createInterceptorAwareProxy(instance);
                }

                return instance;
            }

            // 1. Create an instance via constructor injection
            T instance = createInstance(creationalContext);

            // 2. Perform field injection
            performFieldInjection(instance, creationalContext);

            // 3. Perform method injection (initializer methods)
            performMethodInjection(instance, creationalContext);

            // 4. Call @PostConstruct if present (with request context active per CDI 6.6.1)
            invokePostConstructWithRequestContext(instance);
            initializeTargetInterceptorInstances(instance);

            // 5. PHASE 2 - Wrap with interceptor-aware proxy if needed
            // IMPORTANT: This wrapping is ONLY for @Dependent scoped beans!
            //
            // Normal-scoped beans (@ApplicationScoped, @RequestScoped, etc.) are wrapped by their
            // contexts (ApplicationScopedContext, RequestScopedContext, etc.) which call
            // createInterceptorAwareProxy() in their get() methods.
            //
            // @Dependent scoped beans don't go through contexts - they're created directly via
            // bean.create() and returned immediately. So we need to wrap them here.
            //
            // How to tell if this is a @Dependent bean?
            // - Check if the scope is null (CDI spec: @Dependent beans have no scope annotation)
            // - Or check if the scope is jakarta.enterprise.context.Dependent.class
            if (hasDecorators()) {
                instance = createDecoratorChain(instance, creationalContext);
            }

            // 6. PHASE 3 - Wrap with interceptor-aware proxy if needed
            // Interceptors must be outermost around decorators for business method invocations.
            if (hasInterceptors() && isDependent()) {
                instance = createInterceptorAwareProxy(instance);
            }

            return instance;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            Throwable cause = unwrapInvocationCause(e);
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new CreationException("Failed to create bean instance of " + beanClass.getName(), cause);
        }
    }

    /**
     * Checks if this bean is @Dependent scoped.
     * <p>
     * Dependent is the default scope in CDI and is represented by either:
     * - null scope (no scope annotation)
     * - jakarta.enterprise.context.Dependent.class
     * <p>
     * Dependent beans are special because they:
     * - Don't go through scope contexts (no context.get() call)
     * - Are created directly via bean.create()
     * - Have the same lifecycle as their injection point
     * - Need to be wrapped with interceptors HERE, not in contexts
     *
     * @return true if this bean is @Dependent scoped
     */
    private boolean isDependent() {
        // If scope is null, it's @Dependent (default scope)
        if (scope == null) {
            return true;
        }

        // Check if the scope is explicitly @Dependent
        return AnnotationsHelper.hasDependentAnnotation(scope);
    }

    @Override
    public void destroy(T instance, CreationalContext<T> creationalContext) {
        if (instance == null || DestroyedInstanceTracker.isDestroyed(instance)) {
            return;
        }
        creationalContext = BeanManagerImpl.resolveDependentCreationalContext(creationalContext, this, instance);

        Throwable ignored = null;
        try {
            if (customInjectionTarget != null) {
                invokeCustomInjectionTargetPreDestroyWithInterceptors(instance);
                customInjectionTarget.dispose(instance);
            } else {
                // 1. Call @PreDestroy if present
                invokePreDestroy(instance);
            }
            destroyDecorators(instance);
        } catch (Exception e) {
            ignored = e;
        } finally {
            clearTargetInterceptorInstances(instance);
            DestroyedInstanceTracker.markDestroyed(instance);
            try {
                // 2. Release CreationalContext (destroys dependent objects)
                if (creationalContext != null) {
                    creationalContext.release();
                }
            } catch (Exception e) {
                if (ignored == null) {
                    ignored = e;
                }
            }
        }
    }

    private void destroyDecorators(T instance) {
        if (instance == null || decoratorSupport == null) {
            return;
        }
        try {
            decoratorSupport.destroyDecoratorChain(instance);
        } catch (RuntimeException ignored) {
            // Bean destruction should continue even if decorator cleanup fails.
        }
    }

    private Throwable unwrapInvocationCause(Exception e) {
        if (e instanceof InvocationTargetException) {
            Throwable target = ((InvocationTargetException) e).getTargetException();
            return target != null ? target : e;
        }
        return e;
    }

    /**
     * Creates an instance via constructor injection.
     * Uses @Inject constructor if present, otherwise uses no-args constructor.
     * <p>
     * PHASE 3: Supports @AroundConstruct interceptors.
     * If constructor interceptors are present, they are invoked before the actual construction.
     * <p>
     * <b>CDI 4.1 Section 3.10 - InjectionPoint Bean:</b>
     * Sets the current InjectionPoint context for each constructor parameter so that
     * if InjectionPoint is injected as a dependency, it receives the correct metadata.
     */
    private T createInstance(CreationalContext<T> creationalContext) throws Exception {
        Constructor<T> constructor = injectConstructor;

        if (constructor == null) {
            // Use no-args constructor
            constructor = beanClass.getDeclaredConstructor();
        }

        constructor.setAccessible(true);

        // Resolve constructor parameters
        Parameter[] parameters = constructor.getParameters();
        Object[] args = new Object[parameters.length];
        List<TransientInvocationArgument> transientArguments = new ArrayList<>();

        for (int i = 0; i < parameters.length; i++) {
            InjectionPoint injectionPoint = findInjectionPoint(parameters[i]);
            if (injectionPoint == null) {
                injectionPoint = new InjectionPointImpl<>(parameters[i], this);
            }
            Type resolvedType = injectionPoint.getType();
            if (resolvedType == null) {
                resolvedType = GenericTypeResolver.resolve(
                        parameters[i].getParameterizedType(),
                        beanClass,
                        constructor.getDeclaringClass()
                );
            }

            // Resolve the parameter with InjectionPoint context set
            args[i] = resolveInjectionPointWithContext(
                resolvedType,
                qualifiersAsArray(injectionPoint, parameters[i].getAnnotations()),
                injectionPoint,
                creationalContext
            );
            if (args[i] == null && parameters[i].getType().isPrimitive()) {
                args[i] = defaultPrimitiveValue(parameters[i].getType());
            }
            if (args[i] != null &&
                    isTransientReferenceInjectionPoint(injectionPoint, parameters[i].getAnnotations())) {
                transientArguments.add(new TransientInvocationArgument(
                        args[i],
                        parameters[i].getType(),
                        qualifiersAsArray(injectionPoint, parameters[i].getAnnotations())
                ));
            }
        }

        try {
            // PHASE 3: Check for @AroundConstruct interceptors
            if (constructorInterceptorChain != null) {
                ThreadLocal<Map<Class<?>, Object>> constructionCacheThreadLocal = constructionInterceptorInstanceCache();
                Map<Class<?>, Object> constructionCache = new HashMap<>();
                constructionCacheThreadLocal.set(constructionCache);
                // Invoke constructor interceptor chain
                // The chain will execute all @AroundConstruct interceptors, then jakarta.enterprise.invoke the actual constructor
                try {
                    Object result = constructorInterceptorChain.invoke(null, constructor, args);
                    @SuppressWarnings("unchecked")
                    T resultT = (T) result;
                    if (resultT != null && !constructionCache.isEmpty()) {
                        Map<Class<?>, Object> targetCache = getOrCreateTargetInterceptorCache(resultT);
                        synchronized (targetCache) {
                            targetCache.putAll(constructionCache);
                        }
                    }
                    return resultT;
                } finally {
                    constructionCacheThreadLocal.remove();
                }
            } else {
                // No constructor interceptors, jakarta.enterprise.invoke directly
                return constructor.newInstance(args);
            }
        } finally {
            destroyTransientInvocationArguments(transientArguments);
        }
    }

    /**
     * Performs field injection for all @Inject fields.
     * Processes fields in hierarchy order (superclass → subclass) per CDI 4.1 spec.
     * <p>
     * <b>CDI 4.1 Section 3.10 - InjectionPoint Bean:</b>
     * Sets the current InjectionPoint context for each field so that
     * if InjectionPoint is injected as a dependency, it receives the correct metadata.
     */
    private void performFieldInjection(T instance, CreationalContext<T> creationalContext) throws Exception {
        // Build hierarchy: superclass first, then subclass
        List<Class<?>> hierarchy = collectClassHierarchyFromObject(instance);

        // Inject fields in hierarchy order (parent → child)
        for (Class<?> clazz : hierarchy) {
            for (Field field : injectFields) {
                // Only process fields declared by this specific class in the hierarchy
                if (!field.getDeclaringClass().equals(clazz)) {
                    continue;
                }

                field.setAccessible(true);

                InjectionPoint injectionPoint = findInjectionPoint(field);
                if (injectionPoint == null) {
                    injectionPoint = new InjectionPointImpl<>(field, this);
                }
                Type resolvedFieldType = injectionPoint.getType();
                if (resolvedFieldType == null) {
                    resolvedFieldType = GenericTypeResolver.resolve(
                            field.getGenericType(),
                            beanClass,
                            field.getDeclaringClass()
                    );
                }

                // Resolve the field with InjectionPoint context set
                Object value = resolveInjectionPointWithContext(
                    resolvedFieldType,
                    qualifiersAsArray(injectionPoint, field.getAnnotations()),
                    injectionPoint,
                    creationalContext
                );
                if (value == null && field.getType().isPrimitive()) {
                    value = defaultPrimitiveValue(field.getType());
                }

                field.set(instance, value);
            }
        }
    }

    /**
     * Performs method injection for all @Inject methods.
     * Processes methods in hierarchy order (superclass → subclass) per CDI 4.1 spec.
     * Skips overridden methods per JSR-330 rules.
     * <p>
     * <b>CDI 4.1 Section 3.10 - InjectionPoint Bean:</b>
     * Sets the current InjectionPoint context for each method parameter so that
     * if InjectionPoint is injected as a dependency, it receives the correct metadata.
     */
    private void performMethodInjection(T instance, CreationalContext<T> creationalContext) throws Exception {
        // Build hierarchy: superclass first, then subclass
        List<Class<?>> hierarchy = collectClassHierarchyFromObject(instance);

        // Inject methods in hierarchy order (parent → child)
        for (Class<?> clazz : hierarchy) {
            for (Method method : injectMethods) {
                // Only process methods declared by this specific class in the hierarchy
                if (!method.getDeclaringClass().equals(clazz)) {
                    continue;
                }

                // Skip overridden methods per JSR-330 (method already processed in parent)
                if (isOverridden(method, instance.getClass())) {
                    continue;
                }

                method.setAccessible(true);
                Parameter[] parameters = method.getParameters();
                Object[] args = new Object[parameters.length];
                List<TransientInvocationArgument> transientArguments =
                        new ArrayList<>();

                for (int i = 0; i < parameters.length; i++) {
                    InjectionPoint injectionPoint = findInjectionPoint(parameters[i]);
                    if (injectionPoint == null) {
                        injectionPoint = new InjectionPointImpl<>(parameters[i], this);
                    }
                    Type resolvedParameterType = injectionPoint.getType();
                    if (resolvedParameterType == null) {
                        resolvedParameterType = GenericTypeResolver.resolve(
                                parameters[i].getParameterizedType(),
                                beanClass,
                                method.getDeclaringClass()
                        );
                    }

                    // Resolve the parameter with InjectionPoint context set
                    args[i] = resolveInjectionPointWithContext(
                        resolvedParameterType,
                        qualifiersAsArray(injectionPoint, parameters[i].getAnnotations()),
                        injectionPoint,
                        creationalContext
                    );
                    if (args[i] == null && parameters[i].getType().isPrimitive()) {
                        args[i] = defaultPrimitiveValue(parameters[i].getType());
                    }
                    if (args[i] != null &&
                            isTransientReferenceInjectionPoint(injectionPoint, parameters[i].getAnnotations())) {
                        transientArguments.add(new TransientInvocationArgument(
                                args[i],
                                parameters[i].getType(),
                                qualifiersAsArray(injectionPoint, parameters[i].getAnnotations())
                        ));
                    }
                }

                try {
                    method.invoke(instance, args);
                } finally {
                    destroyTransientInvocationArguments(transientArguments);
                }
            }
        }
    }

    private boolean isTransientReferenceInjectionPoint(InjectionPoint injectionPoint, Annotation[] fallbackAnnotations) {
        if (injectionPoint != null && injectionPoint.getAnnotated() != null &&
                injectionPoint.getAnnotated().getAnnotations() != null) {
            for (Annotation annotation : injectionPoint.getAnnotated().getAnnotations()) {
                if (annotation != null &&
                        AnnotationsHelper.hasTransientReferenceAnnotation(annotation.annotationType())) {
                    return true;
                }
            }
        }
        if (fallbackAnnotations == null) {
            return false;
        }
        for (Annotation annotation : fallbackAnnotations) {
            if (annotation != null &&
                    AnnotationsHelper.hasTransientReferenceAnnotation(annotation.annotationType())) {
                return true;
            }
        }
        return false;
    }

    private void destroyTransientInvocationArguments(List<TransientInvocationArgument> transientArguments) {
        if (transientArguments == null || transientArguments.isEmpty()) {
            return;
        }
        for (TransientInvocationArgument transientArgument : transientArguments) {
            if (transientArgument == null || transientArgument.instance == null) {
                continue;
            }
            try {
                destroyTransientInvocationArgument(transientArgument);
            } catch (Exception ignored) {
                // Best-effort cleanup only.
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void destroyTransientInvocationArgument(TransientInvocationArgument argument) throws Exception {
        if (argument == null || argument.instance == null) {
            return;
        }

        BeanManager beanManager = null;
        if (dependencyResolver instanceof BeanResolver) {
            BeanManagerImpl owningBeanManager = ((BeanResolver) dependencyResolver).getOwningBeanManager();
            if (owningBeanManager != null) {
                if (owningBeanManager.destroyOwnedTransientReference(argument.instance)) {
                    return;
                }
                beanManager = owningBeanManager;
            }
        }
        if (beanManager == null && BeanManagerImpl.destroyTransientReference(argument.instance)) {
            return;
        }
        try {
            if (beanManager == null) {
                beanManager = CDI.current().getBeanManager();
            }
            if (beanManager != null) {
                Annotation[] qualifiers = extractQualifierAnnotations(argument.annotations);
                Set<Bean<?>> beans = beanManager.getBeans(argument.type, qualifiers);
                if (beans != null && !beans.isEmpty()) {
                    Bean<?> resolved = beanManager.resolve(beans);
                    if (resolved != null && AnnotationsHelper.hasDependentAnnotation(resolved.getScope())) {
                        CreationalContext context = beanManager.createCreationalContext((Bean) resolved);
                        ((Bean) resolved).destroy(argument.instance, context);
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {
            // Fallback below.
        }

        LifecycleMethodHelper.invokeLifecycleMethod(argument.instance, AnnotationsEnum.PRE_DESTROY);
    }

    private Annotation[] extractQualifierAnnotations(Annotation[] annotations) {
        if (annotations == null || annotations.length == 0) {
            return new Annotation[0];
        }
        Set<Annotation> qualifiers = new HashSet<>();
        for (Annotation annotation : annotations) {
            if (annotation == null || annotation.annotationType() == null) {
                continue;
            }
            if (AnnotationsHelper.hasQualifierAnnotation(annotation.annotationType())) {
                qualifiers.add(annotation);
            }
        }
        return qualifiers.toArray(new Annotation[0]);
    }

    private static final class TransientInvocationArgument {
        private final Object instance;
        private final Class<?> type;
        private final Annotation[] annotations;

        private TransientInvocationArgument(Object instance, Class<?> type, Annotation[] annotations) {
            this.instance = instance;
            this.type = type;
            this.annotations = annotations == null ? new Annotation[0] : annotations.clone();
        }
    }

    private Annotation[] qualifiersAsArray(InjectionPoint injectionPoint, Annotation[] fallbackAnnotations) {
        if (injectionPoint == null || injectionPoint.getQualifiers() == null || injectionPoint.getQualifiers().isEmpty()) {
            return fallbackAnnotations == null ? new Annotation[0] : fallbackAnnotations;
        }
        return injectionPoint.getQualifiers().toArray(new Annotation[0]);
    }

    private InjectionPoint findInjectionPoint(Field field) {
        if (field == null) {
            return null;
        }
        for (InjectionPoint injectionPoint : injectionPoints) {
            if (injectionPoint == null) {
                continue;
            }
            if (field.equals(injectionPoint.getMember())) {
                return injectionPoint;
            }
        }
        return null;
    }

    private InjectionPoint findInjectionPoint(Parameter parameter) {
        if (parameter == null) {
            return null;
        }
        Executable executable = parameter.getDeclaringExecutable();
        int index = parameterIndex(parameter);
        if (index < 0) {
            return null;
        }
        for (InjectionPoint injectionPoint : injectionPoints) {
            if (injectionPoint == null || !executable.equals(injectionPoint.getMember())) {
                continue;
            }
            Annotated annotated = injectionPoint.getAnnotated();
            if (annotated instanceof AnnotatedParameter<?>) {
                if (((AnnotatedParameter<?>) annotated).getPosition() == index) {
                    return injectionPoint;
                }
            }
        }
        return null;
    }

    private int parameterIndex(Parameter parameter) {
        Parameter[] parameters = parameter.getDeclaringExecutable().getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (parameter.equals(parameters[i])) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Checks if a method is overridden in a subclass (JSR-330 rules).
     * Private methods are never considered overridden.
     * Package-private methods must be in the same package to be overridden.
     */
    private boolean isOverridden(Method superMethod, Class<?> leafClass) {
        if (Modifier.isPrivate(superMethod.getModifiers())) {
            return false;
        }
        if (superMethod.getDeclaringClass().equals(leafClass)) {
            return false;
        }

        // Search for the method in the leaf class hierarchy
        Class<?> current = leafClass;
        while (current != null && current != superMethod.getDeclaringClass()) {
            try {
                Method subMethod = current.getDeclaredMethod(superMethod.getName(), superMethod.getParameterTypes());
                if (!subMethod.equals(superMethod)) {
                    // Check package-private rules
                    boolean isSuperPackagePrivate = !Modifier.isPublic(superMethod.getModifiers()) &&
                            !Modifier.isProtected(superMethod.getModifiers()) &&
                            !Modifier.isPrivate(superMethod.getModifiers());

                    if (isSuperPackagePrivate) {
                        // Package-private method is only overridden if in the same package
                        String superPackage = superMethod.getDeclaringClass().getPackage() != null ?
                                superMethod.getDeclaringClass().getPackage().getName() : "";
                        String subPackage = subMethod.getDeclaringClass().getPackage() != null ?
                                subMethod.getDeclaringClass().getPackage().getName() : "";
                        return superPackage.equals(subPackage);
                    }

                    return true; // Method is overridden
                }
            } catch (NoSuchMethodException e) {
                // Method was not found in this class, continue to superclass
            }
            current = current.getSuperclass();
        }

        return false;
    }

    /**
     * Invokes @PostConstruct methods in the bean hierarchy.
     * <p>
     * <b>Interceptors Specification 1.2+ / CDI 4.1 Section 7.1:</b>
     * <ul>
     *   <li>All @PostConstruct methods in the class hierarchy are invoked</li>
     *   <li>Execution order: <b>superclass → subclass</b></li>
     *   <li>If a subclass overrides a parent's @PostConstruct, only the overriding method is called</li>
     *   <li>Interceptor @PostConstruct methods run before all target bean methods</li>
     * </ul>
     * <p>
     * PHASE 4: Supports @PostConstruct lifecycle interceptors.
     * The complete execution order is:
     * <ol>
     *   <li>Interceptor 1 @PostConstruct</li>
     *   <li>Interceptor 2 @PostConstruct</li>
     *   <li>Target bean superclass @PostConstruct</li>
     *   <li>Target bean subclass @PostConstruct</li>
     * </ol>
     */
    private void invokePostConstruct(T instance) throws Exception {
        ensureLifecycleInterceptorChainsInitialized();
        // PHASE 4: Check for @PostConstruct interceptors
        if (postConstructInterceptorChain != null) {
            // Invoke the interceptor chain which will then jakarta.enterprise.invoke all target @PostConstruct methods
            // We pass the list of methods to be invoked in order
            postConstructInterceptorChain.invokeLifecycleChain(instance, postConstructMethods);
        } else if (!postConstructMethods.isEmpty()) {
            // No interceptors, jakarta.enterprise.invoke target @PostConstruct methods directly
            // Methods are already in correct order: superclass → subclass
            for (Method method : postConstructMethods) {
                method.setAccessible(true);
                method.invoke(instance);
            }
        }
    }

    private void invokePostConstructWithRequestContext(T instance) throws Exception {
        if (!requiresRequestContextForPostConstruct()) {
            invokePostConstruct(instance);
            return;
        }

        if (!(dependencyResolver instanceof BeanResolver)) {
            invokePostConstruct(instance);
            return;
        }

        BeanResolver beanResolver = (BeanResolver) dependencyResolver;
        ContextManager contextManager = beanResolver.getContextManager();
        if (contextManager == null) {
            invokePostConstruct(instance);
            return;
        }

        boolean activatedTemporarily = activateTemporaryRequestContext(contextManager);

        try {
            invokePostConstruct(instance);
        } finally {
            if (activatedTemporarily) {
                contextManager.deactivateRequest();
            }
        }
    }

    private boolean requiresRequestContextForPostConstruct() {
        return !postConstructMethods.isEmpty() || postConstructInterceptorChain != null;
    }

    private void invokeCustomInjectionTargetPostConstructWithRequestContext(T instance) throws Exception {
        if (!(dependencyResolver instanceof BeanResolver)) {
            customInjectionTarget.postConstruct(instance);
            return;
        }

        BeanResolver beanResolver = (BeanResolver) dependencyResolver;
        ContextManager contextManager = beanResolver.getContextManager();
        if (contextManager == null) {
            customInjectionTarget.postConstruct(instance);
            return;
        }

        boolean activatedTemporarily = activateTemporaryRequestContext(contextManager);

        try {
            if (postConstructInterceptorChain != null) {
                invokeLifecycleChainWithCustomTarget(instance, postConstructInterceptorChain,
                        (Runnable) () -> customInjectionTarget.postConstruct(instance));
            } else {
                customInjectionTarget.postConstruct(instance);
            }
        } finally {
            if (activatedTemporarily) {
                contextManager.deactivateRequest();
            }
        }
    }

    private boolean activateTemporaryRequestContext(ContextManager contextManager) {
        if (contextManager == null) {
            return false;
        }
        try {
            ScopeContext requestScopeContext = contextManager.getContext(RequestScoped.class);
            if (!requestScopeContext.isActive()) {
                contextManager.activateRequest();
                return contextManager.getContext(RequestScoped.class).isActive();
            }
        } catch (IllegalArgumentException ignored) {
            // Request scope support not available.
        }
        return false;
    }

    private void invokeCustomInjectionTargetPreDestroyWithInterceptors(T instance) throws Exception {
        ensureLifecycleInterceptorChainsInitialized();
        if (preDestroyInterceptorChain != null) {
            invokeLifecycleChainWithCustomTarget(instance, preDestroyInterceptorChain,
                    (Runnable) () -> customInjectionTarget.preDestroy(instance));
            return;
        }
        customInjectionTarget.preDestroy(instance);
    }

    /**
     * Invokes @PreDestroy methods in the bean hierarchy.
     * <p>
     * <b>Interceptors Specification 1.2+ / CDI 4.1 Section 7.1:</b>
     * <ul>
     *   <li>All @PreDestroy methods in the class hierarchy are invoked</li>
     *   <li>Execution order: <b>superclass → subclass</b></li>
     *   <li>If a subclass overrides a parent's @PreDestroy, only the overriding method is called</li>
     *   <li>Interceptor @PreDestroy methods run before all target bean methods</li>
     * </ul>
     * <p>
     * PHASE 4: Supports @PreDestroy lifecycle interceptors.
     * The complete execution order is:
     * <ol>
     *   <li>Interceptor 1 @PreDestroy</li>
     *   <li>Interceptor 2 @PreDestroy</li>
     *   <li>Target bean superclass @PreDestroy</li>
     *   <li>Target bean subclass @PreDestroy</li>
     * </ol>
     */
    private void invokePreDestroy(T instance) throws Exception {
        ensureLifecycleInterceptorChainsInitialized();
        // PHASE 4: Check for @PreDestroy interceptors
        if (preDestroyInterceptorChain != null) {
            // Invoke interceptor chain and then target @PreDestroy callbacks in
            // discovery order (superclass -> subclass).
            preDestroyInterceptorChain.invokeLifecycleChain(instance, preDestroyMethods);
        } else if (!preDestroyMethods.isEmpty()) {
            // No interceptors, jakarta.enterprise.invoke target @PreDestroy callbacks directly in
            // discovery order (superclass -> subclass).
            for (Method method : preDestroyMethods) {
                method.setAccessible(true);
                method.invoke(instance);
            }
        }
    }

    /**
     * Invokes @PrePassivate methods in the bean hierarchy before serialization.
     * <p>
     * <b>IMPORTANT:</b> @PrePassivate is an EJB annotation (jakarta.ejb.PrePassivate), NOT a CDI 4.1 standard.
     * CDI 4.1 only requires beans in passivating scopes to implement Serializable and relies on Java's
     * standard {@code writeObject()} method for custom serialization logic. This method provides optional
     * support for EJB-style callbacks as a convenience for applications using both CDI and EJB.
     * <p>
     * <b>CDI 4.1 Standard Approach:</b> Use Java's {@code private void writeObject(ObjectOutputStream)}
     * method in your bean class instead of @PrePassivate.
     * <p>
     * <b>Execution order:</b> superclass → subclass (same as @PostConstruct)
     * <p>
     * This allows beans to prepare for serialization by:
     * <ul>
     *   <li>Closing non-serializable resources (database connections, file handles)</li>
     *   <li>Clearing transient fields</li>
     *   <li>Saving state to serializable form</li>
     * </ul>
     *
     * @param instance the bean instance to prepare for passivation
     * @throws Exception if any @PrePassivate method fails
     */
    public void invokePrePassivate(T instance) throws Exception {
        if (!prePassivateMethods.isEmpty()) {
            // Invoke in superclass → subclass order
            for (Method method : prePassivateMethods) {
                method.setAccessible(true);
                method.invoke(instance);
            }
        }
    }

    /**
     * Invokes @PostActivate methods in the bean hierarchy after deserialization.
     * <p>
     * <b>IMPORTANT:</b> @PostActivate is an EJB annotation (jakarta.ejb.PostActivate), NOT a CDI 4.1 standard.
     * CDI 4.1 only requires beans in passivating scopes to implement Serializable and relies on Java's
     * standard {@code readObject()} method for custom deserialization logic. This method provides optional
     * support for EJB-style callbacks as a convenience for applications using both CDI and EJB.
     * <p>
     * <b>CDI 4.1 Standard Approach:</b> Use Java's {@code private void readObject(ObjectInputStream)}
     * method in your bean class instead of @PostActivate.
     * <p>
     * <b>Execution order:</b> superclass → subclass (same as @PostConstruct)
     * <p>
     * This allows beans to restore state after deserialization by:
     * <ul>
     *   <li>Re-opening non-serializable resources (database connections, file handles)</li>
     *   <li>Re-initializing transient fields</li>
     *   <li>Restoring state from serialized form</li>
     * </ul>
     *
     * @param instance the bean instance to restore after activation
     * @throws Exception if any @PostActivate method fails
     */
    public void invokePostActivate(T instance) throws Exception {
        if (!postActivateMethods.isEmpty()) {
            // Invoke in superclass → subclass order
            for (Method method : postActivateMethods) {
                method.setAccessible(true);
                method.invoke(instance);
            }
        }
    }

    /**
     * Resolves an injection point with InjectionPoint context set.
     * <p>
     * This method sets the InjectionPoint in BeanResolver's ThreadLocal before resolution,
     * allowing any dependency that injects InjectionPoint to receive the correct metadata
     * about where it's being injected.
     * <p>
     * <b>CDI 4.1 Section 3.10 - InjectionPoint Bean:</b>
     * InjectionPoint is a built-in dependent-scoped bean that provides metadata about
     * the injection point it's being injected into. Per spec, it must be contextual to
     * the current injection operation.
     * <p>
     * <b>Thread Safety:</b> Uses BeanResolver's ThreadLocal storage, safe for concurrent injection.
     *
     * @param type the required type
     * @param annotations the qualifier annotations
     * @param injectionPoint the injection point metadata (field, parameter, etc.)
     * @param creationalContext the creational context
     * @return the resolved bean instance
     */
    private Object resolveInjectionPointWithContext(Type type,
                                                     Annotation[] annotations,
                                                     InjectionPoint injectionPoint,
                                                     CreationalContext<T> creationalContext) {
        if (dependencyResolver == null) {
            throw new IllegalStateException(
                "BeanImpl dependency resolver not set. " +
                "This should be set during container initialization for bean: " + beanClass.getName()
            );
        }

        Deque<InjectionPoint> stack = INJECTION_POINT_CONTEXT.get();
        stack.push(injectionPoint);

        try {
            // InjectionPoint metadata must describe the owning injection site, not itself.
            if (isInjectionPointMetadataType(type)) {
                if (dependencyResolver instanceof BeanResolver) {
                    BeanResolver beanResolver = (BeanResolver) dependencyResolver;
                    beanResolver.setCurrentInjectionPoint(injectionPoint);
                    try {
                        BeanManagerImpl beanManager = beanResolver.getOwningBeanManager();
                        if (beanManager != null && creationalContext != null) {
                            try {
                                return beanManager.getInjectableReference(injectionPoint, creationalContext);
                            } catch (IllegalStateException beforeAfterDeploymentValidation) {
                                // During bootstrap phases BeanManager SPI lookup can be lifecycle-guarded.
                                // Fall back to direct resolver resolution in those phases.
                            }
                        }
                        return dependencyResolver.resolve(type, annotations);
                    } finally {
                        beanResolver.clearCurrentInjectionPoint();
                    }
                }

                InjectionPoint contextual = resolveContextualInjectionPointFromStack(stack);
                if ((contextual == null || isInjectionPointMetadataType(contextual.getType()))
                        && dependencyResolver instanceof BeanResolver) {
                    InjectionPoint current = ((BeanResolver) dependencyResolver).getCurrentInjectionPoint();
                    if (current != null && !isInjectionPointMetadataType(current.getType())) {
                        return current;
                    }
                }
                return contextual;
            }

            // Only set resolver context if the resolver supports it.
            if (dependencyResolver instanceof BeanResolver) {
                BeanResolver beanResolver = (BeanResolver) dependencyResolver;
                beanResolver.setCurrentInjectionPoint(injectionPoint);
                try {
                    BeanManagerImpl beanManager = beanResolver.getOwningBeanManager();
                    if (beanManager != null && creationalContext != null) {
                        try {
                            return beanManager.getInjectableReference(injectionPoint, creationalContext);
                        } catch (IllegalStateException beforeAfterDeploymentValidation) {
                            // During bootstrap phases BeanManager SPI lookup can be lifecycle-guarded.
                            // Fall back to direct resolver resolution in those phases.
                        }
                    }
                    return dependencyResolver.resolve(type, annotations);
                } finally {
                    beanResolver.clearCurrentInjectionPoint();
                }
            }

            return dependencyResolver.resolve(type, annotations);
        } finally {
            if (!stack.isEmpty()) {
                stack.pop();
            }
            if (stack.isEmpty()) {
                INJECTION_POINT_CONTEXT.remove();
            }
        }
    }

    private boolean isInjectionPointMetadataType(Type type) {
        if (!(type instanceof Class<?>)) {
            return false;
        }
        String typeName = ((Class<?>) type).getName();
        return "jakarta.enterprise.inject.spi.InjectionPoint".equals(typeName) ||
                "javax.enterprise.inject.spi.InjectionPoint".equals(typeName);
    }

    private InjectionPoint resolveContextualInjectionPointFromStack(Deque<InjectionPoint> stack) {
        for (InjectionPoint candidate : stack) {
            if (!isInjectionPointMetadataType(candidate.getType())) {
                return candidate;
            }
        }
        return stack.isEmpty() ? null : stack.peek();
    }

    /**
     * Returns whether this bean has validation errors.
     * A bean with validation errors should not be used for dependency resolution,
     * but the error should only be reported if the bean is actually needed.
     *
     * @return true if the bean has validation errors, false otherwise
     */
    public boolean hasValidationErrors() {
        return hasValidationErrors;
    }

    /**
     * Marks this bean as having validation errors.
     * This should be called during validation if any CDI constraint is violated.
     */
    public void setHasValidationErrors(boolean hasValidationErrors) {
        this.hasValidationErrors = hasValidationErrors;
    }

    /**
     * Returns true if this bean was vetoed by an extension during ProcessAnnotatedType.
     * Vetoed beans should not be available for injection.
     */
    public boolean isVetoed() {
        return vetoed;
    }

    /**
     * Marks this bean as vetoed by an extension.
     * This should be called when an extension calls ProcessAnnotatedType.veto().
     */
    public void setVetoed(boolean vetoed) {
        this.vetoed = vetoed;
    }

    // Injection metadata getters/setters (used by CDI41BeanValidator)

    public Constructor<T> getInjectConstructor() {
        return injectConstructor;
    }

    public void setInjectConstructor(Constructor<T> injectConstructor) {
        this.injectConstructor = injectConstructor;
    }

    public Set<Field> getInjectFields() {
        return Collections.unmodifiableSet(injectFields);
    }

    public void addInjectField(Field field) {
        if (field != null) {
            this.injectFields.add(field);
        }
    }

    public Set<Method> getInjectMethods() {
        return Collections.unmodifiableSet(injectMethods);
    }

    public void addInjectMethod(Method method) {
        if (method != null) {
            this.injectMethods.add(method);
        }
    }

    /**
     * Gets all @PostConstruct methods in the bean hierarchy.
     * Methods are ordered from superclass to subclass (execution order).
     *
     * @return list of @PostConstruct methods (can be empty)
     */
    public List<Method> getPostConstructMethods() {
        return Collections.unmodifiableList(postConstructMethods);
    }

    /**
     * Adds a @PostConstruct method to the list.
     * Should be called during bean discovery in hierarchy order (superclass → subclass).
     *
     * @param method the @PostConstruct method to add
     */
    public void addPostConstructMethod(Method method) {
        if (method != null && !postConstructMethods.contains(method)) {
            postConstructMethods.add(method);
        }
    }

    /**
     * Gets all @PreDestroy methods in the bean hierarchy.
     * Methods are ordered from subclass to superclass (execution order).
     *
     * @return list of @PreDestroy methods (can be empty)
     */
    public List<Method> getPreDestroyMethods() {
        return Collections.unmodifiableList(preDestroyMethods);
    }

    /**
     * Adds a @PreDestroy method to the list.
     * Should be called during bean discovery in hierarchy order (superclass → subclass),
     * but will be reversed for execution (subclass → superclass).
     *
     * @param method the @PreDestroy method to add
     */
    public void addPreDestroyMethod(Method method) {
        if (method != null && !preDestroyMethods.contains(method)) {
            preDestroyMethods.add(method);
        }
    }

    /**
     * Gets all @PrePassivate methods in the bean hierarchy.
     * Methods are ordered from superclass to subclass (execution order).
     *
     * @return list of @PrePassivate methods (can be empty)
     */
    public List<Method> getPrePassivateMethods() {
        return Collections.unmodifiableList(prePassivateMethods);
    }

    /**
     * Adds a @PrePassivate method to the list.
     * Should be called during bean discovery in hierarchy order (superclass → subclass).
     *
     * @param method the @PrePassivate method to add
     */
    public void addPrePassivateMethod(Method method) {
        if (method != null && !prePassivateMethods.contains(method)) {
            prePassivateMethods.add(method);
        }
    }

    /**
     * Gets all @PostActivate methods in the bean hierarchy.
     * Methods are ordered from superclass to subclass (execution order).
     *
     * @return list of @PostActivate methods (can be empty)
     */
    public List<Method> getPostActivateMethods() {
        return Collections.unmodifiableList(postActivateMethods);
    }

    /**
     * Adds a @PostActivate method to the list.
     * Should be called during bean discovery in hierarchy order (superclass → subclass).
     *
     * @param method the @PostActivate method to add
     */
    public void addPostActivateMethod(Method method) {
        if (method != null && !postActivateMethods.contains(method)) {
            postActivateMethods.add(method);
        }
    }

    public void setDependencyResolver(DependencyResolver dependencyResolver) {
        this.dependencyResolver = dependencyResolver;
    }

    // ====================================================================================
    // Interceptor Support Methods (Phase 2)
    // ====================================================================================

    public void setInterceptorSupport(InterceptorSupport interceptorSupport) {
        this.interceptorSupport = interceptorSupport;
    }

    /**
     * Sets the knowledge base for this bean.
     * Called during container initialization by InjectorImpl.
     *
     * @param knowledgeBase the knowledge base containing interceptor metadata
     */
    public void setKnowledgeBase(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    public void setDecoratorSupport(DecoratorSupport decoratorSupport) {
        this.decoratorSupport = decoratorSupport;
    }

    /**
     * Sets the BeanManager for this bean.
     * Called during container initialization by InjectorImpl2.
     * Needed for creating decorator instances.
     *
     * @param beanManager the BeanManager
     */
    public void setBeanManager(BeanManager beanManager) {
        this.beanManager = beanManager;
    }

    /**
     * Builds interceptor chains for all methods that have interceptor bindings.
     * <p>
     * This method is called once during bean initialization to analyze all methods
     * and build interceptor chains for those that require interception.
     * <p>
     * PHASE 3 & 4: Also builds constructor and lifecycle interceptor chains.
     * <p>
     * <h3>Process:</h3>
     * <ol>
     * <li>Iterate through all declared methods in the bean class</li>
     * <li>For each method, use InterceptorResolver to find applicable interceptors</li>
     * <li>If interceptors are found, build an InterceptorChainModel and cache it</li>
     * <li>Methods without interceptors are not added to the map (memory optimization)</li>
     * <li>Build constructor interceptor chain if @AroundConstruct interceptors exist</li>
     * <li>Build @PostConstruct interceptor chain if lifecycle interceptors exist</li>
     * <li>Build @PreDestroy interceptor chain if lifecycle interceptors exist</li>
     * </ol>
     *
     * <h3>Example:</h3>
     * <pre>
     * {@literal @}ApplicationScoped
     * public class OrderService {
     *     {@literal @}Transactional // Method-level binding
     *     public void createOrder(Order order) { ... } // Will have interceptors
     *
     *     public Order getOrder(String id) { ... } // No interceptors
     * }
     *
     * // Result:
     * methodInterceptorChains = {
     *     createOrder() → [TransactionalInterceptor chain],
     *     // getOrder() not in map - no interceptors
     * }
     * </pre>
     *
     * @throws IllegalStateException if interceptorResolver or knowledgeBase not set
     */
    public void buildMethodInterceptorChains() {
        // If no interceptor support configured, skip chain building
        if (interceptorSupport == null || knowledgeBase == null) {
            this.methodInterceptorChains = Collections.emptyMap();
            this.constructorInterceptorChain = null;
            this.postConstructInterceptorChain = null;
            this.preDestroyInterceptorChain = null;
            this.targetClassAroundInvokeMethods = Collections.emptyList();
            this.interceptorInfoByClass.clear();
            return;
        }
        this.interceptorInfoByClass.clear();

        // ========================================================================
        // PHASE 2: Build business method interceptor chains (@AroundInvoke)
        // ========================================================================
        Map<Method, InterceptorChainModel> chains = new HashMap<>();
        jakarta.enterprise.inject.spi.AnnotatedType<?> beanAnnotatedType = annotatedTypeMetadata;
        if (beanAnnotatedType == null && knowledgeBase != null) {
            beanAnnotatedType = knowledgeBase.getAnnotatedTypeOverride(beanClass);
        }
        this.targetClassAroundInvokeMethods = findTargetClassAroundInvokeMethods(beanClass);
        List<Class<?>> classLevelLegacyInterceptors =
                extractLegacyInterceptorClasses(resolveLegacyInterceptorClassAnnotations(beanAnnotatedType));

        // Iterate through non-private, non-static methods in the bean class hierarchy.
        // Prefer the subclass declaration when a method is overridden.
        List<Class<?>> hierarchy = new ArrayList<>();
        Class<?> current = beanClass;
        while (current != null && current != Object.class) {
            hierarchy.add(current);
            current = current.getSuperclass();
        }
        Set<String> seenSignatures = new HashSet<>();
        for (Class<?> type : hierarchy) {
            for (Method method : type.getDeclaredMethods()) {
                if (Object.class.equals(method.getDeclaringClass())) {
                    continue;
                }
                if (Modifier.isPrivate(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                String signature = method.getName() + Arrays.toString(method.getParameterTypes());
                if (!seenSignatures.add(signature)) {
                    continue;
                }

                // Use InterceptorResolver to find applicable interceptors for this method
                List<InterceptorInfo> interceptors = interceptorSupport.resolve(
                    beanClass,
                    method,
                    beanAnnotatedType,
                    InterceptionType.AROUND_INVOKE
                );
                Set<Annotation> methodBindings = interceptorSupport.resolveBindings(beanClass, method, beanAnnotatedType);

                if (!methodBindings.isEmpty()) {
                    InterceptorChainModel chain = buildInterceptorChain(
                            interceptors,
                            InterceptionType.AROUND_INVOKE,
                            methodBindings
                    );
                    if (!chain.isEmpty()) {
                        chains.put(method, chain);
                        continue;
                    }
                }

                Annotation[] legacyMethodAnnotations =
                        resolveLegacyInterceptorMethodAnnotations(method, beanAnnotatedType);
                List<Class<?>> legacyInterceptors =
                        extractLegacyInterceptorClasses(legacyMethodAnnotations);
                if (legacyInterceptors.isEmpty() &&
                        !hasExcludeClassInterceptorsAnnotation(legacyMethodAnnotations)) {
                    legacyInterceptors = classLevelLegacyInterceptors;
                }
                if (!legacyInterceptors.isEmpty()) {
                    InterceptorChainModel legacyChain = buildLegacyInterceptorChain(legacyInterceptors);
                    if (legacyChain != null) {
                        chains.put(method, legacyChain);
                    }
                }
            }
        }

        // Store the chain map (immutable after this point)
        this.methodInterceptorChains = chains;

        // ========================================================================
        // PHASE 3: Build constructor interceptor chain (@AroundConstruct)
        // ========================================================================
        Constructor<?> constructorForInterception = injectConstructor;
        if (constructorForInterception == null) {
            try {
                constructorForInterception = beanClass.getDeclaredConstructor();
            } catch (NoSuchMethodException ignored) {
            }
        }

        List<InterceptorInfo> constructorInterceptors = interceptorSupport.resolveForConstructor(
            beanClass,
            constructorForInterception,
            beanAnnotatedType,
            InterceptionType.AROUND_CONSTRUCT
        );

        if (!constructorInterceptors.isEmpty()) {
            Set<Annotation> constructorBindings = interceptorSupport.resolveBindingsForConstructor(
                    beanClass,
                    constructorForInterception,
                    beanAnnotatedType
            );
            this.constructorInterceptorChain = buildInterceptorChain(
                constructorInterceptors,
                InterceptionType.AROUND_CONSTRUCT,
                constructorBindings
            );
        } else {
            this.constructorInterceptorChain = null;
        }

        // ========================================================================
        // PHASE 4: Build lifecycle callback interceptor chains
        // ========================================================================

        // @PostConstruct interceptor chain
        List<InterceptorInfo> postConstructInterceptors = interceptorSupport.resolve(
            beanClass,
            null,  // null method = lifecycle callback
            beanAnnotatedType,
            InterceptionType.POST_CONSTRUCT
        );
        Set<Annotation> lifecycleBindings = interceptorSupport.resolveBindings(beanClass, null, beanAnnotatedType);

        if (!postConstructInterceptors.isEmpty()) {
            this.postConstructInterceptorChain = buildLifecycleInterceptorChain(
                postConstructInterceptors,
                InterceptionType.POST_CONSTRUCT,
                lifecycleBindings,
                classLevelLegacyInterceptors
            );
        } else if (!classLevelLegacyInterceptors.isEmpty()) {
            this.postConstructInterceptorChain = buildLifecycleInterceptorChain(
                    Collections.emptyList(),
                    InterceptionType.POST_CONSTRUCT,
                    lifecycleBindings,
                    classLevelLegacyInterceptors
            );
        } else {
            this.postConstructInterceptorChain = null;
        }

        // @PreDestroy interceptor chain
        List<InterceptorInfo> preDestroyInterceptors = interceptorSupport.resolve(
            beanClass,
            null,  // null method = lifecycle callback
            beanAnnotatedType,
            InterceptionType.PRE_DESTROY
        );

        if (!preDestroyInterceptors.isEmpty()) {
            this.preDestroyInterceptorChain = buildLifecycleInterceptorChain(
                preDestroyInterceptors,
                InterceptionType.PRE_DESTROY,
                lifecycleBindings,
                classLevelLegacyInterceptors
            );
        } else if (!classLevelLegacyInterceptors.isEmpty()) {
            this.preDestroyInterceptorChain = buildLifecycleInterceptorChain(
                    Collections.emptyList(),
                    InterceptionType.PRE_DESTROY,
                    lifecycleBindings,
                    classLevelLegacyInterceptors
            );
        } else {
            this.preDestroyInterceptorChain = null;
        }
    }

    /**
     * Builds an InterceptorChainModel from a list of InterceptorInfo objects.
     * <p>
     * This method:
     * <ol>
     * <li>Creates interceptor instances for each InterceptorInfo (with caching)</li>
     * <li>Retrieves the appropriate interceptor method based on the interception type</li>
     * <li>Builds a chain using InterceptorSupport chain builder SPI</li>
     * <li>Returns the immutable, thread-safe chain</li>
     * </ol>
     *
     * <h3>Interceptor Instance Creation:</h3>
     * Interceptors are created with dependency injection (same as beans).
     * Instances are cached per-bean to maintain interceptor state across invocations.
     * <p>
     * Example: A {@code @Transactional} interceptor might maintain transaction state
     * between multiple method calls on the same bean instance.
     *
     * <h3>Chain Ordering:</h3>
     * The interceptor list is already sorted by priority (lower value = earlier execution).
     * This sorting is done by InterceptorResolver using KnowledgeBase query methods.
     *
     * @param interceptors list of interceptor metadata, sorted by priority
     * @param interceptionType the type of interception (AROUND_INVOKE, AROUND_CONSTRUCT, etc.)
     * @return an immutable InterceptorChainModel ready for execution
     * @throws RuntimeException if interceptor instance creation fails
     */
    private InterceptorChainModel buildInterceptorChain(List<InterceptorInfo> interceptors,
                                                   InterceptionType interceptionType,
                                                   Set<Annotation> interceptorBindings) {
        // Use the Builder pattern to construct the chain
        InterceptorChainModel.Builder builder = interceptorSupport.newChainBuilder()
                .withInterceptorBindings(interceptorBindings);

        addResolvedInterceptorInvocations(builder, interceptors, interceptionType, interceptorBindings);

        // Build and return the immutable chain
        return builder.build();
    }

    /**
     * Builds a lifecycle interceptor chain that includes both interceptor callbacks and the target callback.
     * <p>
     * PHASE 4: Lifecycle interceptor chains include:
     * <ol>
     * <li>All interceptor lifecycle methods (@PostConstruct or @PreDestroy from interceptors)</li>
     * <li>The target bean's lifecycle method (@PostConstruct or @PreDestroy from the bean itself)</li>
     * </ol>
     * <p>
     * Example chain for @PostConstruct:
     * [Interceptor1.postConstruct] → [Interceptor2.postConstruct] → [TargetBean.postConstruct]
     *
     * @param interceptors list of interceptor metadata, sorted by priority
     * @param interceptionType POST_CONSTRUCT or PRE_DESTROY
     * @return an immutable InterceptorChainModel ready for execution
     */
    private InterceptorChainModel buildLifecycleInterceptorChain(
            List<InterceptorInfo> interceptors,
            InterceptionType interceptionType,
            Set<Annotation> interceptorBindings,
            List<Class<?>> legacyInterceptorClasses) {

        // Use the Builder pattern to construct the chain
        InterceptorChainModel.Builder builder = interceptorSupport.newChainBuilder()
                .withInterceptorBindings(interceptorBindings);

        addLegacyInterceptorInvocations(builder, legacyInterceptorClasses, interceptionType);

        // Add interceptor lifecycle methods to the chain.
        // Note: The target bean's lifecycle methods are NOT added to the interceptor chain here.
        // They are passed separately to invokeLifecycleChain() to support multiple lifecycle
        // methods in the class hierarchy (per Interceptors spec).
        addResolvedInterceptorInvocations(builder, interceptors, interceptionType, interceptorBindings);

        return builder.build();
    }

    private void addLegacyInterceptorInvocations(InterceptorChainModel.Builder builder,
                                                 List<Class<?>> legacyInterceptorClasses,
                                                 InterceptionType interceptionType) {
        if (legacyInterceptorClasses == null || legacyInterceptorClasses.isEmpty()) {
            return;
        }
        for (Class<?> interceptorClass : legacyInterceptorClasses) {
            if (interceptorClass == null) {
                continue;
            }
            List<Method> lifecycleMethods = findInterceptorMethodsInHierarchy(interceptorClass, interceptionType);
            for (Method lifecycleMethod : lifecycleMethods) {
                final Method interceptorMethod = lifecycleMethod;
                builder.addInvocation(ctx -> invokeInterceptorMethod(
                        getOrCreateLegacyInterceptorInstance(interceptorClass, resolveInterceptionTarget(ctx)),
                        interceptorMethod,
                        ctx
                ));
            }
        }
    }

    private void addResolvedInterceptorInvocations(InterceptorChainModel.Builder builder,
                                                   List<InterceptorInfo> interceptors,
                                                   InterceptionType interceptionType,
                                                   Set<Annotation> interceptorBindings) {
        Map<Class<?>, InterceptorInfo> interceptorInfoByClass = new LinkedHashMap<>();
        for (InterceptorInfo interceptorInfo : interceptors) {
            if (interceptorInfo == null || interceptorInfo.getInterceptorClass() == null) {
                continue;
            }
            interceptorInfoByClass.put(interceptorInfo.getInterceptorClass(), interceptorInfo);
        }

        List<Interceptor<?>> runtimeInterceptors =
                resolveRuntimeInterceptors(interceptionType, interceptorBindings);

        if (runtimeInterceptors.isEmpty()) {
            for (InterceptorInfo interceptorInfo : interceptors) {
                addInterceptorInfoInvocations(builder, interceptorInfo, interceptionType);
            }
            return;
        }
        for (InterceptorInfo interceptorInfo : interceptors) {
            addInterceptorInfoInvocations(builder, interceptorInfo, interceptionType);
        }

        Set<Class<?>> interceptorInfoClasses = interceptorInfoByClass.keySet();
        for (Interceptor<?> runtimeInterceptor : runtimeInterceptors) {
            if (runtimeInterceptor == null) {
                continue;
            }
            Class<?> runtimeClass = runtimeInterceptor.getBeanClass();
            if (runtimeClass != null && interceptorInfoClasses.contains(runtimeClass)) {
                continue;
            }
            addProgrammaticInterceptorInvocation(builder, runtimeInterceptor, interceptionType);
        }
    }

    private List<Interceptor<?>> resolveRuntimeInterceptors(
            InterceptionType interceptionType,
            Set<Annotation> interceptorBindings) {
        if (!(beanManager instanceof BeanManagerImpl)) {
            return Collections.emptyList();
        }
        if (interceptorBindings == null || interceptorBindings.isEmpty()) {
            return Collections.emptyList();
        }

        List<Annotation> normalized = new ArrayList<>(interceptorBindings.size());
        for (Annotation binding : interceptorBindings) {
            if (binding == null) {
                continue;
            }
            if (AnnotationsEnum.INTERCEPTOR_BINDING.matches(binding.annotationType())) {
                continue;
            }
            normalized.add(binding);
        }
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }

        Annotation[] bindings = normalized.toArray(new Annotation[0]);
        try {
            return beanManager.resolveInterceptors(interceptionType, bindings);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Collections.emptyList();
        }
    }

    private void addInterceptorInfoInvocations(InterceptorChainModel.Builder builder,
                                               InterceptorInfo interceptorInfo,
                                               InterceptionType interceptionType) {
        if (interceptorInfo == null || interceptorInfo.getInterceptorClass() == null) {
            return;
        }
        interceptorInfoByClass.put(interceptorInfo.getInterceptorClass(), interceptorInfo);
        List<Method> interceptorMethods = getInterceptorMethods(interceptorInfo, interceptionType);
        for (Method interceptorMethod : interceptorMethods) {
            final Method method = interceptorMethod;
            builder.addInvocation(ctx -> invokeInterceptorMethod(
                    getOrCreateInterceptorInstance(interceptorInfo, resolveInterceptionTarget(ctx)),
                    method,
                    ctx
            ));
        }
    }

    private void addProgrammaticInterceptorInvocation(
            InterceptorChainModel.Builder builder,
            Interceptor<?> interceptor,
            InterceptionType interceptionType) {
        if (!interceptor.intercepts(interceptionType)) {
            return;
        }

        final Object interceptionTarget = getOrCreateProgrammaticInterceptorTarget(interceptor);
        @SuppressWarnings("unchecked")
        final Interceptor<Object> rawInterceptor =
                (Interceptor<Object>) interceptor;
        builder.addInvocation(ctx -> rawInterceptor.intercept(interceptionType, interceptionTarget, ctx));
    }

    private Object getOrCreateProgrammaticInterceptorTarget(
            Interceptor<?> interceptor) {
        Class<?> cacheKey = interceptor.getBeanClass() != null
                ? interceptor.getBeanClass()
                : interceptor.getClass();

        @SuppressWarnings("unchecked")
        final Interceptor<Object> rawInterceptor =
                (Interceptor<Object>) interceptor;

        return interceptorInstanceCache.computeIfAbsent(cacheKey, ignored -> {
            CreationalContext<Object> creationalContext = new SimpleCreationalContext<>();
            return rawInterceptor.create(creationalContext);
        });
    }

    private static final class SimpleCreationalContext<X> implements CreationalContext<X> {
        @Override
        public void push(X incompleteInstance) {
            // No-op context for synthetic interceptor bean instances.
        }

        @Override
        public void release() {
            // No-op context for synthetic interceptor bean instances.
        }
    }

    /**
     * Gets the appropriate interceptor method for a given interception type.
     *
     * @param interceptorInfo the interceptor metadata
     * @param interceptionType the type of interception
     * @return the interceptor method, or null if not present
     */
    private List<Method> getInterceptorMethods(InterceptorInfo interceptorInfo, InterceptionType interceptionType) {
        if (interceptorInfo == null || interceptionType == null) {
            return Collections.emptyList();
        }

        Class<?> interceptorClass = interceptorInfo.getInterceptorClass();
        if (interceptorClass == null) {
            return Collections.emptyList();
        }

        if (interceptionType == InterceptionType.AROUND_INVOKE) {
            return findInterceptorMethodsInHierarchy(interceptorClass, InterceptionType.AROUND_INVOKE);
        }
        if (interceptionType == InterceptionType.AROUND_CONSTRUCT) {
            return findInterceptorMethodsInHierarchy(interceptorClass, InterceptionType.AROUND_CONSTRUCT);
        }
        if (interceptionType == InterceptionType.POST_CONSTRUCT) {
            return findInterceptorMethodsInHierarchy(interceptorClass, InterceptionType.POST_CONSTRUCT);
        }
        if (interceptionType == InterceptionType.PRE_DESTROY) {
            return findInterceptorMethodsInHierarchy(interceptorClass, InterceptionType.PRE_DESTROY);
        }
        return Collections.emptyList();
    }

    private List<Method> findInterceptorMethodsInHierarchy(Class<?> interceptorClass, InterceptionType interceptionType) {
        if (interceptorClass == null) {
            return Collections.emptyList();
        }

        List<Class<?>> hierarchy = new ArrayList<>();
        Class<?> current = interceptorClass;
        while (current != null && current != Object.class) {
            hierarchy.add(0, current);
            current = current.getSuperclass();
        }

        List<Method> methods = new ArrayList<>();
        for (int index = 0; index < hierarchy.size(); index++) {
            Class<?> type = hierarchy.get(index);
            for (Method method : type.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                if (!isInterceptorMethodForType(method, interceptionType)) {
                    continue;
                }
                if (isOverriddenBySubclass(method, hierarchy, index + 1)) {
                    continue;
                }
                method.setAccessible(true);
                methods.add(method);
            }
        }
        return methods;
    }

    private boolean isInterceptorMethodForType(Method method, InterceptionType interceptionType) {
        if (method.getParameterCount() != 1) {
            return false;
        }
        if (!jakarta.interceptor.InvocationContext.class.isAssignableFrom(method.getParameterTypes()[0])) {
            return false;
        }
        if (interceptionType == InterceptionType.AROUND_INVOKE) {
            return AnnotationsHelper.hasAroundInvokeAnnotation(method);
        }
        if (interceptionType == InterceptionType.AROUND_CONSTRUCT) {
            return AnnotationsHelper.hasAroundConstructAnnotation(method);
        }
        if (interceptionType == InterceptionType.POST_CONSTRUCT) {
            return AnnotationsHelper.hasPostConstructAnnotation(method);
        }
        if (interceptionType == InterceptionType.PRE_DESTROY) {
            return AnnotationsHelper.hasPreDestroyAnnotation(method);
        }
        return false;
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

        return packageName(method.getDeclaringClass()).equals(packageName(subclass));
    }

    private Object getOrCreateInterceptorInstance(InterceptorInfo interceptorInfo, Object interceptionTarget) {
        if (interceptorInfo == null || interceptorInfo.getInterceptorClass() == null) {
            return null;
        }
        Class<?> interceptorClass = interceptorInfo.getInterceptorClass();
        final Bean<?> interceptorMetadataBean = resolveInterceptorMetadataBean(interceptorInfo);
        Map<Class<?>, Object> cache = resolveInterceptorInstanceCache(interceptionTarget);

        Object existing = cache.get(interceptorClass);
        if (existing != null) {
            return existing;
        }
        synchronized (cache) {
            existing = cache.get(interceptorClass);
            if (existing != null) {
                return existing;
            }
            Object created = createManagedInterceptorInstance(interceptorClass, interceptorMetadataBean);
            cache.put(interceptorClass, created);
            return created;
        }
    }

    private Object createManagedInterceptorInstance(Class<?> interceptorClass, Bean<?> interceptorMetadataBean) {
        try {
            Constructor<?> constructor = selectInterceptorConstructor(interceptorClass);
            constructor.setAccessible(true);
            Object[] constructorArgs = resolveConstructorParameters(constructor, interceptorMetadataBean);
            Object instance = constructor.newInstance(constructorArgs);
            injectInterceptorFields(instance, interceptorClass, interceptorMetadataBean);
            injectInterceptorMethods(instance, interceptorClass, interceptorMetadataBean);
            invokeInterceptorPostConstruct(instance, interceptorClass);
            return instance;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to create interceptor instance of " + interceptorClass.getName() +
                            " for bean " + beanClass.getName(), e
            );
        }
    }

    private Object createLegacyInterceptorInstance(Class<?> interceptorClass) {
        try {
            Constructor<?> constructor = selectInterceptorConstructor(interceptorClass);
            constructor.setAccessible(true);
            Object[] constructorArgs = resolveConstructorParameters(constructor, this);
            Object instance = constructor.newInstance(constructorArgs);
            injectInterceptorFields(instance, interceptorClass, this);
            injectInterceptorMethods(instance, interceptorClass, this);
            invokeInterceptorPostConstruct(instance, interceptorClass);
            return instance;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to create legacy interceptor instance of " + interceptorClass.getName() +
                            " for bean " + beanClass.getName(), e
            );
        }
    }

    private Map<Class<?>, Object> resolveInterceptorInstanceCache(Object interceptionTarget) {
        Object normalizedTarget = normalizeInterceptionTarget(interceptionTarget);
        if (normalizedTarget != null) {
            return getOrCreateTargetInterceptorCache(normalizedTarget);
        }
        Map<Class<?>, Object> constructionCache = constructionInterceptorInstanceCache().get();
        if (constructionCache != null) {
            return constructionCache;
        }
        return interceptorInstanceCache;
    }

    private Map<Class<?>, Object> getOrCreateTargetInterceptorCache(Object interceptionTarget) {
        Object normalizedTarget = normalizeInterceptionTarget(interceptionTarget);
        if (normalizedTarget == null) {
            return interceptorInstanceCache;
        }
        synchronized (interceptorTargetInstanceCache) {
            return interceptorTargetInstanceCache.computeIfAbsent(normalizedTarget, k -> new ConcurrentHashMap<>());
        }
    }

    private Object resolveInterceptionTarget(jakarta.interceptor.InvocationContext invocationContext) {
        if (invocationContext == null) {
            return null;
        }
        return normalizeInterceptionTarget(invocationContext.getTarget());
    }

    private Object normalizeInterceptionTarget(Object target) {
        if (target == null) {
            return null;
        }
        try {
            Method getter = target.getClass().getMethod("$$_getTargetInstance");
            Object unwrapped = getter.invoke(target);
            if (unwrapped != null) {
                return unwrapped;
            }
        } catch (NoSuchMethodException ignored) {
            // Not an interceptor proxy.
        } catch (Exception ignored) {
            // Best-effort unwrapping; fall back to the provided target.
        }
        return target;
    }

    private Object invokeInterceptorMethod(Object interceptorInstance,
                                           Method interceptorMethod,
                                           jakarta.interceptor.InvocationContext invocationContext) throws Exception {
        if (interceptorInstance == null || interceptorMethod == null) {
            return invocationContext != null ? invocationContext.proceed() : null;
        }
        interceptorMethod.setAccessible(true);
        try {
            return interceptorMethod.invoke(interceptorInstance, invocationContext);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getTargetException();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new RuntimeException(cause);
        }
    }

    private ThreadLocal<Map<Class<?>, Object>> constructionInterceptorInstanceCache() {
        if (constructionInterceptorInstanceCache == null) {
            constructionInterceptorInstanceCache = new ThreadLocal<>();
        }
        return constructionInterceptorInstanceCache;
    }

    private void initializeTargetInterceptorInstances(T instance) {
        Object interceptionTarget = normalizeInterceptionTarget(instance);
        if (interceptionTarget == null || interceptorInfoByClass.isEmpty()) {
            return;
        }
        for (InterceptorInfo interceptorInfo : interceptorInfoByClass.values()) {
            getOrCreateInterceptorInstance(interceptorInfo, interceptionTarget);
        }
    }

    private void clearTargetInterceptorInstances(Object instance) {
        Object interceptionTarget = normalizeInterceptionTarget(instance);
        if (interceptionTarget == null) {
            return;
        }
        synchronized (interceptorTargetInstanceCache) {
            interceptorTargetInstanceCache.remove(interceptionTarget);
        }
    }

    /**
     * Checks if this bean has any interceptors configured.
     * <p>
     * This is used to determine whether to create a regular proxy or an interceptor-aware proxy.
     *
     * @return true if this bean has at least one method with interceptors, false otherwise
     */
    public boolean hasInterceptors() {
        return (methodInterceptorChains != null && !methodInterceptorChains.isEmpty())
                || (targetClassAroundInvokeMethods != null && !targetClassAroundInvokeMethods.isEmpty());
    }

    private void ensureLifecycleInterceptorChainsInitialized() {
        if (interceptorSupport == null) {
            return;
        }
        List<Class<?>> classLevelLegacyInterceptors =
                extractLegacyInterceptorClasses(resolveLegacyInterceptorClassAnnotations(annotatedTypeMetadata));

        if (postConstructInterceptorChain == null) {
            List<InterceptorInfo> postConstructInterceptors = interceptorSupport.resolve(
                    beanClass,
                    null,
                    null,
                    InterceptionType.POST_CONSTRUCT
            );
            if (!postConstructInterceptors.isEmpty() || !classLevelLegacyInterceptors.isEmpty()) {
                postConstructInterceptorChain = buildLifecycleInterceptorChain(
                        postConstructInterceptors,
                        InterceptionType.POST_CONSTRUCT,
                        interceptorSupport.resolveBindings(beanClass, null, null),
                        classLevelLegacyInterceptors
                );
            }
        }

        if (preDestroyInterceptorChain == null) {
            List<InterceptorInfo> preDestroyInterceptors = interceptorSupport.resolve(
                    beanClass,
                    null,
                    null,
                    InterceptionType.PRE_DESTROY
            );
            if (!preDestroyInterceptors.isEmpty() || !classLevelLegacyInterceptors.isEmpty()) {
                preDestroyInterceptorChain = buildLifecycleInterceptorChain(
                        preDestroyInterceptors,
                        InterceptionType.PRE_DESTROY,
                        interceptorSupport.resolveBindings(beanClass, null, null),
                        classLevelLegacyInterceptors
                );
            }
        }
    }

    private void invokeLifecycleChainWithCustomTarget(
            T instance,
            InterceptorChainModel lifecycleChain,
            Runnable targetCallback) throws Exception {
        if (targetCallback == null) {
            return;
        }
        if (lifecycleChain == null || interceptorSupport == null) {
            targetCallback.run();
            return;
        }
        interceptorSupport.invokeLifecycle(instance, lifecycleChain, new InterceptorSupport.LifecycleTargetInvocation() {
            @Override
            public Object proceed() {
                targetCallback.run();
                return null;
            }
        });
    }

    /**
     * Gets the method interceptor chains for this bean.
     * <p>
     * This map is used by the InterceptorAwareProxyGenerator to create proxies that
     * execute interceptors before business methods.
     *
     * @return the method interceptor chains map (can be empty, never null)
     */
    public Map<Method, InterceptorChainModel> getMethodInterceptorChains() {
        return methodInterceptorChains != null ? methodInterceptorChains : Collections.emptyMap();
    }

    /**
     * Creates an interceptor-aware proxy for this bean instance.
     * <p>
     * This method is called by normal-scoped contexts when they need to create a client proxy
     * that also supports interceptors.
     * <p>
     * <h3>Usage:</h3>
     * <pre>
     * // In RequestScopeContext.get():
     * T instance = bean.create(context);
     *
     * // If a bean has interceptors, wrap with interceptor-aware proxy
     * if (bean instanceof BeanImpl && ((BeanImpl) bean).hasInterceptors()) {
     *     instance = ((BeanImpl) bean).createInterceptorAwareProxy(instance);
     * }
     * </pre>
     *
     * @param targetInstance the actual bean instance to wrap
     * @return a proxy that executes interceptors before delegating to targetInstance
     * @throws IllegalStateException if interceptor support is not configured
     */
    public T createInterceptorAwareProxy(T targetInstance) {
        if (interceptorSupport == null) {
            throw new IllegalStateException(
                "InterceptorSupport not set for bean: " + beanClass.getName()
            );
        }

        boolean hasMethodChains = methodInterceptorChains != null && !methodInterceptorChains.isEmpty();
        boolean hasTargetClassAroundInvoke = targetClassAroundInvokeMethods != null &&
                !targetClassAroundInvokeMethods.isEmpty();
        if (!hasMethodChains && !hasTargetClassAroundInvoke) {
            // No interceptors, return the instance as-is
            return targetInstance;
        }

        Map<Method, InterceptorChainModel> effectiveChains = new HashMap<>();
        if (hasMethodChains) {
            effectiveChains.putAll(methodInterceptorChains);
        }

        if (hasTargetClassAroundInvoke) {
            List<Class<?>> hierarchy = new ArrayList<>();
            Class<?> current = beanClass;
            while (current != null && current != Object.class) {
                hierarchy.add(current);
                current = current.getSuperclass();
            }
            Set<String> seenSignatures = new HashSet<>();
            for (Class<?> type : hierarchy) {
                for (Method method : type.getDeclaredMethods()) {
                    if (Modifier.isPrivate(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) {
                        continue;
                    }
                    if (targetClassAroundInvokeMethods.contains(method)) {
                        continue;
                    }
                    String signature = method.getName() + Arrays.toString(method.getParameterTypes());
                    if (!seenSignatures.add(signature)) {
                        continue;
                    }
                    InterceptorChainModel.Builder chainBuilder = interceptorSupport.newChainBuilder();
                    InterceptorChainModel existingChain = effectiveChains.get(method);
                    if (existingChain != null) {
                        chainBuilder.withInterceptorBindings(existingChain.getInterceptorBindings());
                        for (InterceptorChainModel.InterceptorInvocation invocation : existingChain.getInvocations()) {
                            chainBuilder.addInvocation(invocation);
                        }
                    }

                    for (Method aroundInvokeMethod : targetClassAroundInvokeMethods) {
                        chainBuilder.addInterceptor(targetInstance, aroundInvokeMethod);
                    }
                    effectiveChains.put(method, chainBuilder.build());
                }
            }
        }

        // Create and return an interceptor-aware proxy
        Object proxied = interceptorSupport.applyInterceptorProxy(targetInstance, this, effectiveChains);
        return proxied != null ? beanClass.cast(proxied) : targetInstance;
    }

    private List<Method> findTargetClassAroundInvokeMethods(Class<?> type) {
        if (type == null) {
            return Collections.emptyList();
        }
        jakarta.enterprise.inject.spi.AnnotatedType<?> override =
                knowledgeBase != null ? knowledgeBase.getAnnotatedTypeOverride(type) : null;

        List<Class<?>> hierarchy = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            hierarchy.add(0, current);
            current = current.getSuperclass();
        }

        List<Method> methods = new ArrayList<>();
        for (Class<?> hierarchyType : hierarchy) {
            for (Method method : hierarchyType.getDeclaredMethods()) {
                if (!hasAroundInvokeAnnotation(method, override)
                        || Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                if (method.getParameterCount() == 1 &&
                        jakarta.interceptor.InvocationContext.class.isAssignableFrom(method.getParameterTypes()[0])) {
                    method.setAccessible(true);
                    methods.add(method);
                }
            }
        }
        return methods;
    }

    private Annotation[] resolveLegacyInterceptorClassAnnotations(jakarta.enterprise.inject.spi.AnnotatedType<?> beanAnnotatedType) {
        if (beanAnnotatedType != null) {
            return beanAnnotatedType.getAnnotations().toArray(new Annotation[0]);
        }
        return beanClass.getAnnotations();
    }

    private Annotation[] resolveLegacyInterceptorMethodAnnotations(
            Method method,
            jakarta.enterprise.inject.spi.AnnotatedType<?> beanAnnotatedType
    ) {
        if (method == null) {
            return new Annotation[0];
        }
        if (beanAnnotatedType != null) {
            return AnnotationsHelper.annotationsOf(beanAnnotatedType, method);
        }
        return method.getAnnotations();
    }

    private List<Class<?>> extractLegacyInterceptorClasses(Annotation[] annotations) {
        if (annotations == null) {
            return Collections.emptyList();
        }
        for (Annotation annotation : annotations) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (!AnnotationsEnum.INTERCEPTORS.matches(annotationType)) {
                continue;
            }
            try {
                Method valueMethod = annotationType.getMethod("value");
                Object raw = valueMethod.invoke(annotation);
                if (raw instanceof Class[]) {
                    Class<?>[] classes = (Class<?>[]) raw;
                    return Arrays.asList(classes);
                }
            } catch (Exception ignored) {
                return Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }

    private InterceptorChainModel buildLegacyInterceptorChain(List<Class<?>> interceptorClasses) {
        if (interceptorClasses == null || interceptorClasses.isEmpty()) {
            return null;
        }
        InterceptorChainModel.Builder builder = interceptorSupport.newChainBuilder();
        for (Class<?> interceptorClass : interceptorClasses) {
            if (interceptorClass == null) {
                continue;
            }
            Method aroundInvokeMethod = findAroundInvokeMethod(interceptorClass);
            if (aroundInvokeMethod != null) {
                final Method interceptorMethod = aroundInvokeMethod;
                builder.addInvocation(ctx -> invokeInterceptorMethod(
                        getOrCreateLegacyInterceptorInstance(interceptorClass, resolveInterceptionTarget(ctx)),
                        interceptorMethod,
                        ctx
                ));
            }
        }
        return builder.build();
    }

    private Method findAroundInvokeMethod(Class<?> interceptorClass) {
        Class<?> current = interceptorClass;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (AnnotationsHelper.hasAroundInvokeAnnotation(method)) {
                    if (Modifier.isStatic(method.getModifiers())) {
                        continue;
                    }
                    if (method.getParameterCount() == 1 &&
                            jakarta.interceptor.InvocationContext.class.isAssignableFrom(method.getParameterTypes()[0])) {
                        method.setAccessible(true);
                        return method;
                    }
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private Object getOrCreateLegacyInterceptorInstance(Class<?> interceptorClass, Object interceptionTarget) {
        if (interceptorClass == null) {
            return null;
        }
        Map<Class<?>, Object> cache = resolveInterceptorInstanceCache(interceptionTarget);
        Object existing = cache.get(interceptorClass);
        if (existing != null) {
            return existing;
        }
        synchronized (cache) {
            existing = cache.get(interceptorClass);
            if (existing != null) {
                return existing;
            }
            Object created = createLegacyInterceptorInstance(interceptorClass);
            cache.put(interceptorClass, created);
            return created;
        }
    }

    // ====================================================================================
    // Interceptor Instance Creation with Dependency Injection
    // ====================================================================================

    /**
     * Selects the appropriate constructor for interceptor instantiation.
     *
     * <p>CDI constructor selection rules:
     * <ol>
     *   <li>If there's a constructor annotated with @Inject, use it</li>
     *   <li>Otherwise, if there's only one public constructor, use it</li>
     *   <li>Otherwise, use the no-arg constructor</li>
     * </ol>
     */
    private Constructor<?> selectInterceptorConstructor(Class<?> interceptorClass) {
        Constructor<?>[] constructors = interceptorClass.getDeclaredConstructors();

        // Look for @Inject constructor
        for (Constructor<?> ctor : constructors) {
            if (AnnotationsHelper.hasInjectAnnotation(ctor)) {
                return ctor;
            }
        }

        // If only one public constructor, use it (CDI implicit constructor injection)
        Constructor<?>[] publicConstructors = interceptorClass.getConstructors();
        if (publicConstructors.length == 1) {
            return publicConstructors[0];
        }

        // Otherwise, use no-arg constructor
        try {
            return interceptorClass.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(
                "Interceptor " + interceptorClass.getName() +
                " has no suitable constructor (no @Inject, not exactly one public, no no-arg)", e
            );
        }
    }

    /**
     * Resolves constructor parameters via dependency injection.
     */
    private Object[] resolveConstructorParameters(Constructor<?> constructor, Bean<?> interceptorMetadataBean) {
        Parameter[] parameters = constructor.getParameters();
        Object[] args = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            Type paramType = param.getParameterizedType();
            Annotation[] paramAnnotations = param.getAnnotations();
            Bean<?> owningBean = resolveInterceptorInjectionOwningBean(paramAnnotations, interceptorMetadataBean);
            InjectionPoint injectionPoint = new InjectionPointImpl<>(
                    param,
                    castBean(owningBean),
                    paramType,
                    paramAnnotations,
                    null
            );

            // Use dependency resolver to resolve the parameter
            if (dependencyResolver != null) {
                args[i] = resolveInjectionPointWithContext(
                        paramType,
                        paramAnnotations,
                        injectionPoint,
                        null
                );
            } else {
                throw new IllegalStateException(
                    "DependencyResolver not set for bean " + beanClass.getName() +
                    " - cannot inject constructor parameter " + param.getName()
                );
            }
        }

        return args;
    }

    /**
     * Injects @Inject annotated fields on the interceptor instance.
     */
    private void injectInterceptorFields(Object instance, Class<?> clazz, Bean<?> interceptorMetadataBean)
            throws IllegalAccessException {
        if (dependencyResolver == null) {
            return; // No injection possible without resolver
        }

        // Find all @Inject fields (including private, inherited)
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            for (Field field : currentClass.getDeclaredFields()) {
                if (AnnotationsHelper.hasInjectAnnotation(field)) {
                    field.setAccessible(true);

                    Type fieldType = field.getGenericType();
                    Annotation[] fieldAnnotations = field.getAnnotations();
                    Bean<?> owningBean = resolveInterceptorInjectionOwningBean(fieldAnnotations, interceptorMetadataBean);
                    InjectionPoint injectionPoint = new InjectionPointImpl<>(
                            field,
                            castBean(owningBean),
                            fieldType,
                            fieldAnnotations,
                            null
                    );

                    // Resolve and inject the dependency
                    Object value = resolveInjectionPointWithContext(
                            fieldType,
                            fieldAnnotations,
                            injectionPoint,
                            null
                    );
                    field.set(instance, value);
                }
            }
            currentClass = currentClass.getSuperclass();
        }
    }

    /**
     * Invokes @Inject annotated methods on the interceptor instance.
     */
    private void injectInterceptorMethods(Object instance, Class<?> clazz, Bean<?> interceptorMetadataBean)
            throws Exception {
        if (dependencyResolver == null) {
            return; // No injection possible without resolver
        }

        // Find all @Inject methods (including private, inherited)
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            for (Method method : currentClass.getDeclaredMethods()) {
                if (AnnotationsHelper.hasInjectAnnotation(method)) {
                    method.setAccessible(true);

                    // Resolve method parameters
                    Parameter[] parameters = method.getParameters();
                    Object[] args = new Object[parameters.length];

                    for (int i = 0; i < parameters.length; i++) {
                        Parameter param = parameters[i];
                        Type paramType = param.getParameterizedType();
                        Annotation[] paramAnnotations = param.getAnnotations();
                        Bean<?> owningBean = resolveInterceptorInjectionOwningBean(paramAnnotations, interceptorMetadataBean);
                        InjectionPoint injectionPoint = new InjectionPointImpl<>(
                                param,
                                castBean(owningBean),
                                paramType,
                                paramAnnotations,
                                null
                        );

                        args[i] = resolveInjectionPointWithContext(
                                paramType,
                                paramAnnotations,
                                injectionPoint,
                                null
                        );
                    }

                    // Invoke the injector method
                    method.invoke(instance, args);
                }
            }
            currentClass = currentClass.getSuperclass();
        }
    }

    private Bean<?> resolveInterceptorMetadataBean(InterceptorInfo interceptorInfo) {
        if (interceptorInfo == null || interceptorInfo.getInterceptorClass() == null) {
            return this;
        }

        Class<?> interceptorClass = interceptorInfo.getInterceptorClass();
        Bean<?> cached = interceptorMetadataBeanCache.get(interceptorClass);
        if (cached != null) {
            return cached;
        }

        if (beanManager instanceof BeanManagerImpl) {
            InterceptionType interceptionType = resolveMetadataInterceptionType(interceptorInfo);
            if (interceptionType != null) {
                Annotation[] bindings = interceptorInfo.getInterceptorBindings().toArray(new Annotation[0]);
                try {
                    List<Interceptor<?>> interceptors =
                            beanManager.resolveInterceptors(interceptionType, bindings);
                    for (Interceptor<?> interceptor : interceptors) {
                        if (interceptor != null && interceptorClass.equals(interceptor.getBeanClass())) {
                            interceptorMetadataBeanCache.putIfAbsent(interceptorClass, interceptor);
                            return interceptor;
                        }
                    }
                } catch (RuntimeException ignored) {
                    // During bootstrap BeanManager lifecycle guards may reject SPI calls.
                }
            }
        }

        Bean<?> fallback = createInterceptorMetadataFallback(interceptorInfo);
        interceptorMetadataBeanCache.putIfAbsent(interceptorClass, fallback);
        return fallback;
    }

    private InterceptionType resolveMetadataInterceptionType(InterceptorInfo interceptorInfo) {
        if (interceptorInfo.getAroundInvokeMethod() != null) {
            return InterceptionType.AROUND_INVOKE;
        }
        if (interceptorInfo.getAroundConstructMethod() != null) {
            return InterceptionType.AROUND_CONSTRUCT;
        }
        if (interceptorInfo.getPostConstructMethod() != null) {
            return InterceptionType.POST_CONSTRUCT;
        }
        if (interceptorInfo.getPreDestroyMethod() != null) {
            return InterceptionType.PRE_DESTROY;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <I> Bean<?> createInterceptorMetadataFallback(InterceptorInfo interceptorInfo) {
        final Class<?> interceptorClass = interceptorInfo.getInterceptorClass();
        final Set<Annotation> bindings = Collections.unmodifiableSet(
                new HashSet<>(interceptorInfo.getInterceptorBindings()));
        return new Interceptor<I>() {
            @Override
            public Set<Annotation> getInterceptorBindings() {
                return bindings;
            }

            @Override
            public boolean intercepts(InterceptionType type) {
                if (type == null) {
                    return false;
                }
                switch (type) {
                    case AROUND_INVOKE:
                        return interceptorInfo.getAroundInvokeMethod() != null;
                    case AROUND_CONSTRUCT:
                        return interceptorInfo.getAroundConstructMethod() != null;
                    case POST_CONSTRUCT:
                        return interceptorInfo.getPostConstructMethod() != null;
                    case PRE_DESTROY:
                        return interceptorInfo.getPreDestroyMethod() != null;
                    default:
                        return false;
                }
            }

            @Override
            public Object intercept(InterceptionType type, I instance, jakarta.interceptor.InvocationContext ctx) throws Exception {
                Method interceptorMethod;
                switch (type) {
                    case AROUND_INVOKE:
                        interceptorMethod = interceptorInfo.getAroundInvokeMethod();
                        break;
                    case AROUND_CONSTRUCT:
                        interceptorMethod = interceptorInfo.getAroundConstructMethod();
                        break;
                    case POST_CONSTRUCT:
                        interceptorMethod = interceptorInfo.getPostConstructMethod();
                        break;
                    case PRE_DESTROY:
                        interceptorMethod = interceptorInfo.getPreDestroyMethod();
                        break;
                    default:
                        interceptorMethod = null;
                }
                if (interceptorMethod == null) {
                    return ctx != null ? ctx.proceed() : null;
                }
                if (!interceptorMethod.isAccessible()) {
                    interceptorMethod.setAccessible(true);
                }
                try {
                    return interceptorMethod.invoke(instance, ctx);
                } catch (InvocationTargetException e) {
                    Throwable target = e.getTargetException();
                    if (target instanceof Exception) {
                        throw (Exception) target;
                    }
                    throw new RuntimeException(target);
                }
            }

            @Override
            public Class<?> getBeanClass() {
                return interceptorClass;
            }

            @Override
            public Set<InjectionPoint> getInjectionPoints() {
                return Collections.emptySet();
            }

            @Override
            public I create(CreationalContext<I> context) {
                try {
                    return (I) interceptorClass.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create interceptor metadata fallback for "
                            + interceptorClass.getName(), e);
                }
            }

            @Override
            public void destroy(I instance, CreationalContext<I> context) {
                if (context != null) {
                    context.release();
                }
            }

            @Override
            public Set<Type> getTypes() {
                Set<Type> types = new HashSet<>();
                types.add(interceptorClass);
                types.add(Object.class);
                return Collections.unmodifiableSet(types);
            }

            @Override
            public Set<Annotation> getQualifiers() {
                Set<Annotation> qualifiers = new HashSet<>();
                qualifiers.add(jakarta.enterprise.inject.Default.Literal.INSTANCE);
                qualifiers.add(jakarta.enterprise.inject.Any.Literal.INSTANCE);
                return qualifiers;
            }

            @Override
            public Class<? extends Annotation> getScope() {
                return Dependent.class;
            }

            @Override
            public String getName() {
                return null;
            }

            @Override
            public Set<Class<? extends Annotation>> getStereotypes() {
                return Collections.emptySet();
            }

            @Override
            public boolean isAlternative() {
                return false;
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                }
                if (!(obj instanceof Bean<?>)) {
                    return false;
                }
                Bean<?> other = (Bean<?>) obj;
                return Objects.equals(interceptorClass, other.getBeanClass());
            }

            @Override
            public int hashCode() {
                return Objects.hashCode(interceptorClass);
            }
        };
    }

    private Bean<?> resolveInterceptorInjectionOwningBean(Annotation[] annotations, Bean<?> interceptorMetadataBean) {
        if (hasInterceptedQualifier(annotations)) {
            return this;
        }
        if (interceptorMetadataBean != null) {
            return interceptorMetadataBean;
        }
        return this;
    }

    private boolean hasInterceptedQualifier(Annotation[] annotations) {
        if (annotations == null) {
            return false;
        }
        for (Annotation annotation : annotations) {
            if (annotation != null && AnnotationsHelper.hasInterceptedAnnotation(annotation.annotationType())) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private <X> Bean<X> castBean(Bean<?> bean) {
        return (Bean<X>) bean;
    }

    /**
     * Invokes @PostConstruct lifecycle callback on the interceptor instance.
     */
    private void invokeInterceptorPostConstruct(Object instance, Class<?> clazz) throws Exception {
        // Find @PostConstruct method (including private, inherited)
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            for (Method method : currentClass.getDeclaredMethods()) {
                if (AnnotationsHelper.hasPostConstructAnnotation(method) && method.getParameterCount() == 0) {
                    method.setAccessible(true);
                    method.invoke(instance);
                    return; // Only one @PostConstruct per class hierarchy
                }
            }
            currentClass = currentClass.getSuperclass();
        }
    }

    // ====================================================================================
    // PHASE 3: Decorator Support
    // ====================================================================================

    /**
     * Checks if any decorators apply to this bean.
     *
     * <p>This is used to determine whether to wrap the bean instance with decorators.
     *
     * @return true if this bean has decorators, false otherwise
     */
    public boolean hasDecorators() {
        if (decoratorSupport == null) {
            return false;
        }
        return decoratorSupport.hasDecorators(getTypes(), getQualifiers());
    }

    /**
     * Creates a decorator chain wrapping the target instance.
     *
     * <p>This method:
     * <ol>
     *   <li>Resolves decorators that apply to this bean (by type)</li>
     *   <li>Creates decorator instances with @Delegate injection</li>
     *   <li>Returns the outermost decorator (what client code sees)</li>
     * </ol>
     *
     * <p><b>Decorator vs Interceptor Wrapping:</b>
     * <ul>
     *   <li><b>Interceptors</b>: Single proxy with method interception</li>
     *   <li><b>Decorators</b>: Nested instances with @Delegate injection</li>
     * </ul>
     *
     * <p><b>Wrapping Order:</b>
     * <pre>
     * Client → Decorator1 (priority=100) → Decorator2 (priority=200) → Target (possibly interceptor proxy)
     * </pre>
     *
     * @param targetInstance the bean instance to decorate (may already be an interceptor proxy)
     * @param creationalContext the CreationalContext for managing decorator lifecycle
     * @return the outermost decorator instance, or target if no decorators apply
     * @throws IllegalStateException if decorator support isn't configured
     */
    @SuppressWarnings("unchecked")
    public T createDecoratorChain(T targetInstance, CreationalContext<T> creationalContext) {
        if (decoratorSupport == null) {
            throw new IllegalStateException(
                "DecoratorSupport not set for bean: " + beanClass.getName()
            );
        }

        // Resolve decorators that apply to this bean
        List<DecoratorInfo> decorators =
                decoratorSupport.resolve(getTypes(), getQualifiers());

        if (decorators.isEmpty()) {
            // No decorators apply, return target as-is
            return targetInstance;
        }

        // Check if BeanManager is available
        if (beanManager == null) {
            throw new IllegalStateException(
                "BeanManager not set for bean: " + beanClass.getName() +
                " - cannot create decorator instances"
            );
        }

        Object wrapped = decoratorSupport.applyDecoratorChain(
                targetInstance,
                decorators,
                beanManager,
                creationalContext
        );
        return wrapped != null ? (T) wrapped : targetInstance;
    }
}
