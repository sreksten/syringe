package com.threeamigos.common.util.implementations.injection.scopes;

import com.threeamigos.common.util.implementations.injection.NotEnabledFeatureException;
import com.threeamigos.common.util.implementations.injection.modules.ModulesEnum;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Facade for client proxy generation.
 *
 * <p>The ByteBuddy implementation lives in syringe-proxy and is loaded reflectively to keep
 * syringe-core free from a direct ByteBuddy dependency.
 */
public class ClientProxyGenerator {

    private static final String IMPL_CLASS_NAME =
            "com.threeamigos.common.util.implementations.injection.scopes.ByteBuddyClientProxyGenerator";
    private static volatile Class<?> implementationClass;
    private static volatile boolean implementationResolved;
    @SuppressWarnings("unused") // Accessed by stress tests via reflection
    private static final Map<ClassLoader, Object> containerRegistry = new ConcurrentHashMap<ClassLoader, Object>();

    private final Object delegate;

    public ClientProxyGenerator(ContextManager contextManager) {
        this.delegate = instantiateDelegate(contextManager);
    }

    public static void registerContainer(ClassLoader classLoader, BeanManager beanManager,
                                         ContextManager contextManager) {
        if (classLoader != null) {
            containerRegistry.put(classLoader, Boolean.TRUE);
        }
        invokeStaticIfPresent(
                "registerContainer",
                new Class<?>[]{ClassLoader.class, BeanManager.class, ContextManager.class},
                classLoader,
                beanManager,
                contextManager
        );
    }

    public static void unregisterContainer(ClassLoader classLoader) {
        if (classLoader != null) {
            containerRegistry.remove(classLoader);
        }
        invokeStaticIfPresent("unregisterContainer", new Class<?>[]{ClassLoader.class}, classLoader);
    }

    public static void unregisterContainer(ClassLoader classLoader,
                                           BeanManager beanManager,
                                           ContextManager contextManager) {
        if (classLoader != null) {
            containerRegistry.remove(classLoader);
        }
        invokeStaticIfPresent(
                "unregisterContainer",
                new Class<?>[]{ClassLoader.class, BeanManager.class, ContextManager.class},
                classLoader,
                beanManager,
                contextManager
        );
    }

    public void clearCache() {
        invokeDelegate("clearCache", new Class<?>[0]);
    }

    @SuppressWarnings("unchecked")
    public <T> T createProxy(Bean<T> bean) {
        if (delegate == null) {
            throw normalScopeNotEnabled(bean);
        }
        return (T) invokeDelegate("createProxy", new Class<?>[]{Bean.class}, bean);
    }

    public interface ProxyState {
        void $$_setProxyState(Bean<?> bean, ContextManager contextManager);

        Bean<?> $$_getBean();

        ContextManager $$_getContextManager();
    }

    private Object instantiateDelegate(ContextManager contextManager) {
        Class<?> implClass = resolveImplementationClass();
        if (implClass == null) {
            return null;
        }
        try {
            Constructor<?> constructor = implClass.getConstructor(ContextManager.class);
            return constructor.newInstance(contextManager);
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
        synchronized (ClientProxyGenerator.class) {
            if (implementationResolved) {
                return implementationClass;
            }
            implementationClass = tryLoadWith(Thread.currentThread().getContextClassLoader());
            if (implementationClass == null) {
                implementationClass = tryLoadWith(ClientProxyGenerator.class.getClassLoader());
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

    private static NotEnabledFeatureException normalScopeNotEnabled(Bean<?> bean) {
        String location = bean != null && bean.getBeanClass() != null
                ? "class " + bean.getBeanClass().getName()
                : "unknown bean type";
        String message =
                "normal-scoped bean found at " + location + " but normal scope support is not available.";
        return new NotEnabledFeatureException(message, ModulesEnum.SCOPES);
    }
}
