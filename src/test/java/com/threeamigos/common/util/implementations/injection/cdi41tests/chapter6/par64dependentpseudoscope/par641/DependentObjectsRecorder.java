package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par64dependentpseudoscope.par641;

import java.util.ArrayList;
import java.util.List;

public final class DependentObjectsRecorder {

    private static final List<String> PRE_DESTROY_IDS = new ArrayList<>();
    private static final List<String> INTERCEPTOR_IDS = new ArrayList<>();
    private static final List<String> PRODUCER_PARAM_IDS = new ArrayList<>();

    private DependentObjectsRecorder() {
    }

    public static synchronized void reset() {
        PRE_DESTROY_IDS.clear();
        INTERCEPTOR_IDS.clear();
        PRODUCER_PARAM_IDS.clear();
    }

    public static synchronized void recordPreDestroy(String id) {
        PRE_DESTROY_IDS.add(id);
    }

    public static synchronized void recordInterceptor(String id) {
        INTERCEPTOR_IDS.add(id);
    }

    public static synchronized void recordProducerParam(String id) {
        PRODUCER_PARAM_IDS.add(id);
    }

    public static synchronized List<String> preDestroyIds() {
        return new ArrayList<>(PRE_DESTROY_IDS);
    }

    public static synchronized List<String> interceptorIds() {
        return new ArrayList<>(INTERCEPTOR_IDS);
    }

    public static synchronized List<String> producerParamIds() {
        return new ArrayList<>(PRODUCER_PARAM_IDS);
    }
}
