package com.threeamigos.common.util.implementations.injection.interceptors;

import com.threeamigos.common.util.implementations.injection.NotEnabledFeatureException;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXml;
import com.threeamigos.common.util.implementations.injection.builtinbeans.ActivateRequestContextInterceptor;
import com.threeamigos.common.util.implementations.injection.knowledgebase.InterceptorInfo;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.modules.ModulesEnum;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * No-op {@link InterceptorSupport} used when syringe-interceptors.jar is not on the classpath.
 *
 * <p>Returns {@code null} for the resolver and proxy generator (safe because BeanImpl handles
 * {@code null} gracefully when no interceptors are registered). Throws
 * {@link NotEnabledFeatureException} during validation if interceptors are actually required.
 */
public class NoOpInterceptorSupport implements InterceptorSupport {

    private KnowledgeBase knowledgeBase;

    @Override
    public void setKnowledgeBase(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    @Override
    public void setMessageHandler(MessageHandler messageHandler) {
        // not needed
    }

    @Override
    public List<InterceptorInfo> resolve(Class<?> targetClass,
                                         Method method,
                                         AnnotatedType<?> annotatedTypeOverride,
                                         InterceptionType interceptionType) {
        return Collections.emptyList();
    }

    @Override
    public List<InterceptorInfo> resolveForConstructor(Class<?> targetClass,
                                                       Constructor<?> constructor,
                                                       AnnotatedType<?> annotatedTypeOverride,
                                                       InterceptionType interceptionType) {
        return Collections.emptyList();
    }

    @Override
    public Set<Annotation> resolveBindings(Class<?> targetClass,
                                           Method method,
                                           AnnotatedType<?> annotatedTypeOverride) {
        return Collections.emptySet();
    }

    @Override
    public Set<Annotation> resolveBindingsForConstructor(Class<?> targetClass,
                                                         Constructor<?> constructor,
                                                         AnnotatedType<?> annotatedTypeOverride) {
        return Collections.emptySet();
    }

    @Override
    public InterceptorChainModel.Builder newChainBuilder() {
        return NoOpChainBuilder.INSTANCE;
    }

    @Override
    public Object applyInterceptorProxy(Object target,
                                        Bean<?> bean,
                                        Map<Method, ? extends InterceptorChainModel> chains) {
        return target;
    }

    @Override
    public Object invokeLifecycle(Object target, Object chain, LifecycleTargetInvocation targetInvocation) throws Exception {
        return targetInvocation != null ? targetInvocation.proceed() : null;
    }

    @Override
    public <T> InterceptionFactory<T> createInterceptionFactory(CreationalContext<T> ctx,
                                                                Class<T> clazz,
                                                                BeanManager beanManager) {
        throw new NotEnabledFeatureException(
                "API call found at BeanManager.createInterceptionFactory(CreationalContext, Class) but interceptor support is not available.",
                ModulesEnum.INTERCEPTORS);
    }

    @Override
    public void clearTargetAroundInvokeCache() {
        // no-op
    }

    @Override
    public void clearTargetAroundInvokeCacheForClassLoader(ClassLoader classLoader) {
        // no-op
    }

    @Override
    public void validateBeansXmlInterceptorConfiguration() {
        Class<?> interceptorClass = firstUnsupportedInterceptorClass();
        if (interceptorClass != null) {
            throw notEnabled("@Interceptor", "class " + interceptorClass.getName());
        }
        for (BeansXml beansXml : knowledgeBase.getBeansXmlConfigurations()) {
            if (beansXml == null) {
                continue;
            }
            com.threeamigos.common.util.implementations.injection.beansxml.Interceptors interceptors =
                    beansXml.getInterceptors();
            if (interceptors != null && interceptors.getClasses() != null
                    && !interceptors.getClasses().isEmpty()) {
                String configured = interceptors.getClasses().get(0);
                String location = configured != null && !configured.trim().isEmpty()
                        ? "beans.xml <interceptors><class> " + configured
                        : "beans.xml <interceptors><class>";
                throw notEnabled("beans.xml <interceptors>", location);
            }
        }
    }

    @Override
    public void clear() {
        // no-op
    }

    private NotEnabledFeatureException notEnabled(String usage, String location) {
        return new NotEnabledFeatureException(
                usage + " found at " + location + " but interceptor support is not available.",
                ModulesEnum.INTERCEPTORS);
    }

    private Class<?> firstUnsupportedInterceptorClass() {
        for (Class<?> interceptorClass : knowledgeBase.getInterceptors()) {
            if (isNotIgnorableBuiltInInterceptor(interceptorClass)) {
                return interceptorClass;
            }
        }
        for (InterceptorInfo interceptorInfo : knowledgeBase.getInterceptorInfos()) {
            Class<?> interceptorClass = interceptorInfo != null
                    ? interceptorInfo.getInterceptorClass()
                    : null;
            if (interceptorClass != null && isNotIgnorableBuiltInInterceptor(interceptorClass)) {
                return interceptorClass;
            }
        }
        return null;
    }

    private boolean isNotIgnorableBuiltInInterceptor(Class<?> interceptorClass) {
        if (interceptorClass == null) {
            return true;
        }
        return !ActivateRequestContextInterceptor.class.getName().equals(interceptorClass.getName());
    }

    private static final class NoOpChainBuilder implements InterceptorChainModel.Builder {
        private static final NoOpChainBuilder INSTANCE = new NoOpChainBuilder();
        private static final InterceptorChainModel EMPTY_CHAIN = new NoOpChain();

        @Override
        public InterceptorChainModel.Builder addInterceptor(Object interceptorInstance, Method interceptorMethod) {
            return this;
        }

        @Override
        public InterceptorChainModel.Builder addInvocation(InterceptorChainModel.InterceptorInvocation invocation) {
            return this;
        }

        @Override
        public InterceptorChainModel.Builder withInterceptorBindings(Set<Annotation> interceptorBindings) {
            return this;
        }

        @Override
        public InterceptorChainModel build() {
            return EMPTY_CHAIN;
        }
    }

    private static final class NoOpChain implements InterceptorChainModel {

        @Override
        public List<InterceptorInvocation> getInvocations() {
            return Collections.emptyList();
        }

        @Override
        public Set<Annotation> getInterceptorBindings() {
            return Collections.emptySet();
        }

        @Override
        public Object invoke(Object target, Method method, Object[] args) {
            return null;
        }

        @Override
        public Object invoke(Object target, Constructor<?> constructor, Object[] args) {
            return null;
        }

        @Override
        public void invokeLifecycleChain(Object target, List<Method> lifecycleCallbacks) {
            // no-op
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public int size() {
            return 0;
        }
    }
}
