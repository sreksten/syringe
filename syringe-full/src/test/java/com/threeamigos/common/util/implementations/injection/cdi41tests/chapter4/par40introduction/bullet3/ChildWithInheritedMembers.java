package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par40introduction.bullet3;

import jakarta.enterprise.context.Dependent;

@Dependent
public class ChildWithInheritedMembers extends ParentWithInheritedMembers {

    public InheritedMemberDependency getInjectedField() {
        return injectedField;
    }

    public InheritedMemberDependency getInitializerDependency() {
        return initializerDependency;
    }

    public static boolean isInitializerInvoked() {
        return initializerInvoked;
    }

    public static boolean isPostConstructInvoked() {
        return postConstructInvoked;
    }

    public static void resetState() {
        reset();
    }
}
