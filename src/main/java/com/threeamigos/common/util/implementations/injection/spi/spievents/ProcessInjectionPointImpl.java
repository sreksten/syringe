package com.threeamigos.common.util.implementations.injection.spi.spievents;

import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.spi.Phase;
import com.threeamigos.common.util.implementations.injection.spi.configurators.InjectionPointConfiguratorImpl;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.ProcessInjectionPoint;
import jakarta.enterprise.inject.spi.configurator.InjectionPointConfigurator;

/**
 * ProcessInjectionPoint event implementation.
 */
public class ProcessInjectionPointImpl<T, X> extends PhaseAware implements ProcessInjectionPoint<T, X>, ObserverInvocationLifecycle {

    private InjectionPoint injectionPoint;
    private final KnowledgeBase knowledgeBase;
    private InjectionPointConfiguratorImpl configurator;
    private final ThreadLocal<Boolean> observerInvocationActive = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private final ThreadLocal<Boolean> setCalledInCurrentInvocation = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private final ThreadLocal<Boolean> configureCalledInCurrentInvocation = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public ProcessInjectionPointImpl(MessageHandler messageHandler, InjectionPoint injectionPoint,
                                     KnowledgeBase knowledgeBase) {
        super(messageHandler);
        checkNotNull(injectionPoint, "InjectionPoint");
        this.injectionPoint = injectionPoint;
        this.knowledgeBase = knowledgeBase;
    }

    @Override
    public InjectionPoint getInjectionPoint() {
        assertObserverInvocationActive();
        return injectionPoint;
    }

    public InjectionPoint getInjectionPointInternal() {
        return injectionPoint;
    }

    @Override
    public void setInjectionPoint(InjectionPoint injectionPoint) {
        assertObserverInvocationActive();
        if (configureCalledInCurrentInvocation.get()) {
            throw new IllegalStateException("setInjectionPoint() and configureInjectionPoint() cannot both be used in the same observer invocation");
        }
        checkNotNull(injectionPoint, "InjectionPoint");
        info(Phase.PROCESS_INJECTION_POINT, "Changing injection point for " + injectionPoint.getMember());
        this.injectionPoint = injectionPoint;
        setCalledInCurrentInvocation.set(Boolean.TRUE);
    }

    @Override
    public InjectionPointConfigurator configureInjectionPoint() {
        assertObserverInvocationActive();
        if (setCalledInCurrentInvocation.get()) {
            throw new IllegalStateException("setInjectionPoint() and configureInjectionPoint() cannot both be used in the same observer invocation");
        }
        info(Phase.PROCESS_INJECTION_POINT, "Configuring injection point for " + injectionPoint.getMember());
        configureCalledInCurrentInvocation.set(Boolean.TRUE);
        if (configurator == null) {
            configurator = new InjectionPointConfiguratorImpl(injectionPoint) {
                @Override
                public InjectionPoint complete() {
                    InjectionPoint configured = super.complete();
                    injectionPoint = configured;
                    return configured;
                }
            };
        }
        return configurator;
    }

    @Override
    public void addDefinitionError(Throwable t) {
        assertObserverInvocationActive();
        knowledgeBase.addDefinitionError(Phase.PROCESS_INJECTION_POINT, "Definition error for " +
                injectionPoint.getMember(), t);
    }

    @Override
    public void beginObserverInvocation() {
        observerInvocationActive.set(Boolean.TRUE);
        setCalledInCurrentInvocation.set(Boolean.FALSE);
        configureCalledInCurrentInvocation.set(Boolean.FALSE);
    }

    @Override
    public void endObserverInvocation() {
        if (configureCalledInCurrentInvocation.get() && configurator != null) {
            injectionPoint = configurator.complete();
        }
        observerInvocationActive.set(Boolean.FALSE);
        setCalledInCurrentInvocation.set(Boolean.FALSE);
        configureCalledInCurrentInvocation.set(Boolean.FALSE);
    }

    private void assertObserverInvocationActive() {
        if (!observerInvocationActive.get()) {
            throw new IllegalStateException("ProcessInjectionPoint methods may only be called during observer method invocation");
        }
    }
}
