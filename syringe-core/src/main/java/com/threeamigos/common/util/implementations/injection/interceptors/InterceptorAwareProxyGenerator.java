package com.threeamigos.common.util.implementations.injection.interceptors;

import com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates;

import com.threeamigos.common.util.implementations.injection.discovery.NonPortableBehaviourException;
import com.threeamigos.common.util.implementations.injection.resolution.DestroyedInstanceTracker;
import com.threeamigos.common.util.implementations.injection.scopes.ClientProxyGenerator;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedType;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.*;
import net.bytebuddy.matcher.ElementMatchers;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.hasAroundInvokeAnnotation;

/**
 * Generates proxies that integrate interceptor chain execution with business method calls.
 * <p>
 * This generator extends the basic proxy functionality to support CDI interceptors.
 * When a business method is called on a proxy:
 * 1. Check if the method has interceptors configured
 * 2. If yes, execute the interceptor chain (which eventually calls the target method)
 * 3. If no, directly jakarta.enterprise.invoke the target method
 *
 * <h2>Architecture</h2>
 * <pre>
 * Client Code
 *     ↓ calls method
 * Interceptor-Aware Proxy
 *     ↓ checks for interceptors
 *     ├─→ Has Interceptors? → Execute InterceptorChain → [Interceptor 1] → [Interceptor 2] → Target Method
 *     └─→ No Interceptors?  → Directly jakarta.enterprise.invoke Target Method
 * </pre>
 *
 * <h2>Key Differences from ClientProxyGenerator</h2>
 * <ul>
 * <li><b>ClientProxyGenerator</b>: Delegates to contextual instances for normal-scoped beans</li>
 * <li><b>InterceptorAwareProxyGenerator</b>: Wraps method calls with interceptor chain execution</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>
 * // Bean with interceptors
 * {@literal @}ApplicationScoped
 * {@literal @}Transactional // Interceptor binding
 * public class OrderService {
 *     public void createOrder(Order order) {
 *         // Business logic
 *     }
 * }
 *
 * // At runtime:
 * OrderService proxy = injector.getInstance(OrderService.class);
 * proxy.createOrder(order);
 * // → TransactionalInterceptor.aroundInvoke()
 * //   → Begin transaction
 * //   → OrderService.createOrder()
 * //   → Commit transaction
 * </pre>
 *
 * <h2>Thread Safety</h2>
 * <ul>
 * <li>Proxy classes are cached (thread-safe via ConcurrentHashMap)</li>
 * <li>InterceptorChains are immutable and thread-safe</li>
 * <li>Each invocation creates a new InvocationContext (thread-local)</li>
 * </ul>
 *
 * @author Stefano Reksten
 * @see InterceptorChain
 * @see InvocationContextImpl
 * @see ClientProxyGenerator
 */
public class InterceptorAwareProxyGenerator {

    // Cache generated proxy classes to avoid regenerating for the same bean type
    // Key: Bean class
    // Value: Generated proxy class
    private final ConcurrentHashMap<Class<?>, Class<?>> proxyClassCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Optional<Method>> targetAroundInvokeCache =
            new ConcurrentHashMap<>();

    public static void clearTargetAroundInvokeCache() {
        targetAroundInvokeCache.clear();
    }

    public static void clearTargetAroundInvokeCacheForClassLoader(ClassLoader classLoader) {
        if (classLoader == null) {
            return;
        }
        targetAroundInvokeCache.entrySet().removeIf(entry -> {
            Class<?> type = entry.getKey();
            return type != null && type.getClassLoader() == classLoader;
        });
    }

    public void clearCache() {
        proxyClassCache.clear();
    }

    /**
     * Creates an interceptor-aware proxy for a bean.
     * <p>
     * The proxy will:
     * <ul>
     * <li>Intercept all non-private business methods (public, protected, package-private)</li>
     * <li>Check if each method has interceptors configured (from methodInterceptorChains map)</li>
     * <li>Execute an interceptor chain if present, or direct invocation if not</li>
     * <li>Return the result to the caller</li>
     * </ul>
     *
     * <h3>Method Interception Decision</h3>
     * The proxy uses the methodInterceptorChains map to determine which methods to intercept:
     * <pre>
     * Map&lt;Method, InterceptorChain&gt; methodInterceptorChains = {
     *     OrderService.createOrder() → [TransactionalInterceptor, LoggingInterceptor],
     *     OrderService.deleteOrder() → [SecurityInterceptor],
     *     OrderService.getOrder() → null // No interceptors, direct call
     * }
     * </pre>
     *
     * @param <T> the bean type
     * @param bean the bean to create a proxy for
     * @param targetInstance the actual bean instance that will receive method calls
     * @param methodInterceptorChains map of methods to their interceptor chains
     * @return a proxy instance that executes interceptors before delegating to targetInstance
     * @throws RuntimeException if proxy creation fails
     */
    @SuppressWarnings("unchecked")
    public <T> T createProxy(Bean<T> bean, T targetInstance, Map<Method, InterceptorChain> methodInterceptorChains) {
        return createProxy((Class<T>) bean.getBeanClass(), targetInstance, methodInterceptorChains);
    }

    /**
     * Creates an interceptor-aware proxy for the given class and target instance.
     * <p>
     * PHASE 6: This overload is used by InterceptionFactory for programmatic interception.
     * <p>
     * This method:
     * <ol>
     * <li>Retrieves or generates a proxy class (cached)</li>
     * <li>Instantiates the proxy</li>
     * <li>Initializes the proxy state (target instance and interceptor chains)</li>
     * </ol>
     *
     * @param <T> the bean type
     * @param beanClass the class to create a proxy for
     * @param targetInstance the actual bean instance that will receive method calls
     * @param methodInterceptorChains map of methods to their interceptor chains
     * @return a proxy instance that executes interceptors before delegating to targetInstance
     * @throws RuntimeException if proxy creation fails
     */
    @SuppressWarnings("unchecked")
    public <T> T createProxy(Class<T> beanClass, T targetInstance, Map<Method, InterceptorChain> methodInterceptorChains) {
        // Get or generate the proxy class (cached for performance)
        Class<?> proxyClass = proxyClassCache.computeIfAbsent(beanClass, this::generateProxyClass);

        try {
            // Create an instance of the proxy class
            T proxy = (T) instantiateProxyInstance(proxyClass);

            // Initialize the proxy with the target instance and interceptor chains
            // This is done via the InterceptorProxyState interface that the proxy implements
            if (proxy instanceof InterceptorProxyState) {
                ((InterceptorProxyState) proxy).$$_setInterceptorProxyState(
                    targetInstance,
                    methodInterceptorChains != null ? methodInterceptorChains : new ConcurrentHashMap<>()
                );
            }

            return proxy;
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to create interceptor-aware proxy for class: " + beanClass.getName(), e);
        }
    }

    private Object instantiateProxyInstance(Class<?> proxyClass) throws Exception {
        try {
            Constructor<?> noArg = proxyClass.getDeclaredConstructor();
            noArg.setAccessible(true);
            return noArg.newInstance();
        } catch (NoSuchMethodException ignored) {
            // Fall through to best-effort constructor invocation below.
        }

        Constructor<?>[] constructors = proxyClass.getDeclaredConstructors();
        Arrays.sort(constructors, Comparator.comparingInt(Constructor::getParameterCount));
        for (Constructor<?> constructor : constructors) {
            if (Modifier.isPrivate(constructor.getModifiers())) {
                continue;
            }
            constructor.setAccessible(true);
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            Object[] args = new Object[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                args[i] = defaultValue(parameterTypes[i]);
            }
            return constructor.newInstance(args);
        }

        throw new IllegalStateException("No accessible constructor available for generated proxy " +
                proxyClass.getName());
    }

    private Object defaultValue(Class<?> type) {
        if (type == null || !type.isPrimitive()) {
            return null;
        }
        if (boolean.class.equals(type)) {
            return false;
        }
        if (byte.class.equals(type)) {
            return (byte) 0;
        }
        if (short.class.equals(type)) {
            return (short) 0;
        }
        if (int.class.equals(type)) {
            return 0;
        }
        if (long.class.equals(type)) {
            return 0L;
        }
        if (float.class.equals(type)) {
            return 0f;
        }
        if (double.class.equals(type)) {
            return 0d;
        }
        if (char.class.equals(type)) {
            return '\0';
        }
        return null;
    }

    /**
     * Generates an interceptor-aware proxy class using ByteBuddy.
     * <p>
     * The generated class:
     * <ol>
     * <li><b>Extends</b> the target bean class (for type compatibility)</li>
     * <li><b>Adds fields</b>:
     *     <ul>
     *     <li>$$_targetInstance: The actual bean instance to delegate to</li>
     *     <li>$$_methodInterceptorChains: Map of methods to their interceptor chains</li>
     *     </ul>
     * </li>
     * <li><b>Implements</b> InterceptorProxyState for state initialization</li>
     * <li><b>Implements</b> Serializable for passivation support</li>
     * <li><b>Intercepts</b> all public business methods to check for interceptors</li>
     * </ol>
     *
     * <h3>Constructor Strategy</h3>
     * Uses DEFAULT_CONSTRUCTOR strategy which:
     * <ul>
     * <li>Creates a no-arg constructor</li>
     * <li>Calls super() with default values (null, 0, false)</li>
     * <li>Safe because the proxy shell is never used directly - all calls go through interceptors</li>
     * </ul>
     *
     * <h3>Method Interception Logic</h3>
     * The proxy delegates ALL business methods to {@link InterceptorMethodInterceptor}, which:
     * <pre>
     * 1. Checks if a method has interceptors in the methodInterceptorChains map
     * 2. If YES:
     *    a. Retrieve the InterceptorChain for this method
     *    B. Execute chain.jakarta.enterprise.invoke(targetInstance, method, args)
     *    C. Return result
     * 3. If NO:
     *    a. Directly jakarta.enterprise.invoke method.jakarta.enterprise.invoke(targetInstance, args)
     *    B. Return result
     * </pre>
     *
     * @param beanClass the class to generate a proxy for
     * @return the generated proxy class
     * @throws RuntimeException if proxy generation fails
     */
    private Class<?> generateProxyClass(Class<?> beanClass) {
        try {
            return new ByteBuddy()
                // Create a subclass of the target bean class
                // Imitate superclass constructors so proxy generation also works for classes
                // without a no-arg constructor (for example, synthetic beans with @Inject ctor).
                .subclass(beanClass, ConstructorStrategy.Default.IMITATE_SUPER_CLASS)

                // Add field to store the target instance (the real bean)
                .defineField("$$_targetInstance", Object.class,
                    net.bytebuddy.description.modifier.Visibility.PRIVATE)

                // Add field to store the method-to-interceptor-chain mappings
                .defineField("$$_methodInterceptorChains", Map.class,
                    net.bytebuddy.description.modifier.Visibility.PRIVATE)

                // Implement InterceptorProxyState to allow initialization of proxy state
                .implement(InterceptorProxyState.class)

                // Implement the state setter method (called after proxy creation)
                .method(ElementMatchers.named("$$_setInterceptorProxyState"))
                .intercept(FieldAccessor.ofField("$$_targetInstance").setsArgumentAt(0)
                    .andThen(FieldAccessor.ofField("$$_methodInterceptorChains").setsArgumentAt(1)))

                // Implement the target instance getter
                .method(ElementMatchers.named("$$_getTargetInstance"))
                .intercept(FieldAccessor.ofField("$$_targetInstance"))

                // Implement the interceptor chains getter
                .method(ElementMatchers.named("$$_getMethodInterceptorChains"))
                .intercept(FieldAccessor.ofField("$$_methodInterceptorChains"))

                // Implement Serializable (CDI requirement for passivating scopes)
                .implement(Serializable.class)

                // Intercept ALL business methods (exclude Object methods and our state methods)
                // and delegate them to our InterceptorMethodInterceptor
                .method(ElementMatchers.any()
                    .and(ElementMatchers.not(ElementMatchers.isDeclaredBy(Object.class)))
                    .and(ElementMatchers.not(ElementMatchers.isDeclaredBy(InterceptorProxyState.class)))
                    .and(ElementMatchers.not(ElementMatchers.isPrivate()))
                    .and(ElementMatchers.not(ElementMatchers.isStatic())))
                .intercept(MethodDelegation.to(InterceptorMethodInterceptor.class))

                // Load the class into the same classloader as the target class
                .make()
                .load(beanClass.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                .getLoaded();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate interceptor-aware proxy class for: " +
                beanClass.getName(), e);
        }
    }

    /**
     * Interface implemented by all generated interceptor-aware proxies to store proxy state.
     * <p>
     * This allows us to initialize the proxy after creation with:
     * <ul>
     * <li>The target instance (real bean) to delegate calls to</li>
     * <li>The map of methods to their interceptor chains</li>
     * </ul>
     *
     * <h3>Why is this needed?</h3>
     * ByteBuddy generates the proxy class at runtime, but we need to pass runtime data
     * (target instance, interceptor chains) to the proxy instance. This interface provides
     * the bridge to do that initialization.
     */
    public interface InterceptorProxyState {
        /**
         * Initializes the proxy with its target instance and interceptor chains.
         *
         * @param targetInstance the real bean instance to delegate to
         * @param methodInterceptorChains map of methods to their interceptor chains
         */
        void $$_setInterceptorProxyState(Object targetInstance, Map<Method, InterceptorChain> methodInterceptorChains);

        /**
         * Gets the target instance stored in the proxy.
         *
         * @return the target instance
         */
        Object $$_getTargetInstance();

        /**
         * Gets the method interceptor chains stored in the proxy.
         *
         * @return the method interceptor chains map
         */
        Map<Method, InterceptorChain> $$_getMethodInterceptorChains();
    }

    /**
     * ByteBuddy interceptor that handles method calls on interceptor-aware proxy instances.
     * <p>
     * This is the heart of method interception. For every business method call:
     *
     * <h3>Call Flow</h3>
     * <pre>
     * 1. User calls: proxy.createOrder(order)
     * 2. ByteBuddy intercepts and calls: InterceptorMethodInterceptor.intercept(...)
     * 3. Interceptor checks: Does this method have interceptors?
     * 4a. YES - Execute an interceptor chain:
     *     InterceptorChain.jakarta.enterprise.invoke(targetInstance, method, args)
     *       → Interceptor 1 (e.g., TransactionalInterceptor)
     *       → Interceptor 2 (e.g., LoggingInterceptor)
     *       → Target method (OrderService.createOrder)
     * 4b. NO - Direct invocation:
     *     method.jakarta.enterprise.invoke(targetInstance, args)
     * 5. Return result to the user
     * </pre>
     *
     * <h3>Method Matching</h3>
     * The interceptor uses {@link Method#equals(Object)} to match methods in the map.
     * This works because:
     * <ul>
     * <li>Method.equals() compares declaring class, name, and parameter types</li>
     * <li>The same Method objects are used when building the chain and during invocation</li>
     * </ul>
     *
     * <h3>Performance Considerations</h3>
     * <ul>
     * <li>Map lookup is O(1) - very fast</li>
     * <li>InterceptorChains are pre-built and cached</li>
     * <li>No reflection overhead for non-intercepted methods (direct jakarta.enterprise.invoke)</li>
     * </ul>
     */
    public static class InterceptorMethodInterceptor {

        /**
         * Intercepts method calls on the proxy and either:
         * 1. Executes the interceptor chain (if interceptors exist for this method), OR
         * 2. Directly invokes the method on the target instance (if no interceptors)
         *
         * @param targetInstance the real bean instance from the proxy field
         * @param methodInterceptorChains the method-to-chain map from the proxy field
         * @param method the method being called
         * @param args the method arguments
         * @return the result from either the interceptor chain or direct invocation
         * @throws Throwable if the method invocation or interceptor execution fails
         */
        @RuntimeType  // Tells ByteBuddy to adapt return types dynamically
        public static Object intercept(
                @FieldValue("$$_targetInstance") Object targetInstance,                    // The real bean
                @FieldValue("$$_methodInterceptorChains") Map<Method, InterceptorChain> methodInterceptorChains,  // Method interceptor map
                @Origin Method method,                                                      // The method being called
                @AllArguments Object[] args                                                 // The method arguments
        ) throws Throwable {

            // Step 1: Validate proxy state
            if (targetInstance == null) {
                throw new IllegalStateException(
                    "Interceptor-aware proxy has not been initialized. " +
                    "Call $$_setInterceptorProxyState first. Method: " + method.getName()
                );
            }
            if (DestroyedInstanceTracker.isDestroyed(targetInstance)) {
                throw new NonPortableBehaviourException(
                    "Invocation on destroyed contextual instance of " + targetInstance.getClass().getName()
                );
            }

            // Step 2: Check if this method has interceptors configured
            // The map contains entries ONLY for methods that have interceptors
            InterceptorChain chain = methodInterceptorChains.get(method);
            if (chain == null) {
                chain = findMatchingChain(methodInterceptorChains, method);
            }

            if (chain != null) {
                // Step 3a: Method has interceptors - execute the chain will:
                // 1. Execute each interceptor in priority order
                // 2. Each interceptor calls context.proceed() to continue the chain
                // 3. The last proceed() invokes the actual target method
                // 4. Results propagate back through the chain
                return chain.invoke(targetInstance, method, args);
            } else {
                Method targetAroundInvoke = findTargetClassAroundInvokeMethod(targetInstance.getClass());
                if (targetAroundInvoke != null && !method.equals(targetAroundInvoke)) {
                    InterceptorChain dynamicChain = InterceptorChain.builder()
                            .addInterceptor(targetInstance, targetAroundInvoke)
                            .build();
                    return dynamicChain.invoke(targetInstance, method, args);
                }

                // Step 3b: No interceptors for this method-direct invocation
                // This is the fast path for non-intercepted methods
                // Simply jakarta.enterprise.invoke the method on the target instance and return the result
                method.setAccessible(true);
                return method.invoke(targetInstance, args);
            }
        }

        private static InterceptorChain findMatchingChain(Map<Method, InterceptorChain> methodInterceptorChains,
                                                          Method invokedMethod) {
            if (methodInterceptorChains == null || methodInterceptorChains.isEmpty() || invokedMethod == null) {
                return null;
            }
            for (Map.Entry<Method, InterceptorChain> entry : methodInterceptorChains.entrySet()) {
                Method configuredMethod = entry.getKey();
                if (configuredMethod == null) {
                    continue;
                }
                if (!configuredMethod.getName().equals(invokedMethod.getName())) {
                    continue;
                }
                if (!Arrays.equals(configuredMethod.getParameterTypes(), invokedMethod.getParameterTypes())) {
                    continue;
                }
                return entry.getValue();
            }
            return null;
        }

        private static Method findTargetClassAroundInvokeMethod(Class<?> targetClass) {
            if (targetClass == null) {
                return null;
            }

            Optional<Method> cached = targetAroundInvokeCache.get(targetClass);
            if (cached != null) {
                return cached.orElse(null);
            }

            Class<?> current = targetClass;
            Method found = null;
            AnnotatedType<?> override = resolveAnnotatedTypeOverride(targetClass);
            if (override != null) {
                found = findAroundInvokeMethodFromOverride(override);
            } else {
                while (current != null && current != Object.class && found == null) {
                    for (Method candidate : current.getDeclaredMethods()) {
                        if (AnnotationPredicates.hasAroundInvokeAnnotation(candidate)) {
                            if (candidate.getParameterCount() == 1 &&
                                    jakarta.interceptor.InvocationContext.class.isAssignableFrom(candidate.getParameterTypes()[0])) {
                                candidate.setAccessible(true);
                                found = candidate;
                                break;
                            }
                        }
                    }
                    current = current.getSuperclass();
                }
            }

            Optional<Method> value = Optional.ofNullable(found);
            targetAroundInvokeCache.put(targetClass, value);
            return found;
        }

        private static AnnotatedType<?> resolveAnnotatedTypeOverride(Class<?> targetClass) {
            BeanManagerImpl beanManager = BeanManagerImpl.getRegisteredBeanManager(targetClass.getClassLoader());
            if (beanManager == null || beanManager.getKnowledgeBase() == null) {
                return null;
            }
            Class<?> current = targetClass;
            while (current != null && current != Object.class) {
                AnnotatedType<?> override = beanManager.getKnowledgeBase().getAnnotatedTypeOverride(current);
                if (override != null) {
                    return override;
                }
                current = current.getSuperclass();
            }
            return null;
        }

        private static Method findAroundInvokeMethodFromOverride(AnnotatedType<?> override) {
            if (override == null) {
                return null;
            }
            for (AnnotatedMethod<?> method : override.getMethods()) {
                if (method == null || !hasAroundInvokeAnnotation(method.getAnnotations())) {
                    continue;
                }
                Method javaMethod = method.getJavaMember();
                if (javaMethod == null) {
                    continue;
                }
                if (javaMethod.getParameterCount() == 1 &&
                        jakarta.interceptor.InvocationContext.class.isAssignableFrom(javaMethod.getParameterTypes()[0])) {
                    javaMethod.setAccessible(true);
                    return javaMethod;
                }
            }
            return null;
        }
    }
}
