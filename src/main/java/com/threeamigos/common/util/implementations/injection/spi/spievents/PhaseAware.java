package com.threeamigos.common.util.implementations.injection.spi.spievents;

import com.threeamigos.common.util.implementations.injection.spi.Phase;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;

class PhaseAware {

    protected final MessageHandler messageHandler;

    PhaseAware(MessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }

    protected void info(Phase phase, String message) {
        messageHandler.info("[" + phase.getDescription() + "] " + message);
    }

    protected void checkNotNull(Object object, String objectName) {
        if (object == null) {
            throw new IllegalArgumentException(objectName + " cannot be null");
        }
    }
}
