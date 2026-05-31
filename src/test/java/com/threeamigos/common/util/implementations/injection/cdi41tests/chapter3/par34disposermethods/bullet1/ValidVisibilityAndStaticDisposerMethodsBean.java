package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par34disposermethods.bullet1;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;

@Dependent
public class ValidVisibilityAndStaticDisposerMethodsBean {

    @Produces
    String producePackagePrivate() {
        return "package";
    }

    void disposePackagePrivate(@Disposes String value) {
    }

    @Produces
    public Integer producePublic() {
        return 1;
    }

    public void disposePublic(@Disposes Integer value) {
    }

    @Produces
    protected Long produceProtected() {
        return 2L;
    }

    protected void disposeProtected(@Disposes Long value) {
    }

    @Produces
    private Double producePrivate() {
        return 3.0;
    }

    private void disposePrivate(@Disposes Double value) {
    }

    @Produces
    static Byte produceStatic() {
        return 4;
    }

    static void disposeStatic(@Disposes Byte value) {
    }

    @Produces
    Short produceNonStatic() {
        return 5;
    }

    void disposeNonStatic(@Disposes Short value) {
    }
}
