package com.threeamigos.common.util.implementations.injection.bce;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.extensions.ExtensionsManager;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.SkipIfPortableExtensionPresent;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationExtractors.getSkipIfPortableExtensionPresentAnnotation;

/**
 *
 * @author Stefano Reksten
 */
public class BuildCompatibleExtensionsManagerImpl implements BuildCompatibleExtensionsManager {

    private MessageHandler messageHandler;
    private KnowledgeBase knowledgeBase;
    private ExtensionsManager extensionsManager;
    private BeanManager beanManager;

    /**
     * Set of build-compatible extension class names to be loaded.
     */
    private final Set<String> buildCompatibleExtensionClassNames = new HashSet<>();

    /**
     * Loaded build compatible extension instances.
     */
    private final List<BuildCompatibleExtension> buildCompatibleExtensions = new ArrayList<>();

    /**
     * BCE phase runner.
     */
    private BuildCompatibleExtensionRunner buildCompatibleExtensionRunner;

    private final BceInvokerRegistry bceInvokerRegistry = new BceInvokerRegistry();

    public BuildCompatibleExtensionsManagerImpl() {

    }

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
        this.beanManager = beanManager;
    }

    @Override
    public void setExtensionsManager(ExtensionsManager extensionsManager) {
        this.extensionsManager = extensionsManager;
    }

    @Override
    public void addBuildCompatibleExtension(String extensionClassName) {
        buildCompatibleExtensionClassNames.add(extensionClassName);
        messageHandler.info("Queued build compatible extension: " + extensionClassName);
    }

    /**
     * Loads build-compatible extensions via ServiceLoader and explicitly registered class names.
     */
    public void loadBuildCompatibleExtensions() {
        buildCompatibleExtensionRunner = new BuildCompatibleExtensionRunner(
                messageHandler, knowledgeBase, beanManager, bceInvokerRegistry);

        messageHandler.info("Loading build compatible extensions");
        Set<String> loadedBceClassNames = new HashSet<>();
        final ClassLoader ccl = Thread.currentThread().getContextClassLoader();

        ServiceLoader<BuildCompatibleExtension> serviceLoader = ServiceLoader.load(BuildCompatibleExtension.class,
                ccl);

        for (BuildCompatibleExtension extension : serviceLoader) {
            String className = extension.getClass().getName();
            if (loadedBceClassNames.add(className)) {
                if (shouldSkipBuildCompatibleExtension(extension.getClass())) {
                    messageHandler.info("Skipped build compatible extension due to @SkipIfPortableExtensionPresent: " + className);
                    continue;
                }
                buildCompatibleExtensions.add(extension);
                messageHandler.info("Loaded build compatible extension: " + className);
            } else {
                messageHandler.warn("Skipped duplicate build compatible extension registration: " + className);
            }
        }

        discoverBuildCompatibleExtensionsFromServiceResources(loadedBceClassNames, ccl);

        int loadedCount = buildCompatibleExtensions.size();

        for (String className : buildCompatibleExtensionClassNames) {
            try {
                Class<?> extensionClass = loadClassWithCclFallback(className, ccl);
                if (!BuildCompatibleExtension.class.isAssignableFrom(extensionClass)) {
                    knowledgeBase.addDefinitionError("Build compatible extension class " + className +
                            " does not implement jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension");
                } else {
                    if (loadedBceClassNames.add(className)) {
                        if (shouldSkipBuildCompatibleExtension(extensionClass)) {
                            messageHandler.info("Skipped build compatible extension due to @SkipIfPortableExtensionPresent: " + className);
                            continue;
                        }
                        BuildCompatibleExtension extension =
                                (BuildCompatibleExtension) extensionClass.getDeclaredConstructor().newInstance();
                        buildCompatibleExtensions.add(extension);
                        messageHandler.info("Loaded build compatible extension: " + className);
                    } else {
                        messageHandler.info("Skipped duplicate build compatible extension registration: " + className);
                    }
                }
                loadedCount++;
            } catch (Exception e) {
                knowledgeBase.addDefinitionError("Failed to load build compatible extension: " + className);
                messageHandler.exception("Failed to load build compatible extension: " + className, e);
            }
        }

        messageHandler.info("Loaded " + loadedCount + " build compatible extension(s)");
    }

    private void discoverBuildCompatibleExtensionsFromServiceResources(Set<String> loadedBceClassNames,
                                                                       ClassLoader ccl) {
        final String servicePath = "META-INF/services/jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension";
        try {
            Enumeration<URL> resources = (ccl != null)
                    ? ccl.getResources(servicePath)
                    : Syringe.class.getClassLoader().getResources(servicePath);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                List<String> providerClassNames = readServiceProviderClassNames(url);
                for (String providerClassName : providerClassNames) {
                    if (!loadedBceClassNames.add(providerClassName)) {
                        messageHandler.warn("Skipped duplicate build compatible extension registration: " + providerClassName);
                        continue;
                    }
                    try {
                        Class<?> providerClass = loadClassWithCclFallback(providerClassName, ccl);
                        if (!BuildCompatibleExtension.class.isAssignableFrom(providerClass)) {
                            knowledgeBase.addDefinitionError("Build compatible extension class " + providerClassName +
                                    " from " + servicePath + " does not implement " +
                                    "jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension");
                            continue;
                        }
                        if (shouldSkipBuildCompatibleExtension(providerClass)) {
                            messageHandler.info("Skipped build compatible extension due to @SkipIfPortableExtensionPresent: " + providerClassName);
                            continue;
                        }
                        BuildCompatibleExtension extension =
                                (BuildCompatibleExtension) providerClass.getDeclaredConstructor().newInstance();
                        buildCompatibleExtensions.add(extension);
                        messageHandler.info("Loaded build compatible extension from service resource: " + providerClassName);
                    } catch (Exception e) {
                        knowledgeBase.addDefinitionError("Failed to load build compatible extension from service resource: " +
                                providerClassName);
                        messageHandler.exception("Failed to load build compatible extension from service resource: " + providerClassName, e);
                    }
                }
            }
        } catch (Exception e) {
            messageHandler.exception("Failed to scan build compatible extension service resources", e);
        }
    }

    private List<String> readServiceProviderClassNames(URL url) {
        List<String> classNames = new ArrayList<>();
        InputStream stream = null;
        BufferedReader reader = null;
        try {
            stream = url.openStream();
            reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int commentIdx = trimmed.indexOf('#');
                if (commentIdx >= 0) {
                    trimmed = trimmed.substring(0, commentIdx).trim();
                }
                if (!trimmed.isEmpty()) {
                    classNames.add(trimmed);
                }
            }
        } catch (Exception e) {
            messageHandler.exception("Failed to read build compatible extension service resource: " + url, e);
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                } else if (stream != null) {
                    stream.close();
                }
            } catch (Exception ignored) {
                // Best effort.
            }
        }
        return classNames;
    }

    private Class<?> loadClassWithCclFallback(String className, ClassLoader ccl) throws ClassNotFoundException {
        if (ccl != null) {
            try {
                return Class.forName(className, true, ccl);
            } catch (ClassNotFoundException ignored) {
                // Fallback below.
            }
        }
        return Class.forName(className);
    }

    private boolean shouldSkipBuildCompatibleExtension(Class<?> extensionClass) {
        SkipIfPortableExtensionPresent skipAnnotation = getSkipIfPortableExtensionPresentAnnotation(extensionClass);
        if (skipAnnotation == null) {
            return false;
        }
        Class<? extends Extension> portableExtensionType = skipAnnotation.value();
        if (portableExtensionType == null) {
            return false;
        }
        for (Extension extension : extensionsManager.getExtensions()) {
            if (portableExtensionType.isInstance(extension)) {
                return true;
            }
        }
        for (String extensionClassName : extensionsManager.getExtensionClassNames()) {
            try {
                Class<?> configuredExtensionType = Class.forName(extensionClassName);
                if (portableExtensionType.isAssignableFrom(configuredExtensionType)) {
                    return true;
                }
            } catch (ClassNotFoundException ignored) {
                // Definition error is handled in loadExtensions(); skip evaluation remains best-effort.
            }
        }
        return false;
    }

    public void fireBuildCompatibleExtensionPhase(BceSupportedPhase phase) {
        if (buildCompatibleExtensionRunner == null || buildCompatibleExtensions.isEmpty()) {
            return;
        }
        messageHandler.info("Firing BCE phase: " + phase);
        buildCompatibleExtensionRunner.runPhase(phase, buildCompatibleExtensions);
    }

    @Override
    public void clear() {
        buildCompatibleExtensionClassNames.clear();
        buildCompatibleExtensions.clear();
        bceInvokerRegistry.clear();
        buildCompatibleExtensionRunner = null;
    }
}
