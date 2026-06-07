package com.threeamigos.common.util.implementations.injection.spi;

import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.CDIProvider;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/**
 * CDI Provider implementation for the Syringe container.
 *
 * <p>This provider is discovered via ServiceLoader and enables static container access
 * through CDI.current(). When CDI.current() is called, the CDI class uses ServiceLoader
 * to discover all CDIProvider implementations and calls getCDI() on each until one
 * returns a non-null value.
 *
 * <p><b>ServiceLoader Registration:</b>
 * <p>This provider is registered in META-INF/services/jakarta.enterprise.inject.spi.CDIProvider
 *
 * <p><b>Usage:</b>
 * <pre>
 * // Get BeanManager statically
 * BeanManager manager = CDI.current().getBeanManager();
 *
 * // Perform programmatic lookup
 * MyBean bean = CDI.current().select(MyBean.class).get();
 * </pre>
 *
 * <p><b>CDI 4.1 Compliance:</b>
 * <ul>
 *   <li>Section 12.1: Accessing the BeanManager via CDI.current()</li>
 *   <li>Section 12.2: CDI provider discovery via ServiceLoader</li>
 * </ul>
 *
 * @author Stefano Reksten
 */
public class SyringeCDIProvider implements CDIProvider {

    /**
     * Thread-local storage for the current CDI instance.
     * This allows multiple containers to coexist (useful for testing).
     */
    private static final ThreadLocal<CDI<Object>> CURRENT_CDI = new ThreadLocal<>();

    /**
     * Global CDI instance (used when thread-local is not set).
     */
    private static volatile CDI<Object> globalCDI;
    private static final SyringeCDIProvider PROVIDER_INSTANCE = new SyringeCDIProvider();

    /**
     * Returns the CDI instance for the current context.
     *
     * <p>This method is called by CDI.current() to get the container instance.
     * Returns null if no container has been registered, indicating that another
     * provider should be tried.
     *
     * @return The CDI instance, or null if no container is available
     */
    @Override
    public CDI<Object> getCDI() {
        // Check thread-local first (for testing scenarios with multiple containers)
        CDI<Object> threadLocalCDI = CURRENT_CDI.get();
        if (threadLocalCDI != null) {
            return threadLocalCDI;
        }

        // Fall back to global instance
        return globalCDI;
    }

    /**
     * Returns the priority of this provider.
     *
     * <p>Uses the default priority (100). If multiple providers are available,
     * the one with the highest priority is used first.
     *
     * @return The priority (100)
     */
    @Override
    public int getPriority() {
        return DEFAULT_CDI_PROVIDER_PRIORITY;
    }

    /**
     * Registers a CDI instance globally.
     *
     * <p>This should be called during container initialization (in Syringe.initialize()
     * or InjectorImpl2 constructor) to make the container available via CDI.current().
     *
     * @param cdi The CDI instance to register
     */
    public static void registerGlobalCDI(CDI<Object> cdi) {
        globalCDI = cdi;
    }

    /**
     * Unregisters the global CDI instance.
     *
     * <p>This should be called during container shutdown to clean up resources.
     */
    public static void unregisterGlobalCDI() {
        globalCDI = null;
    }

    /**
     * Registers a CDI instance for the current thread.
     *
     * <p>This is useful for testing scenarios where multiple containers need to
     * coexist in the same JVM. The thread-local CDI takes precedence over the
     * global CDI.
     *
     * @param cdi The CDI instance to register for this thread
     */
    public static void registerThreadLocalCDI(CDI<Object> cdi) {
        CURRENT_CDI.set(cdi);
    }

    /**
     * Unregisters the thread-local CDI instance.
     *
     * <p>This should be called in a finally block after tests complete to avoid
     * memory leaks.
     */
    public static void unregisterThreadLocalCDI() {
        CURRENT_CDI.remove();
    }

    /**
     * Returns whether a CDI instance is currently available (global or thread-local).
     *
     * @return true if a CDI instance is registered
     */
    public static boolean isAvailable() {
        return CURRENT_CDI.get() != null || globalCDI != null;
    }

    /**
     * Ensures CDI.current() is wired to a provider that can return Syringe CDI instances.
     *
     * <p>In managed containers, another provider may already be configured and
     * {@link CDI#setCDIProvider(CDIProvider)} then throws {@link IllegalStateException}.
     * In that case we best-effort replace the configured provider with Syringe provider.
     */
    public static void ensureProviderConfigured() {
        SyringeCDIProvider provider = PROVIDER_INSTANCE;
        try {
            CDI.setCDIProvider(provider);
            return;
        } catch (IllegalStateException ignored) {
            // Fall back to reflective replacement.
        }

        try {
            Field configuredProvider = CDI.class.getDeclaredField("configuredProvider");
            configuredProvider.setAccessible(true);
            Object currentConfigured = configuredProvider.get(null);
            if (!(currentConfigured instanceof SyringeCDIProvider)) {
                configuredProvider.set(null, provider);
            }

            Field discoveredProviders = CDI.class.getDeclaredField("discoveredProviders");
            discoveredProviders.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<CDIProvider> discovered = (Set<CDIProvider>) discoveredProviders.get(null);
            Set<CDIProvider> updated = new HashSet<>();
            if (discovered != null) {
                for (CDIProvider discoveredProvider : discovered) {
                    if (!(discoveredProvider instanceof SyringeCDIProvider)) {
                        updated.add(discoveredProvider);
                    }
                }
            }
            updated.add(provider);
            discoveredProviders.set(null, updated);
        } catch (Exception ignored) {
            // Best-effort setup. If reflection is blocked, the existing configuration remains.
        }
    }
}
