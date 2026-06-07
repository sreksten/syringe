package com.threeamigos.common.util.implementations.injection.interceptors;

import com.threeamigos.common.util.implementations.injection.discovery.NonPortableBehaviourException;
import com.threeamigos.common.util.implementations.injection.scopes.ByteBuddyClientProxyGenerator;
import com.threeamigos.common.util.implementations.injection.spi.configurators.AnnotatedTypeConfiguratorImpl;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.UnproxyableResolutionException;
import jakarta.enterprise.inject.spi.*;
import jakarta.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.hasAroundInvokeAnnotation;

/**
 * Implementation of {@link InterceptionFactory} for CDI 4.1.
 *
 * <p>InterceptionFactory allows you to dynamically add interceptor bindings to objects
 * created by producer methods. This is essential because producer methods create plain
 * instances, not CDI proxies, so they don't normally support interception.
 *
 * <h2>CDI 4.1 Section 9.6: InterceptionFactory</h2>
 * <p>An InterceptionFactory may be obtained by:
 * <ul>
 *   <li>Injecting as a parameter in a producer method</li>
 *   <li>Calling BeanManager.createInterceptionFactory(CreationalContext, Class)</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * @Produces
 * @ApplicationScoped
 * public MyService produceService(InterceptionFactory<MyService> factory) {
 *     // Step 1: Configure - add interceptor bindings dynamically
 *     factory.configure()
 *            .add(new AnnotationLiteral<Transactional>() {})
 *            .add(new AnnotationLiteral<Logged>() {});
 *
 *     // Step 2: Create intercepted instance - wraps in proxy
 *     return factory.createInterceptedInstance(new MyServiceImpl());
 * }
 * }</pre>
 *
 * <h2>Key Methods:</h2>
 * <ul>
 *   <li><b>configure()</b> - Returns AnnotatedTypeConfigurator for adding interceptor bindings</li>
 *   <li><b>createInterceptedInstance(T)</b> - Wraps instance in interceptor-aware proxy</li>
 *   <li><b>ignoreFinalMethods()</b> - Skips final methods during proxy generation</li>
 * </ul>
 *
 * @param <T> the type of the bean to intercept
 * @author Stefano Reksten
 */
public class InterceptionFactoryImpl<T> implements InterceptionFactory<T> {

    private final Class<T> clazz;
    private final CreationalContext<T> creationalContext;
    private final BeanManager beanManager;
    private final InterceptorAwareProxyGenerator proxyGenerator;
    private final AnnotatedTypeConfiguratorImpl<T> configurator;

    private boolean ignoreFinalMethods = false;
    private boolean createInterceptedInstanceCalled = false;

    /**
     * Constructs an InterceptionFactory for the specified class.
     *
     * @param clazz the class to create intercepted instances for
     * @param creationalContext the creational context for managing dependencies
     * @param beanManager the bean manager for looking up interceptors
     * @param proxyGenerator the proxy generator for creating interceptor proxies
     */
    public InterceptionFactoryImpl(Class<T> clazz,
                                   CreationalContext<T> creationalContext,
                                   BeanManager beanManager,
                                   InterceptorAwareProxyGenerator proxyGenerator) {
        if (clazz == null) {
            throw new IllegalArgumentException("clazz cannot be null");
        }
        if (creationalContext == null) {
            throw new IllegalArgumentException("creationalContext cannot be null");
        }
        if (beanManager == null) {
            throw new IllegalArgumentException("beanManager cannot be null");
        }
        if (proxyGenerator == null) {
            throw new IllegalArgumentException("proxyGenerator cannot be null");
        }

        this.clazz = clazz;
        this.creationalContext = creationalContext;
        this.beanManager = beanManager;
        this.proxyGenerator = proxyGenerator;

        // Create AnnotatedType for the class
        AnnotatedType<T> annotatedType = beanManager.createAnnotatedType(clazz);
        this.configurator = new AnnotatedTypeConfiguratorImpl<>(annotatedType);
    }

    /**
     * Instructs the proxy generator to ignore final methods during proxy creation.
     *
     * <p>CDI 4.1: Final methods cannot be overridden, so they cannot be intercepted.
     * By default, attempting to create a proxy for a class with final methods may
     * result in an error. This method tells the proxy generator to skip final methods.
     *
     * <p>Note: Final methods will be invoked directly on the target instance without
     * interception.
     *
     * @return this InterceptionFactory instance for method chaining
     */
    @Override
    public InterceptionFactory<T> ignoreFinalMethods() {
        this.ignoreFinalMethods = true;
        return this;
    }

    /**
     * Returns an AnnotatedTypeConfigurator for dynamically modifying the bean's metadata.
     *
     * <p>CDI 4.1 Section 9.6: This method allows you to add interceptor bindings,
     * qualifiers, stereotypes, and other annotations to the bean type dynamically.
     *
     * <p>The configurator is initialized with the AnnotatedType for the class passed
     * to the InterceptionFactory constructor.
     *
     * <h3>Example: Adding Interceptor Bindings</h3>
     * <pre>{@code
     * factory.configure()
     *        .add(new AnnotationLiteral<Transactional>() {})
     *        .add(new AnnotationLiteral<Logged>() {});
     * }</pre>
     *
     * @return the AnnotatedTypeConfigurator for this bean type
     */
    @Override
    public AnnotatedTypeConfigurator<T> configure() {
        return configurator;
    }

    /**
     * Creates an intercepted instance by wrapping the given instance in a proxy.
     *
     * <p>CDI 4.1 Section 9.6: This method takes a plain object and returns a proxy
     * that applies all configured interceptors.
     *
     * <p><b>How it works:</b>
     * <ol>
     *   <li>Retrieves all interceptor bindings from the configured AnnotatedType</li>
     *   <li>Resolves interceptors for those bindings via BeanManager</li>
     *   <li>Creates a ByteBuddy proxy that intercepts method calls</li>
     *   <li>Returns the proxy with the original instance as the target</li>
     * </ol>
     *
     * <p><b>Important:</b> The instance parameter should be the actual object to wrap.
     * Do not pass an already-proxied instance.
     *
     * @param instance the plain object to wrap in an interceptor proxy
     * @return the intercepted proxy instance
     * @throws IllegalArgumentException if instance is null
     * @throws IllegalStateException if proxy creation fails
     */
    @Override
    public T createInterceptedInstance(T instance) {
        if (instance == null) {
            throw new IllegalArgumentException("instance cannot be null");
        }
        if (createInterceptedInstanceCalled) {
            throw new IllegalStateException("createInterceptedInstance() can only be called once");
        }
        createInterceptedInstanceCalled = true;

        if (instance instanceof ByteBuddyClientProxyGenerator.ProxyState ||
                instance instanceof InterceptorAwareProxyGenerator.InterceptorProxyState) {
            throw new NonPortableBehaviourException(
                    "Non-portable behavior: InterceptionFactory.createInterceptedInstance() must not be given an internal container construct");
        }

        if (!ignoreFinalMethods) {
            String reason = unproxyableReason(clazz);
            if (reason != null) {
                throw new UnproxyableResolutionException("Type " + clazz.getName() + " is not proxyable: " + reason);
            }
        }

        // Get the configured AnnotatedType (with any added annotations)
        AnnotatedType<T> configuredType = configurator.complete();

        Map<Method, Set<Annotation>> interceptorBindingsByMethod =
                extractInterceptorBindingsByMethod(configuredType);

        if (interceptorBindingsByMethod.isEmpty()) {
            // No interceptors to apply - return original instance
            return instance;
        }

        // Build interceptor chains for each method
        Map<Method, InterceptorChain> methodInterceptorChains = buildInterceptorChains(
                interceptorBindingsByMethod
        );

        if (methodInterceptorChains.isEmpty()) {
            return instance;
        }

        // Create interceptor-aware proxy
        try {
            // Use InterceptorAwareProxyGenerator to create a proxy with interceptors
            // The proxy will delegate to the original instance

            return proxyGenerator.createProxy(
                clazz,
                instance,
                methodInterceptorChains
            );
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to create intercepted instance for " + clazz.getName() +
                ": " + e.getMessage(), e
            );
        }
    }

    /**
     * Builds interceptor chains for all business methods of the class.
     *
     * <p>For each public method that can be intercepted:
     * <ol>
     *   <li>Resolve interceptors for an AROUND_INVOKE interception type</li>
     *   <li>Create an InterceptorChain linking all interceptors</li>
     *   <li>Map the method to its chain</li>
     * </ol>
     *
     * @param interceptorBindingsByMethod the interceptor bindings to apply
     * @return map of methods to their interceptor chains
     */
    private Map<Method, InterceptorChain> buildInterceptorChains(Map<Method, Set<Annotation>> interceptorBindingsByMethod) {

        Map<Method, InterceptorChain> chains = new HashMap<>();

        if (interceptorBindingsByMethod.isEmpty()) {
            return chains; // No interceptors to apply
        }

        for (Map.Entry<Method, Set<Annotation>> entry : interceptorBindingsByMethod.entrySet()) {
            Method method = entry.getKey();
            Set<Annotation> interceptorBindings = entry.getValue();
            if (method == null || interceptorBindings == null || interceptorBindings.isEmpty()) {
                continue;
            }
            if (shouldSkipMethod(method)) {
                continue;
            }

            Annotation[] bindingsArray = interceptorBindings.toArray(new Annotation[0]);
            List<Interceptor<?>> interceptors = beanManager.resolveInterceptors(
                    InterceptionType.AROUND_INVOKE,
                    bindingsArray
            );
            if (interceptors.isEmpty()) {
                continue;
            }

            // Create interceptor chain for this method using builder
            InterceptorChain.Builder chainBuilder = InterceptorChain.builder()
                    .withInterceptorBindings(interceptorBindings);

            for (Interceptor<?> interceptor : interceptors) {
                Object interceptorInstance = beanManager.getReference(
                        interceptor,
                        interceptor.getBeanClass(),
                        creationalContext
                );
                Method aroundInvokeMethod = findAroundInvokeMethod(interceptor.getBeanClass());
                if (aroundInvokeMethod != null) {
                    chainBuilder.addInterceptor(interceptorInstance, aroundInvokeMethod);
                }
            }

            chains.put(method, chainBuilder.build());
        }

        return chains;
    }

    /**
     * Finds the @AroundInvoke method in an interceptor class.
     *
     * @param interceptorClass the interceptor class
     * @return the @AroundInvoke method, or null if not found
     */
    private Method findAroundInvokeMethod(Class<?> interceptorClass) {
        for (Method method : interceptorClass.getDeclaredMethods()) {
            if (hasAroundInvokeAnnotation(method)) {
                return method;
            }
        }
        // Check superclasses
        Class<?> superClass = interceptorClass.getSuperclass();
        if (superClass != null && !superClass.equals(Object.class)) {
            return findAroundInvokeMethod(superClass);
        }
        return null;
    }

    /**
     * Determines if a method should be skipped during interception.
     *
     * <p>Methods are skipped if:
     * <ul>
     *   <li>Declared in Object class (toString, equals, hashCode, etc.)</li>
     *   <li>Final methods (if ignoreFinalMethods is true)</li>
     *   <li>Static methods (not interceptable)</li>
     * </ul>
     *
     * @param method the method to check
     * @return true if the method should not be intercepted
     */
    private boolean shouldSkipMethod(Method method) {
        // Skip methods from Object class
        if (method.getDeclaringClass().equals(Object.class)) {
            return true;
        }

        // Skip static methods
        if (Modifier.isStatic(method.getModifiers())) {
            return true;
        }

        // Skip final methods if configured
        return ignoreFinalMethods && Modifier.isFinal(method.getModifiers());
    }

    private String unproxyableReason(Class<?> rawType) {
        if (rawType == null) {
            return null;
        }
        if (rawType.isPrimitive() || rawType.isArray()) {
            return "primitive or array type";
        }
        int classModifiers = rawType.getModifiers();
        if (Modifier.isFinal(classModifiers)) {
            return "final class";
        }
        if (rawType.getTypeParameters().length > 0) {
            return "generic class with type parameters";
        }
        for (Class<?> current = rawType; current != null && !Object.class.equals(current); current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                int modifiers = method.getModifiers();
                if (!Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers) &&
                        !Modifier.isPrivate(modifiers)) {
                    return "declares non-static final method " + current.getName() + "#" + method.getName();
                }
            }
        }
        return null;
    }

    /**
     * Extracts interceptor-binding annotations from the AnnotatedType.
     *
     * <p>An annotation is considered an interceptor binding if:
     * <ul>
     *   <li>It is annotated with @InterceptorBinding</li>
     *   <li>It is registered as an interceptor binding via BeforeBeanDiscovery</li>
     * </ul>
     *
     * @param annotatedType the configured annotated type
     * @return set of interceptor binding annotations
     */
    private Set<Annotation> extractInterceptorBindings(AnnotatedType<T> annotatedType) {
        Set<Annotation> bindings = new HashSet<>();

        for (Annotation annotation : annotatedType.getAnnotations()) {
            if (beanManager.isInterceptorBinding(annotation.annotationType())) {
                bindings.add(annotation);
            }
        }

        return bindings;
    }

    private Map<Method, Set<Annotation>> extractInterceptorBindingsByMethod(AnnotatedType<T> annotatedType) {
        Map<Method, Set<Annotation>> bindingsByMethod = new HashMap<>();
        Set<Annotation> classBindings = extractInterceptorBindings(annotatedType);

        Map<Method, Set<Annotation>> configuredMethodBindings = new HashMap<>();
        for (AnnotatedMethod<? super T> annotatedMethod : annotatedType.getMethods()) {
            if (annotatedMethod == null || annotatedMethod.getJavaMember() == null) {
                continue;
            }
            Set<Annotation> methodBindings = new LinkedHashSet<>();
            for (Annotation annotation : annotatedMethod.getAnnotations()) {
                if (beanManager.isInterceptorBinding(annotation.annotationType())) {
                    methodBindings.add(annotation);
                }
            }
            if (!methodBindings.isEmpty()) {
                configuredMethodBindings.put(annotatedMethod.getJavaMember(), methodBindings);
            }
        }

        for (Method method : clazz.getMethods()) {
            if (shouldSkipMethod(method)) {
                continue;
            }

            Set<Annotation> bindings = new LinkedHashSet<>(classBindings);
            Set<Annotation> methodBindings = configuredMethodBindings.get(method);
            if (methodBindings != null) {
                bindings.addAll(methodBindings);
            } else {
                for (Annotation annotation : method.getAnnotations()) {
                    if (beanManager.isInterceptorBinding(annotation.annotationType())) {
                        bindings.add(annotation);
                    }
                }
            }

            if (!bindings.isEmpty()) {
                bindingsByMethod.put(method, bindings);
            }
        }

        return bindingsByMethod;
    }

    /**
     * Returns the class this InterceptionFactory was created for.
     *
     * @return the bean class
     */
    public Class<T> getBeanClass() {
        return clazz;
    }

    @Override
    public String toString() {
        return "InterceptionFactoryImpl{" +
               "clazz=" + clazz.getName() +
               ", ignoreFinalMethods=" + ignoreFinalMethods +
               '}';
    }
}
