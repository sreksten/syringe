package com.threeamigos.common.util.implementations.injection.spi.spievents;

import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.spi.Phase;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.inject.spi.*;

/**
 * ProcessProducerField event implementation.
 *
 * <p>Fired for each producer field discovered in managed beans.
 * This is a specialized ProcessProducer event specifically for fields annotated with @Produces.
 *
 * <p>Extensions can observe this event to:
 * <ul>
 *   <li>Inspect producer field metadata</li>
 *   <li>Replace the Producer instance via {@link #setProducer(Producer)}</li>
 *   <li>Wrap the Producer to customize field access logic</li>
 *   <li>Add custom validation or logging</li>
 * </ul>
 *
 * @param <T> the class declaring the producer field
 * @param <X> the type of the producer field
 * @see ProcessProducerField
 */
public class ProcessProducerFieldImpl<T, X> extends ProcessProducerImpl<T, X>
        implements ProcessProducerField<T, X> {

    private final Bean<X> bean;
    private final AnnotatedField<T> annotatedField;
    private final AnnotatedParameter<T> disposerParameter;

    /**
     * Constructor for a producer field with a disposer.
     */
    public ProcessProducerFieldImpl(MessageHandler messageHandler, KnowledgeBase knowledgeBase, Bean<X> bean,
                                    AnnotatedField<T> annotatedField, Producer<X> producer,
                                    AnnotatedParameter<T> disposerParameter) {
        super(messageHandler, knowledgeBase, Phase.PROCESS_PRODUCER_FIELD, annotatedField, producer);
        this.bean = bean;
        this.annotatedField = annotatedField;
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
        return annotatedField;
    }

    @Override
    public AnnotatedField<T> getAnnotatedProducerField() {
        return annotatedField;
    }

    @Override
    public AnnotatedParameter<T> getAnnotatedDisposedParameter() {
        return disposerParameter;
    }
}
