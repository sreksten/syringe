package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par41typelevelmetadata.bullet3;

import jakarta.enterprise.context.Dependent;

@Dependent
@InheritedTraceBinding("root")
public class InheritedBindingParentBean {

    public String ping() {
        return "parent";
    }
}
