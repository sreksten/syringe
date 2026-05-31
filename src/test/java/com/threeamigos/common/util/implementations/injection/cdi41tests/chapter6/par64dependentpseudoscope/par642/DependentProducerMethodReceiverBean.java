package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par64dependentpseudoscope.par642;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

@Dependent
public class DependentProducerMethodReceiverBean {

    @Produces
    ProducedByDependentProducerMethod produce() {
        return new ProducedByDependentProducerMethod();
    }

    @PreDestroy
    void preDestroy() {
        ReceiverInvocationRecorder.producerMethodReceiverDestroyed();
    }
}
