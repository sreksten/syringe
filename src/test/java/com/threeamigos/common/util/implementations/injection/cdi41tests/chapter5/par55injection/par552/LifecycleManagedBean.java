package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par552;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class LifecycleManagedBean extends LifecycleBaseBean {

    @Inject
    private SubFieldDependency subFieldDependency;

    private boolean subInitializerSawInjectedFields;
    private boolean subPostConstructAfterAllInitializers;

    @Inject
    void initializeSub(SubInitDependency ignored) {
        subInitializerSawInjectedFields = baseFieldDependency != null && subFieldDependency != null;
        events.add("sub-init");
    }

    @PostConstruct
    void postConstructSub() {
        subPostConstructAfterAllInitializers = events.contains("base-init") && events.contains("sub-init");
        events.add("sub-post");
    }

    public SubFieldDependency getSubFieldDependency() {
        return subFieldDependency;
    }

    public boolean isSubInitializerSawInjectedFields() {
        return subInitializerSawInjectedFields;
    }

    public boolean isSubPostConstructAfterAllInitializers() {
        return subPostConstructAfterAllInitializers;
    }
}
