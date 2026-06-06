package com.threeamigos.common.util.implementations.injection.bce;

import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.inject.build.compatible.spi.ScannedClasses;

final class BceScannedClasses implements ScannedClasses {

    private final KnowledgeBase knowledgeBase;
    private final MessageHandler messageHandler;

    BceScannedClasses(KnowledgeBase knowledgeBase, MessageHandler messageHandler) {
        this.knowledgeBase = knowledgeBase;
        this.messageHandler = messageHandler;
    }

    @Override
    public void add(String className) {
        try {
            Class<?> clazz = resolveClass(className);
            // ScannedClasses#add is a programmatic discovery hook.
            // Per CDI behavior this must make the class participate in discovery even
            // when archive defaults are annotated/implicit or the class was pre-discovered
            // with a restrictive mode (e.g., NONE in managed bootstrap).
            knowledgeBase.addProgrammatic(clazz, BeanArchiveMode.EXPLICIT);
            messageHandler.info("[BCE] Added scanned class " + className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Cannot add scanned class " + className, e);
        }
    }

    private Class<?> resolveClass(String className) throws ClassNotFoundException {
        ClassLoader ccl = Thread.currentThread().getContextClassLoader();
        if (ccl != null) {
            try {
                return Class.forName(className, false, ccl);
            } catch (ClassNotFoundException ignored) {
                // fall through to the container/module class loader
            }
        }

        ClassLoader fallback = BceScannedClasses.class.getClassLoader();
        if (fallback != null && fallback != ccl) {
            return Class.forName(className, false, fallback);
        }
        return Class.forName(className);
    }
}
