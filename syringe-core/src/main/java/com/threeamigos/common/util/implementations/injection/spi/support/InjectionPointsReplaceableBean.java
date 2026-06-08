package com.threeamigos.common.util.implementations.injection.spi.support;

import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InjectionPoint;

import java.util.Set;

/**
 * Contract for synthetic beans that can be rebuilt with updated injection points.
 */
public interface InjectionPointsReplaceableBean {

    Bean<?> withInjectionPoints(Set<InjectionPoint> injectionPoints);
}
