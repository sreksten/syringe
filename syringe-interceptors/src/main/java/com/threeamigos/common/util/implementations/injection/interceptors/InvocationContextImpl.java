package com.threeamigos.common.util.implementations.injection.interceptors;

import jakarta.interceptor.InvocationContext;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Implementation of Jakarta Interceptors InvocationContext.
 *
 * <p>This class provides the context for interceptor method invocations, allowing interceptors to:
 * <ul>
 *   <li>Access and modify method parameters via {@link #getParameters()} and {@link #setParameters(Object[])}</li>
 *   <li>Access the target instance via {@link #getTarget()}</li>
 *   <li>Access the invoked method via {@link #getMethod()} or constructor via {@link #getConstructor()}</li>
 *   <li>Share data between interceptors via {@link #getContextData()}</li>
 *   <li>Proceed to the next interceptor or target method via {@link #proceed()}</li>
 * </ul>
 *
 * <p><b>Usage in Interceptors:</b>
 * <pre>{@code
 * @AroundInvoke
 * public Object intercept(InvocationContext ctx) throws Exception {
 *     // Modify parameters
 *     Object[] params = ctx.getParameters();
 *     params[0] = "modified";
 *     ctx.setParameters(params);
 *
 *     // Share data with other interceptors
 *     ctx.getContextData().put("startTime", System.currentTimeMillis());
 *
 *     // Proceed to next interceptor or target method
 *     return ctx.proceed();
 * }
 * }</pre>
 *
 * <p><b>Thread Safety:</b> InvocationContext instances are NOT thread-safe and should not be shared
 * across threads. Each invocation gets its own instance.
 *
 * @see InvocationContext
 * @see jakarta.interceptor.AroundInvoke
 * @see jakarta.interceptor.AroundConstruct
 */
public class InvocationContextImpl implements InvocationContext {

    /**
     * The target object instance (null for @AroundConstruct before construction).
     */
    private Object target;

    /**
     * The method being intercepted (null for constructor interception).
     */
    private final Method method;

    /**
     * The constructor being intercepted (null for method interception).
     */
    private final Constructor<?> constructor;

    /**
     * The method/constructor parameters (can be modified by interceptors).
     */
    private Object[] parameters;

    /**
     * Shared context data map for passing data between interceptors.
     */
    private final Map<String, Object> contextData;

    /**
     * The chain of interceptor invocations to execute.
     */
    private final InterceptorChain chain;
    private final Set<Annotation> interceptorBindings;

    /**
     * Current position in the interceptor chain.
     */
    private int currentPosition = 0;

    /**
     * The final target method/constructor invocation to execute after all interceptors.
     */
    private final TargetInvocation targetInvocation;

    /**
     * Creates an InvocationContext for method interception (@AroundInvoke).
     *
     * @param target the target bean instance
     * @param method the method being intercepted
     * @param parameters the method parameters
     * @param chain the interceptor chain
     * @param targetInvocation the final target method invocation
     */
    public InvocationContextImpl(
            Object target,
            Method method,
            Object[] parameters,
            InterceptorChain chain,
            TargetInvocation targetInvocation) {

        this.target = Objects.requireNonNull(target, "target cannot be null for method interception");
        this.method = Objects.requireNonNull(method, "method cannot be null for method interception");
        this.constructor = null;
        this.parameters = parameters != null ? parameters : new Object[0];
        this.contextData = new HashMap<>();
        this.chain = Objects.requireNonNull(chain, "chain cannot be null");
        this.interceptorBindings = copyInterceptorBindings(chain);
        this.targetInvocation = Objects.requireNonNull(targetInvocation, "targetInvocation cannot be null");
    }

    /**
     * Creates an InvocationContext for constructor interception (@AroundConstruct).
     *
     * @param constructor the constructor being intercepted
     * @param parameters the constructor parameters
     * @param chain the interceptor chain
     * @param targetInvocation the final constructor invocation
     */
    public InvocationContextImpl(
            Constructor<?> constructor,
            Object[] parameters,
            InterceptorChain chain,
            TargetInvocation targetInvocation) {

        this.target = null; // Target not available until construction completes
        this.method = null;
        this.constructor = Objects.requireNonNull(constructor, "constructor cannot be null for constructor interception");
        this.parameters = parameters != null ? parameters : new Object[0];
        this.contextData = new HashMap<>();
        this.chain = Objects.requireNonNull(chain, "chain cannot be null");
        this.interceptorBindings = copyInterceptorBindings(chain);
        this.targetInvocation = Objects.requireNonNull(targetInvocation, "targetInvocation cannot be null");
    }

    /**
     * Creates an InvocationContext for lifecycle callback interception (@PostConstruct, @PreDestroy).
     *
     * @param target the target bean instance
     * @param chain the interceptor chain
     * @param targetInvocation the final target callback invocation
     */
    public InvocationContextImpl(
            Object target,
            InterceptorChain chain,
            TargetInvocation targetInvocation) {

        this.target = Objects.requireNonNull(target, "target cannot be null for lifecycle callback");
        this.method = null;
        this.constructor = null;
        this.parameters = new Object[0]; // Lifecycle callbacks have no parameters
        this.contextData = new HashMap<>();
        this.chain = Objects.requireNonNull(chain, "chain cannot be null");
        this.interceptorBindings = copyInterceptorBindings(chain);
        this.targetInvocation = Objects.requireNonNull(targetInvocation, "targetInvocation cannot be null");
    }

    @Override
    public Object getTarget() {
        return target;
    }

    /**
     * Sets the target instance (used internally after @AroundConstruct completes).
     *
     * @param target the constructed target instance
     */
    public void setTarget(Object target) {
        this.target = target;
    }

    @Override
    public Method getMethod() {
        return method;
    }

    @Override
    public Constructor<?> getConstructor() {
        return constructor;
    }

    @Override
    public Object[] getParameters() {
        return parameters;
    }

    @Override
    public void setParameters(Object[] params) {
        if (params == null) {
            throw new IllegalArgumentException("parameters cannot be null");
        }

        // Validate that parameter count matches method or constructor signature
        int expectedCount = getExpectedParameterCount();
        if (params.length != expectedCount) {
            throw new IllegalArgumentException(
                    "Parameter count mismatch: expected " + expectedCount + " but got " + params.length);
        }

        this.parameters = params;
    }

    @Override
    public Map<String, Object> getContextData() {
        return contextData;
    }

    @Override
    public Object getTimer() {
        // EJB Timer feature - not supported in this implementation
        return null;
    }

    @Override
    public Set<Annotation> getInterceptorBindings() {
        return interceptorBindings;
    }

    @Override
    public <T extends Annotation> T getInterceptorBinding(Class<T> annotationType) {
        if (annotationType == null || interceptorBindings.isEmpty()) {
            return null;
        }
        for (Annotation interceptorBinding : interceptorBindings) {
            if (annotationType.equals(interceptorBinding.annotationType())) {
                return annotationType.cast(interceptorBinding);
            }
        }
        return null;
    }

    @Override
    public <T extends Annotation> Set<T> getInterceptorBindings(Class<T> annotationType) {
        if (annotationType == null || interceptorBindings.isEmpty()) {
            return Collections.emptySet();
        }
        Set<T> out = new LinkedHashSet<>();
        for (Annotation interceptorBinding : interceptorBindings) {
            if (annotationType.equals(interceptorBinding.annotationType())) {
                out.add(annotationType.cast(interceptorBinding));
            }
        }
        return Collections.unmodifiableSet(out);
    }

    /**
     * Proceeds to the next interceptor in the chain, or to the target method if all interceptors have been invoked.
     *
     * <p>This method is called by each interceptor to pass control to the next interceptor or to the target.
     * The interceptor chain is executed in priority order (lower priority value = earlier execution).
     *
     * <p><b>Execution Flow:</b>
     * <pre>
     * Client → Interceptor1.proceed() → Interceptor2.proceed() → Target Method
     *          ← return ← return ← return result
     * </pre>
     *
     * @return the result of the next interceptor or target method invocation
     * @throws Exception any exception thrown by interceptors or target method
     */
    @Override
    public Object proceed() throws Exception {
        if (currentPosition < chain.getInvocations().size()) {
            // Advance the position only for successful navigation to the next chain element.
            // If invocation throws, restore the position so callers can catch and retry proceed().
            int interceptorPosition = currentPosition;
            InterceptorChainModel.InterceptorInvocation nextInterceptor = chain.getInvocations().get(interceptorPosition);
            currentPosition = interceptorPosition + 1;
            try {
                return nextInterceptor.invoke(this);
            } catch (Exception | Error e) {
                currentPosition = interceptorPosition;
                throw e;
            }
        }
        // All interceptors executed - jakarta.enterprise.invoke target method/constructor
        return targetInvocation.invoke(this);
    }

    /**
     * Gets the expected parameter count based on the method or constructor signature.
     *
     * @return the expected number of parameters
     */
    private int getExpectedParameterCount() {
        if (method != null) {
            return method.getParameterCount();
        } else if (constructor != null) {
            return constructor.getParameterCount();
        } else {
            // Lifecycle callback - no parameters
            return 0;
        }
    }

    private static Set<Annotation> copyInterceptorBindings(InterceptorChain chain) {
        Set<Annotation> bindings = chain.getInterceptorBindings();
        if (bindings == null || bindings.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(bindings));
    }

    /**
     * Functional interface for the final target invocation (method, constructor, or lifecycle callback).
     *
     * <p>This allows different invocation strategies without requiring InvocationContext to know
     * the specific invocation mechanism.
     */
    @FunctionalInterface
    public interface TargetInvocation {
        /**
         * Invokes the target method, constructor, or lifecycle callback.
         *
         * @param context the invocation context containing parameters and target
         * @return the result of the invocation (null for void methods)
         * @throws Exception any exception thrown by the target
         */
        Object invoke(InvocationContext context) throws Exception;
    }

    @Override
    public String toString() {
        return "InvocationContextImpl{" +
                "target=" + (target != null ? target.getClass().getSimpleName() : "null") +
                ", method=" + (method != null ? method.getName() : "null") +
                ", constructor=" + (constructor != null ? constructor.getName() : "null") +
                ", parameterCount=" + parameters.length +
                ", currentPosition=" + currentPosition + "/" + chain.getInvocations().size() +
                '}';
    }
}
