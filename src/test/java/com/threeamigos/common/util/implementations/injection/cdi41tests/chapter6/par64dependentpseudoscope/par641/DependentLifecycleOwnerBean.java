package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par64dependentpseudoscope.par641;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class DependentLifecycleOwnerBean {

    @Inject
    private TrackedDependentObject fieldObject;

    private final TrackedDependentObject constructorObject;
    private TrackedDependentObject initializerObject;

    @Inject
    public DependentLifecycleOwnerBean(TrackedDependentObject constructorObject) {
        this.constructorObject = constructorObject;
    }

    @Inject
    void init(TrackedDependentObject initializerObject) {
        this.initializerObject = initializerObject;
    }

    public String fieldId() {
        return fieldObject.id();
    }

    public String constructorId() {
        return constructorObject.id();
    }

    public String initializerId() {
        return initializerObject.id();
    }
}
