package com.threeamigos.common.util.implementations.injection.decorators;

import com.threeamigos.common.util.implementations.injection.NotEnabledFeatureException;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXml;
import com.threeamigos.common.util.implementations.injection.knowledgebase.DecoratorInfo;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.modules.ModulesEnum;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Decorator;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * No-op {@link DecoratorSupport} used when syringe-decorators.jar is not on the classpath.
 *
 * <p>Resolution methods return empty/identity results, so the container can run without decorator
 * support when decorators are not used. Validation methods detect actual decorator usage and throw
 * {@link NotEnabledFeatureException} with guidance to add the missing module.
 */
public class NoOpDecoratorSupport implements DecoratorSupport {

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
    public List<DecoratorInfo> resolve(Set<Type> beanTypes, Set<Annotation> qualifiers) {
        return Collections.emptyList();
    }

    @Override
    public boolean hasDecorators(Set<Type> beanTypes, Set<Annotation> qualifiers) {
        return false;
    }

    @Override
    public Object applyDecoratorChain(Object target,
                                      List<DecoratorInfo> decorators,
                                      BeanManager beanManager,
                                      CreationalContext<?> creationalContext) {
        return target;
    }

    @Override
    public void destroyDecoratorChain(Object outermostInstance) {
        // no-op
    }

    @Override
    public void validateBeansXmlDecoratorConfiguration() {
        if (knowledgeBase == null) {
            return;
        }
        if (!knowledgeBase.getDecorators().isEmpty() || !knowledgeBase.getDecoratorInfos().isEmpty()) {
            Class<?> decoratorClass = !knowledgeBase.getDecorators().isEmpty()
                    ? knowledgeBase.getDecorators().iterator().next()
                    : knowledgeBase.getDecoratorInfos().iterator().next().getDecoratorClass();
            String location = decoratorClass != null
                    ? "class " + decoratorClass.getName()
                    : "decorator metadata";
            throw notEnabled("@Decorator", location);
        }
        for (BeansXml beansXml : knowledgeBase.getBeansXmlConfigurations()) {
            if (beansXml == null) {
                continue;
            }
            com.threeamigos.common.util.implementations.injection.beansxml.Decorators decorators =
                    beansXml.getDecorators();
            if (decorators != null && decorators.getClasses() != null
                    && !decorators.getClasses().isEmpty()) {
                String configured = decorators.getClasses().get(0);
                String location = configured != null && !configured.trim().isEmpty()
                        ? "beans.xml <decorators><class> " + configured
                        : "beans.xml <decorators><class>";
                throw notEnabled("beans.xml <decorators>", location);
            }
        }
    }

    @Override
    public void validateProgrammaticDecoratorConfiguration() {
        if (knowledgeBase == null) {
            return;
        }
        for (DecoratorInfo decoratorInfo : knowledgeBase.getDecoratorInfos()) {
            if (decoratorInfo != null && decoratorInfo.getDecoratorClass() != null) {
                throw notEnabled("programmatic decorator",
                        "class " + decoratorInfo.getDecoratorClass().getName());
            }
        }
        for (Bean<?> bean : knowledgeBase.getBeans()) {
            if (bean instanceof Decorator<?>) {
                Class<?> beanClass = bean.getBeanClass();
                String location = beanClass != null
                        ? "class " + beanClass.getName()
                        : "custom decorator bean";
                throw notEnabled("programmatic decorator", location);
            }
        }
    }

    @Override
    public void clear() {
        // no-op
    }

    private NotEnabledFeatureException notEnabled(String usage, String location) {
        return new NotEnabledFeatureException(
                usage + " found at " + location + " but decorator support is not available.",
                ModulesEnum.DECORATORS);
    }
}
