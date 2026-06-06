package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par42memberlevelmetadata.bullet3;

public abstract class ParentWithInterceptedMethod {

    @InheritedMethodBinding
    public String ping() {
        return "parent";
    }
}
