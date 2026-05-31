package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par64dependentpseudoscope.par641;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@ApplicationScoped
public class InstanceLookupOwnerBean {

    @Inject
    private Instance<TrackedDependentObject> instance;

    public TrackedDependentObject getFromInstance() {
        return instance.get();
    }

    public void destroyFromSameInstance(TrackedDependentObject bean) {
        instance.destroy(bean);
    }
}
