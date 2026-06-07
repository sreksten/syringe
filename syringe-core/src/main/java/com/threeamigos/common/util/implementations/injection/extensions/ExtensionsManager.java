package com.threeamigos.common.util.implementations.injection.extensions;

import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
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
import java.util.Map;

/**
 *
 * @author Stefano Reksten
 */
public interface ExtensionsManager {

    void setMessageHandler(MessageHandler messageHandler);

    void setKnowledgeBase(KnowledgeBase knowledgeBase);

    void setBeanManager(BeanManager beanManager);

    void addExtension(String extensionClassName);

    void addExtension(Extension extension);

    void loadExtensions();

    Collection<Extension> getExtensions();

    Collection<String> getExtensionClassNames();

    /**
     * Fires BeforeBeanDiscovery event to all extensions.
     *
     * <p>Extensions can use this event to:
     * <ul>
     *   <li>Add new qualifiers via addQualifier()</li>
     *   <li>Add new scopes via addScope()</li>
     *   <li>Add new stereotypes via addStereotype()</li>
     *   <li>Add interceptor bindings via addInterceptorBinding()</li>
     *   <li>Add annotated types programmatically via addAnnotatedType()</li>
     * </ul>
     */
    void fireBeforeBeanDiscovery();

    void fireAfterTypeDiscovery();

    <T> void fireEventToExtensions(T event);

    ProcessAnnotatedTypeResult processAnnotatedType(AnnotatedType<?> annotatedType);

    InjectionPoint processInjectionPoint(InjectionPoint injectionPoint);

    <T> InjectionTarget<T> processInjectionTarget(AnnotatedType<T> annotatedType, InjectionTarget<T> injectionTarget);

    <T> ProcessBeanAttributesResult<T> processBeanAttributes(Annotated annotated, BeanAttributes<T> beanAttributes);

    void processSyntheticBean(Bean<?> bean, Extension sourceExtension);

    void processManagedBean(Bean<?> bean, AnnotatedType<?> annotatedType, BeanManager beanManager);

    Producer<?> processProducer(Phase phase, AnnotatedMember<?> annotatedMember, Producer<?> producer);

    Producer<?> processProducerMethod(Bean<?> bean,
                                      AnnotatedMethod<?> annotatedMethod,
                                      Producer<?> producer,
                                      AnnotatedParameter<?> disposedParameter);

    Producer<?> processProducerField(Bean<?> bean,
                                     AnnotatedField<?> annotatedField,
                                     Producer<?> producer,
                                     AnnotatedParameter<?> disposedParameter);

    void fireAfterBeanDiscovery(Map<Class<? extends Annotation>, Context> customContextsToRegister);

    void fireAfterDeploymentValidation();

    void fireBeforeShutdown();
    /**
     * Processes AnnotatedTypes that were registered programmatically via BeforeBeanDiscovery.addAnnotatedType().
     *
     * <p>These synthetic types are added to the KnowledgeBase classes collection, so they will be
     * validated and registered as beans during the normal bean processing phase.
     */
    void processRegisteredAnnotatedTypes();

    void clear();

    final class ProcessAnnotatedTypeResult {
        private final boolean vetoed;
        private final AnnotatedType<?> annotatedType;

        public ProcessAnnotatedTypeResult(boolean vetoed, AnnotatedType<?> annotatedType) {
            this.vetoed = vetoed;
            this.annotatedType = annotatedType;
        }

        public boolean isVetoed() {
            return vetoed;
        }

        public AnnotatedType<?> getAnnotatedType() {
            return annotatedType;
        }
    }

    final class ProcessBeanAttributesResult<T> {
        private final boolean vetoed;
        private final boolean ignoreFinalMethods;
        private final BeanAttributes<T> beanAttributes;

        public ProcessBeanAttributesResult(boolean vetoed,
                                           boolean ignoreFinalMethods,
                                           BeanAttributes<T> beanAttributes) {
            this.vetoed = vetoed;
            this.ignoreFinalMethods = ignoreFinalMethods;
            this.beanAttributes = beanAttributes;
        }

        public boolean isVetoed() {
            return vetoed;
        }

        public boolean isIgnoreFinalMethods() {
            return ignoreFinalMethods;
        }

        public BeanAttributes<T> getBeanAttributes() {
            return beanAttributes;
        }
    }
}
