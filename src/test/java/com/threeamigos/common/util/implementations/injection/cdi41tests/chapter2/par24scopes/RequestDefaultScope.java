package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par24scopes;

import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Stereotype;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Stereotype
@RequestScoped
@Target(TYPE)
@Retention(RUNTIME)
public @interface RequestDefaultScope {
}
