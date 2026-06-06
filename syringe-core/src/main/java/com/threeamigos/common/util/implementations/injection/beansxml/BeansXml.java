package com.threeamigos.common.util.implementations.injection.beansxml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * Root model class for CDI 4.1 beans.xml descriptor.
 *
 * <p>Represents the complete structure of a beans.xml file according to the
 * Jakarta CDI 4.0/4.1 specification. This class uses JAXB for XML unmarshalling.
 *
 * <h2>CDI 4.1 beans.xml Structure:</h2>
 * <pre>{@code
 * <beans xmlns="https://jakarta.ee/xml/ns/jakartaee"
 *        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
 *        xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee
 *                            https://jakarta.ee/xml/ns/jakartaee/beans_4_0.xsd"
 *        version="4.0"
 *        bean-discovery-mode="all">
 *
 *     <alternatives>
 *         <class>com.example.MockService</class>
 *         <stereotype>com.example.Mock</stereotype>
 *     </alternatives>
 *
 *     <interceptors>
 *         <class>com.example.TransactionalInterceptor</class>
 *     </interceptors>
 *
 *     <decorators>
 *         <class>com.example.TimingDecorator</class>
 *     </decorators>
 *
 *     <scan>
 *         <exclude name="com.example.legacy.**"/>
 *     </scan>
 *
 *     <trim/>
 * </beans>
 * }</pre>
 *
 * <h2>Bean Discovery Modes (CDI 4.1 Section 12.1):</h2>
 * <ul>
 *   <li><b>all</b> - Explicit bean archive: all classes with suitable constructors are beans</li>
 *   <li><b>annotated</b> - Implicit bean archive: only classes with bean-defining annotations are beans</li>
 *   <li><b>none</b> - Not a bean archive: no beans are discovered</li>
 * </ul>
 *
 * @author Stefano Reksten
 * @see BeansXmlParser
 */
@XmlRootElement(name = "beans", namespace = "https://jakarta.ee/xml/ns/jakartaee")
@XmlAccessorType(XmlAccessType.FIELD)
public class BeansXml {

    static final String LEGACY_JAVA_SUN_NAMESPACE = "http://java.sun.com/xml/ns/javaee";

    /**
     * The bean discovery mode.
     *
     * <p>Valid values: "all", "annotated", "none"
     * <p>Default: "annotated" (if the attribute is missing or empty)
     *
     * <p>CDI 4.1 Section 12.1:
     * <ul>
     *   <li>"all" - All classes are considered for bean discovery</li>
     *   <li>"annotated" - Only annotated classes are considered</li>
     *   <li>"none" - No bean discovery occurs</li>
     * </ul>
     */
    @XmlAttribute(name = "bean-discovery-mode")
    private String beanDiscoveryMode = "annotated";

    /**
     * The CDI version (e.g., "4.0", "3.0", "2.0", "1.1").
     *
     * <p>This is informational and doesn't affect behavior in most containers.
     */
    @XmlAttribute(name = "version")
    private String version;

    // Metadata captured by parser from the original descriptor source.
    // Not part of the beans.xml schema and not unmarshalled by JAXB.
    private transient String sourceNamespace;
    private transient boolean beanDiscoveryModeDeclared;

    /**
     * Alternative beans and stereotypes to enable.
     *
     * <p>CDI 4.1 Section 5.1.2: Alternatives can be enabled via:
     * <ul>
     *   <li>@Priority annotation on the class (preferred in CDI 4.1)</li>
     *   <li>beans.xml &lt;alternatives&gt; section (traditional method)</li>
     * </ul>
     */
    @XmlElement(name = "alternatives")
    private Alternatives alternatives = new Alternatives();

    /**
     * Interceptor classes with explicit ordering.
     *
     * <p>CDI 4.1 Section 9.5: Interceptors can be ordered via:
     * <ul>
     *   <li>@Priority annotation on the class (preferred in CDI 4.1)</li>
     *   <li>beans.xml &lt;interceptors&gt; section (traditional method)</li>
     * </ul>
     *
     * <p>The order in the XML determines interceptor precedence (first = highest priority).
     */
    @XmlElement(name = "interceptors")
    private Interceptors interceptors = new Interceptors();

    /**
     * Decorator classes with explicit ordering.
     *
     * <p>CDI 4.1 Section 8.2: Decorators can be ordered via:
     * <ul>
     *   <li>@Priority annotation on the class (preferred in CDI 4.1)</li>
     *   <li>beans.xml &lt;decorators&gt; section (traditional method)</li>
     * </ul>
     *
     * <p>The order in the XML determines decorator precedence (first = highest priority).
     */
    @XmlElement(name = "decorators")
    private Decorators decorators = new Decorators();

    /**
     * Scan configuration for excluding packages/classes.
     *
     * <p>CDI 4.1 Section 12.4: Allows fine-grained control over bean scanning.
     * Useful for excluding legacy code, test classes, or unwanted dependencies.
     *
     * <p><b>Examples:</b></p>
     * <pre>
     * &lt;scan&gt;
     *   &lt;exclude name=&quot;com.myapp.internal.**&quot;/&gt;
     *   &lt;if-class-available name=&quot;com.acme.FeatureFlag&quot;/&gt;
     *   &lt;if-class-not-available name=&quot;com.legacy.DeprecatedFeature&quot;/&gt;
     *   &lt;if-system-property name=&quot;env&quot; value=&quot;prod&quot;/&gt;
     * &lt;/scan&gt;
     *
     * &lt;scan&gt;
     *   &lt;exclude name=&quot;com.example.tests.**&quot;/&gt;
     *   &lt;if-class-available name=&quot;org.slf4j.Logger&quot;/&gt;        &lt;!-- include only if SLF4J present --&gt;
     *   &lt;if-class-not-available name=&quot;com.example.DebugMode&quot;/&gt; &lt;!-- skip when debug module absent --&gt;
     *   &lt;if-system-property name=&quot;featureX.enabled&quot; value=&quot;true&quot;/&gt; &lt;!-- include only when featureX on --&gt;
     * &lt;/scan&gt;
     * </pre>
     */
    @XmlElement(name = "scan")
    private Scan scan;

    /**
     * Trim element for optimization.
     *
     * <p>CDI 4.1 Section 12.4: When present, beans are enabled if they are:
     * <ul>
     *   <li>Explicitly declared in beans.xml (alternatives, interceptors, decorators)</li>
     *   <li>Directly injected somewhere in the application</li>
     * </ul>
     * All other beans are excluded.
     *
     * <p>This is an optimization for large applications with many unused beans.
     */
    @XmlElement(name = "trim")
    private Trim trim;

    // ============================================
    // Getters
    // ============================================

    public String getBeanDiscoveryMode() {
        if (beanDiscoveryMode == null || beanDiscoveryMode.trim().isEmpty()) {
            beanDiscoveryMode = "annotated";
        }
        return beanDiscoveryMode;
    }

    public String getVersion() {
        return version;
    }

    public boolean isBeanDiscoveryModeDeclared() {
        return beanDiscoveryModeDeclared;
    }

    public boolean isNotLegacyJavaSunDescriptor() {
        return !LEGACY_JAVA_SUN_NAMESPACE.equals(sourceNamespace);
    }

    public Alternatives getAlternatives() {
        return alternatives;
    }

    public Interceptors getInterceptors() {
        return interceptors;
    }

    public Decorators getDecorators() {
        return decorators;
    }

    public Scan getScan() {
        return scan;
    }

    public Trim getTrim() {
        return trim;
    }

    // ============================================
    // Convenience Methods
    // ============================================

    /**
     * Checks if trim mode is enabled.
     *
     * @return true if &lt;trim/&gt; element is present
     */
    public boolean isTrimEnabled() {
        return trim != null;
    }

    /**
     * Checks if this is an empty beans.xml (no configuration).
     *
     * @return true if no alternatives, interceptors, decorators, scan, or trim are configured
     */
    public boolean isEmpty() {
        return alternatives == null &&
               interceptors == null &&
               decorators == null &&
               scan == null &&
               trim == null;
    }

    void setSourceNamespace(String sourceNamespace) {
        this.sourceNamespace = sourceNamespace;
    }

    void setBeanDiscoveryModeDeclared(boolean beanDiscoveryModeDeclared) {
        this.beanDiscoveryModeDeclared = beanDiscoveryModeDeclared;
    }

    @Override
    public String toString() {
        return "BeansXml{" +
               "beanDiscoveryMode='" + beanDiscoveryMode + '\'' +
               ", version='" + version + '\'' +
               ", alternatives=" + (alternatives != null ? alternatives.getClasses().size() + " classes, " +
                                   alternatives.getStereotypes().size() + " stereotypes" : "none") +
               ", interceptors=" + (interceptors != null ? interceptors.getClasses().size() + " classes" : "none") +
               ", decorators=" + (decorators != null ? decorators.getClasses().size() + " classes" : "none") +
               ", scan=" + (scan != null ? "configured" : "none") +
               ", trim=" + isTrimEnabled() +
               '}';
    }
}
