package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par64dependentpseudoscope.par641;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;

@Dependent
public class ProducerMethodOwnerBean {

    @Produces
    ProducedDependentObject produce(TrackedDependentObject producerParam) {
        DependentObjectsRecorder.recordProducerParam(producerParam.id());
        return new ProducedDependentObject(producerParam.id());
    }

    void dispose(@Disposes ProducedDependentObject produced) {
        // no-op
    }
}
