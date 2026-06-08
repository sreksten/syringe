package com.threeamigos.common.util.implementations.injection.spi.configurators;

import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.Producer;
import jakarta.enterprise.inject.spi.configurator.ProducerConfigurator;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Implementation of ProducerConfigurator for customizing Producer instances.
 *
 * <p>Provides a fluent API for configuring producer behavior.
 *
 * @param <T> the type produced by the producer
 * @see ProducerConfigurator
 */
public class ProducerConfiguratorImpl<T> implements ProducerConfigurator<T> {

    private final Producer<T> originalProducer;
    @SuppressWarnings("rawtypes")
    private Function produceCallback;
    private Consumer<T> disposeCallback;
    private Set<InjectionPoint> injectionPoints;

    public ProducerConfiguratorImpl(Producer<T> originalProducer) {
        if (originalProducer == null) {
            throw new IllegalArgumentException("Original producer cannot be null");
        }
        this.originalProducer = originalProducer;
        this.injectionPoints = new HashSet<>(originalProducer.getInjectionPoints());
    }

    @Override
    public <U extends T> ProducerConfigurator<T> produceWith(Function<CreationalContext<U>, U> callback) {
        this.produceCallback = callback;
        return this;
    }

    @Override
    public ProducerConfigurator<T> disposeWith(Consumer<T> callback) {
        this.disposeCallback = callback;
        return this;
    }

    public ProducerConfigurator<T> injectionPoints(InjectionPoint... injectionPoints) {
        this.injectionPoints = new HashSet<>();
        if (injectionPoints != null) {
            for (InjectionPoint ip : injectionPoints) {
                if (ip != null) {
                    this.injectionPoints.add(ip);
                }
            }
        }
        return this;
    }

    public ProducerConfigurator<T> injectionPoints(Set<InjectionPoint> injectionPoints) {
        this.injectionPoints = new HashSet<>();
        if (injectionPoints != null) {
            this.injectionPoints.addAll(injectionPoints);
        }
        return this;
    }

    public ProducerConfigurator<T> addInjectionPoint(InjectionPoint injectionPoint) {
        if (injectionPoint != null) {
            this.injectionPoints.add(injectionPoint);
        }
        return this;
    }

    public ProducerConfigurator<T> addInjectionPoints(InjectionPoint... injectionPoints) {
        if (injectionPoints != null) {
            for (InjectionPoint ip : injectionPoints) {
                if (ip != null) {
                    this.injectionPoints.add(ip);
                }
            }
        }
        return this;
    }

    public ProducerConfigurator<T> addInjectionPoints(Set<InjectionPoint> injectionPoints) {
        if (injectionPoints != null) {
            this.injectionPoints.addAll(injectionPoints);
        }
        return this;
    }

    /**
     * Completes the configuration and returns a configured Producer.
     */
    public Producer<T> complete() {
        return new ConfiguredProducer<>(
            originalProducer,
            produceCallback,
            disposeCallback,
            injectionPoints
        );
    }

    private static class ConfiguredProducer<T> implements Producer<T> {
        private final Producer<T> originalProducer;
        @SuppressWarnings("rawtypes")
        private final Function produceCallback;
        private final Consumer<T> disposeCallback;
        private final Set<InjectionPoint> injectionPoints;

        ConfiguredProducer(
                Producer<T> originalProducer,
                @SuppressWarnings("rawtypes") Function produceCallback,
                Consumer<T> disposeCallback,
                Set<InjectionPoint> injectionPoints) {
            this.originalProducer = originalProducer;
            this.produceCallback = produceCallback;
            this.disposeCallback = disposeCallback;
            this.injectionPoints = new HashSet<>(injectionPoints);
        }

        @Override
        @SuppressWarnings("unchecked")
        public T produce(CreationalContext<T> ctx) {
            if (produceCallback != null) {
                return (T) produceCallback.apply(ctx);
            }
            return originalProducer.produce(ctx);
        }

        @Override
        public void dispose(T instance) {
            if (disposeCallback != null) {
                disposeCallback.accept(instance);
            } else {
                originalProducer.dispose(instance);
            }
        }

        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            return injectionPoints;
        }
    }
}
