package com.threeamigos.common.util.implementations.injection.beansxml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Model class for the &lt;interceptors&gt; section of beans.xml.
 *
 * <p>CDI 4.1 Section 9.5: Interceptors can be enabled and ordered via beans.xml.
 * The order in the list determines interceptor precedence - the first entry has the highest priority.
 *
 * <h2>Example XML:</h2>
 * <pre>{@code
 * <interceptors>
 *     <class>com.example.TransactionalInterceptor</class>
 *     <class>com.example.SecurityInterceptor</class>
 *     <class>com.example.LoggingInterceptor</class>
 * </interceptors>
 * }</pre>
 *
 * <h2>Interceptor Ordering:</h2>
 * <p>In the example above:
 * <ol>
 *   <li>TransactionalInterceptor runs first (outermost)</li>
 *   <li>SecurityInterceptor runs second</li>
 *   <li>LoggingInterceptor runs last (innermost, closest to business method)</li>
 * </ol>
 *
 * <p><b>Note:</b> CDI 4.1 prefers @Priority annotation over beans.xml for interceptor ordering.
 * However, beans.xml interceptor ordering is still part of the specification.
 *
 * <p><b>Priority Rules:</b>
 * <ul>
 *   <li>@Priority on interceptor class takes precedence over beans.xml</li>
 *   <li>If both @Priority and beans.xml are used, @Priority wins</li>
 *   <li>Interceptors not listed in beans.xml and without @Priority are disabled</li>
 * </ul>
 *
 * @author Stefano Reksten
 * @see BeansXml
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class Interceptors {

    /**
     * An ordered list of interceptor classes to enable.
     *
     * <p>Each entry is a fully qualified class name (e.g., "com.example.TransactionalInterceptor").
     *
     * <p>The order in this list determines interceptor execution order:
     * <ul>
     *   <li>First entry = highest priority (outermost interceptor)</li>
     *   <li>Last entry = lowest priority (innermost interceptor, closest to target method)</li>
     * </ul>
     *
     * <p><b>Execution Flow:</b>
     * <pre>
     * Client → Interceptor1 → Interceptor2 → Interceptor3 → Target Method
     *                                                      ← Return Value
     * </pre>
     */
    @XmlElement(name = "class")
    private List<String> classes = new ArrayList<>();

    // ============================================
    // Getters
    // ============================================

    public List<String> getClasses() {
        return Collections.unmodifiableList(classes);
    }

    // ============================================
    // Convenience Methods
    // ============================================

    /**
     * Checks if any interceptors are configured.
     *
     * @return true if at least one interceptor is configured
     */
    public boolean isEmpty() {
        return classes.isEmpty();
    }

    @Override
    public String toString() {
        return "Interceptors{" +
               "classes=" + classes.size() +
               '}';
    }
}
