package com.threeamigos.common.util.implementations.injection.knowledgebase;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Set;

/**
 * Metadata holder for CDI interceptors (@Interceptor).
 *
 * <p>CDI 4.1 Interceptor Requirements:
 * <ul>
 *   <li>Must have @Interceptor annotation</li>
 *   <li>Must have at least one interceptor binding annotation</li>
 *   <li>Must have exactly one @AroundInvoke, @AroundConstruct, @PostConstruct, or @PreDestroy method</li>
 *   <li>Priority determines invocation order (lower = earlier)</li>
 * </ul>
 *
 * @see jakarta.interceptor.Interceptor
 * @see jakarta.interceptor.AroundInvoke
 * @see jakarta.interceptor.InterceptorBinding
 */
public class InterceptorInfo {

    private final Class<?> interceptorClass;
    private final Set<Annotation> interceptorBindings;
    private final int priority;
    private final Method aroundInvokeMethod;      // @AroundInvoke
    private final Method aroundConstructMethod;   // @AroundConstruct
    private final Method postConstructMethod;     // @PostConstruct
    private final Method preDestroyMethod;        // @PreDestroy

    /**
     * Creates interceptor metadata.
     *
     * @param interceptorClass the interceptor class annotated with @Interceptor
     * @param interceptorBindings the interceptor-binding annotations found on this interceptor
     * @param priority the priority value (from @Priority), defaults to Integer.MAX_VALUE if not specified
     * @param aroundInvokeMethod the @AroundInvoke method, or null if not present
     * @param aroundConstructMethod the @AroundConstruct method, or null if not present
     * @param postConstructMethod the @PostConstruct method, or null if not present
     * @param preDestroyMethod the @PreDestroy method, or null if not present
     */
    public InterceptorInfo(
            Class<?> interceptorClass,
            Set<Annotation> interceptorBindings,
            int priority,
            Method aroundInvokeMethod,
            Method aroundConstructMethod,
            Method postConstructMethod,
            Method preDestroyMethod) {

        this.interceptorClass = Objects.requireNonNull(interceptorClass, "interceptorClass cannot be null");
        this.interceptorBindings = Objects.requireNonNull(interceptorBindings, "interceptorBindings cannot be null");
        this.priority = priority;
        this.aroundInvokeMethod = aroundInvokeMethod;
        this.aroundConstructMethod = aroundConstructMethod;
        this.postConstructMethod = postConstructMethod;
        this.preDestroyMethod = preDestroyMethod;
    }

    public Class<?> getInterceptorClass() {
        return interceptorClass;
    }

    public Set<Annotation> getInterceptorBindings() {
        return interceptorBindings;
    }

    public int getPriority() {
        return priority;
    }

    public Method getAroundInvokeMethod() {
        return aroundInvokeMethod;
    }

    public Method getAroundConstructMethod() {
        return aroundConstructMethod;
    }

    public Method getPostConstructMethod() {
        return postConstructMethod;
    }

    public Method getPreDestroyMethod() {
        return preDestroyMethod;
    }

    /**
     * Checks if this interceptor has any lifecycle callback methods (@PostConstruct or @PreDestroy).
     */
    public boolean hasLifecycleCallbacks() {
        return postConstructMethod != null || preDestroyMethod != null;
    }

    /**
     * Checks if this interceptor has business method interception (@AroundInvoke).
     */
    public boolean hasAroundInvoke() {
        return aroundInvokeMethod != null;
    }

    /**
     * Checks if this interceptor has constructor interception (@AroundConstruct).
     */
    public boolean hasAroundConstruct() {
        return aroundConstructMethod != null;
    }

    @Override
    public String toString() {
        return "InterceptorInfo{" +
                "class=" + interceptorClass.getName() +
                ", bindings=" + interceptorBindings +
                ", priority=" + priority +
                ", hasAroundInvoke=" + hasAroundInvoke() +
                ", hasAroundConstruct=" + hasAroundConstruct() +
                ", hasLifecycleCallbacks=" + hasLifecycleCallbacks() +
                '}';
    }
}
