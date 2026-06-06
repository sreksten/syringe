package com.threeamigos.common.util.implementations.injection.testpackages.fields;

import jakarta.inject.Inject;

public class ClassWithStaticField {

    @Inject
    @SuppressWarnings("all")
    private static ClassFirstDependency staticField;

    public void setStaticField(ClassFirstDependency staticField) {
        ClassWithStaticField.staticField = staticField;
    }

    public ClassFirstDependency getStaticField() {
        return staticField;
    }
}
