package com.threeamigos.common.util.implementations.injection;

import com.threeamigos.common.util.implementations.injection.discovery.ParallelClasspathScanner;
import com.threeamigos.common.util.implementations.injection.discovery.SimpleClassConsumer;
import com.threeamigos.common.util.implementations.injection.testpackages.interfaces.singleimplementation.SingleImplementationClass;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collection;
import java.util.Enumeration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for ParallelClasspathScanner to ensure feature parity with ClasspathScanner.
 * Adapted from ClasspathScannerUnitTest.
 */
@DisplayName("ParallelClasspathScanner unit test")
class ParallelClasspathScannerUnitTest {

    @TempDir
    Path tempDir;

    private ClassLoader classLoader;
    private KnowledgeBase knowledgeBase;
    private SimpleClassConsumer sink;

    @BeforeEach
    void setUp() {
        MessageHandler messageHandler = new InMemoryMessageHandler();
        classLoader = Thread.currentThread().getContextClassLoader();
        knowledgeBase = new KnowledgeBase(messageHandler);
        sink = new SimpleClassConsumer();
    }

    @Nested
    @DisplayName("Input Validation Tests")
    class InputValidationTests {

        @Test
        @DisplayName("Should throw IllegalArgumentException for invalid package name starting with digit")
        void shouldThrowExceptionForPackageNameStartingWithDigit() {
            // When/Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ParallelClasspathScanner(classLoader, sink, knowledgeBase, "123invalid"));
            assertTrue(exception.getMessage().contains("Invalid package name"));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for package name with special characters")
        void shouldThrowExceptionForPackageNameWithSpecialChars() {
            // When/Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ParallelClasspathScanner(classLoader, sink, knowledgeBase, "com.example.@invalid"));
            assertTrue(exception.getMessage().contains("Invalid package name"));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for package name with double dots")
        void shouldThrowExceptionForPackageNameWithDoubleDots() {
            // When/Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ParallelClasspathScanner(classLoader, sink, knowledgeBase, "com..double.dot"));
            assertTrue(exception.getMessage().contains("Invalid package name"));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for package name ending with dot")
        void shouldThrowExceptionForPackageNameEndingWithDot() {
            // When/Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ParallelClasspathScanner(classLoader, sink, knowledgeBase, "com.example."));
            assertTrue(exception.getMessage().contains("Invalid package name"));
        }

        @Test
        @DisplayName("Should accept valid package name with underscores and numbers")
        void shouldAcceptValidPackageNameWithUnderscoresAndNumbers() throws Exception {
            // When/Then - Should not throw
            assertDoesNotThrow(() -> new ParallelClasspathScanner(classLoader, sink, knowledgeBase, "com.threeamigos.util_1.test_2"));
        }

        @Test
        @DisplayName("Should accept package name starting with underscore")
        void shouldAcceptPackageNameStartingWithUnderscore() throws Exception {
            // When/Then - Should not throw
            assertDoesNotThrow(() -> new ParallelClasspathScanner(classLoader, sink, knowledgeBase, "_private.com.example"));
        }
    }

    @Nested
    @DisplayName("When package is wrong")
    class WrongPackage {

        @Test
        @DisplayName("Should return an empty collection if package to search does not exist")
        void shouldReturnEmptyCollectionIfPackageToSearchDoesNotExist() throws Exception {
            // Given
            new ParallelClasspathScanner(classLoader, sink, knowledgeBase, "com.threeamigos.common.util.implementations.injection.notexistingpackage");

            // Then
            assertTrue(sink.getClasses().isEmpty());
        }

        @Test
        @DisplayName("Should return an empty collection if package to search is a file")
        void shouldReturnEmptyCollectionIfPackageToSearchIsAFile() throws Exception {
            // Given
            new ParallelClasspathScanner(classLoader, sink, knowledgeBase, "com.threeamigos.common.util.implementations.injection.wrongdirectory.fakeFileToSkip");

            // Then
            assertTrue(sink.getClasses().isEmpty());
        }
    }

    @Nested
    @DisplayName("When package is empty or null")
    class NullOrEmptyPackage {

        @Test
        @DisplayName("Should work if package is null")
        void shouldFindALotOfClassesIfPackageIsNull() throws Exception {
            // Given
            new ParallelClasspathScanner(classLoader, sink, knowledgeBase, (String) null);

            // When
            Collection<Class<?>> classes = sink.getClasses();

            // Then
            assertTrue(classes.contains(SingleImplementationClass.class));
        }

        @Test
        @DisplayName("Should work if package is empty")
        void shouldFindALotOfClassesIfPackageIsEmpty() throws Exception {
            // Given
            new ParallelClasspathScanner(classLoader, sink, knowledgeBase, "");

            // When
            Collection<Class<?>> classes = sink.getClasses();

            // Then
            assertTrue(classes.contains(SingleImplementationClass.class));
        }

        @Test
        @DisplayName("Should handle multiple null package names by filtering them")
        void shouldHandleMultipleNullPackages() throws Exception {
            // Given
            new ParallelClasspathScanner(classLoader, sink, knowledgeBase, null, null, "com.threeamigos.common.util.implementations.injection");

            // When
            Collection<Class<?>> classes = sink.getClasses();

            // Then
            assertFalse(classes.isEmpty());
        }

        @Test
        @DisplayName("Should handle mix of valid and null package names")
        void shouldHandleMixOfValidAndNullPackages() throws Exception {
            // Given
            new ParallelClasspathScanner(classLoader, sink, knowledgeBase,
                    "com.threeamigos.common.util.implementations.injection.testpackages.interfaces",
                    null,
                    "com.threeamigos.common.util.implementations.injection.testpackages.abstractclasses"
            );

            // When
            Collection<Class<?>> classes = sink.getClasses();

            // Then
            assertFalse(classes.isEmpty());
        }
    }

    @Nested
    @DisplayName("Multiple Package Scanning Tests")
    class MultiplePackageScanningTests {

        @Test
        @DisplayName("Should scan multiple packages successfully")
        void shouldScanMultiplePackages() throws Exception {
            // Given
            new ParallelClasspathScanner(classLoader, sink, knowledgeBase,
                    "com.threeamigos.common.util.implementations.injection.testpackages.interfaces",
                    "com.threeamigos.common.util.implementations.injection.testpackages.abstractclasses"
            );

            // When
            Collection<Class<?>> classes = sink.getClasses();

            // Then
            assertFalse(classes.isEmpty());
            assertTrue(classes.size() >= 2, "Should contain classes from multiple packages");
        }
    }

    @Nested
    @DisplayName("Directory Content Tests")
    class DirectoryContentTests {

        @Test
        @DisplayName("Should handle empty directory gracefully")
        void shouldHandleEmptyDirectory() throws Exception {
            // Given
            ParallelClasspathScanner sut = new ParallelClasspathScanner(classLoader, sink, knowledgeBase);
            sink.getClasses().clear();

            File emptyDir = tempDir.resolve("empty").toFile();
            assertTrue(emptyDir.mkdir());

            // When
            sut.findClassesInDirectory(classLoader, emptyDir, "test.package", sink);

            // Then
            assertTrue(sink.getClasses().isEmpty());
        }

        @Test
        @DisplayName("Should skip non-class files in directory")
        @SuppressWarnings("ResultOfMethodCallIgnored")
        void shouldSkipNonClassFiles() throws Exception {
            // Given
            ParallelClasspathScanner sut = new ParallelClasspathScanner(classLoader, sink, knowledgeBase);
            sink.getClasses().clear();

            File dir = tempDir.resolve("nonclass").toFile();
            assertTrue(dir.mkdir());

            // Create non-.class files
            new File(dir, "readme.txt").createNewFile();
            new File(dir, "config.xml").createNewFile();
            new File(dir, "data.json").createNewFile();

            // When
            sut.findClassesInDirectory(classLoader, dir, "test.package", sink);

            // Then
            assertTrue(sink.getClasses().isEmpty(), "Should not find any classes in directory with only non-.class files");
        }

        @Test
        @DisplayName("Should handle empty package name in directory scanning")
        void shouldHandleEmptyPackageInDirectory() throws Exception {
            // Given - Create a simple test directory structure
            ParallelClasspathScanner sut = new ParallelClasspathScanner(classLoader, sink, knowledgeBase);
            sink.getClasses().clear();

            File testDir = tempDir.resolve("testpkg").toFile();
            assertTrue(testDir.mkdir());

            // When - Scan with empty package name
            sut.findClassesInDirectory(classLoader, testDir, "", sink);

            // Then - Should return empty list (no .class files in our test dir)
            assertTrue(sink.getClasses().isEmpty(), "Should handle empty package name without crashing");
        }

        @Test
        @DisplayName("Should return empty collection when listFiles returns null due to unreadable directory")
        @SuppressWarnings("ResultOfMethodCallIgnored")
        void shouldHandleUnreadableDirectory() throws Exception {
            // Given
            ParallelClasspathScanner sut = new ParallelClasspathScanner(classLoader, sink, knowledgeBase);
            sink.getClasses().clear();

            File unreadableDir = tempDir.resolve("unreadable").toFile();
            assertTrue(unreadableDir.mkdir());

            // When - Try to make directory unreadable (platform-dependent behavior)
            boolean madeUnreadable = false;
            try {
                // Try POSIX permissions first (Unix/Linux/Mac)
                if (Files.getFileStore(tempDir).supportsFileAttributeView("posix")) {
                    Files.setPosixFilePermissions(unreadableDir.toPath(), PosixFilePermissions.fromString("---------"));
                    madeUnreadable = true;
                } else {
                    // Fallback to setReadable for Windows
                    madeUnreadable = unreadableDir.setReadable(false);
                }
            } catch (Exception e) {
                // If we can't make it unreadable, skip the test
                System.out.println("Skipping unreadable directory test - cannot modify permissions on this platform");
            }

            if (madeUnreadable) {
                try {
                    // When
                    sut.findClassesInDirectory(classLoader, unreadableDir, "test.package", sink);

                    // Then - Should return empty collection gracefully (listFiles returns null)
                    assertTrue(sink.getClasses().isEmpty(), "Should handle unreadable directory gracefully");
                } finally {
                    // Cleanup - restore permissions so JUnit can delete temp directory
                    try {
                        if (Files.getFileStore(tempDir).supportsFileAttributeView("posix")) {
                            Files.setPosixFilePermissions(unreadableDir.toPath(), PosixFilePermissions.fromString("rwxr-xr-x"));
                        } else {
                            unreadableDir.setReadable(true);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle exceptions from package scanning")
        void shouldHandleExceptionsFromPackageScanning() throws Exception {
            // Given - Use a ClassLoader that throws IOException
            ClassLoader mockLoader = new ClassLoader() {
                @Override
                public Enumeration<URL> getResources(String name) throws IOException {
                    throw new IOException("Test error");
                }
            };

            // When / Then
            assertThrows(IOException.class, () -> new ParallelClasspathScanner(mockLoader, sink,  knowledgeBase,"test.package"));
        }

        @Test
        @DisplayName("Should handle directory with null listFiles result")
        void shouldHandleNullListFilesResult() throws Exception {
            // Given
            ParallelClasspathScanner sut = new ParallelClasspathScanner(classLoader, sink, knowledgeBase);
            sink.getClasses().clear();

            // When - Try to scan a path that will cause listFiles() to return null
            File nonExistentDir = new File("/nonexistent/path/that/does/not/exist");
            sut.findClassesInDirectory(classLoader, nonExistentDir, "test.package", sink);
            Collection<Class<?>> classes = sink.getClasses();

            // Then
            assertTrue(classes.isEmpty());
        }
    }

    @Nested
    @DisplayName("Thread Safety Tests")
    class ThreadSafetyTests {

        @Test
        @DisplayName("Should handle concurrent package scanning")
        void shouldHandleConcurrentAccess() throws Exception {
            // Given
            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            // When - multiple threads create scanners simultaneously
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await(); // Wait for all threads to be ready
                        ClassLoader loader = Thread.currentThread().getContextClassLoader();
                        SimpleClassConsumer localSink = new SimpleClassConsumer();

                        new ParallelClasspathScanner(loader, localSink, knowledgeBase, "com.threeamigos.common.util.implementations.injection");

                        Collection<Class<?>> classes = localSink.getClasses();
                        if (!classes.isEmpty()) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown(); // Release all threads at once

            // Then
            assertTrue(endLatch.await(15, TimeUnit.SECONDS));
            executor.shutdown();

            assertTrue(successCount.get() >= 1, "At least one thread should succeed");
            assertEquals(0, failureCount.get(), "No failures should occur");
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle package name with numbers and underscores")
        void shouldHandleUnusualPackageNames() throws Exception {
            // Given - While not common, package names can have numbers and underscores
            new ParallelClasspathScanner(classLoader, sink, knowledgeBase, "com.threeamigos");

            // When
            Collection<Class<?>> classes = sink.getClasses();

            // Then
            assertFalse(classes.isEmpty());
        }

        @Test
        @DisplayName("Should handle repeated scans with different sinks")
        void shouldHandleRepeatedScans() throws Exception {
            // Given
            SimpleClassConsumer sink1 = new SimpleClassConsumer();
            SimpleClassConsumer sink3 = new SimpleClassConsumer();
            SimpleClassConsumer sink2 = new SimpleClassConsumer();

            // When - Call multiple times
            new ParallelClasspathScanner(classLoader, sink1,  knowledgeBase,"com.threeamigos.common.util.implementations.injection");

            new ParallelClasspathScanner(classLoader, sink2,  knowledgeBase,"com.threeamigos.common.util.implementations.injection");

            new ParallelClasspathScanner(classLoader, sink3,  knowledgeBase,"com.threeamigos.common.util.implementations.injection");

            Collection<Class<?>> result1 = sink1.getClasses();
            Collection<Class<?>> result2 = sink2.getClasses();
            Collection<Class<?>> result3 = sink3.getClasses();

            // Then - All should return similar results
            assertEquals(result1.size(), result2.size(), "Repeated scans should return equal results");
            assertEquals(result2.size(), result3.size(), "Repeated scans should return equal results");
            assertFalse(result1.isEmpty());
        }

        @Test
        @DisplayName("Should handle deeply nested package structure")
        void shouldHandleDeeplyNestedPackages() throws Exception {
            // Given - Test with deeply nested package
            new ParallelClasspathScanner(classLoader, sink, knowledgeBase, "com.threeamigos.common.util.implementations.injection.interfaces.singleimplementation");

            // When
            Collection<Class<?>> classes = sink.getClasses();

            // Then
            assertNotNull(classes);
            // May or may not find classes depending on structure, but should not crash
        }
    }

    @Nested
    @DisplayName("Protocol Handling Tests")
    class ProtocolHandlingTests {

        @Test
        @DisplayName("Should handle file protocol correctly")
        void shouldHandleFileProtocol() throws Exception {
            // Given
            ParallelClasspathScanner sut = new ParallelClasspathScanner(classLoader, sink, knowledgeBase);
            URL classUrl = getClass().getClassLoader().getResource("com/threeamigos/common/util/implementations/injection");
            assertNotNull(classUrl);
            assertEquals("file", classUrl.getProtocol());

            // When
            sut.getClassesFromResource(classLoader, classUrl, "com.threeamigos.common.util.implementations.injection", sink);

            // Then
            assertFalse(sink.getClasses().isEmpty());
        }

        @Test
        @DisplayName("Should throw exception for unknown protocol")
        void shouldThrowExceptionForUnknownProtocol() throws Exception {
            // Given
            ParallelClasspathScanner sut = new ParallelClasspathScanner(classLoader, sink, knowledgeBase);
            URL mockUrl = new URL("http://example.com/test");

            // When/Then
            assertThrows(IllegalArgumentException.class,
                    () -> sut.getClassesFromResource(classLoader, mockUrl, "test.package", sink));
        }
    }

    @Nested
    @DisplayName("Should find classes in a JAR file")
    class JARFileTests {

        @ParameterizedTest
        @DisplayName("Should find classes in a JAR file")
        @MethodSource("com.threeamigos.common.util.implementations.injection.ParallelClasspathScannerUnitTest#getPackageNamesToFilter")
        void shouldFindClassesInJar(String packageNameToFilter) throws Exception {
            // 1. Create a dummy JAR file in the temp directory
            Path jarPath = tempDir.resolve("test-classes.jar");
            String packageName = "com.threeamigos.common.util.implementations.injection";
            String baseEntryName = packageName.replace('.', '/');
            createJarFile(jarPath, baseEntryName);

            // 3. Create a custom ClassLoader WITH parent delegation
            URL[] urls = {jarPath.toUri().toURL()};
            try (URLClassLoader testLoader = createURLClassLoader(urls, packageName, baseEntryName)) {
                SimpleClassConsumer jarSink = new SimpleClassConsumer();

                new ParallelClasspathScanner(testLoader, jarSink, knowledgeBase, packageNameToFilter);

                Collection<Class<?>> classes = jarSink.getClasses();
                String classNameToFind = SingleImplementationClass.class.getSimpleName();
                Class<?> result = classes.stream().filter(c -> c.getSimpleName().equals(classNameToFind)).findFirst().orElse(null);

                assertNotNull(result);

                // This should now pass because we are explicitly using the loader that knows about the JAR
                String location = result.getProtectionDomain().getCodeSource().getLocation().toString();
                assertTrue(location.endsWith(".jar"), "Class should be loaded from JAR, but was: " + location);
            }
        }

        @Test
        @DisplayName("Should handle non-standard JAR URLs using fallback logic")
        void shouldHandleNonStandardJarUrl() throws Exception {
            ParallelClasspathScanner sut = new ParallelClasspathScanner(classLoader, sink, knowledgeBase);

            // 1. Create a URL string with an unencoded space.
            String nonStandardUrl = "jar:file:/C:/My Documents/test.jar!/com/package";

            // 2. Create the URL object
            URL mockUrl = new URL(nonStandardUrl);

            // When/Then - Should throw IOException when trying to open the JAR
            assertThrows(IOException.class, () -> sut.findClassesInJar(classLoader, mockUrl, "com.package", sink));
        }

        private void createJarFile(Path jarPath, String baseEntryName) throws Exception {
            URL classUrl = getClass().getClassLoader().getResource(baseEntryName);
            assertNotNull(classUrl, "Could not find the compiled class file to package");

            URL rootUrl = getClass().getClassLoader().getResource(baseEntryName);
            assertNotNull(rootUrl, "Could not find the test classes directory");
            File packageDir = new File(rootUrl.toURI());

            //noinspection IOStreamConstructor
            try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
                addFiles(jos, baseEntryName, packageDir);

                // Add a fake test file for testing ClasspathScanner filters
                jos.putNextEntry(new JarEntry("com/threeamigos/common/utils/fakeFileToSkip"));
                jos.write("This file is used to test ClasspathScanner filters.".getBytes());
                jos.closeEntry();
            }
        }

        private void addFiles(JarOutputStream jos, String baseEntryName, File dir) throws Exception {
            jos.putNextEntry(new JarEntry(baseEntryName + "/"));
            jos.closeEntry();
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        addFiles(jos, baseEntryName + "/" + file.getName(), file);
                    } else {
                        String entryName = baseEntryName + "/" + file.getName();
                        jos.putNextEntry(new JarEntry(entryName));
                        Files.copy(file.toPath(), jos);
                        jos.closeEntry();
                    }
                }
            }
        }

        private URLClassLoader createURLClassLoader(URL[] urls, String packageName, String baseEntryName) {
            return new URLClassLoader(urls, getClass().getClassLoader()) {
                @Override
                public Class<?> loadClass(String name) throws ClassNotFoundException {
                    if (name.startsWith(packageName)) {
                        try {
                            return findClass(name);
                        } catch (ClassNotFoundException e) {
                            // ignore, fall through to parent
                        }
                    }
                    return super.loadClass(name);
                }

                @Override
                public Enumeration<URL> getResources(String name) throws IOException {
                    if (name.equals(baseEntryName)) {
                        return findResources(name);
                    }
                    return super.getResources(name);
                }
            };
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should successfully scan entire common-utils injection package")
        void shouldScanEntireInjectionPackage() throws Exception {
            // Given
            new ParallelClasspathScanner(classLoader, sink, knowledgeBase, "com.threeamigos.common.util.implementations.injection");

            // When
            Collection<Class<?>> classes = sink.getClasses();

            // Then
            assertFalse(classes.isEmpty());
            assertTrue(classes.size() > 10, "Should find multiple classes in injection package");

            // Verify it includes ParallelClasspathScanner itself
            assertTrue(classes.contains(ParallelClasspathScanner.class));
        }

        @Test
        @DisplayName("Should handle no packages specified (scan everything)")
        void shouldHandleNoPackagesSpecified() throws Exception {
            // Given
            new ParallelClasspathScanner(classLoader, sink, knowledgeBase);

            // When
            Collection<Class<?>> classes = sink.getClasses();

            // Then
            assertFalse(classes.isEmpty());
            assertTrue(classes.size() > 50, "Should find many classes when scanning entire classpath");
        }
    }

    static String[] getPackageNamesToFilter() {
        return new String[]{"com.threeamigos.common.util.implementations.injection", "", null};
    }
}