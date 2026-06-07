package com.threeamigos.common.util.implementations.injection;

import com.threeamigos.common.util.implementations.injection.events.NoOpObserverSupport;
import com.threeamigos.common.util.implementations.injection.extensions.NoOpExtensionsManager;
import com.threeamigos.common.util.implementations.injection.interceptors.InterceptorSupport;
import com.threeamigos.common.util.implementations.injection.interceptors.NoOpInterceptorSupport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceLoaderClassLoaderIsolationTest {

    @Test
    void discoversInternalModulesEvenWhenTcclHidesServiceResources() throws Exception {
        ClassLoader originalTccl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new ServiceResourceHidingClassLoader(originalTccl));
        try {
            Object extensionsManager = invokeDiscover("discoverExtensionsManager");
            Object interceptorSupport = invokeDiscover("discoverInterceptorSupport");
            Object observerSupport = invokeDiscover("discoverObserverSupport");

            assertFalse(extensionsManager instanceof NoOpExtensionsManager,
                    "ExtensionsManager discovery must not depend on TCCL service resources");
            assertFalse(interceptorSupport instanceof NoOpInterceptorSupport,
                    "InterceptorSupport discovery must not depend on TCCL service resources");
            assertFalse(observerSupport instanceof NoOpObserverSupport,
                    "ObserverSupport discovery must not depend on TCCL service resources");
        } finally {
            Thread.currentThread().setContextClassLoader(originalTccl);
        }
    }

    private Object invokeDiscover(String methodName) throws Exception {
        Method method = Syringe.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(null);
    }

    @Test
    void fallsBackToTcclWhenPrimaryClassLoaderCannotSeeServiceDescriptors() throws Exception {
        ClassLoader hiddenServices = new ServiceResourceHidingClassLoader(Syringe.class.getClassLoader());

        Method method = Syringe.class.getDeclaredMethod(
                "loadInternalService",
                Class.class,
                ClassLoader.class,
                ClassLoader.class
        );
        method.setAccessible(true);

        Iterator<?> iterator = (Iterator<?>) method.invoke(
                null,
                InterceptorSupport.class,
                hiddenServices,
                Syringe.class.getClassLoader()
        );

        assertTrue(iterator.hasNext(), "Expected fallback classloader to provide InterceptorSupport");
        assertFalse(iterator.next() instanceof NoOpInterceptorSupport,
                "Fallback classloader should resolve concrete InterceptorSupport implementation");
    }

    private static final class ServiceResourceHidingClassLoader extends ClassLoader {
        private ServiceResourceHidingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (name != null && name.startsWith("META-INF/services/")) {
                return Collections.emptyEnumeration();
            }
            return super.getResources(name);
        }
    }
}
