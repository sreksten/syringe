package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par42memberlevelmetadata.bullet1;

import jakarta.enterprise.context.Dependent;

@Dependent
public class ChildWithInheritedInjectedField extends ParentWithInjectedField {

    public InjectedFieldDependency getInheritedDependency() {
        return inheritedDependency;
    }
}
