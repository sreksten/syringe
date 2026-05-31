package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par27alternatives.bullet4priority;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;

@Dependent
public class ProducerMethodStereotypePriorityProducerFactory {

    @Produces
    @Alternative
    @PriorityEnabledAlternativeStereotype
    public ProducerMethodStereotypePriorityService alternativeService() {
        return () -> "producerMethodStereotypePriorityAlternative";
    }
}
