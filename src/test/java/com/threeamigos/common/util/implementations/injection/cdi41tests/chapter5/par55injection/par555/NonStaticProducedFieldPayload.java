package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par555;

public class NonStaticProducedFieldPayload {

    private final String declaringBeanInstanceId;

    public NonStaticProducedFieldPayload(String declaringBeanInstanceId) {
        this.declaringBeanInstanceId = declaringBeanInstanceId;
    }

    public String getDeclaringBeanInstanceId() {
        return declaringBeanInstanceId;
    }
}
