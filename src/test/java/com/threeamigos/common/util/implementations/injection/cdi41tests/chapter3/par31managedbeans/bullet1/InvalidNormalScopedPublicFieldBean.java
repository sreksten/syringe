package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par31managedbeans.bullet1;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class InvalidNormalScopedPublicFieldBean {

    public String publicState;
}
