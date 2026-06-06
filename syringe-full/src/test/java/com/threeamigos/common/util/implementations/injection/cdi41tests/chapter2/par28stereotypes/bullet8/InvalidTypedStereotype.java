package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet8;

import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Stereotype;
import jakarta.enterprise.inject.Typed;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@RequestScoped
@Typed(Object.class)
@Stereotype
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface InvalidTypedStereotype {
}
