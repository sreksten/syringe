package com.threeamigos.common.util.implementations.injection.testpackages.superclasses;

import jakarta.inject.Inject;

public class MyClass extends ParentClass {

    @Inject
    private FieldClass fieldClass;

    private FieldClass fieldClassByMethod;

    public FieldClass getFieldClass() {
        return fieldClass;
    }

    @Inject
    public void setFieldClassByMethod(FieldClass fieldClassByMethod) {
        this.fieldClassByMethod = fieldClassByMethod;
    }

    public FieldClass getFieldClassInjectedByMethod() {
        return fieldClassByMethod;
    }

}
