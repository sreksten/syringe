package com.threeamigos.common.util.implementations.injection.testpackages.superclasses;

import jakarta.inject.Inject;

public class GrandparentClass {

    @Inject
    private FieldClass grandparentFieldClass;

    private FieldClass grandparentFieldClassByMethod;

    public FieldClass getGrandparentFieldClass() {
        return grandparentFieldClass;
    }

    @Inject
    private void setGrandparentFieldClassByMethod(FieldClass grandparentFieldClassByMethod) {
        this.grandparentFieldClassByMethod = grandparentFieldClassByMethod;
    }

    public FieldClass getGrandparentFieldClassInjectedByMethod() {
        return grandparentFieldClassByMethod;
    }

}
