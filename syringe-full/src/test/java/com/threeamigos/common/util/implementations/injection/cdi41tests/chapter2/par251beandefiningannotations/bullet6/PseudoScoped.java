package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par251beandefiningannotations.bullet6;

import jakarta.inject.Scope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Scope
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PseudoScoped {
}
