package com.threeamigos.common.util.implementations.injection.events;

import com.threeamigos.common.util.implementations.injection.extensions.ExtensionsManager;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.scopes.ContextManager;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.InjectionPoint;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Set;

/**
 * Service provider interface for event and observer-method support.
 *
 * <p>Discovered via {@link java.util.ServiceLoader}. If {@code syringe-events} is on the
 * classpath, {@code ObserverSupportImpl} is loaded; otherwise {@link NoOpObserverSupport}
 * is used.
 *
 * <p>The no-op throws
 * {@link com.threeamigos.common.util.implementations.injection.NotEnabledFeatureException}
 * during {@link #processObserverMethodSpiEvents(ExtensionsManager)} if any
 * {@code @Observes} or {@code @ObservesAsync} methods are detected in the discovered
 * classes.
 */
public interface ObserverSupport {

    void setMessageHandler(MessageHandler messageHandler);

    void setKnowledgeBase(KnowledgeBase knowledgeBase);

    /** The full CDI BeanManager is needed for {@code createAnnotatedType()} calls. */
    void setBeanManager(BeanManager beanManager);

    /**
     * The ContextManager is needed for BeanResolver creation and for EventImpl
     * construction when firing lifecycle events.
     */
    void setContextManager(ContextManager contextManager);

    /**
     * Scans {@code beanClass} for {@code @Observes} and {@code @ObservesAsync} parameters
     * and registers the resulting observer metadata entries in the KnowledgeBase.
     *
     * <p>This method is intended to be called per-bean by the bean validator. The no-op
     * throws {@code NotEnabledFeatureException} if any observer annotations are found.
     */
    void discoverObserverMethods(Class<?> beanClass, Bean<?> declaringBean);

    /**
     * Registers runtime observer methods declared on portable extensions.
     */
    void registerRuntimeExtensionObserverMethods(Collection<Extension> extensions);

    /**
     * Fires the {@code ProcessObserverMethod} SPI event for each registered observer,
     * allowing extensions to modify or veto them.
     *
     * <p>Also performs a lightweight fallback discovery scan if the KnowledgeBase does not
     * yet contain observer method metadata (because CDI41InjectionValidator runs later).
     *
     * <p>Called during {@code start()}, after {@code processProducers()}.
     *
     * <p>The no-op throws {@code NotEnabledFeatureException} if any {@code @Observes} or
     * {@code @ObservesAsync} methods are found in any discovered class.
     */
    void processObserverMethodSpiEvents(ExtensionsManager extensionsManager);

    /**
     * Fires a synchronous CDI event with the given payload and optional qualifiers.
     * The no-op silently drops the event.
     */
    void fireEvent(Object event, Annotation... qualifiers);

    /**
     * Fires an asynchronous CDI event with the given payload and optional qualifiers.
     * The no-op silently drops the event.
     */
    void fireEventAsync(Object event, Annotation... qualifiers);

    /**
     * Fires the {@code jakarta.enterprise.event.Startup} lifecycle event.
     * Called at the end of {@code start()}.
     */
    void fireStartupEvent();

    /**
     * Fires the {@code jakarta.enterprise.event.Shutdown} lifecycle event.
     * Called at the start of {@code shutdown()}.
     */
    void fireShutdownEvent();

    /**
     * Fires {@code @Initialized(scopeType)} to all matching observers.
     * Called by ContextManager scope lifecycle listeners. The no-op silently drops it.
     */
    void fireContextInitializedEvent(Class<? extends Annotation> scopeType);

    /**
     * Fires {@code @BeforeDestroyed(scopeType)} to all matching observers.
     * The no-op silently drops it.
     */
    void fireContextBeforeDestroyedEvent(Class<? extends Annotation> scopeType);

    /**
     * Fires {@code @Destroyed(scopeType)} to all matching observers.
     * The no-op silently drops it.
     */
    void fireContextDestroyedEvent(Class<? extends Annotation> scopeType);

    /**
     * Returns the root {@code Event<Object>} instance used as the fallback in
     * {@code BeanManagerImpl.getEvent()}.
     */
    Event<Object> getRootEvent();

    /**
     * Creates a typed {@code Event<T>} for the given payload type and qualifiers.
     * Used by {@code BeanManagerImpl} for {@code @Inject Event<T>} injection-point resolution.
     */
    <T> Event<T> createEvent(Type payloadType, Set<Annotation> qualifiers);

    /**
     * Creates a typed {@code Event<T>} for the given payload type, qualifiers, and owning
     * injection point. Implementations that support it should preserve this metadata so
     * {@link jakarta.enterprise.inject.spi.EventMetadata#getInjectionPoint()} is available to
     * observers fired through injected {@code Event<T>} handles.
     */
    default <T> Event<T> createEvent(Type payloadType,
                                     Set<Annotation> qualifiers,
                                     InjectionPoint firingInjectionPoint) {
        return createEvent(payloadType, qualifiers);
    }

    /**
     * Returns the context-token provider used for event context propagation.
     */
    ContextTokenProvider getContextTokenProvider();

    /**
     * Clears static state (async executor threads, warning flags, etc.).
     * Called during container shutdown's {@code cleanupStaticState()}.
     */
    void clear();
}
