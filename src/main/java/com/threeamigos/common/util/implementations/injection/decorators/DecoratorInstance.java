package com.threeamigos.common.util.implementations.injection.decorators;

import com.threeamigos.common.util.implementations.injection.knowledgebase.DecoratorInfo;
import jakarta.annotation.Nonnull;

/**
 * Represents a single decorator in the chain with its metadata and instance.
 */
public class DecoratorInstance {

    private final DecoratorInfo decoratorInfo;
    private final Object decoratorInstance;

    /**
     * Creates a decorator instance holder.
     *
     * @param decoratorInfo     the decorator metadata
     * @param decoratorInstance the actual decorator instance
     */
    DecoratorInstance(@Nonnull DecoratorInfo decoratorInfo, @Nonnull Object decoratorInstance) {
        this.decoratorInfo = decoratorInfo;
        this.decoratorInstance = decoratorInstance;
    }

    DecoratorInfo getDecoratorInfo() {
        return decoratorInfo;
    }

    Object getDecoratorInstance() {
        return decoratorInstance;
    }

    @Override
    public String toString() {
        return "DecoratorInstance{" +
                "class=" + decoratorInfo.getDecoratorClass().getSimpleName() +
                ", priority=" + decoratorInfo.getPriority() +
                '}';
    }
}
