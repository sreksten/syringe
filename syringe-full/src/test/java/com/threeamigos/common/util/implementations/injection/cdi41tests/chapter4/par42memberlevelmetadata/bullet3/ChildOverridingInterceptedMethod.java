package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par42memberlevelmetadata.bullet3;

import jakarta.enterprise.context.Dependent;

@Dependent
public class ChildOverridingInterceptedMethod extends ParentWithInterceptedMethod {

    @Override
    public String ping() {
        return "child";
    }
}
