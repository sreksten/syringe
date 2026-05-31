package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.legalinjectionpointtypes;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

import java.util.List;

@Dependent
public class WildcardInjectionConsumerBean {

    @Inject
    private List<?> values;

    public List<?> getValues() {
        return values;
    }
}
