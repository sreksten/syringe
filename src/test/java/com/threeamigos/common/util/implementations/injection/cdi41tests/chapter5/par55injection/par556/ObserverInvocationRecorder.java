package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par556;

import java.util.ArrayList;
import java.util.List;

public final class ObserverInvocationRecorder {

    private static final List<String> EVENTS = new ArrayList<>();

    private ObserverInvocationRecorder() {
    }

    public static synchronized void reset() {
        EVENTS.clear();
    }

    public static synchronized void record(String event) {
        EVENTS.add(event);
    }

    public static synchronized List<String> events() {
        return new ArrayList<>(EVENTS);
    }
}
