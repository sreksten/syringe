package com.threeamigos.common.util.implementations.injection.usage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 *
 * @author Stefano Reksten
 */
@ApplicationScoped
public class MyBean {

    @Inject
    private MyBeanDependency dependency;

    public void doWork() {
        int currentCounter = dependency.getCounter();
        System.out.println("Current counter: " + currentCounter);
    }
}
