package com.threeamigos.common.util.implementations.injection.discovery;

import com.threeamigos.common.util.implementations.injection.beansxml.*;

import java.util.Collection;

/**
 * Utility class for evaluating exclude filters from beans.xml.
 *
 * <p>CDI 4.1 Section 12.4: Exclude filters allow fine-grained control over
 * which classes are excluded from bean discovery.
 *
 * <h2>Features:</h2>
 * <ul>
 *   <li>Pattern matching (exact, single-level wildcard, recursive wildcard)</li>
 *   <li>Conditional exclusions (if-class-available, if-class-not-available, if-system-property)</li>
 *   <li>Multi-beans.xml aggregation (excludes from all beans.xml files are applied)</li>
 * </ul>
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * ExcludeFilter filter = new ExcludeFilter(beansXmlConfigurations);
 * if (filter.isExcluded("com.example.legacy.OldClass")) {
 *     // Skip this class during bean discovery
 * }
 * }</pre>
 *
 * @author Stefano Reksten
 * @see Exclude
 * @see Scan
 */
public class ExcludeFilter {

    /**
     * Collection of BeansXml configurations containing exclude rules.
     */
    private final Collection<BeansXml> beansXmlConfigurations;

    /**
     * Creates a new ExcludeFilter.
     *
     * @param beansXmlConfigurations collection of BeansXml configurations to evaluate
     */
    public ExcludeFilter(Collection<BeansXml> beansXmlConfigurations) {
        this.beansXmlConfigurations = beansXmlConfigurations;
    }

    /**
     * Checks if a class should be excluded from bean discovery.
     *
     * <p>A class is excluded if ANY beans.xml contains an exclude rule that:
     * <ul>
     *   <li>Matches the class name (pattern match)</li>
     *   <li>Have all conditions satisfied (if any)</li>
     * </ul>
     *
     * @param className the fully qualified class name to check
     * @return true if the class should be excluded from bean discovery
     */
    public boolean isExcluded(String className) {
        if (className == null) {
            return false;
        }

        // Check all beans.xml configurations
        for (BeansXml beansXml : beansXmlConfigurations) {
            Scan scan = beansXml.getScan();
            if (scan == null || scan.isEmpty()) {
                continue;
            }

            // Check each exclude rule
            for (Exclude exclude : scan.getExcludes()) {
                if (matchesExclude(className, exclude)) {
                    System.out.println("[ExcludeFilter] Class excluded: " + className +
                                      " (pattern: " + exclude.getName() + ")");
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Checks if a class matches an exclude rule and all its conditions.
     *
     * @param className the class name to check
     * @param exclude the exclude rule
     * @return true if the class matches and all conditions are satisfied
     */
    private boolean matchesExclude(String className, Exclude exclude) {
        // First check if pattern matches
        if (!exclude.matches(className)) {
            return false;
        }

        // Pattern matches - now check conditions
        return evaluateConditions(exclude);
    }

    /**
     * Evaluates all conditions for an exclude rule.
     *
     * <p>ALL conditions must be satisfied for the exclusion to apply (AND logic).
     *
     * @param exclude the exclude rule with conditions
     * @return true if all conditions are satisfied (or no conditions exist)
     */
    private boolean evaluateConditions(Exclude exclude) {
        // If no conditions, exclusion always applies
        if (exclude.isUnconditional()) {
            return true;
        }

        // Check if-class-available conditions
        for (IfClassAvailable condition : exclude.getIfClassAvailable()) {
            if (!isClassAvailable(condition.getName())) {
                return false; // Condition isn't met - exclusion doesn't apply
            }
        }

        // Check if-class-not-available conditions
        for (IfClassNotAvailable condition : exclude.getIfClassNotAvailable()) {
            if (isClassAvailable(condition.getName())) {
                return false; // Condition isn't met - exclusion doesn't apply
            }
        }

        // Check if-system-property conditions
        for (IfSystemProperty condition : exclude.getIfSystemProperty()) {
            String propName = condition.getName();
            String expectedValue = condition.getValue();
            String actualValue = System.getProperty(propName);

            if (actualValue == null) {
                return false; // Condition isn't met - exclusion doesn't apply
            }
            // <if-system-property name="..."/> is satisfied when the property exists with any value.
            if (expectedValue != null && !expectedValue.equals(actualValue)) {
                return false; // Condition isn't met - exclusion doesn't apply
            }
        }

        // All conditions satisfied
        return true;
    }

    /**
     * Checks if a class is available on the classpath.
     *
     * @param className the fully qualified class name
     * @return true if the class can be loaded
     */
    private boolean isClassAvailable(String className) {
        if (className == null) {
            return false;
        }

        try {
            Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
