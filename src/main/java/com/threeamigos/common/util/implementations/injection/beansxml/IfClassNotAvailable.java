package com.threeamigos.common.util.implementations.injection.beansxml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

/**
 * Model class for the &lt;if-class-not-available&gt; condition element.
 *
 * <p>CDI 4.1 Section 12.4: This condition checks if a specified class
 * is NOT available on the classpath.
 *
 * <h2>Example XML:</h2>
 * <pre>{@code
 * <exclude name="com.example.optional.**">
 *     <if-class-not-available name="com.example.OptionalFeature"/>
 * </exclude>
 * }</pre>
 *
 * <p>In this example, optional feature classes are excluded if the main
 * feature class is not installed.
 *
 * @author Stefano Reksten
 * @see Exclude
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class IfClassNotAvailable {

    /**
     * The fully qualified name of the class to check for absence.
     *
     * <p>The exclusion rule only applies if this class is NOT found on the classpath.
     */
    @XmlAttribute(name = "name", required = true)
    private String name;

    // ============================================
    // Getters
    // ============================================

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "IfClassNotAvailable{" +
               "name='" + name + '\'' +
               '}';
    }
}
