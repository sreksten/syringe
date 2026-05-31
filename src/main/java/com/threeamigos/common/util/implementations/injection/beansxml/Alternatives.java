package com.threeamigos.common.util.implementations.injection.beansxml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Model class for the &lt;alternatives&gt; section of beans.xml.
 *
 * <p>CDI 4.1 Section 5.1.2: Alternatives provide different implementations
 * that can be selected for different environments (development, testing, production).
 *
 * <h2>Example XML:</h2>
 * <pre>{@code
 * <alternatives>
 *     <class>com.example.MockPaymentService</class>
 *     <class>com.example.TestEmailService</class>
 *     <stereotype>com.example.Mock</stereotype>
 * </alternatives>
 * }</pre>
 *
 * <h2>Usage:</h2>
 * <ul>
 *   <li><b>&lt;class&gt;</b> - Enable a specific alternative bean</li>
 *   <li><b>&lt;stereotype&gt;</b> - Enable all beans with this stereotype</li>
 * </ul>
 *
 * <p><b>Note:</b> CDI 4.1 prefers @Priority annotation over beans.xml for alternatives.
 * However, beans.xml alternatives are still part of the specification for backward compatibility.
 *
 * @author Stefano Reksten
 * @see BeansXml
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class Alternatives {

    /**
     * List of alternative bean classes to enable.
     *
     * <p>Each entry is a fully qualified class name (e.g., "com.example.MockService").
     *
     * <p>When a class is listed here, it is enabled as an alternative and will be
     * selected instead of non-alternative beans of the same type.
     */
    @XmlElement(name = "class")
    private List<String> classes = new ArrayList<>();

    /**
     * List of alternative stereotypes to enable.
     *
     * <p>Each entry is a fully qualified stereotype annotation class name.
     *
     * <p>When a stereotype is listed here, ALL beans annotated with that stereotype
     * are enabled as alternatives.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * @Stereotype
     * @Target(TYPE)
     * @Retention(RUNTIME)
     * public @interface Mock {}
     *
     * @Mock
     * public class MockPaymentService implements PaymentService { }
     *
     * // In beans.xml:
     * <alternatives>
     *     <stereotype>com.example.Mock</stereotype>
     * </alternatives>
     * }</pre>
     */
    @XmlElement(name = "stereotype")
    private List<String> stereotypes = new ArrayList<>();

    // ============================================
    // Getters
    // ============================================

    public List<String> getClasses() {
        return Collections.unmodifiableList(classes);
    }

    public List<String> getStereotypes() {
        return Collections.unmodifiableList(stereotypes);
    }

    // ============================================
    // Convenience Methods
    // ============================================

    /**
     * Checks if any alternatives are configured.
     *
     * @return true if at least one class or stereotype is configured
     */
    public boolean isEmpty() {
        return classes.isEmpty() && stereotypes.isEmpty();
    }

    @Override
    public String toString() {
        return "Alternatives{" +
               "classes=" + classes.size() +
               ", stereotypes=" + stereotypes.size() +
               '}';
    }
}
