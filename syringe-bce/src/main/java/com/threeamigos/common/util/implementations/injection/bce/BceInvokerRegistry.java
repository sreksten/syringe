package com.threeamigos.common.util.implementations.injection.bce;

import jakarta.enterprise.inject.build.compatible.spi.InvokerInfo;
import jakarta.enterprise.invoke.Invoker;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for build-compatible invoker tokens.
 *
 * <p>Maps opaque {@link InvokerInfo} tokens to runtime {@link Invoker} instances.
 */
public class BceInvokerRegistry {

    private final Map<String, Invoker<?, ?>> invokers = new ConcurrentHashMap<>();

    public InvokerInfo register(Invoker<?, ?> invoker) {
        if (invoker == null) {
            throw new IllegalArgumentException("invoker cannot be null");
        }
        String id = UUID.randomUUID().toString();
        invokers.put(id, invoker);
        return new BceInvokerInfo(id);
    }

    public Invoker<?, ?> resolve(InvokerInfo invokerInfo) {
        if (!(invokerInfo instanceof BceInvokerInfo)) {
            throw new IllegalArgumentException("Unsupported InvokerInfo implementation: " +
                (invokerInfo != null ? invokerInfo.getClass().getName() : "null"));
        }
        BceInvokerInfo token = (BceInvokerInfo) invokerInfo;
        Invoker<?, ?> invoker = invokers.get(token.id());
        if (invoker == null) {
            throw new IllegalArgumentException("Unknown InvokerInfo token: " + token.id());
        }
        return invoker;
    }

    public void clear() {
        invokers.clear();
    }
}
