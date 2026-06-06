package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par42memberlevelmetadata.bullet2;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

public abstract class ParentWithInheritableMethods {

    public static int initializerCalls;
    public static int observerCalls;
    public static int postConstructCalls;
    public static int preDestroyCalls;

    InheritedMemberDependency initializerDependency;

    @Inject
    void initializeMember(InheritedMemberDependency dependency) {
        this.initializerDependency = dependency;
        initializerCalls++;
    }

    void onMemberEvent(@Observes InheritedMemberEvent event) {
        observerCalls++;
    }

    @PostConstruct
    void afterConstruction() {
        postConstructCalls++;
    }

    @PreDestroy
    void beforeDestruction() {
        preDestroyCalls++;
    }

    static void resetState() {
        initializerCalls = 0;
        observerCalls = 0;
        postConstructCalls = 0;
        preDestroyCalls = 0;
    }
}
