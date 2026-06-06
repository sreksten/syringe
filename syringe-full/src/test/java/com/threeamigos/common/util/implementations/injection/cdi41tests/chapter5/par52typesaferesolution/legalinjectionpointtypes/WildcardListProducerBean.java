package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.legalinjectionpointtypes;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

import java.util.Collections;
import java.util.List;

@Dependent
public class WildcardListProducerBean {

    @Produces
    public List<String> produceValues() {
        return Collections.singletonList("value");
    }
}
