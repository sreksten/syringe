package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par35beanconstructors.bullet2;

import jakarta.enterprise.context.Dependent;

@Dependent
public class NoInjectNoArgConstructorBean {

    public static int noArgConstructorCalls = 0;
    public static int parameterizedConstructorCalls = 0;

    public NoInjectNoArgConstructorBean() {
        noArgConstructorCalls++;
    }

    public NoInjectNoArgConstructorBean(String ignored) {
        parameterizedConstructorCalls++;
    }

    public static void reset() {
        noArgConstructorCalls = 0;
        parameterizedConstructorCalls = 0;
    }
}
