package com.threeamigos.common.util.implementations.injection.beansxml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

/**
 * Model class for the &lt;if-system-property&gt; condition element.
 *
 * <p>CDI 4.1 Section 12.4: This condition checks if a system property
 * matches an expected value.
 *
 * <h2>Example XML:</h2>
 * <pre>{@code
 * <exclude name="com.example.debug.**">
 *     <if-system-property name="env" value="production"/>
 * </exclude>
 * }</pre>
 *
 * <p>In this example, debug classes are excluded when running in production
 * (i.e., when the system property "env" is set to "production").
 *
 * <h2>System Property Check:</h2>
 * <p>This condition evaluates to true if:
 * <ul>
 *   <li>System.getProperty(name) equals the specified value</li>
 *   <li>Both strings are compared case-sensitively</li>
 * </ul>
 *
 * @author Stefano Reksten
 * @see Exclude
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class IfSystemProperty {

    /**
     * The name of the system property to check.
     *
     * <p>Examples: "env", "os.name", "user.home", custom properties
     */
    @XmlAttribute(name = "name", required = true)
    private String name;

    /**
     * The expected value of the system property.
     *
     * <p>The condition is true only if System.getProperty(name).equals(value).
     */
    @XmlAttribute(name = "value")
    private String value;

    // ============================================
    // Getters
    // ============================================

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "IfSystemProperty{" +
               "name='" + name + '\'' +
               ", value='" + value + '\'' +
               '}';
    }
}
