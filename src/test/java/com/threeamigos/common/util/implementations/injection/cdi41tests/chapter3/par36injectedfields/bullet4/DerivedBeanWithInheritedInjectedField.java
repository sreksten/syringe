package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par36injectedfields.bullet4;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class DerivedBeanWithInheritedInjectedField extends SupportingClassWithInjectedField {

    public InheritedFieldDependency getInheritedDependency() {
        return super.getInheritedDependency();
    }
}

class SupportingClassWithInjectedField {

    @Inject
    InheritedFieldDependency inheritedDependency;

    protected InheritedFieldDependency getInheritedDependency() {
        return inheritedDependency;
    }
}

@Dependent
class InheritedFieldDependency {
}
