package com.threeamigos.common.util.implementations.injection.decorators;

import com.threeamigos.common.util.implementations.injection.knowledgebase.DecoratorInfo;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.BeanManager;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;

/**
 * Service provider interface for decorator support.
 *
 * <p>Discovered via {@link java.util.ServiceLoader}. If syringe-decorators.jar is on the classpath,
 * {@code DecoratorSupportImpl} is loaded; otherwise {@link NoOpDecoratorSupport} is used.
 */
public interface DecoratorSupport {

    void setKnowledgeBase(KnowledgeBase knowledgeBase);

    void setMessageHandler(MessageHandler messageHandler);

    List<DecoratorInfo> resolve(Set<Type> beanTypes, Set<Annotation> qualifiers);

    boolean hasDecorators(Set<Type> beanTypes, Set<Annotation> qualifiers);

    Object applyDecoratorChain(Object target,
                               List<DecoratorInfo> decorators,
                               BeanManager beanManager,
                               CreationalContext<?> creationalContext);

    void destroyDecoratorChain(Object outermostInstance);

    /**
     * Validates beans.xml {@code <decorators>} entries against the discovered beans.
     * Records any errors into the KnowledgeBase via {@code addDefinitionError}.
     */
    void validateBeansXmlDecoratorConfiguration();

    /**
     * Validates programmatically registered decorators (via extensions or the SPI).
     * Records any errors into the KnowledgeBase via {@code addDefinitionError}.
     */
    void validateProgrammaticDecoratorConfiguration();

    /**
     * Clears any per-instance caches held by this support object (e.g., proxy class cache).
     */
    void clear();
}
