package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter27;

import com.threeamigos.common.util.implementations.injection.se.SyringeSeContainerInitializer;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("27 - Packaging and Deployment in Java SE")
@Execution(ExecutionMode.SAME_THREAD)
public class PackagingAndDeploymentInJavaSETest {

    @Test
    @DisplayName("27.1 - Implicit bean archives without beans.xml are not discovered by default in Java SE")
    void shouldNotDiscoverImplicitArchiveWithoutBeansXmlByDefault() throws Exception {
        System.clearProperty(SyringeSeContainerInitializer.SCAN_IMPLICIT_PROPERTY);
        URLClassLoader isolatedClassLoader = isolatedChapter27ClassLoader();
        Class<?> implicitBeanClass = isolatedClassLoader.loadClass(Chapter27_1ImplicitBean.class.getName());
        try (URLClassLoader ignored = isolatedClassLoader;
             SeContainer container = SeContainerInitializer.newInstance()
                     .setClassLoader(isolatedClassLoader)
                     .initialize()) {
            assertThrows(UnsatisfiedResolutionException.class,
                    () -> container.select((Class) implicitBeanClass).get());
        }
    }

    @Test
    @DisplayName("27.1 - System property jakarta.enterprise.inject.scan.implicit=true enables implicit archive discovery")
    void shouldDiscoverImplicitArchiveWhenSystemPropertyIsTrue() throws Exception {
        String previous = System.getProperty(SyringeSeContainerInitializer.SCAN_IMPLICIT_PROPERTY);
        System.setProperty(SyringeSeContainerInitializer.SCAN_IMPLICIT_PROPERTY, "true");
        URLClassLoader isolatedClassLoader = isolatedChapter27ClassLoader();
        Class<?> implicitBeanClass = isolatedClassLoader.loadClass(Chapter27_1ImplicitBean.class.getName());
        try (URLClassLoader ignored = isolatedClassLoader;
             SeContainer container = SeContainerInitializer.newInstance()
                     .setClassLoader(isolatedClassLoader)
                     .initialize()) {
            Object bean = container.select((Class) implicitBeanClass).get();
            assertEquals("implicit", bean.getClass().getMethod("id").invoke(bean));
        } finally {
            if (previous == null) {
                System.clearProperty(SyringeSeContainerInitializer.SCAN_IMPLICIT_PROPERTY);
            } else {
                System.setProperty(SyringeSeContainerInitializer.SCAN_IMPLICIT_PROPERTY, previous);
            }
        }
    }

    @Test
    @DisplayName("27.1 - Initializer properties map with jakarta.enterprise.inject.scan.implicit=Boolean.TRUE enables implicit discovery")
    void shouldDiscoverImplicitArchiveWhenInitializerPropertyIsTrue() throws Exception {
        System.clearProperty(SyringeSeContainerInitializer.SCAN_IMPLICIT_PROPERTY);

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(SyringeSeContainerInitializer.SCAN_IMPLICIT_PROPERTY, Boolean.TRUE);

        URLClassLoader isolatedClassLoader = isolatedChapter27ClassLoader();
        Class<?> implicitBeanClass = isolatedClassLoader.loadClass(Chapter27_1ImplicitBean.class.getName());
        try (URLClassLoader ignored = isolatedClassLoader;
             SeContainer container = SeContainerInitializer.newInstance()
                     .setClassLoader(isolatedClassLoader)
                     .setProperties(properties)
                     .initialize()) {
            Object bean = container.select((Class) implicitBeanClass).get();
            assertEquals("implicit", bean.getClass().getMethod("id").invoke(bean));
        }
    }

    @Dependent
    public static class Chapter27_1ImplicitBean {
        public String id() {
            return "implicit";
        }
    }

    private URLClassLoader isolatedChapter27ClassLoader() throws IOException {
        Path root = Files.createTempDirectory("syringe-ch27");
        copyClassBytes(PackagingAndDeploymentInJavaSETest.class, root);
        copyClassBytes(Chapter27_1ImplicitBean.class, root);
        return new IsolatedDiscoveryClassLoader(
                new URL[]{root.toUri().toURL()},
                PackagingAndDeploymentInJavaSETest.class.getClassLoader()
        );
    }

    private void copyClassBytes(Class<?> clazz, Path root) throws IOException {
        String resource = clazz.getName().replace('.', '/') + ".class";
        try (InputStream input = clazz.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Could not load class bytes for " + clazz.getName());
            }
            Path target = root.resolve(resource);
            Files.createDirectories(target.getParent());
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static class IsolatedDiscoveryClassLoader extends URLClassLoader {
        private IsolatedDiscoveryClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            return findResources(name);
        }
    }
}
