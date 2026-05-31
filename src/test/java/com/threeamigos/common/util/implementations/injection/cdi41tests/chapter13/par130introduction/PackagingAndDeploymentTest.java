package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter13.par130introduction;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.Discovery;
import jakarta.enterprise.inject.build.compatible.spi.ScannedClasses;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("13.0 - Packaging and deployment tests")
@Execution(ExecutionMode.SAME_THREAD)
public class PackagingAndDeploymentTest {

    @Test
    @DisplayName("13.0 - Deployment performs discovery/BCE before application start and startup can complete afterwards")
    public void shouldRunDiscoveryAndBceBeforeStart() {
        DeploymentRecorder.reset();
        Syringe syringe = newSyringe(RootBean.class);
        syringe.addBuildCompatibleExtension(DiscoveryAddsBeanExtension.class.getName());

        syringe.initialize();
        assertTrue(DeploymentRecorder.discoveryBceCalled);
        assertThrows(IllegalStateException.class, syringe::getBeanManager);

        syringe.start();
        assertTrue(syringe.inject(DiscoveredByBceBean.class) != null);
    }

    @Test
    @DisplayName("13.0 - Definition errors are detected at deployment time")
    public void shouldFailDeploymentForDefinitionErrors() {
        Syringe syringe = newErrorSyringe(RawInstanceDefinitionErrorBean.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("13.0 - Deployment problems are detected before application becomes available")
    public void shouldFailDeploymentForUnsatisfiedDependencies() {
        Syringe syringe = newErrorSyringe(UnsatisfiedInjectionBean.class);
        assertThrows(RuntimeException.class, syringe::setup);
    }

    @Test
    @DisplayName("13.0 - Enabled alternatives participate in bean discovery and resolution")
    public void shouldEnableAlternativesDuringDiscovery() {
        Syringe syringe = newSyringe(AlternativeConsumer.class);
        syringe.setup();
        AlternativeConsumer consumer = syringe.inject(AlternativeConsumer.class);
        assertEquals("alternative", consumer.serviceId());
    }

    @Test
    @DisplayName("13.0 - Bean discovery determines beans contained in bean archives")
    public void shouldDiscoverBeansContainedInArchive() {
        Syringe syringe = newSyringe(ArchiveConsumer.class);
        syringe.setup();
        ArchiveConsumer consumer = syringe.inject(ArchiveConsumer.class);
        assertEquals("archive-bean", consumer.value());
    }

    @Test
    @DisplayName("13.0 - Enabled interceptors are ordered by priority")
    public void shouldOrderEnabledInterceptorsByPriority() {
        InterceptorRecorder.reset();
        Syringe syringe = newSyringe(InterceptedService.class);
        syringe.setup();

        InterceptedService service = syringe.inject(InterceptedService.class);
        assertEquals("ok", service.call());
        assertEquals(4, InterceptorRecorder.calls.size());
        assertEquals("first-before", InterceptorRecorder.calls.get(0));
        assertEquals("second-before", InterceptorRecorder.calls.get(1));
        assertEquals("second-after", InterceptorRecorder.calls.get(2));
        assertEquals("first-after", InterceptorRecorder.calls.get(3));
    }

    @Test
    @DisplayName("13.0 - Additional beans can be registered programmatically via build compatible extensions")
    public void shouldRegisterAdditionalBeansViaBuildCompatibleExtension() {
        DeploymentRecorder.reset();
        Syringe syringe = newSyringe(RootBean.class);
        syringe.addBuildCompatibleExtension(DiscoveryAddsBeanExtension.class.getName());
        syringe.setup();

        assertTrue(syringe.inject(DiscoveredByBceBean.class) != null);
    }

    private Syringe newSyringe(Class<?> rootClass) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), rootClass);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(RawInstanceDefinitionErrorBean.class);
        syringe.exclude(UnsatisfiedInjectionBean.class);
        syringe.exclude(MissingDependency.class);
        return syringe;
    }

    private Syringe newErrorSyringe(Class<?> rootClass) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), rootClass);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        return syringe;
    }

    @Dependent
    public static class RootBean {
    }

    static class DeploymentRecorder {
        static boolean discoveryBceCalled;

        static void reset() {
            discoveryBceCalled = false;
        }
    }

    public static class DiscoveryAddsBeanExtension implements BuildCompatibleExtension {
        @Discovery
        public void discovery(ScannedClasses scannedClasses) {
            DeploymentRecorder.discoveryBceCalled = true;
            scannedClasses.add(DiscoveredByBceBean.class.getName());
        }
    }

    @Dependent
    public static class DiscoveredByBceBean {
    }

    @Dependent
    public static class RawInstanceDefinitionErrorBean {
        @Inject
        Instance raw;
    }

    @Dependent
    public static class UnsatisfiedInjectionBean {
        @Inject
        MissingDependency missing;
    }

    public interface MissingDependency {
    }

    public interface DemoService {
        String id();
    }

    @Dependent
    public static class DefaultDemoService implements DemoService {
        @Override
        public String id() {
            return "default";
        }
    }

    @Alternative
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 1)
    @Dependent
    public static class AlternativeDemoService implements DemoService {
        @Override
        public String id() {
            return "alternative";
        }
    }

    @Dependent
    public static class AlternativeConsumer {
        @Inject
        DemoService demoService;

        public String serviceId() {
            return demoService.id();
        }
    }

    @Dependent
    public static class ArchiveBean {
        public String value() {
            return "archive-bean";
        }
    }

    @Dependent
    public static class ArchiveConsumer {
        @Inject
        ArchiveBean archiveBean;

        public String value() {
            return archiveBean.value();
        }
    }

    @InterceptorBinding
    @Retention(RUNTIME)
    @Target({TYPE, METHOD})
    public @interface Tracked {
    }

    static class InterceptorRecorder {
        static final List<String> calls = new ArrayList<String>();

        static void reset() {
            calls.clear();
        }
    }

    @Tracked
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION + 10)
    public static class FirstInterceptor {
        @AroundInvoke
        public Object around(InvocationContext ctx) throws Exception {
            InterceptorRecorder.calls.add("first-before");
            try {
                return ctx.proceed();
            } finally {
                InterceptorRecorder.calls.add("first-after");
            }
        }
    }

    @Tracked
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION + 20)
    public static class SecondInterceptor {
        @AroundInvoke
        public Object around(InvocationContext ctx) throws Exception {
            InterceptorRecorder.calls.add("second-before");
            try {
                return ctx.proceed();
            } finally {
                InterceptorRecorder.calls.add("second-after");
            }
        }
    }

    @Tracked
    @Dependent
    public static class InterceptedService {
        public String call() {
            return "ok";
        }
    }
}
