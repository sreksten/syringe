package com.threeamigos.common.util.implementations.injection.extensions;

import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;

import java.util.Collection;

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

    void registerRuntimeExtensionObserverMethods();

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

    <T> void fireEventToExtensions(T event);

    void clear();
}
