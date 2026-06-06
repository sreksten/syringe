package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par5611handleinterface;

import com.threeamigos.common.util.implementations.injection.resolution.InstanceImpl;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("5.6.1.1 - The Handle interface")
public class HandleInterfaceTest {

    @Test
    @DisplayName("5.6.1.1 - Handle.get() returns lazily initialized contextual reference")
    void shouldReturnLazilyInitializedHandle() {
        AtomicInteger resolveCalls = new AtomicInteger(0);
        InstanceImpl<HandlePayload> instance = testInstance(resolveCalls, new AtomicInteger(0));

        Instance.Handle<HandlePayload> handle = instance.getHandle();
        assertEquals(0, resolveCalls.get());

        HandlePayload payload = handle.get();
        assertEquals("handle-payload", payload.value());
        assertEquals(1, resolveCalls.get());
    }

    @Test
    @DisplayName("5.6.1.1 - Handle.getBean() returns Bean metadata for the contextual instance")
    void shouldExposeBeanMetadataThroughHandle() {
        InstanceImpl<HandlePayload> instance = testInstance(new AtomicInteger(0), new AtomicInteger(0));
        Instance.Handle<HandlePayload> handle = instance.getHandle();
        jakarta.enterprise.inject.spi.Bean<HandlePayload> bean = handle.getBean();

        assertNotNull(bean);
        assertEquals(HandlePayload.class, bean.getBeanClass());
    }

    @Test
    @DisplayName("5.6.1.1 - Handle.get() throws IllegalStateException after successful destroy")
    void shouldThrowIllegalStateAfterHandleDestroy() {
        InstanceImpl<HandlePayload> instance = testInstance(new AtomicInteger(0), new AtomicInteger(0));
        Instance.Handle<HandlePayload> handle = instance.getHandle();
        handle.get();
        handle.destroy();

        assertThrows(IllegalStateException.class, handle::get);
    }

    @Test
    @DisplayName("5.6.1.1 - Handle.destroy() is a no-op when get() was never called")
    void shouldNoOpDestroyWhenHandleNeverInitialized() {
        AtomicInteger resolveCalls = new AtomicInteger(0);
        AtomicInteger destroyCalls = new AtomicInteger(0);
        InstanceImpl<HandlePayload> instance = testInstance(resolveCalls, destroyCalls);
        Instance.Handle<HandlePayload> handle = instance.getHandle();
        handle.destroy();
        handle.destroy();

        assertEquals(0, resolveCalls.get());
        assertEquals(0, destroyCalls.get());
    }

    @Test
    @DisplayName("5.6.1.1 - Handle.destroy() is idempotent when called multiple times")
    void shouldDestroyHandleContextualReferenceOnlyOnce() {
        AtomicInteger resolveCalls = new AtomicInteger(0);
        AtomicInteger destroyCalls = new AtomicInteger(0);
        InstanceImpl<HandlePayload> instance = testInstance(resolveCalls, destroyCalls);
        Instance.Handle<HandlePayload> handle = instance.getHandle();
        handle.get();
        handle.destroy();
        handle.destroy();

        assertEquals(1, destroyCalls.get());
    }

    @Test
    @DisplayName("5.6.1.1 - Handle.close() delegates to destroy()")
    void shouldDelegateCloseToDestroy() throws Exception {
        AtomicInteger resolveCalls = new AtomicInteger(0);
        AtomicInteger destroyCalls = new AtomicInteger(0);
        InstanceImpl<HandlePayload> instance = testInstance(resolveCalls, destroyCalls);
        Instance.Handle<HandlePayload> handle = instance.getHandle();
        handle.get();
        handle.close();

        assertEquals(1, destroyCalls.get());
        assertThrows(IllegalStateException.class, handle::get);
    }

    private InstanceImpl<HandlePayload> testInstance(AtomicInteger resolveCalls, AtomicInteger destroyCalls) {
        return new InstanceImpl<>(
                HandlePayload.class,
                java.util.Collections.<java.lang.annotation.Annotation>emptyList(),
                new InstanceImpl.ResolutionStrategy<HandlePayload>() {
                    @Override
                    public HandlePayload resolveInstance(Class<HandlePayload> type, java.util.Collection<java.lang.annotation.Annotation> qualifiers) {
                        resolveCalls.incrementAndGet();
                        return new HandlePayload();
                    }

                    @Override
                    public java.util.Collection<Class<? extends HandlePayload>> resolveImplementations(
                            Class<HandlePayload> type,
                            java.util.Collection<java.lang.annotation.Annotation> qualifiers
                    ) {
                        return java.util.Collections.<Class<? extends HandlePayload>>singletonList(HandlePayload.class);
                    }

                    @Override
                    public void invokePreDestroy(HandlePayload instance) {
                        destroyCalls.incrementAndGet();
                    }
                }
        );
    }

    private static class HandlePayload {
        String value() {
            return "handle-payload";
        }
    }
}
