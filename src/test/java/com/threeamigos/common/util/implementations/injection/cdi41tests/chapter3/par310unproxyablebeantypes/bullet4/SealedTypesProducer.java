package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par310unproxyablebeantypes.bullet4;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class SealedTypesProducer {

//    As for now we support java 8, these tests cannot be run
//
//    @Produces
//    @ApplicationScoped
//    public java.nio.Buffer sealedClassType() {
//        return java.nio.ByteBuffer.allocate(1);
//    }
//
//    @Produces
//    @ApplicationScoped
//    public java.lang.constant.ClassDesc sealedInterfaceType() {
//        return java.lang.constant.ClassDesc.of("java.lang.String");
//    }
}
