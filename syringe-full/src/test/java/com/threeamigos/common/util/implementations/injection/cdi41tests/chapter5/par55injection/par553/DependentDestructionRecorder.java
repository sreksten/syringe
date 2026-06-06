package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par553;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DependentDestructionRecorder {

    private static final List<String> EVENTS = new ArrayList<>();
    private static boolean childDestroyed;

    private DependentDestructionRecorder() {
    }

    public static synchronized void reset() {
        EVENTS.clear();
        childDestroyed = false;
    }

    public static synchronized void record(String event) {
        EVENTS.add(event);
    }

    public static synchronized List<String> events() {
        return Collections.unmodifiableList(new ArrayList<>(EVENTS));
    }

    public static synchronized void markChildDestroyed() {
        childDestroyed = true;
    }

    public static synchronized boolean isChildDestroyed() {
        return childDestroyed;
    }
}
