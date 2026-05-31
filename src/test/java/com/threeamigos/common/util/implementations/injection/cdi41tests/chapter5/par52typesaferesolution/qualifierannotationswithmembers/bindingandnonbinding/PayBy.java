package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.qualifierannotationswithmembers.bindingandnonbinding;

import jakarta.enterprise.util.Nonbinding;
import jakarta.inject.Qualifier;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Qualifier
@Retention(RUNTIME)
@Target({TYPE, FIELD, METHOD, PARAMETER})
public @interface PayBy {
    PaymentMethod value();

    @Nonbinding
    String comment() default "";
}
