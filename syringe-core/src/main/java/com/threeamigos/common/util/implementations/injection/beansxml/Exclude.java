package com.threeamigos.common.util.implementations.injection.beansxml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Model class for an exclusion rule within the &lt;scan&gt; section.
 *
 * <p>CDI 4.1 Section 12.4: An exclusion rule specifies classes to exclude from
 * bean discovery, optionally with conditions.
 *
 * <h2>Example XML:</h2>
 * <pre>{@code
 * <!-- Simple exclusion -->
 * <exclude name="com.example.legacy.**"/>
 *
 * <!-- Conditional exclusion (only if JUnit is present) -->
 * <exclude name="com.example.test.**">
 *     <if-class-available name="org.junit.Test"/>
 *     <if-class-available name="org.junit.jupiter.api.Test"/>
 * </exclude>
 *
 * <!-- Conditional exclusion (only if class NOT available) -->
 * <exclude name="com.example.optional.**">
 *     <if-class-not-available name="com.example.OptionalFeature"/>
 * </exclude>
 *
 * <!-- Multiple conditions (AND logic) -->
 * <exclude name="com.example.special.**">
 *     <if-class-available name="org.example.SpecialLib"/>
 *     <if-system-property name="exclude.special" value="true"/>
 * </exclude>
 * }</pre>
 *
 * <h2>Pattern Matching:</h2>
 * <ul>
 *   <li><b>Exact:</b> com.example.MyClass - matches only this class</li>
 *   <li><b>Package:</b> com.example.* - matches all classes in the package (no subpackages)</li>
 *   <li><b>Recursive:</b> com.example.** - matches all classes in package and subpackages</li>
 * </ul>
 *
 * <h2>Conditional Logic:</h2>
 * <p>If conditions are specified, the exclusion only applies when ALL conditions are met (AND logic).
 * If no conditions are specified, the exclusion always applies.
 *
 * @author Stefano Reksten
 * @see Scan
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class Exclude {

    /**
     * The pattern to match against class names.
     *
     * <p>Supports:
     * <ul>
     *   <li>Exact class name: "com.example.MyClass"</li>
     *   <li>Package wildcard: "com.example.*" (one level)</li>
     *   <li>Recursive wildcard: "com.example.**" (all levels)</li>
     * </ul>
     */
    @XmlAttribute(name = "name", required = true)
    private String name;

    /**
     * List of "if class available" conditions.
     *
     * <p>The exclusion only applies if ALL specified classes are available on the classpath.
     *
     * <p><b>Example use case:</b> Exclude test classes only when JUnit is present.
     * <pre>{@code
     * <exclude name="com.example.test.**">
     *     <if-class-available name="org.junit.Test"/>
     * </exclude>
     * }</pre>
     */
    @XmlElement(name = "if-class-available")
    private List<IfClassAvailable> ifClassAvailable = new ArrayList<>();

    /**
     * List of "if class not available" conditions.
     *
     * <p>The exclusion only applies if ALL specified classes are NOT available on the classpath.
     *
     * <p><b>Example use case:</b> Exclude optional feature classes when the feature is not installed.
     * <pre>{@code
     * <exclude name="com.example.optional.**">
     *     <if-class-not-available name="com.example.OptionalFeature"/>
     * </exclude>
     * }</pre>
     */
    @XmlElement(name = "if-class-not-available")
    private List<IfClassNotAvailable> ifClassNotAvailable = new ArrayList<>();

    /**
     * List of system property conditions.
     *
     * <p>The exclusion only applies if ALL specified system properties match the expected values.
     *
     * <p><b>Example use case:</b> Exclude debugging beans in production.
     * <pre>{@code
     * <exclude name="com.example.debug.**">
     *     <if-system-property name="env" value="production"/>
     * </exclude>
     * }</pre>
     */
    @XmlElement(name = "if-system-property")
    private List<IfSystemProperty> ifSystemProperty = new ArrayList<>();

    // ============================================
    // Getters
    // ============================================

    public String getName() {
        return name;
    }

    public List<IfClassAvailable> getIfClassAvailable() {
        return Collections.unmodifiableList(ifClassAvailable);
    }

    public List<IfClassNotAvailable> getIfClassNotAvailable() {
        return Collections.unmodifiableList(ifClassNotAvailable);
    }

    public List<IfSystemProperty> getIfSystemProperty() {
        return Collections.unmodifiableList(ifSystemProperty);
    }

    // ============================================
    // Convenience Methods
    // ============================================

    /**
     * Checks if this exclusion is unconditional (always applies).
     *
     * @return true if no conditions are specified
     */
    public boolean isUnconditional() {
        return ifClassAvailable.isEmpty() &&
               ifClassNotAvailable.isEmpty() &&
               ifSystemProperty.isEmpty();
    }

    /**
     * Checks if the pattern matches a given class name.
     *
     * <p>Supports:
     * <ul>
     *   <li>Exact match: "com.example.MyClass"</li>
     *   <li>Single-level wildcard: "com.example.*"</li>
     *   <li>Multi-level wildcard: "com.example.**"</li>
     * </ul>
     *
     * @param className the fully qualified class name to check
     * @return true if the pattern matches
     */
    public boolean matches(String className) {
        if (name == null || className == null) {
            return false;
        }

        // Exact match
        if (name.equals(className)) {
            return true;
        }

        // Recursive wildcard: com.example.**
        if (name.endsWith(".**")) {
            String packagePrefix = name.substring(0, name.length() - 2); // Remove "**"
            return className.startsWith(packagePrefix);
        }

        // Single-level wildcard: com.example.*
        if (name.endsWith(".*")) {
            String packagePrefix = name.substring(0, name.length() - 1); // Remove "*"
            if (!className.startsWith(packagePrefix)) {
                return false;
            }
            // Check that there's no additional package separator after the prefix
            String remainder = className.substring(packagePrefix.length());
            return !remainder.contains(".");
        }

        return false;
    }

    @Override
    public String toString() {
        return "Exclude{" +
               "name='" + name + '\'' +
               ", conditions=" + (ifClassAvailable.size() + ifClassNotAvailable.size() + ifSystemProperty.size()) +
               '}';
    }
}
