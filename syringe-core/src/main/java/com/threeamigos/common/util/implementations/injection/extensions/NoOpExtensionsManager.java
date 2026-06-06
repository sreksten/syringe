package com.threeamigos.common.util.implementations.injection.extensions;

import com.threeamigos.common.util.implementations.injection.NotEnabledFeatureException;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;

import java.util.Collection;
import java.util.Collections;

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
                "Extension support is not available. Add syringe-extensions to your classpath.");
    }

    @Override
    public void addExtension(Extension extension) {
        throw new NotEnabledFeatureException(
                "Extension support is not available. Add syringe-extensions to your classpath.");
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
    public void registerRuntimeExtensionObserverMethods() {
        // no-op
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
    public void processRegisteredAnnotatedTypes() {
        // no-op
    }

    @Override
    public void clear() {
        // no-op
    }
}
