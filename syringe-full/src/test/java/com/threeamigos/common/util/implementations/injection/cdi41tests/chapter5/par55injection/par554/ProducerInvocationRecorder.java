package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par554;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProducerInvocationRecorder {

    private static final List<String> EVENTS = new ArrayList<>();

    private ProducerInvocationRecorder() {
    }

    public static synchronized void reset() {
        EVENTS.clear();
    }

    public static synchronized void record(String event) {
        EVENTS.add(event);
    }

    public static synchronized List<String> events() {
        return Collections.unmodifiableList(new ArrayList<String>(EVENTS));
    }
}
