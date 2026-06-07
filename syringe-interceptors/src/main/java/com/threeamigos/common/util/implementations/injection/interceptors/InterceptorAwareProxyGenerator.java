package com.threeamigos.common.util.implementations.injection.interceptors;

import com.threeamigos.common.util.implementations.injection.NotEnabledFeatureException;
import com.threeamigos.common.util.implementations.injection.modules.ModulesEnum;
import jakarta.enterprise.inject.spi.Bean;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Facade for interceptor-aware proxy generation.
 *
 * <p>The ByteBuddy implementation lives in syringe-proxy and is loaded reflectively to keep
 * syringe-core free from a direct ByteBuddy dependency.
 */
public class InterceptorAwareProxyGenerator {

    private static final String IMPL_CLASS_NAME =
            "com.threeamigos.common.util.implementations.injection.interceptors.ByteBuddyInterceptorAwareProxyGenerator";
    private static volatile Class<?> implementationClass;
    private static volatile boolean implementationResolved;

    private final Object delegate;

    public InterceptorAwareProxyGenerator() {
        this.delegate = instantiateDelegate();
    }

    public static void clearTargetAroundInvokeCache() {
        invokeStaticIfPresent("clearTargetAroundInvokeCache", new Class<?>[0]);
    }

    public static void clearTargetAroundInvokeCacheForClassLoader(ClassLoader classLoader) {
        invokeStaticIfPresent(
                "clearTargetAroundInvokeCacheForClassLoader",
                new Class<?>[]{ClassLoader.class},
                classLoader
        );
    }

    public void clearCache() {
        invokeDelegate("clearCache", new Class<?>[0]);
    }

    @SuppressWarnings("unchecked")
    public <T> T createProxy(Bean<T> bean,
                             T targetInstance,
                             Map<Method, ? extends InterceptorChainModel> methodInterceptorChains) {
        if (delegate == null) {
            throw interceptorNotEnabled(bean != null ? bean.getBeanClass() : null);
        }
        return (T) invokeDelegate(
                "createProxy",
                new Class<?>[]{Bean.class, Object.class, Map.class},
                bean,
                targetInstance,
                methodInterceptorChains
        );
    }

    @SuppressWarnings("unchecked")
    public <T> T createProxy(Class<T> beanClass,
                             T targetInstance,
                             Map<Method, ? extends InterceptorChainModel> methodInterceptorChains) {
        if (delegate == null) {
            throw interceptorNotEnabled(beanClass);
        }
        return (T) invokeDelegate(
                "createProxy",
                new Class<?>[]{Class.class, Object.class, Map.class},
                beanClass,
                targetInstance,
                methodInterceptorChains
        );
    }

    public interface InterceptorProxyState {
        void $$_setInterceptorProxyState(Object targetInstance,
                                         Map<Method, ? extends InterceptorChainModel> methodInterceptorChains);

        Object $$_getTargetInstance();

        Map<Method, ? extends InterceptorChainModel> $$_getMethodInterceptorChains();
    }

    private Object instantiateDelegate() {
        Class<?> implClass = resolveImplementationClass();
        if (implClass == null) {
            return null;
        }
        try {
            return implClass.getConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to instantiate " + IMPL_CLASS_NAME, e);
        }
    }

    private static void invokeStaticIfPresent(String methodName, Class<?>[] parameterTypes, Object... args) {
        Class<?> implClass = resolveImplementationClass();
        if (implClass == null) {
            return;
        }
        try {
            Method method = implClass.getMethod(methodName, parameterTypes);
            method.invoke(null, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getTargetException();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("Failed to call " + IMPL_CLASS_NAME + "#" + methodName, cause);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to call " + IMPL_CLASS_NAME + "#" + methodName, e);
        }
    }

    private Object invokeDelegate(String methodName, Class<?>[] parameterTypes, Object... args) {
        if (delegate == null) {
            throw new IllegalStateException(IMPL_CLASS_NAME + " is not available on classpath");
        }
        try {
            Method method = delegate.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(delegate, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getTargetException();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("Failed to call " + IMPL_CLASS_NAME + "#" + methodName, cause);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to call " + IMPL_CLASS_NAME + "#" + methodName, e);
        }
    }

    private static Class<?> resolveImplementationClass() {
        if (implementationResolved) {
            return implementationClass;
        }
        synchronized (InterceptorAwareProxyGenerator.class) {
            if (implementationResolved) {
                return implementationClass;
            }
            implementationClass = tryLoadWith(Thread.currentThread().getContextClassLoader());
            if (implementationClass == null) {
                implementationClass = tryLoadWith(InterceptorAwareProxyGenerator.class.getClassLoader());
            }
            implementationResolved = true;
            return implementationClass;
        }
    }

    private static Class<?> tryLoadWith(ClassLoader classLoader) {
        if (classLoader == null) {
            return null;
        }
        try {
            return Class.forName(IMPL_CLASS_NAME, true, classLoader);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static NotEnabledFeatureException interceptorNotEnabled(Class<?> beanClass) {
        String location = beanClass != null ? "class " + beanClass.getName() : "unknown bean type";
        String message =
                "interceptor-enabled bean found at " + location + " but interceptor support is not available.";
        return new NotEnabledFeatureException(message, ModulesEnum.INTERCEPTORS);
    }
}
