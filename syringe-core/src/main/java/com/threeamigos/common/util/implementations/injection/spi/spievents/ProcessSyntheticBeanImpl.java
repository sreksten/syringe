package com.threeamigos.common.util.implementations.injection.spi.spievents;

import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.spi.Phase;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.inject.spi.*;

/**
 * ProcessSyntheticBean event implementation.
 *
 * <p>Fired for each synthetic bean registered via AfterBeanDiscovery.addBean().
 * Synthetic beans are not discovered via classpath scanning but are programmatically
 * registered by portable extensions.
 *
 * <p>Extensions can observe this event to:
 * <ul>
 *   <li>Inspect synthetic bean metadata</li>
 *   <li>Validate synthetic bean configuration</li>
 *   <li>Observe beans added by other extensions</li>
 * </ul>
 *
 * <p><b>Note:</b> Unlike other ProcessBean events (ProcessManagedBean, ProcessProducerMethod),
 * ProcessSyntheticBean does not provide setters or configurators since the bean
 * was already fully configured when it was added.
 *
 * @param <T> the bean type
 * @see ProcessSyntheticBean
 */
public class ProcessSyntheticBeanImpl<T> extends PhaseAware
        implements ProcessSyntheticBean<T>, ObserverInvocationLifecycle {

    private final Bean<T> bean;
    private final Annotated annotated;
    private final Extension source;
    private final KnowledgeBase knowledgeBase;
    private final ThreadLocal<Boolean> observerInvocationActive = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private final ThreadLocal<Boolean> lifecycleManaged = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * Constructor for ProcessSyntheticBean event.
     *
     * @param bean the synthetic bean
     * @param source the extension that registered this bean (can be null if unknown)
     */
    public ProcessSyntheticBeanImpl(MessageHandler messageHandler, KnowledgeBase knowledgeBase, Bean<T> bean,
                                    Extension source) {
        super(messageHandler);
        this.knowledgeBase = knowledgeBase;
        this.bean = bean;
        this.annotated = resolveAnnotated(bean);
        this.source = source;
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
    public Annotated getAnnotated() {
        assertObserverInvocationActive();
        return annotated;
    }

    @Override
    public Extension getSource() {
        return source;
    }

    @Override
    public void addDefinitionError(Throwable t) {
        assertObserverInvocationActive();
        knowledgeBase.addDefinitionError(Phase.PROCESS_SYNTHETIC_BEAN, "Definition error for " +
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

    private void assertObserverInvocationActive() {
        if (lifecycleManaged.get() && !observerInvocationActive.get()) {
            throw new IllegalStateException(
                    "ProcessSyntheticBean methods may only be called during observer method invocation");
        }
    }

    @Override
    public String toString() {
        return "ProcessSyntheticBean{" +
               "bean=" + bean.getBeanClass().getName() +
               ", types=" + bean.getTypes() +
               ", qualifiers=" + bean.getQualifiers() +
               '}';
    }

    @SuppressWarnings("unchecked")
    private Annotated resolveAnnotated(Bean<T> bean) {
        Class<?> beanClass = bean != null ? bean.getBeanClass() : null;
        if (beanClass == null) {
            beanClass = Object.class;
        }
        return new SimpleAnnotatedType<>((Class<T>) beanClass);
    }
}
