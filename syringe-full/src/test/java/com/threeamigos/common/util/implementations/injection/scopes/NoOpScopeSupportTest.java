package com.threeamigos.common.util.implementations.injection.scopes;

import com.threeamigos.common.util.implementations.injection.NotEnabledFeatureException;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.NormalScope;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InjectionPoint;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static org.junit.jupiter.api.Assertions.*;

class NoOpScopeSupportTest {

    @Test
    void validateThrowsCanonicalMessageWhenApplicationScopedUsageIsDetected() {
        NoOpScopeSupport noOp = new NoOpScopeSupport();
        KnowledgeBase knowledgeBase = new KnowledgeBase(new InMemoryMessageHandler());
        Bean<Object> bean = new StubBean(
                ApplicationScopedFixture.class,
                (Class<? extends Annotation>) (Class<?>) ApplicationScoped.class
        );
        knowledgeBase.addBean(bean);
        noOp.setKnowledgeBase(knowledgeBase);

        NotEnabledFeatureException ex = assertThrows(
                NotEnabledFeatureException.class,
                noOp::validateNormalScopeUsage
        );

        assertTrue(ex.getMessage().startsWith(
                "@ApplicationScoped found on class " + ApplicationScopedFixture.class.getName() +
                        " but scope support is not available."));
    }

    @Test
    void validateThrowsCanonicalMessageWhenCustomNormalScopeUsageIsDetected() {
        NoOpScopeSupport noOp = new NoOpScopeSupport();
        KnowledgeBase knowledgeBase = new KnowledgeBase(new InMemoryMessageHandler());
        Bean<Object> bean = new StubBean(
                CustomScopedFixture.class,
                (Class<? extends Annotation>) (Class<?>) CustomNormalScope.class
        );
        knowledgeBase.addBean(bean);
        noOp.setKnowledgeBase(knowledgeBase);

        NotEnabledFeatureException ex = assertThrows(
                NotEnabledFeatureException.class,
                noOp::validateNormalScopeUsage
        );

        assertTrue(ex.getMessage().startsWith(
                "@CustomNormalScope found on class " + CustomScopedFixture.class.getName() +
                        " but scope support is not available."));
    }

    @Test
    void requestAndSessionActivationMethodsRemainSilent() {
        NoOpScopeSupport noOp = new NoOpScopeSupport();
        assertFalse(noOp.activateRequestContextIfNeeded());
        assertNull(noOp.activateSyntheticSessionContextIfNeeded());
    }

    private static final class ApplicationScopedFixture {
    }

    private static final class CustomScopedFixture {
    }

    @NormalScope
    @Retention(RetentionPolicy.RUNTIME)
    @Target({TYPE, METHOD, FIELD})
    private @interface CustomNormalScope {
    }

    private static final class StubBean implements Bean<Object> {
        private final Class<?> beanClass;
        private final Class<? extends Annotation> scope;

        private StubBean(Class<?> beanClass, Class<? extends Annotation> scope) {
            this.beanClass = beanClass;
            this.scope = scope;
        }

        @Override
        public Class<?> getBeanClass() {
            return beanClass;
        }

        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            return Collections.emptySet();
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public Set<Annotation> getQualifiers() {
            return Collections.emptySet();
        }

        @Override
        public Class<? extends Annotation> getScope() {
            return scope;
        }

        @Override
        public Set<Class<? extends Annotation>> getStereotypes() {
            return Collections.emptySet();
        }

        @Override
        public Set<Type> getTypes() {
            Set<Type> types = new HashSet<>();
            types.add(beanClass);
            types.add(Object.class);
            return types;
        }

        @Override
        public boolean isAlternative() {
            return false;
        }

        @Override
        public Object create(jakarta.enterprise.context.spi.CreationalContext<Object> creationalContext) {
            return null;
        }

        @Override
        public void destroy(Object instance, jakarta.enterprise.context.spi.CreationalContext<Object> creationalContext) {
            // no-op for test
        }
    }
}
