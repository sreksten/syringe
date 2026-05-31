package com.threeamigos.common.util.implementations.injection.spi.spievents;

import com.threeamigos.common.util.implementations.injection.discovery.NonPortableBehaviourException;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.spi.Phase;
import com.threeamigos.common.util.implementations.injection.spi.configurators.ObserverMethodConfiguratorImpl;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ObserverMethod;
import jakarta.enterprise.inject.spi.ProcessSyntheticObserverMethod;
import jakarta.enterprise.inject.spi.configurator.ObserverMethodConfigurator;

/**
 * ProcessSyntheticObserverMethod event implementation.
 *
 * @param <T> observed event type
 * @param <X> declaring bean class
 */
public class ProcessSyntheticObserverMethodImpl<T, X> extends PhaseAware
        implements ProcessSyntheticObserverMethod<T, X>, ObserverInvocationLifecycle {

    private final KnowledgeBase knowledgeBase;
    private final Extension source;
    private ObserverMethod<T> observerMethod;
    private ObserverMethodConfiguratorImpl<T> configurator;
    private boolean vetoed;
    private final ThreadLocal<Boolean> observerInvocationActive = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private final ThreadLocal<Boolean> lifecycleManaged = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private final ThreadLocal<Boolean> setCalledInCurrentInvocation = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private final ThreadLocal<Boolean> configureCalledInCurrentInvocation = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public ProcessSyntheticObserverMethodImpl(MessageHandler messageHandler,
                                              KnowledgeBase knowledgeBase,
                                              ObserverMethod<T> observerMethod,
                                              Extension source) {
        super(messageHandler);
        this.knowledgeBase = knowledgeBase;
        this.observerMethod = observerMethod;
        this.source = source;
    }

    @Override
    public AnnotatedMethod<X> getAnnotatedMethod() {
        assertObserverInvocationActive();
        throw new NonPortableBehaviourException(
                "ProcessSyntheticObserverMethod.getAnnotatedMethod() is non-portable and must not be used");
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
        knowledgeBase.addDefinitionError(Phase.PROCESS_OBSERVER_METHOD,
                "Definition error for synthetic observer " + observerMethod.getClass().getName(), t);
    }

    @Override
    public void setObserverMethod(ObserverMethod<T> observerMethod) {
        assertObserverInvocationActive();
        if (configureCalledInCurrentInvocation.get()) {
            throw new IllegalStateException(
                    "setObserverMethod() and configureObserverMethod() cannot both be used in the same observer invocation");
        }
        checkNotNull(observerMethod, "ObserverMethod");
        this.observerMethod = observerMethod;
        setCalledInCurrentInvocation.set(Boolean.TRUE);
    }

    @Override
    public ObserverMethodConfigurator<T> configureObserverMethod() {
        assertObserverInvocationActive();
        if (setCalledInCurrentInvocation.get()) {
            throw new IllegalStateException(
                    "setObserverMethod() and configureObserverMethod() cannot both be used in the same observer invocation");
        }
        configureCalledInCurrentInvocation.set(Boolean.TRUE);
        if (configurator == null) {
            configurator = new ObserverMethodConfiguratorImpl<>(true);
            configurator.read(observerMethod);
        }
        return configurator;
    }

    @Override
    public void veto() {
        assertObserverInvocationActive();
        this.vetoed = true;
    }

    @Override
    public Extension getSource() {
        assertObserverInvocationActive();
        return source;
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
                    "ProcessSyntheticObserverMethod methods may only be called during observer method invocation");
        }
    }
}
