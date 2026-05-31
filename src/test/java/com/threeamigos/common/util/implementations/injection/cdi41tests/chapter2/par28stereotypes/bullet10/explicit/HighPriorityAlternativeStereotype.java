package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet10.explicit;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Stereotype;
import jakarta.interceptor.Interceptor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Alternative
@Priority(Interceptor.Priority.APPLICATION + 100)
@Stereotype
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface HighPriorityAlternativeStereotype {
}
