package com.threeamigos.common.util.implementations.injection.beansxml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

/**
 * Model class for the &lt;if-class-available&gt; condition element.
 *
 * <p>CDI 4.1 Section 12.4: This condition checks if a specified class
 * is available on the classpath.
 *
 * <h2>Example XML:</h2>
 * <pre>{@code
 * <exclude name="com.example.test.**">
 *     <if-class-available name="org.junit.Test"/>
 * </exclude>
 * }</pre>
 *
 * <p>In this example, test classes are only excluded if JUnit is present.
 *
 * @author Stefano Reksten
 * @see Exclude
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class IfClassAvailable {

    /**
     * The fully qualified name of the class to check for.
     *
     * <p>The exclusion rule only applies if this class is found on the classpath.
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
        return "IfClassAvailable{" +
               "name='" + name + '\'' +
               '}';
    }
}
