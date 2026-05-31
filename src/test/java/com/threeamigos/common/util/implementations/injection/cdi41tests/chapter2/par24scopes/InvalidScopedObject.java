package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par24scopes;

import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.SessionScoped;

import java.io.Serializable;

@SessionScoped @RequestScoped
public class InvalidScopedObject implements Serializable {
}
