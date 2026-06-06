package com.threeamigos.common.util.implementations.injection.decorators;

import com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates;

import com.threeamigos.common.util.implementations.injection.scopes.InjectionPointImpl;
import com.threeamigos.common.util.implementations.injection.knowledgebase.DecoratorInfo;
import com.threeamigos.common.util.implementations.injection.util.LifecycleMethodHelper;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
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

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.hasDelegateAnnotation;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.hasInjectAnnotation;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsEnum.PRE_DESTROY;

/**
 * Generates decorator proxies that wrap bean instances with decorator chains.
 *
 * <p>This generator creates nested decorator instances where each decorator receives
 * a @Delegate injection point referencing the next decorator in the chain (or the
 * actual bean instance for the innermost decorator).
 *
 * <p><b>Decorator Chain Architecture:</b>
 * <pre>
 * Client Code
 *     ↓ calls method
 * Outermost Decorator Proxy (Priority 100)
 *     ↓ @Delegate field → injects reference to next decorator
 * Middle Decorator Proxy (Priority 200)
 *     ↓ @Delegate field → injects reference to next decorator
 * Innermost Decorator Proxy (Priority 300)
 *     ↓ @Delegate field → injects reference to target
 * Actual Bean Instance (Target)
 * </pre>
 *
 * <p><b>Key Features:</b>
 * <ul>
 *   <li>Creates decorator instances via CDI (supports dependency injection)</li>
 *   <li>Injects @Delegate references automatically (field, constructor, or method injection)</li>
 *   <li>Handles multiple decorators with proper priority ordering</li>
 *   <li>Cache decorator classes for performance</li>
 *   <li>Supports interface-based and class-based decoration</li>
 * </ul>
 *
 * <p><b>Example Usage:</b>
 * <pre>{@code
 * // Given decorators: TimingDecorator, LoggingDecorator
 * // And target: PaymentProcessorImpl
 *
 * DecoratorAwareProxyGenerator generator = new DecoratorAwareProxyGenerator();
 * DecoratorChain chain = generator.createDecoratorChain(
 *     targetInstance,        // PaymentProcessorImpl
 *     decoratorInfos,        // [TimingDecorator, LoggingDecorator]
 *     beanManager,
 *     creationalContext
 * );
 *
 * Object decorated = chain.getOutermostInstance();
 * // decorated is TimingDecorator wrapping LoggingDecorator wrapping PaymentProcessorImpl
 * }</pre>
 *
 * <p><b>Differences from InterceptorAwareProxyGenerator:</b>
 * <table>
 * <tr><th>Aspect</th><th>InterceptorAwareProxyGenerator</th><th>DecoratorAwareProxyGenerator</th></tr>
 * <tr><td>Wrapping</td><td>Single proxy with method interception</td><td>Nested decorator instances</td></tr>
 * <tr><td>Delegation</td><td>InvocationContext.proceed()</td><td>@Delegate injection</td></tr>
 * <tr><td>Instances</td><td>One proxy wraps target</td><td>Multiple decorators wrap each other</td></tr>
 * <tr><td>Injection</td><td>N/A (interceptors are stateless)</td><td>@Delegate injected at runtime</td></tr>
 * </table>
 *
 * <p><b>Thread Safety:</b>
 * <ul>
 *   <li>Decorator class cache is thread-safe (ConcurrentHashMap)</li>
 *   <li>Decorator instances may be stateful (managed by CDI scope)</li>
 *   <li>@Delegate references are final once injected</li>
 * </ul>
 *
 * @see DecoratorChain
 * @see DecoratorResolver
 * @see DecoratorInfo
 * @author Stefano Reksten
 */
public class DecoratorAwareProxyGenerator {

    // Cache generated decorator proxy classes to avoid regenerating for the same decorator type
    // Key: Decorator class
    // Value: Generated proxy class (if needed for decoration)
    private final ConcurrentHashMap<Class<?>, Class<?>> decoratorProxyCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<?>, Class<?>> decoratedTypeBridgeProxyCache = new ConcurrentHashMap<>();
    private final Map<Object, DecoratorChain> activeChains =
            Collections.synchronizedMap(new WeakHashMap<>());

    public void clearCache() {
        decoratorProxyCache.clear();
        decoratedTypeBridgeProxyCache.clear();
        activeChains.clear();
    }

    /**
     * Creates a decorator chain wrapping the target instance.
     *
     * <p>This method:
     * <ol>
     *   <li>Creates decorator instances via BeanManager.getReference()</li>
     *   <li>Injects @Delegate references (each decorator receives the next decorator/target)</li>
     *   <li>Builds a DecoratorChain with all decorator instances</li>
     *   <li>Returns the chain (caller uses chain.getOutermostInstance())</li>
     * </ol>
     *
     * <p><b>Decorator Instance Creation:</b>
     * Decorators are CDI beans, so they're created using BeanManager.getReference().
     * This ensures they receive full dependency injection (constructor, field, method, @PostConstruct).
     *
     * <p><b>@Delegate Injection:</b>
     * After creating each decorator instance, this method finds the @Delegate injection point
     * (field, constructor parameter, or method parameter) and injects the appropriate delegate
     * (next decorator or target instance).
     *
     * @param targetInstance the actual bean instance to be decorated
     * @param decoratorInfos ordered list of decorators (by priority, outermost first)
     * @param beanManager the BeanManager for creating decorator instances
     * @param creationalContext the CreationalContext for managing decorator lifecycle
     * @return a DecoratorChain containing all decorator instances
     * @throws IllegalStateException if decorator creation or injection fails
     */
    public DecoratorChain createDecoratorChain(
            @Nonnull Object targetInstance,
            @Nonnull List<DecoratorInfo> decoratorInfos,
            @Nonnull BeanManager beanManager,
            @Nonnull CreationalContext<?> creationalContext) {

        // If no decorators, return a chain with just the target
        if (decoratorInfos.isEmpty()) {
            return new DecoratorChainBuilder()
                    .setTarget(targetInstance)
                    .build();
        }

        // Build a decorator chain from innermost to outermost
        // (We need to create decorators in reverse order for constructor injection)
        DecoratorChainBuilder chainBuilder = new DecoratorChainBuilder();
        List<Object> decoratorInstances = new ArrayList<>();

        // Create decorators in reverse order (innermost first)
        // This way, when we create a decorator, its delegate already exists
        Object currentDelegate = targetInstance;

        for (int i = decoratorInfos.size() - 1; i >= 0; i--) {
            DecoratorInfo decoratorInfo = decoratorInfos.get(i);

            // Check if this decorator uses constructor @Delegate injection
            InjectionPoint delegateInjectionPoint = decoratorInfo.getDelegateInjectionPoint();
            boolean isConstructorInjection = delegateInjectionPoint.getMember() instanceof Constructor;

            Object decoratorInstance;
            if (isConstructorInjection) {
                // Constructor injection: Pass delegate during instantiation
                decoratorInstance = createDecoratorInstanceWithConstructorDelegate(
                        decoratorInfo,
                        currentDelegate,
                        beanManager,
                        creationalContext
                );
            } else {
                // Field/method injection: Create instance first, inject delegate later
                decoratorInstance = createDecoratorInstance(
                        decoratorInfo,
                        beanManager,
                        creationalContext
                );

                // Inject the delegate
                injectDelegate(decoratorInstance, decoratorInfo, currentDelegate);
            }

            // Add to the list (will be reversed later)
            decoratorInstances.add(0, decoratorInstance);

            // Update delegate for next decorator
            currentDelegate = decoratorInstance;
        }

        // Add decorators to the chain (now in correct order: outermost first)
        for (int i = 0; i < decoratorInstances.size(); i++) {
            chainBuilder.addDecorator(decoratorInfos.get(i), decoratorInstances.get(i));
        }

        Object rawOutermost = decoratorInstances.isEmpty() ? targetInstance : decoratorInstances.get(0);
        Object exposedOutermost = adaptDecoratedReference(targetInstance, rawOutermost);

        // Set target and build
        chainBuilder.setTarget(targetInstance).setOutermostInstance(exposedOutermost);
        DecoratorChain chain = chainBuilder.build();
        activeChains.put(chain.getOutermostInstance(), chain);
        if (rawOutermost != chain.getOutermostInstance()) {
            activeChains.put(rawOutermost, chain);
        }
        return chain;
    }

    public void destroyDecoratorChain(Object outermostInstance) {
        if (outermostInstance == null) {
            return;
        }

        DecoratorChain chain;
        synchronized (activeChains) {
            chain = activeChains.remove(outermostInstance);
        }
        if (chain == null || chain.isEmpty()) {
            return;
        }

        List<DecoratorInstance> decorators = chain.getDecorators();
        for (int i = decorators.size() - 1; i >= 0; i--) {
            Object decoratorInstance = decorators.get(i).getDecoratorInstance();
            try {
                LifecycleMethodHelper.invokeLifecycleMethod(decoratorInstance, PRE_DESTROY);
            } catch (Exception ignored) {
                // Continue destroying remaining decorators.
            }
        }
    }

    private Object adaptDecoratedReference(Object targetInstance, Object decoratedReference) {
        if (targetInstance == null || decoratedReference == null) {
            return decoratedReference;
        }

        Class<?> targetClass = targetInstance.getClass();
        if (targetClass.isInstance(decoratedReference) || targetClass.isInterface()) {
            return decoratedReference;
        }
        if (Modifier.isFinal(targetClass.getModifiers())) {
            return decoratedReference;
        }

        try {
            Class<?> bridgeClass = decoratedTypeBridgeProxyCache.computeIfAbsent(
                    targetClass, this::generateDecoratedTypeBridgeProxyClass);
            Object bridgeProxy = instantiateBridgeProxy(bridgeClass);
            if (bridgeProxy instanceof DecoratedTypeBridgeProxyState) {
                ((DecoratedTypeBridgeProxyState) bridgeProxy).$$_setDecoratedTypeBridgeState(
                        targetInstance,
                        decoratedReference
                );
            }
            return bridgeProxy;
        } catch (Exception e) {
            return decoratedReference;
        }
    }

    private Class<?> generateDecoratedTypeBridgeProxyClass(Class<?> targetClass) {
        try {
            return new ByteBuddy()
                    .subclass(targetClass, ConstructorStrategy.Default.IMITATE_SUPER_CLASS)
                    .defineField("$$_decoratorTargetInstance", Object.class,
                            net.bytebuddy.description.modifier.Visibility.PRIVATE)
                    .defineField("$$_decoratorDelegateInstance", Object.class,
                            net.bytebuddy.description.modifier.Visibility.PRIVATE)
                    .implement(DecoratedTypeBridgeProxyState.class)
                    .method(ElementMatchers.named("$$_setDecoratedTypeBridgeState"))
                    .intercept(FieldAccessor.ofField("$$_decoratorTargetInstance").setsArgumentAt(0)
                            .andThen(FieldAccessor.ofField("$$_decoratorDelegateInstance").setsArgumentAt(1)))
                    .method(ElementMatchers.named("$$_getDecoratedTypeBridgeTarget"))
                    .intercept(FieldAccessor.ofField("$$_decoratorTargetInstance"))
                    .method(ElementMatchers.named("$$_getDecoratedTypeBridgeDelegate"))
                    .intercept(FieldAccessor.ofField("$$_decoratorDelegateInstance"))
                    .method(ElementMatchers.any()
                            .and(ElementMatchers.not(ElementMatchers.isDeclaredBy(Object.class)))
                            .and(ElementMatchers.not(ElementMatchers.isDeclaredBy(DecoratedTypeBridgeProxyState.class)))
                            .and(ElementMatchers.not(ElementMatchers.isPrivate()))
                            .and(ElementMatchers.not(ElementMatchers.isStatic())))
                    .intercept(MethodDelegation.to(DecoratedTypeBridgeMethodInterceptor.class))
                    .make()
                    .load(targetClass.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                    .getLoaded();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to generate decorated-type bridge proxy for " + targetClass.getName(), e);
        }
    }

    private Object instantiateBridgeProxy(Class<?> bridgeClass) throws Exception {
        try {
            Constructor<?> noArg = bridgeClass.getDeclaredConstructor();
            noArg.setAccessible(true);
            return noArg.newInstance();
        } catch (NoSuchMethodException ignored) {
            // Fall back to constructors with default values.
        } catch (Exception e) {
            Object allocated = allocateWithoutConstructor(bridgeClass);
            if (allocated != null) {
                return allocated;
            }
            throw e;
        }

        Constructor<?>[] constructors = bridgeClass.getDeclaredConstructors();
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

        Object allocated = allocateWithoutConstructor(bridgeClass);
        if (allocated != null) {
            return allocated;
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new IllegalStateException("Failed to instantiate decorated-type bridge proxy: " + bridgeClass.getName());
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
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }

    /**
     * Creates a decorator instance using the BeanManager.
     *
     * <p>This method resolves the decorator bean and creates an instance using
     * BeanManager.getReference(). This ensures the decorator receives full CDI
     * dependency injection.
     *
     * @param decoratorInfo the decorator metadata
     * @param beanManager the BeanManager
     * @param creationalContext the CreationalContext
     * @return the decorator instance
     * @throws IllegalStateException if the decorator bean cannot be resolved
     */
    private Object createDecoratorInstance(
            DecoratorInfo decoratorInfo,
            BeanManager beanManager,
            CreationalContext<?> creationalContext) {

        Class<?> decoratorClass = decoratorInfo.getDecoratorClass();
        Class<?> instantiationClass = getOrGenerateDecoratorProxyClass(decoratorClass);
        Bean<?> decoratorBean = createSyntheticDecoratorBean(decoratorClass);

        try {
            Constructor<?> constructor = findInjectionConstructor(decoratorClass);
            if (!constructor.getDeclaringClass().equals(instantiationClass)) {
                constructor = instantiationClass.getDeclaredConstructor(constructor.getParameterTypes());
            }
            constructor.setAccessible(true);
            Parameter[] parameters = constructor.getParameters();
            Object[] args = new Object[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                Parameter parameter = parameters[i];
                if (hasDelegateAnnotation(parameter)) {
                    throw new IllegalStateException("Decorator " + decoratorClass.getName() +
                            " requires @Delegate constructor injection, but createDecoratorInstance was used");
                }
                args[i] = beanManager.getInjectableReference(
                        new InjectionPointImpl<>(parameter, decoratorBean), creationalContext);
            }

            Object instance = constructor.newInstance(args);
            injectNonDelegateMembers(instance, decoratorClass, decoratorBean, beanManager, creationalContext);
            return instance;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create decorator instance " + decoratorClass.getName(), e);
        }
    }

    /**
     * Creates a decorator instance with constructor @Delegate injection.
     *
     * <p>This method manually instantiates the decorator by calling its constructor
     * with the delegate parameter, then performs field/method injection.
     *
     * @param decoratorInfo the decorator metadata
     * @param delegate the delegate to inject via constructor
     * @param beanManager the BeanManager
     * @param creationalContext the CreationalContext
     * @return the decorator instance
     * @throws IllegalStateException if decorator creation fails
     */
    private Object createDecoratorInstanceWithConstructorDelegate(
            DecoratorInfo decoratorInfo,
            Object delegate,
            BeanManager beanManager,
            CreationalContext<?> creationalContext) {

        Class<?> decoratorClass = decoratorInfo.getDecoratorClass();
        Class<?> instantiationClass = getOrGenerateDecoratorProxyClass(decoratorClass);
        InjectionPoint delegateInjectionPoint = decoratorInfo.getDelegateInjectionPoint();

        try {
            Constructor<?> delegateConstructor = (Constructor<?>) delegateInjectionPoint.getMember();
            Constructor<?> constructor = delegateConstructor;
            if (!delegateConstructor.getDeclaringClass().equals(instantiationClass)) {
                constructor = instantiationClass.getDeclaredConstructor(delegateConstructor.getParameterTypes());
            }
            constructor.setAccessible(true);
            Parameter[] parameters = delegateConstructor.getParameters();
            int delegateParameterPosition = resolveDelegateParameterPosition(delegateInjectionPoint, parameters);
            Object[] args = new Object[parameters.length];
            Bean<?> decoratorBean = createSyntheticDecoratorBean(decoratorClass);

            for (int i = 0; i < parameters.length; i++) {
                if (i == delegateParameterPosition || hasDelegateAnnotation(parameters[i])) {
                    args[i] = delegate;
                } else {
                    args[i] = beanManager.getInjectableReference(
                            new InjectionPointImpl<>(parameters[i], decoratorBean),
                            creationalContext
                    );
                }
            }

            Object decoratorInstance = constructor.newInstance(args);
            injectNonDelegateMembers(decoratorInstance, decoratorClass, decoratorBean, beanManager, creationalContext);
            return decoratorInstance;

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to create decorator instance with constructor @Delegate: " +
                    decoratorClass.getName() + ": " + e.getMessage(), e
            );
        }
    }

    private int resolveDelegateParameterPosition(InjectionPoint delegateInjectionPoint, Parameter[] parameters) {
        if (delegateInjectionPoint == null || parameters == null || parameters.length == 0) {
            return -1;
        }
        if (!(delegateInjectionPoint.getMember() instanceof Constructor)) {
            return -1;
        }
        if (delegateInjectionPoint.getAnnotated() instanceof AnnotatedParameter<?>) {
            return ((AnnotatedParameter<?>) delegateInjectionPoint.getAnnotated()).getPosition();
        }

        Type delegateType = delegateInjectionPoint.getType();
        if (delegateType == null) {
            return -1;
        }

        int matchedPosition = -1;
        for (int i = 0; i < parameters.length; i++) {
            if (!delegateType.equals(parameters[i].getParameterizedType())) {
                continue;
            }
            if (matchedPosition >= 0) {
                return -1;
            }
            matchedPosition = i;
        }
        return matchedPosition;
    }

    private Constructor<?> findInjectionConstructor(Class<?> decoratorClass) throws NoSuchMethodException {
        Constructor<?> injectConstructor = null;
        for (Constructor<?> constructor : decoratorClass.getDeclaredConstructors()) {
            if (hasInjectAnnotation(constructor)) {
                injectConstructor = constructor;
                break;
            }
        }
        if (injectConstructor != null) {
            return injectConstructor;
        }
        return decoratorClass.getDeclaredConstructor();
    }

    private void injectNonDelegateMembers(Object instance,
                                          Class<?> decoratorClass,
                                          Bean<?> decoratorBean,
                                          BeanManager beanManager,
                                          CreationalContext<?> creationalContext) throws Exception {
        for (Field field : decoratorClass.getDeclaredFields()) {
            if (!hasInjectAnnotation(field) || hasDelegateAnnotation(field)) {
                continue;
            }
            field.setAccessible(true);
            Object value = beanManager.getInjectableReference(new InjectionPointImpl<>(field, decoratorBean), creationalContext);
            field.set(instance, value);
        }

        for (Method method : decoratorClass.getDeclaredMethods()) {
            if (!hasInjectAnnotation(method)) {
                continue;
            }
            Parameter[] parameters = method.getParameters();
            Object[] args = new Object[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                if (hasDelegateAnnotation(parameters[i])) {
                    continue;
                }
                args[i] = beanManager.getInjectableReference(
                        new InjectionPointImpl<>(parameters[i], decoratorBean), creationalContext);
            }
            method.setAccessible(true);
            method.invoke(instance, args);
        }
    }

    private Bean<?> createSyntheticDecoratorBean(Class<?> decoratorClass) {
        Set<Type> types = new HashSet<>();
        types.add(decoratorClass);
        Collections.addAll(types, decoratorClass.getGenericInterfaces());

        return new Bean<Object>() {
            @Override
            public Class<?> getBeanClass() {
                return decoratorClass;
            }

            @Override
            public Set<InjectionPoint> getInjectionPoints() {
                return Collections.emptySet();
            }

            @Override
            public String getName() {
                return null;
            }

            @Override
            public Set<Annotation> getQualifiers() {
                Set<Annotation> qualifiers = new HashSet<>();
                qualifiers.add(Default.Literal.INSTANCE);
                qualifiers.add(Any.Literal.INSTANCE);
                return qualifiers;
            }

            @Override
            public Class<? extends Annotation> getScope() {
                return Dependent.class;
            }

            @Override
            public Set<Class<? extends Annotation>> getStereotypes() {
                return Collections.emptySet();
            }

            @Override
            public Set<Type> getTypes() {
                return types;
            }

            @Override
            public boolean isAlternative() {
                return false;
            }

            @Override
            public Object create(CreationalContext<Object> creationalContext) {
                throw new UnsupportedOperationException("Synthetic decorator bean does not support create()");
            }

            @Override
            public void destroy(Object instance, CreationalContext<Object> creationalContext) {
                // no-op
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
                return decoratorClass.equals(other.getBeanClass());
            }

            @Override
            public int hashCode() {
                return decoratorClass.hashCode();
            }
        };
    }

    /**
     * Injects the @Delegate reference into a decorator instance.
     *
     * <p>This method finds the @Delegate injection point (field, constructor param, or method param)
     * and injects the delegate instance using reflection.
     *
     * <p><b>Supported @Delegate Injection Points:</b>
     * <ul>
     *   <li><b>Field injection</b>: {@code @Inject @Delegate PaymentProcessor delegate;}</li>
     *   <li><b>Constructor injection</b>: {@code @Inject TimingDecorator(@Delegate PaymentProcessor delegate)}</li>
     *   <li><b>Method injection</b>: {@code @Inject setDelegate(@Delegate PaymentProcessor delegate)}</li>
     * </ul>
     *
     * @param decoratorInstance the decorator instance
     * @param decoratorInfo the decorator metadata (contains @Delegate injection point info)
     * @param delegate the delegate to inject (next decorator or target)
     * @throws IllegalStateException if injection fails
     */
    private void injectDelegate(
            Object decoratorInstance,
            DecoratorInfo decoratorInfo,
            Object delegate) {

        InjectionPoint delegateInjectionPoint = decoratorInfo.getDelegateInjectionPoint();

        try {
            Member delegateMember = delegateInjectionPoint != null ? delegateInjectionPoint.getMember() : null;

            // Field injection
            if (delegateMember instanceof Field) {
                Field field = (Field) delegateMember;
                if (Modifier.isStatic(field.getModifiers())) {
                    if (injectDelegateByScanning(decoratorInstance, delegate)) {
                        return;
                    }
                    throw new IllegalStateException("Resolved @Delegate field is static: " + field.getName());
                }
                field.setAccessible(true);
                field.set(decoratorInstance, delegate);
            }
            // Constructor injection - already handled during instance creation
            else if (delegateMember instanceof Constructor) {
                // Constructor injection is handled by createDecoratorInstanceWithConstructorDelegate()
                // Nothing to do here - delegate was passed during constructor call
            }
            // Method injection
            else if (delegateMember instanceof Method) {
                Method method = (Method) delegateMember;
                method.setAccessible(true);

                // Find the @Delegate parameter index
                Parameter[] parameters = method.getParameters();
                int delegateParamIndex = -1;
                for (int i = 0; i < parameters.length; i++) {
                    if (hasDelegateAnnotation(parameters[i])) {
                        delegateParamIndex = i;
                        break;
                    }
                }

                if (delegateParamIndex >= 0) {
                    // Create an args array with delegate at the correct position
                    Object[] args = new Object[parameters.length];
                    args[delegateParamIndex] = delegate;
                    method.invoke(decoratorInstance, args);
                }
            } else if (!injectDelegateByScanning(decoratorInstance, delegate)) {
                throw new IllegalStateException("No usable @Delegate injection point member found");
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to inject @Delegate into " + decoratorInfo.getDecoratorClass().getName() +
                    ": " + e.getMessage(), e
            );
        }
    }

    private boolean injectDelegateByScanning(Object decoratorInstance, Object delegate) throws Exception {
        Class<?> current = decoratorInstance.getClass();
        while (current != null && !Object.class.equals(current)) {
            for (Field field : current.getDeclaredFields()) {
                if (!hasDelegateAnnotation(field) || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                field.set(decoratorInstance, delegate);
                return true;
            }
            current = current.getSuperclass();
        }

        current = decoratorInstance.getClass();
        while (current != null && !Object.class.equals(current)) {
            for (Method method : current.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                Parameter[] parameters = method.getParameters();
                int delegateParamIndex = -1;
                for (int i = 0; i < parameters.length; i++) {
                    if (hasDelegateAnnotation(parameters[i])) {
                        delegateParamIndex = i;
                        break;
                    }
                }
                if (delegateParamIndex < 0) {
                    continue;
                }
                Object[] args = new Object[parameters.length];
                args[delegateParamIndex] = delegate;
                method.setAccessible(true);
                method.invoke(decoratorInstance, args);
                return true;
            }
            current = current.getSuperclass();
        }

        return false;
    }

    /**
     * Creates a proxy class for a decorator (if needed).
     *
     * <p>This method is currently a placeholder. In most cases, decorators don't need
     * proxy classes because they're concrete classes that implement the decorated interface.
     *
     * <p>Future enhancement: If a decorator needs to be proxied (e.g., for lazy loading
     * or additional interception), this method can be implemented using ByteBuddy.
     *
     * @param decoratorClass the decorator class
     * @return the decorator class (or proxy class if needed)
     */
    @SuppressWarnings("unused")
    private Class<?> getOrGenerateDecoratorProxyClass(Class<?> decoratorClass) {
        return decoratorProxyCache.computeIfAbsent(decoratorClass, this::generateDecoratorProxyClass);
    }

    /**
     * Generates a proxy class for a decorator using ByteBuddy.
     *
     * <p>This is a placeholder for future enhancement. Currently, decorators are used
     * directly without additional proxying.
     *
     * @param decoratorClass the decorator class
     * @return the generated proxy class
     */
    private Class<?> generateDecoratorProxyClass(Class<?> decoratorClass) {
        if (!Modifier.isAbstract(decoratorClass.getModifiers())) {
            return decoratorClass;
        }
        try {
            return new ByteBuddy()
                    .subclass(decoratorClass, ConstructorStrategy.Default.IMITATE_SUPER_CLASS)
                    .method(ElementMatchers.isAbstract())
                    .intercept(MethodDelegation.to(AbstractMethodDelegateInterceptor.class))
                    .make()
                    .load(decoratorClass.getClassLoader())
                    .getLoaded();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate concrete decorator proxy for "
                    + decoratorClass.getName(), e);
        }
    }

    public interface DecoratedTypeBridgeProxyState {
        void $$_setDecoratedTypeBridgeState(Object targetInstance, Object delegateInstance);

        Object $$_getDecoratedTypeBridgeTarget();

        Object $$_getDecoratedTypeBridgeDelegate();
    }

    public static class DecoratedTypeBridgeMethodInterceptor {

        @RuntimeType
        public static Object intercept(
                @FieldValue("$$_decoratorTargetInstance") Object targetInstance,
                @FieldValue("$$_decoratorDelegateInstance") Object delegateInstance,
                @Origin Method method,
                @AllArguments Object[] args) throws Throwable {

            Method delegateMethod = findCompatibleMethod(delegateInstance, method);
            if (delegateMethod != null) {
                return invoke(delegateMethod, delegateInstance, args);
            }

            Method targetMethod = findCompatibleMethod(targetInstance, method);
            if (targetMethod != null) {
                return invoke(targetMethod, targetInstance, args);
            }

            throw new IllegalStateException(
                    "No compatible method '" + method.getName() + "' found for decorator bridge delegate.");
        }

        private static Method findCompatibleMethod(Object instance, Method referenceMethod) {
            if (instance == null || referenceMethod == null) {
                return null;
            }
            Class<?> type = instance.getClass();

            try {
                return type.getMethod(referenceMethod.getName(), referenceMethod.getParameterTypes());
            } catch (NoSuchMethodException ignored) {
                // Fallback to signature matching by type names for cross-loader compatibility.
            }

            Class<?> current = type;
            while (current != null && current != Object.class) {
                for (Method candidate : current.getDeclaredMethods()) {
                    if (!candidate.getName().equals(referenceMethod.getName())) {
                        continue;
                    }
                    if (!parameterTypesCompatible(candidate.getParameterTypes(), referenceMethod.getParameterTypes())) {
                        continue;
                    }
                    return candidate;
                }
                current = current.getSuperclass();
            }
            return null;
        }

        private static boolean parameterTypesCompatible(Class<?>[] candidate, Class<?>[] reference) {
            if (candidate.length != reference.length) {
                return false;
            }
            for (int i = 0; i < candidate.length; i++) {
                if (candidate[i] == reference[i]) {
                    continue;
                }
                if (candidate[i].isPrimitive() || reference[i].isPrimitive()) {
                    return false;
                }
                if (candidate[i].getName().equals(reference[i].getName())) {
                    continue;
                }
                if (candidate[i].isAssignableFrom(reference[i]) || reference[i].isAssignableFrom(candidate[i])) {
                    continue;
                }
                return false;
            }
            return true;
        }

        private static Object invoke(Method method, Object instance, Object[] args) throws Throwable {
            try {
                method.setAccessible(true);
                return method.invoke(instance, args);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getTargetException();
                throw cause != null ? cause : e;
            }
        }
    }

    public static class AbstractMethodDelegateInterceptor {

        @RuntimeType
        public static Object intercept(@This Object decorator,
                                       @Origin Method method,
                                       @AllArguments Object[] args) throws Throwable {
            Field delegateField = findDelegateField(decorator.getClass());
            if (delegateField == null) {
                throw new IllegalStateException("Abstract decorator method invoked without @Delegate field: "
                        + decorator.getClass().getName());
            }
            delegateField.setAccessible(true);
            Object delegate = delegateField.get(decorator);
            if (delegate == null) {
                throw new IllegalStateException("@Delegate not injected for decorator " + decorator.getClass().getName());
            }
            Method delegateMethod = delegate.getClass().getMethod(method.getName(), method.getParameterTypes());
            delegateMethod.setAccessible(true);
            return delegateMethod.invoke(delegate, args);
        }

        private static Field findDelegateField(Class<?> type) {
            Class<?> current = type;
            while (current != null && !Object.class.equals(current)) {
                for (Field field : current.getDeclaredFields()) {
                    if (hasDelegateAnnotation(field)) {
                        return field;
                    }
                }
                current = current.getSuperclass();
            }
            return null;
        }
    }
}
