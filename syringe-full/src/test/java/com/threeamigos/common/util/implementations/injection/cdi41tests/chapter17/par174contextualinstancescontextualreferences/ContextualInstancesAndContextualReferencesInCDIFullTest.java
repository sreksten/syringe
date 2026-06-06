package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter17.par174contextualinstancescontextualreferences;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("17.4 - Contextual instances and contextual references in CDI full test")
public class ContextualInstancesAndContextualReferencesInCDIFullTest {

    @Test
    @DisplayName("17.4 - For a custom Bean implementation, the container calls getScope() and uses it to determine application scope semantics")
    void shouldCallGetScopeForCustomBeanAndApplyApplicationScopeSemantics() {
        ScopeAwareSyntheticBean customBean = new ScopeAwareSyntheticBean(ApplicationScoped.class);
        Syringe syringe = newSyringe(ScopeResolutionAnchor.class);
        syringe.getKnowledgeBase().addBean(customBean);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();

        ScopeAwareService first = (ScopeAwareService) beanManager.getReference(
                customBean,
                ScopeAwareService.class,
                beanManager.createCreationalContext(customBean)
        );
        ScopeAwareService second = (ScopeAwareService) beanManager.getReference(
                customBean,
                ScopeAwareService.class,
                beanManager.createCreationalContext(customBean)
        );

        assertEquals(first.id(), second.id());
        assertEquals(1, customBean.createCalls());
        assertTrue(customBean.getScopeCalls() > 0);
    }

    @Test
    @DisplayName("17.4 - For a custom Bean implementation, the container calls getScope() and uses it to determine dependent scope semantics")
    void shouldCallGetScopeForCustomBeanAndApplyDependentScopeSemantics() {
        ScopeAwareSyntheticBean customBean = new ScopeAwareSyntheticBean(Dependent.class);
        Syringe syringe = newSyringe(ScopeResolutionAnchor.class);
        syringe.getKnowledgeBase().addBean(customBean);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();

        ScopeAwareService first = (ScopeAwareService) beanManager.getReference(
                customBean,
                ScopeAwareService.class,
                beanManager.createCreationalContext(customBean)
        );
        ScopeAwareService second = (ScopeAwareService) beanManager.getReference(
                customBean,
                ScopeAwareService.class,
                beanManager.createCreationalContext(customBean)
        );

        assertNotEquals(first.id(), second.id());
        assertEquals(2, customBean.createCalls());
        assertTrue(customBean.getScopeCalls() > 0);
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        return syringe;
    }

    @ApplicationScoped
    public static class ScopeResolutionAnchor {
    }

    interface ScopeAwareService {
        String id();
    }

    public static class ScopeAwareServiceImpl implements ScopeAwareService {
        private final String id = java.util.UUID.randomUUID().toString();

        @Override
        public String id() {
            return id;
        }
    }

    static class ScopeAwareSyntheticBean implements Bean<ScopeAwareService> {
        private static final Set<Type> TYPES;
        private static final Set<Annotation> QUALIFIERS;

        static {
            Set<Type> types = new HashSet<Type>();
            types.add(ScopeAwareService.class);
            types.add(ScopeAwareServiceImpl.class);
            types.add(Object.class);
            TYPES = Collections.unmodifiableSet(types);

            Set<Annotation> qualifiers = new HashSet<Annotation>();
            qualifiers.add(Default.Literal.INSTANCE);
            qualifiers.add(Any.Literal.INSTANCE);
            QUALIFIERS = Collections.unmodifiableSet(qualifiers);
        }

        private final Class<? extends Annotation> scope;
        private final AtomicInteger createCalls = new AtomicInteger(0);
        private final AtomicInteger getScopeCalls = new AtomicInteger(0);

        ScopeAwareSyntheticBean(Class<? extends Annotation> scope) {
            this.scope = scope;
        }

        int createCalls() {
            return createCalls.get();
        }

        int getScopeCalls() {
            return getScopeCalls.get();
        }

        @Override
        public ScopeAwareService create(CreationalContext<ScopeAwareService> creationalContext) {
            createCalls.incrementAndGet();
            return new ScopeAwareServiceImpl();
        }

        @Override
        public void destroy(ScopeAwareService instance, CreationalContext<ScopeAwareService> creationalContext) {
            if (creationalContext != null) {
                creationalContext.release();
            }
        }

        @Override
        public Class<?> getBeanClass() {
            return ScopeAwareServiceImpl.class;
        }

        @Override
        public Set<jakarta.enterprise.inject.spi.InjectionPoint> getInjectionPoints() {
            return Collections.emptySet();
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public Set<Annotation> getQualifiers() {
            return QUALIFIERS;
        }

        @Override
        public Class<? extends Annotation> getScope() {
            getScopeCalls.incrementAndGet();
            return scope;
        }

        @Override
        public Set<Class<? extends Annotation>> getStereotypes() {
            return Collections.emptySet();
        }

        @Override
        public Set<Type> getTypes() {
            return TYPES;
        }

        @Override
        public boolean isAlternative() {
            return false;
        }
    }
}
