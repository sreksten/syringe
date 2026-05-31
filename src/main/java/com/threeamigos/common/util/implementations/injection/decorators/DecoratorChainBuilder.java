package com.threeamigos.common.util.implementations.injection.decorators;

import com.threeamigos.common.util.implementations.injection.knowledgebase.DecoratorInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for constructing decorator chains in a fluent manner.
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * DecoratorChain chain = DecoratorChain.builder()
 *     .addDecorator(timingDecoratorInfo, timingInstance)
 *     .addDecorator(loggingDecoratorInfo, loggingInstance)
 *     .setTarget(paymentProcessor)
 *     .build();
 * }</pre>
 */
public class DecoratorChainBuilder {
    private final List<DecoratorInstance> decorators = new ArrayList<>();
    private Object targetInstance;
    private Object outermostInstance;

    /**
     * Adds a decorator to the chain.
     *
     * <p>Decorators should be added in priority order (outermost first).
     * The builder does NOT automatically sort by priority - the caller must
     * add decorators in the correct order (typically using DecoratorResolver).
     *
     * @param decoratorInfo     the decorator metadata
     * @param decoratorInstance the decorator instance
     * @throws NullPointerException if any parameter is null
     */
    public void addDecorator(DecoratorInfo decoratorInfo, Object decoratorInstance) {
        decorators.add(new DecoratorInstance(decoratorInfo, decoratorInstance));
    }

    /**
     * Sets the target bean instance.
     *
     * <p>This is the actual bean instance that will be decorated (innermost).
     *
     * @param targetInstance the target bean
     * @return this builder for chaining
     * @throws NullPointerException if targetInstance is null
     */
    public DecoratorChainBuilder setTarget(Object targetInstance) {
        this.targetInstance = targetInstance;
        return this;
    }

    public void setOutermostInstance(Object outermostInstance) {
        this.outermostInstance = outermostInstance;
    }

    /**
     * Builds the decorator chain.
     *
     * @return the completed decorator chain
     * @throws IllegalStateException if the target instance is not set
     */
    public DecoratorChain build() {
        if (targetInstance == null) {
            throw new IllegalStateException("Target instance must be set");
        }
        Object exposedInstance = outermostInstance;
        if (exposedInstance == null) {
            exposedInstance = decorators.isEmpty() ? targetInstance : decorators.get(0).getDecoratorInstance();
        }
        return new DecoratorChain(decorators, targetInstance, exposedInstance);
    }
}
