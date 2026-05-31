package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter10.par103usinginvokerbuilder;

import com.threeamigos.common.util.implementations.injection.bce.BceMetadata;
import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.InvokerFactory;
import jakarta.enterprise.inject.build.compatible.spi.InvokerInfo;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.inject.build.compatible.spi.Registration;
import jakarta.enterprise.inject.build.compatible.spi.Synthesis;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticComponents;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticObserver;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.EventContext;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.enterprise.invoke.Invoker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("10.3 - Runtime materialization of InvokerInfo in synthetic components")
@Tag("bce-conformance")
@Execution(ExecutionMode.SAME_THREAD)
public class BuildCompatibleInvokerMaterializationTest {

    @AfterEach
    public void resetState() {
        RecordingSynthesisExtension.invokerTokenForBean = null;
        RecordingSynthesisExtension.invokerTokenForObserver = null;
        ContextDrivenSynthesisExtension.invokerToken = null;
        SurfaceSynthesisExtension.invokerTokenForBean = null;
        SurfaceSynthesisExtension.invokerTokenForObserver = null;
        SurfaceSyntheticBeanCreator.reset();
        SurfaceSyntheticObserver.reset();
        SyntheticObserverRecorder.reset();
    }

    @Test
    @DisplayName("10.3 - Synthetic bean creator receives materialized Invoker from InvokerInfo parameter")
    public void shouldMaterializeInvokerInfoForSyntheticBeanCreator() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(RecordingSynthesisExtension.class.getName());
        syringe.setup();

        GreetingFacade facade = syringe.inject(GreetingFacade.class);
        assertEquals("Hello world!", facade.greet("world"));
    }

    @Test
    @DisplayName("10.3 - Synthetic observer receives materialized Invoker from InvokerInfo parameter")
    public void shouldMaterializeInvokerInfoForSyntheticObserver() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(RecordingSynthesisExtension.class.getName());
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        beanManager.getEvent().select(PingEvent.class).fire(new PingEvent("ping"));

        assertEquals(1, SyntheticObserverRecorder.messages().size());
        assertEquals("ping", SyntheticObserverRecorder.messages().get(0));
    }

    @Test
    @DisplayName("10.3 - Synthetic builders accept ClassInfo, AnnotationInfo and lang-model Type surfaces")
    public void shouldSupportClassInfoAnnotationInfoAndLangModelTypeSurfaces() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(SurfaceSynthesisExtension.class.getName());
        syringe.setup();

        SurfaceGreetingFacade facade = syringe.inject(SurfaceGreetingFacade.class, SurfaceQualifierLiteral.INSTANCE);
        assertEquals("Hello neo!", facade.greet("neo"));
        assertEquals(SurfaceTag.class, SurfaceSyntheticBeanCreator.lastClassParam);
        assertEquals("surface", SurfaceSyntheticBeanCreator.lastQualifierValue);
        assertEquals(SurfaceTag.class, SurfaceSyntheticBeanCreator.lastClassArrayFirst);

        BeanManager beanManager = syringe.getBeanManager();
        beanManager.getEvent().select(PingEvent.class).fire(new PingEvent("surface-ping"));
        assertEquals(2, SurfaceSyntheticObserver.messages().size());
        assertEquals("surface-ping", SurfaceSyntheticObserver.messages().get(0));
        assertEquals("surface", SurfaceSyntheticObserver.messages().get(1));
    }

    @Test
    @DisplayName("10.3 - Full registration to synthesis flow can use discovered registration metadata context")
    public void shouldUseDiscoveredRegistrationMetadataContextEndToEnd() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(ContextDrivenSynthesisExtension.class.getName());
        syringe.setup();

        ContextGreetingFacade facade = syringe.inject(ContextGreetingFacade.class);
        assertEquals("ctx:hello", facade.greet("hello"));
    }

    private Syringe newSyringe() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), RootBean.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        return syringe;
    }

    public interface GreetingFacade {
        String greet(String name);
    }

    public interface SurfaceGreetingFacade {
        String greet(String name);
    }

    public interface ContextGreetingFacade {
        String greet(String name);
    }

    public static class GreetingInvokerSource {
        public static String greet(String name) {
            return "Hello " + name + "!";
        }
    }

    public static class ObserverInvokerSource {
        public static void record(String message) {
            SyntheticObserverRecorder.record(message);
        }
    }

    public static class ContextInvokerSource {
        public static String call(String value) {
            return "ctx:" + value;
        }
    }

    public static class GreetingFacadeCreator implements SyntheticBeanCreator<GreetingFacade> {
        @SuppressWarnings("unchecked")
        @Override
        public GreetingFacade create(jakarta.enterprise.inject.Instance<Object> lookup, Parameters params) {
            final Invoker<Object, Object> invoker = params.get("invoker", Invoker.class);
            return name -> {
                try {
                    return (String) invoker.invoke(null, new Object[]{name});
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
        }
    }

    public static class SurfaceSyntheticBeanCreator implements SyntheticBeanCreator<SurfaceGreetingFacade> {
        static volatile Class<?> lastClassParam;
        static volatile String lastQualifierValue;
        static volatile Class<?> lastClassArrayFirst;

        @SuppressWarnings("unchecked")
        @Override
        public SurfaceGreetingFacade create(jakarta.enterprise.inject.Instance<Object> lookup, Parameters params) {
            final Invoker<Object, Object> invoker = params.get("invoker", Invoker.class);
            lastClassParam = params.get("classParam", Class.class);
            SurfaceQualifier qualifier = params.get("qualifierParam", SurfaceQualifier.class);
            lastQualifierValue = qualifier.value();
            Class<?>[] classes = params.get("classArrayParam", Class[].class);
            lastClassArrayFirst = classes != null && classes.length > 0 ? classes[0] : null;
            return name -> {
                try {
                    return (String) invoker.invoke(null, new Object[]{name});
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
        }

        static void reset() {
            lastClassParam = null;
            lastQualifierValue = null;
            lastClassArrayFirst = null;
        }
    }

    public static class ContextSyntheticBeanCreator implements SyntheticBeanCreator<ContextGreetingFacade> {
        @SuppressWarnings("unchecked")
        @Override
        public ContextGreetingFacade create(jakarta.enterprise.inject.Instance<Object> lookup, Parameters params) {
            final Invoker<Object, Object> invoker = params.get("invoker", Invoker.class);
            return name -> {
                try {
                    return (String) invoker.invoke(null, new Object[]{name});
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
        }
    }

    public static class PingObserver implements SyntheticObserver<PingEvent> {
        @SuppressWarnings("unchecked")
        @Override
        public void observe(EventContext<PingEvent> event, Parameters params) throws Exception {
            Invoker<Object, Object> invoker = params.get("invoker", Invoker.class);
            invoker.invoke(null, new Object[]{event.getEvent().message});
        }
    }

    public static class SurfaceSyntheticObserver implements SyntheticObserver<PingEvent> {
        private static final List<String> MESSAGES = new ArrayList<String>();

        @SuppressWarnings("unchecked")
        @Override
        public void observe(EventContext<PingEvent> event, Parameters params) throws Exception {
            Invoker<Object, Object> invoker = params.get("invoker", Invoker.class);
            invoker.invoke(null, new Object[]{event.getEvent().message});
            // verifies AnnotationInfo[] -> Annotation[] conversion
            SurfaceQualifier[] qualifiers = params.get("qualifierArrayParam", SurfaceQualifier[].class);
            if (qualifiers != null && qualifiers.length > 0) {
                MESSAGES.add(qualifiers[0].value());
            }
        }

        static synchronized void record(String message) {
            MESSAGES.add(message);
        }

        static synchronized List<String> messages() {
            return new ArrayList<String>(MESSAGES);
        }

        static synchronized void reset() {
            MESSAGES.clear();
        }
    }

    public static class RecordingSynthesisExtension implements BuildCompatibleExtension {
        static volatile InvokerInfo invokerTokenForBean;
        static volatile InvokerInfo invokerTokenForObserver;

        @Registration(types = {GreetingInvokerSource.class, ObserverInvokerSource.class})
        public void registration(InvokerFactory invokerFactory) {
            try {
                invokerTokenForBean = invokerFactory
                    .createInvoker(
                        BceMetadata.beanInfo(GreetingInvokerSource.class),
                        BceMetadata.methodInfo(GreetingInvokerSource.class.getDeclaredMethod("greet", String.class)))
                    .build();
                invokerTokenForObserver = invokerFactory
                    .createInvoker(
                        BceMetadata.beanInfo(ObserverInvokerSource.class),
                        BceMetadata.methodInfo(ObserverInvokerSource.class.getDeclaredMethod("record", String.class)))
                    .build();
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        @Synthesis
        public void synthesis(SyntheticComponents syntheticComponents) {
            if (invokerTokenForBean != null) {
                syntheticComponents.addBean(GreetingFacade.class)
                    .withParam("invoker", invokerTokenForBean)
                    .createWith(GreetingFacadeCreator.class);
            }
            if (invokerTokenForObserver != null) {
                syntheticComponents.addObserver(PingEvent.class)
                    .withParam("invoker", invokerTokenForObserver)
                    .observeWith(PingObserver.class);
            }
        }
    }

    public static class SurfaceSynthesisExtension implements BuildCompatibleExtension {
        static volatile InvokerInfo invokerTokenForBean;
        static volatile InvokerInfo invokerTokenForObserver;

        @Registration(types = {GreetingInvokerSource.class, SurfaceSyntheticObserver.class})
        public void registration(InvokerFactory invokerFactory) {
            try {
                invokerTokenForBean = invokerFactory
                    .createInvoker(
                        BceMetadata.beanInfo(GreetingInvokerSource.class),
                        BceMetadata.methodInfo(GreetingInvokerSource.class.getDeclaredMethod("greet", String.class)))
                    .build();
                invokerTokenForObserver = invokerFactory
                    .createInvoker(
                        BceMetadata.beanInfo(SurfaceSyntheticObserver.class),
                        BceMetadata.methodInfo(SurfaceSyntheticObserver.class.getDeclaredMethod("record", String.class)))
                    .build();
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        @Synthesis
        public void synthesis(SyntheticComponents syntheticComponents) {
            syntheticComponents.addBean(SurfaceGreetingFacade.class)
                .type(BceMetadata.classInfo(SurfaceGreetingFacade.class))
                .type(BceMetadata.type(Object.class))
                .qualifier(BceMetadata.annotationInfo(new SurfaceQualifierLiteral()))
                .withParam("invoker", invokerTokenForBean)
                .withParam("classParam", BceMetadata.classInfo(SurfaceTag.class))
                .withParam("classArrayParam", new jakarta.enterprise.lang.model.declarations.ClassInfo[]{
                    BceMetadata.classInfo(SurfaceTag.class)})
                .withParam("qualifierParam", BceMetadata.annotationInfo(new SurfaceQualifierLiteral()))
                .createWith(SurfaceSyntheticBeanCreator.class);

            syntheticComponents.<PingEvent>addObserver(BceMetadata.type(PingEvent.class))
                .declaringClass(BceMetadata.classInfo(SurfaceSyntheticObserver.class))
                .withParam("invoker", invokerTokenForObserver)
                .withParam("qualifierArrayParam", new jakarta.enterprise.lang.model.AnnotationInfo[]{
                    BceMetadata.annotationInfo(new SurfaceQualifierLiteral())})
                .observeWith(SurfaceSyntheticObserver.class);
        }
    }

    public static class ContextDrivenSynthesisExtension implements BuildCompatibleExtension {
        static volatile InvokerInfo invokerToken;

        @Registration(types = {ContextInvokerSource.class})
        public void registration(InvokerFactory invokerFactory,
                                 com.threeamigos.common.util.implementations.injection.bce.BceRegistrationContext context) {
            jakarta.enterprise.inject.build.compatible.spi.BeanInfo beanInfo = context.bean(ContextInvokerSource.class);
            jakarta.enterprise.lang.model.declarations.MethodInfo methodInfo =
                context.method(beanInfo, "call", String.class);
            invokerToken = invokerFactory.createInvoker(beanInfo, methodInfo).build();
        }

        @Synthesis
        public void synthesis(SyntheticComponents syntheticComponents) {
            syntheticComponents.addBean(ContextGreetingFacade.class)
                .withParam("invoker", invokerToken)
                .createWith(ContextSyntheticBeanCreator.class);
        }
    }

    @jakarta.inject.Qualifier
    @Retention(RUNTIME)
    @Target(TYPE)
    public @interface SurfaceQualifier {
        String value();
    }

    public static final class SurfaceQualifierLiteral extends AnnotationLiteral<SurfaceQualifier>
        implements SurfaceQualifier {
        static final SurfaceQualifierLiteral INSTANCE = new SurfaceQualifierLiteral();

        @Override
        public String value() {
            return "surface";
        }
    }

    public static class SurfaceTag {
    }

    public static class SyntheticObserverRecorder {
        private static final List<String> MESSAGES = new ArrayList<String>();

        static synchronized void record(String message) {
            MESSAGES.add(message);
        }

        static synchronized List<String> messages() {
            return new ArrayList<String>(MESSAGES);
        }

        static synchronized void reset() {
            MESSAGES.clear();
        }
    }

    public static class PingEvent {
        private final String message;

        public PingEvent(String message) {
            this.message = message;
        }
    }

    public static class RootBean {
    }
}
