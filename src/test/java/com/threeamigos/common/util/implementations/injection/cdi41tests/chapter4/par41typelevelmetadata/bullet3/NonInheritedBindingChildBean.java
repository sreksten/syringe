package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par41typelevelmetadata.bullet3;

import jakarta.enterprise.context.Dependent;

@Dependent
public class NonInheritedBindingChildBean extends NonInheritedBindingParentBean {

    @Override
    public String ping() {
        return "child";
    }
}
