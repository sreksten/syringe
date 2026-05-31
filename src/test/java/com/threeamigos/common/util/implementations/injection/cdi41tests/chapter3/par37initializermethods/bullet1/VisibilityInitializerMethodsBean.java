package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par37initializermethods.bullet1;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class VisibilityInitializerMethodsBean {

    public static boolean defaultInitializerCalled;
    public static boolean publicInitializerCalled;
    public static boolean protectedInitializerCalled;
    public static boolean privateInitializerCalled;

    @Inject
    void initDefault(DependencyDefault dependency) {
        defaultInitializerCalled = dependency != null;
    }

    @Inject
    public void initPublic(DependencyPublic dependency) {
        publicInitializerCalled = dependency != null;
    }

    @Inject
    protected void initProtected(DependencyProtected dependency) {
        protectedInitializerCalled = dependency != null;
    }

    @Inject
    private void initPrivate(DependencyPrivate dependency) {
        privateInitializerCalled = dependency != null;
    }

    public static void reset() {
        defaultInitializerCalled = false;
        publicInitializerCalled = false;
        protectedInitializerCalled = false;
        privateInitializerCalled = false;
    }

    @Dependent
    static class DependencyDefault {
    }

    @Dependent
    public static class DependencyPublic {
    }

    @Dependent
    protected static class DependencyProtected {
    }

    @Dependent
    private static class DependencyPrivate {
    }
}
