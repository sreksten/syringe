package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par37initializermethods.bullet2;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class DerivedBeanWithInheritedInitializerMethod extends SupportingClassWithInitializerMethod {

    public static boolean inheritedInitializerCalled = false;

    static void markInheritedInitializerCalled() {
        inheritedInitializerCalled = true;
    }

    public static void reset() {
        inheritedInitializerCalled = false;
    }

    public InheritedInitializerDependency getInheritedDependency() {
        return super.getInheritedDependency();
    }
}

class SupportingClassWithInitializerMethod {

    InheritedInitializerDependency inheritedDependency;

    @Inject
    void initialize(InheritedInitializerDependency dependency) {
        this.inheritedDependency = dependency;
        DerivedBeanWithInheritedInitializerMethod.markInheritedInitializerCalled();
    }

    InheritedInitializerDependency getInheritedDependency() {
        return inheritedDependency;
    }
}

@Dependent
class InheritedInitializerDependency {
}
