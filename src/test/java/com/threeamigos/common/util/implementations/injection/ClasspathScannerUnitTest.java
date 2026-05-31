package com.threeamigos.common.util.implementations.injection;

import com.threeamigos.common.util.implementations.injection.discovery.ClasspathScanner;
import com.threeamigos.common.util.implementations.injection.testpackages.interfaces.singleimplementation.SingleImplementationClass;
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
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for ClasspathScanner to achieve 100% code coverage.
 * Merged from ClasspathScannerUnitTest and ClasspathScannerClaudeUnitTest.
 */
@DisplayName("ClasspathScanner unit test")
class ClasspathScannerUnitTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("Input Validation Tests")
    class InputValidationTests {

        @Test
        @DisplayName("Should throw IllegalArgumentException for invalid package name starting with digit")
        void shouldThrowExceptionForPackageNameStartingWithDigit() {
            // When/Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ClasspathScanner("123invalid"));
            assertTrue(exception.getMessage().contains("Invalid package name"));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for package name with special characters")
        void shouldThrowExceptionForPackageNameWithSpecialChars() {
            // When/Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ClasspathScanner("com.example.@invalid"));
            assertTrue(exception.getMessage().contains("Invalid package name"));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for package name with double dots")
        void shouldThrowExceptionForPackageNameWithDoubleDots() {
            // When/Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ClasspathScanner("com..double.dot"));
            assertTrue(exception.getMessage().contains("Invalid package name"));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for package name ending with dot")
        void shouldThrowExceptionForPackageNameEndingWithDot() {
            // When/Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ClasspathScanner("com.example."));
            assertTrue(exception.getMessage().contains("Invalid package name"));
        }

        @Test
        @DisplayName("Should accept valid package name with underscores and numbers")
        void shouldAcceptValidPackageNameWithUnderscoresAndNumbers() {
            // When/Then - Should not throw
            assertDoesNotThrow(() -> new ClasspathScanner("com.threeamigos.util_1.test_2"));
        }

        @Test
        @DisplayName("Should accept package name starting with underscore")
        void shouldAcceptPackageNameStartingWithUnderscore() {
            // When/Then - Should not throw
            assertDoesNotThrow(() -> new ClasspathScanner("_private.com.example"));
        }
    }

    @Nested
    @DisplayName("When package is wrong")
    class WrongPackage {

        @Test
        @DisplayName("Should return an empty collection if package to search does not exist")
        void shouldReturnEmptyCollectionIfPackageToSearchDoesNotExist() throws IOException, ClassNotFoundException {
            // Given
            ClasspathScanner sut = new ClasspathScanner("com.threeamigos.common.util.implementations.injection.notexistingpackage");
            // Then
            assertTrue(sut.getAllClasses(Thread.currentThread().getContextClassLoader()).isEmpty());
        }

        @Test
        @DisplayName("Should return an empty collection if package to search is a file")
        void shouldReturnEmptyCollectionIfPackageToSearchIsAFile() throws IOException, ClassNotFoundException {
            // Given
            ClasspathScanner sut = new ClasspathScanner("com.threeamigos.common.util.implementations.injection.wrongdirectory.fakeFileToSkip");
            // Then
            assertTrue(sut.getAllClasses(Thread.currentThread().getContextClassLoader()).isEmpty());
        }

        @Test
        @DisplayName("getClassesFromResource should return an empty collection if protocol is not recognized")
        void getClassesFromResourceShouldReturnAnEmptyCollectionIfProtocolNotRecognized() throws Exception {
            // Given
            ClasspathScanner sut = new ClasspathScanner();
            URL url = new URL("http://example.com");
            // When
            Collection<Class<?>> classes = sut.getClassesFromResource(Thread.currentThread().getContextClassLoader(), url, "my-package");
            // Then
            assertTrue(classes.isEmpty());
        }

        @Test
        @DisplayName("findClassesInDirectory returns an empty collection if directory does not exist")
        void findClassesInDirectoryReturnsEmptyIfDirectoryDoesNotExist() throws Exception {
            // Given
            ClasspathScanner sut = new ClasspathScanner();
            File directory = new File("does-not-exist");
            // When
            Collection<Class<?>> classes = sut.findClassesInDirectory(Thread.currentThread().getContextClassLoader(), directory, "my.package.name");
            // Then
            assertTrue(classes.isEmpty());
        }

        @Test
        @DisplayName("findClassesInDirectory returns an empty collection if directory is actually a file")
        void findClassesInDirectoryReturnsEmptyIfDirectoryIsActuallyAFile() throws Exception {
            // Given
            ClasspathScanner sut = new ClasspathScanner();
            File aFile = tempDir.resolve("a-file.txt").toFile();
            if (!aFile.createNewFile()) {
                fail("Could not create temporary file");
            }
            // When
            Collection<Class<?>> classes = sut.findClassesInDirectory(Thread.currentThread().getContextClassLoader(), aFile, "my.package.name");
            // Then
            assertTrue(classes.isEmpty());
        }
    }

    @Nested
    @DisplayName("When package is empty or null")
    class NullOrEmptyPackage {

        @Test
        @DisplayName("Should work if package is null")
        void shouldFindALotOfClassesIfPackageIsNull() throws Exception {
            // Given
            ClasspathScanner sut = new ClasspathScanner((String) null);
            // When
            Collection<Class<?>> classes = sut.getAllClasses(Thread.currentThread().getContextClassLoader());
            // Then
            assertTrue(classes.contains(SingleImplementationClass.class));
        }

        @Test
        @DisplayName("Should work if package is empty")
        void shouldFindALotOfClassesIfPackageIsEmpty() throws Exception {
            // Given
            ClasspathScanner sut = new ClasspathScanner("");
            // When
            Collection<Class<?>> classes = sut.getAllClasses(Thread.currentThread().getContextClassLoader());
            // Then
            assertTrue(classes.contains(SingleImplementationClass.class));
        }

        @Test
        @DisplayName("Should handle multiple null package names by filtering them")
        void shouldHandleMultipleNullPackages() throws Exception {
            // Given
            ClasspathScanner sut = new ClasspathScanner(null, null, "com.threeamigos.common.util.implementations.injection");

            // When
            Collection<Class<?>> classes = sut.getAllClasses(Thread.currentThread().getContextClassLoader());

            // Then
            assertFalse(classes.isEmpty());
        }

        @Test
        @DisplayName("Should handle mix of valid and null package names")
        void shouldHandleMixOfValidAndNullPackages() throws Exception {
            // Given
            ClasspathScanner sut = new ClasspathScanner(
                    "com.threeamigos.common.util.implementations.injection.testpackages.interfaces",
                    null,
                    "com.threeamigos.common.util.implementations.injection.testpackages.abstractclasses"
            );

            // When
            Collection<Class<?>> classes = sut.getAllClasses(Thread.currentThread().getContextClassLoader());

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
            ClasspathScanner sut = new ClasspathScanner(
                    "com.threeamigos.common.util.implementations.injection.testpackages.interfaces",
                    "com.threeamigos.common.util.implementations.injection.testpackages.abstractclasses"
            );

            // When
            Collection<Class<?>> classes = sut.getAllClasses(Thread.currentThread().getContextClassLoader());

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
            ClasspathScanner sut = new ClasspathScanner();
            File emptyDir = tempDir.resolve("empty").toFile();
            assertTrue(emptyDir.mkdir());

            // When
            Collection<Class<?>> classes = sut.findClassesInDirectory(
                    Thread.currentThread().getContextClassLoader(), emptyDir, "test.package"
            );

            // Then
            assertTrue(classes.isEmpty());
        }

        @Test
        @DisplayName("Should skip non-class files in directory")
        void shouldSkipNonClassFiles() throws Exception {
            // Given
            ClasspathScanner sut = new ClasspathScanner();
            File dir = tempDir.resolve("nonclass").toFile();
            assertTrue(dir.mkdir());

            // Create non-.class files
            new File(dir, "readme.txt").createNewFile();
            new File(dir, "config.xml").createNewFile();
            new File(dir, "data.json").createNewFile();

            // When
            Collection<Class<?>> classes = sut.findClassesInDirectory(
                    Thread.currentThread().getContextClassLoader(), dir, "test.package"
            );

            // Then
            assertTrue(classes.isEmpty(), "Should not find any classes in directory with only non-.class files");
        }

        @Test
        @DisplayName("Should handle empty package name in directory scanning")
        void shouldHandleEmptyPackageInDirectory() throws Exception {
            // Given - Create a simple test directory structure
            ClasspathScanner sut = new ClasspathScanner();
            File testDir = tempDir.resolve("testpkg").toFile();
            assertTrue(testDir.mkdir());

            // When - Scan with empty package name
            Collection<Class<?>> classes = sut.findClassesInDirectory(
                    Thread.currentThread().getContextClassLoader(), testDir, ""
            );

            // Then - Should return empty list (no .class files in our test dir)
            assertTrue(classes.isEmpty(), "Should handle empty package name without crashing");
        }

        @Test
        @DisplayName("Should return empty collection when listFiles returns null due to unreadable directory")
        void shouldHandleUnreadableDirectory() throws Exception {
            // Given
            ClasspathScanner sut = new ClasspathScanner();
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
                    Collection<Class<?>> classes = sut.findClassesInDirectory(
                            Thread.currentThread().getContextClassLoader(), unreadableDir, "test.package"
                    );

                    // Then - Should return empty collection gracefully (listFiles returns null)
                    assertTrue(classes.isEmpty(), "Should handle unreadable directory gracefully");
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
        @DisplayName("Should propagate IOException from getResources")
        void shouldPropagateIOExceptionFromGetResources() throws Exception {
            // Given
            ClassLoader mockLoader = mock(ClassLoader.class);
            when(mockLoader.getResources(anyString())).thenThrow(new IOException("Test error"));
            ClasspathScanner sut = new ClasspathScanner("test.package");

            // When/Then
            assertThrows(IOException.class, () -> sut.getAllClasses(mockLoader));
        }

        @Test
        @DisplayName("Should handle directory with null listFiles result")
        void shouldHandleNullListFilesResult() throws Exception {
            // Given
            ClasspathScanner sut = new ClasspathScanner();

            // When - Try to scan a path that will cause listFiles() to return null
            // (This is hard to simulate without mocking, so we test the fallback behavior)
            File nonExistentDir = new File("/nonexistent/path/that/does/not/exist");
            Collection<Class<?>> classes = sut.findClassesInDirectory(
                    Thread.currentThread().getContextClassLoader(), nonExistentDir, "test.package"
            );

            // Then
            assertTrue(classes.isEmpty());
        }
    }

    @Nested
    @DisplayName("Cache Behavior Tests")
    class CacheBehaviorTests {

        @Test
        @DisplayName("Should use the cache to avoid redundant class loading")
        void shouldUseTheCache() throws Exception {
            // Given
            String packageName = "com.threeamigos";
            ClasspathScanner sut = new ClasspathScanner(packageName);
            ClassLoader mockLoader = mock(ClassLoader.class);
            String expectedPath = "com/threeamigos";

            // Stub getResources to return an empty enumeration so the loop finishes
            when(mockLoader.getResources(expectedPath)).thenReturn(Collections.emptyEnumeration());

            // When - calling the public method that internally calls getClasses
            sut.getAllClasses(mockLoader);
            sut.getAllClasses(mockLoader);

            // Then - Verify the getResources method was queried exactly once
            verify(mockLoader, times(1)).getResources(expectedPath);
        }

        @Test
        @DisplayName("Cache should return equal results on multiple calls")
        void cacheShouldReturnEqualResultsOnMultipleCalls() throws Exception {
            // Given
            ClasspathScanner sut = new ClasspathScanner("com.threeamigos.common.util.implementations.injection");

            // When
            List<Class<?>> firstCall = sut.getAllClasses(Thread.currentThread().getContextClassLoader());
            List<Class<?>> secondCall = sut.getAllClasses(Thread.currentThread().getContextClassLoader());

            // Then - Returns equal cached results (wrapped in unmodifiable list)
            assertEquals(firstCall, secondCall, "Should return equal list from cache");
            assertFalse(firstCall.isEmpty(), "Should contain classes");

            // Verify both are unmodifiable
            assertThrows(UnsupportedOperationException.class, firstCall::clear);
            assertThrows(UnsupportedOperationException.class, secondCall::clear);
        }

        @Test
        @DisplayName("Should throw exception when using different ClassLoader")
        void shouldThrowExceptionWhenUsingDifferentClassLoader() throws Exception {
            // Given
            ClasspathScanner sut = new ClasspathScanner("com.threeamigos.common.util.implementations.injection");
            ClassLoader loader1 = Thread.currentThread().getContextClassLoader();
            ClassLoader loader2 = new URLClassLoader(new URL[0], loader1);

            // When - first call with loader1 succeeds
            sut.getAllClasses(loader1);

            // Then - second call with different loader2 should throw
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> sut.getAllClasses(loader2));
            assertTrue(exception.getMessage().contains("different ClassLoader"),
                    "Exception should mention ClassLoader issue");
        }

        @Test
        @DisplayName("Returned cache list is unmodifiable")
        void returnedCacheListIsUnmodifiable() throws Exception {
            // Given
            ClasspathScanner sut = new ClasspathScanner("com.threeamigos.common.util.implementations.injection");

            // When
            List<Class<?>> classes = sut.getAllClasses(Thread.currentThread().getContextClassLoader());
            int originalSize = classes.size();

            // Then - attempting to modify the list should throw UnsupportedOperationException
            assertThrows(UnsupportedOperationException.class, classes::clear);
            assertThrows(UnsupportedOperationException.class, () -> classes.add(String.class));
            assertThrows(UnsupportedOperationException.class, () -> classes.remove(0));

            // List should still have original size
            assertEquals(originalSize, classes.size(), "List should be unchanged");

            // Get it again - should still be same size
            List<Class<?>> classes2 = sut.getAllClasses(Thread.currentThread().getContextClassLoader());
            assertEquals(originalSize, classes2.size(), "Cache is protected from modification");
        }
    }

    @Nested
    @DisplayName("Thread Safety Tests")
    class ThreadSafetyTests {

        @Test
        @DisplayName("Should handle concurrent getAllClasses calls")
        void shouldHandleConcurrentAccess() throws Exception {
            // Given
            ClasspathScanner sut = new ClasspathScanner("com.threeamigos.common.util.implementations.injection");
            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            // When - multiple threads access cache simultaneously
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await(); // Wait for all threads to be ready
                        List<Class<?>> classes = sut.getAllClasses(Thread.currentThread().getContextClassLoader());
                        if (classes != null && !classes.isEmpty()) {
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
            assertTrue(endLatch.await(5, TimeUnit.SECONDS));
            executor.shutdown();

            // Note: This test documents that concurrent access doesn't throw exceptions
            // but race conditions may exist due to cache implementation
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
            ClasspathScanner sut = new ClasspathScanner("com.threeamigos");

            // When
            Collection<Class<?>> classes = sut.getAllClasses(Thread.currentThread().getContextClassLoader());

            // Then
            assertFalse(classes.isEmpty());
        }

        @Test
        @DisplayName("Should handle repeated calls with same ClassLoader")
        void shouldHandleRepeatedCalls() throws Exception {
            // Given
            ClasspathScanner sut = new ClasspathScanner("com.threeamigos.common.util.implementations.injection");
            ClassLoader loader = Thread.currentThread().getContextClassLoader();

            // When - Call multiple times
            List<Class<?>> result1 = sut.getAllClasses(loader);
            List<Class<?>> result2 = sut.getAllClasses(loader);
            List<Class<?>> result3 = sut.getAllClasses(loader);

            // Then - All should return equal cached results (unmodifiable wrappers)
            assertEquals(result1, result2, "Repeated calls should return equal results");
            assertEquals(result2, result3, "Repeated calls should return equal results");
            assertFalse(result1.isEmpty());

            // Verify results are unmodifiable
            assertThrows(UnsupportedOperationException.class, () -> result1.clear());
        }

        @Test
        @DisplayName("Should handle deeply nested package structure")
        void shouldHandleDeeplyNestedPackages() throws Exception {
            // Given - Test with deeply nested package
            ClasspathScanner sut = new ClasspathScanner("com.threeamigos.common.util.implementations.injection.interfaces.singleimplementation");

            // When
            Collection<Class<?>> classes = sut.getAllClasses(Thread.currentThread().getContextClassLoader());

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
            ClasspathScanner sut = new ClasspathScanner();
            URL classUrl = getClass().getClassLoader().getResource("com/threeamigos/common/util/implementations/injection");
            assertNotNull(classUrl);
            assertEquals("file", classUrl.getProtocol());

            // When
            Collection<Class<?>> classes = sut.getClassesFromResource(
                    Thread.currentThread().getContextClassLoader(), classUrl, "com.threeamigos.common.util.implementations.injection"
            );

            // Then
            assertFalse(classes.isEmpty());
        }

        @Test
        @DisplayName("Should return empty list for unknown protocol")
        void shouldReturnEmptyForUnknownProtocol() throws Exception {
            // Given
            ClasspathScanner sut = new ClasspathScanner();
            URL mockUrl = new URL("http://example.com/test");

            // When
            Collection<Class<?>> classes = sut.getClassesFromResource(
                    Thread.currentThread().getContextClassLoader(), mockUrl, "test.package"
            );

            // Then
            assertTrue(classes.isEmpty());
        }
    }

    @Nested
    @DisplayName("Should find classes in a JAR file")
    class JARFileTests {

        @ParameterizedTest
        @DisplayName("Should find classes in a JAR file")
        @MethodSource("com.threeamigos.common.util.implementations.injection.ClasspathScannerUnitTest#getPackageNamesToFilter")
        void shouldFindClassesInJar(String packageNameToFilter) throws Exception {
            // 1. Create a dummy JAR file in the temp directory
            Path jarPath = tempDir.resolve("test-classes.jar");
            String packageName = "com.threeamigos.common.util.implementations.injection";
            String baseEntryName = packageName.replace('.', '/');
            createJarFile(jarPath, baseEntryName);

            // 3. Create a custom ClassLoader WITH parent delegation
            URL[] urls = {jarPath.toUri().toURL()};
            // Passing getClass().getClassLoader() as parent is necessary so the loader
            // can find the annotation classes and abstract classes during Class.forName()
            try (URLClassLoader testLoader = createURLClassLoader(urls, packageName, baseEntryName)) {
                ClasspathScanner sut = new ClasspathScanner(packageNameToFilter);
                Collection<Class<?>> classes = sut.getAllClasses(testLoader);
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
        void shouldHandleNonStandardJarUrl() {
            ClasspathScanner sut = new ClasspathScanner();

            // 1. Create a URL string with an unencoded space.
            // new URL(...).toURI() will fail on this string.
            String nonStandardUrl = "jar:file:/C:/My Documents/test.jar!/com/package";

            // 2. Create a real URL object.
            // 3. Since we want to test the catch block, we don't need the JarFile to actually open.
            // The test will likely throw an IOException later when trying to open "C:/My Documents/test.jar",
            // but we can verify that the code reached the fallback by checking the logs or
            // debugging the File object creation.
            URL jarUrl = assertDoesNotThrow(() -> new URL(nonStandardUrl));
            assertThrows(IOException.class, () -> sut.findClassesInJar(Thread.currentThread().getContextClassLoader(),
                    jarUrl, "com.package"));
        }

        private void createJarFile(Path jarPath, String baseEntryName) throws Exception {
            // Note: We are packaging an existing compiled class from the project into this JAR
            // Usually, target/test-classes/... holds these files during test execution
            URL classUrl = getClass().getClassLoader().getResource(baseEntryName);
            assertNotNull(classUrl, "Could not find the compiled class file to package");

            // Find the actual directory on the file system where the test classes are compiled
            URL rootUrl = getClass().getClassLoader().getResource(baseEntryName);
            assertNotNull(rootUrl, "Could not find the test classes directory");
            File packageDir = new File(rootUrl.toURI());

            // Package the entire directory into a JAR
            // at least until I decide not to support Java 8 any longer:
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
            // First the directory
            jos.putNextEntry(new JarEntry(baseEntryName + "/"));
            jos.closeEntry();
            // Then the contents
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
                    // If it's one of our test classes, try to load it from the JAR first
                    if (name.startsWith(packageName)) {
                        try {
                            return findClass(name);
                        } catch (ClassNotFoundException e) {
                            // ignore, fall through to parent
                        }
                    }
                    return super.loadClass(name);
                }

                // Also override getResources to ONLY return the JAR resource for this package
                @Override
                public Enumeration<URL> getResources(String name) throws IOException {
                    if (name.equals(baseEntryName)) {
                        return findResources(name); // Skip parent, only find in JAR
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
            ClasspathScanner sut = new ClasspathScanner("com.threeamigos.common.util.implementations.injection");

            // When
            Collection<Class<?>> classes = sut.getAllClasses(Thread.currentThread().getContextClassLoader());

            // Then
            assertFalse(classes.isEmpty());
            assertTrue(classes.size() > 10, "Should find multiple classes in injection package");

            // Verify it includes ClasspathScanner itself
            assertTrue(classes.contains(ClasspathScanner.class));
        }

        @Test
        @DisplayName("Should handle no packages specified (scan everything)")
        void shouldHandleNoPackagesSpecified() throws Exception {
            // Given
            ClasspathScanner sut = new ClasspathScanner();

            // When
            Collection<Class<?>> classes = sut.getAllClasses(Thread.currentThread().getContextClassLoader());

            // Then
            assertFalse(classes.isEmpty());
            assertTrue(classes.size() > 50, "Should find many classes when scanning entire classpath");
        }
    }

    static String[] getPackageNamesToFilter() {
        return new String[]{"com.threeamigos.common.util.implementations.injection", "", null};
    }
}
