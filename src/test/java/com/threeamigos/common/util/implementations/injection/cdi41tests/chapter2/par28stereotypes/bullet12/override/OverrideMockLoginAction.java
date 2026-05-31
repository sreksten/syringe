package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet12.override;

import jakarta.enterprise.context.ApplicationScoped;

@OverrideMock
@ApplicationScoped
@OverrideAction
public class OverrideMockLoginAction extends OverrideLoginAction {
}
