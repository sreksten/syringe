package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter20.par202decoratorenablementandordering;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXml;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXmlParser;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("20.2 - Decorator enablement and ordering")
@Execution(ExecutionMode.SAME_THREAD)
public class DecoratorEnablementAndOrderingTest {

    @Test
    @DisplayName("20.2.1 - @Priority enables decorators for the application and lower values are called first")
    void shouldEnableAndOrderPriorityDecoratorsForApplication() {
        InvocationRecorder.reset();
        Syringe syringe = newSyringe(
                OrderedServiceBean.class,
                PriorityFirstDecorator.class,
                PrioritySecondDecorator.class
        );
        syringe.setup();

        OrderedService service = syringe.inject(OrderedService.class);
        assertEquals("ok", service.call());
        assertEquals(
                Arrays.asList(
                        "priority-first-before",
                        "priority-second-before",
                        "business",
                        "priority-second-after",
                        "priority-first-after"
                ),
                InvocationRecorder.events()
        );
    }

    @Test
    @DisplayName("20.2.1 - Decorators are called after interceptors")
    void shouldInvokeDecoratorsAfterInterceptors() {
        InvocationRecorder.reset();
        Syringe syringe = newSyringe(
                OrderedServiceBean.class,
                PriorityFirstDecorator.class,
                AroundInvokeInterceptor.class
        );
        syringe.setup();

        OrderedService service = syringe.inject(OrderedService.class);
        assertEquals("ok", service.call());
        assertEquals(
                Arrays.asList(
                        "interceptor-before",
                        "priority-first-before",
                        "business",
                        "priority-first-after",
                        "interceptor-after"
                ),
                InvocationRecorder.events()
        );
    }

    @Test
    @DisplayName("20.2.1 - @Priority-enabled decorators are called before beans.xml-enabled decorators")
    void shouldInvokePriorityDecoratorsBeforeBeansXmlDecorators() {
        InvocationRecorder.reset();
        Syringe syringe = newSyringe(
                OrderedServiceBean.class,
                PriorityFirstDecorator.class,
                BeansXmlOnlyDecorator.class
        );
        addBeansXmlDecorators(syringe, BeansXmlOnlyDecorator.class.getName());
        syringe.setup();

        OrderedService service = syringe.inject(OrderedService.class);
        assertEquals("ok", service.call());
        assertEquals(
                Arrays.asList(
                        "priority-first-before",
                        "beansxml-only-before",
                        "business",
                        "beansxml-only-after",
                        "priority-first-after"
                ),
                InvocationRecorder.events()
        );
    }

    @Test
    @DisplayName("20.2.1 - If enabled by both @Priority and beans.xml, a decorator executes only once")
    void shouldNotExecuteDecoratorTwiceWhenEnabledByPriorityAndBeansXml() {
        InvocationRecorder.reset();
        Syringe syringe = newSyringe(
                OrderedServiceBean.class,
                PriorityAlsoBeansXmlDecorator.class,
                PriorityFirstDecorator.class
        );
        addBeansXmlDecorators(
                syringe,
                PriorityAlsoBeansXmlDecorator.class.getName()
        );
        syringe.setup();

        OrderedService service = syringe.inject(OrderedService.class);
        assertEquals("ok", service.call());
        assertEquals(
                Arrays.asList(
                        "priority-first-before",
                        "priority-and-beansxml-before",
                        "business",
                        "priority-and-beansxml-after",
                        "priority-first-after"
                ),
                InvocationRecorder.events()
        );
    }

    @Test
    @DisplayName("20.2.2 - Decorators listed in beans.xml are enabled and ordered by declaration order")
    void shouldEnableAndOrderDecoratorsFromBeansXml() {
        InvocationRecorder.reset();
        Syringe syringe = newSyringe(
                OrderedServiceBean.class,
                BeansXmlFirstDecorator.class,
                BeansXmlSecondDecorator.class
        );
        addBeansXmlDecorators(
                syringe,
                BeansXmlSecondDecorator.class.getName(),
                BeansXmlFirstDecorator.class.getName()
        );
        syringe.setup();

        OrderedService service = syringe.inject(OrderedService.class);
        assertEquals("ok", service.call());
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
    @DisplayName("20.2.2 - Unknown decorator class in beans.xml is a deployment problem")
    void shouldFailDeploymentWhenBeansXmlDecoratorClassDoesNotExist() {
        Syringe syringe = newSyringe(OrderedServiceBean.class, BeansXmlFirstDecorator.class);
        addBeansXmlDecorators(syringe, "com.acme.missing.DoesNotExistDecorator");
        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("20.2.2 - Non-decorator class in beans.xml is a deployment problem")
    void shouldFailDeploymentWhenBeansXmlClassIsNotDecorator() {
        Syringe syringe = newSyringe(OrderedServiceBean.class, NotADecoratorClass.class);
        addBeansXmlDecorators(syringe, NotADecoratorClass.class.getName());
        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("20.2.2 - Duplicate decorator entries in beans.xml are a deployment problem")
    void shouldFailDeploymentWhenBeansXmlContainsDuplicateDecoratorClass() {
        Syringe syringe = newSyringe(OrderedServiceBean.class, BeansXmlFirstDecorator.class);
        addBeansXmlDecorators(
                syringe,
                BeansXmlFirstDecorator.class.getName(),
                BeansXmlFirstDecorator.class.getName()
        );
        assertThrows(DeploymentException.class, syringe::setup);
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
                OrderedBinding.class,
                OrderedBindingLiteral.class,
                OrderedService.class,
                OrderedServiceBean.class,
                AroundInvokeInterceptor.class,
                PriorityFirstDecorator.class,
                PrioritySecondDecorator.class,
                BeansXmlOnlyDecorator.class,
                PriorityAlsoBeansXmlDecorator.class,
                BeansXmlFirstDecorator.class,
                BeansXmlSecondDecorator.class,
                NotADecoratorClass.class
        );
    }

    private void addBeansXmlDecorators(Syringe syringe, String... decoratorClassNames) {
        StringBuilder classes = new StringBuilder();
        for (String decoratorClassName : decoratorClassNames) {
            classes.append("<class>").append(decoratorClassName).append("</class>");
        }

        String xml = "<beans xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "xsi:schemaLocation=\"https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/beans_3_0.xsd\" " +
                "version=\"3.0\">" +
                "<decorators>" + classes + "</decorators>" +
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

    public interface OrderedService {
        String call();
    }

    @Dependent
    @OrderedBinding
    public static class OrderedServiceBean implements OrderedService {
        @Override
        public String call() {
            InvocationRecorder.record("business");
            return "ok";
        }
    }

    @OrderedBinding
    @Interceptor
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 5)
    public static class AroundInvokeInterceptor {
        @AroundInvoke
        Object around(InvocationContext context) throws Exception {
            InvocationRecorder.record("interceptor-before");
            try {
                return context.proceed();
            } finally {
                InvocationRecorder.record("interceptor-after");
            }
        }
    }

    @Decorator
    @Dependent
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 10)
    public static class PriorityFirstDecorator implements OrderedService {
        @Inject
        @Delegate
        OrderedService delegate;

        @Override
        public String call() {
            InvocationRecorder.record("priority-first-before");
            try {
                return delegate.call();
            } finally {
                InvocationRecorder.record("priority-first-after");
            }
        }
    }

    @Decorator
    @Dependent
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 20)
    public static class PrioritySecondDecorator implements OrderedService {
        @Inject
        @Delegate
        OrderedService delegate;

        @Override
        public String call() {
            InvocationRecorder.record("priority-second-before");
            try {
                return delegate.call();
            } finally {
                InvocationRecorder.record("priority-second-after");
            }
        }
    }

    @Decorator
    @Dependent
    public static class BeansXmlOnlyDecorator implements OrderedService {
        @Inject
        @Delegate
        OrderedService delegate;

        @Override
        public String call() {
            InvocationRecorder.record("beansxml-only-before");
            try {
                return delegate.call();
            } finally {
                InvocationRecorder.record("beansxml-only-after");
            }
        }
    }

    @Decorator
    @Dependent
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 30)
    public static class PriorityAlsoBeansXmlDecorator implements OrderedService {
        @Inject
        @Delegate
        OrderedService delegate;

        @Override
        public String call() {
            InvocationRecorder.record("priority-and-beansxml-before");
            try {
                return delegate.call();
            } finally {
                InvocationRecorder.record("priority-and-beansxml-after");
            }
        }
    }

    @Decorator
    @Dependent
    public static class BeansXmlFirstDecorator implements OrderedService {
        @Inject
        @Delegate
        OrderedService delegate;

        @Override
        public String call() {
            InvocationRecorder.record("beansxml-first-before");
            try {
                return delegate.call();
            } finally {
                InvocationRecorder.record("beansxml-first-after");
            }
        }
    }

    @Decorator
    @Dependent
    public static class BeansXmlSecondDecorator implements OrderedService {
        @Inject
        @Delegate
        OrderedService delegate;

        @Override
        public String call() {
            InvocationRecorder.record("beansxml-second-before");
            try {
                return delegate.call();
            } finally {
                InvocationRecorder.record("beansxml-second-after");
            }
        }
    }

    @Dependent
    public static class NotADecoratorClass {
    }

}
