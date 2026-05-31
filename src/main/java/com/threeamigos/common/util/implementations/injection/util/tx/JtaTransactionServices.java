package com.threeamigos.common.util.implementations.injection.util.tx;

import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;

import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 * JTA-backed TransactionServices using TransactionSynchronizationRegistry.
 */
public class JtaTransactionServices implements TransactionServices {

    private final TransactionSynchronizationRegistry tsr;

    public JtaTransactionServices(TransactionSynchronizationRegistry tsr) {
        this.tsr = tsr;
    }

    /**
     * Attempts to locate TransactionSynchronizationRegistry via JNDI.
     *
     * @return JtaTransactionServices if found, otherwise null
     */
    public static JtaTransactionServices tryCreate() {
        try {
            InitialContext ctx = new InitialContext();
            Object obj = ctx.lookup("java:comp/TransactionSynchronizationRegistry");
            if (obj instanceof TransactionSynchronizationRegistry) {
                return new JtaTransactionServices((TransactionSynchronizationRegistry) obj);
            }
        } catch (NamingException ignored) {
            // TSR not available
        }
        return null;
    }

    @Override
    public boolean isTransactionActive() {
        try {
            int status = tsr.getTransactionStatus();
            return status == Status.STATUS_ACTIVE || status == Status.STATUS_MARKED_ROLLBACK;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void registerSynchronization(TransactionSynchronizationCallbacks callbacks) {
        tsr.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
                try {
                    callbacks.beforeCompletion();
                } catch (Exception ignored) {
                    // Spec: observer exceptions must not affect transaction outcome
                }
            }

            @Override
            public void afterCompletion(int status) {
                boolean committed = status == Status.STATUS_COMMITTED;
                try {
                    callbacks.afterCompletion(committed);
                } catch (Exception ignored) {
                    // Swallow per spec
                }
            }
        });
    }
}
