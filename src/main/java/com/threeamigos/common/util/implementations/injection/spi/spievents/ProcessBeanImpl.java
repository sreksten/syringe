package com.threeamigos.common.util.implementations.injection.spi.spievents;

import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.spi.Phase;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.inject.spi.*;

/**
 * ProcessBean event implementation.
 * 
 * <p>Fired for each registered bean (managed beans, producer methods, producer fields).
 * Extensions can observe this event to:
 * <ul>
 *   <li>Inspect bean metadata</li>
 *   <li>Validate bean configuration</li>
 * </ul>
 *
 * @param <T> the bean class type
 * @see ProcessBean
 */
public class ProcessBeanImpl<T> extends PhaseAware
        implements ProcessBean<T>, ObserverInvocationLifecycle {

    protected final Bean<T> bean;
    private final Annotated annotated;
    protected final KnowledgeBase knowledgeBase;
    private final ThreadLocal<Boolean> observerInvocationActive = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private final ThreadLocal<Boolean> lifecycleManaged = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public ProcessBeanImpl(MessageHandler messageHandler, KnowledgeBase knowledgeBase, Bean<T> bean, Annotated annotated) {
        super(messageHandler);
        this.knowledgeBase = knowledgeBase;
        this.bean = bean;
        this.annotated = annotated;
    }

    @Override
    public Annotated getAnnotated() {
        assertObserverInvocationActive();
        return annotated;
    }

    @Override
    public Bean<T> getBean() {
        assertObserverInvocationActive();
        return bean;
    }

    /**
     * Internal accessor used by lifecycle observer resolution before observer invocation starts.
     */
    public Bean<T> getBeanInternal() {
        return bean;
    }

    @Override
    public void addDefinitionError(Throwable t) {
        assertObserverInvocationActive();
        knowledgeBase.addDefinitionError(Phase.PROCESS_BEAN, "Definition error for " +
                bean.getBeanClass().getName(), t);
    }

    @Override
    public void beginObserverInvocation() {
        lifecycleManaged.set(Boolean.TRUE);
        observerInvocationActive.set(Boolean.TRUE);
    }

    @Override
    public void endObserverInvocation() {
        observerInvocationActive.set(Boolean.FALSE);
    }

    protected void assertObserverInvocationActive() {
        if (lifecycleManaged.get() && !observerInvocationActive.get()) {
            throw new IllegalStateException(
                    "ProcessBean methods may only be called during observer method invocation");
        }
    }
}
