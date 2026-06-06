package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par42memberlevelmetadata.bullet2;

import jakarta.enterprise.context.Dependent;

@Dependent
public class ChildOverridingInheritableMethods extends ParentWithInheritableMethods {

    public static int overridingInitializerCalls;
    public static int overridingObserverCalls;
    public static int overridingPostConstructCalls;
    public static int overridingPreDestroyCalls;

    @Override
    void initializeMember(InheritedMemberDependency dependency) {
        overridingInitializerCalls++;
    }

    @Override
    void onMemberEvent(InheritedMemberEvent event) {
        overridingObserverCalls++;
    }

    @Override
    void afterConstruction() {
        overridingPostConstructCalls++;
    }

    @Override
    void beforeDestruction() {
        overridingPreDestroyCalls++;
    }

    public static void resetState() {
        ParentWithInheritableMethods.resetState();
        overridingInitializerCalls = 0;
        overridingObserverCalls = 0;
        overridingPostConstructCalls = 0;
        overridingPreDestroyCalls = 0;
    }
}
