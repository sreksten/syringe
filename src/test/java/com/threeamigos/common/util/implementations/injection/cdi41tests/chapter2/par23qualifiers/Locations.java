package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par23qualifiers;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({ PARAMETER, FIELD, METHOD, TYPE })
@Retention(RUNTIME)
@Documented
public @interface Locations {
    Location[] value();
}