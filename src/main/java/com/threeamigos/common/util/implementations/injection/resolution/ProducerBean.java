package com.threeamigos.common.util.implementations.injection.resolution;

import com.threeamigos.common.util.implementations.injection.annotations.AnnotationExtractors;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsEnum.*;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.*;

import com.threeamigos.common.util.implementations.injection.util.LifecycleMethodHelper;
import com.threeamigos.common.util.implementations.injection.scopes.InjectionPointImpl;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.CreationException;
import jakarta.enterprise.inject.IllegalProductException;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InjectionPoint;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of Bean for producer methods and producer fields.
 * Producer beans are not directly instantiated - they are created by invoking
 * a @Produces method or accessing a @Produces field on a declaring bean instance.
 *
 * @param <T> the type produced by this producer
 * @author Stefano Reksten
 */
public class ProducerBean<T> implements Bean<T> {

    // The class that declares the producer method/field
    private final Class<?> declaringClass;

    // Either producerMethod OR producerField will be set, not both
    private final Method producerMethod;
    private final Field producerField;

    // The disposer method, if any (only for producer methods)
    private Method disposerMethod;
    private final Set<Integer> disposerParameterPositions = new HashSet<>();

    // BeanAttributes
    private String name;
    private final Set<Annotation> qualifiers = new HashSet<>();
    private Class<? extends Annotation> scope;
    private final Set<Class<? extends Annotation>> stereotypes = new HashSet<>();
    private final Set<Type> types = new HashSet<>();
    private boolean alternative;
    private boolean alternativeEnabled;
    private Integer priority; // @Priority value when the alternative is enabled

    // Injection points (for producer method parameters)
    private final Set<InjectionPoint> injectionPoints = new HashSet<>();
    private final Map<Object, List<TrackedDependentArgument>> producerMethodDependentArguments =
            Collections.synchronizedMap(new IdentityHashMap<>());

    // Extension veto state
    private boolean vetoed = false;

    // Reference to dependency resolver (will be set during initialization)
    private DependencyResolver dependencyResolver;

    /**
     * Constructor for producer method bean.
     */
    public ProducerBean(Class<?> declaringClass, Method producerMethod, boolean alternative) {
        this.declaringClass = declaringClass;
        this.producerMethod = producerMethod;
        this.producerField = null;
        this.alternative = alternative;
        this.alternativeEnabled = !alternative;
        this.scope = Dependent.class; // Default scope
    }

    /**
     * Constructor for producer field bean.
     */
    public ProducerBean(Class<?> declaringClass, Field producerField, boolean alternative) {
        this.declaringClass = declaringClass;
        this.producerMethod = null;
        this.producerField = producerField;
        this.alternative = alternative;
        this.alternativeEnabled = !alternative;
        this.scope = Dependent.class; // Default scope
    }

    @Override
    public Class<?> getBeanClass() {
        return declaringClass;
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return Collections.unmodifiableSet(injectionPoints);
    }

    public void addInjectionPoint(InjectionPoint injectionPoint) {
        injectionPoints.add(injectionPoint);
    }

    public void replaceInjectionPoint(InjectionPoint oldIp, InjectionPoint newIp) {
        if (oldIp != null) {
            injectionPoints.remove(oldIp);
        }
        if (newIp != null) {
            injectionPoints.add(newIp);
        }
    }

    @Override
    public String getName() {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        return name;
    }

    public void setName(String name) {
        if (name == null) {
            this.name = null;
            return;
        }
        String trimmed = name.trim();
        this.name = trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    public Set<Annotation> getQualifiers() {
        return Collections.unmodifiableSet(qualifiers);
    }

    public void setQualifiers(Set<Annotation> qualifiers) {
        this.qualifiers.clear();
        if (qualifiers != null) {
            this.qualifiers.addAll(qualifiers);
        }
    }

    public void addQualifier(Annotation qualifier) {
        if (qualifier != null) {
            this.qualifiers.add(qualifier);
        }
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return scope;
    }

    public void setScope(Class<? extends Annotation> scope) {
        this.scope = scope;
    }

    @Override
    public Set<Class<? extends Annotation>> getStereotypes() {
        return Collections.unmodifiableSet(stereotypes);
    }

    public void setStereotypes(Set<Class<? extends Annotation>> stereotypes) {
        this.stereotypes.clear();
        if (stereotypes != null) {
            this.stereotypes.addAll(stereotypes);
        }
    }

    @Override
    public Set<Type> getTypes() {
        return Collections.unmodifiableSet(types);
    }

    public void setTypes(Set<Type> types) {
        this.types.clear();
        if (types != null) {
            this.types.addAll(types);
        }
    }

    @Override
    public boolean isAlternative() {
        return alternative;
    }

    public void setAlternative(boolean alternative) {
        this.alternative = alternative;
    }

    public boolean isAlternativeEnabled() {
        return alternativeEnabled;
    }

    public void setAlternativeEnabled(boolean alternativeEnabled) {
        this.alternativeEnabled = alternativeEnabled;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Integer getPriority() {
        return priority;
    }

    // Accessors are provided later in the class; keep single-source of truth to avoid duplicates

    @Override
    public T create(CreationalContext<T> creationalContext) {
        try {
            if (dependencyResolver == null) {
                throw new IllegalStateException(
                    "ProducerBean dependency resolver not set. " +
                    "This should be set during container initialization."
                );
            }

            // 1. Get or create the declaring bean instance only when required by the producer member
            DeclaringInstanceHandle declaringInstanceHandle = resolveDeclaringInstanceHandle(
                    requiresDeclaringInstanceForProducer()
            );

            // 2. Invoke the producer method or access the producer field
            if (producerMethod != null) {
                T produced = invokeProducerMethod(declaringInstanceHandle);
                validateProducerMethodNullProduct(produced);
                validatePassivationRequirementsForProducedValue(produced);
                return produced;
            } else if (producerField != null) {
                T produced = accessProducerField(declaringInstanceHandle);
                validateProducerFieldNullProduct(produced);
                validatePassivationRequirementsForProducedValue(produced);
                return produced;
            } else {
                throw new IllegalStateException("ProducerBean has neither method nor field");
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            Throwable cause = unwrapInvocationCause(e);
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new CreationException("Failed to create instance from producer", cause);
        }
    }

    @Override
    public void destroy(T instance, CreationalContext<T> creationalContext) {
        if (instance == null || DestroyedInstanceTracker.isDestroyed(instance)) {
            return;
        }
        creationalContext = BeanManagerImpl.resolveDependentCreationalContext(creationalContext, this, instance);

        Throwable ignored = null;
        try {
            // Invoke the disposer method if present
            if (disposerMethod != null) {
                invokeDisposerMethod(instance);
            }
            destroyTrackedProducerMethodDependentArguments(instance);
        } catch (Exception e) {
            ignored = e;
        } finally {
            DestroyedInstanceTracker.markDestroyed(instance);
            try {
                // Release CreationalContext
                if (creationalContext != null) {
                    creationalContext.release();
                }
            } catch (Exception e) {
                //FIXME log something?
            }
        }
    }

    private Throwable unwrapInvocationCause(Exception e) {
        if (e instanceof java.lang.reflect.InvocationTargetException) {
            Throwable target = ((java.lang.reflect.InvocationTargetException) e).getTargetException();
            return target != null ? target : e;
        }
        return e;
    }

    /**
     * Invokes the producer method to create an instance.
     */
    @SuppressWarnings("unchecked")
    private T invokeProducerMethod(DeclaringInstanceHandle declaringInstanceHandle)
            throws Exception {
        Object declaringInstance = declaringInstanceHandle.instance;
        producerMethod.setAccessible(true);

        // Resolve method parameters
        Parameter[] parameters = producerMethod.getParameters();
        Object[] args = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            if (InjectionPoint.class.equals(parameters[i].getType())) {
                args[i] = resolveProducerInjectionPoint(parameters[i]);
            } else {
                args[i] = resolveProducerParameter(parameters[i]);
            }
        }

        T produced = null;
        boolean invocationSucceeded = false;
        try {
            produced = (T) invokeOnRuntimeMethod(declaringInstance, producerMethod, args);
            invocationSucceeded = true;
            return produced;
        } finally {
            if (invocationSucceeded && produced != null) {
                destroyTransientProducerMethodParameters(parameters, args);
                trackDependentProducerParametersForProducedInstance(parameters, args, produced);
            } else {
                destroyDependentInvocationParameters(parameters, args, false);
            }
            destroyDeclaringInstance(declaringInstanceHandle);
        }
    }

    /**
     * Accesses the producer field to get an instance.
     */
    @SuppressWarnings("unchecked")
    private T accessProducerField(DeclaringInstanceHandle declaringInstanceHandle) throws Exception {
        Object declaringInstance = declaringInstanceHandle.instance;
        producerField.setAccessible(true);
        try {
            return (T) producerField.get(declaringInstance);
        } finally {
            destroyDeclaringInstance(declaringInstanceHandle);
        }
    }

    private void validateProducerMethodNullProduct(T produced) {
        if (produced != null || producerMethod == null || isDependentScope(scope)) {
            return;
        }

        String scopeName = (scope == null) ? "<unknown>" : scope.getSimpleName();
        throw new IllegalProductException(
                "Producer method " + producerMethod.getName() +
                " of class " + declaringClass.getName() +
                " returned null but declares non-@Dependent scope @" + scopeName);
    }

    private void validateProducerFieldNullProduct(T produced) {
        if (produced != null || producerField == null || isDependentScope(scope)) {
            return;
        }

        String scopeName = (scope == null) ? "<unknown>" : scope.getSimpleName();
        throw new IllegalProductException(
                "Producer field " + producerField.getName() +
                " of class " + declaringClass.getName() +
                " contains null but declares non-@Dependent scope @" + scopeName);
    }

    private boolean isDependentScope(Class<? extends Annotation> scopeType) {
        return hasDependentAnnotation(scopeType);
    }

    private void validatePassivationRequirementsForProducedValue(T produced) {
        if (produced == null || produced instanceof Serializable) {
            return;
        }

        if (isPassivatingScope(scope)) {
            throw new IllegalProductException(
                    "Producer " + describeProducerMember() +
                            " declares passivating scope @" + scope.getSimpleName() +
                            " but returned non-serializable value of type " + produced.getClass().getName()
            );
        }

        if (isDependentScope(scope) && isPassivationCapableDependencyRequiredAtCurrentInjectionPoint()) {
            throw new IllegalProductException(
                    "Producer " + describeProducerMember() +
                            " declares @Dependent scope and returned non-serializable value of type " +
                            produced.getClass().getName() +
                            " for an injection point that requires a passivation-capable dependency"
            );
        }
    }

    private String describeProducerMember() {
        if (producerMethod != null) {
            return "method " + producerMethod.getName() + " of class " + declaringClass.getName();
        }
        if (producerField != null) {
            return "field " + producerField.getName() + " of class " + declaringClass.getName();
        }
        return "member of class " + declaringClass.getName();
    }

    private boolean isPassivationCapableDependencyRequiredAtCurrentInjectionPoint() {
        if (!(dependencyResolver instanceof BeanResolver)) {
            return false;
        }

        InjectionPoint injectionPoint = ((BeanResolver) dependencyResolver).getCurrentInjectionPoint();
        if (injectionPoint == null) {
            return false;
        }
        if (injectionPoint.isTransient()) {
            return false;
        }
        if (hasTransientReference(injectionPoint)) {
            return false;
        }

        Bean<?> owningBean = injectionPoint.getBean();
        if (owningBean == null) {
            return false;
        }

        return isPassivatingScope(owningBean.getScope());
    }

    private boolean hasTransientReference(InjectionPoint injectionPoint) {
        if (injectionPoint.getAnnotated() == null || injectionPoint.getAnnotated().getAnnotations() == null) {
            return false;
        }
        for (Annotation annotation : injectionPoint.getAnnotated().getAnnotations()) {
            if (hasTransientReferenceAnnotation(annotation.annotationType())) {
                return true;
            }
        }
        return false;
    }

    private boolean isPassivatingScope(Class<? extends Annotation> scopeType) {
        if (scopeType == null) {
            return false;
        }
        Boolean passivating = AnnotationExtractors.getNormalScopePassivatingValue(scopeType);
        if (passivating != null) {
            return passivating;
        }
        return hasBuiltInPassivatingScopeAnnotation(scopeType);
    }

    /**
     * Invokes the disposer method to destroy an instance.
     */
    private void invokeDisposerMethod(T instance) throws Exception {
        if (disposerMethod == null) {
            return;
        }

        disposerMethod.setAccessible(true);

        // Get declaring bean instance
        DeclaringInstanceHandle declaringInstanceHandle = resolveDeclaringInstanceHandle(
                !Modifier.isStatic(disposerMethod.getModifiers())
        );
        Object declaringInstance = declaringInstanceHandle.instance;

        // Resolve disposer method parameters
        Parameter[] parameters = disposerMethod.getParameters();
        Object[] args = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            // The @Disposes parameter gets the instance being disposed
            if (isDisposerParameter(parameters[i])) {
                args[i] = instance;
            } else {
                // Other parameters are normal injection points
                args[i] = resolveDisposerParameter(parameters[i]);
            }
        }

        try {
            invokeOnRuntimeMethod(declaringInstance, disposerMethod, args);
        } finally {
            destroyDependentInvocationParameters(parameters, args, true);
            destroyDeclaringInstance(declaringInstanceHandle);
        }
    }

    private Object invokeOnRuntimeMethod(Object targetInstance, Method method, Object[] args) throws Exception {
        Method invocable = method;
        if (targetInstance != null && !Modifier.isStatic(method.getModifiers())) {
            Method resolved = findMethodInHierarchy(targetInstance.getClass(), method.getName(), method.getParameterTypes());
            if (resolved != null) {
                invocable = resolved;
            }
        }
        invocable.setAccessible(true);
        return invocable.invoke(targetInstance, args);
    }

    private Method findMethodInHierarchy(Class<?> type, String methodName, Class<?>[] parameterTypes) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private void destroyDependentDeclaringInstance(Object declaringInstance) throws Exception {
        if (declaringInstance == null) {
            return;
        }
        if (!isDependentDeclaringClass(declaringInstance.getClass())) {
            return;
        }
        LifecycleMethodHelper.invokeLifecycleMethod(declaringInstance, PRE_DESTROY);
    }

    @SuppressWarnings("unchecked")
    private DeclaringInstanceHandle resolveDeclaringInstanceHandle(boolean requiresDeclaringInstance) {
        if (!requiresDeclaringInstance) {
            if (dependencyResolver instanceof BeanResolver) {
                BeanResolver resolver = (BeanResolver) dependencyResolver;
                BeanManagerImpl beanManager = resolver.getOwningBeanManager();
                if (beanManager != null) {
                    Bean<?> declaringBean = findDeclaringManagedBean(beanManager);
                    if (declaringBean != null && hasDependentAnnotation(declaringBean.getScope())) {
                        return new DeclaringInstanceHandle(null, null, null);
                    }
                }
            }

            Object declaringInstance = dependencyResolver.resolveDeclaringBeanInstance(declaringClass);
            return new DeclaringInstanceHandle(declaringInstance, null, null);
        }

        if (!(dependencyResolver instanceof BeanResolver)) {
            Object declaringInstance = dependencyResolver.resolveDeclaringBeanInstance(declaringClass);
            return new DeclaringInstanceHandle(declaringInstance, null, null);
        }

        BeanResolver resolver = (BeanResolver) dependencyResolver;
        BeanManagerImpl beanManager = resolver.getOwningBeanManager();
        if (beanManager == null) {
            Object declaringInstance = dependencyResolver.resolveDeclaringBeanInstance(declaringClass);
            return new DeclaringInstanceHandle(declaringInstance, null, null);
        }

        Bean<?> declaringBean = findDeclaringManagedBean(beanManager);
        if (declaringBean == null) {
            Object declaringInstance = dependencyResolver.resolveDeclaringBeanInstance(declaringClass);
            return new DeclaringInstanceHandle(declaringInstance, null, null);
        }

        if (hasDependentAnnotation(declaringBean.getScope())) {
            Bean<Object> typedBean = (Bean<Object>) declaringBean;
            CreationalContext<Object> creationalContext = beanManager.createCreationalContext(typedBean);
            Object declaringInstance = typedBean.create(creationalContext);
            return new DeclaringInstanceHandle(declaringInstance, typedBean, creationalContext);
        }

        Object declaringInstance = dependencyResolver.resolveDeclaringBeanInstance(declaringClass);
        return new DeclaringInstanceHandle(declaringInstance, null, null);
    }

    private boolean requiresDeclaringInstanceForProducer() {
        if (producerMethod != null) {
            return !Modifier.isStatic(producerMethod.getModifiers());
        }
        if (producerField != null) {
            return !Modifier.isStatic(producerField.getModifiers());
        }
        return true;
    }

    private Bean<?> findDeclaringManagedBean(BeanManagerImpl beanManager) {
        for (Bean<?> bean : beanManager.getKnowledgeBase().getValidBeans()) {
            if (bean instanceof ProducerBean<?>) {
                continue;
            }
            if (bean.getBeanClass().equals(declaringClass)) {
                return bean;
            }
        }
        return null;
    }

    private void destroyDeclaringInstance(DeclaringInstanceHandle declaringInstanceHandle) throws Exception {
        if (declaringInstanceHandle == null || declaringInstanceHandle.instance == null) {
            return;
        }
        if (declaringInstanceHandle.declaringBean != null && declaringInstanceHandle.creationalContext != null) {
            declaringInstanceHandle.declaringBean.destroy(
                    declaringInstanceHandle.instance,
                    declaringInstanceHandle.creationalContext
            );
            return;
        }
        destroyDependentDeclaringInstance(declaringInstanceHandle.instance);
    }

    private boolean isDependentDeclaringClass(Class<?> type) {
        if (type == null) {
            return false;
        }
        return hasDependentAnnotation(type);
    }

    private void destroyDependentInvocationParameters(Parameter[] parameters, Object[] args, boolean skipDisposesParameter)
            throws Exception {
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Object arg = args[i];
            if (arg == null) {
                continue;
            }
            if (skipDisposesParameter && isDisposerParameter(parameter)) {
                continue;
            }
            if (isNotDependentParameter(parameter)) {
                continue;
            }
            destroyDependentArgument(new TrackedDependentArgument(
                    arg,
                    parameter.getType(),
                    parameter.getAnnotations()
            ));
        }
    }

    private boolean isNotDependentParameter(Parameter parameter) {
        Class<?> parameterType = parameter.getType();
        if (parameterType == null) {
            return true;
        }
        return !hasDependentAnnotation(parameterType);
    }

    private void trackDependentProducerParametersForProducedInstance(
            Parameter[] parameters,
            Object[] args,
            Object produced
    ) {
        List<TrackedDependentArgument> tracked = new ArrayList<>();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Object arg = args[i];
            if (arg == null) {
                continue;
            }
            if (isNotDependentParameter(parameter)) {
                continue;
            }
            if (isTransientReferenceParameter(parameter)) {
                continue;
            }
            tracked.add(new TrackedDependentArgument(
                    arg,
                    parameter.getType(),
                    parameter.getAnnotations()
            ));
        }
        if (!tracked.isEmpty()) {
            producerMethodDependentArguments.put(produced, tracked);
        }
    }

    private void destroyTrackedProducerMethodDependentArguments(Object produced) throws Exception {
        List<TrackedDependentArgument> tracked = producerMethodDependentArguments.remove(produced);
        if (tracked == null || tracked.isEmpty()) {
            return;
        }
        for (TrackedDependentArgument dependent : tracked) {
            if (dependent == null || dependent.instance == null) {
                continue;
            }
            destroyDependentArgument(dependent);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void destroyDependentArgument(TrackedDependentArgument argument) throws Exception {
        if (argument == null || argument.instance == null) {
            return;
        }

        BeanManager beanManager = null;
        if (dependencyResolver instanceof BeanResolver) {
            BeanManagerImpl owningBeanManager = ((BeanResolver) dependencyResolver).getOwningBeanManager();
            if (owningBeanManager != null) {
                if (owningBeanManager.destroyOwnedTransientReference(argument.instance)) {
                    return;
                }
                beanManager = owningBeanManager;
            }
        }
        if (beanManager == null && BeanManagerImpl.destroyTransientReference(argument.instance)) {
            return;
        }
        try {
            if (beanManager == null) {
                beanManager = jakarta.enterprise.inject.spi.CDI.current().getBeanManager();
            }
            Annotation[] qualifiers = extractQualifiers(argument.annotations);
            Set<Bean<?>> beans = beanManager.getBeans(argument.type, qualifiers);
            if (beans != null && !beans.isEmpty()) {
                Bean<?> resolved = beanManager.resolve(beans);
                if (resolved != null && hasDependentAnnotation(resolved.getScope())) {
                    CreationalContext creationalContext = beanManager.createCreationalContext((Bean) resolved);
                    ((Bean) resolved).destroy(argument.instance, creationalContext);
                    return;
                }
            }
        } catch (Throwable ignored) {
            // Fallback below
        }

        LifecycleMethodHelper.invokeLifecycleMethod(argument.instance, PRE_DESTROY);
    }

    private void destroyTransientProducerMethodParameters(Parameter[] parameters, Object[] args) throws Exception {
        for (int i = 0; i < parameters.length; i++) {
            if (i >= args.length) {
                break;
            }
            Parameter parameter = parameters[i];
            Object arg = args[i];
            if (arg == null) {
                continue;
            }
            if (!isTransientReferenceParameter(parameter)) {
                continue;
            }
            if (isNotDependentParameter(parameter)) {
                continue;
            }
            destroyDependentArgument(new TrackedDependentArgument(
                    arg,
                    parameter.getType(),
                    parameter.getAnnotations()
            ));
        }
    }

    private boolean isTransientReferenceParameter(Parameter parameter) {
        if (parameter == null) {
            return false;
        }
        for (Annotation annotation : parameter.getAnnotations()) {
            if (hasTransientReferenceAnnotation(annotation.annotationType())) {
                return true;
            }
        }
        return false;
    }

    private Annotation[] extractQualifiers(Annotation[] annotations) {
        if (annotations == null || annotations.length == 0) {
            return new Annotation[0];
        }

        Set<Annotation> qualifiers = new HashSet<>();
        for (Annotation annotation : annotations) {
            if (annotation == null) {
                continue;
            }
            if (hasQualifierAnnotation(annotation.annotationType())) {
                qualifiers.add(annotation);
            }
        }
        return qualifiers.toArray(new Annotation[0]);
    }

    @SuppressWarnings("rawtypes")
    private static final class DeclaringInstanceHandle {
        private final Object instance;
        private final Bean declaringBean;
        private final CreationalContext creationalContext;

        private DeclaringInstanceHandle(Object instance, Bean declaringBean, CreationalContext creationalContext) {
            this.instance = instance;
            this.declaringBean = declaringBean;
            this.creationalContext = creationalContext;
        }
    }

    private static final class TrackedDependentArgument {
        private final Object instance;
        private final Class<?> type;
        private final Annotation[] annotations;

        private TrackedDependentArgument(Object instance, Class<?> type, Annotation[] annotations) {
            this.instance = instance;
            this.type = type;
            this.annotations = annotations == null ? new Annotation[0] : annotations.clone();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object resolveProducerParameter(Parameter parameter) {
        InjectionPoint registeredInjectionPoint = findRegisteredInjectionPoint(parameter);
        Type requiredType = registeredInjectionPoint != null ? registeredInjectionPoint.getType() : parameter.getParameterizedType();
        Annotation[] qualifiers = registeredInjectionPoint != null
                ? registeredInjectionPoint.getQualifiers().toArray(new Annotation[0])
                : parameter.getAnnotations();

        if (dependencyResolver instanceof BeanResolver) {
            BeanResolver beanResolver = (BeanResolver) dependencyResolver;
            InjectionPoint ip = registeredInjectionPoint != null ? registeredInjectionPoint : new InjectionPointImpl(parameter, this);
            beanResolver.setCurrentInjectionPoint(ip);
            try {
                return dependencyResolver.resolve(requiredType, qualifiers);
            } finally {
                beanResolver.clearCurrentInjectionPoint();
            }
        }

        return dependencyResolver.resolve(requiredType, qualifiers);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private InjectionPoint resolveProducerInjectionPoint(Parameter parameter) {
        if (dependencyResolver instanceof BeanResolver) {
            Object contextual = dependencyResolver.resolve(
                    InjectionPoint.class,
                    parameter == null ? new Annotation[0] : parameter.getAnnotations()
            );
            if (contextual instanceof InjectionPoint) {
                return (InjectionPoint) contextual;
            }
            // No owning injection site: built-in InjectionPoint must be null.
            return null;
        }

        InjectionPoint registeredInjectionPoint = findRegisteredInjectionPoint(parameter);
        if (registeredInjectionPoint != null) {
            return registeredInjectionPoint;
        }

        // Fallback for programmatic producer invocations where no owning injection site exists.
        return new InjectionPointImpl(parameter, this);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object resolveDisposerParameter(Parameter parameter) {
        InjectionPoint registeredInjectionPoint = findRegisteredInjectionPoint(parameter);
        Type requiredType = registeredInjectionPoint != null ? registeredInjectionPoint.getType() : parameter.getParameterizedType();
        Annotation[] qualifiers = registeredInjectionPoint != null
                ? registeredInjectionPoint.getQualifiers().toArray(new Annotation[0])
                : parameter.getAnnotations();

        if (dependencyResolver instanceof BeanResolver) {
            BeanResolver beanResolver = (BeanResolver) dependencyResolver;
            BeanImpl syntheticDeclaringBean = new BeanImpl(declaringClass, false);
            InjectionPoint ip = registeredInjectionPoint != null
                    ? registeredInjectionPoint
                    : new InjectionPointImpl(parameter, syntheticDeclaringBean);
            beanResolver.setCurrentInjectionPoint(ip);
            try {
                return dependencyResolver.resolve(requiredType, qualifiers);
            } finally {
                beanResolver.clearCurrentInjectionPoint();
            }
        }

        return dependencyResolver.resolve(requiredType, qualifiers);
    }

    private InjectionPoint findRegisteredInjectionPoint(Parameter parameter) {
        if (parameter == null) {
            return null;
        }
        int index = parameterIndex(parameter);
        if (index < 0) {
            return null;
        }
        for (InjectionPoint injectionPoint : injectionPoints) {
            if (injectionPoint == null || !parameter.getDeclaringExecutable().equals(injectionPoint.getMember())) {
                continue;
            }
            if (injectionPoint.getAnnotated() instanceof jakarta.enterprise.inject.spi.AnnotatedParameter<?>) {
                jakarta.enterprise.inject.spi.AnnotatedParameter<?> annotatedParameter =
                        (jakarta.enterprise.inject.spi.AnnotatedParameter<?>) injectionPoint.getAnnotated();
                if (annotatedParameter.getPosition() == index) {
                    return injectionPoint;
                }
            }
        }
        return null;
    }

    private int parameterIndex(Parameter parameter) {
        Parameter[] parameters = parameter.getDeclaringExecutable().getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (parameter.equals(parameters[i])) {
                return i;
            }
        }
        return -1;
    }

    // Getters and setters

    public Class<?> getDeclaringClass() {
        return declaringClass;
    }

    public Method getProducerMethod() {
        return producerMethod;
    }

    public Field getProducerField() {
        return producerField;
    }

    public boolean isMethod() {
        return producerMethod != null;
    }

    public boolean isField() {
        return producerField != null;
    }

    public Method getDisposerMethod() {
        return disposerMethod;
    }

    public void setDisposerMethod(Method disposerMethod) {
        this.disposerMethod = disposerMethod;
    }

    public void setDisposerParameterPositions(Set<Integer> positions) {
        disposerParameterPositions.clear();
        if (positions != null) {
            disposerParameterPositions.addAll(positions);
        }
    }

    public boolean hasValidationErrors() {
        return false;
    }

    /**
     * Returns true if this producer bean was vetoed by an extension.
     * Vetoed beans should not be available for injection.
     */
    public boolean isVetoed() {
        return vetoed;
    }

    /**
     * Marks this producer bean as vetoed by an extension.
     */
    public void setVetoed(boolean vetoed) {
        this.vetoed = vetoed;
    }

    public void setDependencyResolver(DependencyResolver dependencyResolver) {
        this.dependencyResolver = dependencyResolver;
    }

    private boolean isDisposerParameter(Parameter parameter) {
        if (parameter == null) {
            return false;
        }

        int index = parameterIndex(parameter);
        if (index >= 0 && disposerParameterPositions.contains(index)) {
            return true;
        }
        return hasDisposesAnnotation(parameter);
    }
}
