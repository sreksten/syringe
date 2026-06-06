package com.threeamigos.common.util.implementations.injection.spi.spievents;

import com.threeamigos.common.util.implementations.injection.spi.Phase;
import com.threeamigos.common.util.implementations.injection.spi.configurators.AnnotatedTypeConfiguratorImpl;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.inject.spi.*;
import jakarta.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;

/**
 * ProcessAnnotatedType event implementation.
 * 
 * <p>Fired for each discovered type during bean discovery. Extensions can use this event to:
 * <ul>
 *   <li>Veto the type via {@link #veto()}</li>
 *   <li>Replace the AnnotatedType via {@link #setAnnotatedType(AnnotatedType)}</li>
 *   <li>Configure the AnnotatedType via {@link #configureAnnotatedType()}</li>
 * </ul>
 *
 * @param <T> the type being processed
 * @see ProcessAnnotatedType
 */
public class ProcessAnnotatedTypeImpl<T> extends PhaseAware implements ProcessAnnotatedType<T>, ObserverInvocationLifecycle {

    private AnnotatedType<T> annotatedType;
    private boolean vetoed = false;
    private AnnotatedTypeConfiguratorImpl<T> configurator;
    private final ThreadLocal<Boolean> observerInvocationActive = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private final ThreadLocal<Boolean> setCalledInCurrentInvocation = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private final ThreadLocal<Boolean> configureCalledInCurrentInvocation = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public ProcessAnnotatedTypeImpl(MessageHandler messageHandler, AnnotatedType<T> annotatedType) {
        super(messageHandler);
        this.annotatedType = annotatedType;
    }

    @Override
    public AnnotatedType<T> getAnnotatedType() {
        assertObserverInvocationActive();
        return annotatedType;
    }

    @Override
    public void setAnnotatedType(AnnotatedType<T> type) {
        assertObserverInvocationActive();
        if (type == null) {
            throw new IllegalArgumentException("AnnotatedType cannot be null");
        }
        if (configureCalledInCurrentInvocation.get()) {
            throw new IllegalStateException("setAnnotatedType() and configureAnnotatedType() cannot both be used");
        }
        info(Phase.PROCESS_ANNOTATED_TYPE, "Changing AnnotatedType " + annotatedType.getJavaClass().getName() +
                " with " + type.getJavaClass().getName());
        this.annotatedType = type;
        this.configurator = null;
        this.setCalledInCurrentInvocation.set(Boolean.TRUE);
    }

    @Override
    public AnnotatedTypeConfigurator<T> configureAnnotatedType() {
        assertObserverInvocationActive();
        if (setCalledInCurrentInvocation.get()) {
            throw new IllegalStateException("setAnnotatedType() and configureAnnotatedType() cannot both be used");
        }
        info(Phase.PROCESS_ANNOTATED_TYPE, "Creating AnnotatedTypeConfigurator for " +
                annotatedType.getJavaClass().getName());
        this.configureCalledInCurrentInvocation.set(Boolean.TRUE);
        if (configurator == null) {
            configurator = new AnnotatedTypeConfiguratorImpl<>(annotatedType);
        }
        return configurator;
    }

    @Override
    public void veto() {
        assertObserverInvocationActive();
        info(Phase.PROCESS_ANNOTATED_TYPE, "Veto on " + annotatedType.getJavaClass().getName());
        this.vetoed = true;
    }

    public boolean isVetoed() {
        return vetoed;
    }

    public AnnotatedType<T> getAnnotatedTypeInternal() {
        return annotatedType;
    }

    @Override
    public void beginObserverInvocation() {
        observerInvocationActive.set(Boolean.TRUE);
        setCalledInCurrentInvocation.set(Boolean.FALSE);
        configureCalledInCurrentInvocation.set(Boolean.FALSE);
        configurator = null;
    }

    @Override
    public void endObserverInvocation() {
        if (configureCalledInCurrentInvocation.get() && configurator != null) {
            annotatedType = configurator.complete();
        }
        observerInvocationActive.set(Boolean.FALSE);
        setCalledInCurrentInvocation.set(Boolean.FALSE);
        configureCalledInCurrentInvocation.set(Boolean.FALSE);
        configurator = null;
    }

    private void assertObserverInvocationActive() {
        if (!observerInvocationActive.get()) {
            throw new IllegalStateException("ProcessAnnotatedType methods may only be called during observer method invocation");
        }
    }
}
