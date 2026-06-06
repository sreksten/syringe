package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par557;

import java.util.ArrayList;
import java.util.List;

public final class InjectionPointProducerDisposerRecorder {

    private static final List<String> EVENTS = new ArrayList<>();

    private InjectionPointProducerDisposerRecorder() {
    }

    public static synchronized void reset() {
        EVENTS.clear();
    }

    public static synchronized void record(String value) {
        EVENTS.add(value);
    }

    public static synchronized List<String> events() {
        return new ArrayList<>(EVENTS);
    }
}
