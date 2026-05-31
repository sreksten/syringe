package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet1.ValidVisibilityAndStaticProducerFieldsFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet2.DependentNullProducerFieldFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet2.NonDependentNullProducerFieldFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet3.InvalidRawTypeArgumentProducerFieldFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet4.InvalidWildcardArrayProducerFieldFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet4.InvalidWildcardProducerFieldFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet5.DependentTypeVariableProducerFieldFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet6.NonDependentTypeVariableProducerFieldFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet7.InvalidTypeVariableArrayProducerFieldFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet7.InvalidTypeVariableProducerFieldFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet8.ArrayFieldTypeProducerFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet8.ClassFieldTypeProducerFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet8.InterfaceFieldTypeProducerFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet8.LegalTypePruningProducerFieldFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet8.PrimitiveFieldTypeProducerFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet9.InvalidInjectProducerFieldFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet9.InvalidInterceptorProducerField;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet10.ProducerFieldDefaultNameFactory;
import com.threeamigos.common.util.implementations.injection.resolution.ProducerBean;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.IllegalProductException;
import jakarta.enterprise.inject.spi.DefinitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("3.3 - Producer fields")
public class ProducerFieldsTest {

    @Test
    @DisplayName("3.3 - Producer field may have any Java access modifier and may be static or non-static")
    void producerFieldVisibilityAndStaticnessAreAllowed() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ValidVisibilityAndStaticProducerFieldsFactory.class);

        assertDoesNotThrow(syringe::setup);

        Set<ProducerBean<?>> producerBeans = producerBeansByDeclaringClass(
                syringe, ValidVisibilityAndStaticProducerFieldsFactory.class);
        assertEquals(6, producerBeans.size());

        Set<String> fieldNames = producerBeans.stream()
                .map(ProducerBean::getProducerField)
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertTrue(fieldNames.contains("packagePrivateValue"));
        assertTrue(fieldNames.contains("publicValue"));
        assertTrue(fieldNames.contains("protectedValue"));
        assertTrue(fieldNames.contains("privateValue"));
        assertTrue(fieldNames.contains("staticValue"));
        assertTrue(fieldNames.contains("nonStaticByteValue"));
    }

    @Test
    @DisplayName("3.3 - Non-@Dependent producer field containing null throws IllegalProductException")
    void nonDependentProducerFieldContainingNullThrowsIllegalProductException() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), NonDependentNullProducerFieldFactory.class);
        syringe.setup();

        ProducerBean<?> producerBean = findProducerBeanByDeclaringClassAndFieldName(
                syringe, NonDependentNullProducerFieldFactory.class, "runtimeValue");
        CreationalContext<?> creationalContext = syringe.getBeanManager().createCreationalContext(producerBean);

        assertThrows(IllegalProductException.class,
                () -> producerBean.create((CreationalContext) creationalContext));
    }

    @Test
    @DisplayName("3.3 - @Dependent producer field may contain null")
    void dependentProducerFieldContainingNullIsAllowed() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), DependentNullProducerFieldFactory.class);
        syringe.setup();

        ProducerBean<?> producerBean = findProducerBeanByDeclaringClassAndFieldName(
                syringe, DependentNullProducerFieldFactory.class, "runtimeValue");
        CreationalContext<?> creationalContext = syringe.getBeanManager().createCreationalContext(producerBean);

        Object produced = producerBean.create((CreationalContext) creationalContext);
        assertNull(produced);
    }

    @Test
    @DisplayName("3.3 - Producer field with raw generic type argument is a definition error")
    void producerFieldWithRawGenericTypeArgumentIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidRawTypeArgumentProducerFieldFactory.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.3 - Producer field type containing wildcard is a definition error")
    void producerFieldTypeContainingWildcardIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidWildcardProducerFieldFactory.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.3 - Producer field array component type containing wildcard is a definition error")
    void producerFieldArrayComponentTypeContainingWildcardIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidWildcardArrayProducerFieldFactory.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.3 - Producer field with parameterized type variable is valid for @Dependent scope")
    void producerFieldWithParameterizedTypeVariableIsValidForDependentScope() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), DependentTypeVariableProducerFieldFactory.class);

        assertDoesNotThrow(syringe::setup);
    }

    @Test
    @DisplayName("3.3 - Producer field with parameterized type variable is a definition error for non-@Dependent scope")
    void producerFieldWithParameterizedTypeVariableIsDefinitionErrorForNonDependentScope() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), NonDependentTypeVariableProducerFieldFactory.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.3 - Producer field type variable is a definition error")
    void producerFieldTypeVariableIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidTypeVariableProducerFieldFactory.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.3 - Producer field array component type variable is a definition error")
    void producerFieldArrayComponentTypeVariableIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidTypeVariableArrayProducerFieldFactory.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.3.1 - Interface producer field type includes field type, its superinterfaces and Object")
    void interfaceProducerFieldTypeIncludesHierarchyAndObject() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InterfaceFieldTypeProducerFactory.class);
        syringe.setup();

        ProducerBean<?> producerBean = findProducerBeanByDeclaringClassAndFieldName(
                syringe, InterfaceFieldTypeProducerFactory.class, "producedContract");
        Set<Type> beanTypes = producerBean.getTypes();

        assertEquals(4, beanTypes.size());
        assertTrue(beanTypes.contains(InterfaceFieldTypeProducerFactory.ProducerSpecificContract.class));
        assertTrue(beanTypes.contains(InterfaceFieldTypeProducerFactory.BaseContract.class));
        assertTrue(beanTypes.contains(InterfaceFieldTypeProducerFactory.RootContract.class));
        assertTrue(beanTypes.contains(Object.class));
    }

    @Test
    @DisplayName("3.3.1 - Primitive producer field type has exactly field type and Object")
    void primitiveProducerFieldTypeHasExactlyFieldTypeAndObject() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), PrimitiveFieldTypeProducerFactory.class);
        syringe.setup();

        ProducerBean<?> producerBean = findProducerBeanByDeclaringClassAndFieldName(
                syringe, PrimitiveFieldTypeProducerFactory.class, "producedPrimitive");
        Set<Type> beanTypes = producerBean.getTypes();

        assertEquals(2, beanTypes.size());
        assertTrue(beanTypes.contains(int.class));
        assertTrue(beanTypes.contains(Object.class));
    }

    @Test
    @DisplayName("3.3.1 - Array producer field type has exactly field type and Object")
    void arrayProducerFieldTypeHasExactlyFieldTypeAndObject() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ArrayFieldTypeProducerFactory.class);
        syringe.setup();

        ProducerBean<?> producerBean = findProducerBeanByDeclaringClassAndFieldName(
                syringe, ArrayFieldTypeProducerFactory.class, "producedArray");
        Set<Type> beanTypes = producerBean.getTypes();

        assertEquals(2, beanTypes.size());
        assertTrue(beanTypes.contains(String[].class));
        assertTrue(beanTypes.contains(Object.class));
    }

    @Test
    @DisplayName("3.3.1 - Class producer field type includes class hierarchy, interfaces and Object")
    void classProducerFieldTypeIncludesClassHierarchyInterfacesAndObject() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ClassFieldTypeProducerFactory.class);
        syringe.setup();

        ProducerBean<?> producerBean = findProducerBeanByDeclaringClassAndFieldName(
                syringe, ClassFieldTypeProducerFactory.class, "producedOrder");
        Set<Type> beanTypes = producerBean.getTypes();

        assertEquals(6, beanTypes.size());
        assertTrue(beanTypes.contains(ClassFieldTypeProducerFactory.DetailedOrder.class));
        assertTrue(beanTypes.contains(ClassFieldTypeProducerFactory.BaseOrder.class));
        assertTrue(beanTypes.contains(ClassFieldTypeProducerFactory.Persistable.class));
        assertTrue(beanTypes.contains(ClassFieldTypeProducerFactory.Traceable.class));
        assertTrue(beanTypes.contains(ClassFieldTypeProducerFactory.Auditable.class));
        assertTrue(beanTypes.contains(Object.class));
    }

    @Test
    @DisplayName("3.3.1 - Producer field resulting bean types contain only legal bean types")
    void producerFieldResultingBeanTypesContainOnlyLegalTypes() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), LegalTypePruningProducerFieldFactory.class);
        syringe.setup();

        ProducerBean<?> producerBean = findProducerBeanByDeclaringClassAndFieldName(
                syringe, LegalTypePruningProducerFieldFactory.class, "producedBucket");
        Set<Type> beanTypes = producerBean.getTypes();
        Type producerFieldType = producerFieldType(LegalTypePruningProducerFieldFactory.class, "producedBucket");

        assertEquals(3, beanTypes.size());
        assertTrue(beanTypes.contains(producerFieldType));
        assertTrue(beanTypes.contains(LegalTypePruningProducerFieldFactory.VariableArrayBucket.class));
        assertTrue(beanTypes.contains(Object.class));
        assertFalse(beanTypes.stream().anyMatch(this::hasTypeVariableArrayArgument),
                "Illegal bean types should be removed from resulting producer bean types");
    }

    @Test
    @DisplayName("3.3.2 - Producer field annotated @Inject is a definition error")
    void producerFieldAnnotatedInjectIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidInjectProducerFieldFactory.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.3.2 - Interceptor declaring producer field is a definition error")
    void interceptorDeclaringProducerFieldIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidInterceptorProducerField.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.3.3 - Default bean name for producer field is field name")
    void defaultBeanNameForProducerFieldIsFieldName() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ProducerFieldDefaultNameFactory.class);
        syringe.setup();

        ProducerBean<?> producerBean = findProducerBeanByDeclaringClassAndFieldName(
                syringe, ProducerFieldDefaultNameFactory.class, "paymentProcessor");

        assertTrue(producerBean.getTypes().contains(ProducerFieldDefaultNameFactory.PaymentProcessor.class));
        assertEquals("paymentProcessor", producerBean.getName());
    }

    private Set<ProducerBean<?>> producerBeansByDeclaringClass(Syringe syringe, Class<?> declaringClass) {
        return syringe.getKnowledgeBase().getProducerBeans().stream()
                .filter(producerBean -> producerBean.getDeclaringClass().equals(declaringClass))
                .collect(Collectors.toSet());
    }

    private ProducerBean<?> findProducerBeanByDeclaringClassAndFieldName(Syringe syringe,
                                                                          Class<?> declaringClass,
                                                                          String fieldName) {
        return syringe.getKnowledgeBase().getProducerBeans().stream()
                .filter(producerBean -> producerBean.getDeclaringClass().equals(declaringClass))
                .filter(producerBean -> producerBean.getProducerField() != null)
                .filter(producerBean -> producerBean.getProducerField().getName().equals(fieldName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Producer bean not found for " + declaringClass.getName() + "#" + fieldName));
    }

    private Type producerFieldType(Class<?> declaringClass, String fieldName) {
        try {
            Field field = declaringClass.getDeclaredField(fieldName);
            return field.getGenericType();
        } catch (NoSuchFieldException e) {
            throw new AssertionError("Producer field not found: " + declaringClass.getName() + "#" + fieldName, e);
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
