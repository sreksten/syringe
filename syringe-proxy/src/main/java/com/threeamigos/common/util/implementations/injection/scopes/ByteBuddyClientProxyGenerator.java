package com.threeamigos.common.util.implementations.injection.scopes;

import com.threeamigos.common.util.implementations.injection.discovery.NonPortableBehaviourException;
import com.threeamigos.common.util.implementations.injection.resolution.BeanImpl;
import com.threeamigos.common.util.implementations.injection.resolution.DestroyedInstanceTracker;
import com.threeamigos.common.util.implementations.injection.resolution.ProducerBean;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.PassivationCapable;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.FieldValue;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;
import net.bytebuddy.matcher.ElementMatchers;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates client proxies for normal-scoped CDI beans.
 *
 * <h2>Why Proxies Are Needed</h2>
 * In CDI, normal scopes (ApplicationScoped, RequestScoped, SessionScoped, ConversationScoped)
 * require client proxies to ensure correct behavior when beans with different lifecycles interact.
 *
 * <h3>Example Problem Without Proxies:</h3>
 * <pre>
 * {@literal @}ApplicationScoped
 * class GlobalService {
 *     {@literal @}Inject RequestData requestData; // Different request per HTTP call!
 * }
 *
 * {@literal @}RequestScoped
 * class RequestData {
 *     String userId;
 * }
 * </pre>
 *
 * Without a proxy, the ApplicationScoped bean would get ONE instance of RequestData
 * injected at creation time and use that same instance for ALL requests - WRONG!
 *
 * <h3>Solution With Proxies:</h3>
 * Instead of injecting the actual RequestData instance, CDI injects a PROXY that:
 * 1. Looks identical to RequestData (same class, same methods)
 * 2. On every method call, asks the context "what's the current RequestData instance?"
 * 3. Forwards the method call to the correct contextual instance
 *
 * <h2>How This Generator Works</h2>
 * Uses ByteBuddy to generate a subclass that intercepts all method calls and delegates
 * to the contextual instance from the appropriate scope.
 *
 * <pre>
 * Original Bean:
 * class RequestData {
 *     public String getUserId() { return userId; }
 * }
 *
 * Generated Proxy:
 * class RequestData$$Proxy extends RequestData {
 *     public String getUserId() {
 *         // 1. Get current contextual instance from RequestScoped context
 *         RequestData realInstance = context.get(bean);
 *         // 2. Forward the call to the real instance
 *         return realInstance.getUserId();
 *     }
 * }
 * </pre>
 *
 * <h2>PHASE 2 - Interceptor Integration</h2>
 * This generator works seamlessly with the interceptor infrastructure:
 * <ul>
 * <li>When {@code context.get(bean)} is called, the context returns either:
 *     <ul>
 *     <li>An {@link InterceptorAwareProxyGenerator interceptor-aware proxy} (if bean has interceptors)</li>
 *     <li>The raw bean instance (if no interceptors)</li>
 *     </ul>
 * </li>
 * <li>The interceptor-aware proxy is created by the context automatically based on {@link BeanImpl#hasInterceptors()}</li>
 * <li>This means the client proxy TRANSPARENTLY invokes interceptor chains without any special handling</li>
 * </ul>
 *
 * <h3>Call Flow With Interceptors:</h3>
 * <pre>
 * {@literal @}ApplicationScoped
 * {@literal @}Transactional // Interceptor binding
 * class OrderService {
 *     public void createOrder(Order order) { ... }
 * }
 *
 * // At runtime:
 * orderService.createOrder(order);
 *   → ClientProxy.createOrder(order)
 *     → context.get(bean) returns InterceptorAwareProxy
 *     → InterceptorAwareProxy.createOrder(order)
 *       → InterceptorChain.jakarta.enterprise.invoke()
 *         → [TransactionalInterceptor] begins transaction
 *         → [RealOrderService].createOrder(order)
 *         → [TransactionalInterceptor] commits transaction
 * </pre>
 *
 * @author Stefano Reksten
 * @see InterceptorAwareProxyGenerator
 * @see BeanImpl#createInterceptorAwareProxy(Object)
 */
public class ByteBuddyClientProxyGenerator {

    private final ContextManager contextManager;

    // Cache generated proxy classes to avoid regenerating for the same bean type
    private final ConcurrentHashMap<Class<?>, Class<?>> proxyClassCache = new ConcurrentHashMap<>();

    /**
     * Global registry for container lookup during proxy deserialization.
     *
     * <p>CDI proxies need to be serializable for passivation-capable scopes (@SessionScoped, @ConversationScoped).
     * When a proxy is deserialized, it needs to reconnect to the container to function properly.
     *
     * <p>This registry is thread-safe and supports multiple containers (e.g., in testing scenarios).
     * The key is typically a container ID or classloader identifier.
     */
    private static final ConcurrentHashMap<ClassLoader, ContainerContext> containerRegistry =
        new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ContainerContext> containerRegistryByBeanManagerId =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ContextManager, String> beanManagerIdByContextManager =
            new ConcurrentHashMap<>();

    /**
     * Registers a container context for proxy deserialization.
     *
     * @param classLoader the classloader associated with this container
     * @param beanManager the BeanManager for bean lookups
     * @param contextManager the ContextManager for scope contexts
     */
    public static void registerContainer(ClassLoader classLoader, BeanManager beanManager,
                                        ContextManager contextManager) {
        ContainerContext containerContext = new ContainerContext(beanManager, contextManager);
        containerRegistry.put(classLoader, containerContext);
        String beanManagerId = resolveBeanManagerId(beanManager);
        if (beanManagerId != null) {
            containerRegistryByBeanManagerId.put(beanManagerId, containerContext);
            if (contextManager != null) {
                beanManagerIdByContextManager.put(contextManager, beanManagerId);
            }
        }
    }

    /**
     * Unregisters a container context (e.g., during container shutdown).
     *
     * @param classLoader the classloader to unregister
     */
    public static void unregisterContainer(ClassLoader classLoader) {
        if (classLoader == null) {
            return;
        }
        containerRegistry.remove(classLoader);
    }

    public static void unregisterContainer(ClassLoader classLoader,
                                           BeanManager beanManager,
                                           ContextManager contextManager) {
        String beanManagerId = resolveBeanManagerId(beanManager);
        if (beanManagerId != null) {
            ContainerContext removedById = containerRegistryByBeanManagerId.remove(beanManagerId);
            if (contextManager != null) {
                beanManagerIdByContextManager.remove(contextManager, beanManagerId);
            }
            if (classLoader != null && removedById != null) {
                containerRegistry.remove(classLoader, removedById);
                return;
            }
            if (classLoader != null) {
                ContainerContext current = containerRegistry.get(classLoader);
                if (current != null) {
                    String currentId = resolveBeanManagerId(current.beanManager);
                    if (beanManagerId.equals(currentId)) {
                        containerRegistry.remove(classLoader, current);
                    }
                }
            }
            return;
        }
        if (contextManager != null) {
            beanManagerIdByContextManager.remove(contextManager);
        }
        if (classLoader != null) {
            containerRegistry.remove(classLoader);
        }
    }

    /**
     * Gets the container context for a given classloader.
     *
     * @param classLoader the classloader
     * @return the container context, or null if not registered
     */
    static ContainerContext getContainerContext(ClassLoader classLoader) {
        return containerRegistry.get(classLoader);
    }

    static ContainerContext getContainerContext(String beanManagerId) {
        if (beanManagerId == null) {
            return null;
        }
        return containerRegistryByBeanManagerId.get(beanManagerId);
    }

    private static String resolveBeanManagerId(BeanManager beanManager) {
        if (beanManager instanceof BeanManagerImpl) {
            return ((BeanManagerImpl) beanManager).getBeanManagerId();
        }
        return null;
    }

    /**
     * Clears the generated proxy class cache for this generator instance.
     * Intended for container shutdown to release class references promptly.
     */
    public void clearCache() {
        proxyClassCache.clear();
    }

    /**
     * Holds BeanManager and ContextManager for a container.
     */
    static class ContainerContext {
        final BeanManager beanManager;
        final ContextManager contextManager;

        ContainerContext(BeanManager beanManager, ContextManager contextManager) {
            this.beanManager = beanManager;
            this.contextManager = contextManager;
        }
    }

    public ByteBuddyClientProxyGenerator(ContextManager contextManager) {
        this.contextManager = contextManager;
    }

    /**
     * Creates a client proxy for a normal-scoped bean.
     * <p>
     * The proxy lifecycle:
     * 1. Proxy is created once and injected into dependent beans
     * 2. Proxy lives as long as the dependent bean (could be the entire application)
     * 3. Each method call on the proxy:
     *    - Looks up the current contextual instance from the scope's context
     *    - Delegates the call to that instance
     *    - Returns the result
     * <p>
     * This ensures that even if the proxy is held by a long-lived bean,
     * it always accesses the correct contextual instance for the current scope.
     * <p>
     * IMPORTANT - Constructor Handling:
     * The bean class may have an @Inject constructor with parameters, like:
     *
     * <pre>
     * {@literal @}RequestScoped
     * class MyBean {
     *     {@literal @}Inject
     *     public MyBean(SomeService service) { ... }
     * }
     * </pre>
     *
     * The generated proxy will have a no-arg constructor that calls super() with
     * default/null values. This is SAFE because:
     * - The proxy itself never executes any business logic
     * - All method calls are intercepted and delegated to real contextual instances
     * - The real instances are created properly via BeanImpl.create() with full DI
     * - The proxy is just an empty shell for delegation
     *
     * @param bean the bean to create a proxy for
     * @param <T> the bean type
     * @return a proxy instance that delegates to contextual instances
     */
    @SuppressWarnings("unchecked")
    public <T> T createProxy(Bean<T> bean) {
        Class<?> beanClass = resolveProxyBaseClass(bean);
        validateProxyableBeanType(bean, beanClass);

        Class<?> proxyClass;
        if (beanClass != null && (beanClass.isInterface() || beanClass == Object.class)) {
            // Interface/object-based beans are frequently loaded from parent modules (e.g., CDI API);
            // generate against the active deployment loader to avoid module visibility issues.
            proxyClass = generateProxyClass(beanClass);
        } else {
            // Get or generate the proxy class
            proxyClass = proxyClassCache.computeIfAbsent(beanClass, this::generateProxyClass);
        }

        try {
            // Create an instance of the proxy
            T proxy = (T) instantiateProxy(proxyClass);

            // Initialize the proxy with the bean and context manager
            // This is done via the ProxyState interface that the proxy implements
            if (proxy instanceof ProxyState) {
                ((ProxyState) proxy).$$_setProxyState(bean, contextManager);
            }

            return proxy;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create proxy for bean: " + beanClass.getName(), e);
        }
    }

    private void validateProxyableBeanType(Bean<?> bean, Class<?> beanClass) {
        if (beanClass == null || beanClass.isInterface() || beanClass == Object.class) {
            return;
        }
        boolean ignoreFinalMethods = bean instanceof BeanImpl<?> &&
                ((BeanImpl<?>) bean).isIgnoreFinalMethods();

        if (Modifier.isFinal(beanClass.getModifiers())) {
            throw new jakarta.enterprise.inject.UnproxyableResolutionException(
                    "Cannot proxy final class: " + beanClass.getName());
        }

        if (!hasNonPrivateNoArgConstructor(beanClass)) {
            throw new jakarta.enterprise.inject.UnproxyableResolutionException(
                    "Cannot proxy bean without non-private no-arg constructor: " + beanClass.getName());
        }

        if (!ignoreFinalMethods) {
            Method finalBusinessMethod = findNonStaticFinalNonPrivateMethod(beanClass);
            if (finalBusinessMethod != null) {
                throw new jakarta.enterprise.inject.UnproxyableResolutionException(
                        "Cannot proxy bean with non-static final non-private method " +
                                finalBusinessMethod.getName() + " in " + beanClass.getName());
            }
        }
    }

    private boolean hasNonPrivateNoArgConstructor(Class<?> type) {
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (!Modifier.isPrivate(constructor.getModifiers()) && constructor.getParameterCount() == 0) {
                return true;
            }
        }
        return false;
    }

    private Method findNonStaticFinalNonPrivateMethod(Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            int modifiers = method.getModifiers();
            if (Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers) || Modifier.isPrivate(modifiers)) {
                continue;
            }
            return method;
        }
        return null;
    }

    private Object instantiateProxy(Class<?> proxyClass) throws Exception {
        try {
            Constructor<?> noArg = proxyClass.getDeclaredConstructor();
            noArg.setAccessible(true);
            return noArg.newInstance();
        } catch (NoSuchMethodException ignored) {
            // Fall back to invoking the first visible constructor with default values.
        } catch (Exception e) {
            Object allocated = allocateWithoutConstructor(proxyClass);
            if (allocated != null) {
                return allocated;
            }
            throw e;
        }

        Constructor<?>[] constructors = proxyClass.getDeclaredConstructors();
        if (constructors.length == 0) {
            Object allocated = allocateWithoutConstructor(proxyClass);
            if (allocated != null) {
                return allocated;
            }
            throw new IllegalStateException("Proxy class has no constructors: " + proxyClass.getName());
        }

        Exception lastError = null;
        for (Constructor<?> constructor : constructors) {
            try {
                constructor.setAccessible(true);
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                Object[] args = new Object[parameterTypes.length];
                for (int i = 0; i < parameterTypes.length; i++) {
                    args[i] = defaultValue(parameterTypes[i]);
                }
                return constructor.newInstance(args);
            } catch (Exception e) {
                lastError = e;
            }
        }

        Object allocated = allocateWithoutConstructor(proxyClass);
        if (allocated != null) {
            return allocated;
        }
        throw lastError;
    }

    private Object allocateWithoutConstructor(Class<?> proxyClass) {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Object unsafe = theUnsafe.get(null);
            Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
            return allocateInstance.invoke(unsafe, proxyClass);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return '\0';
        return null;
    }

    private Class<?> resolveProxyBaseClass(Bean<?> bean) {
        if (bean instanceof ProducerBean<?>) {
            ProducerBean<?> producerBean = (ProducerBean<?>) bean;
            Method producerMethod = producerBean.getProducerMethod();
            if (producerMethod != null) {
                return producerMethod.getReturnType();
            }
            Field producerField = producerBean.getProducerField();
            if (producerField != null) {
                return producerField.getType();
            }
        }
        Class<?> fallback = bean.getBeanClass();
        Class<?> inferred = inferProxyBaseFromBeanTypes(bean, fallback);
        return inferred != null ? inferred : fallback;
    }

    private Class<?> inferProxyBaseFromBeanTypes(Bean<?> bean, Class<?> fallback) {
        Class<?> best = null;
        int bestDepth = -1;
        for (Type beanType : bean.getTypes()) {
            if (!(beanType instanceof Class<?>)) {
                continue;
            }
            Class<?> candidate = (Class<?>) beanType;
            if (candidate == Object.class || candidate.isInterface() ||
                    candidate.isAnnotation() || candidate.isArray() || candidate.isPrimitive()) {
                continue;
            }
            int depth = classHierarchyDepth(candidate);
            if (depth > bestDepth) {
                best = candidate;
                bestDepth = depth;
            }
        }
        if (best == null) {
            return fallback;
        }
        if (best.equals(fallback)) {
            return fallback;
        }
        return best;
    }

    private int classHierarchyDepth(Class<?> type) {
        int depth = 0;
        Class<?> current = type;
        while (current != null && current != Object.class) {
            depth++;
            current = current.getSuperclass();
        }
        return depth;
    }

    /**
     * Generates a proxy class using ByteBuddy.
     * <p>
     * The generated class:
     * 1. Extends the target bean class (so it's type-compatible)
     * 2. Adds a DEFAULT (no-arg) constructor
     *    - This is CRITICAL: The bean class might have @Inject constructors with parameters
     *    - The proxy doesn't need to initialize the bean properly because it NEVER calls
     *      business methods on itself - it always delegates to contextual instances
     *    - We use DEFAULT constructor strategy, which calls super() on any existing constructor,
     *      using default values (null for objects, 0 for primitives, false for booleans)
     * 3. Implements ProxyState (to hold bean and contextManager references)
     * 4. Implements Serializable (required by CDI spec for passivation)
     * 5. Adds fields for storing the bean and contextManager
     * 6. Overrides all public methods to delegate to contextual instance
     * <p>
     * Note: The proxy shell is never actually "used" as a bean - it's just a delegation wrapper.
     * All business logic runs on the real contextual instances retrieved from the scope.
     *
     * @param beanClass the class to proxy
     * @return the generated proxy class
     */
    private Class<?> generateProxyClass(Class<?> beanClass) {
        try {
            ByteBuddy byteBuddy = new ByteBuddy();
            net.bytebuddy.dynamic.DynamicType.Builder<?> builder;
            if (beanClass != null && beanClass.isInterface()) {
                builder = byteBuddy
                        .subclass(Object.class, ConstructorStrategy.Default.DEFAULT_CONSTRUCTOR)
                        .implement(beanClass);
            } else if (beanClass == Object.class) {
                builder = byteBuddy.subclass(Object.class, ConstructorStrategy.Default.DEFAULT_CONSTRUCTOR);
            } else {
                builder = byteBuddy
                        // Create a subclass of the target bean class
                        // IMPORTANT: Use DEFAULT constructor strategy to handle parent constructors.
                        // This will create a constructor that calls the super constructor with default values:
                        // - null for object parameters
                        // - 0 for numeric primitives
                        // - false for boolean
                        // This is safe because the proxy never uses the parent's state - it only delegates.
                        .subclass(beanClass, ConstructorStrategy.Default.IMITATE_SUPER_CLASS);
            }

            return builder

                // Add fields to store the bean and contextManager
                .defineField("$$_bean", Bean.class, net.bytebuddy.description.modifier.Visibility.PRIVATE)
                .defineField("$$_contextManager", ContextManager.class, net.bytebuddy.description.modifier.Visibility.PRIVATE)

                // Implement ProxyState to store bean and contextManager
                .implement(ProxyState.class)

                // Implement ProxyState methods using field accessors
                .method(ElementMatchers.named("$$_setProxyState"))
                .intercept(FieldAccessor.ofField("$$_bean").setsArgumentAt(0)
                    .andThen(FieldAccessor.ofField("$$_contextManager").setsArgumentAt(1)))

                .method(ElementMatchers.named("$$_getBean"))
                .intercept(FieldAccessor.ofField("$$_bean"))

                .method(ElementMatchers.named("$$_getContextManager"))
                .intercept(FieldAccessor.ofField("$$_contextManager"))

                // Implement Serializable (CDI requirement for passivating scopes)
                .implement(Serializable.class)

                // Add serialization support via writeReplace method
                // This ensures that when the proxy is serialized, we serialize a minimal
                // SerializedProxy marker that can recreate the proxy on deserialization
                .defineMethod("writeReplace", Object.class, net.bytebuddy.description.modifier.Visibility.PRIVATE)
                .intercept(MethodDelegation.to(SerializationInterceptor.class))

                // Intercept all non-Object methods plus Object.toString().
                // TCK uses toString() on normal-scoped contextual references to trigger bean creation.
                .method(
                    ElementMatchers.named("toString").and(ElementMatchers.takesArguments(0))
                        .or(
                            ElementMatchers.any()
                                .and(ElementMatchers.not(ElementMatchers.isDeclaredBy(Object.class)))
                                .and(ElementMatchers.not(ElementMatchers.isDeclaredBy(ProxyState.class)))
                                .and(ElementMatchers.not(ElementMatchers.named("writeReplace")))
                        )
                )
                .intercept(MethodDelegation.to(ContextualInstanceInterceptor.class))

                // Load the class into the same classloader as the target class
                .make()
                .load(resolveProxyClassLoader(beanClass), ClassLoadingStrategy.Default.INJECTION)
                .getLoaded();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate proxy class for: " + beanClass.getName(), e);
        }
    }

    private ClassLoader resolveProxyClassLoader(Class<?> beanClass) {
        if (beanClass != null && beanClass.isInterface()) {
            ClassLoader ccl = Thread.currentThread().getContextClassLoader();
            if (canLoadType(ccl, beanClass)) {
                return ccl;
            }
            ClassLoader own = ByteBuddyClientProxyGenerator.class.getClassLoader();
            if (canLoadType(own, beanClass)) {
                return own;
            }
        }
        ClassLoader beanLoader = beanClass != null ? beanClass.getClassLoader() : null;
        if (beanLoader != null) {
            return beanLoader;
        }
        ClassLoader ccl = Thread.currentThread().getContextClassLoader();
        if (ccl != null) {
            return ccl;
        }
        return ByteBuddyClientProxyGenerator.class.getClassLoader();
    }

    private boolean canLoadType(ClassLoader classLoader, Class<?> type) {
        if (classLoader == null) {
            return type.getClassLoader() == null;
        }
        try {
            return Class.forName(type.getName(), false, classLoader) == type;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Interface implemented by all generated proxies to store proxy state.
     * This allows us to pass the bean and contextManager to the proxy instance.
     */
    public interface ProxyState {
        void $$_setProxyState(Bean<?> bean, ContextManager contextManager);
        Bean<?> $$_getBean();
        ContextManager $$_getContextManager();
    }

    /**
     * ByteBuddy interceptor that handles method calls on proxy instances.
     *
     * <h3>Call Flow:</h3>
     * <pre>
     * 1. User calls: proxy.getUserId()
     * 2. ByteBuddy intercepts and calls: ContextualInstanceInterceptor.intercept(...)
     * 3. Interceptor does:
     *    - Extract bean and contextManager from proxy (via ProxyState)
     *    - Get bean's scope annotation (e.g., @RequestScoped)
     *    - Get the context for that scope from contextManager
     *    - Get the current contextual instance from the context
     *    - Invoke the original method on the contextual instance
     *    - Return the result
     * 4. Result flows back to the user
     * </pre>
     *
     * This interceptor is what makes the proxy "transparent" - from the caller's
     * perspective, they're just calling a normal method, but behind the scenes
     * we're ensuring they get the right contextual instance for the current scope.
     */
    public static class ContextualInstanceInterceptor {

        /**
         * Intercepts method calls on the proxy and delegates to the contextual instance.
         *
         * @param bean the bean stored in the proxy
         * @param contextManager the contextManager stored in the proxy
         * @param method the method being called
         * @param args the method arguments
         * @return the result from the contextual instance
         * @throws Throwable if the method invocation fails
         */
        @RuntimeType  // Tells ByteBuddy to adapt return types dynamically
        public static Object intercept(
                @FieldValue("$$_bean") Bean<?> bean,                           // The bean from proxy field
                @FieldValue("$$_contextManager") ContextManager contextManager, // The contextManager from proxy field
                @Origin Method method,                                          // The method being called
                @AllArguments Object[] args                                     // The method arguments
        ) throws Throwable {

            // Step 1: Validate proxy state
            if (bean == null || contextManager == null) {
                throw new IllegalStateException("Proxy has not been initialized. Call $$_setProxyState first.");
            }

            // Step 2: Get the bean's scope annotation
            // Every normal-scoped bean has exactly one scope annotation
            Class<? extends Annotation> scopeType = bean.getScope();

            // Step 3: Get the context for this scope
            // E.g., for @RequestScoped beans, this returns the RequestScopedContext
            // The context maintains the actual bean instances for the current scope
            // (current request, current session, etc.)
            ScopeContext context = contextManager.getContext(scopeType);

            // Ensure custom InjectionPoint implementations are consulted when resolving
            // contextual instances for custom beans with declared injection metadata.
            touchInjectionPointMembers(bean);

            // Step 4: Get the current contextual instance from the context
            // This is THE KEY STEP that makes proxies work:
            // - For RequestScoped: returns the instance for the CURRENT HTTP request
            // - For SessionScoped: returns the instance for the CURRENT user session
            // - For ApplicationScoped: returns the singleton instance
            // - For ConversationScoped: returns the instance for the CURRENT conversation
            //
            // If no instance exists yet, the context will create one.
            //
            // PHASE 2 - INTERCEPTOR INTEGRATION:
            // The context.get() call will return either:
            // a) An interceptor-aware proxy (if the bean has interceptors) - already wrapping the real instance
            // b) The raw bean instance (if no interceptors)
            //
            // This is handled automatically by the contexts (ApplicationScopedContext, RequestScopedContext, etc.)
            // which check bean.hasInterceptors() and call bean.createInterceptorAwareProxy() when needed.
            //
            // So the contextualInstance we get here is ALREADY interceptor-aware if needed!
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object contextualInstance = context.get((Bean) bean, new SerializableCreationalContext<>());

            if (contextualInstance == null) {
                throw new IllegalStateException(
                    "No contextual instance available for bean: " + bean.getBeanClass().getName() +
                    " in scope: " + scopeType.getName() + ". Is the scope active?"
                );
            }
            if (DestroyedInstanceTracker.isDestroyed(contextualInstance)) {
                throw new NonPortableBehaviourException(
                    "Invocation on destroyed contextual instance of " + bean.getBeanClass().getName()
                );
            }

            // Step 5: Invoke the method on the contextual instance
            // This is the real bean instance (or interceptor-aware proxy wrapping it) that will process the call
            //
            // PHASE 2 - INTERCEPTOR FLOW:
            // If the bean has interceptors, the call flow is:
            // ClientProxy.method(args)
            //   → context.get(bean) returns InterceptorAwareProxy
            //   → method.jakarta.enterprise.invoke(InterceptorAwareProxy, args)
            //   → InterceptorAwareProxy.method(args)
            //     → Check if method has interceptors
            //     → If yes: InterceptorChain.jakarta.enterprise.invoke()
            //       → [Interceptor 1] → [Interceptor 2] → RealInstance.method()
            //     → If no: RealInstance.method()
            //
            // So interceptors are invoked TRANSPARENTLY through the contextual instance!
            method.setAccessible(true);
            try {
                return method.invoke(contextualInstance, args);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                throw cause != null ? cause : e;
            }
        }

        private static void touchInjectionPointMembers(Bean<?> bean) {
            if (bean == null) {
                return;
            }
            Set<InjectionPoint> injectionPoints;
            try {
                injectionPoints = bean.getInjectionPoints();
            } catch (RuntimeException ignored) {
                return;
            }
            if (injectionPoints == null || injectionPoints.isEmpty()) {
                return;
            }
            for (InjectionPoint injectionPoint : injectionPoints) {
                if (injectionPoint == null) {
                    continue;
                }
                try {
                    injectionPoint.getMember();
                } catch (RuntimeException ignored) {
                    // Ignore metadata callback failures here; deployment validation reports definition errors.
                }
            }
        }
    }

    /**
     * Interceptor for proxy serialization.
     * <p>
     * When a proxy is serialized (e.g., when an HTTP session is passivated), we don't want to
     * serialize the proxy itself - instead, we serialize a minimal marker object that knows
     * how to recreate the proxy on deserialization.
     * <p>
     * This is the standard CDI proxy serialization pattern.
     */
    public static class SerializationInterceptor {

        /**
         * Called automatically during serialization via the writeReplace() method.
         *
         * @param proxyState the proxy being serialized
         * @return a SerializedProxy marker object
         */
        @RuntimeType
        public static Object writeReplace(@This ProxyState proxyState) {
            // Extract bean class from the proxy
            Bean<?> bean = proxyState.$$_getBean();
            Class<?> beanClass = bean.getBeanClass();
            ContextManager contextManager = proxyState.$$_getContextManager();
            String beanManagerId = resolveBeanManagerId(bean, contextManager, beanClass);
            String beanPassivationId = bean instanceof PassivationCapable
                    ? ((PassivationCapable) bean).getId()
                    : null;

            // Return a serializable marker that can recreate the proxy
            return new SerializedProxy(beanClass, beanManagerId, beanPassivationId);
        }

        private static String resolveBeanManagerId(Bean<?> bean,
                                                   ContextManager contextManager,
                                                   Class<?> beanClass) {
            if (contextManager != null) {
                String byContext = beanManagerIdByContextManager.get(contextManager);
                if (byContext != null) {
                    return byContext;
                }
            }

            String byBean = resolveBeanManagerIdFromBean(bean);
            if (byBean != null) {
                return byBean;
            }

            if (beanClass != null) {
                BeanManagerImpl byClassLoader = BeanManagerImpl.getRegisteredBeanManager(beanClass.getClassLoader());
                if (byClassLoader != null) {
                    return byClassLoader.getBeanManagerId();
                }
            }

            ClassLoader ccl = Thread.currentThread().getContextClassLoader();
            BeanManagerImpl byCcl = BeanManagerImpl.getRegisteredBeanManager(ccl);
            if (byCcl != null) {
                return byCcl.getBeanManagerId();
            }
            return null;
        }

        private static String resolveBeanManagerIdFromBean(Bean<?> bean) {
            if (bean == null) {
                return null;
            }
            Class<?> current = bean.getClass();
            while (current != null && !Object.class.equals(current)) {
                for (Field field : current.getDeclaredFields()) {
                    if (!BeanManager.class.isAssignableFrom(field.getType())) {
                        continue;
                    }
                    try {
                        field.setAccessible(true);
                        Object value = field.get(bean);
                        if (value instanceof BeanManagerImpl) {
                            return ((BeanManagerImpl) value).getBeanManagerId();
                        }
                    } catch (Exception ignored) {
                        // try next field/superclass
                    }
                }
                current = current.getSuperclass();
            }
            return null;
        }
    }

    /**
     * Serializable fallback creational context used when resolving normal-scoped contextual instances
     * from client proxies. This ensures passivating contexts always receive a serializable context object.
     */
    private static final class SerializableCreationalContext<T> implements jakarta.enterprise.context.spi.CreationalContext<T>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public void push(T incompleteInstance) {
            // Client proxies do not require push semantics.
        }

        @Override
        public void release() {
            // Nothing to release for proxy fallback creational context.
        }
    }

    /**
     * Serialization marker for CDI client proxies.
     * <p>
     * This small object is what actually gets serialized when a proxy is passivated.
     * On deserialization (via readResolve), it recreates the proxy.
     * <p>
     * Why this pattern?
     * - Proxies contain references to Bean and ContextManager (not serializable)
     * - We only need the bean class to recreate the proxy
     * - The proxy will get re-initialized with proper Bean/ContextManager references
     *   when retrieved from the container after deserialization
     */
    private static class SerializedProxy implements Serializable {
        private static final long serialVersionUID = 1L;

        private final Class<?> beanClass;
        private final String beanManagerId;
        private final String beanPassivationId;

        SerializedProxy(Class<?> beanClass, String beanManagerId, String beanPassivationId) {
            this.beanClass = beanClass;
            this.beanManagerId = beanManagerId;
            this.beanPassivationId = beanPassivationId;
        }

        /**
         * Called automatically during deserialization.
         *
         * <p>This method reconstructs the client proxy by:
         * <ol>
         *   <li>Finding the container context for the current classloader</li>
         *   <li>Looking up the bean from the BeanManager</li>
         *   <li>Creating a new proxy with proper Bean and ContextManager references</li>
         * </ol>
         *
         * <p>This allows passivated beans (e.g., @SessionScoped) to be deserialized
         * and continue functioning properly after JVM restart or session migration.
         *
         * @return a properly initialized proxy instance
         * @throws IllegalStateException if container isn't registered or bean not found
         */
        private Object readResolve() {
            // Get the classloader for this bean class
            assert beanClass != null;
            ClassLoader classLoader = beanClass.getClassLoader();
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

            // Look up the container context
            ContainerContext containerContext = getContainerContext(beanManagerId);
            if (containerContext == null && beanManagerId != null) {
                BeanManagerImpl byId = BeanManagerImpl.getRegisteredBeanManager(beanManagerId);
                if (byId != null) {
                    containerContext = new ContainerContext(byId, byId.getContextManager());
                }
            }
            if (containerContext == null) {
                containerContext = getContainerContext(classLoader);
            }
            if (containerContext == null && contextClassLoader != null && contextClassLoader != classLoader) {
                containerContext = getContainerContext(contextClassLoader);
            }
            if (containerContext == null) {
                BeanManagerImpl registeredBeanManager = BeanManagerImpl.getRegisteredBeanManager(classLoader);
                if (registeredBeanManager == null && contextClassLoader != null && contextClassLoader != classLoader) {
                    registeredBeanManager = BeanManagerImpl.getRegisteredBeanManager(contextClassLoader);
                }
                if (registeredBeanManager != null) {
                    containerContext = new ContainerContext(registeredBeanManager, registeredBeanManager.getContextManager());
                }
            }
            if (containerContext == null) {
                throw new IllegalStateException(
                    "Cannot deserialize proxy for " + beanClass.getName() + ": " +
                    "No container registered for classloader " + classLoader + ". " +
                    "Ensure ClientProxyGenerator.registerContainer() was called during container initialization."
                );
            }

            BeanManager beanManager = containerContext.beanManager;
            ContextManager contextManager = containerContext.contextManager;

            Bean<?> bean = null;
            if (beanManager instanceof BeanManagerImpl && beanPassivationId != null) {
                bean = beanManager.getPassivationCapableBean(beanPassivationId);
            }
            if (bean == null) {
                // Look up the bean from the BeanManager
                // Use BeanManager.getBeans() to find beans of this type
                Set<Bean<?>> beans = beanManager.getBeans(beanClass);

                if (beans.isEmpty()) {
                    throw new IllegalStateException(
                        "Cannot deserialize proxy for " + beanClass.getName() + ": " +
                        "No bean found in container. The bean may have been removed or the container restarted."
                    );
                }

                // Resolve to a single bean (handles alternatives and priorities)
                bean = beanManager.resolve(beans);

                if (bean == null) {
                    throw new IllegalStateException(
                        "Cannot deserialize proxy for " + beanClass.getName() + ": " +
                        "Ambiguous beans found - cannot resolve to a single bean."
                    );
                }
            }

            // Create a new ClientProxyGenerator and generate the proxy
            ByteBuddyClientProxyGenerator generator = new ByteBuddyClientProxyGenerator(contextManager);
            return generator.createProxy(bean);
        }
    }
}
