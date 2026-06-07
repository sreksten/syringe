package com.threeamigos.common.util.implementations.injection.resolution;

import com.threeamigos.common.util.implementations.injection.annotations.AnyLiteral;
import com.threeamigos.common.util.implementations.injection.events.NoOpObserverSupport;
import com.threeamigos.common.util.implementations.injection.events.ObserverSupport;
import com.threeamigos.common.util.implementations.injection.discovery.NonPortableBehaviourException;
import com.threeamigos.common.util.implementations.injection.decorators.DecoratorSupport;
import com.threeamigos.common.util.implementations.injection.decorators.NoOpDecoratorSupport;
import com.threeamigos.common.util.implementations.injection.knowledgebase.DecoratorInfo;
import com.threeamigos.common.util.implementations.injection.scopes.ContextManager;
import com.threeamigos.common.util.implementations.injection.scopes.ScopeContext;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.injection.spi.SyntheticBean;
import com.threeamigos.common.util.implementations.injection.spi.SyntheticProducerBeanImpl;
import com.threeamigos.common.util.implementations.injection.spi.configured.ConfiguredInjectionPoint;
import com.threeamigos.common.util.implementations.injection.types.RawTypeExtractor;
import com.threeamigos.common.util.implementations.injection.annotations.legacy.LegacyNewSupport;
import com.threeamigos.common.util.implementations.injection.annotations.legacy.NoOpLegacyNewSupport;
import com.threeamigos.common.util.implementations.injection.util.tx.NoOpTransactionServices;
import com.threeamigos.common.util.implementations.injection.util.tx.TransactionServices;
import com.threeamigos.common.util.implementations.injection.util.LifecycleMethodHelper;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Decorator;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.inject.spi.Interceptor;
import jakarta.enterprise.inject.spi.InterceptionFactory;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.inject.Named;
import jakarta.inject.Provider;

import java.lang.annotation.Annotation;
import java.io.Serializable;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsEnum.*;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.*;

/**
 * Resolves dependencies by finding matching beans from the KnowledgeBase.
 * This is the core dependency resolution engine for the CDI container.
 *
 * <p>Special handling for built-in beans:
 * <ul>
 *   <li><b>InjectionPoint</b> - Contextual metadata about the current injection point</li>
 *   <li><b>Instance&lt;T&gt;</b> - Programmatic bean lookup</li>
 *   <li><b>Provider&lt;T&gt;</b> - Lazy dependency resolution</li>
 *   <li><b>Event&lt;T&gt;</b> - Programmatic event firing</li>
 * </ul>
 *
 * @author Stefano Reksten
 */
public class BeanResolver implements DependencyResolver {

    private final KnowledgeBase knowledgeBase;
    private final ContextManager contextManager;
    private final TypeChecker typeChecker;
    private TransactionServices transactionServices;
    private volatile ObserverSupport observerSupport;
    private volatile BeanManagerImpl owningBeanManager;
    private volatile DecoratorSupport decoratorSupport;
    private volatile LegacyNewSupport legacyNewSupport;

    // ThreadLocal stack to pass nested injection point context during resolution
    private final ThreadLocal<Deque<InjectionPoint>> currentInjectionPoint =
            ThreadLocal.withInitial(ArrayDeque::new);

    public BeanResolver(KnowledgeBase knowledgeBase, ContextManager contextManager) {
        this(knowledgeBase, contextManager, new NoOpTransactionServices());
    }

    public BeanResolver(KnowledgeBase knowledgeBase, ContextManager contextManager, TransactionServices transactionServices) {
        this.knowledgeBase = Objects.requireNonNull(knowledgeBase, "knowledgeBase cannot be null");
        this.contextManager = Objects.requireNonNull(contextManager, "contextManager cannot be null");
        this.typeChecker = new TypeChecker();
        this.transactionServices = transactionServices == null ? new NoOpTransactionServices() : transactionServices;
        this.decoratorSupport = new NoOpDecoratorSupport();
        this.legacyNewSupport = new NoOpLegacyNewSupport();
        this.observerSupport = new NoOpObserverSupport();
    }

    public void setOwningBeanManager(BeanManagerImpl beanManager) {
        this.owningBeanManager = beanManager;
    }

    public BeanManagerImpl getOwningBeanManager() {
        return owningBeanManager;
    }

    public void setLegacyNewSupport(LegacyNewSupport legacyNewSupport) {
        this.legacyNewSupport = legacyNewSupport != null
                ? legacyNewSupport
                : new NoOpLegacyNewSupport();
    }

    public void setDecoratorSupport(DecoratorSupport decoratorSupport) {
        this.decoratorSupport = decoratorSupport != null
                ? decoratorSupport
                : new NoOpDecoratorSupport();
    }

    public void setObserverSupport(ObserverSupport observerSupport) {
        this.observerSupport = observerSupport != null
                ? observerSupport
                : new NoOpObserverSupport();
    }

    @Override
    public Object resolve(Type requiredType, Annotation[] qualifiers) {
        Annotation[] effectiveQualifiers = qualifiers != null ? qualifiers : new Annotation[0];

        // Special handling for InjectionPoint built-in bean
        if (requiredType instanceof Class &&
            InjectionPoint.class.equals(requiredType)) {
            // Return the current injection point context
            InjectionPoint ip = resolveContextualInjectionPoint();
            if (ip == null) {
                return null;
            }
            return createInjectionPointWrapper(ip, effectiveQualifiers);
        }

        // Special handling for Bean/Interceptor metadata built-in beans
        if (requiredType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) requiredType;
            Type rawType = parameterizedType.getRawType();
            if (rawType instanceof Class && Decorator.class.equals(rawType)) {
                InjectionPoint ip = getCurrentInjectionPoint();
                return resolveDecoratorMetadata(ip, parameterizedType);
            }

            if (rawType instanceof Class &&
                    (Bean.class.equals(rawType) || Interceptor.class.equals(rawType))) {
                InjectionPoint ip = getCurrentInjectionPoint();
                if (ip == null || ip.getBean() == null) {
                    throw new IllegalStateException("Bean metadata is not available in this context");
                }
                if (Bean.class.equals(rawType) && hasDecoratedQualifier(effectiveQualifiers)) {
                    return resolveDecoratedBeanMetadata(ip, parameterizedType);
                }
                return ip.getBean();
            }
        }

        // Handle Event<T>, Instance<T> and Provider<T> injection
        if (requiredType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) requiredType;
            Class<?> rawType = (Class<?>) pt.getRawType();

            // Built-in InterceptionFactory<T> is only valid for producer method parameters.
            if (InterceptionFactory.class.equals(rawType)) {
                return resolveInterceptionFactory(pt, effectiveQualifiers);
            }

            // Check if it's Event<T>
            if (Event.class.isAssignableFrom(rawType)) {
                Type eventType = pt.getActualTypeArguments()[0];
                Set<Annotation> requiredQualifiers = extractQualifiers(effectiveQualifiers);

                return createEventWrapper(eventType, requiredQualifiers);
            }

            // Check if it's a Provider<T> (which includes Instance<T>)
            if (Provider.class.isAssignableFrom(rawType)) {
                Type actualType = pt.getActualTypeArguments()[0];
                Class<?> actualClass = RawTypeExtractor.getRawType(actualType);
                Set<Annotation> requiredQualifiers = extractQualifiers(effectiveQualifiers);

                return createProviderWrapper(actualClass, new ArrayList<>(requiredQualifiers));
            }
        }

        // Find matching beans for regular dependencies
        Collection<Bean<?>> candidates = findMatchingBeans(requiredType, effectiveQualifiers);

        if (candidates.isEmpty()) {
            String message = buildResolutionMessage(
                "Unsatisfied dependency",
                requiredType,
                effectiveQualifiers
            );
            throw new UnsatisfiedResolutionException(message);
        }

        if (candidates.size() > 1) {
            Optional<Bean<?>> resolvedCandidate = resolveByAlternativePrecedence(candidates);
            if (resolvedCandidate.isPresent()) {
                candidates = Collections.singletonList(resolvedCandidate.get());
            }
        }

        if (candidates.size() > 1) {
            String message = buildResolutionMessage(
                "Ambiguous dependency",
                requiredType,
                effectiveQualifiers
            ) + ". Matching beans: " + candidates.stream()
                .map(this::formatBeanSummary)
                .collect(Collectors.joining(", "));
            throw new AmbiguousResolutionException(message);
        }

        Bean<?> bean = candidates.iterator().next();

        // Get or create an instance from the appropriate scope
        return getInstanceFromScope(bean);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object resolveInterceptionFactory(ParameterizedType requiredType, Annotation[] qualifiers) {
        if (!hasDefaultQualifier(qualifiers)) {
            throw new UnsatisfiedResolutionException(
                    "InterceptionFactory is only available with @Default qualifier");
        }

        InjectionPoint ip = getCurrentInjectionPoint();
        if (ip == null || !(ip.getMember() instanceof Method) || !hasProducesAnnotation((Method) ip.getMember())) {
            throw new DefinitionException(
                    "Injection point of type InterceptionFactory with @Default must be a producer method parameter");
        }

        Type[] args = requiredType.getActualTypeArguments();
        if (args == null || args.length != 1 || !(args[0] instanceof Class)) {
            throw new NonPortableBehaviourException(
                    "Non-portable behavior: InterceptionFactory type parameter must be a Java class");
        }

        Class beanClass = (Class) args[0];
        if (beanClass.isInterface() || beanClass.isAnnotation() || beanClass.isArray() || beanClass.isPrimitive()) {
            throw new NonPortableBehaviourException(
                    "Non-portable behavior: InterceptionFactory type parameter must be a Java class");
        }

        if (owningBeanManager == null) {
            throw new IllegalStateException("BeanManager is not available for InterceptionFactory resolution");
        }
        CreationalContext context = owningBeanManager.createCreationalContext(null);
        return owningBeanManager.createInterceptionFactory(context, beanClass);
    }

    private boolean hasDefaultQualifier(Annotation[] qualifiers) {
        if (qualifiers == null || qualifiers.length == 0) {
            return true;
        }
        for (Annotation annotation : qualifiers) {
            if (annotation != null && hasDefaultAnnotation(annotation.annotationType())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object resolveDeclaringBeanInstance(Class<?> declaringClass) {
        // Find the managed bean for the declaring class.
        // ProducerBean#getBeanClass() returns the declaring class too, but using that here
        // would recurse infinitely when the producer tries to get its declaring instance.
        for (Bean<?> bean : knowledgeBase.getValidBeans()) {
            if (bean instanceof ProducerBean) {
                continue;
            }
            if (bean.getBeanClass().equals(declaringClass)) {
                return getInstanceFromScope(bean);
            }
        }

        throw new RuntimeException(
            "No bean found for declaring class: " + declaringClass.getName()
        );
    }

    /**
     * Finds all beans matching the required type and qualifiers.
     */
    private Collection<Bean<?>> findMatchingBeans(Type requiredType, Annotation[] qualifiers) {
        LegacyNewSupport.LegacyNewSelection legacyNewSelection =
                legacyNewSupport.resolveSelection(requiredType, qualifiers);
        if (legacyNewSelection != null) {
            if (!legacyNewSupport.isEnabled()) {
                return Collections.emptyList();
            }
            return findLegacyNewBeans(requiredType, legacyNewSelection);
        }

        List<Bean<?>> matches = new ArrayList<>();

        // Extract qualifier annotations (ignore non-qualifiers)
        Set<Annotation> requiredQualifiers = extractQualifiers(qualifiers);

        // Search through all valid beans
        for (Bean<?> bean : knowledgeBase.getValidBeans()) {
            if (!isBeanEnabledForResolution(bean)) {
                continue;
            }

            // Skip beans with validation errors
            if (bean instanceof BeanImpl && ((BeanImpl<?>) bean).hasValidationErrors()) {
                continue;
            }
            if (bean instanceof ProducerBean && ((ProducerBean<?>) bean).hasValidationErrors()) {
                continue;
            }

            // Skip vetoed beans - beans vetoed by extensions during ProcessAnnotatedType
            if (bean instanceof BeanImpl && ((BeanImpl<?>) bean).isVetoed()) {
                continue;
            }
            if (bean instanceof ProducerBean && ((ProducerBean<?>) bean).isVetoed()) {
                continue;
            }

            // Check type match
            boolean typeMatches = false;
            for (Type beanType : bean.getTypes()) {
                if (notSameRawType(requiredType, beanType)) {
                    continue;
                }
                if (typeChecker.isLookupTypeAssignable(requiredType, beanType)) {
                    typeMatches = true;
                    break;
                }
            }

            if (!typeMatches) {
                continue;
            }

            // Check qualifier match
            if (qualifiersMatchIncludingBeanName(requiredQualifiers, bean)) {
                if (isNotBeanClassAccessibleFromCurrentInjectionPoint(bean)) {
                    continue;
                }
                matches.add(bean);
            }
        }

        return applySpecializationFiltering(matches);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Collection<Bean<?>> findLegacyNewBeans(
            Type requiredType,
            LegacyNewSupport.LegacyNewSelection selection) {
        List<Bean<?>> matches = new ArrayList<>();
        Class<?> targetClass = selection.getTargetClass();

        for (Bean<?> bean : knowledgeBase.getValidBeans()) {
            if (!isBeanEnabledForResolution(bean)) {
                continue;
            }
            if (bean instanceof BeanImpl && ((BeanImpl<?>) bean).hasValidationErrors()) {
                continue;
            }
            if (bean instanceof ProducerBean && ((ProducerBean<?>) bean).hasValidationErrors()) {
                continue;
            }
            if (bean instanceof BeanImpl && ((BeanImpl<?>) bean).isVetoed()) {
                continue;
            }
            if (bean instanceof ProducerBean && ((ProducerBean<?>) bean).isVetoed()) {
                continue;
            }
            if (!targetClass.equals(bean.getBeanClass())) {
                continue;
            }

            boolean typeMatches = false;
            for (Type beanType : bean.getTypes()) {
                if (notSameRawType(requiredType, beanType)) {
                    continue;
                }
                if (typeChecker.isLookupTypeAssignable(requiredType, beanType)) {
                    typeMatches = true;
                    break;
                }
            }
            if (!typeMatches) {
                continue;
            }
            if (isNotBeanClassAccessibleFromCurrentInjectionPoint(bean)) {
                continue;
            }

            matches.add(legacyNewSupport.adaptLegacyNewBean((Bean) bean));
        }

        return applySpecializationFiltering(matches);
    }

    private boolean qualifiersMatchIncludingBeanName(Set<Annotation> requiredQualifiers, Bean<?> bean) {
        Set<Annotation> beanQualifiers = bean.getQualifiers();
        if (qualifiersMatch(requiredQualifiers, beanQualifiers)) {
            return true;
        }

        String requiredNamed = extractNonEmptyRequiredNamedValue(requiredQualifiers);
        if (requiredNamed == null) {
            return false;
        }

        String beanName = bean.getName();
        if (beanName == null || beanName.isEmpty() || !beanName.equals(requiredNamed)) {
            return false;
        }

        Set<Annotation> requiredWithoutNamed = removeNamedQualifiers(requiredQualifiers);
        return qualifiersMatch(requiredWithoutNamed, beanQualifiers);
    }

    private String extractNonEmptyRequiredNamedValue(Set<Annotation> requiredQualifiers) {
        if (requiredQualifiers == null) {
            return null;
        }
        for (Annotation qualifier : requiredQualifiers) {
            if (qualifier != null && hasNamedAnnotation(qualifier.annotationType())) {
                String value = getNamedValue(qualifier);
                if (value == null) {
                    return null;
                }
                String trimmed = value.trim();
                return trimmed.isEmpty() ? null : trimmed;
            }
        }
        return null;
    }

    private Set<Annotation> removeNamedQualifiers(Set<Annotation> qualifiers) {
        Set<Annotation> withoutNamed = new LinkedHashSet<>();
        if (qualifiers == null) {
            return withoutNamed;
        }
        for (Annotation qualifier : qualifiers) {
            if (qualifier == null || !hasNamedAnnotation(qualifier.annotationType())) {
                withoutNamed.add(qualifier);
            }
        }
        return withoutNamed;
    }

    /**
     * Resolves matching beans using CDI alternative precedence:
     * enabled alternatives are preferred over non-alternatives and the highest
     * priority alternative wins. Equal top priority remains ambiguous.
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

    private boolean notSameRawType(Type requiredType, Type beanType) {
        if (requiredType == null || beanType == null) {
            return true;
        }
        if (requiredType instanceof java.lang.reflect.TypeVariable ||
                requiredType instanceof java.lang.reflect.WildcardType) {
            return false;
        }

        Class<?> requiredRaw;
        Class<?> beanRaw;
        try {
            requiredRaw = normalizePrimitiveType(RawTypeExtractor.getRawType(requiredType));
            beanRaw = normalizePrimitiveType(RawTypeExtractor.getRawType(beanType));
        } catch (RuntimeException e) {
            return false;
        }

        if (requiredRaw == null || beanRaw == null) {
            return false;
        }
        return !requiredRaw.equals(beanRaw);
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
     * Basic specialization filtering: if a bean specializes its direct superclass, remove the
     * specialized superclass from candidates.
     */
    private Collection<Bean<?>> applySpecializationFiltering(Collection<Bean<?>> candidates) {
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
                .collect(Collectors.toList());
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

    private Object resolveDecoratorMetadata(InjectionPoint injectionPoint, ParameterizedType requiredType) {
        if (injectionPoint == null || injectionPoint.getBean() == null) {
            throw new IllegalStateException("Decorator metadata is not available in this context");
        }

        Class<?> declaringClass = injectionPoint.getMember().getDeclaringClass();
        DecoratorInfo decoratorInfo = findDecoratorInfo(declaringClass);
        if (decoratorInfo == null) {
            throw new DefinitionException("Decorator metadata may only be injected into decorator instances");
        }

        Type requestedType = requiredType.getActualTypeArguments()[0];
        if (!requestedType.getTypeName().equals(declaringClass.getTypeName())) {
            throw new DefinitionException("Decorator metadata type parameter must match decorator type " +
                    declaringClass.getName());
        }

        return new SerializableDecoratorMetadata<>(decoratorInfo, injectionPoint.getBean());
    }

    private Object resolveDecoratedBeanMetadata(InjectionPoint injectionPoint,
                                                ParameterizedType requiredType) {
        if (injectionPoint == null || injectionPoint.getBean() == null) {
            throw new IllegalStateException("@Decorated Bean metadata is not available in this context");
        }

        Class<?> declaringClass = injectionPoint.getMember().getDeclaringClass();
        DecoratorInfo decoratorInfo = findDecoratorInfo(declaringClass);
        if (decoratorInfo == null) {
            throw new DefinitionException("@Decorated Bean metadata may only be injected into decorator instances");
        }

        Type delegateType = decoratorInfo.getDelegateInjectionPoint().getType();
        Type requestedType = requiredType.getActualTypeArguments()[0];
        if (!requestedType.getTypeName().equals(delegateType.getTypeName())) {
            throw new DefinitionException("@Decorated Bean metadata type parameter must match delegate type " +
                    delegateType.getTypeName());
        }

        Annotation[] delegateQualifiers =
                decoratorInfo.getDelegateInjectionPoint().getQualifiers().toArray(new Annotation[0]);
        Collection<Bean<?>> candidates = findMatchingBeans(delegateType, delegateQualifiers);
        candidates = candidates.stream()
                .filter(bean -> !declaringClass.equals(bean.getBeanClass()))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            throw new UnsatisfiedResolutionException("No decorated bean found for delegate type " +
                    delegateType.getTypeName());
        }
        if (candidates.size() > 1) {
            Optional<Bean<?>> resolved = resolveByAlternativePrecedence(candidates);
            if (resolved.isPresent()) {
                return resolved.get();
            }
            throw new AmbiguousResolutionException("Ambiguous decorated bean metadata for delegate type " +
                    delegateType.getTypeName());
        }

        return candidates.iterator().next();
    }

    private DecoratorInfo findDecoratorInfo(Class<?> decoratorClass) {
        for (DecoratorInfo info : knowledgeBase.getDecoratorInfos()) {
            if (info.getDecoratorClass().equals(decoratorClass)) {
                return info;
            }
        }
        return null;
    }

    private boolean hasDecoratedQualifier(Annotation[] qualifiers) {
        if (qualifiers == null) {
            return false;
        }
        for (Annotation qualifier : qualifiers) {
            if (qualifier == null) {
                continue;
            }
            if (hasDecoratedAnnotation(qualifier.annotationType())) {
                return true;
            }
        }
        return false;
    }

    private static class SerializableBeanMetadata<T> implements Bean<T>, Serializable {
        private static final long serialVersionUID = 1L;
        private final Bean<T> delegate;

        @SuppressWarnings("unchecked")
        private SerializableBeanMetadata(Bean<?> delegate) {
            this.delegate = (Bean<T>) delegate;
        }

        @Override
        public Class<?> getBeanClass() { return delegate.getBeanClass(); }
        @Override
        public Set<InjectionPoint> getInjectionPoints() { return delegate.getInjectionPoints(); }
        @Override
        public String getName() { return delegate.getName(); }
        @Override
        public Set<Annotation> getQualifiers() { return delegate.getQualifiers(); }
        @Override
        public Class<? extends Annotation> getScope() { return delegate.getScope(); }
        @Override
        public Set<Class<? extends Annotation>> getStereotypes() { return delegate.getStereotypes(); }
        @Override
        public Set<Type> getTypes() { return delegate.getTypes(); }
        @Override
        public boolean isAlternative() { return delegate.isAlternative(); }
        @Override
        public T create(CreationalContext<T> creationalContext) { return delegate.create(creationalContext); }
        @Override
        public void destroy(T instance, CreationalContext<T> creationalContext) { delegate.destroy(instance, creationalContext); }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Bean<?>)) {
                return false;
            }
            Bean<?> other = (Bean<?>) obj;
            return Objects.equals(getBeanClass(), other.getBeanClass());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(getBeanClass());
        }
    }

    private static final class SerializableDecoratorMetadata<T> extends SerializableBeanMetadata<T>
            implements Decorator<T> {
        private static final long serialVersionUID = 1L;
        private final Type delegateType;
        private final Set<Annotation> delegateQualifiers;
        private final Set<Type> decoratedTypes;

        private SerializableDecoratorMetadata(DecoratorInfo info, Bean<?> bean) {
            super(bean);
            this.delegateType = info.getDelegateInjectionPoint().getType();
            this.delegateQualifiers = Collections.unmodifiableSet(
                    new HashSet<>(info.getDelegateInjectionPoint().getQualifiers()));
            this.decoratedTypes = Collections.unmodifiableSet(new HashSet<>(info.getDecoratedTypes()));
        }

        @Override
        public Type getDelegateType() {
            return delegateType;
        }

        @Override
        public Set<Annotation> getDelegateQualifiers() {
            return delegateQualifiers;
        }

        @Override
        public Set<Type> getDecoratedTypes() {
            return decoratedTypes;
        }
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

    private boolean isBeanEnabledForResolution(Bean<?> bean) {
        if (bean == null) {
            return false;
        }
        if (bean instanceof ProducerBean) {
            ProducerBean<?> producerBean = (ProducerBean<?>) bean;
            Bean<?> declaringBean = findDeclaringBean(producerBean.getDeclaringClass());
            if (declaringBean == null) {
                return false;
            }
            if (!isBeanEnabledForResolution(declaringBean)) {
                return false;
            }
            return producerBean.isAlternativeEnabled();
        }
        if (bean instanceof SyntheticProducerBeanImpl) {
            Bean<?> originalBean = findOriginalProducerBean(bean);
            if (originalBean instanceof ProducerBean) {
                return isBeanEnabledForResolution(originalBean);
            }
        }
        if (!bean.isAlternative()) {
            return true;
        }
        if (bean instanceof SyntheticBean) {
            return ((SyntheticBean<?>) bean).getPriority() != null || isAlternativeSelectedByClassOrStereotype(bean);
        }
        if (bean instanceof BeanImpl) {
            return ((BeanImpl<?>) bean).isAlternativeEnabled();
        }
        return isAlternativeSelectedByClassOrStereotype(bean);
    }

    private Bean<?> findDeclaringBean(Class<?> declaringClass) {
        if (declaringClass == null) {
            return null;
        }
        for (Bean<?> candidate : knowledgeBase.getValidBeans()) {
            if (candidate instanceof ProducerBean) {
                continue;
            }
            if (declaringClass.equals(candidate.getBeanClass())) {
                return candidate;
            }
        }
        return null;
    }

    private Bean<?> findOriginalProducerBean(Bean<?> syntheticBean) {
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

    private boolean isAlternativeSelectedByClassOrStereotype(Bean<?> bean) {
        if (bean instanceof SyntheticProducerBeanImpl) {
            Bean<?> originalBean = findOriginalProducerBean(bean);
            if (originalBean instanceof ProducerBean) {
                ProducerBean<?> producerBean = (ProducerBean<?>) originalBean;

                if (extractPriorityFromProducerMember(producerBean) != null) {
                    return true;
                }
                if (producerBean.getPriority() != null) {
                    return true;
                }

                Class<?> declaringClass = producerBean.getDeclaringClass();
                if (declaringClass != null) {
                    Integer declaringPriority = extractPriorityFromClass(declaringClass);
                    if (declaringPriority != null) {
                        return true;
                    }

                    String declaringClassName = declaringClass.getName();
                    if (knowledgeBase.isAlternativeEnabledProgrammatically(declaringClassName) ||
                            knowledgeBase.isAlternativeEnabledInBeansXml(declaringClassName)) {
                        return true;
                    }

                    for (Annotation annotation : declaringClass.getAnnotations()) {
                        Class<? extends Annotation> annotationType = annotation.annotationType();
                        if (hasStereotypeAnnotation(annotationType)) {
                            String stereotypeName = annotationType.getName();
                            if (knowledgeBase.isAlternativeEnabledProgrammatically(stereotypeName) ||
                                    knowledgeBase.isAlternativeEnabledInBeansXml(stereotypeName)) {
                                return true;
                            }
                        }
                    }
                }

                Set<Class<? extends Annotation>> producerStereotypes = producerBean.getStereotypes();
                if (producerStereotypes != null) {
                    for (Class<? extends Annotation> stereotype : producerStereotypes) {
                        String stereotypeName = stereotype.getName();
                        if (knowledgeBase.isAlternativeEnabledProgrammatically(stereotypeName) ||
                                knowledgeBase.isAlternativeEnabledInBeansXml(stereotypeName)) {
                            return true;
                        }
                    }
                }
            }
        }

        Class<?> beanClass = bean.getBeanClass();
        if (beanClass == null) {
            return false;
        }

        if (knowledgeBase.getApplicationAlternativeOrder(beanClass) >= 0) {
            return true;
        }

        Integer classPriority = extractPriorityFromClass(beanClass);
        if (classPriority != null) {
            return true;
        }

        String className = beanClass.getName();
        if (knowledgeBase.isAlternativeEnabledProgrammatically(className) ||
                knowledgeBase.isAlternativeEnabledInBeansXml(className)) {
            return true;
        }

        Set<Class<? extends Annotation>> stereotypes = bean.getStereotypes();
        if (stereotypes != null) {
            for (Class<? extends Annotation> stereotype : stereotypes) {
                String stereotypeName = stereotype.getName();
                if (knowledgeBase.isAlternativeEnabledProgrammatically(stereotypeName) ||
                        knowledgeBase.isAlternativeEnabledInBeansXml(stereotypeName)) {
                    return true;
                }
            }
        }

        for (Annotation annotation : beanClass.getAnnotations()) {
            Class<? extends Annotation> type = annotation.annotationType();
            if (hasStereotypeAnnotation(type)) {
                String stereotypeName = type.getName();
                if (knowledgeBase.isAlternativeEnabledProgrammatically(stereotypeName) ||
                        knowledgeBase.isAlternativeEnabledInBeansXml(stereotypeName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isNotBeanClassAccessibleFromCurrentInjectionPoint(Bean<?> bean) {
        if (bean == null) {
            return true;
        }
        InjectionPoint injectionPoint = getCurrentInjectionPoint();
        Class<?> beanClass = bean.getBeanClass();
        if (injectionPoint == null) {
            return beanClass != null && Modifier.isPrivate(beanClass.getModifiers());
        }
        Member member = injectionPoint.getMember();
        Class<?> declaringClass = member != null ? member.getDeclaringClass() : null;
        if (declaringClass == null && injectionPoint.getBean() != null) {
            declaringClass = injectionPoint.getBean().getBeanClass();
        }
        return !isClassAccessibleTo(beanClass, declaringClass);
    }

    private boolean isClassAccessibleTo(Class<?> beanClass, Class<?> consumerClass) {
        if (beanClass == null || consumerClass == null) {
            return true;
        }
        if (beanClass.equals(consumerClass)) {
            return true;
        }

        int modifiers = beanClass.getModifiers();
        if (Modifier.isPublic(modifiers)) {
            return enclosingClassesAccessible(beanClass, consumerClass);
        }

        if (Modifier.isPrivate(modifiers)) {
            Class<?> owner = beanClass.getEnclosingClass();
            return owner != null && (owner.equals(consumerClass) || isEnclosedWithin(consumerClass, owner));
        }

        if (Modifier.isProtected(modifiers)) {
            return inSamePackage(beanClass, consumerClass) || beanClass.isAssignableFrom(consumerClass);
        }

        return inSamePackage(beanClass, consumerClass);
    }

    private boolean enclosingClassesAccessible(Class<?> beanClass, Class<?> consumerClass) {
        Class<?> enclosing = beanClass.getEnclosingClass();
        while (enclosing != null) {
            int modifiers = enclosing.getModifiers();
            if (Modifier.isPrivate(modifiers)) {
                return enclosing.equals(consumerClass) || isEnclosedWithin(consumerClass, enclosing);
            }
            if (!Modifier.isPublic(modifiers) && !inSamePackage(enclosing, consumerClass)) {
                return false;
            }
            enclosing = enclosing.getEnclosingClass();
        }
        return true;
    }

    private boolean isEnclosedWithin(Class<?> nestedClass, Class<?> enclosingClass) {
        Class<?> current = nestedClass;
        while (current != null) {
            if (current.equals(enclosingClass)) {
                return true;
            }
            current = current.getEnclosingClass();
        }
        return false;
    }

    private boolean inSamePackage(Class<?> a, Class<?> b) {
        String pa = packageName(a);
        String pb = packageName(b);
        return pa.equals(pb);
    }

    private String packageName(Class<?> clazz) {
        Package pkg = clazz.getPackage();
        return pkg != null ? pkg.getName() : "";
    }

    /**
     * Formats an array of qualifiers for human-friendly CDI-style error messages.
     */
    private String formatQualifiers(Annotation[] qualifiers) {
        if (qualifiers == null || qualifiers.length == 0) {
            return "[@Default]";
        }

        return Arrays.stream(qualifiers)
            .filter(a -> hasQualifierAnnotation(a.annotationType()))
            .sorted(Comparator.comparing(a -> a.annotationType().getName()))
            .map(this::qualifierToString)
            .collect(Collectors.joining(", ", "[", "]"));
    }

    private String qualifierToString(Annotation qualifier) {
        if (qualifier instanceof Named) {
            return "@Named(" + getNamedValue(qualifier) + ")";
        }
        return "@" + qualifier.annotationType().getSimpleName();
    }

    /**
     * Builds a CDI-style resolution error message with contextual injection point details.
     */
    private String buildResolutionMessage(String prefix, Type requiredType, Annotation[] qualifiers) {
        StringBuilder message = new StringBuilder()
            .append(prefix)
            .append(" for type ")
            .append(requiredType.getTypeName())
            .append(" with qualifiers ")
            .append(formatQualifiers(qualifiers));

        InjectionPoint ip = getCurrentInjectionPoint();
        if (ip != null) {
            message.append(" at ").append(describeInjectionPoint(ip));
        }
        return message.toString();
    }

    private String describeInjectionPoint(InjectionPoint ip) {
        StringBuilder sb = new StringBuilder("[");
        if (ip.getBean() != null) {
            sb.append(ip.getBean().getBeanClass().getName()).append(" -> ");
        }
        if (ip.getMember() != null) {
            sb.append(ip.getMember().getDeclaringClass().getName())
              .append("#")
              .append(ip.getMember().getName());
        } else {
            sb.append("<unknown member>");
        }

        if (ip.getAnnotated() instanceof AnnotatedParameter) {
            AnnotatedParameter<?> param = (AnnotatedParameter<?>) ip.getAnnotated();
            sb.append(" parameter ").append(param.getPosition());
        }
        sb.append("]");
        return sb.toString();
    }

    private String formatBeanSummary(Bean<?> bean) {
        String qualifiers = bean.getQualifiers().stream()
            .filter(q -> hasQualifierAnnotation(q.annotationType()))
            .map(this::qualifierToString)
            .sorted()
            .collect(Collectors.joining(", "));
        if (qualifiers.isEmpty()) {
            qualifiers = "@Default";
        }
        return bean.getBeanClass().getName() + " {" + qualifiers + "}";
    }

    /**
     * Gets or creates an instance from the appropriate scope.
     * <p>
     * For normal scopes (ApplicationScoped, RequestScoped, SessionScoped, ConversationScoped),
     * this returns a CLIENT PROXY instead of the actual instance. The proxy will delegate
     * all method calls to the current contextual instance from the scope.
     * <p>
     * Why proxies are needed:
     * - Allows injecting short-lived beans (RequestScoped) into long-lived beans (ApplicationScoped)
     * - The proxy ensures each method call gets the correct contextual instance
     * - Without proxies, you'd get the wrong instance (e.g., the same RequestScoped instance for all requests)
     * <p>
     * For pseudo-scopes (Dependent), this returns the actual instance directly since
     * Dependent beans are created fresh for each injection point and don't need proxies.
     */
    private <T> T getInstanceFromScope(Bean<T> bean) {
        Class<? extends Annotation> scope = bean.getScope();

        // Check if this is a normal scope that requires a client proxy
        if (contextManager.isNormalScope(scope)) {
            // For normal scopes, return a client proxy
            // The proxy will delegate to the contextual instance on each method call
            T proxy = contextManager.createClientProxy(bean);
            if (proxy != null) {
                return maybeDecorateResolvedInstance(bean, proxy, new CreationalContextImpl<>());
            }
        }

        // For pseudo-scopes like @Dependent, or if proxy support is unavailable, return
        // the actual contextual instance from the active scope.
        ScopeContext context = contextManager.getContext(scope);
        CreationalContext<T> creationalContext = owningBeanManager != null
                ? owningBeanManager.createCreationalContext(bean)
                : new CreationalContextImpl<T>();
        T instance = context.get(bean, creationalContext);
        if (DEPENDENT.matches(scope) && owningBeanManager != null) {
            owningBeanManager.registerOwnedTransientReference(bean, instance, creationalContext);
        }
        return maybeDecorateResolvedInstance(bean, instance, creationalContext);
    }

    private <T> T maybeDecorateResolvedInstance(Bean<T> bean, T instance, CreationalContext<T> creationalContext) {
        if (instance == null || bean == null) {
            return instance;
        }
        if (bean instanceof BeanImpl<?> || bean instanceof ProducerBean<?>) {
            return instance;
        }
        if (bean instanceof Interceptor || bean instanceof Decorator) {
            return instance;
        }
        if (BeanManager.class.equals(bean.getBeanClass())
                && bean.getQualifiers().contains(jakarta.enterprise.inject.Default.Literal.INSTANCE)) {
            return instance;
        }

        List<DecoratorInfo> decorators = decoratorSupport.resolve(bean.getTypes(), bean.getQualifiers());
        if (decorators.isEmpty() && owningBeanManager != null) {
            decorators = resolveDecoratorsViaBeanManager(bean);
        }
        if (decorators.isEmpty() || owningBeanManager == null) {
            return instance;
        }

        @SuppressWarnings("unchecked")
        T decorated = (T) decoratorSupport.applyDecoratorChain(
                instance,
                decorators,
                owningBeanManager,
                creationalContext
        );
        return decorated != null ? decorated : instance;
    }

    private <T> List<DecoratorInfo> resolveDecoratorsViaBeanManager(Bean<T> bean) {
        Annotation[] qualifierArray = bean.getQualifiers().toArray(new Annotation[0]);
        List<Decorator<?>> resolved;
        try {
            resolved = owningBeanManager.resolveDecorators(bean.getTypes(), qualifierArray);
        } catch (IllegalStateException lifecycleGuard) {
            // BeanManager SPI lookup is not available before AfterBeanDiscovery.
            return Collections.emptyList();
        }
        if (resolved.isEmpty()) {
            return Collections.emptyList();
        }
        List<DecoratorInfo> infos = new ArrayList<>();
        for (Decorator<?> decorator : resolved) {
            DecoratorInfo info = findDecoratorInfoByClass(decorator.getBeanClass());
            if (info != null) {
                infos.add(info);
            }
        }
        return infos;
    }

    private DecoratorInfo findDecoratorInfoByClass(Class<?> decoratorClass) {
        for (DecoratorInfo info : knowledgeBase.getDecoratorInfos()) {
            if (info.getDecoratorClass().equals(decoratorClass)) {
                return info;
            }
        }
        return null;
    }

    /**
     * Creates a Provider/Instance wrapper for lazy dependency resolution.
     * Since Instance<T> extends Provider<T>, this single method handles both cases.
     * The returned wrapper implements the full Instance<T> interface which includes Provider<T>.
     *
     * @param type the type of instances to provide
     * @param qualifiers the qualifiers to use for resolution
     * @return Instance wrapper (which also implements Provider)
     */
    @SuppressWarnings("unchecked")
    private <T> Instance<T> createProviderWrapper(Class<T> type, Collection<Annotation> qualifiers) {
        InjectionPoint ownerInjectionPoint = getCurrentInjectionPoint();

        // Create a resolution strategy that delegates to BeanResolver
        InstanceImpl.ResolutionStrategy<T> strategy = new InstanceImpl.ResolutionStrategy<T>() {
            @Override
            public T resolveInstance(Class<T> typeToResolve, Collection<Annotation> quals) {
                // Convert qualifiers to array
                Annotation[] qualArray = quals.toArray(new Annotation[0]);
                Object resolved = resolveWithDynamicInjectionPoint(typeToResolve, qualArray, ownerInjectionPoint);
                return (T) resolved;
            }

            @Override
            public T resolveInstance(Type typeToResolve, Collection<Annotation> quals) {
                Annotation[] qualArray = quals.toArray(new Annotation[0]);
                Object resolved = resolveWithDynamicInjectionPoint(typeToResolve, qualArray, ownerInjectionPoint);
                return (T) resolved;
            }

            @Override
            public Collection<Class<? extends T>> resolveImplementations(Class<T> typeToResolve, Collection<Annotation> quals) {
                // Find all matching beans
                Annotation[] qualArray = quals.toArray(new Annotation[0]);
                Collection<Bean<?>> beans = findMatchingBeansForIterator(typeToResolve, qualArray);

                // Extract bean classes
                List<Class<? extends T>> implementations = new ArrayList<>();
                for (Bean<?> bean : beans) {
                    implementations.add((Class<? extends T>) bean.getBeanClass());
                }
                return implementations;
            }

            @Override
            public Collection<Class<? extends T>> resolveImplementations(Type typeToResolve, Collection<Annotation> quals) {
                Annotation[] qualArray = quals.toArray(new Annotation[0]);
                Collection<Bean<?>> beans = findMatchingBeansForIterator(typeToResolve, qualArray);

                List<Class<? extends T>> implementations = new ArrayList<>();
                for (Bean<?> bean : beans) {
                    implementations.add((Class<? extends T>) bean.getBeanClass());
                }
                return implementations;
            }

            @Override
            public void invokePreDestroy(T instance) throws InvocationTargetException, IllegalAccessException {
                // Invoke @PreDestroy via LifecycleMethodHelper
                LifecycleMethodHelper.invokeLifecycleMethod(instance, PRE_DESTROY);
            }
        };

        // Look up the Bean metadata from KnowledgeBase so Handle#getBean can return it
        java.util.function.Function<Class<? extends T>, Bean<? extends T>> beanLookup = beanClass -> {
            for (Bean<?> bean : knowledgeBase.getValidBeans()) {
                if (bean.getBeanClass().equals(beanClass)) {
                    @SuppressWarnings("unchecked")
                    Bean<? extends T> cast = (Bean<? extends T>) bean;
                    return cast;
                }
            }
            return null;
        };

        Instance<T> baseInstance = new InstanceImpl<>(type, qualifiers, strategy, beanLookup, owningBeanManager);
        if (owningBeanManager == null) {
            return baseInstance;
        }

        Set<Annotation> normalizedQualifiers = new HashSet<>(qualifiers);
        normalizedQualifiers.add(new AnyLiteral());

        Set<Type> instanceBeanTypes = new HashSet<>();
        instanceBeanTypes.add(Instance.class);
        instanceBeanTypes.add(Object.class);
        instanceBeanTypes.add(parameterizedInstanceType(type));
        instanceBeanTypes.add(parameterizedProviderType(type));
        instanceBeanTypes.add(parameterizedIterableType(type));

        List<DecoratorInfo> decorators = decoratorSupport.resolve(instanceBeanTypes, normalizedQualifiers);
        if (decorators.isEmpty()) {
            return baseInstance;
        }

        @SuppressWarnings("unchecked")
        Instance<T> decoratedInstance = (Instance<T>) decoratorSupport.applyDecoratorChain(
                baseInstance,
                decorators,
                owningBeanManager,
                new CreationalContextImpl<>()
        );
        return decoratedInstance != null ? decoratedInstance : baseInstance;
    }

    private ParameterizedType parameterizedInstanceType(final Type argumentType) {
        return new ParameterizedType() {
            @Override
            public @Nonnull Type[] getActualTypeArguments() {
                return new Type[]{argumentType};
            }

            @Override
            public @Nonnull Type getRawType() {
                return Instance.class;
            }

            @Override
            public Type getOwnerType() {
                return null;
            }
        };
    }

    private ParameterizedType parameterizedProviderType(final Type argumentType) {
        return new ParameterizedType() {
            @Override
            public @Nonnull Type[] getActualTypeArguments() {
                return new Type[]{argumentType};
            }

            @Override
            public @Nonnull Type getRawType() {
                return Provider.class;
            }

            @Override
            public Type getOwnerType() {
                return null;
            }
        };
    }

    private ParameterizedType parameterizedIterableType(final Type argumentType) {
        return new ParameterizedType() {
            @Override
            public @Nonnull Type[] getActualTypeArguments() {
                return new Type[]{argumentType};
            }

            @Override
            public @Nonnull Type getRawType() {
                return Iterable.class;
            }

            @Override
            public Type getOwnerType() {
                return null;
            }
        };
    }

    /**
     * Applies CDI iterator() ambiguity semantics for Instance:
     * - Unsatisfied => empty set
     * - Ambiguous with at least one alternative => eliminate non-alternatives, then
     *   if all remaining have explicit priority, keep the highest-priority subset
     * - Ambiguous with no alternatives => keep all candidates
     */
    private Collection<Bean<?>> findMatchingBeansForIterator(Type requiredType, Annotation[] qualifiers) {
        Collection<Bean<?>> candidates = findMatchingBeans(requiredType, qualifiers);
        if (candidates.size() <= 1) {
            return candidates;
        }

        boolean hasAlternative = candidates.stream().anyMatch(this::isEffectivelyAlternative);
        if (!hasAlternative) {
            return candidates;
        }

        List<Bean<?>> remaining = candidates.stream()
                .filter(this::isEffectivelyAlternative)
                .collect(Collectors.toList());

        if (remaining.size() <= 1) {
            return remaining;
        }

        boolean allHaveExplicitPriority = remaining.stream()
                .allMatch(bean -> getExplicitAlternativePriority(bean) != null);
        if (!allHaveExplicitPriority) {
            return remaining;
        }

        int highestPriority = remaining.stream()
                .map(this::getExplicitAlternativePriority)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(Integer.MIN_VALUE);

        return remaining.stream()
                .filter(bean -> Objects.equals(getExplicitAlternativePriority(bean), highestPriority))
                .collect(Collectors.toList());
    }

    private Integer getExplicitAlternativePriority(Bean<?> bean) {
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

            return extractPriorityFromClass(producerBean.getDeclaringClass());
        }

        if (bean instanceof BeanImpl) {
            Integer beanPriority = ((BeanImpl<?>) bean).getPriority();
            if (beanPriority != null) {
                return beanPriority;
            }
        }

        return extractPriorityFromClass(bean.getBeanClass());
    }

    /**
     * Creates an Event wrapper for programmatic event firing.
     * This allows injection of Event&lt;T&gt; with appropriate type parameters and qualifiers.
     *
     * @param eventType the type of events to fire
     * @param qualifiers the qualifiers for event filtering
     * @return Event instance configured for the specified type and qualifiers
     */
    private <T> Event<T> createEventWrapper(Type eventType, Set<Annotation> qualifiers) {
        Set<Annotation> normalizedQualifiers = new HashSet<>(qualifiers);
        normalizedQualifiers.add(new AnyLiteral());
        InjectionPoint ownerInjectionPoint = getCurrentInjectionPoint();
        Event<T> baseEvent = observerSupport.createEvent(eventType, normalizedQualifiers, ownerInjectionPoint);

        if (owningBeanManager == null) {
            return baseEvent;
        }

        Set<Type> eventBeanTypes = new HashSet<>();
        eventBeanTypes.add(Event.class);
        eventBeanTypes.add(Object.class);
        eventBeanTypes.add(parameterizedEventType(eventType));

        List<DecoratorInfo> decorators = decoratorSupport.resolve(eventBeanTypes, normalizedQualifiers);
        if (decorators.isEmpty()) {
            return baseEvent;
        }

        @SuppressWarnings("unchecked")
        Event<T> decoratedEvent = (Event<T>) decoratorSupport.applyDecoratorChain(
                baseEvent,
                decorators,
                owningBeanManager,
                new CreationalContextImpl<>()
        );
        return decoratedEvent != null ? decoratedEvent : baseEvent;
    }

    private InjectionPoint createInjectionPointWrapper(InjectionPoint injectionPoint, Annotation[] qualifiers) {
        if (injectionPoint == null || owningBeanManager == null) {
            return injectionPoint;
        }

        Set<Annotation> normalizedQualifiers = extractQualifiers(qualifiers);
        normalizedQualifiers.add(new AnyLiteral());

        Set<Type> injectionPointTypes = new HashSet<>();
        injectionPointTypes.add(InjectionPoint.class);
        injectionPointTypes.add(Object.class);

        List<DecoratorInfo> decorators = decoratorSupport.resolve(injectionPointTypes, normalizedQualifiers);
        if (decorators.isEmpty()) {
            return injectionPoint;
        }

        Object decorated = decoratorSupport.applyDecoratorChain(
                injectionPoint,
                decorators,
                owningBeanManager,
                new CreationalContextImpl<>()
        );
        return decorated instanceof InjectionPoint ? (InjectionPoint) decorated : injectionPoint;
    }

    private ParameterizedType parameterizedEventType(final Type eventType) {
        return new ParameterizedType() {
            @Override
            public @Nonnull Type[] getActualTypeArguments() {
                return new Type[]{eventType};
            }

            @Override
            public @Nonnull Type getRawType() {
                return Event.class;
            }

            @Override
            public Type getOwnerType() {
                return null;
            }

            @Override
            public String getTypeName() {
                return "jakarta.enterprise.event.Event<" + eventType.getTypeName() + ">";
            }
        };
    }

    private Object resolveWithDynamicInjectionPoint(Type dynamicType,
                                                    Annotation[] dynamicQualifiers,
                                                    InjectionPoint ownerInjectionPoint) {
        if (ownerInjectionPoint == null) {
            return resolve(dynamicType, dynamicQualifiers);
        }

        Set<Annotation> qualifierSet = new HashSet<>();
        if (dynamicQualifiers != null) {
            qualifierSet.addAll(Arrays.asList(dynamicQualifiers));
        }

        InjectionPoint dynamicInjectionPoint = new ConfiguredInjectionPoint(
                ownerInjectionPoint,
                dynamicType,
                qualifierSet,
                ownerInjectionPoint.isDelegate(),
                ownerInjectionPoint.isTransient()
        );

        setCurrentInjectionPoint(dynamicInjectionPoint);
        try {
            return resolve(dynamicType, dynamicQualifiers);
        } finally {
            clearCurrentInjectionPoint();
        }
    }

    public TransactionServices getTransactionServices() {
        return transactionServices;
    }

    void setTransactionServices(TransactionServices transactionServices) {
        this.transactionServices = transactionServices == null ? new NoOpTransactionServices() : transactionServices;
    }

    /**
     * Simple implementation of CreationalContext.
     * This tracks dependent instances for cleanup.
     */
    private static class CreationalContextImpl<T> implements CreationalContext<T> {
        private final List<Object> dependentInstances = new ArrayList<>();

        @Override
        public void push(T incompleteInstance) {
            // Not needed for our simple implementation
        }

        @Override
        public void release() {
            // Clean up dependent instances
            dependentInstances.clear();
        }

        public void addDependentInstance(Object instance) {
            dependentInstances.add(instance);
        }
    }

    // ==================== InjectionPoint Context Management ====================

    /**
     * Sets the current injection point context for InjectionPoint bean resolution.
     *
     * <p>BeanImpl must call this method before resolving dependencies
     * to provide the correct contextual InjectionPoint metadata.
     *
     * <p><b>Thread Safety:</b> Uses ThreadLocal, safe for concurrent injection.
     *
     * @param injectionPoint the current injection point being resolved
     */
    public void setCurrentInjectionPoint(InjectionPoint injectionPoint) {
        if (injectionPoint == null) {
            return;
        }
        currentInjectionPoint.get().push(injectionPoint);
    }

    /**
     * Clears the current injection point context after resolution completes.
     *
     * <p>This prevents memory leaks in thread pools and ensures a clean state.
     */
    public void clearCurrentInjectionPoint() {
        Deque<InjectionPoint> stack = currentInjectionPoint.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            currentInjectionPoint.remove();
        }
    }

    /**
     * Clears thread-local and cache state retained by this resolver instance.
     * Intended for container shutdown.
     */
    public void clearRuntimeState() {
        currentInjectionPoint.remove();
        owningBeanManager = null;
        if (decoratorSupport != null) {
            decoratorSupport.clear();
        }
    }

    /**
     * Gets the current injection point context (for testing/debugging).
     *
     * @return the current injection point, or null if not set
     */
    public InjectionPoint getCurrentInjectionPoint() {
        Deque<InjectionPoint> stack = currentInjectionPoint.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    private InjectionPoint resolveContextualInjectionPoint() {
        Deque<InjectionPoint> stack = currentInjectionPoint.get();
        if (stack.isEmpty()) {
            return null;
        }
        // Prefer an owning non-InjectionPoint site when available (e.g., dependent
        // beans instantiated for an outer injection point).
        for (InjectionPoint injectionPoint : stack) {
            if (!isInjectionPointMetadataType(injectionPoint.getType())) {
                return injectionPoint;
            }
        }

        // Dynamic Instance.get()/select() lookups synthesize a configured metadata
        // injection point that remains valid even without an outer non-metadata site.
        InjectionPoint active = stack.peek();
        if (active instanceof ConfiguredInjectionPoint) {
            return active;
        }

        // CDI 4.1: if there is no owning injection point, InjectionPoint is null.
        return null;
    }

    private boolean isInjectionPointMetadataType(Type type) {
        if (!(type instanceof Class<?>)) {
            return false;
        }
        String typeName = ((Class<?>) type).getName();
        return "jakarta.enterprise.inject.spi.InjectionPoint".equals(typeName) ||
                "javax.enterprise.inject.spi.InjectionPoint".equals(typeName);
    }

    public ContextManager getContextManager() {
        return contextManager;
    }
}
