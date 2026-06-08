package com.threeamigos.common.util.implementations.injection.spi.support;

/**
 * Contract for synthetic beans that expose alternative priority metadata.
 */
public interface SyntheticBeanPriority extends SyntheticBeanMarker {

    Integer getPriority();
}
