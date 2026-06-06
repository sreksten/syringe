package com.threeamigos.common.util.implementations.injection.bce;

import jakarta.enterprise.lang.model.declarations.ClassInfo;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BceMetadataClassLoadingTest {

    @Test
    void unwrapClassInfoUsesTcclForDeploymentOnlyClasses() throws Exception {
        String className = "org.jboss.cdi.tck.tests.bce.tccl.TcclOnlyBean";
        Path tempRoot = Files.createTempDirectory("syringe-bce-metadata");
        compileTcclOnlyClass(tempRoot, className);

        ClassLoader previousTccl = Thread.currentThread().getContextClassLoader();
        URLClassLoader deploymentClassLoader = new URLClassLoader(new URL[]{tempRoot.toUri().toURL()}, previousTccl);
        try {
            Thread.currentThread().setContextClassLoader(deploymentClassLoader);
            Class<?> expected = Class.forName(className, false, deploymentClassLoader);
            Class<?> resolved = BceMetadata.unwrapClassInfo(classInfo(className));
            assertEquals(expected, resolved);
        } finally {
            Thread.currentThread().setContextClassLoader(previousTccl);
            deploymentClassLoader.close();
        }
    }

    private static ClassInfo classInfo(String className) {
        return (ClassInfo) Proxy.newProxyInstance(
                BceMetadataClassLoadingTest.class.getClassLoader(),
                new Class<?>[]{ClassInfo.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("name".equals(methodName)) {
                        return className;
                    }
                    if ("toString".equals(methodName)) {
                        return "ClassInfo[" + className + "]";
                    }
                    if ("hashCode".equals(methodName)) {
                        return className.hashCode();
                    }
                    if ("equals".equals(methodName)) {
                        return proxy == args[0];
                    }
                    throw new UnsupportedOperationException("Unsupported method: " + methodName);
                });
    }

    private static void compileTcclOnlyClass(Path outputRoot, String className) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("System Java compiler is unavailable");
        }

        int lastDot = className.lastIndexOf('.');
        String packageName = className.substring(0, lastDot);
        String simpleName = className.substring(lastDot + 1);
        Path sourceFile = outputRoot.resolve(className.replace('.', '/') + ".java");
        Files.createDirectories(sourceFile.getParent());
        String source = "package " + packageName + ";\n" +
                "public class " + simpleName + " {\n" +
                "}\n";
        Files.write(sourceFile, source.getBytes(StandardCharsets.UTF_8));

        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8);
        try {
            Iterable<? extends JavaFileObject> compilationUnits =
                    fileManager.getJavaFileObjectsFromFiles(Collections.singletonList(sourceFile.toFile()));
            String classPath = System.getProperty("java.class.path");
            boolean compiled = Boolean.TRUE.equals(compiler.getTask(
                    null,
                    fileManager,
                    null,
                    java.util.Arrays.asList("-classpath", classPath, "-d", outputRoot.toString()),
                    null,
                    compilationUnits
            ).call());
            assertTrue(compiled, "Failed to compile " + className + " for TCCL regression test");
        } finally {
            fileManager.close();
        }
    }
}
