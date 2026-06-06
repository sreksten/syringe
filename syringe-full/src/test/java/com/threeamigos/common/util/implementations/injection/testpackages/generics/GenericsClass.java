package com.threeamigos.common.util.implementations.injection.testpackages.generics;

import com.threeamigos.common.util.Holder;

import jakarta.inject.Inject;

public class GenericsClass {

    @Inject
    Holder<Object1> holder1;

    @Inject
    Holder<Object2> holder2;

    public Holder<Object1> getHolder1() {
        return holder1;
    }

    public Holder<Object2> getHolder2() {
        return holder2;
    }

}
