package com.threeamigos.common.util.implementations.injection.discovery;

import com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates;

import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Scans the classpath for classes in specified packages.
 *
 * <p>Discovers all .class files from both filesystem directories and JAR files within the specified package(s).
 * If no packages are specified, scans the entire classpath.</p>
 * Scan results are passed to a {@link ClassConsumer} for further processing (e.g., JSR-330 compliance checks).</p>
 *
 * <p><b>Filtering:</b> Automatically excludes:
 * <ul>
 *   <li>META-INF entries</li>
 *   <li>module-info.class files</li>
 *   <li>Classes that cannot be loaded (missing dependencies)</li>
 *   <li>Classes in packages annotated with @Vetoed (CDI 4.1)</li>
 * </ul>
 *
 * <p><b>@Vetoed Package Support (CDI 4.1):</b>
 * Packages can be vetoed by annotating their {@code package-info.java} with {@code @Vetoed}.
 * All classes in vetoed packages and their subpackages are automatically excluded from bean discovery.
 *
 * @author Stefano Reksten
 */
public class ParallelClasspathScanner {

    private static final String CLASS_EXTENSION = ".class";
    private static final int CLASS_EXTENSION_LENGTH = 6;
    private static final String MODULE_INFO_CLASS = "module-info.class";
    private static final String PACKAGE_INFO_CLASS = "package-info.class";
    private static final String META_INF = "META-INF";
    private static final String ROOT_PACKAGE = "";
    private static final String FILE_PROTOCOL = "file";
    private static final String JAR_PROTOCOL = "jar";

    /**
     * Cache of vetoed packages. Thread-safe for concurrent scanning.
     * Key: package name, Value: true if vetoed
     */
    private final Map<String, Boolean> vetoedPackages = new ConcurrentHashMap<>();

    /**
     * Set to track already-scanned classes to avoid duplicates.
     * This prevents the same class from being added multiple times when it appears
     * in multiple JARs (e.g., javax.inject-tck and jakarta.inject-tck both contain
     * the same org.atinject.tck classes).
     */
    private final Set<String> scannedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Detector for bean archive modes (EXPLICIT/IMPLICIT/NONE based on beans.xml).
     */
    private final BeanArchiveDetector beanArchiveDetector;

    /**
     * Collector for BeansXml configurations from all scanned archives.
     * Thread-safe for concurrent scanning. Key: archive root path, Value: BeansXml
     */
    private final Map<String, com.threeamigos.common.util.implementations.injection.beansxml.BeansXml> beansXmlMap = new ConcurrentHashMap<>();

    public ParallelClasspathScanner(ClassLoader classLoader,
                     ClassConsumer sink,
                     KnowledgeBase knowledgeBase,
                     String... packageNames) throws IOException {
        Objects.requireNonNull(sink, "sink cannot be null");
        Objects.requireNonNull(packageNames, "packageNames cannot be null");
        this.beanArchiveDetector = new BeanArchiveDetector(knowledgeBase);

        Collection<String> packageList = sanitizePackages(packageNames);

        for (String packageName : packageList) {
            String path = packageName.replace('.', '/');
            Enumeration<URL> resources = classLoader.getResources(path);
            boolean foundAny = false;
            while (resources.hasMoreElements()) {
                foundAny = true;
                URL resource = resources.nextElement();
                getClassesFromResource(classLoader, resource, packageName, sink);
            }
            if (!foundAny && ROOT_PACKAGE.equals(packageName)) {
                scanRootUrlsFallback(classLoader, sink);
            }
        }
    }

    private void scanRootUrlsFallback(ClassLoader classLoader, ClassConsumer sink) throws IOException {
        if (!(classLoader instanceof URLClassLoader)) {
            return;
        }

        URL[] urls = ((URLClassLoader) classLoader).getURLs();
        if (urls == null || urls.length == 0) {
            return;
        }

        for (URL url : urls) {
            if (url == null) {
                continue;
            }

            String protocol = url.getProtocol();
            if (FILE_PROTOCOL.equals(protocol)) {
                File file = toFile(url);
                if (file.isDirectory()) {
                    findClassesInDirectory(classLoader, file, ROOT_PACKAGE, sink);
                } else if (file.isFile() && file.getName().endsWith(".jar")) {
                    scanJarFile(classLoader, file, sink);
                }
            } else if (JAR_PROTOCOL.equals(protocol)) {
                findClassesInJar(classLoader, url, ROOT_PACKAGE, sink);
            }
        }
    }

    private void scanJarFile(ClassLoader classLoader, File jarFile, ClassConsumer sink) throws IOException {
        try {
            URL jarUrl = new URL("jar:" + jarFile.toURI().toURL().toExternalForm() + "!/");
            findClassesInJar(classLoader, jarUrl, ROOT_PACKAGE, sink);
        } catch (Exception e) {
            throw new IOException("Failed to scan JAR file " + jarFile.getAbsolutePath(), e);
        }
    }

    private File toFile(URL url) {
        try {
            return new File(url.toURI());
        } catch (Exception ignored) {
            return new File(url.getPath());
        }
    }

    private Collection<String> sanitizePackages(String... packageNames) {
        List<String> packages = new ArrayList<>();
        for (String pkg : packageNames) {
            if (pkg != null && !pkg.isEmpty()) {
                validatePackageName(pkg);
                packages.add(pkg);
            }
        }
        if (packages.isEmpty()) {
            packages.add(ROOT_PACKAGE);
        }
        return packages;
    }

    private void validatePackageName(String packageName) {
        if (!packageName.matches("[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*")) {
            throw new IllegalArgumentException("Invalid package name: " + packageName);
        }
    }

    public void getClassesFromResource(ClassLoader classLoader, URL resource, String packageName,
                                       ClassConsumer sink) throws IOException {
        if (resource.getProtocol().equals(FILE_PROTOCOL)) {
            findClassesInDirectory(classLoader, new File(resource.getFile()), packageName, sink);
        } else if (resource.getProtocol().equals(JAR_PROTOCOL)) {
            findClassesInJar(classLoader, resource, packageName, sink);
        } else {
            throw new IllegalArgumentException("Unsupported protocol: " + resource.getProtocol());
        }
    }

    public void findClassesInDirectory(ClassLoader classLoader, File directory, String packageName,
                                       ClassConsumer sink) {
        // Package scans are recursive: requesting "com.example" includes subpackages.
        findClassesInDirectory(classLoader, directory, packageName, sink, true);
    }

    private void findClassesInDirectory(ClassLoader classLoader, File directory, String packageName,
                                        ClassConsumer sink, boolean recursive) {
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }

        // Check if the package is vetoed (skip the entire package and subpackages)
        if (isPackageVetoed(classLoader, packageName)) {
            return;
        }

        // Detect bean archive mode for this directory (check for META-INF/beans.xml)
        File archiveRoot = findArchiveRoot(directory);
        BeanArchiveMode archiveMode = beanArchiveDetector.detectArchiveMode(archiveRoot);

        // Collect beans.xml configuration if present
        collectBeansXml(archiveRoot);

        // bean-discovery-mode="none" → skip all classes in this archive
        if (archiveMode == BeanArchiveMode.NONE) {
            return;
        }

        File[] files = directory.listFiles();

        // listFiles() CAN return null on I/O errors or permission issues
        if (files == null) {
            // Could log a warning here if logging is available
            return;
        }

        for (File file : files) {
            String prefix = packageName.isEmpty() ? "" : packageName + ".";
            if (file.isDirectory()) {
                if (recursive) {
                    // Root scans are intentionally recursive over the whole classpath.
                    findClassesInDirectory(classLoader, file, prefix + file.getName(), sink, true);
                }
            } else if (file.getName().endsWith(CLASS_EXTENSION) && !file.getName().equals(PACKAGE_INFO_CLASS)) {
                String className = prefix + file.getName().substring(0, file.getName().length() - CLASS_EXTENSION_LENGTH);
                // Only process if we haven't seen this class before (avoid duplicates from multiple JARs)
                if (scannedClasses.add(className)) {
                    // Check beans.xml scan exclusions (CDI 4.1 Section 12.4)
                    if (isExcludedByBeansXml(className, archiveRoot, classLoader)) {
                        continue; // Skip excluded classes
                    }

                    try {
                        Class<?> clazz = Class.forName(className, false, classLoader);
                        sink.add(clazz, archiveMode);
                    } catch (NoClassDefFoundError | ClassNotFoundException e) {
                        // Skip classes with missing dependencies or those that can't be loaded; continue scanning
                    }
                }
            }
        }
    }

    /**
     * Finds the archive root (where META-INF would be located) by navigating up from a directory.
     *
     * @param directory a directory within the classpath
     * @return the archive root directory
     */
    private File findArchiveRoot(File directory) {
        File current = directory;
        // Navigate up to find where META-INF exists or use the original directory
        while (current != null && current.getParentFile() != null) {
            File metaInf = new File(current, "META-INF");
            if (metaInf.exists() && metaInf.isDirectory()) {
                return current;
            }
            File parentMetaInf = new File(current.getParentFile(), "META-INF");
            if (parentMetaInf.exists() && parentMetaInf.isDirectory()) {
                return current.getParentFile();
            }
            current = current.getParentFile();
        }
        // If we can't find META-INF, return the original directory
        return directory;
    }

    public void findClassesInJar(ClassLoader classLoader, URL jarUrl, String packageName, ClassConsumer sink) throws IOException {
        // Extract the file path properly handling 'jar:file': and '!'
        String urlString = jarUrl.toString();
        String jarFilePath = urlString.substring(urlString.indexOf("file:"), urlString.indexOf("!"));

        File jarFile;
        try {
            jarFile = new File(new URL(jarFilePath).toURI());
        } catch (Exception e) {
            // Fallback for non-standard URI formats
            jarFile = new File(jarFilePath.replace("file:", ""));
        }

        // Detect bean archive mode for this JAR (check for META-INF/beans.xml inside JAR)
        BeanArchiveMode archiveMode = beanArchiveDetector.detectArchiveMode(jarFile);

        // Collect beans.xml configuration if present
        collectBeansXml(jarFile);

        if (archiveMode == BeanArchiveMode.NONE) {
            return; // Skip entries entirely for bean-discovery-mode="none"
        }

        String packagePath = packageName.replace('.', '/');

        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith(META_INF) && isClassInScannedPackage(name, packagePath) &&
                        name.endsWith(CLASS_EXTENSION) && !name.endsWith(MODULE_INFO_CLASS) &&
                        !name.endsWith(PACKAGE_INFO_CLASS)) {
                    String className = name.replace('/', '.').substring(0, name.length() - CLASS_EXTENSION_LENGTH);

                    // Extract the package from the class name and check if vetoed
                    String classPackage = getPackageFromClassName(className);
                    if (isPackageVetoed(classLoader, classPackage)) {
                        continue; // Skip classes in vetoed packages
                    }

                    // Only process if we haven't seen this class before (avoid duplicates from multiple JARs)
                    if (scannedClasses.add(className)) {
                        // Check beans.xml scan exclusions (CDI 4.1 Section 12.4)
                        if (isExcludedByBeansXml(className, jarFile, classLoader)) {
                            continue; // Skip excluded classes
                        }

                        try {
                            sink.add(Class.forName(className, false, classLoader), archiveMode);
                        } catch (NoClassDefFoundError | ClassNotFoundException e) {
                            // Skip classes with missing dependencies or those that can't be loaded
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new IOException("Failed to read JAR file: " + jarFilePath, e);
        }
    }

    private boolean isClassInScannedPackage(String entryName, String packagePath) {
        if (entryName == null) {
            return false;
        }
        if (packagePath == null || packagePath.isEmpty()) {
            return true;
        }
        // Package scans are recursive, so all classes under packagePath are eligible.
        return entryName.startsWith(packagePath + "/");
    }

    /**
     * Checks if a package is vetoed by examining its package-info class.
     * Results are cached to avoid repeated reflection calls.
     *
     * @param classLoader the class loader to use
     * @param packageName the package name to check
     * @return true if the package (or any parent package) is vetoed
     */
    private boolean isPackageVetoed(ClassLoader classLoader, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }

        // Check cache first
        Boolean cached = vetoedPackages.get(packageName);
        if (cached != null) {
            return cached;
        }

        // Check if this package is directly vetoed
        boolean vetoed = checkPackageAnnotation(classLoader, packageName);

        // If not directly vetoed, check parent packages (CDI 4.1: @Vetoed on parent applies to children)
        if (!vetoed) {
            String parentPackage = getParentPackage(packageName);
            if (parentPackage != null) {
                vetoed = isPackageVetoed(classLoader, parentPackage);
            }
        }

        // Cache result
        vetoedPackages.put(packageName, vetoed);
        return vetoed;
    }

    /**
     * Checks if a specific package has @Vetoed annotation.
     *
     * @param classLoader the class loader to use
     * @param packageName the package name to check
     * @return true if the package-info is annotated with @Vetoed
     */
    private boolean checkPackageAnnotation(ClassLoader classLoader, String packageName) {
        try {
            String packageInfoClass = packageName + ".package-info";
            Class<?> pkgInfo = Class.forName(packageInfoClass, false, classLoader);
            return AnnotationPredicates.hasVetoedAnnotation(pkgInfo);
        } catch (ClassNotFoundException e) {
            // No package-info.java exists - package is not vetoed
            return false;
        }
    }

    /**
     * Extracts the parent package name from a given package.
     *
     * @param packageName the package name
     * @return the parent package name, or null if no parent exists
     */
    private String getParentPackage(String packageName) {
        int lastDot = packageName.lastIndexOf('.');
        return lastDot > 0 ? packageName.substring(0, lastDot) : null;
    }

    /**
     * Extracts the package name from a fully qualified class name.
     *
     * @param className the fully qualified class name
     * @return the package name, or empty string if in the default package
     */
    private String getPackageFromClassName(String className) {
        int lastDot = className.lastIndexOf('.');
        return lastDot > 0 ? className.substring(0, lastDot) : "";
    }

    /**
     * Collects beans.xml configuration from an archive root (JAR or directory).
     * Uses the canonical path as a key to avoid a duplicate collection from the same archive.
     *
     * @param archiveRoot the archive root (where META-INF/beans.xml would be located)
     */
    private void collectBeansXml(File archiveRoot) {
        if (archiveRoot == null || !archiveRoot.exists()) {
            return;
        }

        try {
            String canonicalPath = archiveRoot.getCanonicalPath();

            // Only collect once per archive (avoid duplicates)
            if (!beansXmlMap.containsKey(canonicalPath)) {
                com.threeamigos.common.util.implementations.injection.beansxml.BeansXml beansXml =
                    beanArchiveDetector.getBeansXml(archiveRoot);

                // Only store non-empty beans.xml configurations
                if (beansXml != null && !beansXml.isEmpty()) {
                    beansXmlMap.put(canonicalPath, beansXml);
                }
            }
        } catch (Exception e) {
            // Ignore errors during beans.xml collection (already handled by detector)
        }
    }

    /**
     * Gets all collected beans.xml configurations from scanned archives.
     *
     * @return collection of BeansXml objects
     */
    public Collection<com.threeamigos.common.util.implementations.injection.beansxml.BeansXml> getBeansXmlConfigurations() {
        return Collections.unmodifiableCollection(beansXmlMap.values());
    }

    /**
     * Checks if a class should be excluded based on beans.xml scan exclusions.
     *
     * <p>CDI 4.1 Section 12.4: The &lt;scan&gt;&lt;exclude&gt; elements allow
     * fine-grained control over which classes are discovered as beans.
     *
     * <p>An exclusion rule applies if:
     * <ul>
     *   <li>The class name matches the exclusion pattern (exact, *, **)</li>
     *   <li>ALL conditions (if-class-available, if-class-not-available, if-system-property) are met</li>
     * </ul>
     *
     * @param className the fully qualified class name to check
     * @param archiveRoot the archive root (to get its beans.xml configuration)
     * @param classLoader the class loader for condition evaluation
     * @return true if the class should be excluded
     */
    private boolean isExcludedByBeansXml(String className, File archiveRoot, ClassLoader classLoader) {
        if (className == null || archiveRoot == null) {
            return false;
        }

        try {
            String canonicalPath = archiveRoot.getCanonicalPath();
            com.threeamigos.common.util.implementations.injection.beansxml.BeansXml beansXml =
                beansXmlMap.get(canonicalPath);

            if (beansXml == null || beansXml.getScan() == null) {
                return false; // No exclusions defined
            }

            // Check each exclusion rule
            for (com.threeamigos.common.util.implementations.injection.beansxml.Exclude exclude :
                    beansXml.getScan().getExcludes()) {

                // Check if pattern matches
                if (!exclude.matches(className)) {
                    continue; // Pattern doesn't match, try next exclusion
                }

                // Pattern matches - now check conditions (AND logic)
                if (evaluateExclusionConditions(exclude, classLoader)) {
                    // All conditions met - exclude this class
                    return true;
                }
            }

            return false; // No matching exclusion rules

        } catch (Exception e) {
            // On error, don't exclude (fail-safe approach)
            return false;
        }
    }

    /**
     * Evaluates all conditions for an exclusion rule.
     *
     * <p>CDI 4.1 Section 12.4: All conditions must be true (AND logic)
     * for the exclusion to apply.
     *
     * @param exclude the exclusion rule with conditions
     * @param classLoader the class loader for checking class availability
     * @return true if ALL conditions are met (or no conditions exist)
     */
    private boolean evaluateExclusionConditions(
            com.threeamigos.common.util.implementations.injection.beansxml.Exclude exclude,
            ClassLoader classLoader) {

        // No conditions = unconditional exclusion (always applies)
        if (exclude.isUnconditional()) {
            return true;
        }

        // Check all "if-class-available" conditions
        for (com.threeamigos.common.util.implementations.injection.beansxml.IfClassAvailable condition :
                exclude.getIfClassAvailable()) {
            if (!isClassAvailable(condition.getName(), classLoader)) {
                return false; // Condition isn't met
            }
        }

        // Check all "if-class-not-available" conditions
        for (com.threeamigos.common.util.implementations.injection.beansxml.IfClassNotAvailable condition :
                exclude.getIfClassNotAvailable()) {
            if (isClassAvailable(condition.getName(), classLoader)) {
                return false; // Condition isn't met (class IS available but shouldn't be)
            }
        }

        // Check all "if-system-property" conditions
        for (com.threeamigos.common.util.implementations.injection.beansxml.IfSystemProperty condition :
                exclude.getIfSystemProperty()) {
            String actualValue = System.getProperty(condition.getName());
            if (actualValue == null || !actualValue.equals(condition.getValue())) {
                return false; // Condition isn't met
            }
        }

        // All conditions met
        return true;
    }

    /**
     * Checks if a class is available on the classpath.
     *
     * @param className the fully qualified class name
     * @param classLoader the class loader to use
     * @return true if the class can be loaded
     */
    private boolean isClassAvailable(String className, ClassLoader classLoader) {
        if (className == null || className.isEmpty()) {
            return false;
        }

        try {
            Class.forName(className, false, classLoader);
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return false;
        }
    }
}
