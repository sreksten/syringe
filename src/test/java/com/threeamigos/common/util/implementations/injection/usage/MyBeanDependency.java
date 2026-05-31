package com.threeamigos.common.util.implementations.injection.usage;

import jakarta.enterprise.context.ApplicationScoped;

/**
 *
 * @author Stefano Reksten
 */
@ApplicationScoped
public class MyBeanDependency {

    private static int counter = 0;

    public int getCounter() {
        return ++counter;
    }

}
