package com.threeamigos.common.util.implementations.injection.decorators;

import com.threeamigos.common.util.implementations.injection.annotations.AnnotationExtractors;

import com.threeamigos.common.util.implementations.injection.knowledgebase.DecoratorInfo;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.types.RawTypeExtractor;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.Decorator;
import jakarta.enterprise.inject.spi.InjectionPoint;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.*;
import java.util.stream.Collectors;

import static com.threeamigos.common.util.implementations.injection.annotations.QualifiersHelper.normalizeQualifiers;
import static com.threeamigos.common.util.implementations.injection.annotations.QualifiersHelper.qualifiersMatch;

/**
 * Resolves decorators for target beans based on type matching.
 *
 * <p>This class is responsible for determining which decorators should be applied to a given
 * bean based on:
 * <ul>
 *   <li>Type compatibility (decorator implements the same interface/class as the bean)</li>
 *   <li>Qualifier matching (decorators can have qualifiers)</li>
 *   <li>Priority ordering (lower priority = outer decorator, executed first)</li>
 * </ul>
 *
 * <p><b>CDI 4.1 Decorator Resolution Rules:</b>
 * <ul>
 *   <li>A decorator applies if its decorated types match the bean's types</li>
 *   <li>Decorators are type-based, not annotation-based (unlike interceptors)</li>
 *   <li>Multiple decorators can apply to the same bean</li>
 *   <li>Decorators are ordered by priority (lower value = earlier/outer execution)</li>
 *   <li>Each decorator receives a @Delegate injection point referencing the next decorator or bean</li>
 * </ul>
 *
 * <p><b>Decorator vs Interceptor Resolution:</b>
 * <table>
 * <tr><th>Aspect</th><th>Interceptors</th><th>Decorators</th></tr>
 * <tr><td>Matching</td><td>Annotation bindings</td><td>Type compatibility</td></tr>
 * <tr><td>Granularity</td><td>Per-method</td><td>Per-bean (all methods)</td></tr>
 * <tr><td>Delegation</td><td>InvocationContext.proceed()</td><td>@Delegate injection</td></tr>
 * </table>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * DecoratorResolver resolver = new DecoratorResolver(knowledgeBase);
 *
 * // Resolve decorators for a PaymentProcessor bean
 * Set<Type> beanTypes = Set.of(PaymentProcessor.class, Serializable.class);
 * Set<Annotation> qualifiers = Set.of(new DefaultLiteral());
 * List<DecoratorInfo> decorators = resolver.resolve(beanTypes, qualifiers);
 * // Returns: [TimingDecorator, LoggingDecorator] (sorted by priority)
 * }</pre>
 *
 * <p><b>Execution Order Example:</b>
 * <pre>
 * If decorators are resolved as [Decorator1(priority=100), Decorator2(priority=200)]:
 *
 * Client → Decorator1 → Decorator2 → Actual Bean
 *                                    ← Return Value
 *
 * Decorator1 wraps Decorator2 wraps Actual Bean
 * </pre>
 *
 * @see DecoratorInfo
 * @see KnowledgeBase
 * @see DecoratorChain
 * @author Stefano Reksten
 */
public class DecoratorResolver {

    private final KnowledgeBase knowledgeBase;

    /**
     * Creates a decorator resolver.
     *
     * @param knowledgeBase the knowledge base containing decorator metadata
     */
    public DecoratorResolver(@Nonnull KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    /**
     * Resolves decorators for a bean with the given types and qualifiers.
     *
     * <p>This method:
     * <ol>
     *   <li>Queries all decorators from KnowledgeBase</li>
     *   <li>Filters decorators that match the bean's types</li>
     *   <li>Filters decorators that match the bean's qualifiers</li>
     *   <li>Sorts decorators by priority (lower priority = outer decorator)</li>
     * </ol>
     *
     * <p><b>Type Matching:</b>
     * A decorator matches if ANY of its decorated types are assignable from ANY of the bean's types.
     *
     * <p><b>Priority Ordering:</b>
     * Decorators are sorted by priority (ascending). Lower priority decorators execute first:
     * <pre>
     * Priority 100 (outer) → Priority 200 → Priority 300 (inner) → Bean
     * </pre>
     *
     * @param beanTypes the set of types implemented by the bean
     * @param qualifiers the set of qualifiers on the bean
     * @return list of matching decorators sorted by priority (can be empty, never null)
     */
    public List<DecoratorInfo> resolve(@Nonnull Set<Type> beanTypes, @Nonnull Set<Annotation> qualifiers) {
        if (beanTypes.isEmpty()) {
            return Collections.emptyList();
        }
        List<DecoratorInfo> allDecorators = collectRuntimeDecorators();
        return allDecorators.stream()
                .filter(this::isEnabled) // must be enabled via beans.xml or @Priority
                .filter(decorator -> matchesDelegateType(decorator, beanTypes))
                .filter(decorator -> matchesQualifiers(decorator, qualifiers))
                .sorted(decoratorOrderingComparator())
                .collect(Collectors.toList());
    }

    private List<DecoratorInfo> collectRuntimeDecorators() {
        List<DecoratorInfo> all = new ArrayList<>();
        Set<Class<?>> seenDecoratorClasses = new HashSet<>();

        for (DecoratorInfo info : knowledgeBase.getDecoratorInfos()) {
            all.add(info);
            seenDecoratorClasses.add(info.getDecoratorClass());
        }

        for (Bean<?> bean : knowledgeBase.getBeans()) {
            if (!(bean instanceof Decorator<?>)) {
                continue;
            }
            Decorator<?> decoratorBean = (Decorator<?>) bean;
            Class<?> decoratorClass = decoratorBean.getBeanClass();
            if (decoratorClass == null || !seenDecoratorClasses.add(decoratorClass)) {
                continue;
            }

            DecoratorInfo syntheticInfo = toDecoratorInfo(decoratorBean);
            if (syntheticInfo != null) {
                all.add(syntheticInfo);
            }
        }

        return all;
    }

    private DecoratorInfo toDecoratorInfo(Decorator<?> decoratorBean) {
        Type delegateType = decoratorBean.getDelegateType();
        if (delegateType == null) {
            return null;
        }
        Set<Annotation> delegateQualifiers = decoratorBean.getDelegateQualifiers();

        Set<Type> decoratedTypes = decoratorBean.getDecoratedTypes();
        if (decoratedTypes == null || decoratedTypes.isEmpty()) {
            decoratedTypes = new LinkedHashSet<>();
            decoratedTypes.add(delegateType);
        } else {
            decoratedTypes = new LinkedHashSet<>(decoratedTypes);
        }

        InjectionPoint delegateInjectionPoint = findDelegateInjectionPoint(
                decoratorBean,
                delegateType,
                delegateQualifiers
        );

        return new DecoratorInfo(
                decoratorBean.getBeanClass(),
                decoratedTypes,
                extractPriority(decoratorBean.getBeanClass()),
                delegateInjectionPoint
        );
    }

    private InjectionPoint findDelegateInjectionPoint(Decorator<?> decoratorBean,
                                                      Type delegateType,
                                                      Set<Annotation> delegateQualifiers) {
        Set<InjectionPoint> injectionPoints = decoratorBean.getInjectionPoints();
        if (injectionPoints != null) {
            for (InjectionPoint injectionPoint : injectionPoints) {
                if (injectionPoint != null && injectionPoint.isDelegate()) {
                    Member member = injectionPoint.getMember();
                    if (member instanceof Field && Modifier.isStatic(member.getModifiers())) {
                        continue;
                    }
                    return injectionPoint;
                }
            }
            if (injectionPoints.size() == 1) {
                InjectionPoint single = injectionPoints.iterator().next();
                if (single != null) {
                    Member member = single.getMember();
                    if (member instanceof Field && Modifier.isStatic(member.getModifiers())) {
                        single = null;
                    }
                }
                if (single != null) {
                    return single;
                }
            }
        }

        return new SyntheticDelegateInjectionPoint(
                delegateType,
                delegateQualifiers != null ? delegateQualifiers : Collections.emptySet(),
                decoratorBean
        );
    }

    private int extractPriority(Class<?> decoratorClass) {
        if (decoratorClass == null) {
            return Integer.MAX_VALUE;
        }
        Integer priority = AnnotationExtractors.getPriorityValue(decoratorClass);
        return priority != null ? priority : Integer.MAX_VALUE;
    }

    private static final class SyntheticDelegateInjectionPoint implements InjectionPoint {
        private final Type type;
        private final Set<Annotation> qualifiers;
        private final Bean<?> bean;

        private SyntheticDelegateInjectionPoint(Type type, Set<Annotation> qualifiers, Bean<?> bean) {
            this.type = type;
            this.qualifiers = Collections.unmodifiableSet(new LinkedHashSet<>(qualifiers));
            this.bean = bean;
        }

        @Override
        public Type getType() {
            return type;
        }

        @Override
        public Set<Annotation> getQualifiers() {
            return qualifiers;
        }

        @Override
        public Bean<?> getBean() {
            return bean;
        }

        @Override
        public Member getMember() {
            return null;
        }

        @Override
        public jakarta.enterprise.inject.spi.Annotated getAnnotated() {
            return null;
        }

        @Override
        public boolean isDelegate() {
            return true;
        }

        @Override
        public boolean isTransient() {
            return false;
        }
    }

    /**
     * Comparator implementing CDI ordering rules:
     * 1) Decorators enabled with @Priority come first, ordered by @Priority ascending.
     * 2) Decorators enabled only via beans.xml come next, in beans.xml declaration order.
     * 3) Tiebreaker: class name for determinism.
     */
    private Comparator<DecoratorInfo> decoratorOrderingComparator() {
        if (knowledgeBase.hasAfterTypeDiscoveryDecoratorsCustomized()) {
            return Comparator
                    .comparingInt((DecoratorInfo d) -> {
                        int appOrder = knowledgeBase.getApplicationDecoratorOrder(d.getDecoratorClass());
                        return appOrder >= 0 ? 0 : 1;
                    })
                    .thenComparingInt(d -> {
                        int appOrder = knowledgeBase.getApplicationDecoratorOrder(d.getDecoratorClass());
                        if (appOrder >= 0) {
                            return appOrder;
                        }
                        int beansXmlOrder = knowledgeBase.getDecoratorBeansXmlOrder(d.getDecoratorClass());
                        return beansXmlOrder >= 0 ? beansXmlOrder : Integer.MAX_VALUE;
                    })
                    .thenComparing(di -> di.getDecoratorClass().getName());
        }

        return Comparator
            .comparingInt((DecoratorInfo d) -> d.getPriority() != Integer.MAX_VALUE ? 0 : 1)
            .thenComparingInt(DecoratorInfo::getPriority)
            .thenComparingInt(d -> {
                int appOrder = knowledgeBase.getApplicationDecoratorOrder(d.getDecoratorClass());
                if (appOrder >= 0) {
                    return appOrder;
                }
                int order = knowledgeBase.getDecoratorBeansXmlOrder(d.getDecoratorClass());
                return order >= 0 ? order : Integer.MAX_VALUE;
            })
            .thenComparing(di -> di.getDecoratorClass().getName());
    }

    /**
     * A decorator is enabled if it is listed in any beans.xml OR has @Priority.
     */
    private boolean isEnabled(DecoratorInfo decorator) {
        if (knowledgeBase.hasAfterTypeDiscoveryDecoratorsCustomized()) {
            return knowledgeBase.getApplicationDecoratorOrder(decorator.getDecoratorClass()) >= 0
                    || knowledgeBase.getDecoratorBeansXmlOrder(decorator.getDecoratorClass()) >= 0;
        }
        int beansXmlOrder = knowledgeBase.getDecoratorBeansXmlOrder(decorator.getDecoratorClass());
        return beansXmlOrder >= 0 || decorator.getPriority() != Integer.MAX_VALUE;
    }

    /**
     * Checks if a decorator matches any of the bean's types.
     *
     * <p>A decorator matches if ANY of its decorated types are compatible with ANY of the bean's types.
     * Type compatibility is checked using Class.isAssignableFrom().
     *
     * <p><b>Examples:</b>
     * <ul>
     *   <li>Bean implements PaymentProcessor → Decorator decorates PaymentProcessor → MATCH</li>
     *   <li>Bean implements CreditCardProcessor extends PaymentProcessor → Decorator decorates PaymentProcessor → MATCH</li>
     *   <li>Bean implements OrderService → Decorator decorates PaymentProcessor → NO MATCH</li>
     * </ul>
     *
     * @param decorator the decorator to check
     * @param beanTypes the bean's types
     * @return true if the decorator matches any bean type
     */
    private boolean matchesDelegateType(DecoratorInfo decorator, Set<Type> beanTypes) {
        Type delegateType = decorator.getDelegateInjectionPoint().getType();
        for (Type beanType : beanTypes) {
            if (isBeanTypeAssignableToDelegateType(beanType, delegateType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if two types are compatible for decorator matching.
     *
     * <p>Type compatibility rules:
     * <ul>
     *   <li>If both are Class objects, use isAssignableFrom()</li>
     *   <li>Otherwise, use simple equality (for generic types)</li>
     * </ul>
     *
     * @param delegateType the type that the decorator decorates
     * @param beanType the bean's type
     * @return true if the decorator can decorate the bean
     */
    private boolean isBeanTypeAssignableToDelegateType(Type beanType, Type delegateType) {
        if (beanType == null || delegateType == null) {
            return false;
        }

        Class<?> beanRaw = safeRawType(beanType);
        Class<?> delegateRaw = safeRawType(delegateType);
        if (delegateRaw == null || !delegateRaw.equals(beanRaw)) {
            return false;
        }

        // Raw bean type assignable to parameterized delegate type only for Object/unbounded type variables.
        if (beanType instanceof Class && delegateType instanceof ParameterizedType) {
            for (Type delegateArg : ((ParameterizedType) delegateType).getActualTypeArguments()) {
                if (!isObjectOrUnboundedTypeVariable(delegateArg)) {
                    return false;
                }
            }
            return true;
        }

        if (beanType instanceof ParameterizedType && delegateType instanceof ParameterizedType) {
            Type[] beanArgs = ((ParameterizedType) beanType).getActualTypeArguments();
            Type[] delegateArgs = ((ParameterizedType) delegateType).getActualTypeArguments();
            if (beanArgs.length != delegateArgs.length) {
                return false;
            }
            for (int i = 0; i < beanArgs.length; i++) {
                if (!matchesDelegateParameter(beanArgs[i], delegateArgs[i])) {
                    return false;
                }
            }
            return true;
        }

        // Raw/raw falls back to identical raw types check done above.
        if (beanType instanceof Class && delegateType instanceof Class) {
            return true;
        }

        return beanType.equals(delegateType);
    }

    private boolean matchesDelegateParameter(Type beanParam, Type delegateParam) {
        // both actual types
        if (isActualType(beanParam) && isActualType(delegateParam)) {
            Class<?> beanRaw = safeRawType(beanParam);
            Class<?> delegateRaw = safeRawType(delegateParam);
            if (beanRaw == null || !beanRaw.equals(delegateRaw)) {
                return false;
            }
            if (beanParam instanceof ParameterizedType && delegateParam instanceof ParameterizedType) {
                return isBeanTypeAssignableToDelegateType(beanParam, delegateParam);
            }
            return true;
        }

        // delegate wildcard + bean actual
        if (delegateParam instanceof WildcardType && isActualType(beanParam)) {
            return wildcardMatches((WildcardType) delegateParam, beanParam);
        }

        // delegate wildcard + bean type variable
        if (delegateParam instanceof WildcardType && beanParam instanceof TypeVariable<?>) {
            Type beanUpperBound = firstUpperBound((TypeVariable<?>) beanParam);
            return wildcardMatches((WildcardType) delegateParam, beanUpperBound);
        }

        // both type variables
        if (delegateParam instanceof TypeVariable<?> && beanParam instanceof TypeVariable<?>) {
            Type delegateUpper = firstUpperBound((TypeVariable<?>) delegateParam);
            Type beanUpper = firstUpperBound((TypeVariable<?>) beanParam);
            return isAssignable(beanUpper, delegateUpper);
        }

        // delegate type variable + bean actual
        if (delegateParam instanceof TypeVariable<?> && isActualType(beanParam)) {
            Type delegateUpper = firstUpperBound((TypeVariable<?>) delegateParam);
            return isAssignable(beanParam, delegateUpper);
        }

        return false;
    }

    private boolean wildcardMatches(WildcardType wildcard, Type candidate) {
        for (Type upper : wildcard.getUpperBounds()) {
            if (!Object.class.equals(upper) && !isAssignable(candidate, upper)) {
                return false;
            }
        }
        for (Type lower : wildcard.getLowerBounds()) {
            if (!isAssignable(lower, candidate)) {
                return false;
            }
        }
        return true;
    }

    private boolean isAssignable(Type from, Type to) {
        Class<?> fromRaw = safeRawType(from);
        Class<?> toRaw = safeRawType(to);
        return fromRaw != null && toRaw != null && toRaw.isAssignableFrom(fromRaw);
    }

    private Type firstUpperBound(TypeVariable<?> variable) {
        Type[] bounds = variable.getBounds();
        return bounds.length == 0 ? Object.class : bounds[0];
    }

    private boolean isActualType(Type type) {
        return type instanceof Class<?> || type instanceof ParameterizedType;
    }

    private boolean isObjectOrUnboundedTypeVariable(Type type) {
        if (Object.class.equals(type)) {
            return true;
        }
        if (type instanceof TypeVariable<?>) {
            TypeVariable<?> tv = (TypeVariable<?>) type;
            Type[] bounds = tv.getBounds();
            return bounds.length == 0 || (bounds.length == 1 && Object.class.equals(bounds[0]));
        }
        return false;
    }

    private Class<?> safeRawType(Type type) {
        try {
            return RawTypeExtractor.getRawType(type);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Checks if a decorator matches the bean's qualifiers per CDI 4.1.
     *
     * <p>Qualifier matching uses the qualifiers on the decorator's single {@code @Delegate}
     * injection point. A decorator applies only if the bean has all qualifiers declared on
     * that delegate injection point (including {@code @Named} value matching). {@code @Any}
     * is ignored; {@code @Default} is assumed when no qualifier is present.</p>
     *
     * @param decorator the decorator to check
     * @param qualifiers the bean's qualifiers
     * @return true if the bean's qualifiers satisfy the delegate's qualifier set
     */
    private boolean matchesQualifiers(DecoratorInfo decorator, Set<Annotation> qualifiers) {
        Set<Annotation> required = normalizeQualifiers(decorator.getDelegateInjectionPoint().getQualifiers());
        Set<Annotation> available = normalizeQualifiers(qualifiers);
        return qualifiersMatch(required, available);
    }

    /**
     * Checks if any decorators would apply to a bean with the given types.
     *
     * <p>This is a convenience method for checking if decorator resolution is needed.
     *
     * @param beanTypes the bean's types
     * @param qualifiers the bean's qualifiers
     * @return true if at least one decorator applies
     */
    public boolean hasDecorators(Set<Type> beanTypes, Set<Annotation> qualifiers) {
        return !resolve(beanTypes, qualifiers).isEmpty();
    }
}
