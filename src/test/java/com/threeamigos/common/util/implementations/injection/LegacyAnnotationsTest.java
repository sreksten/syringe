package com.threeamigos.common.util.implementations.injection;

import com.threeamigos.common.util.implementations.injection.legacyfixtures.CdiApplicationScopedFixtureBean;
import com.threeamigos.common.util.implementations.injection.legacyfixtures.LegacySingletonFixtureBean;
import com.threeamigos.common.util.implementations.injection.legacynewfixtures.LegacyNewConsumerBean;
import com.threeamigos.common.util.implementations.injection.legacynewfixtures.LegacyNewTargetBean;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Named;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Legacy annotations test")
public class LegacyAnnotationsTest {

    @Test
    @DisplayName("@Singleton bean is semantically equivalent to @ApplicationScoped")
    void shouldTreatSingletonLikeApplicationScoped() {
        Syringe syringe = new Syringe(
                new InMemoryMessageHandler(),
                LegacySingletonFixtureBean.class,
                CdiApplicationScopedFixtureBean.class
        );
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();

        Bean<LegacySingletonFixtureBean> singletonBeanDef = resolveBean(beanManager, LegacySingletonFixtureBean.class);
        Bean<CdiApplicationScopedFixtureBean> appScopedBeanDef = resolveBean(beanManager, CdiApplicationScopedFixtureBean.class);

        LegacySingletonFixtureBean singleton1 = getBeanInstance(beanManager, singletonBeanDef);
        LegacySingletonFixtureBean singleton2 = getBeanInstance(beanManager, singletonBeanDef);
        CdiApplicationScopedFixtureBean appScoped1 = getBeanInstance(beanManager, appScopedBeanDef);
        CdiApplicationScopedFixtureBean appScoped2 = getBeanInstance(beanManager, appScopedBeanDef);

        assertSame(singleton1, singleton2, "@Singleton should resolve to a single contextual instance");
        assertSame(appScoped1, appScoped2, "@ApplicationScoped should resolve to a single contextual instance");
        assertEquals(
                ApplicationScoped.class.getName(),
                singletonBeanDef.getScope().getName(),
                "@Singleton should be normalized to @ApplicationScoped scope"
        );
    }

    @Test
    @DisplayName("@New behaves as dependent while original bean can remain normal-scoped")
    void shouldSupportLegacyNewWhenEnabled() {
        Syringe syringe = new Syringe(
                new InMemoryMessageHandler(),
                LegacyNewTargetBean.class,
                LegacyNewConsumerBean.class
        );
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.enableLegacyCdi10New(true);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        Bean<LegacyNewConsumerBean> consumerBeanDef = resolveBean(beanManager, LegacyNewConsumerBean.class);

        LegacyNewConsumerBean consumer1 = getBeanInstance(beanManager, consumerBeanDef);
        LegacyNewConsumerBean consumer2 = getBeanInstance(beanManager, consumerBeanDef);

        assertEquals(
                consumer1.getNormalReference().id(),
                consumer2.getNormalReference().id(),
                "Normal @ApplicationScoped reference should resolve to the same underlying contextual instance"
        );
        assertNotEquals(
                consumer1.getNewImplicitReference().id(),
                consumer2.getNewImplicitReference().id(),
                "@New implicit reference should be dependent-style (new instance)"
        );
        assertNotEquals(
                consumer1.getNewExplicitReference().id(),
                consumer2.getNewExplicitReference().id(),
                "@New explicit reference should be dependent-style (new instance)"
        );
        assertNotEquals(
                consumer1.getNewImplicitReference().id(),
                consumer1.getNewExplicitReference().id(),
                "Two distinct @New injection points should not share the same instance"
        );
    }

    @Test
    @DisplayName("@New remains unsatisfied when legacy compatibility is disabled")
    void shouldKeepLegacyNewDisabledByDefault() {
        Syringe syringe = new Syringe(
                new InMemoryMessageHandler(),
                LegacyNewTargetBean.class,
                LegacyNewConsumerBean.class
        );
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        assertThrows(
                DeploymentException.class,
                syringe::setup,
                "Legacy @New should fail validation when compatibility is disabled"
        );
    }

    @Test
    @DisplayName("@New with non-assignable explicit value must fail")
    void shouldRejectNonAssignableNewValue() {
        Syringe syringe = new Syringe(
                new InMemoryMessageHandler(),
                LegacyNewTargetBean.class,
                LegacyNewConsumerBean.class
        );
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.enableLegacyCdi10New(true);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        Annotation newQualifier = findFieldAnnotation(NewQualifierSamples.class, "mismatchedNew", "javax.enterprise.inject.New");

        assertThrows(
                DefinitionException.class,
                () -> beanManager.getBeans(CdiApplicationScopedFixtureBean.class, newQualifier),
                "@New value must be assignable to the required type"
        );
    }

    @Test
    @DisplayName("@New with extra qualifiers must fail")
    void shouldRejectAdditionalQualifiersAlongsideNew() {
        Syringe syringe = new Syringe(
                new InMemoryMessageHandler(),
                LegacyNewTargetBean.class,
                LegacyNewConsumerBean.class
        );
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.enableLegacyCdi10New(true);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        Annotation newQualifier = findFieldAnnotation(NewQualifierSamples.class, "explicitNew", "javax.enterprise.inject.New");
        Named namedQualifier = findNamedAnnotation(NewQualifierSamples.class, "newWithNamed");

        assertThrows(
                DefinitionException.class,
                () -> beanManager.getBeans(LegacyNewTargetBean.class, newQualifier, namedQualifier),
                "@New must not be combined with extra qualifiers"
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> Bean<T> resolveBean(BeanManager beanManager, Class<T> beanClass) {
        Set<Bean<?>> beans = beanManager.getBeans(beanClass);
        assertFalse(beans.isEmpty(), "No bean discovered for " + beanClass.getName());
        return (Bean<T>) beanManager.resolve((Set) beans);
    }

    private <T> T getBeanInstance(BeanManager beanManager, Bean<T> bean) {
        assertNotNull(bean, "Resolved bean must not be null");
        CreationalContext<T> ctx = beanManager.createCreationalContext(bean);
        return (T) beanManager.getContext(bean.getScope()).get(bean, ctx);
    }

    private Annotation findFieldAnnotation(Class<?> holder, String fieldName, String annotationTypeName) {
        try {
            Field field = holder.getDeclaredField(fieldName);
            for (Annotation annotation : field.getAnnotations()) {
                if (annotation.annotationType().getName().equals(annotationTypeName)) {
                    return annotation;
                }
            }
            throw new IllegalStateException("No annotation " + annotationTypeName + " found on " + fieldName);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Missing field " + fieldName, e);
        }
    }

    private Named findNamedAnnotation(Class<?> holder, String fieldName) {
        try {
            Field field = holder.getDeclaredField(fieldName);
            Named annotation = field.getAnnotation(Named.class);
            if (annotation == null) {
                throw new IllegalStateException("No @Named found on " + fieldName);
            }
            return annotation;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Missing field " + fieldName, e);
        }
    }

    private static class NewQualifierSamples {
        @SuppressWarnings("unused")
        @javax.enterprise.inject.New(LegacyNewTargetBean.class)
        private LegacyNewTargetBean explicitNew;

        @SuppressWarnings("unused")
        @javax.enterprise.inject.New(LegacyNewTargetBean.class)
        private CdiApplicationScopedFixtureBean mismatchedNew;

        @SuppressWarnings("unused")
        @javax.enterprise.inject.New(LegacyNewTargetBean.class)
        @Named("legacy")
        private LegacyNewTargetBean newWithNamed;
    }
}
