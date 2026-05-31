package com.threeamigos.common.util.implementations.injection.spi;

import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralizes BeanManager global registration and transient-dependent reference tracking.
 */
final class BeanManagerRegistry {

    private static final ConcurrentHashMap<ClassLoader, BeanManagerImpl> BEAN_MANAGER_REGISTRY =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, BeanManagerImpl> BEAN_MANAGER_ID_REGISTRY =
            new ConcurrentHashMap<>();
    private static final Map<Object, DependentReference<?>> TRANSIENT_DEPENDENT_REFERENCE_REGISTRY =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private BeanManagerRegistry() {
    }

    static void register(BeanManagerImpl beanManager, ClassLoader classLoader) {
        if (beanManager == null || classLoader == null) {
            return;
        }
        BEAN_MANAGER_REGISTRY.put(classLoader, beanManager);
        BEAN_MANAGER_ID_REGISTRY.put(beanManager.getBeanManagerId(), beanManager);
    }

    static void unregister(BeanManagerImpl beanManager, ClassLoader classLoader) {
        if (beanManager == null || classLoader == null) {
            return;
        }
        BEAN_MANAGER_ID_REGISTRY.remove(beanManager.getBeanManagerId(), beanManager);
        BEAN_MANAGER_REGISTRY.remove(classLoader, beanManager);
    }

    static BeanManagerImpl getRegisteredBeanManager(ClassLoader classLoader) {
        if (classLoader == null) {
            return null;
        }
        return BEAN_MANAGER_REGISTRY.get(classLoader);
    }

    static BeanManagerImpl getRegisteredBeanManager(String beanManagerId) {
        if (beanManagerId == null) {
            return null;
        }
        return BEAN_MANAGER_ID_REGISTRY.get(beanManagerId);
    }

    static Collection<BeanManagerImpl> getRegisteredBeanManagersSnapshot() {
        return new ArrayList<>(
                new LinkedHashSet<>(BEAN_MANAGER_ID_REGISTRY.values()));
    }

    static boolean destroyTransientReference(Object instance) {
        return destroyTransientReference(null, instance);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static boolean destroyTransientReference(String ownerBeanManagerId, Object instance) {
        if (instance == null) {
            return false;
        }

        DependentReference<?> reference;
        synchronized (TRANSIENT_DEPENDENT_REFERENCE_REGISTRY) {
            reference = TRANSIENT_DEPENDENT_REFERENCE_REGISTRY.get(instance);
            if (reference == null) {
                return false;
            }
            if (ownerBeanManagerId != null && !ownerBeanManagerId.equals(reference.beanManagerId)) {
                return false;
            }
            TRANSIENT_DEPENDENT_REFERENCE_REGISTRY.remove(instance);
        }

        try {
            Bean bean = reference.bean;
            CreationalContext context = reference.creationalContext;
            bean.destroy(instance, context);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static <T> void registerTransientReference(String ownerBeanManagerId,
                                               Bean<T> bean,
                                               T instance,
                                               CreationalContext<T> creationalContext) {
        if (ownerBeanManagerId == null || bean == null || instance == null || creationalContext == null) {
            return;
        }
        synchronized (TRANSIENT_DEPENDENT_REFERENCE_REGISTRY) {
            TRANSIENT_DEPENDENT_REFERENCE_REGISTRY.put(instance,
                    new DependentReference<>(ownerBeanManagerId, bean, creationalContext));
        }
    }

    static void clearTransientReferencesForOwner(String ownerBeanManagerId) {
        if (ownerBeanManagerId == null) {
            return;
        }
        synchronized (TRANSIENT_DEPENDENT_REFERENCE_REGISTRY) {
            Iterator<Map.Entry<Object, DependentReference<?>>> iterator =
                    TRANSIENT_DEPENDENT_REFERENCE_REGISTRY.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Object, DependentReference<?>> entry = iterator.next();
                DependentReference<?> reference = entry.getValue();
                if (reference != null && ownerBeanManagerId.equals(reference.beanManagerId)) {
                    iterator.remove();
                }
            }
        }
    }

    static Object createSerializedReference(String beanManagerId) {
        return new SerializedBeanManagerReference(beanManagerId);
    }

    static Object resolveSerializedReference(String beanManagerId) throws ObjectStreamException {
        BeanManagerImpl byId = getRegisteredBeanManager(beanManagerId);
        if (byId != null) {
            return byId;
        }

        ClassLoader ccl = Thread.currentThread().getContextClassLoader();
        if (ccl == null) {
            ccl = BeanManagerImpl.class.getClassLoader();
        }
        BeanManagerImpl byClassLoader = getRegisteredBeanManager(ccl);
        if (byClassLoader != null) {
            return byClassLoader;
        }

        throw new InvalidObjectException("No BeanManager registered for deserialization handle");
    }

    private static final class SerializedBeanManagerReference implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String beanManagerId;

        private SerializedBeanManagerReference(String beanManagerId) {
            this.beanManagerId = beanManagerId;
        }

        private Object readResolve() throws ObjectStreamException {
            return BeanManagerRegistry.resolveSerializedReference(beanManagerId);
        }
    }

    private static final class DependentReference<T> {
        private final String beanManagerId;
        private final Bean<T> bean;
        private final CreationalContext<T> creationalContext;

        private DependentReference(String beanManagerId, Bean<T> bean, CreationalContext<T> creationalContext) {
            this.beanManagerId = beanManagerId;
            this.bean = bean;
            this.creationalContext = creationalContext;
        }
    }
}
