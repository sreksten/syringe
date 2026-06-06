package com.threeamigos.common.util.implementations.injection.bce;

import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.inject.build.compatible.spi.BeanInfo;
import jakarta.enterprise.inject.build.compatible.spi.Messages;
import jakarta.enterprise.inject.build.compatible.spi.ObserverInfo;
import jakarta.enterprise.lang.model.AnnotationTarget;

final class BceMessages implements Messages {

    private final MessageHandler messageHandler;
    private final KnowledgeBase knowledgeBase;

    BceMessages(MessageHandler messageHandler, KnowledgeBase knowledgeBase) {
        this.messageHandler = messageHandler;
        this.knowledgeBase = knowledgeBase;
    }

    @Override
    public void info(String message) {
        messageHandler.info("[BCE] " + message);
    }

    @Override
    public void info(String message, AnnotationTarget location) {
        info(message + " @" + describeTarget(location));
    }

    @Override
    public void info(String message, BeanInfo bean) {
        info(message + " @bean=" + describeBean(bean));
    }

    @Override
    public void info(String message, ObserverInfo observer) {
        info(message + " @observer=" + describeObserver(observer));
    }

    @Override
    public void warn(String message) {
        messageHandler.warn("[BCE] " + message);
    }

    @Override
    public void warn(String message, AnnotationTarget location) {
        warn(message + " @" + describeTarget(location));
    }

    @Override
    public void warn(String message, BeanInfo bean) {
        warn(message + " @bean=" + describeBean(bean));
    }

    @Override
    public void warn(String message, ObserverInfo observer) {
        warn(message + " @observer=" + describeObserver(observer));
    }

    @Override
    public void error(String message) {
        messageHandler.error("[BCE] " + message);
        knowledgeBase.addDefinitionError("[BCE] " + message);
    }

    @Override
    public void error(String message, AnnotationTarget location) {
        error(message + " @" + describeTarget(location));
    }

    @Override
    public void error(String message, BeanInfo bean) {
        error(message + " @bean=" + describeBean(bean));
    }

    @Override
    public void error(String message, ObserverInfo observer) {
        error(message + " @observer=" + describeObserver(observer));
    }

    @Override
    public void error(Exception exception) {
        messageHandler.exception("[BCE]", exception);
        knowledgeBase.addDefinitionError("[BCE] " + exception.getMessage(), exception);
    }

    private static String describeTarget(AnnotationTarget target) {
        if (target == null) {
            return "<unknown-target>";
        }
        if (target.isDeclaration()) {
            return target.asDeclaration().kind().name();
        }
        if (target.isType()) {
            return target.asType().kind().name();
        }
        return target.toString();
    }

    private static String describeBean(BeanInfo bean) {
        if (bean == null || bean.declaringClass() == null) {
            return "<unknown-bean>";
        }
        return bean.declaringClass().name();
    }

    private static String describeObserver(ObserverInfo observer) {
        if (observer == null) {
            return "<unknown-observer>";
        }
        try {
            if (observer.observerMethod() != null) {
                return observer.observerMethod().name();
            }
        } catch (RuntimeException ignored) {
            // Some observer kinds (e.g., synthetic) may not expose a java method.
        }
        if (observer.declaringClass() != null) {
            return observer.declaringClass().name();
        }
        return "<unknown-observer>";
    }
}
