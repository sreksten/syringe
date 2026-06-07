package com.threeamigos.common.util.implementations.injection.coreonly.fixtures.smoke;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@Dependent
public class SmokeRootBean {

    private final ConstructorDependency constructorDependency;

    @Inject
    private FieldDependency fieldDependency;

    private MethodDependency methodDependency;

    @Inject
    private Instance<ProgrammaticDependency> programmaticDependencies;

    @Inject
    public SmokeRootBean(ConstructorDependency constructorDependency) {
        this.constructorDependency = constructorDependency;
    }

    @Inject
    void setMethodDependency(MethodDependency methodDependency) {
        this.methodDependency = methodDependency;
    }

    public ConstructorDependency getConstructorDependency() {
        return constructorDependency;
    }

    public FieldDependency getFieldDependency() {
        return fieldDependency;
    }

    public MethodDependency getMethodDependency() {
        return methodDependency;
    }

    public Instance<ProgrammaticDependency> getProgrammaticDependencies() {
        return programmaticDependencies;
    }
}
