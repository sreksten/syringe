package com.threeamigos.common.util.implementations.injection.scopes;

import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;

/**
 * Interface for managing scoped bean instances.
 * Each scope type (ApplicationScoped, RequestScoped, etc.) has its own context implementation.
 *
 * @author Stefano Reksten
 */
public interface ScopeContext {

    /**
     * Gets an existing instance from this scope or creates a new one if needed.
     *
     * @param bean the bean to get/create
     * @param creationalContext the creation context for dependency injection
     * @return the scoped instance
     */
    <T> T get(Bean<T> bean, CreationalContext<T> creationalContext);

    /**
     * Gets an existing instance from this scope without creating a new one.
     *
     * @param bean the bean to get
     * @return the existing instance, or null if not present
     */
    <T> T getIfExists(Bean<T> bean);

    /**
     * Destroys a contextual instance for a specific contextual type, if present.
     * Implementations for normal scopes should remove the instance from the context
     * and jakarta.enterprise.invoke {@link Contextual#destroy(Object, CreationalContext)}
     * on the contextual type with the same CreationalContext used at creation time.
     *
     * @param contextual the contextual type
     */
    default void destroy(Contextual<?> contextual) {
        // Optional operation for scope implementations that support per-contextual destruction.
    }

    /**
     * Destroys all instances in this scope.
     */
    void destroy();

    /**
     * Checks if this scope is active.
     *
     * @return true if the scope is currently active
     */
    boolean isActive();

    /**
     * Checks if this scope supports passivation.
     * <p>
     * Passivation-capable scopes in CDI:
     * - @SessionScoped - beans can be serialized when the session is passivated
     * - @ConversationScoped - beans can be serialized when conversation is passivated
     * <p>
     * Non-passivating scopes:
     * - @ApplicationScoped - lives for the entire application lifetime, no passivation needed
     * - @RequestScoped - short-lived, destroyed at the end of the request
     * - @Dependent - pseudo-scope, lifecycle tied to parent bean
     * <p>
     * Beans in passivation-capable scopes MUST be Serializable.
     *
     * @return true if beans in this scope must support serialization
     */
    boolean isPassivationCapable();
}
