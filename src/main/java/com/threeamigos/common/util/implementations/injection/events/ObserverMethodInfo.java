package com.threeamigos.common.util.implementations.injection.events;

import com.threeamigos.common.util.implementations.injection.resolution.TypeChecker;
import jakarta.enterprise.event.Reception;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.spi.Bean;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.Set;

/**
 * Metadata holder for CDI observer methods (@Observes and @ObservesAsync).
 *
 * <p>CDI 4.1 Observer Method Requirements:
 * <ul>
 *   <li>Must have exactly one parameter annotated with @Observes or @ObservesAsync</li>
 *   <li>The observed event type is the type of the @Observes parameter</li>
 *   <li>May have qualifiers on the observed parameter to filter events</li>
 *   <li>May have additional injection points as other parameters</li>
 *   <li>Can specify reception condition (IF_EXISTS or ALWAYS)</li>
 *   <li>Can specify the transaction phase for transactional observers</li>
 * </ul>
 *
 * <p>Observer methods are invoked when events are fired via Event.fire() or Event.fireAsync().
 * Synchronous observers (@Observes) are invoked immediately during Event.fire().
 * Asynchronous observers (@ObservesAsync) are invoked asynchronously during Event.fireAsync().
 *
 * @see jakarta.enterprise.event.Observes
 * @see jakarta.enterprise.event.ObservesAsync
 * @see jakarta.enterprise.inject.spi.ObserverMethod
 */
public class ObserverMethodInfo {

    private static final TypeChecker TYPE_CHECKER = new TypeChecker();

    private final Method observerMethod;           // Null for synthetic observers
    private final Type eventType;                  // The type of event being observed
    private final Set<Annotation> qualifiers;      // Qualifiers for event filtering
    private final Reception reception;             // IF_EXISTS or ALWAYS
    private final TransactionPhase transactionPhase; // Transaction phase for sync observers
    private final boolean async;                   // true if @ObservesAsync, false if @Observes
    private final Bean<?> declaringBean;           // The bean that declares this observer method
    private final int priority;                    // Observer priority (lower = earlier)
    private final int observedParameterPosition;   // Position of @Observes/@ObservesAsync parameter (-1 if unknown)
    private final jakarta.enterprise.inject.spi.ObserverMethod<?> syntheticObserver; // Synthetic observer (for programmatically registered observers)

    /**
     * Creates observer method metadata for reflection-based observers.
     *
     * @param observerMethod the method annotated with @Observes or @ObservesAsync
     * @param eventType the type of event being observed (from observed parameter)
     * @param qualifiers the qualifiers on the observed parameter for event filtering
     * @param reception the reception condition (IF_EXISTS = only notify if bean instance exists, ALWAYS = always notify)
     * @param transactionPhase the transaction phase for synchronous observers (only applicable to @Observes)
     * @param async true if @ObservesAsync (asynchronous), false if @Observes (synchronous)
     * @param declaringBean the bean that declares this observer method
     * @param priority the priority for observer invocation order (from @Priority)
     */
    public ObserverMethodInfo(
            Method observerMethod,
            Type eventType,
            Set<Annotation> qualifiers,
            Reception reception,
            TransactionPhase transactionPhase,
            boolean async,
            Bean<?> declaringBean,
            int priority) {
        this(observerMethod, eventType, qualifiers, reception, transactionPhase, async, declaringBean, priority, -1);
    }

    public ObserverMethodInfo(
            Method observerMethod,
            Type eventType,
            Set<Annotation> qualifiers,
            Reception reception,
            TransactionPhase transactionPhase,
            boolean async,
            Bean<?> declaringBean,
            int priority,
            int observedParameterPosition) {

        this.observerMethod = Objects.requireNonNull(observerMethod, "observerMethod cannot be null");
        this.eventType = Objects.requireNonNull(eventType, "eventType cannot be null");
        this.qualifiers = Objects.requireNonNull(qualifiers, "qualifiers cannot be null");
        this.reception = Objects.requireNonNull(reception, "reception cannot be null");
        this.transactionPhase = Objects.requireNonNull(transactionPhase, "transactionPhase cannot be null");
        this.async = async;
        this.declaringBean = declaringBean; // Can be null during validation phase
        this.priority = priority;
        this.observedParameterPosition = observedParameterPosition;
        this.syntheticObserver = null;
    }

    /**
     * Creates observer method metadata for synthetic observers (registered via AfterBeanDiscovery.addObserverMethod()).
     *
     * @param eventType the type of event being observed
     * @param qualifiers the qualifiers for event filtering
     * @param reception the reception condition
     * @param transactionPhase the transaction phase for synchronous observers
     * @param priority the priority for observer invocation order
     * @param async true if asynchronous, false if synchronous
     * @param declaringBean the declaring bean (can be null for synthetic observers)
     * @param syntheticObserver the synthetic ObserverMethod instance
     */
    public ObserverMethodInfo(
            Type eventType,
            Set<Annotation> qualifiers,
            Reception reception,
            TransactionPhase transactionPhase,
            int priority,
            boolean async,
            Bean<?> declaringBean,
            jakarta.enterprise.inject.spi.ObserverMethod<?> syntheticObserver) {

        this.observerMethod = null; // No Method for synthetic observers
        this.eventType = Objects.requireNonNull(eventType, "eventType cannot be null");
        this.qualifiers = Objects.requireNonNull(qualifiers, "qualifiers cannot be null");
        this.reception = Objects.requireNonNull(reception, "reception cannot be null");
        this.transactionPhase = Objects.requireNonNull(transactionPhase, "transactionPhase cannot be null");
        this.async = async;
        this.declaringBean = declaringBean;
        this.priority = priority;
        this.observedParameterPosition = -1;
        this.syntheticObserver = Objects.requireNonNull(syntheticObserver, "syntheticObserver cannot be null");
    }

    public Method getObserverMethod() {
        return observerMethod;
    }

    public Type getEventType() {
        return eventType;
    }

    public Set<Annotation> getQualifiers() {
        return qualifiers;
    }

    public Reception getReception() {
        return reception;
    }

    public TransactionPhase getTransactionPhase() {
        return transactionPhase;
    }

    public boolean isAsync() {
        return async;
    }

    public Bean<?> getDeclaringBean() {
        return declaringBean;
    }

    public int getPriority() {
        return priority;
    }

    public int getObservedParameterPosition() {
        return observedParameterPosition;
    }

    public jakarta.enterprise.inject.spi.ObserverMethod<?> getSyntheticObserver() {
        return syntheticObserver;
    }

    public boolean isSynthetic() {
        return syntheticObserver != null;
    }

    /**
     * Checks if this observer matches the given event type and qualifiers.
     *
     * @param eventType the fired event type
     * @param eventQualifiers the fired event qualifiers
     * @return true if this observer should be notified
     */
    public boolean matches(Type eventType, Set<Annotation> eventQualifiers) {
        // Type must be assignable
        if (!TYPE_CHECKER.isAssignable(this.eventType, eventType)) {
            return false;
        }

        // Qualifiers must match (observer qualifiers must be subset of event qualifiers)
        return eventQualifiers.containsAll(this.qualifiers);
    }

    @Override
    public String toString() {
        String methodText = observerMethod != null
                ? observerMethod.getDeclaringClass().getSimpleName() + "." + observerMethod.getName()
                : "synthetic";
        return "ObserverMethodInfo{" +
                "method=" + methodText +
                ", eventType=" + eventType.getTypeName() +
                ", qualifiers=" + qualifiers +
                ", async=" + async +
                ", reception=" + reception +
                ", transactionPhase=" + transactionPhase +
                ", priority=" + priority +
                ", observedParameterPosition=" + observedParameterPosition +
                '}';
    }
}
