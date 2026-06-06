package com.threeamigos.common.util.implementations.injection.testpackages.superclasses;

import jakarta.inject.Inject;

public class ParentClass extends GrandparentClass {

    @Inject
    private FieldClass parentFieldClass;

    private FieldClass parentFieldClassByMethod;

    public FieldClass getParentFieldClass() {
        return parentFieldClass;
    }

    @Inject
    protected void setParentFieldClassByMethod(FieldClass parentFieldClassByMethod) {
        this.parentFieldClassByMethod = parentFieldClassByMethod;
    }

    public FieldClass getParentFieldClassInjectedByMethod() {
        return parentFieldClassByMethod;
    }

}
