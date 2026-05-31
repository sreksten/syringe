package com.threeamigos.common.util.implementations.injection.knowledgebase;

import jakarta.enterprise.inject.spi.InjectionPoint;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.Set;

/**
 * Metadata holder for CDI decorators (@Decorator).
 *
 * <p>CDI 4.1 Decorator Requirements:
 * <ul>
 *   <li>Must have @Decorator annotation</li>
 *   <li>Must have exactly ONE @Delegate injection point</li>
 *   <li>The @Delegate injection point determines which types are decorated</li>
 *   <li>Must implement or extend the decorated types</li>
 *   <li>Priority determines decorator order (lower = outer decorator)</li>
 * </ul>
 *
 * <p>Decorators wrap beans and add behavior by delegating to the original instance.
 * Unlike interceptors (which work via method interception), decorators work by implementing
 * the same interface/class and delegating calls to the @Delegate instance.
 *
 * @see jakarta.decorator.Decorator
 * @see jakarta.decorator.Delegate
 */
public class DecoratorInfo {

    private final Class<?> decoratorClass;
    private final Set<Type> decoratedTypes;      // Types this decorator can decorate
    private final int priority;
    private final InjectionPoint delegateInjectionPoint;  // The @Delegate injection point

    /**
     * Creates decorator metadata.
     *
     * @param decoratorClass the decorator class annotated with @Decorator
     * @param decoratedTypes the set of types this decorator can decorate (from @Delegate injection point)
     * @param priority the priority value (from @Priority), defaults to Integer.MAX_VALUE if not specified
     * @param delegateInjectionPoint the @Delegate injection point (must be exactly one)
     */
    public DecoratorInfo(
            Class<?> decoratorClass,
            Set<Type> decoratedTypes,
            int priority,
            InjectionPoint delegateInjectionPoint) {

        this.decoratorClass = Objects.requireNonNull(decoratorClass, "decoratorClass cannot be null");
        this.decoratedTypes = Objects.requireNonNull(decoratedTypes, "decoratedTypes cannot be null");
        this.priority = priority;
        this.delegateInjectionPoint = Objects.requireNonNull(delegateInjectionPoint, "delegateInjectionPoint cannot be null");
    }

    public Class<?> getDecoratorClass() {
        return decoratorClass;
    }

    public Set<Type> getDecoratedTypes() {
        return decoratedTypes;
    }

    public int getPriority() {
        return priority;
    }

    public InjectionPoint getDelegateInjectionPoint() {
        return delegateInjectionPoint;
    }

    /**
     * Checks if this decorator can decorate the given type.
     *
     * @param type the type to check
     * @return true if this decorator's decorated types include the given type
     */
    public boolean canDecorate(Type type) {
        return decoratedTypes.contains(type);
    }

    @Override
    public String toString() {
        return "DecoratorInfo{" +
                "class=" + decoratorClass.getName() +
                ", decoratedTypes=" + decoratedTypes +
                ", priority=" + priority +
                ", delegate=" + delegateInjectionPoint +
                '}';
    }
}
