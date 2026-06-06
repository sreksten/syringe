package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par27alternatives.bullet3;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class AlternativeClient {

    @Inject
    private AlternativeService alternativeService;

    public String serviceType() {
        return alternativeService.type();
    }
}
