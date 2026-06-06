package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par40introduction.bullet3;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

public abstract class ParentWithInheritedMembers {

    static boolean initializerInvoked;
    static boolean postConstructInvoked;

    @Inject
    InheritedMemberDependency injectedField;

    InheritedMemberDependency initializerDependency;

    @Inject
    void initialize(InheritedMemberDependency dependency) {
        this.initializerDependency = dependency;
        initializerInvoked = true;
    }

    @PostConstruct
    void postConstruct() {
        postConstructInvoked = true;
    }

    static void reset() {
        initializerInvoked = false;
        postConstructInvoked = false;
    }
}
