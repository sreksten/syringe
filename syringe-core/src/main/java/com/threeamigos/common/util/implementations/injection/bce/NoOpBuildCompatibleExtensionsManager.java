package com.threeamigos.common.util.implementations.injection.bce;

import com.threeamigos.common.util.implementations.injection.NotEnabledFeatureException;
import com.threeamigos.common.util.implementations.injection.extensions.ExtensionsManager;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.modules.ModulesEnum;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.inject.spi.BeanManager;

/**
 * No-op implementation of {@link BuildCompatibleExtensionsManager} used when the syringe-bce
 * module is absent from the classpath.
 * <p>
 * Throws {@link NotEnabledFeatureException} if a caller explicitly tries to register a
 * build-compatible extension. All lifecycle phase methods are silent no-ops.
 */
public class NoOpBuildCompatibleExtensionsManager implements BuildCompatibleExtensionsManager {

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
    public void setExtensionsManager(ExtensionsManager extensionsManager) {
        // no-op
    }

    @Override
    public void addBuildCompatibleExtension(String extensionClassName) {
        throw new NotEnabledFeatureException(
                "API call found at Syringe.addBuildCompatibleExtension(String) but build-compatible extension support is not available.",
                ModulesEnum.BCE);
    }

    @Override
    public void loadBuildCompatibleExtensions() {
        // no-op: no BCE registered, nothing to load
    }

    @Override
    public void fireBuildCompatibleExtensionPhase(BceSupportedPhase phase) {
        // no-op
    }

    @Override
    public void clear() {
        // no-op
    }
}
