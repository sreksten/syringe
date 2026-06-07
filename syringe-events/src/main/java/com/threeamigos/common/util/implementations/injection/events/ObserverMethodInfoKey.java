package com.threeamigos.common.util.implementations.injection.events;

import java.lang.reflect.Method;

/**
 * Utility for stable observer metadata identity keys.
 */
public class ObserverMethodInfoKey {

    public static String of(ObserverMethodMetadata info) {
        if (info == null) {
            return "";
        }
        String methodKey = "";
        if (info.getObserverMethod() != null) {
            Method m = info.getObserverMethod();
            methodKey = m.getDeclaringClass().getName() + "#" + m.getName() + "/" + m.getParameterCount();
        } else if (info.getSyntheticObserver() != null) {
            methodKey = "synthetic:" + info.getSyntheticObserver().getClass().getName();
        }
        String eventType = String.valueOf(info.getEventType());
        String qualifiers = String.valueOf(info.getQualifiers());
        String declaringBeanClass = info.getDeclaringBean() != null && info.getDeclaringBean().getBeanClass() != null
                ? info.getDeclaringBean().getBeanClass().getName()
                : "";
        return methodKey + "|" + declaringBeanClass + "|" + eventType + "|" + qualifiers + "|" + info.isAsync();
    }
}
