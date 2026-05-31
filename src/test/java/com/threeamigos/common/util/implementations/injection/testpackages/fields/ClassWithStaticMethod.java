package com.threeamigos.common.util.implementations.injection.testpackages.fields;

import jakarta.inject.Inject;

public class ClassWithStaticMethod {

    private static ClassFirstDependency staticField;

    @Inject
    @SuppressWarnings("all")
    public static void initStaticField(ClassFirstDependency staticField) {
        ClassWithStaticMethod.staticField = staticField;
    }

    public static void setStaticField(ClassFirstDependency staticField) {
        ClassWithStaticMethod.staticField = staticField;
    }

    public static ClassFirstDependency getStaticField() {
        return staticField;
    }

}
