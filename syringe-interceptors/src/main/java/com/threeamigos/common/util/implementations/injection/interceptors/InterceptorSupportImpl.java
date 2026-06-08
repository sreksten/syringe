package com.threeamigos.common.util.implementations.injection.interceptors;

import com.threeamigos.common.util.implementations.injection.beansxml.BeansXml;
import com.threeamigos.common.util.implementations.injection.knowledgebase.InterceptorInfo;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InterceptionFactory;
import jakarta.enterprise.inject.spi.InterceptionType;
import jakarta.enterprise.inject.spi.Interceptor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.hasInterceptorAnnotation;

/**
 * Full {@link InterceptorSupport} implementation, active when syringe-interceptors is on the
 * classpath. Creates and owns the {@link InterceptorResolver} and
 * {@link InterceptorAwareProxyGenerator} for a container instance, and validates
 * {@code beans.xml} interceptor declarations.
 */
public class InterceptorSupportImpl implements InterceptorSupport {

    private KnowledgeBase knowledgeBase;
    private InterceptorResolver resolver;
    private InterceptorAwareProxyGenerator proxyGenerator;

    @Override
    public void setKnowledgeBase(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    @Override
    public void setMessageHandler(MessageHandler messageHandler) {
        // not needed currently
    }

    private InterceptorResolver getResolver() {
        if (resolver == null) {
            resolver = new InterceptorResolver(knowledgeBase);
        }
        return resolver;
    }

    private InterceptorAwareProxyGenerator getProxyGenerator() {
        if (proxyGenerator == null) {
            proxyGenerator = new InterceptorAwareProxyGenerator();
        }
        return proxyGenerator;
    }

    @Override
    public List<InterceptorInfo> resolve(Class<?> targetClass,
                                         Method method,
                                         AnnotatedType<?> annotatedTypeOverride,
                                         InterceptionType interceptionType) {
        return getResolver().resolve(targetClass, method, annotatedTypeOverride, interceptionType);
    }

    @Override
    public List<InterceptorInfo> resolveForConstructor(Class<?> targetClass,
                                                       Constructor<?> constructor,
                                                       AnnotatedType<?> annotatedTypeOverride,
                                                       InterceptionType interceptionType) {
        return getResolver().resolveForConstructor(targetClass, constructor, annotatedTypeOverride, interceptionType);
    }

    @Override
    public Set<Annotation> resolveBindings(Class<?> targetClass,
                                           Method method,
                                           AnnotatedType<?> annotatedTypeOverride) {
        return getResolver().resolveBindings(targetClass, method, annotatedTypeOverride);
    }

    @Override
    public Set<Annotation> resolveBindingsForConstructor(Class<?> targetClass,
                                                         Constructor<?> constructor,
                                                         AnnotatedType<?> annotatedTypeOverride) {
        return getResolver().resolveBindingsForConstructor(targetClass, constructor, annotatedTypeOverride);
    }

    @Override
    public InterceptorChainModel.Builder newChainBuilder() {
        return InterceptorChain.builder();
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Object applyInterceptorProxy(Object target,
                                        Bean<?> bean,
                                        Map<Method, ? extends InterceptorChainModel> chains) {
        if (target == null || bean == null || chains == null || chains.isEmpty()) {
            return target;
        }
        return getProxyGenerator().createProxy((Bean) bean, target, (Map) chains);
    }

    @Override
    public Object invokeLifecycle(Object target, Object chain, LifecycleTargetInvocation targetInvocation) throws Exception {
        if (!(chain instanceof InterceptorChain)) {
            return targetInvocation != null ? targetInvocation.proceed() : null;
        }
        InvocationContextImpl.TargetInvocation invocation = ctx ->
                targetInvocation != null ? targetInvocation.proceed() : null;
        InvocationContextImpl invocationContext = new InvocationContextImpl(
                target,
                (InterceptorChain) chain,
                invocation
        );
        return invocationContext.proceed();
    }

    @Override
    public <T> InterceptionFactory<T> createInterceptionFactory(CreationalContext<T> ctx,
                                                                Class<T> clazz,
                                                                BeanManager beanManager) {
        return new InterceptionFactoryImpl<>(clazz, ctx, beanManager, getProxyGenerator());
    }

    @Override
    public void clearTargetAroundInvokeCache() {
        InterceptorAwareProxyGenerator.clearTargetAroundInvokeCache();
    }

    @Override
    public void clearTargetAroundInvokeCacheForClassLoader(ClassLoader classLoader) {
        InterceptorAwareProxyGenerator.clearTargetAroundInvokeCacheForClassLoader(classLoader);
    }

    @Override
    public void validateBeansXmlInterceptorConfiguration() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = InterceptorSupportImpl.class.getClassLoader();
        }

        for (BeansXml beansXml : knowledgeBase.getBeansXmlConfigurations()) {
            if (beansXml == null) {
                continue;
            }
            com.threeamigos.common.util.implementations.injection.beansxml.Interceptors interceptors =
                    beansXml.getInterceptors();
            if (interceptors == null) {
                continue;
            }

            List<String> classes = interceptors.getClasses() != null
                    ? interceptors.getClasses()
                    : Collections.emptyList();

            validateNoDuplicateEntries(classes);

            for (String className : classes) {
                validateInterceptorClassEntry(className, classLoader);
            }
        }
    }

    private void validateNoDuplicateEntries(List<String> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (String entry : entries) {
            if (entry == null) {
                continue;
            }
            if (!seen.add(entry)) {
                duplicates.add(entry);
            }
        }
        if (!duplicates.isEmpty()) {
            knowledgeBase.addDefinitionError(
                    "beans.xml <interceptors><class> contains duplicate entries: " + duplicates);
        }
    }

    private void validateInterceptorClassEntry(String className, ClassLoader classLoader) {
        if (className == null || className.trim().isEmpty()) {
            knowledgeBase.addDefinitionError("beans.xml <interceptors><class> must not be empty");
            return;
        }

        Class<?> clazz;
        try {
            clazz = Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException e) {
            knowledgeBase.addDefinitionError("beans.xml interceptor class not found: " + className);
            return;
        }

        if (hasInterceptorAnnotation(clazz)
                || Interceptor.class.isAssignableFrom(clazz)
                || hasInterceptorBeanWithBeanClassName(className)) {
            return;
        }

        knowledgeBase.addDefinitionError(
                "beans.xml interceptor class '" + className + "' is not an interceptor class");
    }

    private boolean hasInterceptorBeanWithBeanClassName(String className) {
        for (Bean<?> bean : knowledgeBase.getBeans()) {
            if (!(bean instanceof Interceptor<?>)) {
                continue;
            }
            Class<?> beanClass = bean.getBeanClass();
            if (beanClass != null && className.equals(beanClass.getName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void clear() {
        if (proxyGenerator != null) {
            proxyGenerator.clearCache();
            proxyGenerator = null;
        }
        resolver = null;
    }
}
