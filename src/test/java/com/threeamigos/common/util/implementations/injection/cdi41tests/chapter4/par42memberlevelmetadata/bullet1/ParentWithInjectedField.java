package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par42memberlevelmetadata.bullet1;

import jakarta.inject.Inject;

public abstract class ParentWithInjectedField {

    @Inject
    InjectedFieldDependency inheritedDependency;
}
