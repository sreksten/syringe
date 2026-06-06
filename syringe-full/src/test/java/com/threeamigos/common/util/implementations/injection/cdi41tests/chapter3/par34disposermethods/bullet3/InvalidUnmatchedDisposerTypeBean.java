package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par34disposermethods.bullet3;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;

@Dependent
public class InvalidUnmatchedDisposerTypeBean {

    @Produces
    Integer produceNumber() {
        return 7;
    }

    void disposeText(@Disposes String value) {
    }
}
