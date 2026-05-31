package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet1;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

@Dependent
public class ValidVisibilityAndStaticProducerFieldsFactory {

    @Produces
    String packagePrivateValue = "package";

    @Produces
    public Integer publicValue = 1;

    @Produces
    protected Long protectedValue = 2L;

    @Produces
    private Double privateValue = 3.0;

    @Produces
    static Short staticValue = 4;

    @Produces
    Byte nonStaticByteValue = 5;
}
