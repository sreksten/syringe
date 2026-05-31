package com.threeamigos.common.util.implementations.injection.testpackages.interfaces.noimplementations;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

public class ClassWithFailingInstance {

    @Inject
    private Instance<NoImplementationsInterface> instance;

    public Instance<NoImplementationsInterface> getInstance() {
        return instance;
    }

}
