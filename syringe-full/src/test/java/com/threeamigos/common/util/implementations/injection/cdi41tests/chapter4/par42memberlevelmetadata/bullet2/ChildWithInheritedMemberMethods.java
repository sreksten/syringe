package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par42memberlevelmetadata.bullet2;

import jakarta.enterprise.context.Dependent;

@Dependent
public class ChildWithInheritedMemberMethods extends ParentWithInheritableMethods {

    public InheritedMemberDependency getInitializerDependency() {
        return initializerDependency;
    }

    public static void resetState() {
        ParentWithInheritableMethods.resetState();
    }
}
