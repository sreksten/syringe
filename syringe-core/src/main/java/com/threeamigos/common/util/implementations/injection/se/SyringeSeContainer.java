package com.threeamigos.common.util.implementations.injection.se;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.spi.SyringeCDIProvider;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.util.TypeLiteral;

import java.lang.annotation.Annotation;
import java.util.Iterator;
import java.util.stream.Stream;

/**
 * SeContainer wrapper for Syringe.
 */
public class SyringeSeContainer implements SeContainer {

    private final Syringe syringe;
    private final CDI<Object> cdi;
    private volatile boolean running = true;

    public SyringeSeContainer(Syringe syringe) {
        this.syringe = syringe;
        this.cdi = syringe.getCDI();
        SyringeCDIProvider.registerGlobalCDI(cdi);
    }

    @Override
    public void close() {
        ensureRunning();
        try {
            syringe.shutdown();
        } finally {
            running = false;
            SyringeCDIProvider.unregisterGlobalCDI();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public BeanManager getBeanManager() {
        ensureRunning();
        return cdi.getBeanManager();
    }

    @Override
    public Instance<Object> select(Annotation... qualifiers) {
        ensureRunning();
        return cdi.select(qualifiers);
    }

    @Override
    public <U> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
        ensureRunning();
        return cdi.select(subtype, qualifiers);
    }

    @Override
    public <U> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
        ensureRunning();
        return cdi.select(subtype, qualifiers);
    }

    @Override
    public boolean isUnsatisfied() {
        ensureRunning();
        return cdi.isUnsatisfied();
    }

    @Override
    public boolean isAmbiguous() {
        ensureRunning();
        return cdi.isAmbiguous();
    }

    @Override
    public boolean isResolvable() {
        ensureRunning();
        return cdi.isResolvable();
    }

    @Override
    public Object get() {
        ensureRunning();
        return cdi.get();
    }

    @Override
    public @Nonnull Iterator<Object> iterator() {
        ensureRunning();
        return cdi.iterator();
    }

    @Override
    public Stream<Object> stream() {
        ensureRunning();
        return cdi.stream();
    }

    @Override
    public Handle<Object> getHandle() {
        ensureRunning();
        return cdi.getHandle();
    }

    @Override
    public Iterable<? extends Handle<Object>> handles() {
        ensureRunning();
        return cdi.handles();
    }

    @Override
    public Stream<? extends Handle<Object>> handlesStream() {
        ensureRunning();
        return cdi.handlesStream();
    }

    @Override
    public void destroy(Object instance) {
        ensureRunning();
        cdi.destroy(instance);
    }

    private void ensureRunning() {
        if (!running) {
            throw new IllegalStateException("SeContainer is already shut down");
        }
    }
}
