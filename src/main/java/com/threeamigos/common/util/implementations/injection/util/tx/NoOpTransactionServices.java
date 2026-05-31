package com.threeamigos.common.util.implementations.injection.util.tx;

/**
 * Fallback implementation used when no transaction manager is available.
 * Transactional observers will be treated as non-transactional (invoked immediately).
 */
public class NoOpTransactionServices implements TransactionServices {
    @Override
    public boolean isTransactionActive() {
        return false;
    }

    @Override
    public void registerSynchronization(TransactionSynchronizationCallbacks callbacks) {
        // No transaction present; nothing to register.
    }
}
