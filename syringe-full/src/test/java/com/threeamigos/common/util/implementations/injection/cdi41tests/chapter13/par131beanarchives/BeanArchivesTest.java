package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter13.par131beanarchives;

import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveDetector;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("13.1 - Bean archives tests")
public class BeanArchivesTest {

    @Test
    @DisplayName("13.1 - Empty META-INF/beans.xml defines an implicit bean archive")
    public void shouldTreatEmptyBeansXmlAsImplicitArchive() throws Exception {
        Path root = Files.createTempDirectory("bean-archive-empty");
        write(root, "META-INF/beans.xml", "<beans xmlns=\"https://jakarta.ee/xml/ns/jakartaee\"/>");

        BeanArchiveDetector detector = new BeanArchiveDetector();
        assertEquals(BeanArchiveMode.IMPLICIT, detector.detectArchiveMode(root.toFile()));
    }

    @Test
    @DisplayName("13.1 - bean-discovery-mode=annotated defines an implicit bean archive")
    public void shouldTreatAnnotatedModeAsImplicitArchive() throws Exception {
        Path root = Files.createTempDirectory("bean-archive-annotated");
        write(root, "META-INF/beans.xml",
                "<beans xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" bean-discovery-mode=\"annotated\"/>");

        BeanArchiveDetector detector = new BeanArchiveDetector();
        assertEquals(BeanArchiveMode.IMPLICIT, detector.detectArchiveMode(root.toFile()));
    }

    @Test
    @DisplayName("13.1 - bean-discovery-mode=none is not a bean archive")
    public void shouldTreatNoneModeAsNotBeanArchive() throws Exception {
        Path root = Files.createTempDirectory("bean-archive-none");
        write(root, "META-INF/beans.xml",
                "<beans xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" bean-discovery-mode=\"none\"/>");

        BeanArchiveDetector detector = new BeanArchiveDetector();
        assertEquals(BeanArchiveMode.NONE, detector.detectArchiveMode(root.toFile()));
    }

    @Test
    @DisplayName("13.1 - bean-discovery-mode=all is treated as a deployment problem in CDI Lite")
    public void shouldFlagAllModeAsDeploymentProblemInCdiLite() throws Exception {
        Path root = Files.createTempDirectory("bean-archive-all");
        write(root, "META-INF/beans.xml",
                "<beans xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" bean-discovery-mode=\"all\"/>");

        KnowledgeBase knowledgeBase = new KnowledgeBase(new InMemoryMessageHandler());
        BeanArchiveDetector detector = new BeanArchiveDetector(knowledgeBase);
        assertEquals(BeanArchiveMode.EXPLICIT, detector.detectArchiveMode(root.toFile()));
        assertTrue(!knowledgeBase.getDefinitionErrors().isEmpty());
        assertTrue(knowledgeBase.getDefinitionErrors().get(0).contains("bean-discovery-mode=\"all\""));
    }

    @Test
    @DisplayName("13.1 - beans.xml must be named META-INF/beans.xml")
    public void shouldIgnoreBeansXmlInWrongLocation() throws Exception {
        Path root = Files.createTempDirectory("bean-archive-wrong-location");
        write(root, "beans.xml",
                "<beans xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" bean-discovery-mode=\"none\"/>");

        BeanArchiveDetector detector = new BeanArchiveDetector();
        assertEquals(BeanArchiveMode.IMPLICIT, detector.detectArchiveMode(root.toFile()));
    }

    @Test
    @DisplayName("13.1 - Archive with portable extension and no beans.xml is not a bean archive")
    public void shouldTreatPortableExtensionArchiveWithoutBeansXmlAsNotBeanArchive() throws Exception {
        Path root = Files.createTempDirectory("bean-archive-portable-ext");
        write(root, "META-INF/services/jakarta.enterprise.inject.spi.Extension",
                "com.example.MyPortableExtension");

        BeanArchiveDetector detector = new BeanArchiveDetector();
        assertEquals(BeanArchiveMode.NONE, detector.detectArchiveMode(root.toFile()));
    }

    @Test
    @DisplayName("13.1 - Archive with build compatible extension and no beans.xml is not a bean archive")
    public void shouldTreatBuildCompatibleExtensionArchiveWithoutBeansXmlAsNotBeanArchive() throws Exception {
        Path root = Files.createTempDirectory("bean-archive-bce-ext");
        write(root, "META-INF/services/jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension",
                "com.example.MyBuildCompatibleExtension");

        BeanArchiveDetector detector = new BeanArchiveDetector();
        assertEquals(BeanArchiveMode.NONE, detector.detectArchiveMode(root.toFile()));
    }

    @Test
    @DisplayName("13.1 - Non bean-discovery beans.xml sections are ignored for archive mode in CDI Lite")
    public void shouldIgnoreNonDiscoveryBeansXmlSectionsForArchiveMode() throws Exception {
        Path root = Files.createTempDirectory("bean-archive-ignore-content");
        write(root, "META-INF/beans.xml",
                "<beans xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" bean-discovery-mode=\"annotated\">" +
                        "<alternatives><class>com.example.Alt</class></alternatives>" +
                        "<interceptors><class>com.example.Int</class></interceptors>" +
                        "<decorators><class>com.example.Dec</class></decorators>" +
                        "</beans>");

        BeanArchiveDetector detector = new BeanArchiveDetector();
        assertEquals(BeanArchiveMode.IMPLICIT, detector.detectArchiveMode(root.toFile()));
    }

    private static void write(Path root, String relativePath, String content) throws Exception {
        Path target = root.resolve(relativePath);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(target, content.getBytes(StandardCharsets.UTF_8));
    }
}
