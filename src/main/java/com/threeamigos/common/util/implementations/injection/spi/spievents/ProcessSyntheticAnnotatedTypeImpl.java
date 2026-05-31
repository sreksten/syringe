package com.threeamigos.common.util.implementations.injection.spi.spievents;

import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessSyntheticAnnotatedType;

/**
 * ProcessSyntheticAnnotatedType event implementation for extension-added annotated types.
 */
public class ProcessSyntheticAnnotatedTypeImpl<T> extends ProcessAnnotatedTypeImpl<T>
        implements ProcessSyntheticAnnotatedType<T> {

    private final Extension source;

    public ProcessSyntheticAnnotatedTypeImpl(MessageHandler messageHandler,
                                             AnnotatedType<T> annotatedType,
                                             Extension source) {
        super(messageHandler, annotatedType);
        this.source = source;
    }

    @Override
    public Extension getSource() {
        return source;
    }
}
