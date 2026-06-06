package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par64dependentpseudoscope.par64;

import java.util.ArrayList;
import java.util.List;

public final class DependentInvocationRecorder {

    private static final List<String> PRE_DESTROYED_DEPENDENT_IDS = new ArrayList<>();
    private static final List<String> PRODUCER_PARAMETER_DEPENDENT_IDS = new ArrayList<>();
    private static final List<String> DISPOSER_PARAMETER_DEPENDENT_IDS = new ArrayList<>();
    private static final List<String> OBSERVER_PARAMETER_DEPENDENT_IDS = new ArrayList<>();

    private DependentInvocationRecorder() {
    }

    public static synchronized void reset() {
        PRE_DESTROYED_DEPENDENT_IDS.clear();
        PRODUCER_PARAMETER_DEPENDENT_IDS.clear();
        DISPOSER_PARAMETER_DEPENDENT_IDS.clear();
        OBSERVER_PARAMETER_DEPENDENT_IDS.clear();
    }

    public static synchronized void recordPreDestroy(String id) {
        PRE_DESTROYED_DEPENDENT_IDS.add(id);
    }

    public static synchronized void recordProducerParameter(String id) {
        PRODUCER_PARAMETER_DEPENDENT_IDS.add(id);
    }

    public static synchronized void recordDisposerParameter(String id) {
        DISPOSER_PARAMETER_DEPENDENT_IDS.add(id);
    }

    public static synchronized void recordObserverParameter(String id) {
        OBSERVER_PARAMETER_DEPENDENT_IDS.add(id);
    }

    public static synchronized List<String> preDestroyedDependentIds() {
        return new ArrayList<>(PRE_DESTROYED_DEPENDENT_IDS);
    }

    public static synchronized List<String> producerParameterDependentIds() {
        return new ArrayList<>(PRODUCER_PARAMETER_DEPENDENT_IDS);
    }

    public static synchronized List<String> disposerParameterDependentIds() {
        return new ArrayList<>(DISPOSER_PARAMETER_DEPENDENT_IDS);
    }

    public static synchronized List<String> observerParameterDependentIds() {
        return new ArrayList<>(OBSERVER_PARAMETER_DEPENDENT_IDS);
    }
}
