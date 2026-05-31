package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet12;

public class MultiParameterProduct {

    private final String signature;

    public MultiParameterProduct(String signature) {
        this.signature = signature;
    }

    public String getSignature() {
        return signature;
    }
}
