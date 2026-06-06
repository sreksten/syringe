package com.threeamigos.common.util.implementations.injection.beansxml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

/**
 * Model class for the &lt;trim/&gt; element in beans.xml.
 *
 * <p>CDI 4.1 Section 12.4: The trim element is an optimization that excludes
 * beans that are not explicitly needed by the application.
 *
 * <h2>Example XML:</h2>
 * <pre>{@code
 * <beans xmlns="https://jakarta.ee/xml/ns/jakartaee"
 *        bean-discovery-mode="all"
 *        version="4.0">
 *     <trim/>
 * </beans>
 * }</pre>
 *
 * <h2>What Gets Trimmed:</h2>
 * <p>When &lt;trim/&gt; is present, ONLY the following beans are enabled:
 * <ul>
 *   <li>Beans explicitly declared in beans.xml (alternatives, interceptors, decorators)</li>
 *   <li>Beans with injection points that are actually used in the application</li>
 *   <li>Beans that are injected somewhere (directly or transitively)</li>
 * </ul>
 *
 * <p>All other beans are EXCLUDED, even if they have bean-defining annotations.
 *
 * <h2>Use Case:</h2>
 * <p>Trim is useful for large applications with many beans where:
 * <ul>
 *   <li>You want to reduce memory footprint</li>
 *   <li>You want to speed up container startup</li>
 *   <li>You have many unused beans from third-party libraries</li>
 * </ul>
 *
 * <h2>Example Scenario:</h2>
 * <pre>{@code
 * // You have 1000 beans in classpath
 * // But your app only uses 50 of them
 * // With <trim/>, only those 50 are instantiated
 *
 * @ApplicationScoped
 * public class UsedService { } // ✓ Kept (used)
 *
 * @ApplicationScoped
 * public class UnusedService { } // ✗ Trimmed (never injected)
 *
 * @ApplicationScoped
 * public class MyApp {
 *     @Inject UsedService service; // This makes UsedService "used"
 * }
 * }</pre>
 *
 * <p><b>Note:</b> This is an advanced optimization. Most applications don't need it.
 * Only use trim if you have confirmed performance issues with bean discovery.
 *
 * @author Stefano Reksten
 * @see BeansXml#isTrimEnabled()
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class Trim {

    /**
     * Empty class - the presence of this element is all that matters.
     *
     * <p>In XML, this is represented as a self-closing tag: &lt;trim/&gt;
     */

    @Override
    public String toString() {
        return "Trim{enabled}";
    }
}
