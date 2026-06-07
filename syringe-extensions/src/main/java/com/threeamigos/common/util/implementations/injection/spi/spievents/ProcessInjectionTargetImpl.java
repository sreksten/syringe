package com.threeamigos.common.util.implementations.injection.spi.spievents;

import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.spi.Phase;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.InjectionTarget;
import jakarta.enterprise.inject.spi.ProcessInjectionTarget;

/**
 * ProcessInjectionTarget event implementation.
 */
public class ProcessInjectionTargetImpl<T> extends PhaseAware
        implements ProcessInjectionTarget<T>, ObserverInvocationLifecycle {

    private final AnnotatedType<T> annotatedType;
    private final KnowledgeBase knowledgeBase;
    private InjectionTarget<T> injectionTarget;
    private final ThreadLocal<Boolean> observerInvocationActive = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public ProcessInjectionTargetImpl(MessageHandler messageHandler, KnowledgeBase knowledgeBase,
                                      AnnotatedType<T> annotatedType, InjectionTarget<T> injectionTarget) {
        super(messageHandler);
        checkNotNull(annotatedType, "AnnotatedType");
        checkNotNull(injectionTarget, "InjectionTarget");
        this.annotatedType = annotatedType;
        this.knowledgeBase = knowledgeBase;
        this.injectionTarget = injectionTarget;
    }

    @Override
    public AnnotatedType<T> getAnnotatedType() {
        assertObserverInvocationActive();
        return annotatedType;
    }

    public AnnotatedType<T> getAnnotatedTypeInternal() {
        return annotatedType;
    }

    @Override
    public InjectionTarget<T> getInjectionTarget() {
        assertObserverInvocationActive();
        return injectionTarget;
    }

    public InjectionTarget<T> getInjectionTargetInternal() {
        return injectionTarget;
    }

    @Override
    public void setInjectionTarget(InjectionTarget<T> injectionTarget) {
        assertObserverInvocationActive();
        checkNotNull(injectionTarget, "InjectionTarget");
        info(Phase.PROCESS_INJECTION_TARGET, "Changing InjectionTarget for " + annotatedType.getJavaClass().getName());
        this.injectionTarget = injectionTarget;
    }

    @Override
    public void addDefinitionError(Throwable t) {
        assertObserverInvocationActive();
        knowledgeBase.addDefinitionError(Phase.PROCESS_INJECTION_TARGET, "Definition error for " +
                annotatedType.getJavaClass().getName(), t);
    }

    @Override
    public void beginObserverInvocation() {
        observerInvocationActive.set(Boolean.TRUE);
    }

    @Override
    public void endObserverInvocation() {
        observerInvocationActive.set(Boolean.FALSE);
    }

    private void assertObserverInvocationActive() {
        if (!observerInvocationActive.get()) {
            throw new IllegalStateException("ProcessInjectionTarget methods may only be called during observer method invocation");
        }
    }
}
