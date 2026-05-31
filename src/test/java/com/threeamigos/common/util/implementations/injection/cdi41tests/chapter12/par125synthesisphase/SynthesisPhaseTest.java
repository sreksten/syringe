package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter12.par125synthesisphase;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.bce.BceMetadata;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.build.compatible.spi.BeanInfo;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.InvokerFactory;
import jakarta.enterprise.inject.build.compatible.spi.InvokerInfo;
import jakarta.enterprise.inject.build.compatible.spi.Messages;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.inject.build.compatible.spi.Registration;
import jakarta.enterprise.inject.build.compatible.spi.Synthesis;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticComponents;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticObserver;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.inject.spi.EventContext;
import jakarta.enterprise.invoke.Invoker;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("12.5 - Synthesis phase tests")
@Execution(ExecutionMode.SAME_THREAD)
public class SynthesisPhaseTest {

    @Test
    @DisplayName("12.5 - @Synthesis supports SyntheticComponents, Types and Messages parameters")
    public void shouldInjectSupportedSynthesisParameters() {
        SynthesisRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(SynthesisContractExtension.class.getName());

        syringe.setup();

        assertTrue(SynthesisRecorder.syntheticComponentsInjected);
        assertTrue(SynthesisRecorder.typesInjected);
        assertTrue(SynthesisRecorder.messagesInjected);
    }

    @Test
    @DisplayName("12.5 - Synthetic builders configure bean/observer attributes and parameter map values")
    public void shouldConfigureSyntheticBeanObserverAndParameterMapMaterialization() {
        SynthesisRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(SynthesisContractExtension.class.getName());
        syringe.setup();

        SyntheticContract bean = syringe.inject(SyntheticContract.class, SyntheticQLiteral.INSTANCE);
        assertEquals("echo:bean", bean.value());
        assertTrue(SynthesisRecorder.beanParamsResolved);
        assertTrue(SynthesisRecorder.invokerMaterializedInBean);
        assertTrue(SynthesisRecorder.annotationInfoMaterializedInBean);
        assertTrue(SynthesisRecorder.classInfoMaterializedInBean);

        BeanManager beanManager = syringe.getBeanManager();
        beanManager.getEvent().select(SynthEvent.class).fire(new SynthEvent("obs"));
        assertTrue(SynthesisRecorder.observerNotified);
        assertTrue(SynthesisRecorder.invokerMaterializedInObserver);
        assertTrue(SynthesisRecorder.annotationInfoMaterializedInObserver);
    }

    @Test
    @DisplayName("12.5 - Container creates creator/observer function instances when creating beans and notifying observers")
    public void shouldInstantiateSyntheticFunctionsPerInvocation() {
        SynthesisRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(SynthesisContractExtension.class.getName());
        syringe.setup();

        syringe.inject(SyntheticContract.class, SyntheticQLiteral.INSTANCE);
        syringe.inject(SyntheticContract.class, SyntheticQLiteral.INSTANCE);
        assertTrue(SynthesisRecorder.creatorCtorCalls >= 2);
        assertEquals(2, SynthesisRecorder.creatorCreateCalls);

        BeanManager beanManager = syringe.getBeanManager();
        beanManager.getEvent().select(SynthEvent.class).fire(new SynthEvent("one"));
        beanManager.getEvent().select(SynthEvent.class).fire(new SynthEvent("two"));
        assertTrue(SynthesisRecorder.observerCtorCalls >= 2);
        assertEquals(2, SynthesisRecorder.observerCalls);
    }

    @Test
    @DisplayName("12.5 - Synthesis method with unsupported parameter type is a deployment problem")
    public void shouldRejectUnsupportedSynthesisParameterType() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidSynthesisParameterExtension.class.getName());
        assertThrows(DefinitionException.class, syringe::setup);
    }

    private Syringe newSyringe() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), RootBean.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        return syringe;
    }

    @Dependent
    public static class RootBean {
    }

    public enum Mode {
        FAST
    }

    @Qualifier
    @Retention(RUNTIME)
    @Target(TYPE)
    public @interface SyntheticQ {
    }

    public static final class SyntheticQLiteral extends AnnotationLiteral<SyntheticQ> implements SyntheticQ {
        static final SyntheticQLiteral INSTANCE = new SyntheticQLiteral();
    }

    @Retention(RUNTIME)
    @Target(TYPE)
    public @interface SynthAnn {
        String value();
    }

    public static final class SynthAnnLiteral extends AnnotationLiteral<SynthAnn> implements SynthAnn {
        private final String value;

        public SynthAnnLiteral(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }
    }

    public interface SyntheticContract {
        String value();
    }

    public static class SyntheticBeanImpl implements SyntheticContract {
        private final String value;

        public SyntheticBeanImpl(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }
    }

    public static class InvokerSource {
        public static String echo(String v) {
            return "echo:" + v;
        }
    }

    public static class MarkerClass {
    }

    public static class SynthEvent {
        final String value;

        public SynthEvent(String value) {
            this.value = value;
        }
    }

    static class SynthesisRecorder {
        static boolean syntheticComponentsInjected;
        static boolean typesInjected;
        static boolean messagesInjected;
        static boolean beanParamsResolved;
        static boolean invokerMaterializedInBean;
        static boolean annotationInfoMaterializedInBean;
        static boolean classInfoMaterializedInBean;
        static boolean observerNotified;
        static boolean invokerMaterializedInObserver;
        static boolean annotationInfoMaterializedInObserver;
        static int creatorCtorCalls;
        static int creatorCreateCalls;
        static int observerCtorCalls;
        static int observerCalls;

        static void reset() {
            syntheticComponentsInjected = false;
            typesInjected = false;
            messagesInjected = false;
            beanParamsResolved = false;
            invokerMaterializedInBean = false;
            annotationInfoMaterializedInBean = false;
            classInfoMaterializedInBean = false;
            observerNotified = false;
            invokerMaterializedInObserver = false;
            annotationInfoMaterializedInObserver = false;
            creatorCtorCalls = 0;
            creatorCreateCalls = 0;
            observerCtorCalls = 0;
            observerCalls = 0;
        }
    }

    public static class SynthesisContractExtension implements BuildCompatibleExtension {
        static volatile InvokerInfo invokerToken;

        @Registration(types = InvokerSource.class)
        public void registration(BeanInfo beanInfo, InvokerFactory invokerFactory) {
            try {
                invokerToken = invokerFactory.createInvoker(
                    beanInfo,
                    BceMetadata.methodInfo(InvokerSource.class.getDeclaredMethod("echo", String.class))
                ).build();
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        @Synthesis
        public void synthesis(SyntheticComponents syntheticComponents, Types types, Messages messages) {
            SynthesisRecorder.syntheticComponentsInjected = syntheticComponents != null;
            SynthesisRecorder.typesInjected = types != null && types.ofVoid() != null;
            SynthesisRecorder.messagesInjected = messages != null;

            syntheticComponents.addBean(SyntheticBeanImpl.class)
                .type(SyntheticContract.class)
                .qualifier(SyntheticQLiteral.INSTANCE)
                .scope(Dependent.class)
                .withParam("bool", true)
                .withParam("int", 7)
                .withParam("long", 99L)
                .withParam("double", 3.5d)
                .withParam("string", "bean")
                .withParam("class", MarkerClass.class)
                .withParam("enum", Mode.FAST)
                .withParam("boolArr", new boolean[]{true, false})
                .withParam("stringArr", new String[]{"a", "b"})
                .withParam("classInfo", BceMetadata.classInfo(MarkerClass.class))
                .withParam("annInfo", BceMetadata.annotationInfo(new SynthAnnLiteral("bean-ann")))
                .withParam("invoker", invokerToken)
                .createWith(SyntheticBeanFactory.class);

            syntheticComponents.addObserver(SynthEvent.class)
                .withParam("flag", true)
                .withParam("annInfo", BceMetadata.annotationInfo(new SynthAnnLiteral("obs-ann")))
                .withParam("invoker", invokerToken)
                .observeWith(SyntheticEventObserver.class);
        }
    }

    public static class SyntheticBeanFactory implements SyntheticBeanCreator<SyntheticBeanImpl> {
        public SyntheticBeanFactory() {
            SynthesisRecorder.creatorCtorCalls++;
        }

        @SuppressWarnings("unchecked")
        @Override
        public SyntheticBeanImpl create(jakarta.enterprise.inject.Instance<Object> lookup, Parameters params) {
            SynthesisRecorder.creatorCreateCalls++;
            boolean b = params.get("bool", Boolean.class);
            int i = params.get("int", Integer.class);
            long l = params.get("long", Long.class);
            double d = params.get("double", Double.class);
            String s = params.get("string", String.class);
            Class<?> c = params.get("class", Class.class);
            Mode m = params.get("enum", Mode.class);
            boolean[] ba = params.get("boolArr", boolean[].class);
            String[] sa = params.get("stringArr", String[].class);
            Class<?> ci = params.get("classInfo", Class.class);
            SynthAnn ann = params.get("annInfo", SynthAnn.class);
            Invoker<Object, Object> inv = params.get("invoker", Invoker.class);

            SynthesisRecorder.beanParamsResolved =
                b && i == 7 && l == 99L && d == 3.5d &&
                    "bean".equals(s) && MarkerClass.class.equals(c) &&
                    Mode.FAST == m && ba.length == 2 && sa.length == 2;
            SynthesisRecorder.classInfoMaterializedInBean = MarkerClass.class.equals(ci);
            SynthesisRecorder.annotationInfoMaterializedInBean = ann != null && "bean-ann".equals(ann.value());
            try {
                String echoed = (String) inv.invoke(null, new Object[]{"bean"});
                SynthesisRecorder.invokerMaterializedInBean = "echo:bean".equals(echoed);
                return new SyntheticBeanImpl(echoed);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class SyntheticEventObserver implements SyntheticObserver<SynthEvent> {
        public SyntheticEventObserver() {
            SynthesisRecorder.observerCtorCalls++;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void observe(EventContext<SynthEvent> event, Parameters params) throws Exception {
            SynthesisRecorder.observerCalls++;
            boolean flag = params.get("flag", Boolean.class);
            SynthAnn ann = params.get("annInfo", SynthAnn.class);
            Invoker<Object, Object> inv = params.get("invoker", Invoker.class);
            String echoed = (String) inv.invoke(null, new Object[]{event.getEvent().value});
            SynthesisRecorder.observerNotified = flag && "echo:obs".equals(echoed);
            SynthesisRecorder.annotationInfoMaterializedInObserver = ann != null && "obs-ann".equals(ann.value());
            SynthesisRecorder.invokerMaterializedInObserver = inv != null;
        }
    }

    public static class InvalidSynthesisParameterExtension implements BuildCompatibleExtension {
        @Synthesis
        public void synthesis(SyntheticComponents syntheticComponents, String unsupported) {
        }
    }
}
