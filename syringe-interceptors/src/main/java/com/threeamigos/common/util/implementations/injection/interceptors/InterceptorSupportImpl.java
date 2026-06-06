package com.threeamigos.common.util.implementations.injection.interceptors;

import com.threeamigos.common.util.implementations.injection.beansxml.BeansXml;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.Interceptor;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.hasInterceptorAnnotation;

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

    @Override
    public InterceptorResolver getResolver() {
        if (resolver == null) {
            resolver = new InterceptorResolver(knowledgeBase);
        }
        return resolver;
    }

    @Override
    public InterceptorAwareProxyGenerator getProxyGenerator() {
        if (proxyGenerator == null) {
            proxyGenerator = new InterceptorAwareProxyGenerator();
        }
        return proxyGenerator;
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
