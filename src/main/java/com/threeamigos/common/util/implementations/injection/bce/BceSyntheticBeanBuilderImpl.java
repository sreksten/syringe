package com.threeamigos.common.util.implementations.injection.bce;

import com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates;

import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.resolution.BeanResolver;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.injection.spi.SyntheticBean;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.build.compatible.spi.InvokerInfo;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanDisposer;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.types.Type;

import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

final class BceSyntheticBeanBuilderImpl<T> extends BceSyntheticAbstractBuilder implements SyntheticBeanBuilder<T> {

    private final KnowledgeBase knowledgeBase;
    private final BeanManager beanManager;
    private final BceInvokerRegistry invokerRegistry;
    private final Class<T> implementationClass;

    private final Set<java.lang.reflect.Type> types = new LinkedHashSet<>();
    private final Set<Class<? extends Annotation>> stereotypes = new LinkedHashSet<>();
    private Class<? extends Annotation> scope = Dependent.class;
    private boolean alternative;
    private Integer priority;
    private String name;
    private Class<? extends SyntheticBeanCreator<T>> creatorClass;
    private Class<? extends SyntheticBeanDisposer<T>> disposerClass;

    BceSyntheticBeanBuilderImpl(KnowledgeBase knowledgeBase,
                                BeanManager beanManager,
                                BceInvokerRegistry invokerRegistry,
                                Class<T> implementationClass) {
        this.knowledgeBase = knowledgeBase;
        this.beanManager = beanManager;
        this.invokerRegistry = invokerRegistry;
        this.implementationClass = implementationClass;
    }

    @Override
    public SyntheticBeanBuilder<T> type(Class<?> type) {
        if (type != null) {
            this.types.add(type);
        }
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> type(ClassInfo type) {
        if (type != null) {
            this.types.add(BceMetadata.unwrapClassInfo(type));
        }
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> type(Type type) {
        if (type != null) {
            this.types.add(BceMetadata.unwrapType(type));
        }
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> qualifier(Class<? extends Annotation> qualifier) {
        qualifierImpl(qualifier);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> qualifier(AnnotationInfo qualifier) {
        qualifierImpl(qualifier);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> qualifier(Annotation qualifier) {
        qualifierImpl(qualifier);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> scope(Class<? extends Annotation> scope) {
        if (scope != null) {
            this.scope = scope;
        }
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> alternative(boolean alternative) {
        this.alternative = alternative;
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> priority(int priority) {
        this.priority = priority;
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> name(String name) {
        this.name = name;
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> stereotype(Class<? extends Annotation> stereotype) {
        if (stereotype != null) {
            this.stereotypes.add(stereotype);
        }
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> stereotype(ClassInfo stereotype) {
        if (stereotype != null) {
            Class<?> stereotypeClass = BceMetadata.unwrapClassInfo(stereotype);
            if (!Annotation.class.isAssignableFrom(stereotypeClass)) {
                throw new IllegalArgumentException("Stereotype ClassInfo does not represent annotation type: " +
                    stereotypeClass.getName());
            }
            @SuppressWarnings("unchecked")
            Class<? extends Annotation> annType = (Class<? extends Annotation>) stereotypeClass;
            this.stereotypes.add(annType);
        }
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String name, boolean value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String name, boolean[] value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String name, int value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String name, int[] value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String name, long value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String name, long[] value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String name, double value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String name, double[] value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String name, String value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String name, String[] value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String name, Enum<?> value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String name, Enum<?>[] value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String name, Class<?> value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String name, ClassInfo value) {
        withParamInternal(name, value != null ? BceMetadata.unwrapClassInfo(value) : null);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String name, Class<?>[] value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String name, ClassInfo[] value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String name, AnnotationInfo value) {
        withParamInternal(name, value != null ? BceMetadata.unwrapAnnotationInfo(value) : null);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String name, Annotation value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String name, AnnotationInfo[] value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String name, Annotation[] value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String name, InvokerInfo value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String name, InvokerInfo[] value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> createWith(Class<? extends SyntheticBeanCreator<T>> creatorClass) {
        this.creatorClass = creatorClass;
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> disposeWith(Class<? extends SyntheticBeanDisposer<T>> disposerClass) {
        this.disposerClass = disposerClass;
        return this;
    }

    void complete() {
        if (creatorClass == null) {
            throw new IllegalStateException("Synthetic bean creator is required via createWith()");
        }

        final Map<String, Object> frozenParams = Collections.unmodifiableMap(new LinkedHashMap<>(params));
        final Class<? extends SyntheticBeanCreator<T>> frozenCreatorClass = creatorClass;
        final Class<? extends SyntheticBeanDisposer<T>> frozenDisposerClass = disposerClass;
        final boolean fallbackInjectionPointForCreator = Dependent.class.equals(scope);
        final DependentLookupTracker dependentLookupTracker = new DependentLookupTracker();

        Function<CreationalContext<T>, T> createCallback = ctx -> {
            try {
                SyntheticBeanCreator<T> creator = frozenCreatorClass.getDeclaredConstructor().newInstance();
                return creator.create(
                        createLookup(ctx, dependentLookupTracker, fallbackInjectionPointForCreator),
                        new BceParameters(frozenParams, invokerRegistry)
                );
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Failed to instantiate synthetic bean creator " +
                    frozenCreatorClass.getName(), e);
            }
        };

        BiConsumer<T, CreationalContext<T>> destroyCallback = getCreationalContextBiConsumer(
                frozenDisposerClass,
                frozenParams,
                dependentLookupTracker);

        Set<java.lang.reflect.Type> beanTypes = new LinkedHashSet<>(types);
        if (beanTypes.isEmpty()) {
            beanTypes.add(implementationClass);
            beanTypes.add(Object.class);
        }

        Set<Annotation> beanQualifiers = new LinkedHashSet<>(qualifiers);
        if (beanQualifiers.isEmpty()) {
            beanQualifiers.add(Default.Literal.INSTANCE);
            beanQualifiers.add(Any.Literal.INSTANCE);
        }

        SyntheticBean<T> bean = new SyntheticBean<>(
                implementationClass,
                beanTypes,
                beanQualifiers,
                scope,
                name,
                null,
                stereotypes,
                alternative,
                priority,
                createCallback,
                destroyCallback,
                Collections.emptySet()
        );
        knowledgeBase.addBean(bean);
    }

    @Nonnull
    private BiConsumer<T, CreationalContext<T>> getCreationalContextBiConsumer(
            Class<? extends SyntheticBeanDisposer<T>> frozenDisposerClass,
            Map<String, Object> frozenParams,
            DependentLookupTracker dependentLookupTracker) {
        BiConsumer<T, CreationalContext<T>> destroyCallback;
        if (frozenDisposerClass != null) {
            destroyCallback = (instance, ctx) -> {
                try {
                    SyntheticBeanDisposer<T> disposer = frozenDisposerClass.getDeclaredConstructor().newInstance();
                    disposer.dispose(instance, createLookup(ctx, dependentLookupTracker, false),
                        new BceParameters(frozenParams, invokerRegistry));
                    dependentLookupTracker.release(ctx);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException("Failed to instantiate synthetic bean disposer " +
                        frozenDisposerClass.getName(), e);
                }
            };
        } else {
            destroyCallback = (instance, ctx) -> dependentLookupTracker.release(ctx);
        }
        return destroyCallback;
    }

    @SuppressWarnings({"unchecked"})
    private Instance<Object> createLookup(
            CreationalContext<?> parentContext,
            DependentLookupTracker dependentLookupTracker,
            boolean fallbackInjectionPointWhenMissing) {
        return new com.threeamigos.common.util.implementations.injection.resolution.InstanceImpl<>(
                Object.class,
                defaultQualifiers(),
                new com.threeamigos.common.util.implementations.injection.resolution.InstanceImpl.ResolutionStrategy<Object>() {
                    @Override
                    public Object resolveInstance(Class<Object> type, java.util.Collection<Annotation> quals) {
                        if (InjectionPoint.class.equals(type)) {
                            BeanResolver beanResolver = null;
                            if (beanManager instanceof BeanManagerImpl) {
                                beanResolver = ((BeanManagerImpl) beanManager).getBeanResolver();
                            }
                            InjectionPoint current = beanResolver != null
                                    ? beanResolver.getCurrentInjectionPoint()
                                    : null;
                            if (current != null) {
                                return current;
                            }
                            if (!fallbackInjectionPointWhenMissing) {
                                return null;
                            }
                            return new LookupInjectionPoint(quals);
                        }

                        Annotation[] qualifierArray = quals.toArray(new Annotation[0]);
                        Set<Bean<?>> beans = beanManager.getBeans(type, qualifierArray);
                        Bean<?> bean = beanManager.resolve(beans);
                        if (bean == null) {
                            throw new jakarta.enterprise.inject.UnsatisfiedResolutionException(
                                    "No bean found for type " + type.getName());
                        }
                        if (bean.getScope() == null || AnnotationPredicates.hasDependentAnnotation(bean.getScope())) {
                            Bean<Object> dependentBean = (Bean<Object>) bean;
                            CreationalContext<Object> childContext = beanManager.createCreationalContext(dependentBean);
                            Object instance = dependentBean.create(childContext);
                            if (parentContext != null) {
                                dependentLookupTracker.add(
                                        parentContext,
                                        new DependentLookupInstance(dependentBean, instance, childContext));
                            }
                            return instance;
                        }
                        return beanManager.getReference(bean, type, parentContext);
                    }

                    @Override
                    public java.util.Collection<Class<?>> resolveImplementations(Class<Object> type,
                                                                                 java.util.Collection<Annotation> quals) {
                        Annotation[] qualifierArray = quals.toArray(new Annotation[0]);
                        Set<Bean<?>> beans = beanManager.getBeans(type, qualifierArray);
                        java.util.List<Class<?>> classes = new java.util.ArrayList<>();
                        for (Bean<?> bean : beans) {
                            classes.add(bean.getBeanClass());
                        }
                        return classes;
                    }

                    @Override
                    public void invokePreDestroy(Object instance) {
                        // not used by BCE lookup callbacks
                    }
                },
                null,
                (BeanManagerImpl) beanManager
        );
    }

    private static final class DependentLookupTracker {
        private final Object lock = new Object();
        private final Map<CreationalContext<?>, java.util.List<DependentLookupInstance>> lookupsByParent =
                new java.util.IdentityHashMap<>();

        private void add(CreationalContext<?> parentContext, DependentLookupInstance lookup) {
            if (parentContext == null || lookup == null) {
                return;
            }
            synchronized (lock) {
                lookupsByParent.computeIfAbsent(parentContext, ignored -> new java.util.ArrayList<>())
                        .add(lookup);
            }
        }

        private void release(CreationalContext<?> parentContext) {
            if (parentContext == null) {
                return;
            }
            java.util.List<DependentLookupInstance> lookups;
            synchronized (lock) {
                lookups = lookupsByParent.remove(parentContext);
            }
            if (lookups == null) {
                return;
            }
            for (DependentLookupInstance lookup : lookups) {
                if (lookup != null) {
                    lookup.destroy();
                }
            }
        }
    }

    private static final class DependentLookupInstance {
        private final Bean<Object> bean;
        private final Object instance;
        private final CreationalContext<Object> creationalContext;

        private DependentLookupInstance(Bean<Object> bean, Object instance, CreationalContext<Object> creationalContext) {
            this.bean = bean;
            this.instance = instance;
            this.creationalContext = creationalContext;
        }

        private void destroy() {
            bean.destroy(instance, creationalContext);
        }
    }

    private Set<Annotation> defaultQualifiers() {
        Set<Annotation> qualifiers = new LinkedHashSet<>();
        qualifiers.add(Default.Literal.INSTANCE);
        qualifiers.add(Any.Literal.INSTANCE);
        return qualifiers;
    }

    private static final class LookupInjectionPoint implements InjectionPoint {
        private final Set<Annotation> qualifiers;

        private LookupInjectionPoint(java.util.Collection<Annotation> qualifiers) {
            this.qualifiers = qualifiers == null
                    ? Collections.emptySet()
                    : Collections.unmodifiableSet(new LinkedHashSet<>(qualifiers));
        }

        @Override
        public java.lang.reflect.Type getType() {
            return Object.class;
        }

        @Override
        public Set<Annotation> getQualifiers() {
            return qualifiers;
        }

        @Override
        public Bean<?> getBean() {
            return null;
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
            return false;
        }

        @Override
        public boolean isTransient() {
            return false;
        }
    }
}
