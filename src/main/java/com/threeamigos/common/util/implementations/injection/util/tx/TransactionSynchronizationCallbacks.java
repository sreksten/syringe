package com.threeamigos.common.util.implementations.injection.util.tx;

/**
 * Callbacks mirroring JTA Synchronization for container-agnostic transaction integration.
 */
public interface TransactionSynchronizationCallbacks {

    /**
     * Invoked before transaction completion.
     */
    void beforeCompletion();

    /**
     * Invoked after transaction completion.
     *
     * @param committed true if the transaction committed, false otherwise
     */
    void afterCompletion(boolean committed);
}
