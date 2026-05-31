package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par64dependentpseudoscope.par642;

public final class ReceiverInvocationRecorder {

    private static int producerMethodReceiverDestroyed;
    private static int producerFieldReceiverDestroyed;
    private static int disposerReceiverDestroyed;
    private static int observerReceiverDestroyed;

    private ReceiverInvocationRecorder() {
    }

    public static synchronized void reset() {
        producerMethodReceiverDestroyed = 0;
        producerFieldReceiverDestroyed = 0;
        disposerReceiverDestroyed = 0;
        observerReceiverDestroyed = 0;
    }

    public static synchronized void producerMethodReceiverDestroyed() {
        producerMethodReceiverDestroyed++;
    }

    public static synchronized void producerFieldReceiverDestroyed() {
        producerFieldReceiverDestroyed++;
    }

    public static synchronized void disposerReceiverDestroyed() {
        disposerReceiverDestroyed++;
    }

    public static synchronized void observerReceiverDestroyed() {
        observerReceiverDestroyed++;
    }

    public static synchronized int producerMethodReceiverDestroyedCount() {
        return producerMethodReceiverDestroyed;
    }

    public static synchronized int producerFieldReceiverDestroyedCount() {
        return producerFieldReceiverDestroyed;
    }

    public static synchronized int disposerReceiverDestroyedCount() {
        return disposerReceiverDestroyed;
    }

    public static synchronized int observerReceiverDestroyedCount() {
        return observerReceiverDestroyed;
    }
}
