package com.threeamigos.common.util.implementations.injection.spi.spievents;

import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.spi.Phase;
import com.threeamigos.common.util.implementations.injection.spi.configurators.ProducerConfiguratorImpl;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.inject.spi.*;
import jakarta.enterprise.inject.spi.configurator.ProducerConfigurator;

/**
 * ProcessProducer event implementation.
 *
 * <p>Fired for each producer method or field discovered in managed beans.
 * Extensions can observe this event to:
 * <ul>
 *   <li>Inspect producer metadata</li>
 *   <li>Replace the Producer instance via {@link #setProducer(Producer)}</li>
 *   <li>Wrap the Producer to customize production logic</li>
 * </ul>
 *
 * <p>This is the base implementation for both ProcessProducerMethod and ProcessProducerField.
 *
 * @param <T> the class declaring the producer method/field
 * @param <X> the return type of the producer method/field
 * @see ProcessProducer
 */
public class ProcessProducerImpl<T, X> extends PhaseAware
        implements ProcessProducer<T, X>, ObserverInvocationLifecycle {

    private final KnowledgeBase knowledgeBase;
    private final Phase phase;
    private final AnnotatedMember<T> annotatedMember;
    private Producer<X> producer;
    private ProducerConfiguratorImpl<X> configurator;
    private final ThreadLocal<Boolean> observerInvocationActive = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private final ThreadLocal<Boolean> lifecycleManaged = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private final ThreadLocal<Boolean> setCalledInCurrentInvocation = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private final ThreadLocal<Boolean> configureCalledInCurrentInvocation = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public ProcessProducerImpl(MessageHandler messageHandler, KnowledgeBase knowledgeBase, Phase phase,
                               AnnotatedMember<T> annotatedMember, Producer<X> producer) {
        super(messageHandler);
        checkNotNull(annotatedMember, "AnnotatedMember");
        checkNotNull(producer, "Producer");
        this.knowledgeBase = knowledgeBase;
        this.phase = phase;
        this.annotatedMember = annotatedMember;
        this.producer = producer;
    }

    @Override
    public AnnotatedMember<T> getAnnotatedMember() {
        assertObserverInvocationActive();
        return annotatedMember;
    }

    @Override
    public Producer<X> getProducer() {
        assertObserverInvocationActive();
        return producer;
    }

    @Override
    public void setProducer(Producer<X> producer) {
        assertObserverInvocationActive();
        if (configureCalledInCurrentInvocation.get()) {
            throw new IllegalStateException(
                    "setProducer() and configureProducer() cannot both be used in the same observer invocation");
        }
        checkNotNull(producer, "Producer");
        info(phase, "Changing Producer for " + annotatedMember.getJavaMember().getName());
        this.producer = producer;
        setCalledInCurrentInvocation.set(Boolean.TRUE);
    }

    @Override
    public ProducerConfigurator<X> configureProducer() {
        assertObserverInvocationActive();
        if (setCalledInCurrentInvocation.get()) {
            throw new IllegalStateException(
                    "setProducer() and configureProducer() cannot both be used in the same observer invocation");
        }
        configureCalledInCurrentInvocation.set(Boolean.TRUE);
        info(phase, "Configuring Producer for " + annotatedMember.getJavaMember().getName());
        if (configurator == null) {
            configurator = new ProducerConfiguratorImpl<>(producer);
        }
        return configurator;
    }

    @Override
    public void addDefinitionError(Throwable t) {
        assertObserverInvocationActive();
        knowledgeBase.addDefinitionError(phase, "Definition error for " + annotatedMember.getJavaMember().getName(), t);
    }

    /**
     * Returns the final Producer after extensions may have wrapped/replaced it.
     */
    public Producer<X> getFinalProducer() {
        return producer;
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
            producer = configurator.complete();
        }
        observerInvocationActive.set(Boolean.FALSE);
        setCalledInCurrentInvocation.set(Boolean.FALSE);
        configureCalledInCurrentInvocation.set(Boolean.FALSE);
        configurator = null;
    }

    protected void assertObserverInvocationActive() {
        if (lifecycleManaged.get() && !observerInvocationActive.get()) {
            throw new IllegalStateException(
                    "ProcessProducer methods may only be called during observer method invocation");
        }
    }
}
