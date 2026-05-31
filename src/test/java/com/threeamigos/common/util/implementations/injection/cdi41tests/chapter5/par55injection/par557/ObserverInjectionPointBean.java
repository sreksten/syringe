package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par557;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.InjectionPoint;

@Dependent
public class ObserverInjectionPointBean {

    void onEvent(@Observes ObserverInjectionPointEvent event, InjectionPoint injectionPoint) {
        if (injectionPoint == null) {
            ObserverInjectionPointRecorder.record("observer-member:null");
            ObserverInjectionPointRecorder.record("observer-bean:null");
            ObserverInjectionPointRecorder.record("observer-type:null");
        } else {
            ObserverInjectionPointRecorder.record("observer-member:" + injectionPoint.getMember().getName());
            ObserverInjectionPointRecorder.record("observer-bean:" +
                    (injectionPoint.getBean() == null ? "null" : injectionPoint.getBean().getBeanClass().getSimpleName()));
            ObserverInjectionPointRecorder.record("observer-type:" + injectionPoint.getType().getTypeName());
        }
        ObserverInjectionPointRecorder.record("observer-event:" + event.getId());
    }
}
