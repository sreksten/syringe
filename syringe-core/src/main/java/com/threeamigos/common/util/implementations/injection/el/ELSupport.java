package com.threeamigos.common.util.implementations.injection.el;

import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import jakarta.el.ELContext;
import jakarta.el.ELResolver;
import jakarta.el.ExpressionFactory;
import jakarta.el.MethodExpression;
import jakarta.el.MethodInfo;
import jakarta.el.ValueExpression;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * EL support utilities for Syringe BeanManager integration.
 */
public final class ELSupport {

    private static final Object EL_UNRESOLVED = new Object();
    private static final Object EL_ABSENT = new Object();

    private ELSupport() {
        // Utility class
    }

    public static ELResolver createELResolver(BeanManagerImpl beanManager) {
        return new BeanManagerELResolver(Objects.requireNonNull(beanManager, "beanManager cannot be null"));
    }

    public static ExpressionFactory wrapExpressionFactory(BeanManagerImpl beanManager,
                                                          ExpressionFactory expressionFactory) {
        if (expressionFactory == null) {
            return null;
        }
        return new WrappedExpressionFactory(
                Objects.requireNonNull(beanManager, "beanManager cannot be null"),
                expressionFactory
        );
    }

    private static void beginEvaluation(ELContext context) {
        if (context == null) {
            return;
        }
        ELResolutionState state = getOrCreateResolutionState(context);
        state.beginEvaluation();
    }

    private static void endEvaluation(ELContext context) {
        if (context == null) {
            return;
        }
        ELResolutionState state = (ELResolutionState) context.getContext(ELResolutionState.class);
        if (state == null) {
            return;
        }
        state.endEvaluation();
    }

    private static ELResolutionState getOrCreateResolutionState(ELContext context) {
        ELResolutionState state = (ELResolutionState) context.getContext(ELResolutionState.class);
        if (state == null) {
            state = new ELResolutionState();
            context.putContext(ELResolutionState.class, state);
        }
        return state;
    }

    private static final class BeanManagerELResolver extends ELResolver {
        private final BeanManagerImpl beanManager;

        private BeanManagerELResolver(BeanManagerImpl beanManager) {
            this.beanManager = beanManager;
        }

        @Override
        public Object getValue(ELContext context, Object base, Object property) {
            if (context == null || property == null) {
                return null;
            }

            String propertyName = String.valueOf(property);
            Object resolved;

            if (base == null) {
                resolved = resolveNamedOrNamespace(context, propertyName);
                if (resolved == EL_UNRESOLVED) {
                    return null;
                }
                context.setPropertyResolved(true);
                return resolved == EL_ABSENT ? null : resolved;
            }

            if (base instanceof ELNamespace) {
                String qualifiedName = ((ELNamespace) base).append(propertyName);
                resolved = resolveNamedOrNamespace(context, qualifiedName);
                context.setPropertyResolved(true);
                return resolved == EL_ABSENT || resolved == EL_UNRESOLVED ? null : resolved;
            }

            resolved = readProperty(base, propertyName);
            if (resolved == EL_UNRESOLVED) {
                return null;
            }
            context.setPropertyResolved(true);
            return resolved;
        }

        @Override
        public Class<?> getType(ELContext context, Object base, Object property) {
            if (context == null || property == null) {
                return null;
            }

            String propertyName = String.valueOf(property);

            if (base == null) {
                if (hasNamedBean(propertyName) || hasNamedBeanPrefix(propertyName)) {
                    context.setPropertyResolved(true);
                    return Object.class;
                }
                return null;
            }

            if (base instanceof ELNamespace) {
                String qualifiedName = ((ELNamespace) base).append(propertyName);
                if (hasNamedBean(qualifiedName) || hasNamedBeanPrefix(qualifiedName)) {
                    context.setPropertyResolved(true);
                    return Object.class;
                }
                return null;
            }

            Class<?> type = readPropertyType(base.getClass(), propertyName);
            if (type != null) {
                context.setPropertyResolved(true);
            }
            return type;
        }

        @Override
        public void setValue(ELContext context, Object base, Object property, Object value) {
            if (context == null || base == null || property == null || base instanceof ELNamespace) {
                return;
            }
            if (writeProperty(base, String.valueOf(property), value)) {
                context.setPropertyResolved(true);
            }
        }

        @Override
        public boolean isReadOnly(ELContext context, Object base, Object property) {
            if (context == null || property == null) {
                return true;
            }
            if (base == null || base instanceof ELNamespace) {
                context.setPropertyResolved(true);
                return true;
            }
            String propertyName = String.valueOf(property);
            boolean writable = isWritable(base.getClass(), propertyName);
            if (writable) {
                context.setPropertyResolved(true);
            }
            return !writable;
        }

        @Override
        public Class<?> getCommonPropertyType(ELContext context, Object base) {
            return Object.class;
        }

        private Object resolveNamedOrNamespace(ELContext context, String qualifiedName) {
            ELResolutionState state = getOrCreateResolutionState(context);
            if (state.values.containsKey(qualifiedName)) {
                return state.values.get(qualifiedName);
            }

            Object beanReference = resolveNamedBean(qualifiedName, state);
            if (beanReference != EL_UNRESOLVED) {
                state.values.put(qualifiedName, beanReference);
                return beanReference;
            }

            if (hasNamedBeanPrefix(qualifiedName)) {
                ELNamespace namespace = state.namespace(qualifiedName);
                state.values.put(qualifiedName, namespace);
                return namespace;
            }

            state.values.put(qualifiedName, EL_ABSENT);
            return EL_ABSENT;
        }

        private Object resolveNamedBean(String name, ELResolutionState state) {
            Set<Bean<?>> beans = getBeansForName(name);
            if (beans.isEmpty()) {
                return EL_UNRESOLVED;
            }

            Bean<?> resolvedBean = beanManager.resolve(beans);
            if (resolvedBean == null) {
                return EL_UNRESOLVED;
            }

            @SuppressWarnings({"rawtypes", "unchecked"})
            CreationalContext<?> creationalContext =
                    beanManager.createCreationalContext((Bean) resolvedBean);
            Class<?> beanClass = resolvedBean.getBeanClass() != null ? resolvedBean.getBeanClass() : Object.class;
            Object reference = beanManager.getReference(resolvedBean, beanClass, creationalContext);
            state.trackCreationalContext(creationalContext);
            return reference;
        }

        private Set<Bean<?>> getBeansForName(String name) {
            try {
                return beanManager.getBeans(name);
            } catch (IllegalArgumentException ignored) {
                return Collections.emptySet();
            }
        }

        private boolean hasNamedBean(String name) {
            return !getBeansForName(name).isEmpty();
        }

        private boolean hasNamedBeanPrefix(String prefix) {
            if (prefix == null || prefix.isEmpty()) {
                return false;
            }
            String expectedPrefix = prefix + ".";
            for (Bean<?> bean : beanManager.getKnowledgeBase().getValidBeans()) {
                String beanName = bean.getName();
                if (beanName != null && beanName.startsWith(expectedPrefix)) {
                    return true;
                }
            }
            return false;
        }

        private Object readProperty(Object base, String propertyName) {
            if (base instanceof Map<?, ?>) {
                Map<?, ?> map = (Map<?, ?>) base;
                return map.containsKey(propertyName) ? map.get(propertyName) : EL_UNRESOLVED;
            }

            Method reader = findReader(base.getClass(), propertyName);
            if (reader != null) {
                try {
                    reader.setAccessible(true);
                    return reader.invoke(base);
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to read property '" + propertyName + "'", e);
                }
            }

            Field field = findField(base.getClass(), propertyName);
            if (field != null) {
                try {
                    field.setAccessible(true);
                    return field.get(base);
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to read field '" + propertyName + "'", e);
                }
            }

            return EL_UNRESOLVED;
        }

        private Class<?> readPropertyType(Class<?> type, String propertyName) {
            Method reader = findReader(type, propertyName);
            if (reader != null) {
                return reader.getReturnType();
            }
            Field field = findField(type, propertyName);
            return field != null ? field.getType() : null;
        }

        private boolean writeProperty(Object base, String propertyName, Object value) {
            Method writer = findWriter(base.getClass(), propertyName, value);
            if (writer != null) {
                try {
                    writer.setAccessible(true);
                    writer.invoke(base, value);
                    return true;
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to write property '" + propertyName + "'", e);
                }
            }

            Field field = findField(base.getClass(), propertyName);
            if (field != null && !Modifier.isFinal(field.getModifiers())) {
                try {
                    field.setAccessible(true);
                    field.set(base, value);
                    return true;
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to write field '" + propertyName + "'", e);
                }
            }
            return false;
        }

        private boolean isWritable(Class<?> type, String propertyName) {
            if (findWriter(type, propertyName, null) != null) {
                return true;
            }
            Field field = findField(type, propertyName);
            return field != null && !Modifier.isFinal(field.getModifiers());
        }

        private Method findReader(Class<?> type, String propertyName) {
            String suffix = propertySuffix(propertyName);
            Method getter = findNoArgMethod(type, "get" + suffix);
            if (getter != null) {
                return getter;
            }
            Method booleanGetter = findNoArgMethod(type, "is" + suffix);
            if (booleanGetter != null &&
                    (boolean.class.equals(booleanGetter.getReturnType()) || Boolean.class.equals(booleanGetter.getReturnType()))) {
                return booleanGetter;
            }
            return null;
        }

        private Method findWriter(Class<?> type, String propertyName, Object value) {
            String methodName = "set" + propertySuffix(propertyName);
            Class<?> current = type;
            while (current != null && !Object.class.equals(current)) {
                for (Method method : current.getDeclaredMethods()) {
                    if (!methodName.equals(method.getName()) || method.getParameterCount() != 1) {
                        continue;
                    }
                    if (Modifier.isStatic(method.getModifiers())) {
                        continue;
                    }
                    Class<?> parameterType = method.getParameterTypes()[0];
                    if (value == null || parameterType.isInstance(value) || isWrapperAssignable(parameterType, value.getClass())) {
                        return method;
                    }
                }
                current = current.getSuperclass();
            }
            return null;
        }

        private Method findNoArgMethod(Class<?> type, String methodName) {
            for (Method method : type.getMethods()) {
                if (methodName.equals(method.getName()) &&
                        method.getParameterCount() == 0 &&
                        !Modifier.isStatic(method.getModifiers())) {
                    return method;
                }
            }

            Class<?> current = type;
            while (current != null && !Object.class.equals(current)) {
                for (Method method : current.getDeclaredMethods()) {
                    if (methodName.equals(method.getName()) &&
                            method.getParameterCount() == 0 &&
                            !Modifier.isStatic(method.getModifiers())) {
                        return method;
                    }
                }
                current = current.getSuperclass();
            }
            return null;
        }

        private Field findField(Class<?> type, String fieldName) {
            Class<?> current = type;
            while (current != null && !Object.class.equals(current)) {
                try {
                    return current.getDeclaredField(fieldName);
                } catch (NoSuchFieldException ignored) {
                    current = current.getSuperclass();
                }
            }
            return null;
        }

        private String propertySuffix(String propertyName) {
            if (propertyName == null || propertyName.isEmpty()) {
                return "";
            }
            if (propertyName.length() == 1) {
                return propertyName.toUpperCase(Locale.ROOT);
            }
            return Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        }

        private boolean isWrapperAssignable(Class<?> targetType, Class<?> valueType) {
            if (targetType == null || valueType == null) {
                return false;
            }
            if (!targetType.isPrimitive()) {
                return false;
            }
            if (boolean.class.equals(targetType)) {
                return Boolean.class.equals(valueType);
            }
            if (byte.class.equals(targetType)) {
                return Byte.class.equals(valueType);
            }
            if (short.class.equals(targetType)) {
                return Short.class.equals(valueType) || Byte.class.equals(valueType);
            }
            if (int.class.equals(targetType)) {
                return Integer.class.equals(valueType) || Short.class.equals(valueType) || Byte.class.equals(valueType);
            }
            if (long.class.equals(targetType)) {
                return Long.class.equals(valueType) || Integer.class.equals(valueType)
                        || Short.class.equals(valueType) || Byte.class.equals(valueType);
            }
            if (float.class.equals(targetType)) {
                return Float.class.equals(valueType) || Long.class.equals(valueType)
                        || Integer.class.equals(valueType) || Short.class.equals(valueType) || Byte.class.equals(valueType);
            }
            if (double.class.equals(targetType)) {
                return Double.class.equals(valueType) || Float.class.equals(valueType)
                        || Long.class.equals(valueType) || Integer.class.equals(valueType)
                        || Short.class.equals(valueType) || Byte.class.equals(valueType);
            }
            if (char.class.equals(targetType)) {
                return Character.class.equals(valueType);
            }
            return false;
        }
    }

    private static final class WrappedExpressionFactory extends ExpressionFactory implements Serializable {
        private static final long serialVersionUID = 1L;
        private final ExpressionFactory delegate;

        private WrappedExpressionFactory(BeanManagerImpl beanManager, ExpressionFactory delegate) {
            this.delegate = delegate;
        }

        @Override
        public ValueExpression createValueExpression(ELContext context, String expression, Class<?> expectedType) {
            ValueExpression valueExpression = delegate.createValueExpression(context, expression, expectedType);
            if (valueExpression == null) {
                return null;
            }
            return new WrappedValueExpression(valueExpression);
        }

        @Override
        public ValueExpression createValueExpression(Object instance, Class<?> expectedType) {
            ValueExpression valueExpression = delegate.createValueExpression(instance, expectedType);
            if (valueExpression == null) {
                return null;
            }
            return new WrappedValueExpression(valueExpression);
        }

        @Override
        public MethodExpression createMethodExpression(ELContext context,
                                                       String expression,
                                                       Class<?> expectedReturnType,
                                                       Class<?>[] expectedParamTypes) {
            MethodExpression methodExpression =
                    delegate.createMethodExpression(context, expression, expectedReturnType, expectedParamTypes);
            if (methodExpression == null) {
                return null;
            }
            return new WrappedMethodExpression(methodExpression);
        }

        @Override
        public <T> T coerceToType(Object o, Class<T> aClass) {
            return delegate.coerceToType(o, aClass);
        }
    }

    private static final class WrappedMethodExpression extends MethodExpression {
        private static final long serialVersionUID = 1L;
        private final MethodExpression delegate;

        private WrappedMethodExpression(MethodExpression delegate) {
            this.delegate = delegate;
        }

        @Override
        public MethodInfo getMethodInfo(ELContext context) {
            return delegate.getMethodInfo(context);
        }

        @Override
        public Object invoke(ELContext context, Object[] params) {
            beginEvaluation(context);
            try {
                return delegate.invoke(context, params);
            } finally {
                endEvaluation(context);
            }
        }

        @Override
        public String getExpressionString() {
            return delegate.getExpressionString();
        }

        @Override
        public boolean equals(Object obj) {
            return delegate.equals(obj);
        }

        @Override
        public int hashCode() {
            return delegate.hashCode();
        }

        @Override
        public boolean isLiteralText() {
            return delegate.isLiteralText();
        }
    }

    private static final class WrappedValueExpression extends ValueExpression {
        private static final long serialVersionUID = 1L;
        private final ValueExpression delegate;

        private WrappedValueExpression(ValueExpression delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object getValue(ELContext context) {
            beginEvaluation(context);
            try {
                return delegate.getValue(context);
            } finally {
                endEvaluation(context);
            }
        }

        @Override
        public void setValue(ELContext context, Object value) {
            beginEvaluation(context);
            try {
                delegate.setValue(context, value);
            } finally {
                endEvaluation(context);
            }
        }

        @Override
        public boolean isReadOnly(ELContext context) {
            beginEvaluation(context);
            try {
                return delegate.isReadOnly(context);
            } finally {
                endEvaluation(context);
            }
        }

        @Override
        public Class<?> getType(ELContext context) {
            beginEvaluation(context);
            try {
                return delegate.getType(context);
            } finally {
                endEvaluation(context);
            }
        }

        @Override
        public Class<?> getExpectedType() {
            return delegate.getExpectedType();
        }

        @Override
        public String getExpressionString() {
            return delegate.getExpressionString();
        }

        @Override
        public boolean equals(Object obj) {
            return delegate.equals(obj);
        }

        @Override
        public int hashCode() {
            return delegate.hashCode();
        }

        @Override
        public boolean isLiteralText() {
            return delegate.isLiteralText();
        }
    }

    private static final class ELResolutionState {
        private final Map<String, Object> values = new HashMap<>();
        private final Map<String, ELNamespace> namespaces = new HashMap<>();
        private final List<CreationalContext<?>> creationalContexts = new ArrayList<>();
        private int evaluationDepth;

        private ELNamespace namespace(String prefix) {
            ELNamespace existing = namespaces.get(prefix);
            if (existing != null) {
                return existing;
            }
            ELNamespace created = new ELNamespace(prefix);
            namespaces.put(prefix, created);
            return created;
        }

        private void trackCreationalContext(CreationalContext<?> creationalContext) {
            if (creationalContext != null) {
                creationalContexts.add(creationalContext);
            }
        }

        private void beginEvaluation() {
            evaluationDepth++;
        }

        private void endEvaluation() {
            if (evaluationDepth > 0) {
                evaluationDepth--;
            }
            if (evaluationDepth == 0) {
                for (int i = creationalContexts.size() - 1; i >= 0; i--) {
                    CreationalContext<?> creationalContext = creationalContexts.get(i);
                    if (creationalContext == null) {
                        continue;
                    }
                    try {
                        creationalContext.release();
                    } catch (Exception ignored) {
                        // Best-effort destruction only.
                    }
                }
                creationalContexts.clear();
                values.clear();
                namespaces.clear();
            }
        }
    }

    private static final class ELNamespace {
        private final String prefix;

        private ELNamespace(String prefix) {
            this.prefix = prefix;
        }

        private String append(String child) {
            return prefix + "." + child;
        }
    }
}
