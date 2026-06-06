package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet9;

@Mock
public class MockStrategy implements Strategy {
    @Override
    public String kind() {
        return "mock";
    }
}
