package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.ambiguityresolution.nonalternativeelimination;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;

@Dependent
@Alternative
@Priority(200)
public class AlternativeProducerOwner {

    @Produces
    public ResolutionService produceService() {
        return new ProducedByAlternativeProducerService();
    }
}
