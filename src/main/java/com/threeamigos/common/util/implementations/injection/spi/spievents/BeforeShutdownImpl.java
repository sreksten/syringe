package com.threeamigos.common.util.implementations.injection.spi.spievents;

import jakarta.enterprise.inject.spi.BeforeShutdown;

/**
 * BeforeShutdown event implementation.
 * 
 * <p>Fired when the container is about to shut down. Extensions can use this event to:
 * <ul>
 *   <li>Perform cleanup operations</li>
 *   <li>Release resources</li>
 *   <li>Log shutdown information</li>
 * </ul>
 *
 * <p>This is the last event fired during the container lifecycle.
 * After this event, all contexts are destroyed and @PreDestroy callbacks are invoked.
 *
 * @see BeforeShutdown
 */
public class BeforeShutdownImpl implements BeforeShutdown {
}
