package com.threeamigos.common.util.implementations.injection.bce;

import com.threeamigos.common.util.implementations.injection.extensions.ExtensionsManager;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.inject.spi.BeanManager;

/**
 *
 * @author Stefano Reksten
 */
public interface BuildCompatibleExtensionsManager {

    void setMessageHandler(MessageHandler messageHandler);

    void setKnowledgeBase(KnowledgeBase knowledgeBase);

    void setBeanManager(BeanManager beanManager);

    void setExtensionsManager(ExtensionsManager extensionsManager);

    void addBuildCompatibleExtension(String extensionClassName);

    void loadBuildCompatibleExtensions();

    void fireBuildCompatibleExtensionPhase(BceSupportedPhase phase);

    void clear();
}
