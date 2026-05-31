package com.threeamigos.common.util.implementations.injection.beansxml;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for CDI 4.1 beans.xml files using JAXB.
 *
 * <p>This parser converts beans.xml files into structured {@link BeansXml} objects
 * according to the Jakarta CDI 4.0/4.1 specification.
 *
 * <h2>Features:</h2>
 * <ul>
 *   <li>✅ Full CDI 4.1 beans.xml support (all elements)</li>
 *   <li>✅ JAXB-based unmarshalling (type-safe)</li>
 *   <li>✅ XSD validation (optional but recommended)</li>
 *   <li>✅ Thread-safe with parser caching</li>
 *   <li>✅ Handles both javax and jakarta namespaces</li>
 *   <li>✅ Graceful error handling</li>
 * </ul>
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * BeansXmlParser parser = new BeansXmlParser();
 *
 * // Parse from InputStream
 * try (InputStream is = getClass().getResourceAsStream("/META-INF/beans.xml")) {
 *     BeansXml beansXml = parser.parse(is);
 *     System.out.println("Discovery mode: " + beansXml.getBeanDiscoveryMode());
 *     System.out.println("Alternatives: " + beansXml.getAlternatives().getClasses());
 * }
 *
 * // Parse with validation
 * BeansXml validated = parser.parseWithValidation(is, schemaUrl);
 * }</pre>
 *
     * <h2>Error Handling:</h2>
     * <p>If parsing fails (malformed XML, invalid structure), this parser returns
     * a default BeansXml with bean-discovery-mode="annotated" and no other config.
 *
 * @author Stefano Reksten
 * @see BeansXml
 */
public class BeansXmlParser {

    private static final String JAKARTA_NAMESPACE = "https://jakarta.ee/xml/ns/jakartaee";
    private static final String JCP_NAMESPACE = "http://xmlns.jcp.org/xml/ns/javaee";
    private static final String JAVA_SUN_NAMESPACE = "http://java.sun.com/xml/ns/javaee";
    private static final Pattern ROOT_BEANS_TAG_PATTERN =
            Pattern.compile("(?is)<beans\\b([^>]*)>");
    private static final Pattern XMLNS_ATTRIBUTE_PATTERN =
            Pattern.compile("(?is)\\bxmlns\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern BEAN_DISCOVERY_MODE_ATTRIBUTE_PATTERN =
            Pattern.compile("(?is)\\bbean-discovery-mode\\s*=\\s*['\"][^'\"]*['\"]");

    /**
     * JAXB context cache for performance.
     * Creating JAXBContext is expensive, so we cache it per thread/parser.
     */
    private static final ConcurrentHashMap<Class<?>, JAXBContext> jaxbContextCache =
        new ConcurrentHashMap<>();

    public static void clearJaxbContextCacheForClassLoader(ClassLoader classLoader) {
        if (classLoader == null) {
            return;
        }
        jaxbContextCache.entrySet().removeIf(entry -> {
            Class<?> type = entry.getKey();
            return type != null && type.getClassLoader() == classLoader;
        });
    }

    /**
     * Whether to enable XSD validation during parsing.
     * Default: false (for performance and backward compatibility).
     */
    private boolean validationEnabled;

    /**
     * URL to the CDI beans XSD schema (if validation is enabled).
     * Can be loaded from the classpath or an external URL.
     */
    private URL schemaUrl;

    /**
     * Creates a new BeansXmlParser with validation disabled.
     */
    public BeansXmlParser() {
        this(false);
    }

    /**
     * Creates a new BeansXmlParser with configurable validation.
     *
     * @param validationEnabled whether to enable XSD validation
     */
    public BeansXmlParser(boolean validationEnabled) {
        this.validationEnabled = validationEnabled;
    }

    /**
     * Enables XSD schema validation with a custom schema URL.
     *
     * @param schemaUrl the URL to the beans.xsd schema file
     */
    public void setSchemaUrl(URL schemaUrl) {
        this.schemaUrl = schemaUrl;
        this.validationEnabled = true;
    }

    /**
     * Parses a beans.xml file from an InputStream.
     *
     * <p>This method uses JAXB to unmarshal the XML into a {@link BeansXml} object.
     *
     * @param inputStream the input stream containing beans.xml content
     * @return the parsed BeansXml object, or a default instance if parsing fails
     */
    public BeansXml parse(InputStream inputStream) {
        if (inputStream == null) {
            return createDefault();
        }

        byte[] rawBytes;
        try {
            rawBytes = readBytes(inputStream);
        } catch (Exception e) {
            System.err.println("[Syringe][BeansXmlParser] Failed to read beans.xml stream: "
                    + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace(System.err);
            return createDefault();
        }

        try {
            BeansXml beansXml = parseInternal(new ByteArrayInputStream(rawBytes));
            applySourceDescriptorMetadata(rawBytes, beansXml);
            return beansXml;
        } catch (Exception primaryFailure) {
            try {
                BeansXml beansXml = parseWithDomFallback(rawBytes, false, null);
                applySourceDescriptorMetadata(rawBytes, beansXml);
                return beansXml;
            } catch (Exception domFailure) {
                if (!(primaryFailure instanceof BeansXmlParseException
                        && domFailure instanceof BeansXmlParseException)) {
                    logParseFailure(primaryFailure, domFailure, rawBytes);
                }
            }
            return createDefault();
        }
    }

    /**
     * Parses a beans.xml file with XSD validation.
     *
     * <p>This method validates the XML against the CDI beans schema before unmarshalling.
     * If validation fails, an exception is thrown.
     *
     * @param inputStream the input stream containing beans.xml content
     * @param schemaUrl the URL to the XSD schema file
     * @return the parsed and validated BeansXml object
     * @throws BeansXmlParseException if parsing or validation fails
     */
    public BeansXml parseWithValidation(InputStream inputStream, URL schemaUrl)
            throws BeansXmlParseException {
        byte[] rawBytes;
        try {
            rawBytes = readBytes(inputStream);
        } catch (Exception e) {
            throw new BeansXmlParseException("Failed to read beans.xml for validation", e);
        }

        try {
            setSchemaUrl(schemaUrl);
            BeansXml beansXml = parseInternal(new ByteArrayInputStream(rawBytes));
            applySourceDescriptorMetadata(rawBytes, beansXml);
            return beansXml;
        } catch (Exception primaryFailure) {
            try {
                Schema schema = createSchema(schemaUrl);
                BeansXml beansXml = parseWithDomFallback(rawBytes, true, schema);
                applySourceDescriptorMetadata(rawBytes, beansXml);
                return beansXml;
            } catch (Exception domFailure) {
                primaryFailure.addSuppressed(domFailure);
                throw new BeansXmlParseException("Failed to parse and validate beans.xml", primaryFailure);
            }
        }
    }

    /**
     * Creates an XSD schema from a URL.
     *
     * @param schemaUrl the URL to the schema file
     * @return the compiled Schema object
     * @throws Exception if schema creation fails
     */
    private Schema createSchema(URL schemaUrl) throws Exception {
        SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        schemaFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        try (InputStream is = schemaUrl.openStream()) {
            return schemaFactory.newSchema(new StreamSource(is));
        }
    }

    /**
     * Creates a default BeansXml instance.
     *
     * <p>Used as a fallback when parsing fails or when no beans.xml exists.
     *
     * @return a BeansXml with bean-discovery-mode="annotated" and no other configuration
     */
    private BeansXml createDefault() {
        return new BeansXml();
    }

    private void ensureValidDiscoveryMode(BeansXml beansXml) throws BeansXmlParseException {
        String mode = beansXml.getBeanDiscoveryMode();
        switch (mode) {
            case "all":
            case "annotated":
            case "none":
                return;
            default:
                throw new BeansXmlParseException(
                    "Invalid bean-discovery-mode '" + mode + "'. Allowed: all, annotated, none.", null);
        }
    }

    private BeansXml parseInternal(InputStream inputStream) throws Exception {
        // Get or create JAXB context (cached for performance)
        JAXBContext jaxbContext = jaxbContextCache.computeIfAbsent(
            BeansXml.class,
            clazz -> {
                try {
                    return JAXBContext.newInstance(BeansXml.class);
                } catch (JAXBException e) {
                    throw new RuntimeException("Failed to create JAXB context for BeansXml", e);
                }
            }
        );

        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

        boolean stripVendorScan = validationEnabled && schemaUrl != null;

        if (validationEnabled && schemaUrl != null) {
            Schema schema = createSchema(schemaUrl);
            unmarshaller.setSchema(schema);
        }

        try (InputStream normalized = normalizeNamespaces(inputStream, stripVendorScan)) {
            BeansXml beansXml = (BeansXml) unmarshaller.unmarshal(normalized);
            ensureValidDiscoveryMode(beansXml);
            return beansXml;
        }
    }

    private BeansXml parseWithDomFallback(byte[] rawBytes, boolean stripVendorScan, Schema schema) throws Exception {
        byte[] normalizedBytes = normalizeBytes(rawBytes, stripVendorScan);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        setFeatureIfSupported(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        setFeatureIfSupported(factory, "http://xml.org/sax/features/external-general-entities", false);
        setFeatureIfSupported(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        setAttributeIfSupported(factory, XMLConstants.ACCESS_EXTERNAL_DTD);
        setAttributeIfSupported(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA);
        if (schema != null) {
            factory.setSchema(schema);
        }

        Document document;
        try (InputStream normalized = new ByteArrayInputStream(normalizedBytes)) {
            document = factory.newDocumentBuilder().parse(normalized);
        }

        BeansXml beansXml = populateBeansXmlFromDom(document);
        ensureValidDiscoveryMode(beansXml);
        return beansXml;
    }

    private byte[] normalizeBytes(byte[] rawBytes, boolean stripVendorScan) throws Exception {
        try (InputStream normalized = normalizeNamespaces(new ByteArrayInputStream(rawBytes), stripVendorScan)) {
            return readBytes(normalized);
        }
    }

    private BeansXml populateBeansXmlFromDom(Document document) throws Exception {
        BeansXml beansXml = new BeansXml();
        Element root = document.getDocumentElement();
        if (root == null || !"beans".equals(localName(root))) {
            return beansXml;
        }

        String mode = trimToNull(root.getAttribute("bean-discovery-mode"));
        if (mode != null) {
            setFieldValue(beansXml, "beanDiscoveryMode", mode);
        }
        String version = trimToNull(root.getAttribute("version"));
        if (version != null) {
            setFieldValue(beansXml, "version", version);
        }

        Element alternativesElement = findDirectChild(root, "alternatives");
        if (alternativesElement != null) {
            setFieldValue(beansXml, "alternatives", buildAlternatives(alternativesElement));
        }
        Element interceptorsElement = findDirectChild(root, "interceptors");
        if (interceptorsElement != null) {
            setFieldValue(beansXml, "interceptors", buildInterceptors(interceptorsElement));
        }
        Element decoratorsElement = findDirectChild(root, "decorators");
        if (decoratorsElement != null) {
            setFieldValue(beansXml, "decorators", buildDecorators(decoratorsElement));
        }
        Element scanElement = findDirectChild(root, "scan");
        if (scanElement != null) {
            setFieldValue(beansXml, "scan", buildScan(scanElement));
        }
        if (findDirectChild(root, "trim") != null) {
            setFieldValue(beansXml, "trim", new Trim());
        }

        return beansXml;
    }

    private Alternatives buildAlternatives(Element alternativesElement) throws Exception {
        Alternatives alternatives = new Alternatives();
        setFieldValue(alternatives, "classes", collectDirectChildText(alternativesElement, "class"));
        setFieldValue(alternatives, "stereotypes", collectDirectChildText(alternativesElement, "stereotype"));
        return alternatives;
    }

    private Interceptors buildInterceptors(Element interceptorsElement) throws Exception {
        Interceptors interceptors = new Interceptors();
        setFieldValue(interceptors, "classes", collectDirectChildText(interceptorsElement, "class"));
        return interceptors;
    }

    private Decorators buildDecorators(Element decoratorsElement) throws Exception {
        Decorators decorators = new Decorators();
        setFieldValue(decorators, "classes", collectDirectChildText(decoratorsElement, "class"));
        return decorators;
    }

    private Scan buildScan(Element scanElement) throws Exception {
        Scan scan = new Scan();
        List<Exclude> excludes = new ArrayList<>();
        for (Element excludeElement : directChildren(scanElement, "exclude")) {
            excludes.add(buildExclude(excludeElement));
        }
        setFieldValue(scan, "excludes", excludes);
        return scan;
    }

    private Exclude buildExclude(Element excludeElement) throws Exception {
        Exclude exclude = new Exclude();
        String name = trimToNull(excludeElement.getAttribute("name"));
        setFieldValue(exclude, "name", name);

        List<IfClassAvailable> ifClassAvailable = new ArrayList<>();
        for (Element element : directChildren(excludeElement, "if-class-available")) {
            IfClassAvailable condition = new IfClassAvailable();
            setFieldValue(condition, "name", trimToNull(element.getAttribute("name")));
            ifClassAvailable.add(condition);
        }
        setFieldValue(exclude, "ifClassAvailable", ifClassAvailable);

        List<IfClassNotAvailable> ifClassNotAvailable = new ArrayList<>();
        for (Element element : directChildren(excludeElement, "if-class-not-available")) {
            IfClassNotAvailable condition = new IfClassNotAvailable();
            setFieldValue(condition, "name", trimToNull(element.getAttribute("name")));
            ifClassNotAvailable.add(condition);
        }
        setFieldValue(exclude, "ifClassNotAvailable", ifClassNotAvailable);

        List<IfSystemProperty> ifSystemProperty = new ArrayList<>();
        for (Element element : directChildren(excludeElement, "if-system-property")) {
            IfSystemProperty condition = new IfSystemProperty();
            setFieldValue(condition, "name", trimToNull(element.getAttribute("name")));
            setFieldValue(condition, "value", trimToNull(element.getAttribute("value")));
            ifSystemProperty.add(condition);
        }
        setFieldValue(exclude, "ifSystemProperty", ifSystemProperty);

        return exclude;
    }

    private List<String> collectDirectChildText(Element parent, String childName) {
        List<String> values = new ArrayList<>();
        for (Element element : directChildren(parent, childName)) {
            String value = trimToNull(element.getTextContent());
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    private List<Element> directChildren(Element parent, String childName) {
        List<Element> elements = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (!(child instanceof Element)) {
                continue;
            }
            if (childName.equals(localName(child))) {
                elements.add((Element) child);
            }
        }
        return elements;
    }

    private Element findDirectChild(Element parent, String childName) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (!(child instanceof Element)) {
                continue;
            }
            if (childName.equals(localName(child))) {
                return (Element) child;
            }
        }
        return null;
    }

    private String localName(Node node) {
        String localName = node.getLocalName();
        if (localName != null) {
            return localName;
        }
        String nodeName = node.getNodeName();
        int colon = nodeName.indexOf(':');
        if (colon >= 0 && colon + 1 < nodeName.length()) {
            return nodeName.substring(colon + 1);
        }
        return nodeName;
    }

    private void setFieldValue(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void setFeatureIfSupported(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception ignored) {
            // Best effort across XML parser implementations.
        }
    }

    private void setAttributeIfSupported(DocumentBuilderFactory factory, String attribute) {
        try {
            factory.setAttribute(attribute, "");
        } catch (Exception ignored) {
            // Best effort across XML parser implementations.
        }
    }

    private byte[] readBytes(InputStream inputStream) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            baos.write(buffer, 0, read);
        }
        return baos.toByteArray();
    }

    private InputStream normalizeNamespaces(InputStream inputStream, boolean stripVendorScan) throws Exception {
        String xml = new String(readBytes(inputStream), StandardCharsets.UTF_8);
        String normalized = xml
            .replace("http://xmlns.jcp.org/xml/ns/javaee", JAKARTA_NAMESPACE)
            .replace("http://java.sun.com/xml/ns/javaee", JAKARTA_NAMESPACE);
        normalized = ensureRootNamespace(normalized);
        if (stripVendorScan) {
            normalized = normalized.replaceAll("(?s)<scan>.*?</scan>", "");
        }
        return new ByteArrayInputStream(normalized.getBytes(StandardCharsets.UTF_8));
    }

    private String ensureRootNamespace(String xml) {
        if (xml == null || xml.isEmpty()) {
            return xml;
        }

        Matcher matcher = ROOT_BEANS_TAG_PATTERN.matcher(xml);
        if (!matcher.find()) {
            return xml;
        }

        String attributes = matcher.group(1);
        if (attributes != null && attributes.matches("(?is).*\\bxmlns\\s*=.*")) {
            return xml;
        }

        String replacement = "<beans" + (attributes == null ? "" : attributes)
                + " xmlns=\"" + JAKARTA_NAMESPACE + "\">";
        return matcher.replaceFirst(Matcher.quoteReplacement(replacement));
    }

    private void logParseFailure(Exception primaryFailure, Exception domFailure, byte[] rawBytes) {
        String xml = new String(rawBytes, StandardCharsets.UTF_8);
        String normalized = xml.replace('\n', ' ').replace('\r', ' ');
        if (normalized.length() > 500) {
            normalized = normalized.substring(0, 500) + "...";
        }
        System.err.println("[Syringe][BeansXmlParser] Failed to parse beans.xml: "
                + primaryFailure.getClass().getName() + ": " + primaryFailure.getMessage());
        System.err.println("[Syringe][BeansXmlParser] DOM fallback also failed: "
                + domFailure.getClass().getName() + ": " + domFailure.getMessage());
        System.err.println("[Syringe][BeansXmlParser] beans.xml preview: " + normalized);
        primaryFailure.printStackTrace(System.err);
        domFailure.printStackTrace(System.err);
    }

    private void applySourceDescriptorMetadata(byte[] rawBytes, BeansXml beansXml) {
        if (rawBytes == null || beansXml == null) {
            return;
        }
        String xml = new String(rawBytes, StandardCharsets.UTF_8);
        Matcher matcher = ROOT_BEANS_TAG_PATTERN.matcher(xml);
        if (!matcher.find()) {
            return;
        }

        String attributes = matcher.group(1);
        if (attributes == null) {
            return;
        }

        Matcher xmlnsMatcher = XMLNS_ATTRIBUTE_PATTERN.matcher(attributes);
        if (xmlnsMatcher.find()) {
            String namespace = xmlnsMatcher.group(1);
            if (namespace != null) {
                String normalized = namespace.trim();
                if (JCP_NAMESPACE.equals(normalized)
                        || JAVA_SUN_NAMESPACE.equals(normalized)
                        || JAKARTA_NAMESPACE.equals(normalized)) {
                    beansXml.setSourceNamespace(normalized);
                }
            }
        }

        beansXml.setBeanDiscoveryModeDeclared(
                BEAN_DISCOVERY_MODE_ATTRIBUTE_PATTERN.matcher(attributes).find());
    }

    /**
     * Clears the JAXB context cache.
     *
     * <p>Useful for testing or when you need to free memory.
     * In normal operation, the cache improves performance significantly.
     */
    public static void clearCache() {
        jaxbContextCache.clear();
    }

    // ============================================
    // Exception Class
    // ============================================

    /**
     * Exception thrown when beans.xml parsing fails with validation enabled.
     */
    public static class BeansXmlParseException extends Exception {
        public BeansXmlParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
