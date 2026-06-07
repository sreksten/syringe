package com.threeamigos.common.util.implementations.injection.extensions;

import com.threeamigos.common.util.implementations.injection.NotEnabledFeatureException;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.modules.ModulesEnum;
import com.threeamigos.common.util.implementations.injection.spi.Phase;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.AnnotatedField;
import jakarta.enterprise.inject.spi.AnnotatedMember;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanAttributes;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.InjectionTarget;
import jakarta.enterprise.inject.spi.Producer;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * No-op implementation of {@link ExtensionsManager} used when the syringe-extensions module
 * is absent from the classpath.
 * <p>
 * Throws {@link NotEnabledFeatureException} if a caller explicitly tries to register an
 * extension. All lifecycle fire methods are silent no-ops.
 */
public class NoOpExtensionsManager implements ExtensionsManager {

    private MessageHandler messageHandler;
    private KnowledgeBase knowledgeBase;

    @Override
    public void setMessageHandler(MessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }

    @Override
    public void setKnowledgeBase(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    @Override
    public void setBeanManager(BeanManager beanManager) {
        // no-op
    }

    @Override
    public void addExtension(String extensionClassName) {
        throw new NotEnabledFeatureException(
                "API call found at Syringe.addExtension(String) but extension support is not available.",
                ModulesEnum.EXTENSIONS);
    }

    @Override
    public void addExtension(Extension extension) {
        throw new NotEnabledFeatureException(
                "API call found at Syringe.addExtension(Extension) but extension support is not available.",
                ModulesEnum.EXTENSIONS);
    }

    @Override
    public void loadExtensions() {
        // no-op: no extensions registered, nothing to load
    }

    @Override
    public Collection<Extension> getExtensions() {
        return Collections.emptyList();
    }

    @Override
    public Collection<String> getExtensionClassNames() {
        return Collections.emptyList();
    }

    @Override
    public void fireBeforeBeanDiscovery() {
        // no-op
    }

    @Override
    public void fireAfterTypeDiscovery() {
        // no-op
    }

    @Override
    public <T> void fireEventToExtensions(T event) {
        // no-op
    }

    @Override
    public ProcessAnnotatedTypeResult processAnnotatedType(AnnotatedType<?> annotatedType) {
        return new ProcessAnnotatedTypeResult(false, annotatedType);
    }

    @Override
    public InjectionPoint processInjectionPoint(InjectionPoint injectionPoint) {
        return injectionPoint;
    }

    @Override
    public <T> InjectionTarget<T> processInjectionTarget(AnnotatedType<T> annotatedType, InjectionTarget<T> injectionTarget) {
        return injectionTarget;
    }

    @Override
    public <T> ProcessBeanAttributesResult<T> processBeanAttributes(Annotated annotated, BeanAttributes<T> beanAttributes) {
        return new ProcessBeanAttributesResult<T>(false, false, beanAttributes);
    }

    @Override
    public void processSyntheticBean(Bean<?> bean, Extension sourceExtension) {
        // no-op
    }

    @Override
    public void processManagedBean(Bean<?> bean, AnnotatedType<?> annotatedType, BeanManager beanManager) {
        // no-op
    }

    @Override
    public Producer<?> processProducer(Phase phase, AnnotatedMember<?> annotatedMember, Producer<?> producer) {
        return producer;
    }

    @Override
    public Producer<?> processProducerMethod(Bean<?> bean,
                                             AnnotatedMethod<?> annotatedMethod,
                                             Producer<?> producer,
                                             AnnotatedParameter<?> disposedParameter) {
        return producer;
    }

    @Override
    public Producer<?> processProducerField(Bean<?> bean,
                                            AnnotatedField<?> annotatedField,
                                            Producer<?> producer,
                                            AnnotatedParameter<?> disposedParameter) {
        return producer;
    }

    @Override
    public void fireAfterBeanDiscovery(Map<Class<? extends Annotation>, Context> customContextsToRegister) {
        // no-op
    }

    @Override
    public void fireAfterDeploymentValidation() {
        // no-op
    }

    @Override
    public void fireBeforeShutdown() {
        // no-op
    }

    @Override
    public void processRegisteredAnnotatedTypes() {
        // no-op
    }

    @Override
    public void clear() {
        // no-op
    }
}
