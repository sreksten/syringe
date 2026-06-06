package com.threeamigos.common.util.implementations.injection.beansxml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Model class for the &lt;scan&gt; section of beans.xml.
 *
 * <p>CDI 4.1 Section 12.4: The scan element allows fine-grained control over
 * which classes are scanned for bean discovery. This is useful for:
 * <ul>
 *   <li>Excluding legacy code that shouldn't be treated as beans</li>
 *   <li>Excluding test classes from production builds</li>
 *   <li>Excluding third-party libraries with problematic annotations</li>
 *   <li>Improving container startup performance by skipping unnecessary scanning</li>
 * </ul>
 *
 * <h2>Example XML:</h2>
 * <pre>{@code
 * <scan>
 *     <!-- Exclude all classes in legacy package -->
 *     <exclude name="com.example.legacy.**"/>
 *
 *     <!-- Exclude test classes (only if JUnit is present) -->
 *     <exclude name="com.example.test.**">
 *         <if-class-available name="org.junit.Test"/>
 *     </exclude>
 *
 *     <!-- Exclude specific problematic class -->
 *     <exclude name="com.example.ProblematicBean"/>
 * </scan>
 * }</pre>
 *
 * <h2>Pattern Syntax:</h2>
 * <ul>
 *   <li><b>com.example.Foo</b> - Exact class match</li>
 *   <li><b>com.example.*</b> - All classes directly in the package (no subpackages)</li>
 *   <li><b>com.example.**</b> - All classes in the package and all subpackages (recursive)</li>
 * </ul>
 *
 * @author Stefano Reksten
 * @see BeansXml
 * @see Exclude
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class Scan {

    /**
     * List of exclusion rules.
     *
     * <p>Each rule specifies a pattern of classes to exclude from bean scanning.
     * Rules can be conditional (only apply if certain classes are available).
     */
    @XmlElement(name = "exclude")
    private List<Exclude> excludes = new ArrayList<>();

    // ============================================
    // Getters
    // ============================================

    public List<Exclude> getExcludes() {
        return Collections.unmodifiableList(excludes);
    }

    // ============================================
    // Convenience Methods
    // ============================================

    /**
     * Checks if any exclusions are configured.
     *
     * @return true if at least one exclusion rule is configured
     */
    public boolean isEmpty() {
        return excludes.isEmpty();
    }

    @Override
    public String toString() {
        return "Scan{" +
               "excludes=" + excludes.size() +
               '}';
    }
}
