package com.threeamigos.common.util.implementations.injection.testpackages.scopes;

import jakarta.enterprise.context.ConversationScoped;

import java.io.Serializable;

@ConversationScoped
public class ConversationScopedClass implements Serializable {
    private final long instanceId = System.nanoTime();

    public long getInstanceId() {
        return instanceId;
    }
}
