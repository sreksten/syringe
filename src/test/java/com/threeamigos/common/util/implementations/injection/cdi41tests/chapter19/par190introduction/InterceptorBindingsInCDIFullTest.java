package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter19.par190introduction;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXml;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXmlParser;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.InterceptionFactory;
import jakarta.enterprise.inject.spi.InterceptionType;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Interceptor bindings in CDI full test")
@Execution(ExecutionMode.SAME_THREAD)
public class InterceptorBindingsInCDIFullTest {

    @Test
    @DisplayName("19.0 - CDI Full supports @jakarta.interceptor.Interceptors class-level association")
    void shouldSupportClassLevelInterceptorsAnnotation() {
        LegacyRecorder.reset();
        Syringe syringe = newLegacyInterceptorsSyringe(ClassLevelLegacyInterceptedBean.class, LegacyStyleClassInterceptor.class);
        syringe.exclude(MethodLevelLegacyInterceptedBean.class);
        syringe.setup();

        ClassLevelLegacyInterceptedBean bean = syringe.inject(ClassLevelLegacyInterceptedBean.class);
        assertEquals("ok", bean.ping());
        assertEquals(Arrays.asList("legacy-before", "legacy-target", "legacy-after"), LegacyRecorder.events());
    }

    @Test
    @DisplayName("19.0 - CDI Full supports @jakarta.interceptor.Interceptors method-level association")
    void shouldSupportMethodLevelInterceptorsAnnotation() {
        LegacyRecorder.reset();
        Syringe syringe = newLegacyInterceptorsSyringe(MethodLevelLegacyInterceptedBean.class, LegacyStyleClassInterceptor.class);
        syringe.exclude(ClassLevelLegacyInterceptedBean.class);
        syringe.setup();

        MethodLevelLegacyInterceptedBean bean = syringe.inject(MethodLevelLegacyInterceptedBean.class);
        assertEquals("ok", bean.ping());
        assertEquals(Arrays.asList("legacy-before", "legacy-target", "legacy-after"), LegacyRecorder.events());
    }

    @Test
    @DisplayName("19.0 - CDI Full supports @AroundInvoke interceptor methods declared on target bean classes")
    void shouldSupportAroundInvokeOnTargetBeanClass() {
        AroundInvokeTargetRecorder.reset();
        Syringe syringe = newSyringe(TargetClassAroundInvokeBean.class);
        syringe.setup();

        TargetClassAroundInvokeBean bean = syringe.inject(TargetClassAroundInvokeBean.class);
        assertEquals("pong", bean.ping());
        assertEquals(Arrays.asList("target-around-before", "target-business", "target-around-after"),
                AroundInvokeTargetRecorder.events());
    }

    @Test
    @DisplayName("19.0 - CDI Full supports InterceptionFactory usage")
    void shouldSupportInterceptionFactoryUsage() {
        FullInterceptorRecorder.reset();
        Syringe syringe = newSyringe(
                FullInterceptorRecorder.class,
                CdiBoundInterceptor.class,
                PlainFactoryTarget.class
        );
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        CreationalContext<PlainFactoryTarget> creationalContext = beanManager.createCreationalContext(null);
        InterceptionFactory<PlainFactoryTarget> interceptionFactory =
                beanManager.createInterceptionFactory(creationalContext, PlainFactoryTarget.class);
        interceptionFactory.configure().add(CdiBoundLiteral.INSTANCE);

        PlainFactoryTarget intercepted = interceptionFactory.createInterceptedInstance(new PlainFactoryTarget());
        assertEquals("factory-ok", intercepted.business());
        assertTrue(FullInterceptorRecorder.events().contains("cdi-bound-before"));
    }

    @Test
    @DisplayName("19.0 - CDI Full supports custom implementations of jakarta.enterprise.inject.spi.Interceptor")
    void shouldSupportCustomInterceptorImplementations() {
        Syringe syringe = newSyringe(CustomInterceptorTarget.class, SyntheticInterceptorExtension.class);
        syringe.addExtension(SyntheticInterceptorExtension.class.getName());
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        List<jakarta.enterprise.inject.spi.Interceptor<?>> interceptors = beanManager.resolveInterceptors(
                InterceptionType.AROUND_INVOKE,
                CustomBindingLiteral.INSTANCE
        );

        assertTrue(interceptors.stream().anyMatch(i -> i.getBeanClass().equals(SyntheticCustomInterceptor.class)));
    }

    @Test
    @DisplayName("19.0 - CDI Full supports interceptor enablement and ordering per bean archive via beans.xml (overriding @Priority)")
    void shouldSupportBeansXmlInterceptorEnablementAndOrderingOverride() {
        FullInterceptorRecorder.reset();
        Syringe syringe = newSyringe(
                FullInterceptorRecorder.class,
                BeansXmlFirstPriorityHighInterceptor.class,
                BeansXmlSecondPriorityLowInterceptor.class,
                BeansXmlOrderedTarget.class
        );
        addBeansXmlInterceptors(syringe,
                BeansXmlSecondPriorityLowInterceptor.class.getName(),
                BeansXmlFirstPriorityHighInterceptor.class.getName());
        syringe.setup();

        BeansXmlOrderedTarget target = syringe.inject(BeansXmlOrderedTarget.class);
        assertEquals("ordered-ok", target.work());
        assertEquals(Arrays.asList(
                "beansxml-second-before",
                "beansxml-first-before",
                "beansxml-target",
                "beansxml-first-after",
                "beansxml-second-after"
        ), FullInterceptorRecorder.events());
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(ClassLevelLegacyInterceptedBean.class);
        syringe.exclude(MethodLevelLegacyInterceptedBean.class);
        syringe.exclude(LegacyStyleClassInterceptor.class);
        return syringe;
    }

    private Syringe newLegacyInterceptorsSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.enableCdiFullLegacyInterception(true);
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

    static class LegacyRecorder {
        private static final List<String> EVENTS = new ArrayList<String>();

        static synchronized void reset() {
            EVENTS.clear();
        }

        static synchronized void record(String event) {
            EVENTS.add(event);
        }

        static synchronized List<String> events() {
            return new ArrayList<String>(EVENTS);
        }
    }

    static class AroundInvokeTargetRecorder {
        private static final List<String> EVENTS = new ArrayList<String>();

        static synchronized void reset() {
            EVENTS.clear();
        }

        static synchronized void record(String event) {
            EVENTS.add(event);
        }

        static synchronized List<String> events() {
            return new ArrayList<String>(EVENTS);
        }
    }

    @Dependent
    @jakarta.interceptor.Interceptors(LegacyStyleClassInterceptor.class)
    public static class ClassLevelLegacyInterceptedBean {
        public String ping() {
            LegacyRecorder.record("legacy-target");
            return "ok";
        }
    }

    @Dependent
    public static class MethodLevelLegacyInterceptedBean {
        @jakarta.interceptor.Interceptors(LegacyStyleClassInterceptor.class)
        public String ping() {
            LegacyRecorder.record("legacy-target");
            return "ok";
        }
    }

    public static class LegacyStyleClassInterceptor {
        @AroundInvoke
        public Object around(InvocationContext invocationContext) throws Exception {
            LegacyRecorder.record("legacy-before");
            try {
                return invocationContext.proceed();
            } finally {
                LegacyRecorder.record("legacy-after");
            }
        }
    }

    @Dependent
    public static class TargetClassAroundInvokeBean {
        @AroundInvoke
        Object around(InvocationContext invocationContext) throws Exception {
            AroundInvokeTargetRecorder.record("target-around-before");
            try {
                return invocationContext.proceed();
            } finally {
                AroundInvokeTargetRecorder.record("target-around-after");
            }
        }

        public String ping() {
            AroundInvokeTargetRecorder.record("target-business");
            return "pong";
        }
    }

    @InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface CdiBound {
    }

    public static final class CdiBoundLiteral extends AnnotationLiteral<CdiBound> implements CdiBound {
        static final CdiBoundLiteral INSTANCE = new CdiBoundLiteral();
    }

    @Dependent
    public static class FullInterceptorRecorder {
        private static final List<String> EVENTS = new ArrayList<String>();

        static synchronized void reset() {
            EVENTS.clear();
        }

        static synchronized void record(String event) {
            EVENTS.add(event);
        }

        static synchronized List<String> events() {
            return new ArrayList<String>(EVENTS);
        }
    }

    @CdiBound
    @jakarta.interceptor.Interceptor
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 10)
    public static class CdiBoundInterceptor {
        @AroundInvoke
        Object around(InvocationContext context) throws Exception {
            FullInterceptorRecorder.record("cdi-bound-before");
            try {
                return context.proceed();
            } finally {
                FullInterceptorRecorder.record("cdi-bound-after");
            }
        }
    }

    @Dependent
    public static class PlainFactoryTarget {
        public String business() {
            FullInterceptorRecorder.record("factory-target");
            return "factory-ok";
        }
    }

    @InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface CustomBinding {
    }

    public static final class CustomBindingLiteral extends AnnotationLiteral<CustomBinding> implements CustomBinding {
        static final CustomBindingLiteral INSTANCE = new CustomBindingLiteral();
    }

    @CustomBinding
    @Dependent
    public static class CustomInterceptorTarget {
        public String call() {
            return "custom";
        }
    }

    public static class SyntheticInterceptorExtension implements Extension {
        public void afterBeanDiscovery(@Observes AfterBeanDiscovery afterBeanDiscovery) {
            afterBeanDiscovery.addBean(new SyntheticCustomInterceptor());
        }
    }

    public static class SyntheticCustomInterceptor implements jakarta.enterprise.inject.spi.Interceptor<Object> {

        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            return Collections.emptySet();
        }

        @Override
        public Object create(CreationalContext<Object> creationalContext) {
            return this;
        }

        @Override
        public void destroy(Object instance, CreationalContext<Object> creationalContext) {
            if (creationalContext != null) {
                creationalContext.release();
            }
        }

        @Override
        public Set<Annotation> getInterceptorBindings() {
            return new HashSet<Annotation>(Collections.<Annotation>singleton(CustomBindingLiteral.INSTANCE));
        }

        @Override
        public boolean intercepts(InterceptionType type) {
            return type == InterceptionType.AROUND_INVOKE;
        }

        @Override
        public Object intercept(InterceptionType type, Object instance, InvocationContext ctx) throws Exception {
            return ctx.proceed();
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public Set<Annotation> getQualifiers() {
            return Collections.<Annotation>singleton(jakarta.enterprise.inject.Default.Literal.INSTANCE);
        }

        @Override
        public Class<? extends Annotation> getScope() {
            return Dependent.class;
        }

        @Override
        public Set<Class<? extends Annotation>> getStereotypes() {
            return Collections.emptySet();
        }

        @Override
        public Set<Type> getTypes() {
            Set<Type> types = new HashSet<Type>();
            types.add(Object.class);
            types.add(jakarta.enterprise.inject.spi.Interceptor.class);
            return types;
        }

        @Override
        public boolean isAlternative() {
            return false;
        }

        @Override
        public Class<?> getBeanClass() {
            return SyntheticCustomInterceptor.class;
        }

    }

    @InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface BeansXmlOrderedBinding {
    }

    @BeansXmlOrderedBinding
    @jakarta.interceptor.Interceptor
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 200)
    public static class BeansXmlFirstPriorityHighInterceptor {
        @AroundInvoke
        Object around(InvocationContext context) throws Exception {
            FullInterceptorRecorder.record("beansxml-first-before");
            try {
                return context.proceed();
            } finally {
                FullInterceptorRecorder.record("beansxml-first-after");
            }
        }
    }

    @BeansXmlOrderedBinding
    @jakarta.interceptor.Interceptor
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 10)
    public static class BeansXmlSecondPriorityLowInterceptor {
        @AroundInvoke
        Object around(InvocationContext context) throws Exception {
            FullInterceptorRecorder.record("beansxml-second-before");
            try {
                return context.proceed();
            } finally {
                FullInterceptorRecorder.record("beansxml-second-after");
            }
        }
    }

    @BeansXmlOrderedBinding
    @Dependent
    public static class BeansXmlOrderedTarget {
        public String work() {
            FullInterceptorRecorder.record("beansxml-target");
            return "ordered-ok";
        }
    }
}
