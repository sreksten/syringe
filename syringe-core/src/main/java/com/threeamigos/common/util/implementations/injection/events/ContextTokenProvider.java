package com.threeamigos.common.util.implementations.injection.events;

/**
 * Provider of context snapshot tokens for event-dispatch context propagation.
 */
public interface ContextTokenProvider {
    ContextSnapshot capture();
}
