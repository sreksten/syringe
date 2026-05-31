package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter19.par192interceptors;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXml;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXmlParser;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterTypeDiscovery;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.ByteArrayInputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("19.2 - Interceptor enabling and ordering in CDI (full)")
@Execution(ExecutionMode.SAME_THREAD)
public class InterceptorEnablingAndOrderingInCDIFullTest {

    @Test
    @DisplayName("19.2 - Interceptors listed in beans.xml are enabled and ordered by declaration order")
    void shouldEnableInterceptorsInBeansXmlOrder() {
        InvocationRecorder.reset();
        Syringe syringe = newSyringe(
                OrderedTargetBean.class,
                BeansXmlOrderedFirstInterceptor.class,
                BeansXmlOrderedSecondInterceptor.class
        );
        addBeansXmlInterceptors(
                syringe,
                BeansXmlOrderedSecondInterceptor.class.getName(),
                BeansXmlOrderedFirstInterceptor.class.getName()
        );
        syringe.setup();

        OrderedTargetBean bean = syringe.inject(OrderedTargetBean.class);
        assertEquals("ok", bean.call());
        assertEquals(
                Arrays.asList(
                        "beansxml-second-before",
                        "beansxml-first-before",
                        "business",
                        "beansxml-first-after",
                        "beansxml-second-after"
                ),
                InvocationRecorder.events()
        );
    }

    @Test
    @DisplayName("19.2 - Unknown interceptor class in beans.xml is a deployment problem")
    void shouldFailDeploymentWhenBeansXmlInterceptorClassDoesNotExist() {
        Syringe syringe = newSyringe(OrderedTargetBean.class, BeansXmlOrderedFirstInterceptor.class);
        addBeansXmlInterceptors(syringe, "com.acme.missing.DoesNotExistInterceptor");
        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("19.2 - Non-interceptor class in beans.xml is a deployment problem")
    void shouldFailDeploymentWhenBeansXmlClassIsNotInterceptor() {
        Syringe syringe = newSyringe(OrderedTargetBean.class, NotAnInterceptorClass.class);
        addBeansXmlInterceptors(syringe, NotAnInterceptorClass.class.getName());
        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("19.2 - Duplicate interceptor entries in beans.xml are a deployment problem")
    void shouldFailDeploymentWhenBeansXmlContainsDuplicateInterceptorClass() {
        Syringe syringe = newSyringe(OrderedTargetBean.class, BeansXmlOrderedFirstInterceptor.class);
        addBeansXmlInterceptors(
                syringe,
                BeansXmlOrderedFirstInterceptor.class.getName(),
                BeansXmlOrderedFirstInterceptor.class.getName()
        );
        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("19.2 - @Priority-enabled interceptors run before beans.xml-enabled interceptors")
    void shouldInvokePriorityInterceptorsBeforeBeansXmlEnabledInterceptors() {
        InvocationRecorder.reset();
        Syringe syringe = newSyringe(
                OrderedTargetBean.class,
                PriorityEnabledInterceptor.class,
                BeansXmlEnabledOnlyInterceptor.class
        );
        addBeansXmlInterceptors(syringe, BeansXmlEnabledOnlyInterceptor.class.getName());
        syringe.setup();

        OrderedTargetBean bean = syringe.inject(OrderedTargetBean.class);
        assertEquals("ok", bean.call());
        assertEquals(
                Arrays.asList(
                        "priority-before",
                        "beansxml-only-before",
                        "business",
                        "beansxml-only-after",
                        "priority-after"
                ),
                InvocationRecorder.events()
        );
    }

    @Test
    @DisplayName("19.2 - If enabled by both @Priority and beans.xml an interceptor executes only once")
    void shouldNotExecuteInterceptorTwiceWhenEnabledByPriorityAndBeansXml() {
        InvocationRecorder.reset();
        Syringe syringe = newSyringe(
                OrderedTargetBean.class,
                PriorityEnabledInterceptor.class,
                PriorityAlsoBeansXmlInterceptor.class
        );
        addBeansXmlInterceptors(
                syringe,
                PriorityAlsoBeansXmlInterceptor.class.getName(),
                PriorityEnabledInterceptor.class.getName()
        );
        syringe.setup();

        OrderedTargetBean bean = syringe.inject(OrderedTargetBean.class);
        assertEquals("ok", bean.call());
        assertEquals(
                Arrays.asList(
                        "priority-before",
                        "priority-and-beansxml-before",
                        "business",
                        "priority-and-beansxml-after",
                        "priority-after"
                ),
                InvocationRecorder.events()
        );
    }

    @Test
    @DisplayName("19.2 - Interceptor order can be overridden using AfterTypeDiscovery")
    void shouldAllowAfterTypeDiscoveryToOverrideInterceptorOrder() {
        InvocationRecorder.reset();
        Syringe syringe = newSyringe(
                OrderedTargetBean.class,
                BeansXmlOrderedFirstInterceptor.class,
                BeansXmlOrderedSecondInterceptor.class,
                ReorderInterceptorsExtension.class
        );
        syringe.addExtension(ReorderInterceptorsExtension.class.getName());
        addBeansXmlInterceptors(
                syringe,
                BeansXmlOrderedFirstInterceptor.class.getName(),
                BeansXmlOrderedSecondInterceptor.class.getName()
        );
        syringe.setup();

        OrderedTargetBean bean = syringe.inject(OrderedTargetBean.class);
        assertEquals("ok", bean.call());
        assertEquals(
                Arrays.asList(
                        "beansxml-second-before",
                        "beansxml-first-before",
                        "business",
                        "beansxml-first-after",
                        "beansxml-second-after"
                ),
                InvocationRecorder.events()
        );
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        Set<Class<?>> included = new HashSet<Class<?>>(Arrays.asList(beanClasses));
        for (Class<?> fixture : allFixtureTypes()) {
            if (!included.contains(fixture)) {
                syringe.exclude(fixture);
            }
        }
        return syringe;
    }

    private Collection<Class<?>> allFixtureTypes() {
        return Arrays.<Class<?>>asList(
                OrderedTargetBean.class,
                BeansXmlOrderedFirstInterceptor.class,
                BeansXmlOrderedSecondInterceptor.class,
                BeansXmlEnabledOnlyInterceptor.class,
                PriorityEnabledInterceptor.class,
                PriorityAlsoBeansXmlInterceptor.class,
                NotAnInterceptorClass.class,
                ReorderInterceptorsExtension.class
        );
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

    static class InvocationRecorder {
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

    @InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface OrderedBinding {
    }

    public static final class OrderedBindingLiteral extends AnnotationLiteral<OrderedBinding> implements OrderedBinding {
        static final OrderedBindingLiteral INSTANCE = new OrderedBindingLiteral();
    }

    @Dependent
    @OrderedBinding
    public static class OrderedTargetBean {
        public String call() {
            InvocationRecorder.record("business");
            return "ok";
        }
    }

    @OrderedBinding
    @Interceptor
    public static class BeansXmlOrderedFirstInterceptor {
        @AroundInvoke
        Object around(InvocationContext context) throws Exception {
            InvocationRecorder.record("beansxml-first-before");
            try {
                return context.proceed();
            } finally {
                InvocationRecorder.record("beansxml-first-after");
            }
        }
    }

    @OrderedBinding
    @Interceptor
    public static class BeansXmlOrderedSecondInterceptor {
        @AroundInvoke
        Object around(InvocationContext context) throws Exception {
            InvocationRecorder.record("beansxml-second-before");
            try {
                return context.proceed();
            } finally {
                InvocationRecorder.record("beansxml-second-after");
            }
        }
    }

    @OrderedBinding
    @Interceptor
    public static class BeansXmlEnabledOnlyInterceptor {
        @AroundInvoke
        Object around(InvocationContext context) throws Exception {
            InvocationRecorder.record("beansxml-only-before");
            try {
                return context.proceed();
            } finally {
                InvocationRecorder.record("beansxml-only-after");
            }
        }
    }

    @OrderedBinding
    @Interceptor
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 10)
    public static class PriorityEnabledInterceptor {
        @AroundInvoke
        Object around(InvocationContext context) throws Exception {
            InvocationRecorder.record("priority-before");
            try {
                return context.proceed();
            } finally {
                InvocationRecorder.record("priority-after");
            }
        }
    }

    @OrderedBinding
    @Interceptor
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 30)
    public static class PriorityAlsoBeansXmlInterceptor {
        @AroundInvoke
        Object around(InvocationContext context) throws Exception {
            InvocationRecorder.record("priority-and-beansxml-before");
            try {
                return context.proceed();
            } finally {
                InvocationRecorder.record("priority-and-beansxml-after");
            }
        }
    }

    @Dependent
    public static class NotAnInterceptorClass {
    }

    public static class ReorderInterceptorsExtension implements Extension {
        public void reorder(@Observes AfterTypeDiscovery afterTypeDiscovery) {
            List<Class<?>> finalInterceptors = afterTypeDiscovery.getInterceptors();
            finalInterceptors.clear();
            finalInterceptors.add(BeansXmlOrderedSecondInterceptor.class);
            finalInterceptors.add(BeansXmlOrderedFirstInterceptor.class);
        }
    }
}
