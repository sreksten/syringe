package com.threeamigos.common.util.implementations.injection.spi.spievents;

import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.spi.Phase;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.inject.spi.*;

/**
 * ProcessProducerMethod event implementation.
 *
 * <p>Fired for each producer method discovered in managed beans.
 * This is a specialized ProcessProducer event specifically for methods annotated with @Produces.
 *
 * <p>Extensions can observe this event to:
 * <ul>
 *   <li>Inspect producer method metadata</li>
 *   <li>Replace the Producer instance via {@link #setProducer(Producer)}</li>
 *   <li>Wrap the Producer to customize method invocation logic</li>
 *   <li>Add custom validation or logging</li>
 * </ul>
 *
 * @param <T> the class declaring the producer method
 * @param <X> the return type of the producer method
 * @see ProcessProducerMethod
 */
public class ProcessProducerMethodImpl<T, X> extends ProcessProducerImpl<T, X>
        implements ProcessProducerMethod<T, X> {

    private final Bean<X> bean;
    private final AnnotatedMethod<T> annotatedMethod;
    private final AnnotatedParameter<T> disposerParameter;

    /**
     * Constructor for a producer method with a disposer.
     */
    public ProcessProducerMethodImpl(MessageHandler messageHandler, KnowledgeBase knowledgeBase, Bean<X> bean,
                                     AnnotatedMethod<T> annotatedMethod, Producer<X> producer,
                                     AnnotatedParameter<T> disposerParameter) {
        super(messageHandler, knowledgeBase, Phase.PROCESS_PRODUCER_METHOD, annotatedMethod, producer);
        this.bean = bean;
        this.annotatedMethod = annotatedMethod;
        this.disposerParameter = disposerParameter;
    }

    @Override
    public Bean<X> getBean() {
        assertObserverInvocationActive();
        return bean;
    }

    @Override
    public Annotated getAnnotated() {
        assertObserverInvocationActive();
        return annotatedMethod;
    }

    @Override
    public AnnotatedMethod<T> getAnnotatedProducerMethod() {
        return annotatedMethod;
    }

    @Override
    public AnnotatedParameter<T> getAnnotatedDisposedParameter() {
        return disposerParameter;
    }
}
