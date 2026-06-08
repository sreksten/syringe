package com.threeamigos.common.util.implementations.injection.decorators;

import com.threeamigos.common.util.implementations.injection.beansxml.BeansXml;
import com.threeamigos.common.util.implementations.injection.knowledgebase.DecoratorInfo;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Decorator;
import jakarta.enterprise.inject.spi.InjectionPoint;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.hasDecoratorAnnotation;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.hasDelegateAnnotation;
import static com.threeamigos.common.util.implementations.injection.util.TypesHelper.extractRawClass;

/**
 * Full {@link DecoratorSupport} implementation, active when syringe-decorators is on the
 * classpath. Creates and owns the {@link DecoratorResolver} and
 * {@link DecoratorAwareProxyGenerator} for a container instance, and validates
 * {@code beans.xml} and programmatic decorator declarations.
 */
public class DecoratorSupportImpl implements DecoratorSupport {

    private KnowledgeBase knowledgeBase;
    private DecoratorResolver resolver;
    private DecoratorAwareProxyGenerator proxyGenerator;

    @Override
    public void setKnowledgeBase(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    @Override
    public void setMessageHandler(MessageHandler messageHandler) {
        // not needed currently
    }

    private DecoratorResolver getResolver() {
        if (resolver == null) {
            resolver = new DecoratorResolver(knowledgeBase);
        }
        return resolver;
    }

    private DecoratorAwareProxyGenerator getProxyGenerator() {
        if (proxyGenerator == null) {
            proxyGenerator = new DecoratorAwareProxyGenerator();
        }
        return proxyGenerator;
    }

    @Override
    public List<DecoratorInfo> resolve(Set<Type> beanTypes, Set<Annotation> qualifiers) {
        if (beanTypes == null || beanTypes.isEmpty()) {
            return Collections.emptyList();
        }
        return getResolver().resolve(beanTypes, qualifiers != null ? qualifiers : Collections.<Annotation>emptySet());
    }

    @Override
    public boolean hasDecorators(Set<Type> beanTypes, Set<Annotation> qualifiers) {
        if (beanTypes == null || beanTypes.isEmpty()) {
            return false;
        }
        return getResolver().hasDecorators(beanTypes, qualifiers != null ? qualifiers : Collections.<Annotation>emptySet());
    }

    @Override
    public Object applyDecoratorChain(Object target,
                                      List<DecoratorInfo> decorators,
                                      BeanManager beanManager,
                                      CreationalContext<?> creationalContext) {
        if (target == null || decorators == null || decorators.isEmpty() || beanManager == null || creationalContext == null) {
            return target;
        }
        return getProxyGenerator().createDecoratorChain(
                target,
                decorators,
                beanManager,
                creationalContext
        );
    }

    @Override
    public void destroyDecoratorChain(Object outermostInstance) {
        if (outermostInstance == null) {
            return;
        }
        getProxyGenerator().destroyDecoratorChain(outermostInstance);
    }

    @Override
    public void validateBeansXmlDecoratorConfiguration() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = DecoratorSupportImpl.class.getClassLoader();
        }

        for (BeansXml beansXml : knowledgeBase.getBeansXmlConfigurations()) {
            if (beansXml == null) {
                continue;
            }
            com.threeamigos.common.util.implementations.injection.beansxml.Decorators decorators =
                    beansXml.getDecorators();
            if (decorators == null) {
                continue;
            }

            List<String> classes = decorators.getClasses() != null
                    ? decorators.getClasses()
                    : Collections.emptyList();

            validateNoDuplicateEntries(classes);

            for (String className : classes) {
                validateDecoratorClassEntry(className, classLoader);
            }
        }
    }

    @Override
    public void validateProgrammaticDecoratorConfiguration() {
        Set<Class<?>> validatedDecoratorClasses = new HashSet<>();
        for (DecoratorInfo decoratorInfo : knowledgeBase.getDecoratorInfos()) {
            if (decoratorInfo != null && decoratorInfo.getDecoratorClass() != null) {
                validatedDecoratorClasses.add(decoratorInfo.getDecoratorClass());
                validateDecoratorInfoDelegateInjectionPoint(decoratorInfo);
            }
        }

        for (Bean<?> bean : knowledgeBase.getBeans()) {
            if (!(bean instanceof Decorator<?>)) {
                continue;
            }
            Decorator<?> decorator = (Decorator<?>) bean;
            Class<?> decoratorClass = decorator.getBeanClass();
            if (decoratorClass != null && validatedDecoratorClasses.contains(decoratorClass)) {
                continue;
            }
            validateProgrammaticDecoratorDecoratedTypes(decorator);
            validateProgrammaticDecoratorDelegateInjectionPoints(decorator);
        }
    }

    @Override
    public void clear() {
        if (proxyGenerator != null) {
            proxyGenerator.clearCache();
            proxyGenerator = null;
        }
        resolver = null;
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
                    "beans.xml <decorators><class> contains duplicate entries: " + duplicates);
        }
    }

    private void validateDecoratorClassEntry(String className, ClassLoader classLoader) {
        if (className == null || className.trim().isEmpty()) {
            knowledgeBase.addDefinitionError("beans.xml <decorators><class> must not be empty");
            return;
        }

        Class<?> clazz;
        try {
            clazz = Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException e) {
            knowledgeBase.addDefinitionError("beans.xml decorator class not found: " + className);
            return;
        }

        if (knowledgeBase.isTypeVetoed(clazz)) {
            return;
        }

        if (hasDecoratorAnnotation(clazz)
                || Decorator.class.isAssignableFrom(clazz)
                || declaresDelegateInjectionPoint(clazz)) {
            return;
        }

        knowledgeBase.addDefinitionError(
                "beans.xml decorator class '" + className + "' is not a decorator class");
    }

    private boolean declaresDelegateInjectionPoint(Class<?> clazz) {
        if (clazz == null) {
            return false;
        }

        for (Field field : clazz.getDeclaredFields()) {
            for (Annotation annotation : field.getAnnotations()) {
                if (hasDelegateAnnotation(annotation.annotationType())) {
                    return true;
                }
            }
        }

        for (Method method : clazz.getDeclaredMethods()) {
            for (Parameter parameter : method.getParameters()) {
                for (Annotation annotation : parameter.getAnnotations()) {
                    if (hasDelegateAnnotation(annotation.annotationType())) {
                        return true;
                    }
                }
            }
        }

        for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            for (Parameter parameter : constructor.getParameters()) {
                for (Annotation annotation : parameter.getAnnotations()) {
                    if (hasDelegateAnnotation(annotation.annotationType())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private void validateDecoratorInfoDelegateInjectionPoint(DecoratorInfo decoratorInfo) {
        if (decoratorInfo == null || decoratorInfo.getDecoratorClass() == null) {
            return;
        }
        InjectionPoint delegateInjectionPoint = decoratorInfo.getDelegateInjectionPoint();
        if (delegateInjectionPoint != null && delegateInjectionPoint.isDelegate()) {
            return;
        }
        knowledgeBase.addDefinitionError(
                decoratorInfo.getDecoratorClass().getName() +
                        ": Decorator must have exactly one @Delegate injection point (found 0). " +
                        "Add @Inject @Delegate to a field, method parameter, or constructor parameter.");
    }

    private void validateProgrammaticDecoratorDecoratedTypes(Decorator<?> decorator) {
        if (decorator == null) {
            return;
        }
        Set<Type> decoratedTypes = decorator.getDecoratedTypes();
        if (hasAtLeastOneValidDecoratedType(decoratedTypes)) {
            return;
        }
        Class<?> beanClass = decorator.getBeanClass();
        String decoratorName = beanClass != null ? beanClass.getName() : decorator.getClass().getName();
        knowledgeBase.addDefinitionError(
                "Custom decorator '" + decoratorName +
                        "' must declare at least one decorated type (interface bean type excluding java.io.Serializable)");
    }

    private boolean hasAtLeastOneValidDecoratedType(Set<Type> decoratedTypes) {
        if (decoratedTypes == null || decoratedTypes.isEmpty()) {
            return false;
        }
        for (Type decoratedType : decoratedTypes) {
            Class<?> rawType = extractRawClass(decoratedType);
            if (rawType == null) {
                continue;
            }
            if (Object.class.equals(rawType)
                    || java.io.Serializable.class.equals(rawType)
                    || Decorator.class.equals(rawType)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private void validateProgrammaticDecoratorDelegateInjectionPoints(Decorator<?> decorator) {
        Set<InjectionPoint> injectionPoints = decorator.getInjectionPoints();
        if (injectionPoints == null || injectionPoints.isEmpty()) {
            return;
        }
        Class<?> beanClass = decorator.getBeanClass();
        String decoratorName = beanClass != null ? beanClass.getName() : decorator.getClass().getName();

        int delegateInjectionPoints = 0;
        for (InjectionPoint injectionPoint : injectionPoints) {
            if (injectionPoint != null && injectionPoint.isDelegate()) {
                delegateInjectionPoints++;
            }
        }

        if (delegateInjectionPoints == 0) {
            knowledgeBase.addDefinitionError(
                    "Custom decorator '" + decoratorName +
                            "' must declare exactly one delegate injection point but declares none");
        } else if (delegateInjectionPoints > 1) {
            knowledgeBase.addDefinitionError(
                    "Custom decorator '" + decoratorName +
                            "' must declare exactly one delegate injection point but declares " + delegateInjectionPoints);
        }
    }
}
