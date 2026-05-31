package com.threeamigos.common.util.implementations.injection.spi.spievents;

import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.spi.Phase;
import com.threeamigos.common.util.implementations.injection.spi.configurators.ObserverMethodConfiguratorImpl;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.inject.spi.*;
import jakarta.enterprise.inject.spi.configurator.ObserverMethodConfigurator;

/**
 * ProcessObserverMethod event implementation.
 * 
 * <p>Fired for each observer method discovered in managed beans.
 * Extensions can observe this event to:
 * <ul>
 *   <li>Inspect observer method metadata</li>
 *   <li>Replace the observer method via {@link #configureObserverMethod()}</li>
 *   <li>Veto the observer method via {@link #veto()}</li>
 * </ul>
 *
 * @param <T> the event type being observed
 * @param <X> the bean class containing the observer method
 * @see ProcessObserverMethod
 */
public class ProcessObserverMethodImpl<T, X> extends PhaseAware
        implements ProcessObserverMethod<T, X>, ObserverInvocationLifecycle {

    private final AnnotatedMethod<X> annotatedMethod;
    private final KnowledgeBase knowledgeBase;
    private ObserverMethod<T> observerMethod;
    private ObserverMethodConfiguratorImpl<T> configurator;
    private boolean vetoed = false;
    private final ThreadLocal<Boolean> observerInvocationActive = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private final ThreadLocal<Boolean> lifecycleManaged = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private final ThreadLocal<Boolean> setCalledInCurrentInvocation = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private final ThreadLocal<Boolean> configureCalledInCurrentInvocation = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public ProcessObserverMethodImpl(MessageHandler messageHandler, KnowledgeBase knowledgeBase,
                                     ObserverMethod<T> observerMethod, AnnotatedMethod<X> annotatedMethod) {
        super(messageHandler);
        this.knowledgeBase = knowledgeBase;
        this.observerMethod = observerMethod;
        this.annotatedMethod = annotatedMethod;
    }

    @Override
    public AnnotatedMethod<X> getAnnotatedMethod() {
        assertObserverInvocationActive();
        return annotatedMethod;
    }

    @Override
    public ObserverMethod<T> getObserverMethod() {
        assertObserverInvocationActive();
        return observerMethod;
    }

    /**
     * Internal accessor used by lifecycle observer resolution before observer invocation starts.
     */
    public ObserverMethod<T> getObserverMethodInternal() {
        return observerMethod;
    }

    @Override
    public void addDefinitionError(Throwable t) {
        assertObserverInvocationActive();
        knowledgeBase.addDefinitionError(Phase.PROCESS_OBSERVER_METHOD, "Definition error for " +
                annotatedMethod.getJavaMember().getName(), t);
    }

    @Override
    public ObserverMethodConfigurator<T> configureObserverMethod() {
        assertObserverInvocationActive();
        if (setCalledInCurrentInvocation.get()) {
            throw new IllegalStateException(
                    "setObserverMethod() and configureObserverMethod() cannot both be used in the same observer invocation");
        }
        configureCalledInCurrentInvocation.set(Boolean.TRUE);
        info(Phase.PROCESS_OBSERVER_METHOD, "Creating ObserverMethodConfigurator");
        if (configurator == null) {
            configurator = new ObserverMethodConfiguratorImpl<>(true);
            configurator.read(observerMethod);
        }
        return configurator;
    }

    @Override
    public void veto() {
        assertObserverInvocationActive();
        info(Phase.PROCESS_OBSERVER_METHOD, "Veto on " + annotatedMethod.getJavaMember().getName());
        this.vetoed = true;
    }

    @Override
    public void setObserverMethod(ObserverMethod<T> observerMethod) {
        assertObserverInvocationActive();
        if (configureCalledInCurrentInvocation.get()) {
            throw new IllegalStateException(
                    "setObserverMethod() and configureObserverMethod() cannot both be used in the same observer invocation");
        }
        checkNotNull(observerMethod, "ObserverMethod");
        info(Phase.PROCESS_OBSERVER_METHOD, "Changing observer method for " +
                annotatedMethod.getJavaMember().getName());
        this.observerMethod = observerMethod;
        setCalledInCurrentInvocation.set(Boolean.TRUE);
    }

    public boolean isVetoed() {
        return vetoed;
    }

    /**
     * Returns the final ObserverMethod selected after extension processing.
     */
    public ObserverMethod<T> getFinalObserverMethod() {
        return observerMethod;
    }

    @Override
    public void beginObserverInvocation() {
        lifecycleManaged.set(Boolean.TRUE);
        observerInvocationActive.set(Boolean.TRUE);
        setCalledInCurrentInvocation.set(Boolean.FALSE);
        configureCalledInCurrentInvocation.set(Boolean.FALSE);
        configurator = null;
    }

    @Override
    public void endObserverInvocation() {
        if (configureCalledInCurrentInvocation.get() && configurator != null) {
            observerMethod = configurator.complete();
        }
        observerInvocationActive.set(Boolean.FALSE);
        setCalledInCurrentInvocation.set(Boolean.FALSE);
        configureCalledInCurrentInvocation.set(Boolean.FALSE);
        configurator = null;
    }

    protected void assertObserverInvocationActive() {
        if (lifecycleManaged.get() && !observerInvocationActive.get()) {
            throw new IllegalStateException(
                    "ProcessObserverMethod methods may only be called during observer method invocation");
        }
    }
}
