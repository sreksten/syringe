package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par40introduction.bullet6;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class SpecializationConsumer {

    @Inject
    SpecializedBaseBean bean;

    public SpecializedBaseBean getBean() {
        return bean;
    }
}
