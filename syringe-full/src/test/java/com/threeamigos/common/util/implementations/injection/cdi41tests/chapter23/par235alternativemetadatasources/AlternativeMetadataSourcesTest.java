package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter23.par235alternativemetadatasources;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.spi.configurators.AnnotatedTypeConfiguratorImpl;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AnnotatedConstructor;
import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.enterprise.inject.spi.AnnotatedField;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.AfterTypeDiscovery;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.configurator.AnnotatedConstructorConfigurator;
import jakarta.enterprise.inject.spi.configurator.AnnotatedFieldConfigurator;
import jakarta.enterprise.inject.spi.configurator.AnnotatedMethodConfigurator;
import jakarta.enterprise.inject.spi.configurator.AnnotatedParameterConfigurator;
import jakarta.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("23.5 - Alternative Metadata Sources")
public class AlternativeMetadataSourcesTest {

    @Test
    @DisplayName("23.5 - AnnotatedType.getConstructors returns all constructors declared on the type regardless of visibility")
    void shouldExposeAllDeclaredConstructors() {
        Syringe syringe = newSyringe(VisibilityConstructorsBean.class);
        syringe.setup();

        AnnotatedType<VisibilityConstructorsBean> type = syringe.getBeanManager().createAnnotatedType(VisibilityConstructorsBean.class);
        assertEquals(4, type.getConstructors().size());
    }

    @Test
    @DisplayName("23.5 - AnnotatedType.getMethods returns methods declared on the type and its supertypes")
    void shouldExposeMethodsDeclaredOnTypeAndSupertypes() {
        Syringe syringe = newSyringe(ChildWithInheritedMembers.class);
        syringe.setup();

        AnnotatedType<ChildWithInheritedMembers> type = syringe.getBeanManager().createAnnotatedType(ChildWithInheritedMembers.class);
        Set<String> signatures = new HashSet<String>();
        for (AnnotatedMethod<? super ChildWithInheritedMembers> method : type.getMethods()) {
            signatures.add(method.getJavaMember().getDeclaringClass().getSimpleName() + "#" + method.getJavaMember().getName());
        }

        assertTrue(signatures.contains("ChildWithInheritedMembers#childMethod"));
        assertTrue(signatures.contains("ParentWithMembers#parentMethod"));
    }

    @Test
    @DisplayName("23.5 - AnnotatedType.getFields returns fields declared on the type and its supertypes")
    void shouldExposeFieldsDeclaredOnTypeAndSupertypes() {
        Syringe syringe = newSyringe(ChildWithInheritedMembers.class);
        syringe.setup();

        AnnotatedType<ChildWithInheritedMembers> type = syringe.getBeanManager().createAnnotatedType(ChildWithInheritedMembers.class);
        Set<String> fields = new HashSet<String>();
        for (AnnotatedField<? super ChildWithInheritedMembers> field : type.getFields()) {
            fields.add(field.getJavaMember().getDeclaringClass().getSimpleName() + "#" + field.getJavaMember().getName());
        }

        assertTrue(fields.contains("ChildWithInheritedMembers#childField"));
        assertTrue(fields.contains("ParentWithMembers#parentField"));
    }

    @Test
    @DisplayName("23.5 - Type-level annotation inheritance for AnnotatedType applies only special scope inheritance rules")
    void shouldApplyOnlyScopeInheritanceRulesForTypeAnnotations() {
        Syringe syringe = newSyringe(ChildTypeForScopeInheritance.class);
        syringe.setup();

        AnnotatedType<ChildTypeForScopeInheritance> type = syringe.getBeanManager()
                .createAnnotatedType(ChildTypeForScopeInheritance.class);

        assertNotNull(type.getAnnotation(ApplicationScoped.class));
        assertTrue(type.isAnnotationPresent(ApplicationScoped.class));
        assertTrue(!type.isAnnotationPresent(InheritedNonScopeMarker.class));
    }

    @Test
    @DisplayName("23.5 - AnnotatedField exposes java.lang.reflect.Field and AnnotatedMember metadata")
    void shouldExposeAnnotatedFieldJavaMemberAndMemberMetadata() throws Exception {
        Syringe syringe = newSyringe(MemberContractBean.class);
        syringe.setup();

        AnnotatedType<MemberContractBean> type = syringe.getBeanManager().createAnnotatedType(MemberContractBean.class);
        AnnotatedField<? super MemberContractBean> field = findField(type, "staticField");

        assertEquals(MemberContractBean.class.getDeclaredField("staticField"), field.getJavaMember());
        assertTrue(field.isStatic());
        assertEquals(MemberContractBean.class, field.getDeclaringType().getJavaClass());
        assertEquals(String.class, field.getBaseType());
        assertTrue(field.getTypeClosure().contains(String.class));
        assertTrue(field.getTypeClosure().contains(Object.class));
        assertTrue(field.isAnnotationPresent(Named.class));
        assertNotNull(field.getAnnotation(Named.class));
        assertEquals(1, field.getAnnotations(Named.class).size());
    }

    @Test
    @DisplayName("23.5 - AnnotatedMethod exposes java.lang.reflect.Method and AnnotatedCallable metadata")
    void shouldExposeAnnotatedMethodJavaMemberAndCallableMetadata() throws Exception {
        Syringe syringe = newSyringe(MemberContractBean.class);
        syringe.setup();

        AnnotatedType<MemberContractBean> type = syringe.getBeanManager().createAnnotatedType(MemberContractBean.class);
        AnnotatedMethod<? super MemberContractBean> method = findMethod(type, "staticMethod");

        assertEquals(MemberContractBean.class.getDeclaredMethod("staticMethod", String.class), method.getJavaMember());
        assertTrue(method.isStatic());
        assertEquals(MemberContractBean.class, method.getDeclaringType().getJavaClass());
        assertEquals(String.class, method.getBaseType());
        assertTrue(method.getTypeClosure().contains(String.class));
        assertTrue(method.getTypeClosure().contains(Object.class));
        List<? extends AnnotatedParameter<? super MemberContractBean>> parameters = method.getParameters();
        assertEquals(1, parameters.size());
        assertEquals(0, parameters.get(0).getPosition());
        assertEquals(method.getJavaMember(), parameters.get(0).getDeclaringCallable().getJavaMember());
    }

    @Test
    @DisplayName("23.5 - AnnotatedConstructor exposes java.lang.reflect.Constructor and AnnotatedParameter position/declaring callable")
    void shouldExposeAnnotatedConstructorJavaMemberAndParameterMetadata() throws Exception {
        Syringe syringe = newSyringe(MemberContractBean.class);
        syringe.setup();

        AnnotatedType<MemberContractBean> type = syringe.getBeanManager().createAnnotatedType(MemberContractBean.class);
        AnnotatedConstructor<MemberContractBean> constructor = findInjectConstructor(type);

        assertEquals(MemberContractBean.class.getDeclaredConstructor(String.class), constructor.getJavaMember());
        assertTrue(!constructor.isStatic());
        assertEquals(MemberContractBean.class, constructor.getDeclaringType().getJavaClass());
        assertEquals(MemberContractBean.class, constructor.getBaseType());
        assertTrue(constructor.getTypeClosure().contains(MemberContractBean.class));
        assertTrue(constructor.getTypeClosure().contains(Object.class));

        List<AnnotatedParameter<MemberContractBean>> parameters = constructor.getParameters();
        assertEquals(1, parameters.size());
        AnnotatedParameter<MemberContractBean> parameter = parameters.get(0);
        assertEquals(0, parameter.getPosition());
        assertEquals(constructor.getJavaMember(), parameter.getDeclaringCallable().getJavaMember());
        assertEquals(String.class, parameter.getBaseType());
        assertTrue(parameter.getTypeClosure().contains(String.class));
        assertTrue(parameter.getTypeClosure().contains(Object.class));
        assertTrue(parameter.isAnnotationPresent(Named.class));
        assertNotNull(parameter.getAnnotation(Named.class));
        assertEquals(1, parameter.getAnnotations(Named.class).size());
    }

    @Test
    @DisplayName("23.5 - Annotated exposes overriding annotations and type declarations")
    void shouldExposeAnnotatedContractOperations() {
        Syringe syringe = newSyringe(MemberContractBean.class);
        syringe.setup();

        AnnotatedType<MemberContractBean> type = syringe.getBeanManager().createAnnotatedType(MemberContractBean.class);
        Set<Annotation> annotations = type.getAnnotations();
        assertTrue(containsAnnotationType(annotations, Named.class));
        assertTrue(type.isAnnotationPresent(Named.class));
        assertNotNull(type.getAnnotation(Named.class));
        assertEquals(1, type.getAnnotations(Named.class).size());

        Type baseType = type.getBaseType();
        assertEquals(MemberContractBean.class, baseType);
        Set<Type> typeClosure = type.getTypeClosure();
        assertTrue(typeClosure.contains(MemberContractBean.class));
        assertTrue(typeClosure.contains(Object.class));
    }

    @Test
    @DisplayName("23.5.1 - Container provides AnnotatedTypeConfigurator SPI implementations in BeforeBeanDiscovery, ProcessAnnotatedType and AfterTypeDiscovery")
    void shouldProvideAnnotatedTypeConfiguratorAcrossLifecycleEvents() {
        ConfiguratorSpiRecorder.reset();

        Syringe syringe = newSyringe(ConfiguratorTarget.class, ConfiguratorSpiExtension.class);
        syringe.addExtension(ConfiguratorSpiExtension.class.getName());
        syringe.setup();

        assertTrue(ConfiguratorSpiRecorder.beforeBeanDiscoverySeen);
        assertTrue(ConfiguratorSpiRecorder.processAnnotatedTypeSeen);
        assertTrue(ConfiguratorSpiRecorder.afterTypeDiscoverySeen);
        assertTrue(ConfiguratorSpiRecorder.beforeBeanDiscoveryConfiguratorUsable);
        assertTrue(ConfiguratorSpiRecorder.processAnnotatedTypeConfiguratorUsable, ConfiguratorSpiRecorder.failureReason);
        assertTrue(ConfiguratorSpiRecorder.afterTypeDiscoveryConfiguratorUsable);
    }

    @Test
    @DisplayName("23.5.1.1 - AnnotatedMethodConfigurator is obtained via AnnotatedTypeConfigurator and supports method/parameter configuration operations")
    @SuppressWarnings("unchecked")
    void shouldProvideAnnotatedMethodConfiguratorFromAnnotatedTypeConfigurator() {
        Syringe syringe = newSyringe(ConfiguratorTarget.class);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        AnnotatedType<ConfiguratorTarget> type = beanManager.createAnnotatedType(ConfiguratorTarget.class);
        AnnotatedTypeConfigurator<ConfiguratorTarget> typeConfigurator = new AnnotatedTypeConfiguratorImpl<ConfiguratorTarget>(type);

        AnnotatedMethodConfigurator<ConfiguratorTarget> targetMethodConfigurator = null;
        for (AnnotatedMethodConfigurator<? super ConfiguratorTarget> methodConfigurator : typeConfigurator.methods()) {
            if ("configuredMethod".equals(methodConfigurator.getAnnotated().getJavaMember().getName())) {
                targetMethodConfigurator = (AnnotatedMethodConfigurator<ConfiguratorTarget>) methodConfigurator;
                break;
            }
        }

        assertNotNull(targetMethodConfigurator);
        assertEquals("configuredMethod", targetMethodConfigurator.getAnnotated().getJavaMember().getName());

        assertTrue(targetMethodConfigurator.add(new NamedLiteral("method-added")) == targetMethodConfigurator);
        assertTrue(targetMethodConfigurator.remove(new Predicate<Annotation>() {
            @Override
            public boolean test(Annotation annotation) {
                return Named.class.equals(annotation.annotationType());
            }
        }) == targetMethodConfigurator);
        assertTrue(targetMethodConfigurator.removeAll() == targetMethodConfigurator);

        List<AnnotatedParameterConfigurator<ConfiguratorTarget>> params = targetMethodConfigurator.params();
        assertEquals(1, params.size());
        AnnotatedParameterConfigurator<ConfiguratorTarget> paramConfigurator = params.get(0);
        assertNotNull(paramConfigurator.getAnnotated());
        assertEquals(0, paramConfigurator.getAnnotated().getPosition());

        long filteredAll = targetMethodConfigurator.filterParams(new Predicate<AnnotatedParameter<ConfiguratorTarget>>() {
            @Override
            public boolean test(AnnotatedParameter<ConfiguratorTarget> parameter) {
                return true;
            }
        }).count();
        long filteredNone = targetMethodConfigurator.filterParams(new Predicate<AnnotatedParameter<ConfiguratorTarget>>() {
            @Override
            public boolean test(AnnotatedParameter<ConfiguratorTarget> parameter) {
                return false;
            }
        }).count();
        assertEquals(1L, filteredAll);
        assertEquals(0L, filteredNone);
    }

    @Test
    @DisplayName("23.5.1.2 - AnnotatedConstructorConfigurator is obtained via AnnotatedTypeConfigurator and supports constructor/parameter configuration operations")
    @SuppressWarnings("unchecked")
    void shouldProvideAnnotatedConstructorConfiguratorFromAnnotatedTypeConfigurator() {
        Syringe syringe = newSyringe(ConfiguratorTarget.class);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        AnnotatedType<ConfiguratorTarget> type = beanManager.createAnnotatedType(ConfiguratorTarget.class);
        AnnotatedTypeConfigurator<ConfiguratorTarget> typeConfigurator = new AnnotatedTypeConfiguratorImpl<ConfiguratorTarget>(type);

        AnnotatedConstructorConfigurator<ConfiguratorTarget> targetCtorConfigurator = null;
        for (AnnotatedConstructorConfigurator<ConfiguratorTarget> ctorConfigurator : typeConfigurator.constructors()) {
            if (ctorConfigurator.getAnnotated().isAnnotationPresent(Inject.class)) {
                targetCtorConfigurator = ctorConfigurator;
                break;
            }
        }

        assertNotNull(targetCtorConfigurator);
        assertTrue(targetCtorConfigurator.getAnnotated().isAnnotationPresent(Inject.class));

        assertTrue(targetCtorConfigurator.add(new NamedLiteral("ctor-added")) == targetCtorConfigurator);
        assertTrue(targetCtorConfigurator.remove(new Predicate<Annotation>() {
            @Override
            public boolean test(Annotation annotation) {
                return Named.class.equals(annotation.annotationType());
            }
        }) == targetCtorConfigurator);
        assertTrue(targetCtorConfigurator.removeAll() == targetCtorConfigurator);

        List<AnnotatedParameterConfigurator<ConfiguratorTarget>> params = targetCtorConfigurator.params();
        assertEquals(1, params.size());
        AnnotatedParameterConfigurator<ConfiguratorTarget> paramConfigurator = params.get(0);
        assertNotNull(paramConfigurator.getAnnotated());
        assertEquals(0, paramConfigurator.getAnnotated().getPosition());

        long filteredAll = targetCtorConfigurator.filterParams(new Predicate<AnnotatedParameter<ConfiguratorTarget>>() {
            @Override
            public boolean test(AnnotatedParameter<ConfiguratorTarget> parameter) {
                return true;
            }
        }).count();
        long filteredNone = targetCtorConfigurator.filterParams(new Predicate<AnnotatedParameter<ConfiguratorTarget>>() {
            @Override
            public boolean test(AnnotatedParameter<ConfiguratorTarget> parameter) {
                return false;
            }
        }).count();
        assertEquals(1L, filteredAll);
        assertEquals(0L, filteredNone);
    }

    @Test
    @DisplayName("23.5.1.3 - AnnotatedParameterConfigurator is obtained via AnnotatedMethodConfigurator and supports parameter configuration operations")
    @SuppressWarnings("unchecked")
    void shouldProvideAnnotatedParameterConfiguratorFromMethodConfigurator() {
        Syringe syringe = newSyringe(ConfiguratorTarget.class);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        AnnotatedType<ConfiguratorTarget> type = beanManager.createAnnotatedType(ConfiguratorTarget.class);
        AnnotatedTypeConfigurator<ConfiguratorTarget> typeConfigurator = new AnnotatedTypeConfiguratorImpl<ConfiguratorTarget>(type);

        AnnotatedMethodConfigurator<ConfiguratorTarget> methodConfigurator = null;
        for (AnnotatedMethodConfigurator<? super ConfiguratorTarget> candidate : typeConfigurator.methods()) {
            if ("configuredMethod".equals(candidate.getAnnotated().getJavaMember().getName())) {
                methodConfigurator = (AnnotatedMethodConfigurator<ConfiguratorTarget>) candidate;
                break;
            }
        }

        assertNotNull(methodConfigurator);
        List<AnnotatedParameterConfigurator<ConfiguratorTarget>> params = methodConfigurator.params();
        assertEquals(1, params.size());

        AnnotatedParameterConfigurator<ConfiguratorTarget> paramConfigurator = params.get(0);
        assertNotNull(paramConfigurator.getAnnotated());
        assertEquals(0, paramConfigurator.getAnnotated().getPosition());
        assertTrue(paramConfigurator.add(new NamedLiteral("method-param-added")) == paramConfigurator);
        assertTrue(paramConfigurator.remove(new Predicate<Annotation>() {
            @Override
            public boolean test(Annotation annotation) {
                return Named.class.equals(annotation.annotationType());
            }
        }) == paramConfigurator);
        assertTrue(paramConfigurator.removeAll() == paramConfigurator);
    }

    @Test
    @DisplayName("23.5.1.3 - AnnotatedParameterConfigurator is obtained via AnnotatedConstructorConfigurator and supports parameter configuration operations")
    @SuppressWarnings("unchecked")
    void shouldProvideAnnotatedParameterConfiguratorFromConstructorConfigurator() {
        Syringe syringe = newSyringe(ConfiguratorTarget.class);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        AnnotatedType<ConfiguratorTarget> type = beanManager.createAnnotatedType(ConfiguratorTarget.class);
        AnnotatedTypeConfigurator<ConfiguratorTarget> typeConfigurator = new AnnotatedTypeConfiguratorImpl<ConfiguratorTarget>(type);

        AnnotatedConstructorConfigurator<ConfiguratorTarget> ctorConfigurator = null;
        for (AnnotatedConstructorConfigurator<ConfiguratorTarget> candidate : typeConfigurator.constructors()) {
            if (candidate.getAnnotated().isAnnotationPresent(Inject.class)) {
                ctorConfigurator = candidate;
                break;
            }
        }

        assertNotNull(ctorConfigurator);
        List<AnnotatedParameterConfigurator<ConfiguratorTarget>> params = ctorConfigurator.params();
        assertEquals(1, params.size());

        AnnotatedParameterConfigurator<ConfiguratorTarget> paramConfigurator = params.get(0);
        assertNotNull(paramConfigurator.getAnnotated());
        assertEquals(0, paramConfigurator.getAnnotated().getPosition());
        assertTrue(paramConfigurator.add(new NamedLiteral("ctor-param-added")) == paramConfigurator);
        assertTrue(paramConfigurator.remove(new Predicate<Annotation>() {
            @Override
            public boolean test(Annotation annotation) {
                return Named.class.equals(annotation.annotationType());
            }
        }) == paramConfigurator);
        assertTrue(paramConfigurator.removeAll() == paramConfigurator);
    }

    @Test
    @DisplayName("23.5.1.4 - AnnotatedFieldConfigurator is obtained via AnnotatedTypeConfigurator")
    @SuppressWarnings("unchecked")
    void shouldProvideAnnotatedFieldConfiguratorFromAnnotatedTypeConfigurator() {
        Syringe syringe = newSyringe(ConfiguratorTarget.class);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        AnnotatedType<ConfiguratorTarget> type = beanManager.createAnnotatedType(ConfiguratorTarget.class);
        AnnotatedTypeConfigurator<ConfiguratorTarget> typeConfigurator = new AnnotatedTypeConfiguratorImpl<ConfiguratorTarget>(type);

        AnnotatedFieldConfigurator<ConfiguratorTarget> fieldConfigurator = null;
        for (AnnotatedFieldConfigurator<? super ConfiguratorTarget> candidate : typeConfigurator.fields()) {
            if ("configuredField".equals(candidate.getAnnotated().getJavaMember().getName())) {
                fieldConfigurator = (AnnotatedFieldConfigurator<ConfiguratorTarget>) candidate;
                break;
            }
        }

        assertNotNull(fieldConfigurator);
        assertNotNull(fieldConfigurator.getAnnotated());
        assertEquals("configuredField", fieldConfigurator.getAnnotated().getJavaMember().getName());
    }

    @Test
    @DisplayName("23.5.1.4 - AnnotatedFieldConfigurator supports add/remove/removeAll operations")
    @SuppressWarnings("unchecked")
    void shouldSupportAnnotatedFieldConfiguratorMutationOperations() {
        Syringe syringe = newSyringe(ConfiguratorTarget.class);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        AnnotatedType<ConfiguratorTarget> type = beanManager.createAnnotatedType(ConfiguratorTarget.class);
        AnnotatedTypeConfigurator<ConfiguratorTarget> typeConfigurator = new AnnotatedTypeConfiguratorImpl<ConfiguratorTarget>(type);

        AnnotatedFieldConfigurator<ConfiguratorTarget> fieldConfigurator = null;
        for (AnnotatedFieldConfigurator<? super ConfiguratorTarget> candidate : typeConfigurator.fields()) {
            if ("configuredField".equals(candidate.getAnnotated().getJavaMember().getName())) {
                fieldConfigurator = (AnnotatedFieldConfigurator<ConfiguratorTarget>) candidate;
                break;
            }
        }

        assertNotNull(fieldConfigurator);
        assertTrue(fieldConfigurator.add(new NamedLiteral("field-added")) == fieldConfigurator);
        assertTrue(fieldConfigurator.remove(new Predicate<Annotation>() {
            @Override
            public boolean test(Annotation annotation) {
                return Named.class.equals(annotation.annotationType());
            }
        }) == fieldConfigurator);
        assertTrue(fieldConfigurator.removeAll() == fieldConfigurator);
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        return syringe;
    }

    public static class VisibilityConstructorsBean {
        public VisibilityConstructorsBean() {
        }

        protected VisibilityConstructorsBean(String ignored) {
        }

        VisibilityConstructorsBean(int ignored) {
        }

        private VisibilityConstructorsBean(double ignored) {
        }
    }

    public static class ParentWithMembers {
        String parentField;

        void parentMethod() {
        }
    }

    public static class ChildWithInheritedMembers extends ParentWithMembers {
        String childField;

        void childMethod() {
        }
    }

    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface InheritedNonScopeMarker {
    }

    @ApplicationScoped
    @InheritedNonScopeMarker
    public static class ParentTypeForScopeInheritance {
    }

    public static class ChildTypeForScopeInheritance extends ParentTypeForScopeInheritance {
    }

    @Named("member-contract")
    public static class MemberContractBean {
        @Named("staticField")
        public static String staticField;

        @Inject
        public MemberContractBean(@Named("ctorParam") String name) {
        }

        @Named("staticMethod")
        public static String staticMethod(String value) {
            return value;
        }
    }

    public static class ConfiguratorTarget {
        String configuredField;

        @Inject
        public ConfiguratorTarget(@Named("ctorCfg") String ctorParam) {
        }

        public void configuredMethod(@Named("methodCfg") String methodParam) {
        }
    }

    public static class ConfiguratorSpiRecorder {
        static volatile boolean beforeBeanDiscoverySeen;
        static volatile boolean processAnnotatedTypeSeen;
        static volatile boolean afterTypeDiscoverySeen;
        static volatile boolean beforeBeanDiscoveryConfiguratorUsable;
        static volatile boolean processAnnotatedTypeConfiguratorUsable;
        static volatile boolean afterTypeDiscoveryConfiguratorUsable;
        static volatile String failureReason;

        static void reset() {
            beforeBeanDiscoverySeen = false;
            processAnnotatedTypeSeen = false;
            afterTypeDiscoverySeen = false;
            beforeBeanDiscoveryConfiguratorUsable = false;
            processAnnotatedTypeConfiguratorUsable = false;
            afterTypeDiscoveryConfiguratorUsable = false;
            failureReason = "";
        }
    }

    public static class ConfiguratorSpiExtension implements Extension {
        public void beforeBeanDiscovery(@Observes BeforeBeanDiscovery event) {
            ConfiguratorSpiRecorder.beforeBeanDiscoverySeen = true;
            AnnotatedTypeConfigurator<ConfiguratorTarget> configurator =
                    event.addAnnotatedType(ConfiguratorTarget.class, "cfg-bbd");
            ConfiguratorSpiRecorder.beforeBeanDiscoveryConfiguratorUsable =
                    exerciseTypeConfigurator(configurator, ConfiguratorTarget.class);
        }

        public void processAnnotatedType(@Observes ProcessAnnotatedType<?> event) {
            ConfiguratorSpiRecorder.processAnnotatedTypeSeen = true;
            @SuppressWarnings("unchecked")
            AnnotatedTypeConfigurator<ConfiguratorTarget> configurator =
                    (AnnotatedTypeConfigurator<ConfiguratorTarget>) event.configureAnnotatedType();
            ConfiguratorSpiRecorder.processAnnotatedTypeConfiguratorUsable =
                    exerciseTypeConfigurator(configurator, null);
        }

        public void afterTypeDiscovery(@Observes AfterTypeDiscovery event) {
            ConfiguratorSpiRecorder.afterTypeDiscoverySeen = true;
            AnnotatedTypeConfigurator<ConfiguratorTarget> configurator =
                    event.addAnnotatedType(ConfiguratorTarget.class, "cfg-atd");
            ConfiguratorSpiRecorder.afterTypeDiscoveryConfiguratorUsable =
                    exerciseTypeConfigurator(configurator, ConfiguratorTarget.class);
        }

        private static boolean exerciseTypeConfigurator(AnnotatedTypeConfigurator<ConfiguratorTarget> configurator,
                                                        Class<?> expectedType) {
            if (configurator == null || configurator.getAnnotated() == null) {
                ConfiguratorSpiRecorder.failureReason = "configurator or getAnnotated null";
                return false;
            }
            if (expectedType != null && !expectedType.equals(configurator.getAnnotated().getJavaClass())) {
                ConfiguratorSpiRecorder.failureReason = "unexpected configured type: " + configurator.getAnnotated().getJavaClass().getName();
                return false;
            }

            if (configurator.add(new NamedLiteral("typeCfg")) != configurator) {
                ConfiguratorSpiRecorder.failureReason = "type add() did not return same configurator";
                return false;
            }
            if (configurator.remove(new Predicate<Annotation>() {
                @Override
                public boolean test(Annotation annotation) {
                    return Named.class.equals(annotation.annotationType());
                }
            }) != configurator) {
                ConfiguratorSpiRecorder.failureReason = "type remove() did not return same configurator";
                return false;
            }
            if (configurator.removeAll() != configurator) {
                ConfiguratorSpiRecorder.failureReason = "type removeAll() did not return same configurator";
                return false;
            }

            Set<AnnotatedMethodConfigurator<ConfiguratorTarget>> methods = castMethods(configurator.methods());
            Set<AnnotatedFieldConfigurator<ConfiguratorTarget>> fields = castFields(configurator.fields());
            Set<AnnotatedConstructorConfigurator<ConfiguratorTarget>> constructors = configurator.constructors();

            if (methods == null || fields == null || constructors == null) {
                ConfiguratorSpiRecorder.failureReason = "methods/fields/constructors returned null";
                return false;
            }

            long filteredMethods = configurator.filterMethods(new Predicate<AnnotatedMethod<? super ConfiguratorTarget>>() {
                @Override
                public boolean test(AnnotatedMethod<? super ConfiguratorTarget> method) {
                    return "configuredMethod".equals(method.getJavaMember().getName());
                }
            }).count();
            long filteredFields = configurator.filterFields(new Predicate<AnnotatedField<? super ConfiguratorTarget>>() {
                @Override
                public boolean test(AnnotatedField<? super ConfiguratorTarget> field) {
                    return "configuredField".equals(field.getJavaMember().getName());
                }
            }).count();
            long filteredConstructors = configurator.filterConstructors(new Predicate<AnnotatedConstructor<ConfiguratorTarget>>() {
                @Override
                public boolean test(AnnotatedConstructor<ConfiguratorTarget> constructor) {
                    return constructor.getParameters().size() == 1;
                }
            }).count();

            if (filteredMethods < 0 || filteredFields < 0 || filteredConstructors < 0) {
                ConfiguratorSpiRecorder.failureReason = "negative filter count";
                return false;
            }

            if (!methods.isEmpty()) {
                AnnotatedMethodConfigurator<ConfiguratorTarget> methodConfigurator = methods.iterator().next();
                if (methodConfigurator.add(new NamedLiteral("methodCfg")) != methodConfigurator) {
                    ConfiguratorSpiRecorder.failureReason = "method add() did not return same configurator";
                    return false;
                }
                if (methodConfigurator.removeAll() != methodConfigurator) {
                    ConfiguratorSpiRecorder.failureReason = "method removeAll() did not return same configurator";
                    return false;
                }

                List<AnnotatedParameterConfigurator<ConfiguratorTarget>> methodParams = methodConfigurator.params();
                if (!methodParams.isEmpty()) {
                    AnnotatedParameterConfigurator<ConfiguratorTarget> paramConfigurator = methodParams.get(0);
                    if (paramConfigurator.add(new NamedLiteral("paramCfg")) != paramConfigurator) {
                        ConfiguratorSpiRecorder.failureReason = "method param add() did not return same configurator";
                        return false;
                    }
                    if (paramConfigurator.removeAll() != paramConfigurator) {
                        ConfiguratorSpiRecorder.failureReason = "method param removeAll() did not return same configurator";
                        return false;
                    }
                }
            }

            if (!fields.isEmpty()) {
                AnnotatedFieldConfigurator<ConfiguratorTarget> fieldConfigurator = fields.iterator().next();
                if (fieldConfigurator.add(new NamedLiteral("fieldCfg")) != fieldConfigurator) {
                    ConfiguratorSpiRecorder.failureReason = "field add() did not return same configurator";
                    return false;
                }
                if (fieldConfigurator.removeAll() != fieldConfigurator) {
                    ConfiguratorSpiRecorder.failureReason = "field removeAll() did not return same configurator";
                    return false;
                }
            }

            if (!constructors.isEmpty()) {
                AnnotatedConstructorConfigurator<ConfiguratorTarget> ctorConfigurator = constructors.iterator().next();
                if (ctorConfigurator.add(new NamedLiteral("ctorCfg")) != ctorConfigurator) {
                    ConfiguratorSpiRecorder.failureReason = "ctor add() did not return same configurator";
                    return false;
                }
                if (ctorConfigurator.removeAll() != ctorConfigurator) {
                    ConfiguratorSpiRecorder.failureReason = "ctor removeAll() did not return same configurator";
                    return false;
                }
                List<AnnotatedParameterConfigurator<ConfiguratorTarget>> ctorParams = ctorConfigurator.params();
                if (!ctorParams.isEmpty()) {
                    AnnotatedParameterConfigurator<ConfiguratorTarget> paramConfigurator = ctorParams.get(0);
                    if (paramConfigurator.add(new NamedLiteral("ctorParamCfg")) != paramConfigurator) {
                        ConfiguratorSpiRecorder.failureReason = "ctor param add() did not return same configurator";
                        return false;
                    }
                    if (paramConfigurator.removeAll() != paramConfigurator) {
                        ConfiguratorSpiRecorder.failureReason = "ctor param removeAll() did not return same configurator";
                        return false;
                    }
                }
            }

            return true;
        }

        @SuppressWarnings("unchecked")
        private static Set<AnnotatedMethodConfigurator<ConfiguratorTarget>> castMethods(
                Set<AnnotatedMethodConfigurator<? super ConfiguratorTarget>> input) {
            Set<AnnotatedMethodConfigurator<ConfiguratorTarget>> out = new HashSet<AnnotatedMethodConfigurator<ConfiguratorTarget>>();
            for (AnnotatedMethodConfigurator<? super ConfiguratorTarget> item : input) {
                out.add((AnnotatedMethodConfigurator<ConfiguratorTarget>) item);
            }
            return out;
        }

        @SuppressWarnings("unchecked")
        private static Set<AnnotatedFieldConfigurator<ConfiguratorTarget>> castFields(
                Set<AnnotatedFieldConfigurator<? super ConfiguratorTarget>> input) {
            Set<AnnotatedFieldConfigurator<ConfiguratorTarget>> out = new HashSet<AnnotatedFieldConfigurator<ConfiguratorTarget>>();
            for (AnnotatedFieldConfigurator<? super ConfiguratorTarget> item : input) {
                out.add((AnnotatedFieldConfigurator<ConfiguratorTarget>) item);
            }
            return out;
        }
    }

    public static class NamedLiteral extends AnnotationLiteral<Named> implements Named {
        private final String value;

        NamedLiteral(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }
    }

    private AnnotatedField<? super MemberContractBean> findField(AnnotatedType<MemberContractBean> type, String name) {
        for (AnnotatedField<? super MemberContractBean> field : type.getFields()) {
            if (name.equals(field.getJavaMember().getName())) {
                return field;
            }
        }
        throw new AssertionError("Field not found: " + name);
    }

    private AnnotatedMethod<? super MemberContractBean> findMethod(AnnotatedType<MemberContractBean> type, String name) {
        for (AnnotatedMethod<? super MemberContractBean> method : type.getMethods()) {
            if (name.equals(method.getJavaMember().getName())) {
                return method;
            }
        }
        throw new AssertionError("Method not found: " + name);
    }

    private AnnotatedConstructor<MemberContractBean> findInjectConstructor(AnnotatedType<MemberContractBean> type) {
        for (AnnotatedConstructor<MemberContractBean> constructor : type.getConstructors()) {
            if (constructor.isAnnotationPresent(Inject.class)) {
                return constructor;
            }
        }
        throw new AssertionError("Inject constructor not found");
    }

    private boolean containsAnnotationType(Set<Annotation> annotations, Class<? extends Annotation> type) {
        for (Annotation annotation : annotations) {
            if (annotation != null && type.equals(annotation.annotationType())) {
                return true;
            }
        }
        return false;
    }
}
