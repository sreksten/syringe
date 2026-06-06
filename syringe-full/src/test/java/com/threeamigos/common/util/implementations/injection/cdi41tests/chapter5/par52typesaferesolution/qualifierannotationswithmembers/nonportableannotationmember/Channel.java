package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.qualifierannotationswithmembers.nonportableannotationmember;

import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
public @interface Channel {
    String value();
}
