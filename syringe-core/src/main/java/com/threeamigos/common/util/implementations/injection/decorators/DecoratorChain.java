package com.threeamigos.common.util.implementations.injection.decorators;

import com.threeamigos.common.util.implementations.injection.knowledgebase.DecoratorInfo;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a chain of decorators that wrap a target bean instance.
 *
 * <p>A decorator chain is an ordered sequence of decorator instances, where each decorator
 * delegates to the next decorator (via @Delegate injection), with the innermost decorator
 * delegating to the actual bean instance.
 *
 * <p><b>Decorator Chain Structure:</b>
 * <pre>
 * Client Code
 *     ↓ calls method
 * Outermost Decorator (Priority 100)
 *     ↓ @Delegate delegates to
 * Middle Decorator (Priority 200)
 *     ↓ @Delegate delegates to
 * Innermost Decorator (Priority 300)
 *     ↓ @Delegate delegates to
 * Actual Bean Instance (Target)
 * </pre>
 *
 * <p><b>Example Usage:</b>
 * <pre>{@code
 * // Given decorators: TimingDecorator, LoggingDecorator
 * // And target: PaymentProcessorImpl
 *
 * DecoratorChain.Builder builder = DecoratorChain.builder();
 * builder.addDecorator(timingDecoratorInfo, timingInstance);
 * builder.addDecorator(loggingDecoratorInfo, loggingInstance);
 * builder.setTarget(paymentProcessorImpl);
 * DecoratorChain chain = builder.build();
 *
 * // Result:
 * // Client → TimingDecorator → LoggingDecorator → PaymentProcessorImpl
 * }</pre>
 *
 * <p><b>Key Differences from InterceptorChain:</b>
 * <table>
 * <tr><th>Aspect</th><th>InterceptorChain</th><th>DecoratorChain</th></tr>
 * <tr><td>Wrapping</td><td>Method interception</td><td>Object wrapping</td></tr>
 * <tr><td>Delegation</td><td>InvocationContext.proceed()</td><td>@Delegate injection</td></tr>
 * <tr><td>Granularity</td><td>Per-method</td><td>Per-bean (all methods)</td></tr>
 * <tr><td>Instances</td><td>Stateless interceptors</td><td>Stateful decorators</td></tr>
 * </table>
 *
 * <p><b>Thread Safety:</b>
 * <ul>
 *   <li>DecoratorChain is immutable once built</li>
 *   <li>Decorator instances themselves may be stateful</li>
 *   <li>@Delegate references are final and thread-safe</li>
 * </ul>
 *
 * @see DecoratorInfo
 * @see DecoratorResolver
 * @see DecoratorAwareProxyGenerator
 * @author Stefano Reksten
 */
public class DecoratorChain {

    private final List<DecoratorInstance> decorators;
    private final Object targetInstance;
    private final Object outermostInstance;

    /**
     * Creates a decorator chain.
     *
     * @param decorators ordered list of decorator instances (outermost first)
     * @param targetInstance the actual bean instance (innermost)
     */
    DecoratorChain(List<DecoratorInstance> decorators, @Nonnull Object targetInstance, Object outermostInstance) {
        this.decorators = Collections.unmodifiableList(new ArrayList<>(decorators));
        this.targetInstance = targetInstance;
        this.outermostInstance = outermostInstance;
    }

    /**
     * Returns the ordered list of decorators (outermost first).
     *
     * @return immutable list of decorator instances
     */
    public List<DecoratorInstance> getDecorators() {
        return decorators;
    }

    /**
     * Returns the outermost decorator (the one client code interacts with).
     *
     * <p>If there are no decorators, returns the target instance.
     *
     * @return the outermost decorator instance or target if no decorators
     */
    public Object getOutermostInstance() {
        return outermostInstance;
    }

    /**
     * Returns the number of decorators in the chain.
     *
     * @return decorator count
     */
    public int size() {
        return decorators.size();
    }

    /**
     * Checks if the chain has any decorators.
     *
     * @return true if there are no decorators
     */
    public boolean isEmpty() {
        return decorators.isEmpty();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("DecoratorChain{");
        sb.append("size=").append(decorators.size());
        sb.append(", chain=[");

        for (int i = 0; i < decorators.size(); i++) {
            if (i > 0) sb.append(" → ");
            sb.append(decorators.get(i).getDecoratorInfo().getDecoratorClass().getSimpleName());
        }

        if (!decorators.isEmpty()) {
            sb.append(" → ");
        }
        sb.append(targetInstance.getClass().getSimpleName());
        sb.append("]}");

        return sb.toString();
    }
}
