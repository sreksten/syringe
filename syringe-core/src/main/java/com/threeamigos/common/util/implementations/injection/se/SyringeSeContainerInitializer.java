package com.threeamigos.common.util.implementations.injection.se;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXml;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXmlParser;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.discovery.ClasspathScanner;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.Extension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CDI SE container initializer for Syringe.
 */
public class SyringeSeContainerInitializer extends SeContainerInitializer {
    public static final String SCAN_IMPLICIT_PROPERTY = "jakarta.enterprise.inject.scan.implicit";

    private final Set<Class<?>> beanClasses = new LinkedHashSet<>();
    private final List<PackageSpec> packages = new ArrayList<>();
    private final Set<Extension> extensionInstances = new LinkedHashSet<>();
    private final Set<Class<? extends Extension>> extensionClasses = new LinkedHashSet<>();
    private final Set<Class<?>> enabledInterceptors = new LinkedHashSet<>();
    private final Set<Class<?>> enabledDecorators = new LinkedHashSet<>();
    private final Set<Class<?>> selectedAlternatives = new LinkedHashSet<>();
    private final Set<Class<? extends Annotation>> selectedAlternativeStereotypes = new LinkedHashSet<>();
    private Map<String, Object> properties = new LinkedHashMap<>();
    private boolean discoveryDisabled;
    private ClassLoader classLoader;

    @Override
    public SeContainerInitializer addBeanClasses(Class<?>... classes) {
        if (classes != null) {
            beanClasses.addAll(Arrays.asList(classes));
        }
        return this;
    }

    @Override
    public SeContainerInitializer addPackages(Class<?>... packageClasses) {
        return addPackages(false, packageClasses);
    }

    @Override
    public SeContainerInitializer addPackages(boolean scanRecursively, Class<?>... packageClasses) {
        if (packageClasses != null) {
            for (Class<?> packageClass : packageClasses) {
                if (packageClass != null && packageClass.getPackage() != null) {
                    packages.add(new PackageSpec(packageClass.getPackage(), scanRecursively));
                }
            }
        }
        return this;
    }

    @Override
    public SeContainerInitializer addPackages(Package... packages) {
        return addPackages(false, packages);
    }

    @Override
    public SeContainerInitializer addPackages(boolean scanRecursively, Package... packageArray) {
        if (packageArray != null) {
            for (Package pkg : packageArray) {
                if (pkg != null) {
                    packages.add(new PackageSpec(pkg, scanRecursively));
                }
            }
        }
        return this;
    }

    @Override
    public SeContainerInitializer addExtensions(Extension... extensions) {
        if (extensions != null) {
            extensionInstances.addAll(Arrays.asList(extensions));
        }
        return this;
    }

    @SafeVarargs
    @Override
    public final SeContainerInitializer addExtensions(Class<? extends Extension>... extensions) {
        if (extensions != null) {
            extensionClasses.addAll(Arrays.asList(extensions));
        }
        return this;
    }

    @Override
    public SeContainerInitializer enableInterceptors(Class<?>... interceptorClasses) {
        if (interceptorClasses != null) {
            enabledInterceptors.addAll(Arrays.asList(interceptorClasses));
        }
        return this;
    }

    @Override
    public SeContainerInitializer enableDecorators(Class<?>... decoratorClasses) {
        if (decoratorClasses != null) {
            enabledDecorators.addAll(Arrays.asList(decoratorClasses));
        }
        return this;
    }

    @Override
    public SeContainerInitializer selectAlternatives(Class<?>... alternativeClasses) {
        if (alternativeClasses != null) {
            selectedAlternatives.addAll(Arrays.asList(alternativeClasses));
        }
        return this;
    }

    @SafeVarargs
    @Override
    public final SeContainerInitializer selectAlternativeStereotypes(
            Class<? extends Annotation>... alternativeStereotypeClasses) {
        if (alternativeStereotypeClasses != null) {
            selectedAlternativeStereotypes.addAll(Arrays.asList(alternativeStereotypeClasses));
        }
        return this;
    }

    @Override
    public SeContainerInitializer addProperty(String key, Object value) {
        if (key != null) {
            properties.put(key, value);
        }
        return this;
    }

    @Override
    public SeContainerInitializer setProperties(Map<String, Object> properties) {
        this.properties = properties == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(properties);
        return this;
    }

    @Override
    public SeContainerInitializer disableDiscovery() {
        this.discoveryDisabled = true;
        return this;
    }

    @Override
    public SeContainerInitializer setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
        return this;
    }

    @Override
    public SeContainer initialize() {
        ClassLoader targetClassLoader = classLoader != null ? classLoader : Thread.currentThread().getContextClassLoader();
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(targetClassLoader);
        try {
            Syringe syringe = createSyringeForDiscoveryRoots();
            for (Class<? extends Extension> extensionClass : extensionClasses) {
                if (extensionClass != null) {
                    syringe.addExtension(extensionClass.getName());
                }
            }
            for (Extension extension : extensionInstances) {
                if (extension != null) {
                    syringe.addExtension(extension);
                }
            }

            syringe.initialize();
            syringe.getKnowledgeBase().setImplicitBeanArchiveScanningEnabled(isImplicitScanEnabled());
            if (!discoveryDisabled) {
                syringe.discover();
            }

            addSyntheticArchiveClasses(syringe, targetClassLoader);
            addSyntheticArchiveEnablementConfiguration(syringe);
            for (Class<?> alternativeClass : selectedAlternatives) {
                if (alternativeClass != null) {
                    syringe.enableAlternative(alternativeClass);
                }
            }

            syringe.start();
            ((BeanManagerImpl) syringe.getBeanManager()).setRequireActiveContextForGetContext(true);
            return new SyringeSeContainer(syringe);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    private Syringe createSyringeForDiscoveryRoots() {
        if (discoveryDisabled) {
            return new Syringe();
        }
        Set<String> roots = new LinkedHashSet<>();
        for (PackageSpec pkg : packages) {
            roots.add(pkg.pkg.getName());
        }
        for (Class<?> clazz : beanClasses) {
            if (clazz != null && clazz.getPackage() != null) {
                roots.add(clazz.getPackage().getName());
            }
        }
        if (roots.isEmpty()) {
            return new Syringe();
        }
        return new Syringe(roots.toArray(new String[0]));
    }

    private void addSyntheticArchiveClasses(Syringe syringe, ClassLoader targetClassLoader) {
        Set<Class<?>> syntheticArchiveTypes = new LinkedHashSet<>(beanClasses);
        for (PackageSpec packageSpec : packages) {
            syntheticArchiveTypes.addAll(resolvePackageTypes(packageSpec, targetClassLoader));
        }
        for (Class<?> clazz : syntheticArchiveTypes) {
            if (clazz != null) {
                syringe.addDiscoveredClass(clazz, BeanArchiveMode.EXPLICIT);
            }
        }
    }

    private Collection<Class<?>> resolvePackageTypes(PackageSpec packageSpec, ClassLoader classLoader) {
        Set<Class<?>> out = new LinkedHashSet<>();
        try {
            ClasspathScanner scanner = new ClasspathScanner(packageSpec.pkg.getName());
            for (Class<?> clazz : scanner.getAllClasses(classLoader)) {
                Package clazzPackage = clazz.getPackage();
                String clazzPackageName = clazzPackage != null ? clazzPackage.getName() : "";
                if (packageSpec.recursive) {
                    if (clazzPackageName.equals(packageSpec.pkg.getName())
                            || clazzPackageName.startsWith(packageSpec.pkg.getName() + ".")) {
                        out.add(clazz);
                    }
                } else if (clazzPackageName.equals(packageSpec.pkg.getName())) {
                    out.add(clazz);
                }
            }
        } catch (Exception ignored) {
            // Best-effort package expansion.
        }
        return out;
    }

    private void addSyntheticArchiveEnablementConfiguration(Syringe syringe) {
        if (enabledInterceptors.isEmpty()
                && enabledDecorators.isEmpty()
                && selectedAlternatives.isEmpty()
                && selectedAlternativeStereotypes.isEmpty()) {
            return;
        }

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<beans xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" ");
        xml.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ");
        xml.append("xsi:schemaLocation=\"https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/beans_3_0.xsd\" ");
        xml.append("bean-discovery-mode=\"all\" version=\"3.0\">");

        if (!selectedAlternatives.isEmpty() || !selectedAlternativeStereotypes.isEmpty()) {
            xml.append("<alternatives>");
            for (Class<?> alternativeClass : selectedAlternatives) {
                if (alternativeClass != null) {
                    xml.append("<class>").append(alternativeClass.getName()).append("</class>");
                }
            }
            for (Class<? extends Annotation> stereotypeClass : selectedAlternativeStereotypes) {
                if (stereotypeClass != null) {
                    xml.append("<stereotype>").append(stereotypeClass.getName()).append("</stereotype>");
                }
            }
            xml.append("</alternatives>");
        }

        if (!enabledInterceptors.isEmpty()) {
            xml.append("<interceptors>");
            for (Class<?> interceptorClass : enabledInterceptors) {
                if (interceptorClass != null) {
                    xml.append("<class>").append(interceptorClass.getName()).append("</class>");
                }
            }
            xml.append("</interceptors>");
        }

        if (!enabledDecorators.isEmpty()) {
            xml.append("<decorators>");
            for (Class<?> decoratorClass : enabledDecorators) {
                if (decoratorClass != null) {
                    xml.append("<class>").append(decoratorClass.getName()).append("</class>");
                }
            }
            xml.append("</decorators>");
        }

        xml.append("</beans>");

        BeansXml beansXml = new BeansXmlParser()
                .parse(new ByteArrayInputStream(xml.toString().getBytes(StandardCharsets.UTF_8)));
        syringe.getKnowledgeBase().addBeansXml(beansXml);
    }

    private boolean isImplicitScanEnabled() {
        String sysValue = System.getProperty(SCAN_IMPLICIT_PROPERTY);
        if (Boolean.parseBoolean(sysValue)) {
            return true;
        }

        Object mapValue = properties.get(SCAN_IMPLICIT_PROPERTY);
        if (Boolean.TRUE.equals(mapValue)) {
            return true;
        }
        if (mapValue instanceof String) {
            return Boolean.parseBoolean((String) mapValue);
        }
        return false;
    }

    private static class PackageSpec {
        private final Package pkg;
        private final boolean recursive;

        private PackageSpec(Package pkg, boolean recursive) {
            this.pkg = pkg;
            this.recursive = recursive;
        }
    }
}
