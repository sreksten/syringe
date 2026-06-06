package com.threeamigos.common.util.implementations.injection.beansxml;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Helper that computes aggregate ordering for beans.xml enabled interceptors and decorators.
 */
public class BeansXmlOrderingHelper {

    private final Collection<BeansXml> beansXmlConfigurations;

    public BeansXmlOrderingHelper(Collection<BeansXml> beansXmlConfigurations) {
        this.beansXmlConfigurations = beansXmlConfigurations;
    }

    public int getDecoratorOrder(Class<?> decoratorClass) {
        if (decoratorClass == null || beansXmlConfigurations.isEmpty()) {
            return -1;
        }

        Map<String, Integer> orderMap = new LinkedHashMap<>();
        int counter = 0;
        for (BeansXml beansXml : beansXmlConfigurations) {
            if (beansXml.getDecorators() == null || beansXml.getDecorators().isEmpty()) {
                continue;
            }
            for (String className : beansXml.getDecorators().getClasses()) {
                if (!orderMap.containsKey(className)) {
                    orderMap.put(className, counter++);
                }
            }
        }

        return orderMap.getOrDefault(decoratorClass.getName(), -1);
    }

    public int getInterceptorOrder(Class<?> interceptorClass) {
        if (interceptorClass == null || beansXmlConfigurations.isEmpty()) {
            return -1;
        }

        Map<String, Integer> orderMap = new LinkedHashMap<>();
        int counter = 0;
        for (BeansXml beansXml : beansXmlConfigurations) {
            if (beansXml.getInterceptors() == null || beansXml.getInterceptors().isEmpty()) {
                continue;
            }
            for (String className : beansXml.getInterceptors().getClasses()) {
                if (!orderMap.containsKey(className)) {
                    orderMap.put(className, counter++);
                }
            }
        }

        return orderMap.getOrDefault(interceptorClass.getName(), -1);
    }
}
