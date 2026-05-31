package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter17.tckparity;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXml;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXmlParser;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.Interceptors;
import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("17.5 - Passivation parity for interceptor constraints")
class PassivationInterceptorsTckParityTest {
    private static final Class<?>[] FIXTURE_CLASSES = new Class<?>[]{
            Interceptor_Broken.class, Kokkola_Broken.class, BakedBean.class, BrokenInterceptor.class, Violation.class,
            ViolationProducer.class
    };

    @Test
    @DisplayName("17.5.5 / ManagedBeanWithNonSerializableInterceptorClassTest - passivating bean with non-serializable @Interceptors class fails deployment")
    void shouldMatchManagedBeanWithNonSerializableInterceptorClassTest() {
        Syringe syringe = newSyringe(Kokkola_Broken.class, Interceptor_Broken.class);
        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("17.5.5 / PassivationCapableBeanWithNonPassivatingInterceptorTest - passivating bean with non-passivating interceptor fails deployment")
    void shouldMatchPassivationCapableBeanWithNonPassivatingInterceptorTest() {
        Syringe syringe = newSyringe(BakedBean.class, BrokenInterceptor.class, ViolationProducer.class, Violation.class);
        addBeansXmlInterceptors(syringe, BrokenInterceptor.class.getName());

        assertThrows(DeploymentException.class, syringe::setup);
    }

    private Syringe newSyringe(Class<?>... classes) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), classes);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        Set<Class<?>> included = new HashSet<Class<?>>(Arrays.asList(classes));
        for (Class<?> fixture : FIXTURE_CLASSES) {
            if (!included.contains(fixture)) {
                syringe.exclude(fixture);
            }
        }
        return syringe;
    }

    private void addBeansXmlInterceptors(Syringe syringe, String... interceptorClassNames) {
        StringBuilder classes = new StringBuilder();
        for (String interceptorClassName : interceptorClassNames) {
            classes.append("<class>").append(interceptorClassName).append("</class>");
        }

        String xml = "<beans xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "xsi:schemaLocation=\"https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/beans_3_0.xsd\" " +
                "version=\"3.0\">" +
                "<interceptors>" + classes + "</interceptors>" +
                "</beans>";

        BeansXml beansXml = new BeansXmlParser().parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        syringe.getKnowledgeBase().addBeansXml(beansXml);
    }

    public static class Interceptor_Broken {
        @AroundInvoke
        public Object intercept(InvocationContext context) throws Exception {
            return context.proceed();
        }
    }

    @SessionScoped
    @Interceptors(Interceptor_Broken.class)
    public static class Kokkola_Broken implements Serializable {
        private static final long serialVersionUID = 1L;

        public void ping() {
        }
    }

    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @InterceptorBinding
    public @interface InterceptorType {
    }

    @Inherited
    @InterceptorBinding
    @InterceptorType
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface BakedBinding {
    }

    @SessionScoped
    @BakedBinding
    public static class BakedBean implements Serializable {
        private static final long serialVersionUID = 1L;

        void bake() {
        }
    }

    @Interceptor
    @InterceptorType
    public static class BrokenInterceptor {
        @Inject
        Violation violation;

        @AroundInvoke
        public Object invoke(InvocationContext ctx) throws Exception {
            return ctx.proceed();
        }
    }

    public static class Violation {
        public Violation(String foo) {
        }
    }

    public static class ViolationProducer {
        @Produces
        public final Violation getViolation() {
            return new Violation(null);
        }
    }
}
