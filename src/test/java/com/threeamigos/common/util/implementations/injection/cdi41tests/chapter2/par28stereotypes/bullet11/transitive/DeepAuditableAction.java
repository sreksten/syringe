package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet11.transitive;

import jakarta.enterprise.inject.Stereotype;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@AuditableAction
@Stereotype
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DeepAuditableAction {
}
