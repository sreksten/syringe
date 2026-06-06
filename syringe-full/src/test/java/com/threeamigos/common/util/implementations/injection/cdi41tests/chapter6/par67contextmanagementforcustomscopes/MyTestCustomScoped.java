package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par67contextmanagementforcustomscopes;

import jakarta.enterprise.context.NormalScope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@NormalScope
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
public @interface MyTestCustomScoped {
}
