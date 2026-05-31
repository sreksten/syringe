package com.threeamigos.common.util.implementations.injection.resolution;

import com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates;

import com.threeamigos.common.util.implementations.injection.scopes.InjectionPointImpl;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Factory for creating Producer instances for fields and methods.
 *
 * <p>This factory creates Producer objects that handle producer method/field invocation
 * and disposer method invocation.
 *
 * <p><b>Usage in Portable Extensions:</b>
 * <pre>{@code
 * public class MyExtension implements Extension {
 *     void processProducer(@Observes ProcessProducer<MyBean, MyProduct> event, BeanManager bm) {
 *         AnnotatedMethod<MyBean> method = event.getAnnotatedProducerMethod();
 *         Bean<MyBean> declaringBean = event.getBean();
 *
 *         ProducerFactory<MyProduct> factory = bm.getProducerFactory(method, declaringBean);
 *         Producer<MyProduct> producer = factory.createProducer(declaringBean);
 *     }
 * }
 * }</pre>
 *
 * @param <X> the produced type
 * @author Stefano Reksten
 */
public class ProducerFactoryImpl<X> implements ProducerFactory<X> {

    private final AnnotatedMember<?> producerMember;
    private final BeanManager beanManager;

    /**
     * Creates a producer factory for a field.
     *
     * @param field the producer field
     * @param beanManager the bean manager
     */
    public ProducerFactoryImpl(AnnotatedField<?> field, BeanManager beanManager) {
        this.producerMember = field;
        this.beanManager = beanManager;
    }

    /**
     * Creates a producer factory for a method.
     *
     * @param method the producer method
     * @param beanManager the bean manager
     */
    public ProducerFactoryImpl(AnnotatedMethod<?> method, BeanManager beanManager) {
        this.producerMember = method;
        this.beanManager = beanManager;
    }

    /**
     * Creates a Producer for the configured producer field/method.
     *
     * <p>The Producer handles:
     * <ul>
     *   <li>Producing instances by invoking the producer method/field</li>
     *   <li>Disposing instances by invoking the disposer method (if present)</li>
     *   <li>Tracking injection points for parameters</li>
     * </ul>
     *
     * @param bean the declaring bean
     * @param <T> the bean type
     * @return the producer
     */
    @Override
    public <T> Producer<T> createProducer(Bean<T> bean) {
        if (producerMember instanceof AnnotatedField) {
            return new FieldProducerImpl<>((AnnotatedField<?>) producerMember, bean, beanManager);
        } else if (producerMember instanceof AnnotatedMethod) {
            return new MethodProducerImpl<>((AnnotatedMethod<?>) producerMember, bean, beanManager);
        } else {
            throw new IllegalStateException("Producer member must be field or method");
        }
    }

    /**
     * Producer implementation for producer fields.
     */
    private static class FieldProducerImpl<T> implements Producer<T> {
        private final AnnotatedField<?> field;
        private final Bean<?> declaringBean;
        private final BeanManager beanManager;

        public FieldProducerImpl(AnnotatedField<?> field, Bean<?> declaringBean, BeanManager beanManager) {
            this.field = field;
            this.declaringBean = declaringBean;
            this.beanManager = beanManager;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T produce(CreationalContext<T> ctx) {
            try {
                Field javaField = field.getJavaMember();
                javaField.setAccessible(true);
                Object declaringInstance = resolveDeclaringInstance(
                        javaField.getDeclaringClass(),
                        declaringBean,
                        beanManager,
                        ctx,
                        Modifier.isStatic(javaField.getModifiers())
                );
                return (T) javaField.get(declaringInstance);

            } catch (Exception e) {
                throw new RuntimeException("Failed to produce from field " + field.getJavaMember().getName(), e);
            }
        }

        @Override
        public void dispose(T instance) {
            invokeDisposerIfPresent(instance, field.getJavaMember().getType(), declaringBean, beanManager);
        }

        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            // Producer fields have no injection points
            return Collections.emptySet();
        }
    }

    /**
     * Producer implementation for producer methods.
     */
    private static class MethodProducerImpl<T> implements Producer<T> {
        private final AnnotatedMethod<?> method;
        private final Bean<?> declaringBean;
        private final BeanManager beanManager;
        private final Set<InjectionPoint> injectionPoints;

        public MethodProducerImpl(AnnotatedMethod<?> method, Bean<?> declaringBean, BeanManager beanManager) {
            this.method = method;
            this.declaringBean = declaringBean;
            this.beanManager = beanManager;
            this.injectionPoints = discoverInjectionPoints();
        }

        @SuppressWarnings("unchecked")
        @Override
        public T produce(CreationalContext<T> ctx) {
            try {
                Method javaMethod = method.getJavaMember();
                javaMethod.setAccessible(true);
                Object declaringInstance = resolveDeclaringInstance(
                        javaMethod.getDeclaringClass(),
                        declaringBean,
                        beanManager,
                        ctx,
                        Modifier.isStatic(javaMethod.getModifiers())
                );
                Object[] args = resolveMethodParameters(javaMethod, ctx);

                return (T) javaMethod.invoke(declaringInstance, args);

            } catch (Exception e) {
                throw new RuntimeException("Failed to produce from method " + method.getJavaMember().getName(), e);
            }
        }

        @Override
        public void dispose(T instance) {
            invokeDisposerIfPresent(instance, method.getJavaMember().getReturnType(), declaringBean, beanManager);
        }

        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            return injectionPoints;
        }

        private Set<InjectionPoint> discoverInjectionPoints() {
            Set<InjectionPoint> points = new HashSet<>();
            Method javaMethod = method.getJavaMember();

            for (Parameter param : javaMethod.getParameters()) {
                points.add(new InjectionPointImpl<>(param, declaringBean));
            }

            return points;
        }

        private Object[] resolveMethodParameters(Method javaMethod, CreationalContext<T> ctx) {
            Parameter[] params = javaMethod.getParameters();
            Object[] args = new Object[params.length];

            for (int i = 0; i < params.length; i++) {
                InjectionPoint ip = new InjectionPointImpl<>(params[i], declaringBean);
                args[i] = beanManager.getInjectableReference(ip, ctx);
            }

            return args;
        }
    }

    private static <T> void invokeDisposerIfPresent(
            T instance,
            Class<?> producedType,
            Bean<?> declaringBean,
            BeanManager beanManager
    ) {
        if (instance == null || declaringBean == null) {
            return;
        }

        Method disposer = findDisposerMethod(declaringBean.getBeanClass(), producedType);
        if (disposer == null) {
            return;
        }

        try {
            CreationalContext<?> declaringContext = beanManager.createCreationalContext(declaringBean);
            Object declaringInstance = beanManager.getReference(
                    declaringBean,
                    declaringBean.getBeanClass(),
                    declaringContext
            );

            Object[] args = resolveDisposerArguments(disposer, instance, declaringBean, beanManager, declaringContext);
            disposer.setAccessible(true);
            disposer.invoke(declaringInstance, args);
        } catch (Exception e) {
            throw new RuntimeException("Failed to dispose produced instance of type " + producedType.getName(), e);
        }
    }

    private static Object resolveDeclaringInstance(
            Class<?> declaringClass,
            Bean<?> declaringBean,
            BeanManager beanManager,
            CreationalContext<?> creationalContext,
            boolean staticMember
    ) throws Exception {
        if (staticMember) {
            return null;
        }
        if (declaringBean != null) {
            return beanManager.getReference(
                    declaringBean,
                    declaringBean.getBeanClass(),
                    creationalContext
            );
        }

        java.lang.reflect.Constructor<?> ctor = declaringClass.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static Method findDisposerMethod(Class<?> beanClass, Class<?> producedType) {
        Method exact = null;
        Method assignable = null;

        Class<?> current = beanClass;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                int disposerIndex = findDisposerParameterIndex(method.getParameters());
                if (disposerIndex < 0) {
                    continue;
                }

                Class<?> disposerType = method.getParameterTypes()[disposerIndex];
                if (disposerType.equals(producedType)) {
                    exact = method;
                    break;
                }
                if (disposerType.isAssignableFrom(producedType) && assignable == null) {
                    assignable = method;
                }
            }
            if (exact != null) {
                break;
            }
            current = current.getSuperclass();
        }

        return exact != null ? exact : assignable;
    }

    private static int findDisposerParameterIndex(Parameter[] parameters) {
        for (int i = 0; i < parameters.length; i++) {
            if (AnnotationPredicates.hasDisposesAnnotation(parameters[i])) {
                return i;
            }
        }
        return -1;
    }

    private static Object[] resolveDisposerArguments(
            Method disposerMethod,
            Object disposedInstance,
            Bean<?> declaringBean,
            BeanManager beanManager,
            CreationalContext<?> creationalContext
    ) {
        Parameter[] parameters = disposerMethod.getParameters();
        Object[] args = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            if (AnnotationPredicates.hasDisposesAnnotation(parameter)) {
                args[i] = disposedInstance;
            } else {
                InjectionPoint ip = new InjectionPointImpl<>(parameter, declaringBean);
                args[i] = beanManager.getInjectableReference(ip, creationalContext);
            }
        }

        return args;
    }
}
