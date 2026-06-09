package com.threeamigos.common.util.implementations.injection.interceptors;

import com.threeamigos.common.util.implementations.injection.knowledgebase.InterceptorInfo;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InterceptionFactory;
import jakarta.enterprise.inject.spi.InterceptionType;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service provider interface for interceptor support.
 *
 * <p>Discovered via {@link java.util.ServiceLoader}. If syringe-interceptors.jar is on the classpath,
 * {@code InterceptorSupportImpl} is loaded; otherwise {@link NoOpInterceptorSupport} is used.
 */
public interface InterceptorSupport {

    void setKnowledgeBase(KnowledgeBase knowledgeBase);

    void setMessageHandler(MessageHandler messageHandler);

    List<InterceptorInfo> resolve(Class<?> targetClass,
                                  Method method,
                                  AnnotatedType<?> annotatedTypeOverride,
                                  InterceptionType interceptionType);

    List<InterceptorInfo> resolveForConstructor(Class<?> targetClass,
                                                Constructor<?> constructor,
                                                AnnotatedType<?> annotatedTypeOverride,
                                                InterceptionType interceptionType);

    Set<Annotation> resolveBindings(Class<?> targetClass,
                                    Method method,
                                    AnnotatedType<?> annotatedTypeOverride);

    Set<Annotation> resolveBindingsForConstructor(Class<?> targetClass,
                                                  Constructor<?> constructor,
                                                  AnnotatedType<?> annotatedTypeOverride);

    InterceptorChainModel.Builder newChainBuilder();

    Object applyInterceptorProxy(Object target, Bean<?> bean, Map<Method, ? extends InterceptorChainModel> chains);

    Object invokeLifecycle(Object target, Object chain, LifecycleTargetInvocation targetInvocation) throws Exception;

    <T> InterceptionFactory<T> createInterceptionFactory(CreationalContext<T> ctx,
                                                         Class<T> clazz,
                                                         BeanManager beanManager);

    void clearTargetAroundInvokeCache();

    void clearTargetAroundInvokeCacheForClassLoader(ClassLoader classLoader);

    /**
     * Validates beans.xml {@code <interceptors>} entries against the discovered beans.
     * Records any errors into the KnowledgeBase via {@code addDefinitionError}.
     */
    void validateBeansXmlInterceptorConfiguration();

    /**
     * Clears any per-instance caches held by this support object (e.g., proxy class cache).
     */
    void clear();

    interface LifecycleTargetInvocation {
        Object proceed() throws Exception;
    }
}
