package com.threeamigos.common.util.implementations.injection.coreonly.fixtures.negative.observers;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;

@Dependent
public class ObserverUsageBean {

    public void onMessage(@Observes String message) {
        // no-op
    }
}
