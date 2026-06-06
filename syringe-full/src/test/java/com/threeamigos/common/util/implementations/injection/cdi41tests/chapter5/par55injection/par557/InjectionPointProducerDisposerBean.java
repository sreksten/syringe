package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par557;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;

@Dependent
public class InjectionPointProducerDisposerBean {

    @Produces
    public ProducedInjectionPointPayload produce(InjectionPoint injectionPoint) {
        if (injectionPoint == null) {
            InjectionPointProducerDisposerRecorder.record("producer-member:null");
            InjectionPointProducerDisposerRecorder.record("producer-bean:null");
            InjectionPointProducerDisposerRecorder.record("producer-type:null");
        } else {
            InjectionPointProducerDisposerRecorder.record("producer-member:" + injectionPoint.getMember().getName());
            InjectionPointProducerDisposerRecorder.record("producer-bean:" +
                    (injectionPoint.getBean() == null ? "null" : injectionPoint.getBean().getBeanClass().getSimpleName()));
            InjectionPointProducerDisposerRecorder.record("producer-type:" + injectionPoint.getType().getTypeName());
        }
        return new ProducedInjectionPointPayload("produced");
    }

    public void dispose(@Disposes ProducedInjectionPointPayload payload) {
        InjectionPointProducerDisposerRecorder.record("disposer-member:dispose");
    }
}
