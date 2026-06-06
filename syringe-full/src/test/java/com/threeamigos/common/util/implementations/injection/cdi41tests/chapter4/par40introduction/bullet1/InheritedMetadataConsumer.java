package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par40introduction.bullet1;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class InheritedMetadataConsumer {

    @Inject
    @InheritedQualifier
    InheritedQualifiedChildBean bean;

    public InheritedQualifiedChildBean getBean() {
        return bean;
    }
}
