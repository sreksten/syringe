package com.threeamigos.common.util.implementations.injection.interceptors;

import jakarta.interceptor.InvocationContext;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a chain of interceptor invocations using the Chain of Responsibility pattern.
 *
 * <p>This class manages the ordered sequence of interceptor method invocations that wrap
 * a target bean method, constructor, or lifecycle callback. Interceptors are invoked in
 * priority order (lower priority value = earlier execution).
 *
 * <p><b>Execution Flow:</b>
 * <pre>
 * InterceptorChain:
 *   1. LoggingInterceptor (priority 100)
 *   2. SecurityInterceptor (priority 200)
 *   3. TransactionalInterceptor (priority 300)
 *   4. → Target Bean Method
 *
 * Invocation:
 *   Client
 *     → LoggingInterceptor.intercept(ctx)
 *         → SecurityInterceptor.intercept(ctx)
 *             → TransactionalInterceptor.intercept(ctx)
 *                 → Target.method()
 *                 ← return result
 *             ← return result
 *         ← return result
 *     ← return result
 * </pre>
 *
 * <p><b>Thread Safety:</b> InterceptorChain instances are immutable and thread-safe once built.
 * They can be cached and reused for multiple invocations of the same method.
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * // Build chain
 * InterceptorChain chain = InterceptorChain.builder()
 *     .addInterceptor(loggingInterceptor, loggingMethod)
 *     .addInterceptor(securityInterceptor, securityMethod)
 *     .build();
 *
 * // Invoke chain
 * Object result = chain.jakarta.enterprise.invoke(targetInstance, targetMethod, args);
 * }</pre>
 *
 * @see InvocationContextImpl
 * @see InterceptorInvocation
 */
public class InterceptorChain implements InterceptorChainModel {

    /**
     * Immutable list of interceptor invocations in execution order.
     */
    private final List<InterceptorChainModel.InterceptorInvocation> invocations;
    private final Set<Annotation> interceptorBindings;

    /**
     * Private constructor - use Builder to create instances.
     *
     * @param invocations the list of interceptor invocations
     */
    private InterceptorChain(List<InterceptorChainModel.InterceptorInvocation> invocations, Set<Annotation> interceptorBindings) {
        this.invocations = Collections.unmodifiableList(new ArrayList<>(invocations));
        if (interceptorBindings == null || interceptorBindings.isEmpty()) {
            this.interceptorBindings = Collections.emptySet();
        } else {
            this.interceptorBindings = Collections.unmodifiableSet(new LinkedHashSet<>(interceptorBindings));
        }
    }

    /**
     * Returns the immutable list of interceptor invocations.
     *
     * @return the interceptor invocations in execution order
     */
    public List<InterceptorChainModel.InterceptorInvocation> getInvocations() {
        return invocations;
    }

    public Set<Annotation> getInterceptorBindings() {
        return interceptorBindings;
    }

    /**
     * Invokes the interceptor chain for a method interception.
     *
     * <p>This creates an InvocationContext and starts the chain execution.
     * Each interceptor calls {@link InvocationContext#proceed()} to continue the chain.
     *
     * @param target the target bean instance
     * @param method the method being intercepted
     * @param args the method arguments
     * @return the result of the method invocation
     * @throws Exception any exception thrown by interceptors or target method
     */
    public Object invoke(Object target, Method method, Object[] args) throws Exception {
        // Create target invocation (final step in the chain)
        InvocationContextImpl.TargetInvocation targetInvocation = ctx -> {
            Object invocationTarget = target;
            if (invocationTarget instanceof InterceptorAwareProxyGenerator.InterceptorProxyState) {
                Object unwrapped = ((InterceptorAwareProxyGenerator.InterceptorProxyState) invocationTarget)
                        .$$_getTargetInstance();
                if (unwrapped != null) {
                    invocationTarget = unwrapped;
                }
            }

            Method invocableMethod = method;
            if (!method.getDeclaringClass().isInstance(invocationTarget)) {
                invocableMethod = invocationTarget.getClass().getMethod(method.getName(), method.getParameterTypes());
            }
            invocableMethod.setAccessible(true);
            try {
                return invocableMethod.invoke(invocationTarget, ctx.getParameters());
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
        };

        // Create invocation context
        InvocationContextImpl context = new InvocationContextImpl(
                target, method, args, this, targetInvocation
        );

        // Start chain execution
        return context.proceed();
    }

    /**
     * Invokes the interceptor chain for a constructor interception (@AroundConstruct).
     *
     * <p>This creates an InvocationContext for constructor interception and starts the chain execution.
     * After all @AroundConstruct interceptors execute, the actual constructor is invoked.
     * The target instance is set after construction completes.
     *
     * @param target initially null (will be set after construction)
     * @param constructor the constructor being intercepted
     * @param args the constructor arguments
     * @return the newly constructed instance
     * @throws Exception any exception thrown by interceptors or constructor
     */
    public Object invoke(Object target, java.lang.reflect.Constructor<?> constructor, Object[] args) throws Exception {
        // Create target invocation (final step in the chain - jakarta.enterprise.invoke the actual constructor)
        InvocationContextImpl.TargetInvocation targetInvocation = ctx -> {
            constructor.setAccessible(true);
            Object instance;
            try {
                instance = constructor.newInstance(ctx.getParameters());
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
            // Set the target on the context after construction
            if (ctx instanceof InvocationContextImpl) {
                ((InvocationContextImpl) ctx).setTarget(instance);
            }
            // Per Interceptors contract, @AroundConstruct proceed() returns null.
            return null;
        };

        // Create invocation context for constructor interception
        InvocationContextImpl context = new InvocationContextImpl(
                constructor, args, this, targetInvocation
        );

        // Start chain execution. In constructor interception, proceed() returns null;
        // the constructed instance is exposed via InvocationContext.getTarget().
        context.proceed();
        return context.getTarget();
    }

    /**
     * Invokes the interceptor chain for a lifecycle callback interception.
     *
     * <p>This is used for @PostConstruct and @PreDestroy callbacks, which have no parameters
     * or return value.
     *
     * @param target the target bean instance
     * @param lifecycleCallback the lifecycle callback method (can be null if no target callback)
     * @throws Exception any exception thrown by interceptors or callback
     */
    public void invokeLifecycle(Object target, Method lifecycleCallback) throws Exception {
        // Create target invocation
        InvocationContextImpl.TargetInvocation targetInvocation = ctx -> {
            if (lifecycleCallback != null) {
                lifecycleCallback.setAccessible(true);
                try {
                    lifecycleCallback.invoke(target);
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
            return null; // Lifecycle callbacks return void
        };

        // Create invocation context
        InvocationContextImpl context = new InvocationContextImpl(
                target, this, targetInvocation
        );

        // Start chain execution
        context.proceed();
    }

    /**
     * Invokes the interceptor chain for multiple lifecycle callbacks in sequence.
     *
     * <p>This is used for @PostConstruct and @PreDestroy callbacks where there may be
     * multiple lifecycle methods in a class hierarchy that all need to be invoked.
     *
     * <p>The interceptor chain executes once, then invokes all target lifecycle methods
     * in the provided order.
     *
     * @param target the target bean instance
     * @param lifecycleCallbacks list of lifecycle callback methods to jakarta.enterprise.invoke in order
     * @throws Exception any exception thrown by interceptors or callbacks
     */
    public void invokeLifecycleChain(Object target, List<Method> lifecycleCallbacks) throws Exception {
        // Create target invocation that calls all lifecycle methods in order
        InvocationContextImpl.TargetInvocation targetInvocation = ctx -> {
            if (lifecycleCallbacks != null && !lifecycleCallbacks.isEmpty()) {
                for (Method callback : lifecycleCallbacks) {
                    callback.setAccessible(true);
                    try {
                        callback.invoke(target);
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
            }
            return null; // Lifecycle callbacks return void
        };

        // Create invocation context
        InvocationContextImpl context = new InvocationContextImpl(
                target, this, targetInvocation
        );

        // Start chain execution
        context.proceed();
    }

    /**
     * Checks if the chain is empty (no interceptors).
     *
     * @return true if there are no interceptors in the chain
     */
    public boolean isEmpty() {
        return invocations.isEmpty();
    }

    /**
     * Returns the number of interceptors in the chain.
     *
     * @return the interceptor count
     */
    public int size() {
        return invocations.size();
    }

    /**
     * Creates a new builder for constructing an InterceptorChain.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing InterceptorChain instances.
     *
     * <p>Interceptors should be added in priority order (the lowest priority first).
     * The builder does not automatically sort - callers must add interceptors in the correct order.
     */
    public static class Builder implements InterceptorChainModel.Builder {
        private final List<InterceptorChainModel.InterceptorInvocation> invocations = new ArrayList<>();
        private Set<Annotation> interceptorBindings = Collections.emptySet();

        /**
         * Adds an interceptor to the chain.
         *
         * <p>The interceptor will be invoked in the order it was added to the builder.
         * Callers should add interceptors in priority order (lower priority value = earlier).
         *
         * @param interceptorInstance the interceptor instance
         * @param interceptorMethod the interceptor method (@AroundInvoke, @AroundConstruct, etc.)
         * @return this builder for method chaining
         */
        @Override
        public Builder addInterceptor(Object interceptorInstance, Method interceptorMethod) {
            Objects.requireNonNull(interceptorInstance, "interceptorInstance cannot be null");
            Objects.requireNonNull(interceptorMethod, "interceptorMethod cannot be null");

            InterceptorChainModel.InterceptorInvocation invocation = ctx -> {
                interceptorMethod.setAccessible(true);
                try {
                    return interceptorMethod.invoke(interceptorInstance, ctx);
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
            };

            invocations.add(invocation);
            return this;
        }

        /**
         * Adds a custom interceptor invocation to the chain.
         *
         * <p>This allows for programmatic interceptor logic without a separate interceptor class.
         *
         * @param invocation the interceptor invocation
         * @return this builder for method chaining
         */
        @Override
        public Builder addInvocation(InterceptorChainModel.InterceptorInvocation invocation) {
            Objects.requireNonNull(invocation, "invocation cannot be null");
            invocations.add(invocation);
            return this;
        }

        @Override
        public Builder withInterceptorBindings(Set<Annotation> interceptorBindings) {
            if (interceptorBindings == null || interceptorBindings.isEmpty()) {
                this.interceptorBindings = Collections.emptySet();
            } else {
                this.interceptorBindings = new LinkedHashSet<>(interceptorBindings);
            }
            return this;
        }

        /**
         * Builds an immutable InterceptorChain from the added interceptors.
         *
         * @return the constructed chain
         */
        @Override
        public InterceptorChain build() {
            return new InterceptorChain(invocations, interceptorBindings);
        }
    }

    /**
     * Functional interface representing a single interceptor invocation in the chain.
     *
     * <p>This wraps the interceptor method invocation, allowing the chain to jakarta.enterprise.invoke
     * interceptors generically without knowing their specific types or methods.
     */
    @FunctionalInterface
    public interface InterceptorInvocation extends InterceptorChainModel.InterceptorInvocation {
    }

    @Override
    public String toString() {
        return "InterceptorChain{" +
                "size=" + invocations.size() +
                ", bindings=" + interceptorBindings.size() +
                '}';
    }
}
