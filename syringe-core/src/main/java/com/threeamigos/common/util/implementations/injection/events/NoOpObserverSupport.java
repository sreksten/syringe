package com.threeamigos.common.util.implementations.injection.events;

import com.threeamigos.common.util.implementations.injection.NotEnabledFeatureException;
import com.threeamigos.common.util.implementations.injection.extensions.ExtensionsManager;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.modules.ModulesEnum;
import com.threeamigos.common.util.implementations.injection.scopes.ContextManager;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.util.TypeLiteral;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.hasObservesAnnotation;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.hasObservesAsyncAnnotation;

/**
 * No-op {@link ObserverSupport} used when {@code syringe-events} is not on the classpath.
 *
 * <p>All event-firing methods are silent no-ops. The {@link #processObserverMethodSpiEvents}
 * method throws {@link NotEnabledFeatureException} if any {@code @Observes} or
 * {@code @ObservesAsync} methods are detected in the discovered classes.
 *
 * <p>{@link #getRootEvent()} and {@link #createEvent} return a no-op {@link Event} that
 * silently discards all fired events.
 */
public class NoOpObserverSupport implements ObserverSupport {

    private static final String FEATURE_UNAVAILABLE = "Event/observer support is not available.";

    private static final ContextTokenProvider NO_OP_CONTEXT_TOKEN_PROVIDER = new ContextTokenProvider() {
        @Override
        public ContextSnapshot capture() {
            return null;
        }
    };

    private KnowledgeBase knowledgeBase;

    @Override
    public void setMessageHandler(MessageHandler messageHandler) {
        // not needed
    }

    @Override
    public void setKnowledgeBase(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    @Override
    public void setBeanManager(BeanManager beanManager) {
        // not needed
    }

    @Override
    public void setContextManager(ContextManager contextManager) {
        // not needed
    }

    @Override
    public void discoverObserverMethods(Class<?> beanClass, Bean<?> declaringBean) {
        // Check if beanClass has any observer methods and throw if so
        for (Method method : beanClass.getDeclaredMethods()) {
            for (Parameter parameter : method.getParameters()) {
                if (hasObservesAnnotation(parameter) || hasObservesAsyncAnnotation(parameter)) {
                    throw new NotEnabledFeatureException(FEATURE_UNAVAILABLE, ModulesEnum.EVENTS);
                }
            }
        }
    }

    @Override
    public void registerRuntimeExtensionObserverMethods(Collection<Extension> extensions) {
        // no-op
    }

    @Override
    public void processObserverMethodSpiEvents(ExtensionsManager extensionsManager) {
        for (Class<?> cls : knowledgeBase.getClasses()) {
            if (hasAnyObserverMethod(cls)) {
                throw new NotEnabledFeatureException(FEATURE_UNAVAILABLE, ModulesEnum.EVENTS);
            }
        }
    }

    private boolean hasAnyObserverMethod(Class<?> cls) {
        Class<?> current = cls;
        while (current != null && !Object.class.equals(current)) {
            for (Method method : current.getDeclaredMethods()) {
                for (Parameter parameter : method.getParameters()) {
                    if (hasObservesAnnotation(parameter) || hasObservesAsyncAnnotation(parameter)) {
                        return true;
                    }
                }
            }
            current = current.getSuperclass();
        }
        return false;
    }

    @Override
    public void fireEvent(Object event, Annotation... qualifiers) {
        // silently ignored
    }

    @Override
    public void fireEventAsync(Object event, Annotation... qualifiers) {
        // silently ignored
    }

    @Override
    public void fireStartupEvent() {
        // silently ignored
    }

    @Override
    public void fireShutdownEvent() {
        // silently ignored
    }

    @Override
    public void fireContextInitializedEvent(Class<? extends Annotation> scopeType) {
        // silently ignored
    }

    @Override
    public void fireContextBeforeDestroyedEvent(Class<? extends Annotation> scopeType) {
        // silently ignored
    }

    @Override
    public void fireContextDestroyedEvent(Class<? extends Annotation> scopeType) {
        // silently ignored
    }

    @Override
    public Event<Object> getRootEvent() {
        return NoOpEvent.INSTANCE;
    }

    @Override
    public <T> Event<T> createEvent(Type payloadType, Set<Annotation> qualifiers) {
        return new NoOpEvent<>();
    }

    @Override
    public ContextTokenProvider getContextTokenProvider() {
        return NO_OP_CONTEXT_TOKEN_PROVIDER;
    }

    @Override
    public void clear() {
        // no-op
    }

    // -------------------------------------------------------------------------
    // No-op Event implementation
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static final class NoOpEvent<T> implements Event<T> {

        @SuppressWarnings("rawtypes")
        static final NoOpEvent INSTANCE = new NoOpEvent<>();

        @Override
        public void fire(T event) {
            // silently ignored
        }

        @Override
        public <U extends T> CompletionStage<U> fireAsync(U event) {
            return java.util.concurrent.CompletableFuture.completedFuture(event);
        }

        @Override
        public <U extends T> CompletionStage<U> fireAsync(U event, NotificationOptions options) {
            return java.util.concurrent.CompletableFuture.completedFuture(event);
        }

        @Override
        public <U extends T> Event<U> select(Class<U> subtype, Annotation... qualifiers) {
            return new NoOpEvent<>();
        }

        @Override
        public <U extends T> Event<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
            return new NoOpEvent<>();
        }

        @Override
        public Event<T> select(Annotation... qualifiers) {
            return this;
        }
    }
}
