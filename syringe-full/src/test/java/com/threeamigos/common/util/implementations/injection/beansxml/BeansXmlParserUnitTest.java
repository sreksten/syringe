package com.threeamigos.common.util.implementations.injection.beansxml;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Execution(ExecutionMode.SAME_THREAD)
@Isolated
class BeansXmlParserUnitTest {

    @BeforeEach
    void resetCache() {
        BeansXmlParser.clearCache();
    }

    @Test
    void parseNullReturnsDefaultBeansXml() {
        BeansXmlParser parser = new BeansXmlParser();

        BeansXml beansXml = parser.parse(null);

        assertEquals("annotated", beansXml.getBeanDiscoveryMode());
        assertTrue(beansXml.getAlternatives().isEmpty());
        assertTrue(beansXml.getInterceptors().isEmpty());
        assertTrue(beansXml.getDecorators().isEmpty());
        assertNull(beansXml.getScan());
        assertFalse(beansXml.isTrimEnabled());
    }

    @Test
    void parseValidXmlPreservesScanAndTrim() {
        String xml = "<beans xmlns=\"http://xmlns.jcp.org/xml/ns/javaee\" bean-discovery-mode=\"all\" version=\"4.0\">" +
                "<alternatives><class>com.example.MockService</class><stereotype>com.example.Mock</stereotype></alternatives>" +
                "<interceptors><class>com.example.TransactionalInterceptor</class></interceptors>" +
                "<decorators><class>com.example.LoggingDecorator</class></decorators>" +
                "<scan>" +
                "<exclude name=\"com.example.legacy.**\"/>" +
                "<exclude name=\"com.example.tests.*\">" +
                "<if-class-available name=\"org.junit.jupiter.api.Test\"/>" +
                "<if-class-not-available name=\"com.example.OptionalFeature\"/>" +
                "<if-system-property name=\"env\" value=\"production\"/>" +
                "</exclude>" +
                "</scan>" +
                "<trim/>" +
                "</beans>";

        BeansXml beansXml = new BeansXmlParser().parse(stream(xml));

        assertEquals("all", beansXml.getBeanDiscoveryMode());
        assertEquals("4.0", beansXml.getVersion());
        assertEquals(java.util.Collections.singletonList("com.example.MockService"), beansXml.getAlternatives().getClasses());
        assertEquals(java.util.Collections.singletonList("com.example.Mock"), beansXml.getAlternatives().getStereotypes());
        assertEquals(java.util.Collections.singletonList("com.example.TransactionalInterceptor"), beansXml.getInterceptors().getClasses());
        assertEquals(java.util.Collections.singletonList("com.example.LoggingDecorator"), beansXml.getDecorators().getClasses());

        Scan scan = beansXml.getScan();
        assertNotNull(scan);
        List<Exclude> excludes = scan.getExcludes();
        assertEquals(2, excludes.size());

        Exclude unconditional = excludes.get(0);
        assertTrue(unconditional.isUnconditional());
        assertTrue(unconditional.matches("com.example.legacy.SomeClass"));
        assertTrue(unconditional.matches("com.example.legacy.subpkg.Another"));

        Exclude conditional = excludes.get(1);
        assertFalse(conditional.isUnconditional());
        assertTrue(conditional.matches("com.example.tests.Foo"));
        assertFalse(conditional.matches("com.example.tests.sub.Foo"));
        assertEquals("org.junit.jupiter.api.Test", conditional.getIfClassAvailable().get(0).getName());
        assertEquals("com.example.OptionalFeature", conditional.getIfClassNotAvailable().get(0).getName());
        IfSystemProperty systemProperty = conditional.getIfSystemProperty().get(0);
        assertEquals("env", systemProperty.getName());
        assertEquals("production", systemProperty.getValue());
        assertNotNull(conditional.getIfClassAvailable().get(0).toString());
        assertNotNull(conditional.getIfClassNotAvailable().get(0).toString());
        assertNotNull(systemProperty.toString());

        assertTrue(beansXml.isTrimEnabled());
        assertFalse(beansXml.isEmpty());
        assertNotNull(beansXml.toString());
    }

    @Test
    void parseNamespaceLessShrinkwrapBeansXml() {
        String xml = "<beans version=\"3.0\" bean-discovery-mode=\"annotated\">" +
                "<alternatives><class>com.example.MockService</class></alternatives>" +
                "<interceptors><class>com.example.TransactionalInterceptor</class></interceptors>" +
                "<decorators><class>com.example.LoggingDecorator</class></decorators>" +
                "</beans>";

        BeansXml beansXml = new BeansXmlParser().parse(stream(xml));

        assertEquals("annotated", beansXml.getBeanDiscoveryMode());
        assertEquals("3.0", beansXml.getVersion());
        assertEquals(java.util.Collections.singletonList("com.example.MockService"),
                beansXml.getAlternatives().getClasses());
        assertEquals(java.util.Collections.singletonList("com.example.TransactionalInterceptor"),
                beansXml.getInterceptors().getClasses());
        assertEquals(java.util.Collections.singletonList("com.example.LoggingDecorator"),
                beansXml.getDecorators().getClasses());
    }

    @Test
    void parseWithValidationStripsScanAndValidates(@TempDir Path tempDir) throws Exception {
        URL schemaUrl = writeSchema(tempDir.resolve("beans.xsd"));
        String xml = "<beans xmlns=\"http://java.sun.com/xml/ns/javaee\" bean-discovery-mode=\"annotated\" version=\"4.1\">" +
                "<alternatives><class>com.example.MockService</class></alternatives>" +
                "<interceptors><class>com.example.TransactionalInterceptor</class></interceptors>" +
                "<decorators><class>com.example.LoggingDecorator</class></decorators>" +
                "<scan><exclude name=\"com.example.legacy.**\"/></scan>" +
                "<trim/>" +
                "</beans>";

        BeansXmlParser parser = new BeansXmlParser(true);

        BeansXml beansXml = parser.parseWithValidation(stream(xml), schemaUrl);

        assertEquals("annotated", beansXml.getBeanDiscoveryMode());
        assertEquals("4.1", beansXml.getVersion());
        assertNull(beansXml.getScan()); // removed because schema does not define scan
        assertEquals(java.util.Collections.singletonList("com.example.MockService"), beansXml.getAlternatives().getClasses());
        assertEquals(java.util.Collections.singletonList("com.example.TransactionalInterceptor"), beansXml.getInterceptors().getClasses());
        assertEquals(java.util.Collections.singletonList("com.example.LoggingDecorator"), beansXml.getDecorators().getClasses());
        assertTrue(beansXml.isTrimEnabled());
    }

    @Test
    void parseWithValidationRejectsInvalidDiscoveryMode(@TempDir Path tempDir) throws Exception {
        URL schemaUrl = writeSchema(tempDir.resolve("beans-invalid.xsd"));
        String xml = "<beans xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" bean-discovery-mode=\"invalid\" version=\"4.1\">" +
                "<alternatives/>" +
                "</beans>";

        BeansXmlParser parser = new BeansXmlParser(true);

        BeansXmlParser.BeansXmlParseException ex = assertThrows(
                BeansXmlParser.BeansXmlParseException.class,
                () -> parser.parseWithValidation(stream(xml), schemaUrl));

        assertTrue(ex.getMessage().contains("Failed to parse"));
        assertNotNull(ex.getCause());
    }

    @Test
    void parseFallsBackToDefaultWhenInvalidWithoutValidation() {
        String xml = "<beans xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" bean-discovery-mode=\"invalid\"></beans>";

        BeansXml beansXml = new BeansXmlParser().parse(stream(xml));

        assertEquals("annotated", beansXml.getBeanDiscoveryMode());
        assertTrue(beansXml.getAlternatives().isEmpty());
        assertNull(beansXml.getScan());
        assertFalse(beansXml.isTrimEnabled());
    }

    @Test
    void clearCacheRemovesCachedContexts() throws Exception {
        BeansXmlParser parser = new BeansXmlParser();
        parser.parse(stream("<beans xmlns=\"https://jakarta.ee/xml/ns/jakartaee\"/>"));

        Map<?, ?> cache = jaxbCache();

        assertFalse(cache.isEmpty());

        BeansXmlParser.clearCache();

        assertTrue(jaxbCache().isEmpty());
    }

    private InputStream stream(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    private URL writeSchema(Path path) throws Exception {
        String xsd = ""
                + "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\""
                + "           targetNamespace=\"https://jakarta.ee/xml/ns/jakartaee\""
                + "           xmlns=\"https://jakarta.ee/xml/ns/jakartaee\""
                + "           elementFormDefault=\"qualified\">"
                + "  <xs:element name=\"beans\">"
                + "    <xs:complexType>"
                + "      <xs:sequence>"
                + "        <xs:element name=\"alternatives\" minOccurs=\"0\">"
                + "          <xs:complexType>"
                + "            <xs:sequence>"
                + "              <xs:element name=\"class\" type=\"xs:string\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>"
                + "              <xs:element name=\"stereotype\" type=\"xs:string\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>"
                + "            </xs:sequence>"
                + "          </xs:complexType>"
                + "        </xs:element>"
                + "        <xs:element name=\"interceptors\" minOccurs=\"0\">"
                + "          <xs:complexType>"
                + "            <xs:sequence>"
                + "              <xs:element name=\"class\" type=\"xs:string\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>"
                + "            </xs:sequence>"
                + "          </xs:complexType>"
                + "        </xs:element>"
                + "        <xs:element name=\"decorators\" minOccurs=\"0\">"
                + "          <xs:complexType>"
                + "            <xs:sequence>"
                + "              <xs:element name=\"class\" type=\"xs:string\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>"
                + "            </xs:sequence>"
                + "          </xs:complexType>"
                + "        </xs:element>"
                + "        <xs:element name=\"trim\" minOccurs=\"0\"/>"
                + "      </xs:sequence>"
                + "      <xs:attribute name=\"bean-discovery-mode\" type=\"xs:string\" use=\"optional\"/>"
                + "      <xs:attribute name=\"version\" type=\"xs:string\" use=\"optional\"/>"
                + "    </xs:complexType>"
                + "  </xs:element>"
                + "</xs:schema>";
        Files.write(path, xsd.getBytes(StandardCharsets.UTF_8));
        return path.toUri().toURL();
    }

    @SuppressWarnings("unchecked")
    private Map<Class<?>, ?> jaxbCache() throws Exception {
        Field field = BeansXmlParser.class.getDeclaredField("jaxbContextCache");
        field.setAccessible(true);
        return (Map<Class<?>, ?>) field.get(null);
    }
}
