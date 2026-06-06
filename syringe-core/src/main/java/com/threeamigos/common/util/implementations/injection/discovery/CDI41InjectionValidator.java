package com.threeamigos.common.util.implementations.injection.discovery;

import com.threeamigos.common.util.implementations.injection.annotations.QualifiersHelper;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.events.ObserverMethodInfo;
import com.threeamigos.common.util.implementations.injection.knowledgebase.DecoratorInfo;
import com.threeamigos.common.util.implementations.injection.knowledgebase.InterceptorInfo;
import com.threeamigos.common.util.implementations.injection.knowledgebase.ScopeMetadata;
import com.threeamigos.common.util.implementations.injection.resolution.BeanImpl;
import com.threeamigos.common.util.implementations.injection.resolution.LegacyNewBeanAdapter;
import com.threeamigos.common.util.implementations.injection.resolution.ProducerBean;
import com.threeamigos.common.util.implementations.injection.resolution.TypeChecker;
import com.threeamigos.common.util.implementations.injection.scopes.InjectionPointImpl;
import com.threeamigos.common.util.implementations.injection.spi.SyntheticBean;
import com.threeamigos.common.util.implementations.injection.spi.SyntheticProducerBeanImpl;
import com.threeamigos.common.util.implementations.injection.annotations.AnnotationComparator;
import com.threeamigos.common.util.implementations.injection.annotations.AnnotatedMetadataHelper;
import com.threeamigos.common.util.implementations.injection.resolution.GenericTypeResolver;
import com.threeamigos.common.util.implementations.injection.annotations.legacy.LegacyNewQualifierHelper;
import com.threeamigos.common.util.implementations.injection.types.RawTypeExtractor;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.inject.spi.PassivationCapable;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.Decorator;
import jakarta.enterprise.inject.spi.InterceptionFactory;
import jakarta.enterprise.inject.spi.Interceptor;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.inject.Provider;

import java.lang.annotation.Annotation;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsEnum.*;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.*;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationExtractors.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Validates that all injection points can be satisfied according to CDI 4.1 rules.
 *
 * <p>This validator runs AFTER bean discovery and validation (CDI41BeanValidator) completes,
 * and checks whether all declared injection points can be resolved to valid beans.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Check for unsatisfied dependencies (no matching bean found)</li>
 *   <li>Check for ambiguous dependencies (multiple matching beans without qualifiers)</li>
 *   <li>Detect invalid beans being used as dependencies</li>
 *   <li>Handle Instance&lt;T&gt; and Provider&lt;T&gt; injection points (always satisfiable)</li>
 *   <li>Validate producer methods can satisfy injection points</li>
 *   <li>Detect circular dependencies during the validation phase</li>
 *   <li>Validate enabled alternatives (only one per type)</li>
 *   <li>Report all resolution errors to KnowledgeBase</li>
 * </ul>
 *
 * <p>This allows the system to avoid false positives: beans with validation errors
 * only cause application failure if they are actually needed for injection.
 *
 * <p>Uses existing utilities:
 * <ul>
 *   <li>{@link TypeChecker} - for proper type hierarchy and generic matching</li>
 *   <li>{@link RawTypeExtractor} - for extracting raw types from generic types</li>
 * </ul>
 */
public class CDI41InjectionValidator {

    private final KnowledgeBase knowledgeBase;
    private final TypeChecker typeChecker;
    private final boolean legacyCdi10NewEnabled;
    private final boolean allowNonPortableAsyncObserverEventParameterPriority;

    /**
     * Tracks beans currently being resolved to detect circular dependencies.
     * Thread-local to support concurrent validation if needed in the future.
     */
    private final ThreadLocal<Deque<Bean<?>>> resolutionStack = ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * Deduplicates circular dependency errors in the current validation thread.
     */
    private final ThreadLocal<Set<String>> reportedCircularDependencies =
            ThreadLocal.withInitial(HashSet::new);

    public CDI41InjectionValidator(KnowledgeBase knowledgeBase) {
        this(knowledgeBase, false, false);
    }

    public CDI41InjectionValidator(KnowledgeBase knowledgeBase, boolean legacyCdi10NewEnabled) {
        this(knowledgeBase, legacyCdi10NewEnabled, false);
    }

    public CDI41InjectionValidator(KnowledgeBase knowledgeBase,
                                   boolean legacyCdi10NewEnabled,
                                   boolean allowNonPortableAsyncObserverEventParameterPriority) {
        this.knowledgeBase = Objects.requireNonNull(knowledgeBase, "knowledgeBase cannot be null");
        this.typeChecker = new TypeChecker();
        this.legacyCdi10NewEnabled = legacyCdi10NewEnabled;
        this.allowNonPortableAsyncObserverEventParameterPriority =
                allowNonPortableAsyncObserverEventParameterPriority;
    }

    /**
     * Validates all injection points across all valid beans in the KnowledgeBase.
     *
     * <p>For each valid bean, checks that all its injection points can be satisfied.
     * Errors are added to the KnowledgeBase and can be retrieved via:
     * <ul>
     *   <li>{@link KnowledgeBase#getErrors()} - general resolution errors</li>
     *   <li>{@link KnowledgeBase#getInjectionErrors()} - injection-specific errors</li>
     * </ul>
     *
     * <p>This method also performs additional validations:
     * <ul>
     *   <li>Alternative bean validation - ensures only one alternative per type</li>
     *   <li>Circular dependency detection - validates injection graphs have no cycles</li>
     * </ul>
     */
    public void validateAllInjectionPoints() {

        // Only validate injection points of valid beans
        Collection<Bean<?>> validBeans = knowledgeBase.getValidBeans().stream()
                .filter(this::isBeanEnabledForResolution)
                .collect(Collectors.toList());

        // Enhancement 3: Validate alternative beans (only one alternative per type)
        validateAlternatives(validBeans);
        // CDI 4.1 §5.3.1: validate ambiguous bean names at initialization.
        validateNameResolution(validBeans);
        // CDI 4.1 §20.3: if a decorator matches a managed bean, the bean class must be proxyable.
        validateDecoratedManagedBeansProxyable(validBeans);
        // CDI 4.1 §3.10: intercepted beans must be proxyable.
        validateInterceptedManagedBeansProxyable(validBeans);

        // Enhancement 4: Validate passivation capability for beans in passivating scopes
        validatePassivation(validBeans);

        // Enhancement 5: Scan and validate observer methods
        scanAndValidateObserverMethods(validBeans);

        Set<Bean<?>> globallyVisited = Collections.newSetFromMap(new IdentityHashMap<>());

        // Validate each bean's dependency graph (including circular dependency detection)
        for (Bean<?> bean : validBeans) {
            validateBeanWithCircularCheck(bean, globallyVisited);
        }

        // Clean up thread-local storage
        resolutionStack.remove();
        reportedCircularDependencies.remove();
    }

    private void validateDecoratedManagedBeansProxyable(Collection<Bean<?>> validBeans) {
        for (Bean<?> bean : validBeans) {
            if (!(bean instanceof BeanImpl<?>)) {
                continue;
            }
            if (!hasBoundDecorator(bean)) {
                continue;
            }

            String reason = unproxyableReason(bean.getBeanClass(), bean);
            if (reason == null) {
                continue;
            }

            knowledgeBase.addDefinitionError(
                    "Managed bean " + bean.getBeanClass().getName() +
                            " matches a decorator but is unproxyable: " + reason);
        }
    }

    private void validateInterceptedManagedBeansProxyable(Collection<Bean<?>> validBeans) {
        for (Bean<?> bean : validBeans) {
            if (!(bean instanceof BeanImpl<?>)) {
                continue;
            }
            Class<?> beanClass = bean.getBeanClass();
            if (beanClass == null) {
                continue;
            }
            if (hasInterceptorAnnotation(beanClass) || hasDecoratorAnnotation(beanClass)) {
                continue;
            }
            if (!hasBoundInterceptor(bean)) {
                continue;
            }

            // Deployment must fail for intercepted beans that are unproxyable due to
            // final class declaration or non-private final business methods.
            boolean finalClass = Modifier.isFinal(beanClass.getModifiers());
            Method finalBusinessMethod = findNonStaticFinalNonPrivateMethod(beanClass);
            if (knowledgeBase.shouldIgnoreFinalMethods(bean)) {
                finalBusinessMethod = null;
            }
            if (!finalClass && finalBusinessMethod == null) {
                continue;
            }

            if (finalClass) {
                knowledgeBase.addError(
                        "Managed bean " + beanClass.getName() +
                                " has bound interceptor(s) but is a final class");
            }
            if (finalBusinessMethod != null) {
                knowledgeBase.addError(
                        "Managed bean " + beanClass.getName() +
                                " has bound interceptor(s) but declares non-private final method: " +
                                finalBusinessMethod.getName());
            }
        }
    }

    private void validateNameResolution(Collection<Bean<?>> validBeans) {
        Map<String, Set<Bean<?>>> beansByName = new HashMap<>();
        for (Bean<?> bean : validBeans) {
            String name = bean.getName();
            if (name == null || name.isEmpty()) {
                continue;
            }
            beansByName.computeIfAbsent(name, k -> Collections.newSetFromMap(new IdentityHashMap<>()))
                    .add(bean);
        }

        for (Map.Entry<String, Set<Bean<?>>> entry : beansByName.entrySet()) {
            String beanName = entry.getKey();
            Set<Bean<?>> candidates = entry.getValue();
            if (candidates.size() <= 1) {
                continue;
            }

            Bean<?> resolved = resolveAmbiguousName(candidates);
            if (resolved == null) {
                String beans = candidates.stream()
                        .map(b -> b.getBeanClass().getName())
                        .distinct()
                        .sorted()
                        .collect(Collectors.joining(", "));
                knowledgeBase.addDefinitionError(
                        "Ambiguous bean name '" + beanName + "': [" + beans + "]"
                );
            }
        }

        // CDI 4.1 §5.3.1: deployment problem when one name is x and another is x.y
        // where y is a valid bean name. This must apply recursively as well
        // (e.g. "foo.bar" vs. "foo.bar.baz").
        Set<String> names = new HashSet<>(beansByName.keySet());
        Set<String> reportedConflicts = new HashSet<>();
        for (String shorter : names) {
            if (shorter == null || shorter.isEmpty()) {
                continue;
            }
            for (String longer : names) {
                if (longer == null || longer.length() <= shorter.length()) {
                    continue;
                }
                String prefix = shorter + ".";
                if (!longer.startsWith(prefix)) {
                    continue;
                }

                String suffix = longer.substring(prefix.length());
                if (!isValidBeanName(suffix)) {
                    continue;
                }

                String conflictKey = shorter + "->" + longer;
                if (reportedConflicts.add(conflictKey)) {
                    knowledgeBase.addDefinitionError(
                            "Bean name conflict detected: '" + shorter + "' and '" + longer + "'"
                    );
                }
            }
        }
    }

    private Bean<?> resolveAmbiguousName(Set<Bean<?>> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.iterator().next();
        }

        List<Bean<?>> alternatives = candidates.stream()
                .filter(this::isEffectivelyAlternative)
                .collect(Collectors.toList());

        // Eliminate non-alternatives first (CDI 4.1 §5.3.1).
        List<Bean<?>> remaining = alternatives.isEmpty()
                ? new ArrayList<>(candidates)
                : alternatives;

        if (remaining.size() == 1) {
            return remaining.get(0);
        }

        // If all remaining beans are alternatives with priority, keep only highest priority.
        boolean allHavePriority = remaining.stream().allMatch(this::hasPriorityValue);
        if (allHavePriority) {
            int highest = remaining.stream()
                    .mapToInt(this::extractPriorityValue)
                    .max()
                    .orElse(Integer.MIN_VALUE);
            List<Bean<?>> highestPriorityBeans = remaining.stream()
                    .filter(bean -> extractPriorityValue(bean) == highest)
                    .collect(Collectors.toList());
            if (highestPriorityBeans.size() == 1) {
                return highestPriorityBeans.get(0);
            }
            return null;
        }

        return null;
    }

    private boolean hasPriorityValue(Bean<?> bean) {
        return extractPriorityValue(bean) != Integer.MIN_VALUE;
    }

    private int extractPriorityValue(Bean<?> bean) {
        if (bean == null) {
            return Integer.MIN_VALUE;
        }

        if (bean instanceof ProducerBean) {
            ProducerBean<?> producerBean = (ProducerBean<?>) bean;
            Method producerMethod = producerBean.getProducerMethod();
            if (producerMethod != null) {
                Integer priority = getPriorityValue(producerMethod);
                if (priority != null) {
                    return priority;
                }
            }
            Field producerField = producerBean.getProducerField();
            if (producerField != null) {
                Integer priority = getPriorityValue(producerField);
                if (priority != null) {
                    return priority;
                }
            }

            Integer explicitProducerPriority = producerBean.getPriority();
            if (explicitProducerPriority != null) {
                return explicitProducerPriority;
            }

            Integer declaringPriority = getPriorityValue(producerBean.getDeclaringClass());
            if (declaringPriority != null) {
                return declaringPriority;
            }
        }

        if (bean instanceof BeanImpl) {
            Integer priority = ((BeanImpl<?>) bean).getPriority();
            if (priority != null) {
                return priority;
            }
        }

        if (bean instanceof SyntheticBean) {
            Integer priority = ((SyntheticBean<?>) bean).getPriority();
            if (priority != null) {
                return priority;
            }
        }

        Integer classPriority = getPriorityValue(bean.getBeanClass());
        return classPriority == null ? Integer.MIN_VALUE : classPriority;
    }

    private boolean isValidSimpleBeanName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidBeanName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String[] segments = name.split("\\.");
        if (segments.length == 0) {
            return false;
        }
        for (String segment : segments) {
            if (!isValidSimpleBeanName(segment)) {
                return false;
            }
        }
        return true;
    }

    // ============================================
    // Enhancement 2: Circular Dependency Detection
    // ============================================

    private void validateBeanWithCircularCheck(Bean<?> rootBean, Set<Bean<?>> globallyVisited) {
        Deque<Bean<?>> stack = resolutionStack.get();
        stack.clear();
        try {
            validateBeanDependencies(rootBean, stack, globallyVisited);
        } finally {
            stack.clear();
        }
    }

    private boolean validateBeanDependencies(Bean<?> owningBean, Deque<Bean<?>> stack,
                                             Set<Bean<?>> globallyVisited) {
        if (containsByIdentity(stack, owningBean)) {
            reportCircularDependency(formatCircularDependencyChain(stack, owningBean), null, null);
            return false;
        }

        if (globallyVisited.contains(owningBean)) {
            return true;
        }

        stack.addLast(owningBean);
        globallyVisited.add(owningBean);
        boolean allValid = true;
        try {
            for (InjectionPoint injectionPoint : owningBean.getInjectionPoints()) {
                boolean valid = validateInjectionPoint(injectionPoint, owningBean);
                allValid &= valid;
                if (!valid) {
                    continue;
                }

                Optional<Bean<?>> resolvedDependency = resolveInjectionPointTargetBean(injectionPoint);
                if (!resolvedDependency.isPresent()) {
                    continue;
                }

                Bean<?> dependency = resolvedDependency.get();
                if (containsByIdentity(stack, dependency)) {
                    if (!isResolvableCircularDependency(stack, dependency)) {
                        String chain = formatCircularDependencyChain(stack, dependency);
                        reportCircularDependency(chain, injectionPoint, owningBean);
                        allValid = false;
                    }
                    continue;
                }

                allValid &= validateBeanDependencies(dependency, stack, globallyVisited);
            }
        } finally {
            stack.removeLast();
        }

        return allValid;
    }

    private Optional<Bean<?>> resolveInjectionPointTargetBean(InjectionPoint injectionPoint) {
        Type requiredType = injectionPoint.getType();
        if (isInstanceOrProvider(requiredType) || isInterceptionFactory(requiredType)) {
            return Optional.empty();
        }

        Set<Bean<?>> candidates = findMatchingBeans(requiredType, injectionPoint.getQualifiers());
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        if (candidates.size() > 1) {
            return resolveByAlternativePrecedence(candidates);
        }

        return Optional.of(candidates.iterator().next());
    }

    private void reportCircularDependency(String chain, InjectionPoint injectionPoint, Bean<?> owningBean) {
        StringBuilder message = new StringBuilder("Circular dependency detected: ").append(chain);
        if (injectionPoint != null && owningBean != null) {
            message.append(" at ").append(formatInjectionPoint(injectionPoint, owningBean));
        }

        String rendered = message.toString();
        Set<String> reported = reportedCircularDependencies.get();
        if (reported.add(rendered)) {
            knowledgeBase.addInjectionError(rendered);
        }
    }

    private boolean containsByIdentity(Deque<Bean<?>> stack, Bean<?> target) {
        for (Bean<?> bean : stack) {
            if (bean == target) {
                return true;
            }
        }
        return false;
    }

    private int indexByIdentity(List<Bean<?>> beans, Bean<?> target) {
        for (int i = 0; i < beans.size(); i++) {
            if (beans.get(i) == target) {
                return i;
            }
        }
        return -1;
    }

    private String formatCircularDependencyChain(Deque<Bean<?>> stack, Bean<?> repeatedBean) {
        List<Bean<?>> path = new ArrayList<>(stack);
        int start = indexByIdentity(path, repeatedBean);
        if (start < 0) {
            String name = beanDisplayName(repeatedBean);
            return name + " -> " + name;
        }

        List<Bean<?>> cycle = new ArrayList<>(path.subList(start, path.size()));
        if (cycle.isEmpty()) {
            String name = beanDisplayName(repeatedBean);
            return name + " -> " + name;
        }

        int canonicalStart = 0;
        String canonicalName = beanDisplayName(cycle.get(0));
        for (int i = 1; i < cycle.size(); i++) {
            String candidate = beanDisplayName(cycle.get(i));
            if (candidate.compareTo(canonicalName) < 0) {
                canonicalStart = i;
                canonicalName = candidate;
            }
        }

        List<String> names = new ArrayList<>(cycle.size() + 1);
        for (int i = 0; i < cycle.size(); i++) {
            names.add(beanDisplayName(cycle.get((canonicalStart + i) % cycle.size())));
        }
        names.add(names.get(0));
        return String.join(" -> ", names);
    }

    private String beanDisplayName(Bean<?> bean) {
        String simpleName = bean.getBeanClass().getSimpleName();
        if (!simpleName.isEmpty()) {
            return simpleName;
        }
        return bean.getBeanClass().getName();
    }

    private boolean isResolvableCircularDependency(Deque<Bean<?>> stack, Bean<?> repeatedBean) {
        List<Bean<?>> cycle = extractCycle(stack, repeatedBean);
        for (Bean<?> bean : cycle) {
            if (isNormalScopedBean(bean)) {
                return true;
            }
        }
        return false;
    }

    private List<Bean<?>> extractCycle(Deque<Bean<?>> stack, Bean<?> repeatedBean) {
        List<Bean<?>> path = new ArrayList<>(stack);
        int start = indexByIdentity(path, repeatedBean);
        if (start < 0) {
            return Collections.singletonList(repeatedBean);
        }
        return new ArrayList<>(path.subList(start, path.size()));
    }

    private boolean isNormalScopedBean(Bean<?> bean) {
        if (bean == null) {
            return false;
        }
        Class<? extends Annotation> scope = bean.getScope();
        if (scope == null) {
            return false;
        }
        return hasBuiltInNormalScopeAnnotation(scope) || hasNormalScopeAnnotation(scope);
    }

    // ============================================
    // Enhancement 3: Alternative Bean Validation
    // ============================================

    /**
     * Validates that alternative beans are properly configured according to CDI 4.1 specification.
     *
     * <p><b>CDI 4.1 rules for alternatives (Section 5.1.2):</b>
     * <ul>
     *   <li>Multiple alternatives for the same type ARE allowed if they have different {@literal @}Priority values</li>
     *   <li>Higher priority value = higher precedence (e.g., {@literal @}Priority(200) beats {@literal @}Priority(100))</li>
     *   <li>If alternatives have the SAME priority (or no priority), it's an ambiguous dependency → ERROR</li>
     *   <li>If an alternative exists, it takes precedence over non-alternative beans</li>
     * </ul>
     *
     * <p><b>Valid scenario (different priorities):</b>
     * <pre>
     * {@literal @}Alternative {@literal @}Priority(100) {@literal @}ApplicationScoped
     * class MockDatabaseService implements DatabaseService { }
     *
     * {@literal @}Alternative {@literal @}Priority(200) {@literal @}ApplicationScoped
     * class TestDatabaseService implements DatabaseService { }
     *
     * // ✓ VALID: TestDatabaseService wins (higher priority)
     * </pre>
     *
     * <p><b>Invalid scenario (same/no priority):</b>
     * <pre>
     * {@literal @}Alternative {@literal @}ApplicationScoped
     * class MockDatabaseService implements DatabaseService { }
     *
     * {@literal @}Alternative {@literal @}ApplicationScoped
     * class TestDatabaseService implements DatabaseService { }
     *
     * // ✗ ERROR: Ambiguous - both have no priority!
     * </pre>
     *
     * @param validBeans collection of all valid beans
     */
    private void validateAlternatives(Collection<Bean<?>> validBeans) {
        // Group by type AND effective qualifiers to detect ambiguity only within the same qualifier set.
        Map<Type, Map<Set<Annotation>, Set<Bean<?>>>> byTypeAndQuals = new HashMap<>();

        for (Bean<?> bean : validBeans) {
            Set<Annotation> qKey = qualifierKey(bean.getQualifiers());
            for (Type type : bean.getTypes()) {
                if (isJavaLangObject(type)) {
                    // Skip java.lang.Object to avoid spurious ambiguity: every bean has Object
                    // as a type, but injection points of Object are exceedingly rare and would
                    // be validated during normal resolution if present.
                    continue;
                }
                byTypeAndQuals
                        .computeIfAbsent(type, t -> new HashMap<>())
                        .computeIfAbsent(qKey, k -> Collections.newSetFromMap(new IdentityHashMap<>()))
                        .add(bean);
            }
        }

        // Check each (type, qualifier set) bucket for ambiguous alternatives
        for (Map.Entry<Type, Map<Set<Annotation>, Set<Bean<?>>>> typeEntry : byTypeAndQuals.entrySet()) {
            Type type = typeEntry.getKey();
            for (Map.Entry<Set<Annotation>, Set<Bean<?>>> qualEntry : typeEntry.getValue().entrySet()) {
                List<Bean<?>> alternatives = qualEntry.getValue().stream()
                        .filter(this::isEffectivelyAlternative)
                        .filter(this::isBeanEnabledForResolution)
                        .collect(Collectors.toList());

                if (alternatives.size() > 1) {
                    // Deduplicate logically equivalent beans (same bean class) within this qualifier bucket
                    Map<String, Bean<?>> uniqueByClass = new LinkedHashMap<>();
                    for (Bean<?> alt : alternatives) {
                        uniqueByClass.put(alt.getBeanClass().getName(), alt);
                    }
                    alternatives = new ArrayList<>(uniqueByClass.values());

                    Map<Bean<?>, Integer> priorities = new HashMap<>();
                    List<Bean<?>> noPriorityAlternatives = new ArrayList<>();

                    for (Bean<?> alt : alternatives) {
                        int priority = extractPriorityValue(alt);
                        if (priority != Integer.MIN_VALUE) {
                            priorities.put(alt, priority);
                        } else {
                            noPriorityAlternatives.add(alt);
                        }
                    }

                    if (noPriorityAlternatives.size() > 1) {
                        String alternativeList = noPriorityAlternatives.stream()
                                .map(b -> b.getBeanClass().getName())
                                .collect(Collectors.joining(", "));

                        knowledgeBase.addError(
                                "Ambiguous alternatives for type " + formatType(type) +
                                " with qualifiers " + formatQualifiers(qualEntry.getKey()) +
                                ": [" + alternativeList + "]. " +
                                "Multiple alternatives without @Priority - cannot determine precedence. " +
                                "Add @Priority to resolve ambiguity."
                        );
                    }

                    if (!priorities.isEmpty()) {
                        Map<Integer, List<Bean<?>>> byPriority = new HashMap<>();
                        for (Map.Entry<Bean<?>, Integer> e : priorities.entrySet()) {
                            byPriority.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
                        }
                        int max = byPriority.keySet().stream().max(Integer::compareTo).orElse(Integer.MIN_VALUE);
                        List<Bean<?>> top = byPriority.get(max);
                        if (top != null && top.size() > 1) {
                            String samePriorityList = top.stream()
                                    .map(b -> b.getBeanClass().getName())
                                    .collect(Collectors.joining(", "));

                            knowledgeBase.addError(
                                    "Ambiguous alternatives for type " + formatType(type) +
                                    " with qualifiers " + formatQualifiers(qualEntry.getKey()) +
                                    ": [" + samePriorityList + "] all have the same (highest) priority @Priority(" + max + "). " +
                                    "Alternatives must have different priority values to resolve ambiguity."
                            );
                        }
                    }
                }
            }
        }
    }

    private boolean isJavaLangObject(Type type) {
        if (type instanceof Class) {
            return Object.class.equals(type);
        }
        return type != null && "java.lang.Object".equals(type.getTypeName());
    }

    // ============================================
    // Enhancement 4: Passivation Validation
    // ============================================

    /**
     * Validates that beans in passivation-capable scopes are serializable.
     *
     * <p>CDI 4.1 Passivation Requirements:
     * <ul>
     *   <li>@SessionScoped beans MUST implement Serializable (session can be passivated)</li>
     *   <li>@ConversationScoped beans MUST implement Serializable (conversation can be passivated)</li>
     *   <li>@ApplicationScoped beans do NOT need to be Serializable (never passivated)</li>
     *   <li>@RequestScoped beans do NOT need to be Serializable (short-lived)</li>
     *   <li>@Dependent beans do NOT need to be Serializable (lifecycle tied to parent)</li>
     * </ul>
     *
     * <p>When a bean is in a passivation-capable scope, it must be passivation-capable:
     * - The bean class must implement {@link java.io.Serializable}
     * - All dependencies (injection points) must be passivation capable (checked recursively)
     * - All interceptors and decorators must be serializable (checked in future phases)
     *
     * <p>Important: Client proxies are ALWAYS Serializable, so injecting non-passivating
     * beans into passivating beans is allowed (the proxy is serialized, not the actual bean).
     * This means normal-scoped beans (@ApplicationScoped, @SessionScoped, etc.) are always
     * passivation-capable dependencies because they are injected as proxies.
     *
     * @param validBeans collection of valid beans to validate
     */
    private void validatePassivation(Collection<Bean<?>> validBeans) {
        // Track visited beans to avoid infinite recursion in circular dependencies
        Set<Bean<?>> visited = new HashSet<>();
        Set<ProducerBean<?>> validatedProducerBeans =
                Collections.newSetFromMap(new IdentityHashMap<>());

        for (ProducerBean<?> producerBean : knowledgeBase.getProducerBeans()) {
            if (producerBean == null) {
                continue;
            }
            if (knowledgeBase.isImplicitBeanArchiveScanningDisabled()) {
                BeanArchiveMode archiveMode = knowledgeBase.getBeanArchiveMode(producerBean.getDeclaringClass());
                if (archiveMode != BeanArchiveMode.EXPLICIT) {
                    continue;
                }
            }
            if (!isBeanEnabledForResolution(producerBean)) {
                continue;
            }
            if (validatedProducerBeans.add(producerBean)) {
                validatePassivatingProducerDeclaration(producerBean);
            }
        }

        for (Bean<?> bean : validBeans) {
            Class<? extends Annotation> scopeAnnotation = bean.getScope();

            // Check if this is a passivation-capable scope
            if (isPassivationCapableScope(scopeAnnotation)) {
                // Producer beans in passivating scopes are validated via producer declaration/runtime checks.
                // The declaring bean class itself does not need to be passivation-capable.
                boolean producerBean = bean instanceof ProducerBean ||
                        bean.getClass().getName().contains("ProducerBean");
                // Custom Bean implementations (including synthetic beans) may be passivation-capable
                // via PassivationCapable#getId() even when beanClass itself is not Serializable.
                boolean customPassivationCapableBean = bean instanceof PassivationCapable &&
                        !(bean instanceof BeanImpl) &&
                        !producerBean;
                if (!producerBean && !customPassivationCapableBean &&
                        !java.io.Serializable.class.isAssignableFrom(bean.getBeanClass())) {
                    knowledgeBase.addError(
                            "Bean " + bean.getBeanClass().getName() +
                            " has passivation-capable scope @" + scopeAnnotation.getSimpleName() +
                            " but does not implement java.io.Serializable. " +
                            "Beans in @SessionScoped or @ConversationScoped must be Serializable " +
                            "because the container may passivate (serialize) them to disk or database."
                    );
                }

                // Recursively validate all dependencies are passivation-capable
                visited.clear();
                validateDependenciesPassivationCapable(bean, visited);

                // Interceptors and decorators attached to passivating beans must be passivation-capable.
                validateInterceptorsAndDecoratorsPassivation(bean, validBeans);
            }
        }
    }

    /**
     * Recursively validates that all dependencies of a bean are passivation-capable.
     *
     * <p>This method checks that for a bean in a passivation-capable scope, all its
     * injected dependencies can be safely serialized. The rules are:
     * <ul>
     *   <li>Normal-scoped beans (@ApplicationScoped, @SessionScoped, etc.) are ALWAYS passivation-capable
     *       because they are injected as serializable client proxies</li>
     *   <li>@Dependent scoped beans MUST be Serializable if injected into passivation-capable beans</li>
     *   <li>@Dependent beans' dependencies are recursively checked (transitive closure)</li>
     *   <li>Instance&lt;T&gt; and Provider&lt;T&gt; are always passivation-capable (they are proxies)</li>
     * </ul>
     *
     * @param bean the bean whose dependencies to check
     * @param visited set of already visited beans to prevent infinite recursion
     * @return true if all dependencies are passivation-capable
     */
    private boolean validateDependenciesPassivationCapable(Bean<?> bean, Set<Bean<?>> visited) {
        // Avoid infinite recursion on circular dependencies
        if (visited.contains(bean)) {
            return true;
        }
        visited.add(bean);

        boolean allValid = true;

        for (InjectionPoint injectionPoint : bean.getInjectionPoints()) {
            // CDI 4.1 §17.5.2: transient fields and @TransientReference parameters
            // are passivation-capable injection points by definition.
            if (isPassivationCapableInjectionPointByDeclaration(injectionPoint)) {
                continue;
            }

            Type requiredType = injectionPoint.getType();
            Set<Annotation> qualifiers = injectionPoint.getQualifiers();

            // Instance<T> and Provider<T> are always passivation-capable (they are proxies)
            if (isInstanceOrProvider(requiredType)) {
                continue;
            }

            // Find the bean that will be injected
            Set<Bean<?>> candidates = findMatchingBeans(requiredType, qualifiers);

            if (candidates.isEmpty()) {
                // Unsatisfied dependency - already reported by validateInjectionPoint
                continue;
            }

            // Get the single resolved bean (ambiguity already checked in validateInjectionPoint)
            Bean<?> resolvedBean;
            if (candidates.size() > 1) {
                Optional<Bean<?>> preferred = resolveByAlternativePrecedence(candidates);
                if (!preferred.isPresent()) {
                    // Ambiguous - already reported by validateInjectionPoint
                    continue;
                }
                resolvedBean = preferred.get();
            } else {
                resolvedBean = candidates.iterator().next();
            }

            // Check if dependency is passivation-capable
            Class<? extends Annotation> dependencyScope = resolvedBean.getScope();

            // CDI 4.1 §17.5.3: these built-in beans are passivation-capable dependencies.
            if (isBuiltInPassivationCapableDependency(requiredType)) {
                continue;
            }

            // CDI 4.1 §17.5.3: custom Bean implementations that implement PassivationCapable
            // are passivation-capable dependencies.
            if (resolvedBean instanceof PassivationCapable &&
                    !(resolvedBean instanceof BeanImpl) &&
                    !(resolvedBean instanceof ProducerBean)) {
                if (isDependentScope(dependencyScope)) {
                    allValid &= validateDependenciesPassivationCapable(resolvedBean, visited);
                }
                continue;
            }

            // Normal-scoped beans are ALWAYS passivation-capable because they inject as proxies
            if (isNormalScope(dependencyScope)) {
                // Proxy is serializable, so this dependency is fine
                continue;
            }

            // @Dependent scoped dependencies must be Serializable themselves
            if (isDependentScope(dependencyScope)) {
                // CDI 4.1 §17.5.5: @Dependent producers may still be legal at deployment time
                // and validated at runtime based on produced instance passivation capability.
                if (resolvedBean instanceof ProducerBean ||
                        resolvedBean.getClass().getName().contains("ProducerBean")) {
                    continue;
                }
                if (!java.io.Serializable.class.isAssignableFrom(resolvedBean.getBeanClass())) {
                    knowledgeBase.addError(
                            "Bean " + bean.getBeanClass().getName() +
                            " has passivation-capable scope @" + bean.getScope().getSimpleName() +
                            " and injects @Dependent bean " + resolvedBean.getBeanClass().getName() +
                            " at " + formatInjectionPoint(injectionPoint, bean) +
                            ", but the dependency does not implement java.io.Serializable. " +
                            "@Dependent beans injected into passivation-capable beans must be Serializable."
                    );
                    allValid = false;
                } else {
                    // Recursively check @Dependent bean's dependencies
                    allValid &= validateDependenciesPassivationCapable(resolvedBean, visited);
                }
            }

            // Pseudo-scoped beans (@Singleton from JSR-330) should also be checked
            // They are not proxied, so they need to be Serializable
            if (!isNormalScope(dependencyScope) && !isDependentScope(dependencyScope)) {
                // Custom scope or @Singleton - must be Serializable
                if (!java.io.Serializable.class.isAssignableFrom(resolvedBean.getBeanClass())) {
                    knowledgeBase.addError(
                            "Bean " + bean.getBeanClass().getName() +
                            " has passivation-capable scope @" + bean.getScope().getSimpleName() +
                            " and injects non-normal-scoped bean " + resolvedBean.getBeanClass().getName() +
                            " with scope @" + dependencyScope.getSimpleName() +
                            " at " + formatInjectionPoint(injectionPoint, bean) +
                            ", but the dependency does not implement java.io.Serializable. " +
                            "Non-normal-scoped beans injected into passivation-capable beans must be Serializable."
                    );
                    allValid = false;
                }
            }
        }

        return allValid;
    }

    private boolean isBuiltInPassivationCapableDependency(Type requiredType) {
        Class<?> rawType = RawTypeExtractor.getRawType(requiredType);
        if (rawType == null) {
            return false;
        }

        String rawTypeName = rawType.getName();
        return "jakarta.enterprise.inject.Instance".equals(rawTypeName) ||
                "javax.enterprise.inject.Instance".equals(rawTypeName) ||
                "jakarta.inject.Provider".equals(rawTypeName) ||
                "javax.inject.Provider".equals(rawTypeName) ||
                "jakarta.enterprise.event.Event".equals(rawTypeName) ||
                "javax.enterprise.event.Event".equals(rawTypeName) ||
                "jakarta.enterprise.inject.spi.Bean".equals(rawTypeName) ||
                "javax.enterprise.inject.spi.Bean".equals(rawTypeName) ||
                "jakarta.enterprise.inject.spi.Decorator".equals(rawTypeName) ||
                "javax.enterprise.inject.spi.Decorator".equals(rawTypeName) ||
                "jakarta.enterprise.inject.spi.Interceptor".equals(rawTypeName) ||
                "javax.enterprise.inject.spi.Interceptor".equals(rawTypeName) ||
                "jakarta.enterprise.inject.spi.InjectionPoint".equals(rawTypeName) ||
                "javax.enterprise.inject.spi.InjectionPoint".equals(rawTypeName) ||
                "jakarta.enterprise.inject.spi.BeanManager".equals(rawTypeName) ||
                "javax.enterprise.inject.spi.BeanManager".equals(rawTypeName);
    }

    private void validatePassivatingProducerDeclaration(Bean<?> bean) {
        if (!(bean instanceof ProducerBean)) {
            return;
        }

        ProducerBean<?> producerBean = (ProducerBean<?>) bean;
        Class<? extends Annotation> declaredPassivatingScope = resolvePassivatingScopeDeclaredOnProducerMember(producerBean);
        if (declaredPassivatingScope == null) {
            return;
        }

        Method producerMethod = producerBean.getProducerMethod();
        if (producerMethod != null) {
            Class<?> returnType = producerMethod.getReturnType();
            if (returnType.isPrimitive()) {
                return;
            }
            if (Modifier.isFinal(returnType.getModifiers()) &&
                    !java.io.Serializable.class.isAssignableFrom(returnType)) {
                knowledgeBase.addError(
                        "Producer method " + producerMethod.getName() + " of class " +
                                producerBean.getDeclaringClass().getName() +
                                " declares passivating scope @" + declaredPassivatingScope.getSimpleName() +
                                " but has final non-serializable return type " + returnType.getName()
                );
                return;
            }
            return;
        }

        Field producerField = producerBean.getProducerField();
        if (producerField != null) {
            Class<?> fieldType = producerField.getType();
            if (fieldType.isPrimitive()) {
                return;
            }
            if (Modifier.isFinal(fieldType.getModifiers()) &&
                    !java.io.Serializable.class.isAssignableFrom(fieldType)) {
                knowledgeBase.addError(
                        "Producer field " + producerField.getName() + " of class " +
                                producerBean.getDeclaringClass().getName() +
                                " declares passivating scope @" + declaredPassivatingScope.getSimpleName() +
                                " but has final non-serializable type " + fieldType.getName()
                );
            }
        }
    }

    private Class<? extends Annotation> resolvePassivatingScopeDeclaredOnProducerMember(ProducerBean<?> producerBean) {
        Method producerMethod = producerBean.getProducerMethod();
        if (producerMethod != null) {
            for (Annotation annotation : producerMethod.getAnnotations()) {
                if (isPassivationCapableScope(annotation.annotationType())) {
                    return annotation.annotationType();
                }
            }
        }

        Field producerField = producerBean.getProducerField();
        if (producerField != null) {
            for (Annotation annotation : producerField.getAnnotations()) {
                if (isPassivationCapableScope(annotation.annotationType())) {
                    return annotation.annotationType();
                }
            }
        }
        return null;
    }

    private void validateInterceptorsAndDecoratorsPassivation(Bean<?> bean, Collection<Bean<?>> validBeans) {
        Class<?> beanClass = bean.getBeanClass();

        Set<InterceptorInfo> boundInterceptors = new LinkedHashSet<>();
        Set<Annotation> classBindings = extractInterceptorBindingAnnotations(beanClass.getAnnotations());
        Set<Class<?>> legacyInterceptorClasses = new LinkedHashSet<>(extractLegacyInterceptorClasses(beanClass.getAnnotations()));
        if (!classBindings.isEmpty()) {
            boundInterceptors.addAll(knowledgeBase.getInterceptorsByBindings(classBindings));
        }
        for (Method method : beanClass.getDeclaredMethods()) {
            int modifiers = method.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isPrivate(modifiers)) {
                continue;
            }
            Set<Annotation> effectiveBindings = new HashSet<>(classBindings);
            effectiveBindings.addAll(extractInterceptorBindingAnnotations(method.getAnnotations()));
            legacyInterceptorClasses.addAll(extractLegacyInterceptorClasses(method.getAnnotations()));
            if (!effectiveBindings.isEmpty()) {
                boundInterceptors.addAll(knowledgeBase.getInterceptorsByBindings(effectiveBindings));
            }
        }

        for (InterceptorInfo interceptorInfo : boundInterceptors) {
            Class<?> interceptorClass = interceptorInfo.getInterceptorClass();
            if (!java.io.Serializable.class.isAssignableFrom(interceptorClass)) {
                knowledgeBase.addError(
                        "Bean " + beanClass.getName() + " has passivation-capable scope @" +
                                bean.getScope().getSimpleName() +
                                " and bound interceptor " + interceptorClass.getName() +
                                " which is not Serializable"
                );
            }
            Bean<?> interceptorBean = findBeanByClass(validBeans, interceptorClass);
            if (interceptorBean != null) {
                validateDependenciesPassivationCapable(interceptorBean, new HashSet<>());
            }
            validateDeclaredInjectionMembersPassivation(interceptorClass, bean);
        }

        for (Class<?> interceptorClass : legacyInterceptorClasses) {
            if (interceptorClass == null) {
                continue;
            }
            if (!java.io.Serializable.class.isAssignableFrom(interceptorClass)) {
                knowledgeBase.addError(
                        "Bean " + beanClass.getName() + " has passivation-capable scope @" +
                                bean.getScope().getSimpleName() +
                                " and legacy interceptor " + interceptorClass.getName() +
                                " which is not Serializable"
                );
            }
            Bean<?> interceptorBean = findBeanByClass(validBeans, interceptorClass);
            if (interceptorBean != null) {
                validateDependenciesPassivationCapable(interceptorBean, new HashSet<>());
            }
            validateDeclaredInjectionMembersPassivation(interceptorClass, bean);
        }

        for (DecoratorInfo decoratorInfo : knowledgeBase.getDecoratorInfos()) {
            if (!decoratesBean(decoratorInfo, bean)) {
                continue;
            }
            Class<?> decoratorClass = decoratorInfo.getDecoratorClass();
            if (!java.io.Serializable.class.isAssignableFrom(decoratorClass)) {
                knowledgeBase.addError(
                        "Bean " + beanClass.getName() + " has passivation-capable scope @" +
                                bean.getScope().getSimpleName() +
                                " and matching decorator " + decoratorClass.getName() +
                                " which is not Serializable"
                );
            }
            Bean<?> decoratorBean = findBeanByClass(validBeans, decoratorClass);
            if (decoratorBean != null) {
                validateDependenciesPassivationCapable(decoratorBean, new HashSet<>());
            }
            validateDeclaredInjectionMembersPassivation(decoratorClass, bean);
        }
    }

    private boolean decoratesBean(DecoratorInfo decoratorInfo, Bean<?> bean) {
        for (Type beanType : bean.getTypes()) {
            if (decoratorInfo.canDecorate(beanType)) {
                return true;
            }
        }
        return false;
    }

    private Bean<?> findBeanByClass(Collection<Bean<?>> validBeans, Class<?> beanClass) {
        for (Bean<?> candidate : validBeans) {
            if (candidate != null && beanClass.equals(candidate.getBeanClass())) {
                return candidate;
            }
        }
        return null;
    }

    private List<Class<?>> extractLegacyInterceptorClasses(Annotation[] annotations) {
        if (annotations == null) {
            return Collections.emptyList();
        }
        for (Annotation annotation : annotations) {
            if (annotation == null) {
                continue;
            }
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (annotationType == null) {
                continue;
            }
            if (!INTERCEPTORS.matches(annotationType)) {
                continue;
            }
            try {
                Method valueMethod = annotationType.getMethod("value");
                Object raw = valueMethod.invoke(annotation);
                if (raw instanceof Class[]) {
                    return Arrays.asList((Class<?>[]) raw);
                }
            } catch (Exception ignored) {
                return Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }

    private void validateDeclaredInjectionMembersPassivation(Class<?> declaringClass,
                                                             Bean<?> owningPassivatingBean) {
        for (Field field : declaringClass.getDeclaredFields()) {
            if (!hasInjectAnnotation(field)) {
                continue;
            }
            if (Modifier.isTransient(field.getModifiers())) {
                continue;
            }
            validateResolvedDependencyPassivation(
                    field.getGenericType(),
                    extractInjectionQualifiers(field.getAnnotations()),
                    field.getName(),
                    owningPassivatingBean
            );
        }

        for (Constructor<?> constructor : declaringClass.getDeclaredConstructors()) {
            if (!hasInjectAnnotation(constructor)) {
                continue;
            }
            for (Parameter parameter : constructor.getParameters()) {
                if (hasTransientReference(parameter.getAnnotations())) {
                    continue;
                }
                validateResolvedDependencyPassivation(
                        parameter.getParameterizedType(),
                        extractInjectionQualifiers(parameter.getAnnotations()),
                        constructor.getName() + "(param)",
                        owningPassivatingBean
                );
            }
        }

        for (Method method : declaringClass.getDeclaredMethods()) {
            if (!hasInjectAnnotation(method)) {
                continue;
            }
            for (Parameter parameter : method.getParameters()) {
                if (hasTransientReference(parameter.getAnnotations())) {
                    continue;
                }
                validateResolvedDependencyPassivation(
                        parameter.getParameterizedType(),
                        extractInjectionQualifiers(parameter.getAnnotations()),
                        method.getName() + "(param)",
                        owningPassivatingBean
                );
            }
        }

    }

    private void validateResolvedDependencyPassivation(Type requiredType,
                                                       Set<Annotation> qualifiers,
                                                       String location,
                                                       Bean<?> owningPassivatingBean) {
        if (isInstanceOrProvider(requiredType) || isInterceptionFactory(requiredType) ||
                isBuiltInPassivationCapableDependency(requiredType)) {
            return;
        }

        Set<Bean<?>> candidates = findMatchingBeans(requiredType, qualifiers);
        if (candidates.isEmpty()) {
            knowledgeBase.addError(
                    "Bean " + owningPassivatingBean.getBeanClass().getName() +
                            " has passivation-capable scope @" + owningPassivatingBean.getScope().getSimpleName() +
                            " and declares unsatisfied dependency at " + location +
                            " for type " + requiredType.getTypeName() +
                            " with qualifiers " + qualifiers);
            return;
        }

        Bean<?> resolvedBean;
        if (candidates.size() > 1) {
            Optional<Bean<?>> preferred = resolveByAlternativePrecedence(candidates);
            if (!preferred.isPresent()) {
                return;
            }
            resolvedBean = preferred.get();
        } else {
            resolvedBean = candidates.iterator().next();
        }

        Class<? extends Annotation> dependencyScope = resolvedBean.getScope();
        if (isNormalScope(dependencyScope)) {
            return;
        }

        if (isDependentScope(dependencyScope)) {
            if (!java.io.Serializable.class.isAssignableFrom(resolvedBean.getBeanClass())) {
                knowledgeBase.addError(
                        "Bean " + owningPassivatingBean.getBeanClass().getName() +
                                " has passivation-capable scope @" + owningPassivatingBean.getScope().getSimpleName() +
                                " and declares dependency at " + location +
                                " resolving to @Dependent bean " + resolvedBean.getBeanClass().getName() +
                                " which is not Serializable");
                return;
            }
            return;
        }

        if (!java.io.Serializable.class.isAssignableFrom(resolvedBean.getBeanClass())) {
            knowledgeBase.addError(
                    "Bean " + owningPassivatingBean.getBeanClass().getName() +
                            " has passivation-capable scope @" + owningPassivatingBean.getScope().getSimpleName() +
                            " and declares dependency at " + location +
                            " resolving to non-normal-scoped bean " + resolvedBean.getBeanClass().getName() +
                            " which is not Serializable");
        }
    }

    private Set<Annotation> extractInjectionQualifiers(Annotation[] annotations) {
        Set<Annotation> qualifiers = new HashSet<>();
        if (annotations != null) {
            for (Annotation annotation : annotations) {
                if (hasQualifierAnnotation(annotation.annotationType()) ||
                    knowledgeBase.isRegisteredQualifier(annotation.annotationType())) {
                    qualifiers.add(annotation);
                }
            }
        }
        if (qualifiers.isEmpty()) {
            qualifiers.add(Default.Literal.INSTANCE);
        }
        return qualifiers;
    }

    private boolean hasTransientReference(Annotation[] annotations) {
        if (annotations == null) {
            return false;
        }
        for (Annotation annotation : annotations) {
            if (hasTransientReferenceAnnotation(annotation.annotationType())) {
                return true;
            }
        }
        return false;
    }

    private boolean isPassivationCapableInjectionPointByDeclaration(InjectionPoint injectionPoint) {
        if (injectionPoint == null) {
            return false;
        }

        if (injectionPoint.isTransient()) {
            return true;
        }

        if (injectionPoint.getAnnotated() == null) {
            return false;
        }

        for (Annotation annotation : injectionPoint.getAnnotated().getAnnotations()) {
            if (hasTransientReferenceAnnotation(annotation.annotationType())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if a scope is a normal scope (uses client proxies).
     * Normal scopes: @ApplicationScoped, @SessionScoped, @ConversationScoped, @RequestScoped
     *
     * @param scopeAnnotation the scope annotation
     * @return true if it's a normal scope
     */
    private boolean isNormalScope(Class<? extends Annotation> scopeAnnotation) {
        ScopeMetadata registeredScope = knowledgeBase.getScopeMetadata(scopeAnnotation);
        if (registeredScope != null) {
            return registeredScope.isNormal();
        }

        return hasBuiltInNormalScopeAnnotation(scopeAnnotation) || hasNormalScopeAnnotation(scopeAnnotation);
    }

    /**
     * Checks if a scope is @Dependent.
     *
     * @param scopeAnnotation the scope annotation
     * @return true if it's @Dependent
     */
    private boolean isDependentScope(Class<? extends Annotation> scopeAnnotation) {
        return hasDependentAnnotation(scopeAnnotation);
    }

    /**
     * Checks if a scope annotation represents a passivation-capable scope.
     *
     * <p>In CDI 4.1, only @SessionScoped and @ConversationScoped are passivation-capable.
     *
     * @param scopeAnnotation the scope annotation to check
     * @return true if the scope is passivation-capable
     */
    private boolean isPassivationCapableScope(Class<? extends Annotation> scopeAnnotation) {
        if (scopeAnnotation == null) {
            return false;
        }

        Boolean passivating = getNormalScopePassivatingValue(scopeAnnotation);
        if (passivating != null) {
            return passivating;
        }

        // SessionScoped and ConversationScoped are passivation-capable
        return hasBuiltInPassivatingScopeAnnotation(scopeAnnotation);
    }

    // ============================================
    // Original Validation Methods
    // ============================================

    /**
     * Validates a single injection point can be satisfied.
     *
     * <p>This method performs the core validation logic:
     * <ul>
     *   <li>Checks if Instance&lt;T&gt; or Provider&lt;T&gt; (always satisfiable)</li>
     *   <li>Finds matching beans by type and qualifiers</li>
     *   <li>Reports unsatisfied dependencies (no beans found)</li>
     *   <li>Reports ambiguous dependencies (multiple beans found)</li>
     *   <li>Checks if a resolved bean has validation errors</li>
     *   <li>Enhancement 1: Validates producer methods can satisfy injection points</li>
     * </ul>
     *
     * @param injectionPoint the injection point to validate
     * @param owningBean the bean that declares this injection point
     * @return true if the injection point can be satisfied, false otherwise
     */
    private boolean validateInjectionPoint(InjectionPoint injectionPoint, Bean<?> owningBean) {
        if (owningBean instanceof SyntheticBean &&
                injectionPoint != null &&
                injectionPoint.getMember() == null &&
                injectionPoint.getAnnotated() == null) {
            return true;
        }

        // Ensure InjectionPoint#getMember participates in the validation flow.
        // Some custom InjectionPoint implementations rely on this callback for metadata checks.
        try {
            if (injectionPoint != null) {
                injectionPoint.getMember();
            }
        } catch (Exception ignored) {
        }

        Type requiredType = injectionPoint.getType();
        Set<Annotation> qualifiers = injectionPoint.getQualifiers();

        if (isRawProgrammaticLookupType(requiredType)) {
            Class<?> rawType = RawTypeExtractor.getRawType(requiredType);
            knowledgeBase.addDefinitionError(
                    formatInjectionPoint(injectionPoint, owningBean) +
                    ": injection point of raw type " + rawType.getSimpleName() + " is not allowed"
            );
            return false;
        }

        // Special handling for Event<T>, Instance<T>, Provider<T> and InterceptionFactory<T>.
        // These are built-in programmatic constructs resolved lazily at runtime.
        if (isEventType(requiredType) || isInstanceOrProvider(requiredType) || isInterceptionFactory(requiredType)) {
            return true; // Always valid - resolved at runtime
        }

        // EventMetadata is only valid as an observer method parameter.
        if (isEventMetadataType(requiredType)) {
            if (isObserverMethodParameter(injectionPoint)) {
                return true;
            }
            knowledgeBase.addDefinitionError(
                    formatInjectionPoint(injectionPoint, owningBean) +
                    ": EventMetadata may only be injected as a parameter of an observer method"
            );
            return false;
        }

        // Bean metadata built-ins are validated via definition rules and resolved at runtime.
        if (isBeanMetadataType(requiredType) || isInterceptorMetadataType(requiredType)) {
            return true;
        }

        // Find all beans that match the required type
        Set<Bean<?>> candidates = findMatchingBeans(requiredType, qualifiers);

        // @Delegate injection points are resolved against decorated beans, not decorator beans.
        // Excluding decorators here avoids false ambiguity when the decorator type is assignable
        // to its own delegate type.
        if (injectionPoint.isDelegate()) {
            Set<Bean<?>> delegateCandidates = new LinkedHashSet<>();
            for (Bean<?> candidate : candidates) {
                if (candidate instanceof Decorator<?>) {
                    continue;
                }
                delegateCandidates.add(candidate);
            }
            candidates = delegateCandidates;
        }

        // Check for unsatisfied dependency
        if (candidates.isEmpty()) {
            knowledgeBase.addInjectionError(
                formatInjectionPoint(injectionPoint, owningBean) +
                ": unsatisfied dependency - no bean found for type " +
                formatType(requiredType) + " with qualifiers " + formatQualifiers(qualifiers)
            );
            return false;
        }

        // Check for ambiguous dependency (apply CDI alternative precedence rules)
        if (candidates.size() > 1) {
            Optional<Bean<?>> preferred = resolveByAlternativePrecedence(candidates);
            if (preferred.isPresent()) {
                candidates = Collections.singleton(preferred.get());
            } else {
                String candidateList = candidates.stream()
                    .map(b -> b.getBeanClass().getName())
                    .collect(Collectors.joining(", "));

                knowledgeBase.addInjectionError(
                    formatInjectionPoint(injectionPoint, owningBean) +
                    ": ambiguous dependency - multiple beans found for type " +
                    formatType(requiredType) + ": [" + candidateList + "]"
                );
                return false;
            }
        }

        // Single candidate found - check if it has validation errors
        Bean<?> resolvedBean = candidates.iterator().next();
        if (resolvedBean instanceof BeanImpl && ((BeanImpl<?>) resolvedBean).hasValidationErrors()) {
            knowledgeBase.addError(
                formatInjectionPoint(injectionPoint, owningBean) +
                ": cannot resolve dependency - resolved bean " +
                resolvedBean.getBeanClass().getName() + " has validation errors"
            );
            return false;
        }

        return validateProxyableBeanTypesIfRequired(injectionPoint, owningBean, resolvedBean);
        // Injection point is valid
    }

    private boolean isBeanMetadataType(Type requiredType) {
        if (!(requiredType instanceof ParameterizedType)) {
            return false;
        }
        Type rawType = ((ParameterizedType) requiredType).getRawType();
        return rawType instanceof Class && Bean.class.equals(rawType);
    }

    private boolean isEventType(Type requiredType) {
        if (!(requiredType instanceof ParameterizedType)) {
            return false;
        }
        Type rawType = ((ParameterizedType) requiredType).getRawType();
        return rawType instanceof Class && jakarta.enterprise.event.Event.class.equals(rawType);
    }

    private boolean isInterceptorMetadataType(Type requiredType) {
        if (!(requiredType instanceof ParameterizedType)) {
            return false;
        }
        Type rawType = ((ParameterizedType) requiredType).getRawType();
        return rawType instanceof Class && Interceptor.class.equals(rawType);
    }

    private boolean isEventMetadataType(Type requiredType) {
        return requiredType instanceof Class &&
                jakarta.enterprise.inject.spi.EventMetadata.class.equals(requiredType);
    }

    private boolean isObserverMethodParameter(InjectionPoint injectionPoint) {
        if (injectionPoint == null || !(injectionPoint.getAnnotated() instanceof AnnotatedParameter)) {
            return false;
        }
        if (!(injectionPoint.getMember() instanceof Method)) {
            return false;
        }
        Method method = (Method) injectionPoint.getMember();
        Class<?> beanClass = injectionPoint.getBean() != null ? injectionPoint.getBean().getBeanClass() : null;
        AnnotatedType<?> override = beanClass != null ? knowledgeBase.getAnnotatedTypeOverride(beanClass) : null;
        if (override == null) {
            for (Parameter parameter : method.getParameters()) {
                if (hasObservesAnnotation(parameter) ||
                    hasObservesAsyncAnnotation(parameter)) {
                    return true;
                }
            }
            return false;
        }

        AnnotatedMethod<?> annotatedMethod = AnnotatedMetadataHelper.findAnnotatedMethod(override, method);
        if (annotatedMethod == null) {
            return false;
        }
        for (AnnotatedParameter<?> parameter : annotatedMethod.getParameters()) {
            Annotation[] annotations = parameter.getAnnotations().toArray(new Annotation[0]);
            if (hasObservesAnnotationIn(annotations) || hasObservesAsyncAnnotationIn(annotations)) {
                return true;
            }
        }
        return false;
    }

    /**
     * CDI 4.1 §3.10 - unproxyable bean types:
     * if an injection point resolves to a bean that requires a client proxy (normal scope)
     * or has bound interceptors, every bean type of that bean must be proxyable.
     */
    private boolean validateProxyableBeanTypesIfRequired(InjectionPoint injectionPoint,
                                                         Bean<?> owningBean,
                                                         Bean<?> resolvedBean) {
        if (!requiresProxyableBeanTypes(resolvedBean)) {
            return true;
        }

        List<String> unproxyableTypes = findUnproxyableBeanTypes(resolvedBean.getTypes(), resolvedBean);
        if (unproxyableTypes.isEmpty()) {
            return true;
        }

        knowledgeBase.addError(
                formatInjectionPoint(injectionPoint, owningBean) +
                ": resolved bean " + resolvedBean.getBeanClass().getName() +
                " requires proxying but declares unproxyable bean type(s): " +
                String.join(", ", unproxyableTypes)
        );
        return false;
    }

    private boolean requiresProxyableBeanTypes(Bean<?> bean) {
        if (isNormalScope(bean.getScope())) {
            return true;
        }

        return hasBoundInterceptor(bean) || hasBoundDecorator(bean);
    }

    private boolean hasBoundInterceptor(Bean<?> bean) {
        if (!(bean instanceof BeanImpl)) {
            return false;
        }

        Class<?> beanClass = bean.getBeanClass();
        Set<Annotation> classBindings = extractInterceptorBindingAnnotations(beanClass.getAnnotations());
        if (!classBindings.isEmpty() && !knowledgeBase.getInterceptorsByBindings(classBindings).isEmpty()) {
            return true;
        }

        for (Method method : beanClass.getDeclaredMethods()) {
            int modifiers = method.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isPrivate(modifiers)) {
                continue;
            }

            Set<Annotation> effectiveBindings = new HashSet<>(classBindings);
            effectiveBindings.addAll(extractInterceptorBindingAnnotations(method.getAnnotations()));
            if (!effectiveBindings.isEmpty() &&
                    !knowledgeBase.getInterceptorsByBindings(effectiveBindings).isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private Set<Annotation> extractInterceptorBindingAnnotations(Annotation[] annotations) {
        Set<Annotation> bindings = new HashSet<>();
        if (annotations == null) {
            return bindings;
        }
        for (Annotation annotation : annotations) {
            collectInterceptorBindings(annotation, bindings, new HashSet<>());
        }
        return bindings;
    }

    private void collectInterceptorBindings(Annotation annotation,
                                            Set<Annotation> bindings,
                                            Set<Class<? extends Annotation>> visited) {
        if (annotation == null) {
            return;
        }
        Class<? extends Annotation> annotationType = annotation.annotationType();
        if (annotationType == null || !visited.add(annotationType)) {
            return;
        }

        try {
            if (hasInterceptorBindingAnnotation(annotationType) ||
                    knowledgeBase.isRegisteredInterceptorBinding(annotationType)) {
                bindings.add(annotation);
            }

            if (hasStereotypeAnnotation(annotationType) ||
                    knowledgeBase.isRegisteredStereotype(annotationType)) {
                for (Annotation meta : annotationType.getAnnotations()) {
                    collectInterceptorBindings(meta, bindings, visited);
                }
            } else if (hasInterceptorBindingAnnotation(annotationType) ||
                    knowledgeBase.isRegisteredInterceptorBinding(annotationType)) {
                // Include transitive interceptor bindings declared as meta-annotations.
                for (Annotation meta : annotationType.getAnnotations()) {
                    collectInterceptorBindings(meta, bindings, visited);
                }
            }
        } finally {
            visited.remove(annotationType);
        }
    }

    private boolean hasBoundDecorator(Bean<?> bean) {
        if (bean == null) {
            return false;
        }
        Class<?> beanClass = bean.getBeanClass();
        if (beanClass == null || !(bean instanceof BeanImpl)) {
            return false;
        }

        Set<Type> beanTypes = bean.getTypes();
        if (beanTypes == null || beanTypes.isEmpty()) {
            return false;
        }

        Set<Annotation> beanQualifiers = bean.getQualifiers() == null
                ? Collections.emptySet()
                : bean.getQualifiers();

        if (hasMatchingDecoratorInfo(beanTypes, beanQualifiers)) {
            return true;
        }
        return hasMatchingDecoratorBean(beanTypes, beanQualifiers);
    }

    private boolean hasMatchingDecoratorInfo(Set<Type> beanTypes, Set<Annotation> beanQualifiers) {
        for (DecoratorInfo decoratorInfo : knowledgeBase.getDecoratorInfos()) {
            if (!isDecoratorInfoEnabled(decoratorInfo)) {
                continue;
            }
            if (!decoratorMatchesTypes(decoratorInfo, beanTypes)) {
                continue;
            }
            Set<Annotation> delegateQualifiers = decoratorInfo.getDelegateInjectionPoint().getQualifiers();
            if (notQualifierSubset(delegateQualifiers, beanQualifiers)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private boolean hasMatchingDecoratorBean(Set<Type> beanTypes, Set<Annotation> beanQualifiers) {
        for (Bean<?> candidate : knowledgeBase.getBeans()) {
            if (!(candidate instanceof Decorator<?>)) {
                continue;
            }
            Decorator<?> decorator = (Decorator<?>) candidate;
            if (!isDecoratorBeanEnabled(decorator)) {
                continue;
            }
            if (!decoratorMatchesTypes(decorator.getDecoratedTypes(), beanTypes)) {
                continue;
            }
            Set<Annotation> delegateQualifiers = decorator.getDelegateQualifiers();
            if (notQualifierSubset(delegateQualifiers, beanQualifiers)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private boolean isDecoratorInfoEnabled(DecoratorInfo decoratorInfo) {
        if (decoratorInfo == null) {
            return false;
        }
        return decoratorInfo.getPriority() != Integer.MAX_VALUE
                || knowledgeBase.getDecoratorBeansXmlOrder(decoratorInfo.getDecoratorClass()) >= 0;
    }

    private boolean isDecoratorBeanEnabled(Decorator<?> decorator) {
        if (decorator == null || decorator.getBeanClass() == null) {
            return false;
        }
        Class<?> beanClass = decorator.getBeanClass();
        return getPriorityValue(beanClass) != null || knowledgeBase.getDecoratorBeansXmlOrder(beanClass) >= 0;
    }

    private boolean decoratorMatchesTypes(DecoratorInfo decoratorInfo, Set<Type> beanTypes) {
        return decoratorMatchesTypes(decoratorInfo.getDecoratedTypes(), beanTypes);
    }

    private boolean decoratorMatchesTypes(Set<Type> decoratedTypes, Set<Type> beanTypes) {
        if (decoratedTypes == null || decoratedTypes.isEmpty()) {
            return false;
        }
        for (Type decoratedType : decoratedTypes) {
            for (Type beanType : beanTypes) {
                try {
                    if (typeChecker.isAssignable(decoratedType, beanType)) {
                        return true;
                    }
                } catch (DefinitionException ex) {
                    // Decorator assignability checks during deployment validation must not fail
                    // due to unresolved type variables; treat the pair as non-matching.
                } catch (RuntimeException ex) {
                    // Defensive fallback to keep decorator matching best-effort and non-fatal.
                    // The offending type pair is ignored, and the validator keeps searching.
                }
            }
        }
        return false;
    }

    private boolean notQualifierSubset(Set<Annotation> required, Set<Annotation> available) {
        if (required == null || required.isEmpty()) {
            return false;
        }
        for (Annotation requiredQualifier : required) {
            if (requiredQualifier == null) {
                continue;
            }
            Class<? extends Annotation> requiredType = requiredQualifier.annotationType();
            if (hasAnyAnnotation(requiredType)) {
                continue;
            }
            boolean found = false;
            for (Annotation availableQualifier : available) {
                if (availableQualifier == null) {
                    continue;
                }
                if (requiredType.equals(availableQualifier.annotationType())
                        && AnnotationComparator.equals(requiredQualifier, availableQualifier)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return true;
            }
        }
        return false;
    }

    private List<String> findUnproxyableBeanTypes(Set<Type> beanTypes, Bean<?> bean) {
        List<String> unproxyable = new ArrayList<>();

        for (Type beanType : beanTypes) {
            String reason = unproxyableReason(beanType, bean);
            if (reason != null) {
                unproxyable.add(beanType.getTypeName() + " (" + reason + ")");
            }
        }

        return unproxyable;
    }

    private String unproxyableReason(Type beanType, Bean<?> bean) {
        Class<?> rawType = RawTypeExtractor.getRawType(beanType);
        if (rawType == null) {
            return null;
        }

        if (rawType.equals(Object.class)) {
            return null;
        }

        if (rawType.isPrimitive()) {
            return "primitive type";
        }

        if (rawType.isArray()) {
            return "array type";
        }

        if (isSealed(rawType)) {
            return "sealed class or interface";
        }

        if (rawType.isInterface()) {
            return null;
        }

        if (Modifier.isFinal(rawType.getModifiers())) {
            return "final class";
        }

        if (!hasNonPrivateNoArgConstructor(rawType)) {
            return "missing non-private no-arg constructor";
        }

        Method finalBusinessMethod = findNonStaticFinalNonPrivateMethod(rawType);
        if (finalBusinessMethod != null && !knowledgeBase.shouldIgnoreFinalMethods(bean)) {
            return "has non-static final method with non-private visibility: " + finalBusinessMethod.getName();
        }

        return null;
    }

    private boolean hasNonPrivateNoArgConstructor(Class<?> type) {
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (!Modifier.isPrivate(constructor.getModifiers()) && constructor.getParameterCount() == 0) {
                return true;
            }
        }
        return false;
    }

    private Method findNonStaticFinalNonPrivateMethod(Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            int modifiers = method.getModifiers();
            if (Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers) || Modifier.isPrivate(modifiers)) {
                continue;
            }
            return method;
        }
        return null;
    }

    private boolean isSealed(Class<?> type) {
        try {
            Method isSealedMethod = Class.class.getMethod("isSealed");
            Object value = isSealedMethod.invoke(type);
            return value instanceof Boolean && (Boolean) value;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    /**
     * Resolves candidates using CDI alternative precedence:
     * if enabled alternatives are present, they are preferred over non-alternatives,
     * and the highest-priority alternative wins; equal top priority remains ambiguous.
     */
    private Optional<Bean<?>> resolveByAlternativePrecedence(Collection<Bean<?>> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        if (candidates.size() == 1) {
            return Optional.of(candidates.iterator().next());
        }

        List<Bean<?>> alternatives = candidates.stream()
                .filter(this::isEffectivelyAlternative)
                .collect(Collectors.toList());

        if (alternatives.isEmpty()) {
            return Optional.empty();
        }

        Bean<?> winner = null;
        int winnerPriority = Integer.MIN_VALUE;
        boolean tie = false;

        for (Bean<?> alternative : alternatives) {
            int priority = getAlternativePriority(alternative);
            if (winner == null || priority > winnerPriority) {
                winner = alternative;
                winnerPriority = priority;
                tie = false;
            } else if (priority == winnerPriority) {
                tie = true;
            }
        }

        if (tie || winner == null) {
            return Optional.empty();
        }

        return Optional.of(winner);
    }

    private int getAlternativePriority(Bean<?> bean) {
        Integer applicationOrderPriority = getAfterTypeDiscoveryAlternativePriority(bean);
        if (applicationOrderPriority != null) {
            return applicationOrderPriority;
        }

        if (bean instanceof ProducerBean) {
            ProducerBean<?> producerBean = (ProducerBean<?>) bean;
            Integer memberPriority = extractPriorityFromProducerMember(producerBean);
            if (memberPriority != null) {
                return memberPriority;
            }

            Integer producerPriority = producerBean.getPriority();
            if (producerPriority != null) {
                return producerPriority;
            }

            Integer declaringPriority = extractPriorityFromClass(producerBean.getDeclaringClass());
            if (declaringPriority != null) {
                return declaringPriority;
            }
        }

        if (bean instanceof BeanImpl) {
            Integer beanPriority = ((BeanImpl<?>) bean).getPriority();
            if (beanPriority != null) {
                return beanPriority;
            }
        }

        if (bean instanceof SyntheticBean) {
            Integer syntheticPriority = ((SyntheticBean<?>) bean).getPriority();
            if (syntheticPriority != null) {
                return syntheticPriority;
            }
        }

        Integer classPriority = extractPriorityFromClass(bean.getBeanClass());
        if (classPriority != null) {
            return classPriority;
        }

        return jakarta.interceptor.Interceptor.Priority.APPLICATION;
    }

    private Integer getAfterTypeDiscoveryAlternativePriority(Bean<?> bean) {
        if (bean == null || !knowledgeBase.hasAfterTypeDiscoveryAlternativesCustomized()) {
            return null;
        }
        Class<?> beanClass = bean.getBeanClass();
        if (beanClass == null) {
            return null;
        }
        int applicationOrder = knowledgeBase.getApplicationAlternativeOrder(beanClass);
        if (applicationOrder < 0) {
            return null;
        }
        return Integer.MAX_VALUE - applicationOrder;
    }

    private Integer extractPriorityFromProducerMember(ProducerBean<?> producerBean) {
        if (producerBean.getProducerMethod() != null) {
            return extractPriorityFromAnnotations(producerBean.getProducerMethod().getAnnotations());
        }
        if (producerBean.getProducerField() != null) {
            return extractPriorityFromAnnotations(producerBean.getProducerField().getAnnotations());
        }
        return null;
    }

    private Integer extractPriorityFromClass(Class<?> beanClass) {
        if (beanClass == null) {
            return null;
        }
        return extractPriorityFromAnnotations(beanClass.getAnnotations());
    }

    private Integer extractPriorityFromAnnotations(Annotation[] annotations) {
        if (annotations == null) {
            return null;
        }
        for (Annotation annotation : annotations) {
            if (PRIORITY.matches(annotation.annotationType())) {
                try {
                    Method valueMethod = annotation.annotationType().getMethod("value");
                    Object value = valueMethod.invoke(annotation);
                    if (value instanceof Integer) {
                        return (Integer) value;
                    }
                } catch (ReflectiveOperationException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Checks if the type is Instance&lt;T&gt; or Provider&lt;T&gt;
     * These types can always be satisfied as they provide lazy/programmatic access.
     *
     * @param type the type to check
     * @return true if the type is Instance or Provider
     */
    private boolean isInstanceOrProvider(Type type) {
        Class<?> rawType = RawTypeExtractor.getRawType(type);
        return Instance.class.equals(rawType) || Provider.class.equals(rawType);
    }

    private boolean isRawProgrammaticLookupType(Type type) {
        if (!(type instanceof Class<?>)) {
            return false;
        }
        return Instance.class.equals(type) || jakarta.enterprise.event.Event.class.equals(type);
    }

    private boolean isInterceptionFactory(Type type) {
        Class<?> rawType = RawTypeExtractor.getRawType(type);
        return InterceptionFactory.class.equals(rawType);
    }

    // ============================================
    // Enhancement 1: Producer Method Resolution
    // ============================================

    /**
     * Finds all beans that match the required type and qualifiers.
     *
     * <p>This method searches for beans that can satisfy an injection point by:
     * <ol>
     *   <li>Checking managed beans (regular classes with @Inject, @ApplicationScoped, etc.)</li>
     *   <li>Checking producer fields (fields annotated with @Produces)</li>
     *   <li>Checking producer methods (methods annotated with @Produces)</li>
     * </ol>
     *
     * <p><b>Type Matching:</b> Uses {@link TypeChecker} for proper generic type matching.
     * For example:
     * <pre>
     * // Injection point
     * {@literal @}Inject List&lt;String&gt; items;
     *
     * // Producer method can satisfy this
     * {@literal @}Produces
     * public ArrayList&lt;String&gt; createList() {
     *     return new ArrayList&lt;&gt;();
     * }
     * </pre>
     *
     * <p><b>Qualifier Matching:</b> CDI 4.1 rules apply:
     * <ul>
     *   <li>Injection point with no qualifiers → matches beans with @Default</li>
     *   <li>Injection point with qualifiers → all qualifiers must match</li>
     *   <li>@Any matches all beans</li>
     * </ul>
     *
     * @param requiredType the type being injected
     * @param qualifiers the qualifiers that must match
     * @return set of matching beans (may include producer-backed beans)
     */
    private Set<Bean<?>> findMatchingBeans(Type requiredType, Set<Annotation> qualifiers) {
        LegacyNewQualifierHelper.LegacyNewSelection legacyNewSelection =
                LegacyNewQualifierHelper.extractSelection(requiredType, qualifiers.toArray(new Annotation[0]));
        if (legacyNewSelection != null) {
            if (!legacyCdi10NewEnabled) {
                return Collections.emptySet();
            }
            return findLegacyNewBeans(requiredType, legacyNewSelection);
        }

        Set<Bean<?>> matches = new HashSet<>();

        // Get all valid beans (exclude beans with validation errors)
        // This includes:
        // 1. Regular managed beans (classes with injection points)
        // 2. Producer field beans (fields annotated with @Produces)
        // 3. Producer method beans (methods annotated with @Produces)
        Collection<Bean<?>> validBeans = knowledgeBase.getValidBeans();

        for (Bean<?> bean : validBeans) {
            if (!isBeanEnabledForResolution(bean)) {
                continue;
            }
            // Check if bean's types are compatible with required type
            // Uses TypeChecker for proper generic matching (e.g., List<String> matches ArrayList<String>)
            if (isTypeCompatible(requiredType, bean.getTypes()) &&
                // Check if bean's qualifiers match the required qualifiers
                // Handles @Default, @Any, and custom qualifiers per CDI 4.1 spec
                areQualifiersCompatible(qualifiers, bean.getQualifiers(), bean.getName())) {
                matches.add(bean);
            }
        }

        // Note: Producer methods are already validated by CDI41BeanValidator
        // and registered as beans in KnowledgeBase, so they're included in
        // the validBeans collection above. No special handling is needed here.

        return applySpecializationFiltering(matches);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Set<Bean<?>> findLegacyNewBeans(
            Type requiredType,
            LegacyNewQualifierHelper.LegacyNewSelection selection) {
        Set<Bean<?>> matches = new HashSet<>();
        Class<?> targetClass = selection.getTargetClass();

        for (Bean<?> bean : knowledgeBase.getValidBeans()) {
            if (!isBeanEnabledForResolution(bean)) {
                continue;
            }
            if (!targetClass.equals(bean.getBeanClass())) {
                continue;
            }
            if (!isTypeCompatible(requiredType, bean.getTypes())) {
                continue;
            }
            matches.add(new LegacyNewBeanAdapter(bean));
        }
        return applySpecializationFiltering(matches);
    }

    private boolean isBeanEnabledForResolution(Bean<?> bean) {
        Set<Bean<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        return isBeanEnabledForResolution(bean, visited);
    }

    private boolean isBeanEnabledForResolution(Bean<?> bean, Set<Bean<?>> visited) {
        if (bean == null) {
            return false;
        }
        if (!visited.add(bean)) {
            return false;
        }
        if (bean instanceof ProducerBean) {
            ProducerBean<?> producerBean = (ProducerBean<?>) bean;
            Bean<?> declaringBean = findDeclaringBean(producerBean.getDeclaringClass());
            if (declaringBean == null) {
                return false;
            }
            if (!isBeanEnabledForResolution(declaringBean, visited)) {
                return false;
            }
            return producerBean.isAlternativeEnabled();
        }
        if (bean instanceof SyntheticProducerBeanImpl) {
            Bean<?> originalBean = findOriginalProducerBean(bean);
            if (originalBean instanceof ProducerBean) {
                return isBeanEnabledForResolution(originalBean, visited);
            }
        }
        if (!bean.isAlternative()) {
            return true;
        }
        if (bean instanceof BeanImpl) {
            return ((BeanImpl<?>) bean).isAlternativeEnabled();
        }
        return true;
    }

    private boolean isEffectivelyAlternative(Bean<?> bean) {
        if (bean == null) {
            return false;
        }
        if (bean.isAlternative()) {
            return true;
        }
        if (bean instanceof ProducerBean) {
            ProducerBean<?> producerBean = (ProducerBean<?>) bean;
            Bean<?> declaringBean = findDeclaringBean(producerBean.getDeclaringClass());
            return declaringBean != null && declaringBean.isAlternative();
        }
        if (bean instanceof SyntheticProducerBeanImpl) {
            Bean<?> originalBean = findOriginalProducerBean(bean);
            return isEffectivelyAlternative(originalBean);
        }
        return false;
    }

    private Bean<?> findDeclaringBean(Class<?> declaringClass) {
        if (declaringClass == null) {
            return null;
        }
        for (Bean<?> candidate : knowledgeBase.getValidBeans()) {
            if (candidate instanceof ProducerBean || candidate instanceof SyntheticProducerBeanImpl) {
                continue;
            }
            if (declaringClass.equals(candidate.getBeanClass())) {
                return candidate;
            }
        }
        return null;
    }

    private Bean<?> findOriginalProducerBean(Bean<?> syntheticBean) {
        if (syntheticBean == null) {
            return null;
        }
        for (ProducerBean<?> producerBean : knowledgeBase.getProducerBeans()) {
            if (!Objects.equals(producerBean.getBeanClass(), syntheticBean.getBeanClass())) {
                continue;
            }
            if (!Objects.equals(producerBean.getTypes(), syntheticBean.getTypes())) {
                continue;
            }
            if (!Objects.equals(producerBean.getQualifiers(), syntheticBean.getQualifiers())) {
                continue;
            }
            return producerBean;
        }
        return null;
    }

    /**
     * Checks if the required type is compatible with the bean's types.
     * Uses the existing TypeChecker for proper type hierarchy and generic matching.
     *
     * @param requiredType the type being injected
     * @param beanTypes the types the bean provides
     * @return true if types are compatible
     */
    private boolean isTypeCompatible(Type requiredType, Set<Type> beanTypes) {
        for (Type beanType : beanTypes) {
            try {
                if (!sameRawType(requiredType, beanType)) {
                    continue;
                }
                // Use TypeChecker for proper type matching with generic support
                if (typeChecker.isLookupTypeAssignable(requiredType, beanType)) {
                    return true;
                }
            } catch (Exception e) {
                // If TypeChecker fails, continue checking other bean types
            }
        }

        return false;
    }

    private boolean sameRawType(Type requiredType, Type beanType) {
        if (requiredType == null || beanType == null) {
            return false;
        }
        if (requiredType instanceof TypeVariable || requiredType instanceof WildcardType) {
            return true;
        }

        Class<?> requiredRaw;
        Class<?> beanRaw;
        try {
            requiredRaw = normalizePrimitiveType(RawTypeExtractor.getRawType(requiredType));
            beanRaw = normalizePrimitiveType(RawTypeExtractor.getRawType(beanType));
        } catch (RuntimeException e) {
            return true;
        }

        if (requiredRaw == null || beanRaw == null) {
            return true;
        }
        return requiredRaw.equals(beanRaw);
    }

    private Class<?> normalizePrimitiveType(Class<?> type) {
        if (type == null || !type.isPrimitive()) {
            return type;
        }
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == boolean.class) return Boolean.class;
        if (type == char.class) return Character.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == void.class) return Void.class;
        return type;
    }

    /**
     * Checks if the required qualifiers are compatible with the bean's qualifiers.
     *
     * <p>CDI rules:
     * <ul>
     *   <li>Injection point with no qualifiers matches bean with @Default</li>
     *   <li>Injection point with qualifiers must match ALL of them on the bean</li>
     *   <li>@Any qualifier matches all beans</li>
     * </ul>
     *
     * @param required the required qualifiers
     * @param provided the bean's qualifiers
     * @return true if qualifiers are compatible
     */
    private boolean areQualifiersCompatible(Set<Annotation> required, Set<Annotation> provided, String beanName) {
        // If no specific qualifiers required, match @Default
        if (required.isEmpty()) {
            return hasDefault(provided);
        }

        // Explicitly requesting only @Any means "any qualifier set is acceptable"
        if (hasOnlyAny(required)) {
            return true;
        }

        // @Default (with optional @Any) matches beans with @Default
        if (hasOnlyDefault(required)) {
            return hasDefault(provided);
        }

        // All required qualifiers must be present in provided
        for (Annotation reqQualifier : required) {
            // @Any is implicit and should not constrain matching when other qualifiers are present
            if (hasAnyAnnotation(reqQualifier.annotationType())) {
                continue;
            }
            if (!isQualifier(reqQualifier)) {
                continue;
            }
            if (hasMatchingQualifier(reqQualifier, provided)) {
                continue;
            }
            if (matchesNamedQualifierByBeanName(reqQualifier, beanName)) {
                continue;
            }
            return false;
        }

        return true;
    }

    /**
     * Checks if a matching qualifier exists in the provided set.
     *
     * @param required the required qualifier
     * @param provided the set of provided qualifiers
     * @return true if a matching qualifier is found
     */
    private boolean hasMatchingQualifier(Annotation required, Set<Annotation> provided) {
        for (Annotation providedQualifier : provided) {
            if (qualifiersMatch(required, providedQualifier)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesNamedQualifierByBeanName(Annotation requiredQualifier, String beanName) {
        if (requiredQualifier == null || !hasNamedAnnotation(requiredQualifier.annotationType())) {
            return false;
        }
        if (beanName == null || beanName.isEmpty()) {
            return false;
        }
        String requiredNamedValue = extractNamedValue(requiredQualifier).trim();
        if (requiredNamedValue.isEmpty()) {
            return false;
        }
        return requiredNamedValue.equals(beanName);
    }

    private String extractNamedValue(Annotation namedQualifier) {
        try {
            Method valueMethod = namedQualifier.annotationType().getMethod("value");
            Object value = valueMethod.invoke(namedQualifier);
            return value == null ? "" : value.toString();
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    /**
     * Checks if two qualifiers match, according to CDI rules.
     *
     * @param a first qualifier
     * @param b second qualifier
     * @return true if qualifiers match
     */
    private boolean qualifiersMatch(Annotation a, Annotation b) {
        // Same type check
        if (!a.annotationType().equals(b.annotationType())) {
            return false;
        }

        // CDI qualifier equality must honor @Nonbinding members.
        return AnnotationComparator.equals(a, b);
    }

    /**
     * Checks if the annotation is a qualifier.
     *
     * @param annotation the annotation to check
     * @return true if it's a qualifier
     */
    private boolean isQualifier(Annotation annotation) {
        return hasQualifierAnnotation(annotation.annotationType()) ||
                knowledgeBase.isRegisteredQualifier(annotation.annotationType());
    }

    /**
     * Checks if @Default is present in the set.
     *
     * @param qualifiers the qualifiers to check
     * @return true if @Default is present
     */
    private boolean hasDefault(Set<Annotation> qualifiers) {
        return qualifiers.stream()
            .anyMatch(q -> hasDefaultAnnotation(q.annotationType()));
    }

    /**
     * Checks if the set contains only @Default (and possibly @Any).
     *
     * @param qualifiers the qualifiers to check
     * @return true if only default qualifiers are present
     */
    private boolean hasOnlyDefault(Set<Annotation> qualifiers) {
        return qualifiers.stream()
            .filter(this::isQualifier)
            .allMatch(q -> hasDefaultAnnotation(q.annotationType()) ||
                          hasAnyAnnotation(q.annotationType()));
    }

    /**
     * Checks if the set contains only @Any.
     */
    private boolean hasOnlyAny(Set<Annotation> qualifiers) {
        boolean hasAtLeastOneQualifier = false;
        for (Annotation qualifier : qualifiers) {
            if (!isQualifier(qualifier)) {
                continue;
            }
            hasAtLeastOneQualifier = true;
            if (!hasAnyAnnotation(qualifier.annotationType())) {
                return false;
            }
        }
        return hasAtLeastOneQualifier;
    }

    /**
     * Formats an injection point for error messages.
     *
     * @param ip the injection point
     * @param owningBean the bean that owns this injection point
     * @return formatted string
     */
    private String formatInjectionPoint(InjectionPoint ip, Bean<?> owningBean) {
        if (ip.getMember() != null) {
            return "Injection point " + ip.getMember().getName() +
                   " in class " + owningBean.getBeanClass().getName();
        }
        return "Injection point in class " + owningBean.getBeanClass().getName();
    }

    /**
     * Formats a type for error messages.
     *
     * @param type the type to format
     * @return formatted string
     */
    private String formatType(Type type) {
        return type.getTypeName();
    }

    private Set<Annotation> qualifierKey(Set<Annotation> qualifiers) {
        return qualifiers.stream()
                .filter(q -> !hasAnyAnnotation(q.annotationType())
                        && !hasDefaultAnnotation(q.annotationType()))
                .collect(Collectors.toSet());
    }

    /**
     * Formats qualifiers for error messages.
     *
     * @param qualifiers the qualifiers to format
     * @return formatted string
     */
    private String formatQualifiers(Set<Annotation> qualifiers) {
        if (qualifiers.isEmpty()) {
            return "[@Default]";
        }
        return qualifiers.stream()
            .map(q -> "@" + q.annotationType().getSimpleName())
            .collect(Collectors.joining(", ", "[", "]"));
    }

    // ============================================
    // Enhancement 5: Observer Method Validation
    // ============================================

    /**
     * Scans all beans for observer methods and validates them.
     *
     * <p><b>CDI 4.1 Observer Method Requirements (Section 10.4):</b>
     * <ul>
     *   <li>Must have exactly one parameter annotated with @Observes or @ObservesAsync</li>
     *   <li>The observed parameter defines the event type</li>
     *   <li>Cannot have both @Observes and @ObservesAsync on the same method</li>
     *   <li>Can have additional injection points as other parameters</li>
     *   <li>May have qualifiers on observed parameter for event filtering</li>
     *   <li>Conditional observers (@Observes(notifyObserver=IF_EXISTS)) only notified if the bean exists</li>
     * </ul>
     *
     * @param validBeans the beans to scan for observer methods
     */
    private void scanAndValidateObserverMethods(Collection<Bean<?>> validBeans) {
        // Validation can execute more than once during startup (for example, before and after BCE @Validation).
        // Observer discovery must remain stable across passes; otherwise the same observer is registered repeatedly.
        if (knowledgeBase.isObserverMethodsDiscovered()) {
            return;
        }

        boolean registerObserverInfos = knowledgeBase.getObserverMethodInfos().isEmpty();
        Set<Bean<?>> specializationFiltered = applySpecializationFiltering(new HashSet<>(validBeans));

        for (Bean<?> bean : specializationFiltered) {
            if (!(bean instanceof BeanImpl)) {
                continue; // Producer beans don't have observer methods
            }

            BeanImpl<?> beanImpl = (BeanImpl<?>) bean;
            Class<?> beanClass = beanImpl.getBeanClass();
            AnnotatedType<?> annotatedTypeOverride = knowledgeBase.getAnnotatedTypeOverride(beanClass);

            // Scan all methods for @Observes and @ObservesAsync
            for (ObserverMethodCandidate candidate : collectObserverCandidateMethods(beanClass, annotatedTypeOverride)) {
                validateObserverMethod(candidate, beanImpl, registerObserverInfos);
            }
        }

        knowledgeBase.setObserverMethodsDiscovered(true);
    }

    /**
     * Validates a single observer method and registers it if valid.
     *
     * @param candidate     the method to validate
     * @param declaringBean the bean that declares this method
     */
    private void validateObserverMethod(ObserverMethodCandidate candidate,
                                        BeanImpl<?> declaringBean,
                                        boolean registerObserverInfo) {
        Method method = candidate.method;
        List<ObserverParameterMetadata> parameters =
                resolveObserverParameterMetadata(method, candidate.annotatedMethod, declaringBean.getBeanClass());

        // Count @Observes and @ObservesAsync parameters
        int observesCount = 0;
        int observesAsyncCount = 0;
        ObserverParameterMetadata observedParameter = null;

        for (ObserverParameterMetadata parameter : parameters) {
            if (hasObservesAnnotationIn(parameter.annotations)) {
                observesCount++;
                observedParameter = parameter;
            }
            if (hasObservesAsyncAnnotationIn(parameter.annotations)) {
                observesAsyncCount++;
                observedParameter = parameter;
            }
        }

        // No observer annotations - not an observer method
        if (observesCount == 0 && observesAsyncCount == 0) {
            return;
        }

        // Validate exactly one observer annotation
        if (observesCount + observesAsyncCount > 1) {
            knowledgeBase.addDefinitionError(
                "Observer method " + method.getName() + " in " + declaringBean.getBeanClass().getName() +
                " must have exactly one parameter with @Observes or @ObservesAsync, found " +
                (observesCount + observesAsyncCount)
            );
            return;
        }

        // Cannot mix @Observes and @ObservesAsync
        if (observesCount > 0 && observesAsyncCount > 0) {
            knowledgeBase.addDefinitionError(
                "Observer method " + method.getName() + " in " + declaringBean.getBeanClass().getName() +
                " cannot have both @Observes and @ObservesAsync"
            );
            return;
        }

        // Extract observer metadata
        boolean async = observesAsyncCount > 0;
        Type eventType = GenericTypeResolver.resolve(
                observedParameter.baseType,
                declaringBean.getBeanClass(),
                method.getDeclaringClass()
        );
        Set<Annotation> qualifiers = extractQualifiers(observedParameter.annotations);

        // Extract reception and transaction phase
        jakarta.enterprise.event.Reception reception;
        jakarta.enterprise.event.TransactionPhase transactionPhase = jakarta.enterprise.event.TransactionPhase.IN_PROGRESS;

        if (async) {
            // Extract from @ObservesAsync
            jakarta.enterprise.event.ObservesAsync observesAsync =
                getObservesAsyncAnnotationFrom(observedParameter.annotations);
            reception = observesAsync.notifyObserver();
        } else {
            // Extract from @Observes
            jakarta.enterprise.event.Observes observes =
                getObservesAnnotationFrom(observedParameter.annotations);
            reception = observes.notifyObserver();
            transactionPhase = observes.during();
        }

        // CDI 4.1: @Dependent beans may not declare conditional observer methods (IF_EXISTS).
        if (reception == jakarta.enterprise.event.Reception.IF_EXISTS) {
            Class<? extends Annotation> scope = declaringBean.getScope();
            if (hasDependentAnnotation(scope)) {
                knowledgeBase.addDefinitionError(
                    "Observer method " + method.getName() + " in " + declaringBean.getBeanClass().getName() +
                    " declares notifyObserver=IF_EXISTS but bean scope is @Dependent"
                );
                return;
            }
        }

        // Additional observer parameters are regular injection points and must be deployment-validated.
        for (ObserverParameterMetadata parameter : parameters) {
            if (parameter == observedParameter) {
                continue;
            }
            Annotation namedQualifier = findNamedQualifier(parameter.annotations);
            if (namedQualifier != null && extractNamedValue(namedQualifier).trim().isEmpty()) {
                knowledgeBase.addDefinitionError(
                        formatObserverParameter(parameter.parameter, method, declaringBean) +
                                ": @Named injection point must declare a non-empty value on non-field injection points");
                return;
            }
            Type parameterType = GenericTypeResolver.resolve(
                    parameter.baseType,
                    declaringBean.getBeanClass(),
                    method.getDeclaringClass()
            );
            InjectionPoint injectionPoint = new InjectionPointImpl<>(
                    parameter.parameter,
                    declaringBean,
                    parameterType,
                    parameter.annotations,
                    parameter.annotatedParameter
            );
            if (!validateInjectionPoint(injectionPoint, declaringBean)) {
                return;
            }
        }

        // Extract observer ordering priority (CDI 4.1 9.5.2):
        // - Declared on observed event parameter via @Priority
        // - Default is Interceptor.Priority.APPLICATION + 500
        // - @Priority on async observed parameter is non-portable
        int priority = jakarta.interceptor.Interceptor.Priority.APPLICATION + 500;
        Integer parameterPriority = getPriorityValueFromAnnotations(observedParameter.annotations);
        if (parameterPriority != null) {
            if (async && !allowNonPortableAsyncObserverEventParameterPriority) {
                throw new NonPortableBehaviourException(
                    "Asynchronous observer method " + method.getName() + " in " +
                    declaringBean.getBeanClass().getName() +
                    " declares @Priority on observed event parameter; this is non-portable behavior"
                );
            }
            priority = parameterPriority;
        } else {
            // Backward-compatible fallback for existing method-level priority declarations.
            Integer methodPriority = getPriorityValue(method);
            if (methodPriority != null) {
                priority = methodPriority;
            }
        }

        if (registerObserverInfo) {
            // Create and register observer method info
            ObserverMethodInfo observerMethodInfo = new ObserverMethodInfo(
                method,
                eventType,
                qualifiers,
                reception,
                transactionPhase,
                async,
                declaringBean,
                priority,
                observedParameter.position
            );
            knowledgeBase.addObserverMethodInfo(observerMethodInfo);
        }
    }

    private String formatObserverParameter(Parameter parameter, Method method, Bean<?> declaringBean) {
        String parameterName = parameter != null ? safeParameterName(parameter) : "<param>";
        return "Parameter " + parameterName + " of " + method.getName() + " of class " +
                (declaringBean != null ? declaringBean.getBeanClass().getName() : method.getDeclaringClass().getName());
    }

    private Annotation findNamedQualifier(Annotation[] annotations) {
        if (annotations == null) {
            return null;
        }
        for (Annotation annotation : annotations) {
            if (annotation != null && hasNamedAnnotation(annotation.annotationType())) {
                return annotation;
            }
        }
        return null;
    }

    private String safeParameterName(Parameter parameter) {
        if (parameter == null) {
            return "<param>";
        }
        String name = parameter.getName();
        if (name == null || name.trim().isEmpty()) {
            return "<param>";
        }
        return name;
    }

    /**
     * Extracts qualifiers from a parameter.
     * Qualifiers are annotations annotated with @Qualifier.
     */
    private Set<Annotation> extractQualifiers(Annotation[] annotations) {
        Set<Annotation> qualifiers = new HashSet<>();
        if (annotations == null) {
            return qualifiers;
        }

        // Handles standard qualifiers and repeatable qualifier containers.
        qualifiers.addAll(QualifiersHelper
                .extractQualifierAnnotations(annotations));

        // Also honor dynamically registered qualifiers from extensions.
        for (Annotation annotation : annotations) {
            if (annotation == null) {
                continue;
            }
            if (knowledgeBase.isRegisteredQualifier(annotation.annotationType())) {
                qualifiers.add(annotation);
                continue;
            }
            Collections.addAll(qualifiers, extractRegisteredQualifierAnnotationsFromContainer(annotation));
        }
        return qualifiers;
    }

    private Annotation[] extractRegisteredQualifierAnnotationsFromContainer(Annotation containerAnnotation) {
        try {
            Method valueMethod = containerAnnotation.annotationType().getMethod("value");
            Class<?> returnType = valueMethod.getReturnType();
            if (!returnType.isArray()) {
                return new Annotation[0];
            }

            Class<?> componentType = returnType.getComponentType();
            if (componentType == null || !Annotation.class.isAssignableFrom(componentType)) {
                return new Annotation[0];
            }

            @SuppressWarnings("unchecked")
            Class<? extends Annotation> nestedType = (Class<? extends Annotation>) componentType;
            if (!knowledgeBase.isRegisteredQualifier(nestedType)) {
                return new Annotation[0];
            }

            Object value = valueMethod.invoke(containerAnnotation);
            if (value instanceof Annotation[]) {
                return (Annotation[]) value;
            }
        } catch (ReflectiveOperationException ignored) {
            // Not a qualifier container annotation.
        }
        return new Annotation[0];
    }

    /**
     * Collects methods from superclass to subclass and keeps overriding methods from subclasses.
     */
    private List<ObserverMethodCandidate> collectObserverCandidateMethods(Class<?> beanClass,
                                                                          AnnotatedType<?> annotatedTypeOverride) {
        if (annotatedTypeOverride != null) {
            Map<String, ObserverMethodCandidate> bySignature = new LinkedHashMap<>();
            for (AnnotatedMethod<?> annotatedMethod : annotatedTypeOverride.getMethods()) {
                Method method = annotatedMethod.getJavaMember();
                bySignature.put(methodSignature(method), new ObserverMethodCandidate(method, annotatedMethod));
            }
            return new ArrayList<>(bySignature.values());
        }

        List<Class<?>> hierarchy = new ArrayList<>();
        Class<?> current = beanClass;
        while (current != null && !Object.class.equals(current)) {
            hierarchy.add(0, current);
            current = current.getSuperclass();
        }

        Map<String, ObserverMethodCandidate> bySignature = new LinkedHashMap<>();
        for (Class<?> type : hierarchy) {
            for (Method method : type.getDeclaredMethods()) {
                bySignature.put(methodSignature(method), new ObserverMethodCandidate(method, null));
            }
        }
        return new ArrayList<>(bySignature.values());
    }

    private String methodSignature(Method method) {
        StringBuilder builder = new StringBuilder(method.getName()).append("(");
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) {
                builder.append(",");
            }
            builder.append(parameterTypes[i].getName());
        }
        builder.append(")");
        return builder.toString();
    }

    private List<ObserverParameterMetadata> resolveObserverParameterMetadata(Method method,
                                                                             AnnotatedMethod<?> annotatedMethod,
                                                                             Class<?> beanClass) {
        List<ObserverParameterMetadata> result = new ArrayList<>();
        Parameter[] reflectionParameters = method.getParameters();
        AnnotatedType<?> annotatedTypeOverride = knowledgeBase.getAnnotatedTypeOverride(beanClass);

        for (int i = 0; i < reflectionParameters.length; i++) {
            Parameter parameter = reflectionParameters[i];
            AnnotatedParameter<?> annotatedParameter = annotatedMethod != null
                    ? annotatedParameterAt(annotatedMethod, i)
                    : null;
            if (annotatedParameter == null && annotatedTypeOverride != null) {
                annotatedParameter = AnnotatedMetadataHelper.findAnnotatedParameter(annotatedTypeOverride, parameter);
            }

            Type baseType = annotatedParameter != null
                    ? annotatedParameter.getBaseType()
                    : parameter.getParameterizedType();
            Annotation[] annotations = annotatedParameter != null
                    ? annotatedParameter.getAnnotations().toArray(new Annotation[0])
                    : parameter.getAnnotations();
            result.add(new ObserverParameterMetadata(parameter, annotatedParameter, baseType, annotations, i));
        }
        return result;
    }

    private AnnotatedParameter<?> annotatedParameterAt(AnnotatedMethod<?> method, int position) {
        if (method == null) {
            return null;
        }
        for (AnnotatedParameter<?> parameter : method.getParameters()) {
            if (parameter.getPosition() == position) {
                return parameter;
            }
        }
        return null;
    }

    private boolean hasObservesAnnotationIn(Annotation[] annotations) {
        return getObservesAnnotationFrom(annotations) != null;
    }

    private boolean hasObservesAsyncAnnotationIn(Annotation[] annotations) {
        return getObservesAsyncAnnotationFrom(annotations) != null;
    }

    private jakarta.enterprise.event.Observes getObservesAnnotationFrom(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof jakarta.enterprise.event.Observes) {
                return (jakarta.enterprise.event.Observes) annotation;
            }
        }
        return null;
    }

    private jakarta.enterprise.event.ObservesAsync getObservesAsyncAnnotationFrom(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof jakarta.enterprise.event.ObservesAsync) {
                return (jakarta.enterprise.event.ObservesAsync) annotation;
            }
        }
        return null;
    }

    private Integer getPriorityValueFromAnnotations(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation == null) {
                continue;
            }
            if (PRIORITY.matches(annotation.annotationType())) {
                try {
                    Method valueMethod = annotation.annotationType().getMethod("value");
                    Object value = valueMethod.invoke(annotation);
                    if (value instanceof Integer) {
                        return (Integer) value;
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Ignore malformed annotation implementation and treat as absent.
                }
            }
        }
        return null;
    }

    private static final class ObserverMethodCandidate {
        private final Method method;
        private final AnnotatedMethod<?> annotatedMethod;

        private ObserverMethodCandidate(Method method, AnnotatedMethod<?> annotatedMethod) {
            this.method = method;
            this.annotatedMethod = annotatedMethod;
        }
    }

    private static final class ObserverParameterMetadata {
        private final Parameter parameter;
        private final AnnotatedParameter<?> annotatedParameter;
        private final Type baseType;
        private final Annotation[] annotations;
        private final int position;

        private ObserverParameterMetadata(Parameter parameter,
                                          AnnotatedParameter<?> annotatedParameter,
                                          Type baseType,
                                          Annotation[] annotations,
                                          int position) {
            this.parameter = parameter;
            this.annotatedParameter = annotatedParameter;
            this.baseType = baseType;
            this.annotations = annotations == null ? new Annotation[0] : annotations;
            this.position = position;
        }
    }

    /**
     * Basic specialization filtering: if a bean specializes its direct superclass, remove the
     * specialized superclass from candidates.
     */
    private Set<Bean<?>> applySpecializationFiltering(Set<Bean<?>> candidates) {
        if (candidates == null || candidates.size() < 2) {
            return candidates;
        }

        Set<Class<?>> specializedSuperclasses = new HashSet<>();
        for (Bean<?> candidate : candidates) {
            Class<?> beanClass = candidate.getBeanClass();
            if (hasSpecializesAnnotation(beanClass)) {
                specializedSuperclasses.addAll(collectSpecializedSuperclasses(beanClass));
            }
        }

        if (specializedSuperclasses.isEmpty()) {
            return candidates;
        }

        return candidates.stream()
                .filter(candidate -> !specializedSuperclasses.contains(candidate.getBeanClass()))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private Set<Class<?>> collectSpecializedSuperclasses(Class<?> beanClass) {
        Set<Class<?>> out = new HashSet<>();
        if (!hasSpecializesAnnotation(beanClass)) {
            return out;
        }
        Class<?> current = beanClass.getSuperclass();
        while (current != null && !Object.class.equals(current)) {
            out.add(current);
            if (!hasSpecializesAnnotation(current)) {
                break;
            }
            current = current.getSuperclass();
        }
        return out;
    }
}
