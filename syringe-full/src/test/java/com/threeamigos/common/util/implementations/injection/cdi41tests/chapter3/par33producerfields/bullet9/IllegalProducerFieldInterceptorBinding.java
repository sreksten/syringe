package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet9;

import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface IllegalProducerFieldInterceptorBinding {
}
