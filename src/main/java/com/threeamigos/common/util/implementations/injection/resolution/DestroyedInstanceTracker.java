package com.threeamigos.common.util.implementations.injection.resolution;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tracks destroyed contextual instances so client/interceptor proxies can reject
 * invocations after destruction.
 */
public final class DestroyedInstanceTracker {

    private static final Map<Object, Boolean> DESTROYED_INSTANCES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private DestroyedInstanceTracker() {
    }

    public static void markDestroyed(Object instance) {
        if (instance != null) {
            DESTROYED_INSTANCES.put(instance, Boolean.TRUE);
        }
    }

    public static boolean isDestroyed(Object instance) {
        return instance != null && DESTROYED_INSTANCES.containsKey(instance);
    }

    public static void clear() {
        DESTROYED_INSTANCES.clear();
    }
}
