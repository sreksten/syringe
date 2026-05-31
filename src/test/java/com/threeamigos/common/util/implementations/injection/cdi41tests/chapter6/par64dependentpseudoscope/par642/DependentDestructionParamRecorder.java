package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par64dependentpseudoscope.par642;

import java.util.ArrayList;
import java.util.List;

public final class DependentDestructionParamRecorder {

    private static final List<String> PRE_DESTROYED_IDS = new ArrayList<>();
    private static final List<String> DISPOSER_PARAM_IDS = new ArrayList<>();
    private static final List<String> OBSERVER_PARAM_IDS = new ArrayList<>();

    private DependentDestructionParamRecorder() {
    }

    public static synchronized void reset() {
        PRE_DESTROYED_IDS.clear();
        DISPOSER_PARAM_IDS.clear();
        OBSERVER_PARAM_IDS.clear();
    }

    public static synchronized void recordPreDestroy(String id) {
        PRE_DESTROYED_IDS.add(id);
    }

    public static synchronized void recordDisposerParam(String id) {
        DISPOSER_PARAM_IDS.add(id);
    }

    public static synchronized void recordObserverParam(String id) {
        OBSERVER_PARAM_IDS.add(id);
    }

    public static synchronized List<String> preDestroyedIds() {
        return new ArrayList<>(PRE_DESTROYED_IDS);
    }

    public static synchronized List<String> disposerParamIds() {
        return new ArrayList<>(DISPOSER_PARAM_IDS);
    }

    public static synchronized List<String> observerParamIds() {
        return new ArrayList<>(OBSERVER_PARAM_IDS);
    }
}
