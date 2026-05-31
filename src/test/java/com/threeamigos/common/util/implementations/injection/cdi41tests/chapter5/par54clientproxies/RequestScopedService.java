package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par54clientproxies;

import jakarta.enterprise.context.RequestScoped;

import java.util.UUID;

@RequestScoped
public class RequestScopedService {

    private final String instanceId = UUID.randomUUID().toString();

    public String currentRequestInstanceId() {
        return instanceId;
    }
}
