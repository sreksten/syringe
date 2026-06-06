package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet1.InvalidAbstractProducerMethodBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet2.DependentNullProducerFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet2.NonDependentNullProducerFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet3.InvalidRawTypeArgumentProducerFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet4.InvalidWildcardArrayProducerFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet4.InvalidWildcardProducerFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet5.DependentTypeVariableProducerFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet5.NonDependentTypeVariableProducerFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet6.InvalidTypeVariableProducerFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet7.InvalidTypeVariableArrayProducerFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet8.ArrayReturnProducerFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet8.ClassReturnProducerFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet8.InterfaceReturnProducerFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet8.LegalTypePruningProducerFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet8.PrimitiveReturnProducerFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet9.ConfiguredProducerFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet9.ProducerMarker;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet9.ProducerMethodStereotype;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet10.InvalidDisposesParameterProducerMethodFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet10.InvalidInjectProducerMethodFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet10.InvalidObservesAsyncParameterProducerMethodFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet10.InvalidObservesParameterProducerMethodFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet11.InvalidInterceptorProducerMethod;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet12.MultiParameterDependencyA;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet12.MultiParameterDependencyB;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet12.MultiParameterDependencyC;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet12.MultiParameterDependencyD;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet12.MultiParameterProducerFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet12.MultiParameterProduct;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet13.PaymentProcessor;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet13.ProducerMethodDefaultNameFactory;
import com.threeamigos.common.util.implementations.injection.resolution.ProducerBean;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.IllegalProductException;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.DefinitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("3.2 - Producer methods")
public class ProducerMethodsTest {

    /**
     * A producer method must be a default-access, public, protected or private,
     * non-abstract method of a managed bean class. A producer method may be either
     * static or non-static.
     */
    @Test
    @DisplayName("3.2 - Abstract producer method is a definition error")
    void abstractProducerMethodIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidAbstractProducerMethodBean.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    /**
     * If a producer method returns a null value at runtime and the producer method declares
     * any scope other than @Dependent, an IllegalProductException must be thrown.
     */
    @Test
    @DisplayName("3.2 - Non-@Dependent producer method returning null throws IllegalProductException")
    void nonDependentProducerMethodReturningNullThrowsIllegalProductException() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), NonDependentNullProducerFactory.class);
        syringe.setup();

        ProducerBean<?> producerBean = findProducerBeanByDeclaringClass(syringe, NonDependentNullProducerFactory.class);
        CreationalContext<?> creationalContext = syringe.getBeanManager().createCreationalContext(producerBean);

        assertThrows(IllegalProductException.class,
                () -> producerBean.create((CreationalContext) creationalContext));
    }

    /**
     * If a producer method can return null, @Dependent scope allows the product to be null.
     */
    @Test
    @DisplayName("3.2 - @Dependent producer method may return null")
    void dependentProducerMethodReturningNullIsAllowed() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), DependentNullProducerFactory.class);
        syringe.setup();

        ProducerBean<?> producerBean = findProducerBeanByDeclaringClass(syringe, DependentNullProducerFactory.class);
        CreationalContext<?> creationalContext = syringe.getBeanManager().createCreationalContext(producerBean);

        Object produced = producerBean.create((CreationalContext) creationalContext);
        assertNull(produced);
    }

    /**
     * If a producer method returns a parameterized type, each type parameter must be specified
     * with an actual type argument or type variable.
     */
    @Test
    @DisplayName("3.2 - Producer method with raw generic type argument is a definition error")
    void producerMethodWithRawGenericTypeArgumentIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidRawTypeArgumentProducerFactory.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    /**
     * If a producer method return type contains a wildcard type parameter,
     * the container must treat it as a definition error.
     */
    @Test
    @DisplayName("3.2 - Producer method with wildcard type parameter is a definition error")
    void producerMethodWithWildcardTypeParameterIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidWildcardProducerFactory.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    /**
     * If a producer method return type is an array type whose component type contains
     * a wildcard type parameter, the container must treat it as a definition error.
     */
    @Test
    @DisplayName("3.2 - Producer method with wildcard type parameter in array component is a definition error")
    void producerMethodWithWildcardTypeParameterInArrayComponentIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidWildcardArrayProducerFactory.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    /**
     * If a producer method return type is a parameterized type with a type variable,
     * the producer method is valid when its scope is @Dependent.
     */
    @Test
    @DisplayName("3.2 - Producer method with parameterized type variable return type is valid for @Dependent scope")
    void producerMethodWithTypeVariableReturnTypeIsValidForDependentScope() {
        InMemoryMessageHandler messageHandler = new InMemoryMessageHandler();
        Syringe syringe = new Syringe(messageHandler, DependentTypeVariableProducerFactory.class);
        syringe.exclude(NonDependentTypeVariableProducerFactory.class);

        assertDoesNotThrow(syringe::setup, () ->
                "Unexpected deployment errors: " + String.join(" | ", messageHandler.getAllErrorMessages()));
    }

    /**
     * If a producer method return type is a parameterized type with a type variable and
     * declares any scope other than @Dependent, the container must treat it as a definition error.
     */
    @Test
    @DisplayName("3.2 - Producer method with parameterized type variable return type is a definition error for non-@Dependent scope")
    void producerMethodWithTypeVariableReturnTypeIsDefinitionErrorForNonDependentScope() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), NonDependentTypeVariableProducerFactory.class);
        syringe.exclude(DependentTypeVariableProducerFactory.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    /**
     * If a producer method return type is a type variable,
     * the container must detect it and treat it as a definition error.
     */
    @Test
    @DisplayName("3.2 - Producer method with type variable return type is a definition error")
    void producerMethodWithTypeVariableReturnTypeIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidTypeVariableProducerFactory.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    /**
     * If a producer method return type is an array whose component type is a type variable,
     * the container must detect it and treat it as a definition error.
     */
    @Test
    @DisplayName("3.2 - Producer method with array component type variable return type is a definition error")
    void producerMethodWithArrayComponentTypeVariableReturnTypeIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidTypeVariableArrayProducerFactory.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.2.1 - Interface producer return type includes return interface, its superinterfaces and Object")
    void interfaceProducerReturnTypeIncludesHierarchyAndObject() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InterfaceReturnProducerFactory.class);
        syringe.setup();

        ProducerBean<?> producerBean = findProducerBeanByDeclaringClass(syringe, InterfaceReturnProducerFactory.class);
        Set<Type> beanTypes = producerBean.getTypes();

        assertEquals(3, beanTypes.size());
        assertTrue(beanTypes.contains(InterfaceReturnProducerFactory.ProducerSpecificContract.class));
        assertTrue(beanTypes.contains(InterfaceReturnProducerFactory.BaseContract.class));
        assertTrue(beanTypes.contains(Object.class));
    }

    @Test
    @DisplayName("3.2.1 - Primitive producer return type has exactly return type and Object")
    void primitiveProducerReturnTypeHasExactlyReturnTypeAndObject() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), PrimitiveReturnProducerFactory.class);
        syringe.setup();

        ProducerBean<?> producerBean = findProducerBeanByDeclaringClass(syringe, PrimitiveReturnProducerFactory.class);
        Set<Type> beanTypes = producerBean.getTypes();

        assertEquals(2, beanTypes.size());
        assertTrue(beanTypes.contains(int.class));
        assertTrue(beanTypes.contains(Object.class));
    }

    @Test
    @DisplayName("3.2.1 - Array producer return type has exactly return type and Object")
    void arrayProducerReturnTypeHasExactlyReturnTypeAndObject() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ArrayReturnProducerFactory.class);
        syringe.setup();

        ProducerBean<?> producerBean = findProducerBeanByDeclaringClass(syringe, ArrayReturnProducerFactory.class);
        Set<Type> beanTypes = producerBean.getTypes();

        assertEquals(2, beanTypes.size());
        assertTrue(beanTypes.contains(String[].class));
        assertTrue(beanTypes.contains(Object.class));
    }

    @Test
    @DisplayName("3.2.1 - Class producer return type includes class hierarchy, interfaces and Object")
    void classProducerReturnTypeIncludesClassHierarchyInterfacesAndObject() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ClassReturnProducerFactory.class);
        syringe.setup();

        ProducerBean<?> producerBean = findProducerBeanByDeclaringClass(syringe, ClassReturnProducerFactory.class);
        Set<Type> beanTypes = producerBean.getTypes();

        assertEquals(5, beanTypes.size());
        assertTrue(beanTypes.contains(ClassReturnProducerFactory.DetailedOrder.class));
        assertTrue(beanTypes.contains(ClassReturnProducerFactory.BaseOrder.class));
        assertTrue(beanTypes.contains(ClassReturnProducerFactory.Persistable.class));
        assertTrue(beanTypes.contains(ClassReturnProducerFactory.Auditable.class));
        assertTrue(beanTypes.contains(Object.class));
    }

    @Test
    @DisplayName("3.2.1 - Producer resulting bean types contain only legal bean types")
    void producerResultingBeanTypesContainOnlyLegalTypes() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), LegalTypePruningProducerFactory.class);
        syringe.setup();

        ProducerBean<?> producerBean = findProducerBeanByDeclaringClass(syringe, LegalTypePruningProducerFactory.class);
        Set<Type> beanTypes = producerBean.getTypes();
        Type producerReturnType = producerReturnType(LegalTypePruningProducerFactory.class, "produceBucket");

        assertEquals(3, beanTypes.size());
        assertTrue(beanTypes.contains(producerReturnType));
        assertTrue(beanTypes.contains(LegalTypePruningProducerFactory.VariableArrayBucket.class));
        assertTrue(beanTypes.contains(Object.class));
        assertFalse(beanTypes.stream().anyMatch(this::hasTypeVariableArrayArgument),
                "Illegal bean types should be removed from resulting producer bean types");
    }

    @Test
    @DisplayName("3.2.2 - Producer method may declare scope, bean name, stereotypes and qualifiers")
    void producerMethodMayDeclareScopeNameStereotypesAndQualifiers() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ConfiguredProducerFactory.class);
        syringe.setup();

        ProducerBean<?> producerBean = findProducerBeanByDeclaringClass(syringe, ConfiguredProducerFactory.class);

        assertEquals(ApplicationScoped.class, producerBean.getScope());
        assertEquals("configuredProduct", producerBean.getName());
        assertTrue(producerBean.getStereotypes().contains(ProducerMethodStereotype.class));
        assertTrue(producerBean.getQualifiers().stream()
                .anyMatch(qualifier -> qualifier.annotationType().equals(ProducerMarker.class)));
    }

    @Test
    @DisplayName("3.2.2 - Producer method annotated @Inject is a definition error")
    void producerMethodAnnotatedInjectIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidInjectProducerMethodFactory.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.2.2 - Producer method with @Disposes parameter is a definition error")
    void producerMethodWithDisposesParameterIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidDisposesParameterProducerMethodFactory.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.2.2 - Producer method with @Observes parameter is a definition error")
    void producerMethodWithObservesParameterIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidObservesParameterProducerMethodFactory.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.2.2 - Producer method with @ObservesAsync parameter is a definition error")
    void producerMethodWithObservesAsyncParameterIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidObservesAsyncParameterProducerMethodFactory.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.2.2 - Interceptor declaring producer method is a definition error")
    void interceptorDeclaringProducerMethodIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidInterceptorProducerMethod.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.2.2 - Producer method may have any number of parameters and all are injection points")
    void producerMethodParametersAreInjectionPoints() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), MultiParameterProducerFactory.class);
        syringe.setup();

        ProducerBean<?> producerBean = findProducerBeanByDeclaringClass(syringe, MultiParameterProducerFactory.class);

        assertEquals(4, producerBean.getInjectionPoints().size());
        Set<Type> injectionPointTypes = new HashSet<>();
        for (InjectionPoint injectionPoint : producerBean.getInjectionPoints()) {
            injectionPointTypes.add(injectionPoint.getType());
        }
        assertTrue(injectionPointTypes.contains(MultiParameterDependencyA.class));
        assertTrue(injectionPointTypes.contains(MultiParameterDependencyB.class));
        assertTrue(injectionPointTypes.contains(MultiParameterDependencyC.class));
        assertTrue(injectionPointTypes.contains(MultiParameterDependencyD.class));

        CreationalContext<?> creationalContext = syringe.getBeanManager().createCreationalContext(producerBean);
        Object produced = producerBean.create((CreationalContext) creationalContext);
        assertTrue(produced instanceof MultiParameterProduct);
        assertEquals("ABCD", ((MultiParameterProduct) produced).getSignature());
    }

    @Test
    @DisplayName("3.2.3 - Default bean name for getter-style producer method is JavaBeans property name")
    void defaultBeanNameForGetterStyleProducerMethodUsesJavaBeansPropertyName() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ProducerMethodDefaultNameFactory.class);
        syringe.setup();

        ProducerBean<?> producerBean = findProducerBeanByDeclaringClassAndMethodName(
                syringe, ProducerMethodDefaultNameFactory.class, "getProducts");

        assertTrue(producerBean.getTypes().contains(List.class));
        assertEquals("products", producerBean.getName());
    }

    @Test
    @DisplayName("3.2.3 - Default bean name for non-getter producer method is method name")
    void defaultBeanNameForNonGetterProducerMethodUsesMethodName() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ProducerMethodDefaultNameFactory.class);
        syringe.setup();

        ProducerBean<?> producerBean = findProducerBeanByDeclaringClassAndMethodName(
                syringe, ProducerMethodDefaultNameFactory.class, "paymentProcessor");

        assertTrue(producerBean.getTypes().contains(PaymentProcessor.class));
        assertEquals("paymentProcessor", producerBean.getName());
    }

    private ProducerBean<?> findProducerBeanByDeclaringClass(Syringe syringe, Class<?> declaringClass) {
        return syringe.getKnowledgeBase().getProducerBeans().stream()
                .filter(producerBean -> producerBean.getDeclaringClass().equals(declaringClass))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Producer bean not found for class: " + declaringClass.getName()));
    }

    private ProducerBean<?> findProducerBeanByDeclaringClassAndMethodName(Syringe syringe,
                                                                           Class<?> declaringClass,
                                                                           String methodName) {
        return syringe.getKnowledgeBase().getProducerBeans().stream()
                .filter(producerBean -> producerBean.getDeclaringClass().equals(declaringClass))
                .filter(producerBean -> producerBean.getProducerMethod() != null)
                .filter(producerBean -> producerBean.getProducerMethod().getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Producer bean not found for " + declaringClass.getName() + "#" + methodName));
    }

    private Type producerReturnType(Class<?> declaringClass, String methodName) {
        try {
            Method method = declaringClass.getDeclaredMethod(methodName);
            return method.getGenericReturnType();
        } catch (NoSuchMethodException e) {
            throw new AssertionError("Producer method not found: " + declaringClass.getName() + "#" + methodName, e);
        }
    }

    private boolean hasTypeVariableArrayArgument(Type type) {
        if (!(type instanceof ParameterizedType)) {
            return false;
        }
        ParameterizedType parameterizedType = (ParameterizedType) type;
        for (Type argument : parameterizedType.getActualTypeArguments()) {
            if (argument instanceof GenericArrayType &&
                    ((GenericArrayType) argument).getGenericComponentType() instanceof TypeVariable) {
                return true;
            }
        }
        return false;
    }

}
