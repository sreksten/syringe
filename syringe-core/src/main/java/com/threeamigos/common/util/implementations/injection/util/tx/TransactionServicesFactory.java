package com.threeamigos.common.util.implementations.injection.util.tx;

/**
 * Creates TransactionServices instances, preferring JTA when available.
 */
public final class TransactionServicesFactory {
    private TransactionServicesFactory() {}

    public static TransactionServices create() {
        JtaTransactionServices jta = JtaTransactionServices.tryCreate();
        if (jta != null) {
            return jta;
        }
        return new NoOpTransactionServices();
    }
}
