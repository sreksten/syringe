package com.threeamigos.common.util.implementations.injection.decorators;

import com.threeamigos.common.util.implementations.injection.NotEnabledFeatureException;
import com.threeamigos.common.util.implementations.injection.knowledgebase.DecoratorInfo;
import com.threeamigos.common.util.implementations.injection.modules.ModulesEnum;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.BeanManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Facade for decorator proxy generation.
 *
 * <p>The ByteBuddy implementation lives in syringe-proxy and is loaded reflectively to keep
 * syringe-core free from a direct ByteBuddy dependency.
 */
public class DecoratorAwareProxyGenerator {

    private static final String IMPL_CLASS_NAME =
            "com.threeamigos.common.util.implementations.injection.decorators.ByteBuddyDecoratorAwareProxyGenerator";
    private static volatile Class<?> implementationClass;
    private static volatile boolean implementationResolved;

    private final Object delegate;

    public DecoratorAwareProxyGenerator() {
        this.delegate = instantiateDelegate();
    }

    public void clearCache() {
        invokeDelegate("clearCache", new Class<?>[0]);
    }

    public Object createDecoratorChain(Object targetInstance,
                                       List<DecoratorInfo> decoratorInfos,
                                       BeanManager beanManager,
                                       CreationalContext<?> creationalContext) {
        if (delegate == null) {
            throw decoratorNotEnabled(targetInstance != null ? targetInstance.getClass() : null);
        }
        return invokeDelegate(
                "createDecoratorChain",
                new Class<?>[]{Object.class, List.class, BeanManager.class, CreationalContext.class},
                targetInstance,
                decoratorInfos,
                beanManager,
                creationalContext
        );
    }

    public void destroyDecoratorChain(Object outermostInstance) {
        if (delegate == null) {
            return;
        }
        invokeDelegate("destroyDecoratorChain", new Class<?>[]{Object.class}, outermostInstance);
    }

    public interface DecoratedTypeBridgeProxyState {
        void $$_setDecoratedTypeBridgeState(Object targetInstance, Object delegateInstance);

        Object $$_getDecoratedTypeBridgeTarget();

        Object $$_getDecoratedTypeBridgeDelegate();
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
        synchronized (DecoratorAwareProxyGenerator.class) {
            if (implementationResolved) {
                return implementationClass;
            }
            implementationClass = tryLoadWith(Thread.currentThread().getContextClassLoader());
            if (implementationClass == null) {
                implementationClass = tryLoadWith(DecoratorAwareProxyGenerator.class.getClassLoader());
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

    private static NotEnabledFeatureException decoratorNotEnabled(Class<?> beanClass) {
        String location = beanClass != null ? "class " + beanClass.getName() : "unknown bean type";
        String message =
                "@Decorator found at " + location + " but decorator support is not available.";
        return new NotEnabledFeatureException(message, ModulesEnum.DECORATORS);
    }
}
