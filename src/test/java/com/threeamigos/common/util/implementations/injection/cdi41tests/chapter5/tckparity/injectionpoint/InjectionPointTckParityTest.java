package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.tckparity.injectionpoint;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.AnnotatedField;
import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("5.5.7 - TCK parity for InjectionPointTest")
class InjectionPointTckParityTest {

    @Test
    @DisplayName("InjectionPointTest - injected InjectionPoint exposes bean, type and qualifiers")
    void metadataExposesBeanTypeAndQualifiers() {
        Syringe syringe = newSyringe(FieldInjectionPointBean.class, BeanWithInjectionPointMetadata.class);
        syringe.setup();

        FieldInjectionPointBean owner = syringe.inject(FieldInjectionPointBean.class);
        InjectionPoint metadata = owner.injectedBean.injectedMetadata;
        assertNotNull(metadata);

        Set<Bean<?>> owners = syringe.getBeanManager().getBeans(FieldInjectionPointBean.class);
        assertEquals(1, owners.size());
        assertEquals(owners.iterator().next(), metadata.getBean());
        assertEquals(BeanWithInjectionPointMetadata.class, metadata.getType());
        assertTrue(metadata.getQualifiers().contains(Default.Literal.INSTANCE));
    }

    @Test
    @DisplayName("InjectionPointTest - field/method/constructor metadata members and annotated type")
    void metadataMemberAndAnnotatedElementAreCorrect() {
        Syringe syringe = newSyringe(
                FieldInjectionPointBean.class,
                MethodInjectionPointBean.class,
                ConstructorInjectionPointBean.class,
                BeanWithInjectionPointMetadata.class
        );
        syringe.setup();

        InjectionPoint fieldIp = syringe.inject(FieldInjectionPointBean.class).injectedBean.injectedMetadata;
        assertInstanceOf(Field.class, fieldIp.getMember());
        assertInstanceOf(AnnotatedField.class, fieldIp.getAnnotated());

        InjectionPoint methodIp = syringe.inject(MethodInjectionPointBean.class).injectedBean.injectedMetadata;
        assertInstanceOf(Method.class, methodIp.getMember());
        assertInstanceOf(AnnotatedParameter.class, methodIp.getAnnotated());

        InjectionPoint constructorIp = syringe.inject(ConstructorInjectionPointBean.class).injectedBean.injectedMetadata;
        assertInstanceOf(Constructor.class, constructorIp.getMember());
        assertInstanceOf(AnnotatedParameter.class, constructorIp.getAnnotated());
    }

    @Test
    @DisplayName("InjectionPointTest - InjectionPoint bean is dependent scoped and isTransient reflects target field")
    void injectionPointBeanScopeAndTransientFlag() {
        Syringe syringe = newSyringe(
                FieldInjectionPointBean.class,
                TransientFieldInjectionPointBean.class,
                BeanWithInjectionPointMetadata.class
        );
        syringe.setup();

        Set<Bean<?>> ipBeans = syringe.getBeanManager().getBeans(InjectionPoint.class);
        assertEquals(1, ipBeans.size());
        assertEquals(Dependent.class, ipBeans.iterator().next().getScope());

        InjectionPoint nonTransient = syringe.inject(FieldInjectionPointBean.class).injectedBean.injectedMetadata;
        InjectionPoint transientIp = syringe.inject(TransientFieldInjectionPointBean.class).injectedBean.injectedMetadata;
        assertTrue(!nonTransient.isTransient());
        assertTrue(transientIp.isTransient());
    }

    private Syringe newSyringe(Class<?>... classes) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), classes);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        return syringe;
    }

    static class BeanWithInjectionPointMetadata {
        @Inject
        InjectionPoint injectedMetadata;
    }

    static class FieldInjectionPointBean {
        @Inject
        BeanWithInjectionPointMetadata injectedBean;
    }

    static class MethodInjectionPointBean {
        BeanWithInjectionPointMetadata injectedBean;

        @Inject
        void init(BeanWithInjectionPointMetadata injectedBean) {
            this.injectedBean = injectedBean;
        }
    }

    static class ConstructorInjectionPointBean {
        final BeanWithInjectionPointMetadata injectedBean;

        @Inject
        ConstructorInjectionPointBean(BeanWithInjectionPointMetadata injectedBean) {
            this.injectedBean = injectedBean;
        }
    }

    static class TransientFieldInjectionPointBean {
        @Inject
        transient BeanWithInjectionPointMetadata injectedBean;
    }
}
