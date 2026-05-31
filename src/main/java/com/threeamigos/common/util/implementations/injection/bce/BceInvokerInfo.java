package com.threeamigos.common.util.implementations.injection.bce;

import jakarta.enterprise.inject.build.compatible.spi.InvokerInfo;

import java.util.Objects;

/**
 * Opaque InvokerInfo token implementation used by Syringe BCE runtime.
 */
final class BceInvokerInfo implements InvokerInfo {
    private final String id;

    BceInvokerInfo(String id) {
        this.id = id;
    }

    String id() {
        return id;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BceInvokerInfo)) {
            return false;
        }
        BceInvokerInfo that = (BceInvokerInfo) other;
        return Objects.equals(this.id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "BceInvokerInfo{id='" + id + "'}";
    }
}
