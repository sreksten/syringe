package com.threeamigos.common.util.implementations.injection.interceptors;

import jakarta.interceptor.InvocationContext;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

/**
 * Core abstraction for interceptor invocation chains.
 *
 * <p>The concrete chain implementation lives in the interceptor feature module.
 * Core code only depends on this contract.
 */
public interface InterceptorChainModel {

    List<InterceptorInvocation> getInvocations();

    Set<Annotation> getInterceptorBindings();

    Object invoke(Object target, Method method, Object[] args) throws Exception;

    Object invoke(Object target, Constructor<?> constructor, Object[] args) throws Exception;

    void invokeLifecycleChain(Object target, List<Method> lifecycleCallbacks) throws Exception;

    boolean isEmpty();

    int size();

    interface Builder {
        Builder addInterceptor(Object interceptorInstance, Method interceptorMethod);

        Builder addInvocation(InterceptorInvocation invocation);

        Builder withInterceptorBindings(Set<Annotation> interceptorBindings);

        InterceptorChainModel build();
    }

    @FunctionalInterface
    interface InterceptorInvocation {
        Object invoke(InvocationContext context) throws Exception;
    }
}
