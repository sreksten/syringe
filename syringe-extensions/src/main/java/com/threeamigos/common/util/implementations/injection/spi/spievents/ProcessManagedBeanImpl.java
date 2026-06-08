package com.threeamigos.common.util.implementations.injection.spi.spievents;

import com.threeamigos.common.util.implementations.injection.discovery.NonPortableBehaviourException;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.injection.spi.Phase;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.inject.spi.*;
import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.invoke.Invoker;
import jakarta.enterprise.invoke.InvokerBuilder;
import jakarta.interceptor.Interceptor;
import jakarta.enterprise.context.spi.CreationalContext;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.CompletionStage;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.hasBuiltInNormalScopeAnnotation;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.hasDependentAnnotation;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.hasDecoratorAnnotation;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.hasInterceptorAnnotation;
import static com.threeamigos.common.util.implementations.injection.util.TypesHelper.getRawType;

/**
 * ProcessManagedBean event implementation.
 *
 * <p>Fired for each discovered managed bean (non-producer). Extensions can
 * inspect or veto via {@link #addDefinitionError(Throwable)}.</p>
 *
 * @param <T> bean type
 */
public class ProcessManagedBeanImpl<T> extends ProcessBeanImpl<T> implements ProcessManagedBean<T> {

    private final AnnotatedType<T> annotatedType;
    private final BeanManager beanManager;

    public ProcessManagedBeanImpl(MessageHandler messageHandler, KnowledgeBase knowledgeBase, Bean<T> bean,
                                  AnnotatedType<T> annotatedType, BeanManager beanManager) {
        super(messageHandler, knowledgeBase, bean, annotatedType);
        this.annotatedType = annotatedType;
        this.beanManager = beanManager;
    }

    @Override
    public AnnotatedType<T> getAnnotatedBeanClass() {
        return annotatedType;
    }

    @Override
    public void addDefinitionError(Throwable t) {
        assertObserverInvocationActive();
        knowledgeBase.addDefinitionError(Phase.PROCESS_MANAGED_BEAN, "Definition error for " +
                bean.getBeanClass().getName(), t);
    }
    @Override
    public InvokerBuilder<Invoker<T, ?>> createInvoker(AnnotatedMethod<? super T> method) {
        checkNotNull(method, "AnnotatedMethod");
        Method javaMethod = method.getJavaMember();
        validateTargetBean();
        validateTargetMethod(javaMethod);
        validateNonPortableTargetMethod(javaMethod);
        return new SimpleInvokerBuilder<>(javaMethod, bean.getBeanClass(), beanManager);
    }

    /**
     * Minimal InvokerBuilder that reflects directly on the underlying Java Method.
     * withInstanceLookup/withArgumentLookup are no-ops for this simple implementation.
     */
    private void validateTargetBean() {
        Class<?> beanClass = bean.getBeanClass();
        if (hasInterceptorAnnotation(beanClass) ||
            bean instanceof Interceptor) {
            throw new DefinitionException("Cannot build invoker for interceptor bean: " + beanClass.getName());
        }
        if (hasDecoratorAnnotation(beanClass) ||
            bean instanceof Decorator) {
            throw new DefinitionException("Cannot build invoker for decorator bean: " + beanClass.getName());
        }
    }

    private void validateTargetMethod(Method javaMethod) {
        int modifiers = javaMethod.getModifiers();
        if (Modifier.isPrivate(modifiers)) {
            throw new DefinitionException("Cannot build invoker for private method: " + javaMethod);
        }

        if (javaMethod.getDeclaringClass().equals(Object.class) &&
            !"toString".equals(javaMethod.getName())) {
            throw new DefinitionException("Cannot build invoker for java.lang.Object method: " + javaMethod);
        }

        if (!javaMethod.getDeclaringClass().isAssignableFrom(bean.getBeanClass())) {
            throw new DefinitionException(
                "Target method is not declared on bean class or inherited from supertypes: " + javaMethod);
        }
    }

    private void validateNonPortableTargetMethod(Method javaMethod) {
        if (Modifier.isStatic(javaMethod.getModifiers())) {
            return;
        }

        Class<?> declaringClass = javaMethod.getDeclaringClass();
        if (!isDeclaringTypePresentInBeanTypes(declaringClass, bean.getTypes())) {
            throw new NonPortableBehaviourException(
                "Building invoker for non-static method declared on type not present in bean types is non-portable: " +
                    declaringClass.getName());
        }

        if (isNormalScope(bean.getScope())) {
            String reason = unproxyableReason(declaringClass);
            if (reason != null) {
                throw new NonPortableBehaviourException(
                    "Building invoker for non-static method declared on unproxyable bean type of normal-scoped bean is non-portable: " +
                        declaringClass.getName() + " (" + reason + ")");
            }
        }
    }

    private boolean isDeclaringTypePresentInBeanTypes(Class<?> declaringClass, Set<Type> beanTypes) {
        for (Type beanType : beanTypes) {
            Class<?> raw = getRawType(beanType);
            if (raw != null && raw.equals(declaringClass)) {
                return true;
            }
        }
        return false;
    }

    private boolean isNormalScope(Class<? extends Annotation> scope) {
        return scope != null && hasBuiltInNormalScopeAnnotation(scope);
    }

    private String unproxyableReason(Class<?> rawType) {
        if (rawType == null || rawType.equals(Object.class) || rawType.isInterface()) {
            return null;
        }
        if (rawType.isPrimitive()) {
            return "primitive type";
        }
        if (rawType.isArray()) {
            return "array type";
        }
        if (Modifier.isFinal(rawType.getModifiers())) {
            return "final class";
        }
        if (!hasNonPrivateNoArgConstructor(rawType)) {
            return "missing non-private no-arg constructor";
        }
        Method finalBusinessMethod = findNonStaticFinalNonPrivateMethod(rawType);
        if (finalBusinessMethod != null) {
            return "has non-static final method with non-private visibility: " + finalBusinessMethod.getName();
        }
        return null;
    }

    private boolean hasNonPrivateNoArgConstructor(Class<?> type) {
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == 0 && !Modifier.isPrivate(constructor.getModifiers())) {
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

    private static class SimpleInvokerBuilder<T> implements InvokerBuilder<Invoker<T, ?>> {
        private final Method javaMethod;
        private final Class<?> targetBeanClass;
        private final BeanManager beanManager;
        private boolean instanceLookup;
        private final Set<Integer> argumentLookups = new LinkedHashSet<>();

        SimpleInvokerBuilder(Method javaMethod, Class<?> targetBeanClass, BeanManager beanManager) {
            this.javaMethod = javaMethod;
            this.targetBeanClass = targetBeanClass;
            this.beanManager = beanManager;
        }

        @Override
        public InvokerBuilder<Invoker<T, ?>> withInstanceLookup() {
            ensureNotAsyncTargetMethod();
            this.instanceLookup = true;
            return this;
        }

        @Override
        public InvokerBuilder<Invoker<T, ?>> withArgumentLookup(int position) {
            ensureNotAsyncTargetMethod();
            int paramCount = javaMethod.getParameterCount();
            if (position < 0 || position >= paramCount) {
                throw new IllegalArgumentException(
                    "withArgumentLookup position must be in [0, " + (paramCount - 1) + "] for " + javaMethod);
            }
            this.argumentLookups.add(position);
            return this;
        }

        @Override
        public Invoker<T, ?> build() {
            final Bean<?> instanceBean = resolveInstanceLookupBeanIfNeeded();
            final List<LookupPlan> lookupPlans = resolveArgumentLookupBeans();
            return (instance, parameters) -> {
                LookupContext lookupContext = new LookupContext(beanManager);
                try {
                    if (!Modifier.isStatic(javaMethod.getModifiers())) {
                        if (instanceLookup) {
                            instance = (T) lookupContext.lookup(instanceBean, javaMethod.getDeclaringClass());
                        }
                        if (instance == null) {
                            throw new IllegalArgumentException("Invoker requires non-null instance for non-static method: " + javaMethod);
                        }
                        if (!targetBeanClass.isInstance(instance)) {
                            throw new IllegalArgumentException(
                                "Invoker built for bean " + targetBeanClass.getName() +
                                " cannot be used with instance of " + instance.getClass().getName());
                        }
                    }
                    Object[] invocationArgs = adaptArguments(javaMethod, parameters);
                    applyArgumentLookups(invocationArgs, lookupPlans, lookupContext);
                    if (!javaMethod.isAccessible()) {
                        javaMethod.setAccessible(true);
                    }
                    return javaMethod.invoke(instance, invocationArgs);
                } catch (InvocationTargetException e) {
                    Throwable target = e.getTargetException();
                    while (target instanceof InvocationTargetException
                            && ((InvocationTargetException) target).getTargetException() != null) {
                        target = ((InvocationTargetException) target).getTargetException();
                    }
                    if (target instanceof Exception) {
                        throw (Exception) target;
                    }
                    throw new RuntimeException(target);
                } finally {
                    lookupContext.destroyDependents();
                }
            };
        }

        private Object[] adaptArguments(Method targetMethod, Object[] providedArguments) {
            Class<?>[] parameterTypes = targetMethod.getParameterTypes();
            int declaredParamCount = parameterTypes.length;

            if (declaredParamCount == 0) {
                return new Object[0];
            }

            if (providedArguments == null) {
                throw new RuntimeException("Arguments cannot be null for method with parameters: " + targetMethod);
            }

            if (providedArguments.length < declaredParamCount) {
                throw new RuntimeException(
                    "Not enough arguments for method " + targetMethod + ": expected " +
                        declaredParamCount + " but got " + providedArguments.length);
            }

            Object[] effectiveArgs = new Object[declaredParamCount];
            System.arraycopy(providedArguments, 0, effectiveArgs, 0, declaredParamCount);
            return effectiveArgs;
        }

        private void applyArgumentLookups(Object[] invocationArgs, List<LookupPlan> plans, LookupContext lookupContext) {
            for (LookupPlan plan : plans) {
                invocationArgs[plan.position] = lookupContext.lookup(plan.bean, plan.requiredType);
            }
        }

        private Bean<?> resolveInstanceLookupBeanIfNeeded() {
            if (!instanceLookup || Modifier.isStatic(javaMethod.getModifiers())) {
                return null;
            }
            if (beanManager == null) {
                throw new DefinitionException("Invoker withInstanceLookup requires BeanManager");
            }
            return resolveUniqueBean(javaMethod.getDeclaringClass(), new Annotation[0]);
        }

        private List<LookupPlan> resolveArgumentLookupBeans() {
            List<LookupPlan> plans = new ArrayList<>();
            if (argumentLookups.isEmpty()) {
                return plans;
            }
            if (beanManager == null) {
                throw new DefinitionException("Invoker withArgumentLookup requires BeanManager");
            }
            Parameter[] parameters = javaMethod.getParameters();
            for (Integer position : argumentLookups) {
                Parameter parameter = parameters[position];
                Annotation[] qualifiers = extractQualifiers(parameter.getAnnotations());
                Bean<?> lookedUpBean = resolveUniqueBean(parameter.getParameterizedType(), qualifiers);
                plans.add(new LookupPlan(position, parameter.getParameterizedType(), lookedUpBean));
            }
            return plans;
        }

        private Annotation[] extractQualifiers(Annotation[] annotations) {
            List<Annotation> qualifiers = new ArrayList<>();
            for (Annotation annotation : annotations) {
                if (beanManager.isQualifier(annotation.annotationType())) {
                    qualifiers.add(annotation);
                }
            }
            return qualifiers.toArray(new Annotation[0]);
        }

        private Bean<?> resolveUniqueBean(Type requiredType, Annotation[] qualifiers) {
            Set<Bean<?>> beans = lookupBeans(requiredType, qualifiers);
            if (beans == null || beans.isEmpty()) {
                throw new DefinitionException("Unsatisfied looked up bean for type " + requiredType);
            }
            Bean<?> resolved;
            try {
                resolved = resolveBean(beans);
            } catch (AmbiguousResolutionException e) {
                throw new DefinitionException("Ambiguous looked up bean for type " + requiredType, e);
            }
            if (resolved == null) {
                throw new DefinitionException("Ambiguous looked up bean for type " + requiredType);
            }
            return resolved;
        }

        private Set<Bean<?>> lookupBeans(Type requiredType, Annotation[] qualifiers) {
            if (beanManager instanceof BeanManagerImpl) {
                return ((BeanManagerImpl) beanManager).getBeansForInvokerLookup(requiredType, qualifiers);
            }
            return beanManager.getBeans(requiredType, qualifiers);
        }

        private Bean<?> resolveBean(Set<Bean<?>> beans) {
            if (beanManager instanceof BeanManagerImpl) {
                return ((BeanManagerImpl) beanManager).resolveForInvokerLookup(beans);
            }
            return beanManager.resolve(beans);
        }

        private void ensureNotAsyncTargetMethod() {
            Class<?> returnType = javaMethod.getReturnType();
            if (Future.class.isAssignableFrom(returnType) || CompletionStage.class.isAssignableFrom(returnType)) {
                throw new NonPortableBehaviourException(
                    "Invoker lookup configuration for asynchronous target methods is non-portable: " + javaMethod);
            }
        }
    }

    private static final class LookupPlan {
        private final int position;
        private final Type requiredType;
        private final Bean<?> bean;

        private LookupPlan(int position, Type requiredType, Bean<?> bean) {
            this.position = position;
            this.requiredType = requiredType;
            this.bean = bean;
        }
    }

    private static final class LookupContext {
        private final BeanManager beanManager;
        private final List<DependentHandle> dependentHandles = new ArrayList<>();

        private LookupContext(BeanManager beanManager) {
            this.beanManager = beanManager;
        }

        private Object lookup(Bean<?> bean, Type requiredType) {
            @SuppressWarnings("unchecked")
            Bean<Object> typedBean = (Bean<Object>) bean;
            CreationalContext<Object> ctx = beanManager.createCreationalContext(typedBean);
            Object reference = beanManager.getReference(typedBean, requiredType, ctx);
            if (bean.getScope() != null && hasDependentAnnotation(bean.getScope())) {
                dependentHandles.add(new DependentHandle(typedBean, reference, ctx));
            }
            return reference;
        }

        private void destroyDependents() {
            for (DependentHandle handle : dependentHandles) {
                handle.destroy();
            }
            dependentHandles.clear();
        }
    }

    private static final class DependentHandle {
        private final Bean<Object> bean;
        private final Object instance;
        private final CreationalContext<Object> creationalContext;

        private DependentHandle(Bean<Object> bean, Object instance, CreationalContext<Object> creationalContext) {
            this.bean = bean;
            this.instance = instance;
            this.creationalContext = creationalContext;
        }

        private void destroy() {
            bean.destroy(instance, creationalContext);
            creationalContext.release();
        }
    }
}
