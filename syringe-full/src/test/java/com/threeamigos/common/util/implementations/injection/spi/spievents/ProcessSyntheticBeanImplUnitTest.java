package com.threeamigos.common.util.implementations.injection.spi.spievents;

import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.InjectionPoint;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessSyntheticBeanImplUnitTest {

    @Test
    void syntheticBeanProvidesNonNullAnnotatedMetadata() {
        MessageHandler messageHandler = new InMemoryMessageHandler();
        KnowledgeBase knowledgeBase = new KnowledgeBase(messageHandler);
        Bean<SyntheticType> bean = new TestSyntheticBean();

        ProcessSyntheticBeanImpl<SyntheticType> event =
                new ProcessSyntheticBeanImpl<>(messageHandler, knowledgeBase, bean, new TestExtension());

        event.beginObserverInvocation();
        try {
            Annotated annotated = event.getAnnotated();
            assertNotNull(annotated);
            assertTrue(annotated instanceof AnnotatedType);
            assertEquals(SyntheticType.class, ((AnnotatedType<?>) annotated).getJavaClass());
            assertNotNull(annotated.getAnnotation(Deprecated.class));
        } finally {
            event.endObserverInvocation();
        }
    }

    @Deprecated
    private static class SyntheticType {
    }

    private static class TestExtension implements Extension {
    }

    private static class TestSyntheticBean implements Bean<SyntheticType> {

        @Override
        public Class<?> getBeanClass() {
            return SyntheticType.class;
        }

        @Override
        public Set<Type> getTypes() {
            Set<Type> types = new LinkedHashSet<>();
            types.add(SyntheticType.class);
            types.add(Object.class);
            return types;
        }

        @Override
        public Set<Annotation> getQualifiers() {
            Set<Annotation> qualifiers = new LinkedHashSet<>();
            qualifiers.add(Default.Literal.INSTANCE);
            qualifiers.add(Any.Literal.INSTANCE);
            return qualifiers;
        }

        @Override
        public Class<? extends Annotation> getScope() {
            return Dependent.class;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public Set<Class<? extends Annotation>> getStereotypes() {
            return Collections.emptySet();
        }

        @Override
        public boolean isAlternative() {
            return false;
        }

        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            return Collections.emptySet();
        }

        @Override
        public SyntheticType create(CreationalContext<SyntheticType> creationalContext) {
            return new SyntheticType();
        }

        @Override
        public void destroy(SyntheticType instance, CreationalContext<SyntheticType> creationalContext) {
            if (creationalContext != null) {
                creationalContext.release();
            }
        }
    }
}
