package com.threeamigos.common.util.implementations.injection.bce;

import com.threeamigos.common.util.implementations.injection.util.BoxedPrimitivesHelper;
import jakarta.enterprise.inject.build.compatible.spi.InvokerInfo;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.invoke.Invoker;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.types.Type;
import jakarta.enterprise.lang.model.declarations.ClassInfo;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.threeamigos.common.util.implementations.injection.spi.SPIUtils.isAnnotation;

/**
 * Parameters implementation that materializes InvokerInfo tokens into runtime Invokers.
 */
public class BceParameters implements Parameters {

    private final Map<String, Object> values;
    private final BceInvokerRegistry invokerRegistry;

    public BceParameters(Map<String, Object> values, BceInvokerRegistry invokerRegistry) {
        this.values = values != null ? new HashMap<>(values) : new HashMap<>();
        this.invokerRegistry = invokerRegistry;
    }

    @Override
    public <T> T get(String name, Class<T> type) {
        Objects.requireNonNull(name, "key cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        if (!values.containsKey(name)) {
            return null;
        }
        Object raw = values.get(name);
        return convert(name, type, raw);
    }

    @Override
    public <T> T get(String name, Class<T> type, T defaultValue) {
        Objects.requireNonNull(name, "key cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        if (!values.containsKey(name)) {
            return defaultValue;
        }
        Object raw = values.get(name);
        return convert(name, type, raw);
    }

    @SuppressWarnings("unchecked")
    private <T> T convert(String name, Class<T> type, Object raw) {
        if (raw == null) {
            return null;
        }

        Object converted = raw;
        if (raw instanceof InvokerInfo) {
            converted = materializeInvoker((InvokerInfo) raw);
        } else if (raw instanceof InvokerInfo[]) {
            InvokerInfo[] infos = (InvokerInfo[]) raw;
            @SuppressWarnings("rawtypes")
            Invoker[] invokers = new Invoker[infos.length];
            for (int i = 0; i < infos.length; i++) {
                invokers[i] = materializeInvoker(infos[i]);
            }
            converted = invokers;
        } else if (raw instanceof ClassInfo) {
            converted = BceMetadata.unwrapClassInfo((ClassInfo) raw);
        } else if (raw instanceof ClassInfo[]) {
            converted = BceMetadata.unwrapClassInfo((ClassInfo[]) raw);
        } else if (raw instanceof AnnotationInfo) {
            converted = BceMetadata.unwrapAnnotationInfo((AnnotationInfo) raw);
        } else if (raw instanceof AnnotationInfo[]) {
            converted = BceMetadata.unwrapAnnotationInfo((AnnotationInfo[]) raw);
        } else if (raw instanceof Type) {
            converted = BceMetadata.unwrapType((Type) raw);
        }

        if (converted != null && !type.isInstance(converted)) {
            Class<?> effectiveType = boxedPrimitive(type);
            if (converted instanceof Annotation[] &&
                effectiveType.isArray() &&
                isAnnotation(effectiveType.getComponentType())) {
                Annotation[] source = (Annotation[]) converted;
                Object typedArray = Array.newInstance(effectiveType.getComponentType(), source.length);
                for (int i = 0; i < source.length; i++) {
                    Array.set(typedArray, i, source[i]);
                }
                converted = typedArray;
            }
            if (!effectiveType.isInstance(converted)) {
                throw new ClassCastException("Parameter '" + name + "' cannot be cast to " +
                    type.getName() + " (actual: " + converted.getClass().getName() + ")");
            }
        }
        return (T) converted;
    }

    @SuppressWarnings("unchecked")
    private Invoker<Object, Object> materializeInvoker(InvokerInfo info) {
        if (invokerRegistry == null) {
            throw new IllegalStateException("No invoker registry available to materialize InvokerInfo");
        }
        return (Invoker<Object, Object>) invokerRegistry.resolve(info);
    }

    private Class<?> boxedPrimitive(Class<?> type) {
        if (type == null || !type.isPrimitive()) {
            return type;
        }
        Class<?> boxedType = BoxedPrimitivesHelper.getBoxedPrimitive(type);
        return boxedType != null ? boxedType : type;
    }
}
