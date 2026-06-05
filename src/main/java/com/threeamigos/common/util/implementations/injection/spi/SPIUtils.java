package com.threeamigos.common.util.implementations.injection.spi;

import com.threeamigos.common.util.implementations.injection.bce.BceRegistrationContext;
import jakarta.enterprise.inject.build.compatible.spi.*;
import jakarta.enterprise.inject.spi.Prioritized;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.FieldInfo;
import jakarta.enterprise.lang.model.declarations.MethodInfo;
import jakarta.enterprise.lang.model.declarations.ParameterInfo;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SPIUtils {

    public static boolean isBeanInfo(Class<?> parameterType) {
        return BeanInfo.class.isAssignableFrom(parameterType);
    }

    public static boolean isObserverInfo(Class<?> parameterType) {
        return ObserverInfo.class.isAssignableFrom(parameterType);
    }

    public static boolean isInterceptorInfo(Class<?> parameterType) {
        return InterceptorInfo.class.isAssignableFrom(parameterType);
    }

    public static boolean isInjectionPointInfo(Class<?> parameterType) {
        return InjectionPointInfo.class.isAssignableFrom(parameterType);
    }

    public static boolean isDisposerInfo(Class<?> parameterType) {
        return DisposerInfo.class.isAssignableFrom(parameterType);
    }

    public static boolean isScopeInfo(Class<?> parameterType) {
        return ScopeInfo.class.isAssignableFrom(parameterType);
    }

    public static boolean isStereotypeInfo(Class<?> parameterType) {
        return StereotypeInfo.class.isAssignableFrom(parameterType);
    }

    public static boolean isInvokerFactory(Class<?> parameterType) {
        return InvokerFactory.class.isAssignableFrom(parameterType);
    }

    public static boolean isBceRegistrationContext(Class<?> parameterType) {
        return BceRegistrationContext.class.isAssignableFrom(parameterType);
    }

    public static boolean isBuildServices(Class<?> parameterType) {
        return BuildServices.class.isAssignableFrom(parameterType);
    }

    public static boolean isTypes(Class<?> parameterType) {
        return Types.class.isAssignableFrom(parameterType);
    }

    public static boolean isMessages(Class<?> parameterType) {
        return Messages.class.isAssignableFrom(parameterType);
    }

    public static boolean isAnnotation(Class<?> parameterType) {
        return Annotation.class.isAssignableFrom(parameterType);
    }

    public static boolean isMetaAnnotations(Class<?> parameterType) {
        return MetaAnnotations.class.isAssignableFrom(parameterType);
    }

    public static boolean isScannedClasses(Class<?> parameterType) {
        return ScannedClasses.class.isAssignableFrom(parameterType);
    }

    public static boolean isClassInfo(Class<?> parameterType) {
        return ClassInfo.class.isAssignableFrom(parameterType);
    }

    public static boolean isClassConfig(Class<?> parameterType) {
        return ClassConfig.class.isAssignableFrom(parameterType);
    }

    public static boolean isMethodInfo(Class<?> parameterType) {
        return MethodInfo.class.isAssignableFrom(parameterType);
    }

    public static boolean isMethodConfig(Class<?> parameterType) {
        return MethodConfig.class.isAssignableFrom(parameterType);
    }

    public static boolean isFieldInfo(Class<?> parameterType) {
        return FieldInfo.class.isAssignableFrom(parameterType);
    }

    public static boolean isFieldConfig(Class<?> parameterType) {
        return FieldConfig.class.isAssignableFrom(parameterType);
    }

    public static boolean isParameterInfo(Class<?> parameterType) {
        return ParameterInfo.class.isAssignableFrom(parameterType);
    }

    public static boolean isParameterConfig(Class<?> parameterType) {
        return ParameterConfig.class.isAssignableFrom(parameterType);
    }

    public static boolean isContainerLifecycleObservedType(Class<?> observedType) {
        String name = observedType.getName();
        return name.startsWith("jakarta.enterprise.inject.spi.Process") ||
                "jakarta.enterprise.inject.spi.BeforeBeanDiscovery".equals(name) ||
                "jakarta.enterprise.inject.spi.AfterTypeDiscovery".equals(name) ||
                "jakarta.enterprise.inject.spi.AfterBeanDiscovery".equals(name) ||
                "jakarta.enterprise.inject.spi.AfterDeploymentValidation".equals(name) ||
                "jakarta.enterprise.inject.spi.BeforeShutdown".equals(name);
    }

    public static boolean isDefinitionErrorLifecycleEvent(Class<?> eventType) {
        Set<String> typeNames = collectTypeNames(eventType);
        return typeNames.contains("jakarta.enterprise.inject.spi.BeforeBeanDiscovery") ||
                typeNames.contains("jakarta.enterprise.inject.spi.AfterTypeDiscovery") ||
                typeNames.contains("jakarta.enterprise.inject.spi.AfterBeanDiscovery");
    }

    public static boolean isAfterDeploymentValidationLifecycleEvent(Class<?> eventType) {
        return collectTypeNames(eventType).contains("jakarta.enterprise.inject.spi.AfterDeploymentValidation");
    }

    public static boolean isBeforeShutdownLifecycleEvent(Class<?> eventType) {
        return collectTypeNames(eventType).contains("jakarta.enterprise.inject.spi.BeforeShutdown");
    }

    private static Set<String> collectTypeNames(Class<?> eventType) {
        if (eventType == null) {
            return Collections.emptySet();
        }
        Set<String> typeNames = new HashSet<>();
        Class<?> current = eventType;
        while (current != null) {
            typeNames.add(current.getName());
            for (Class<?> face : current.getInterfaces()) {
                typeNames.add(face.getName());
            }
            current = current.getSuperclass();
        }
        return typeNames;
    }

    public static Integer extractPrioritizedInterfacePriority(Class<?> candidate) {
        if (candidate == null || !Prioritized.class.isAssignableFrom(candidate)) {
            return null;
        }
        if (candidate.isInterface() || Modifier.isAbstract(candidate.getModifiers())) {
            return null;
        }

        try {
            Constructor<?> constructor = candidate.getDeclaredConstructor();
            if (!constructor.isAccessible()) {
                constructor.setAccessible(true);
            }
            Prioritized prioritized = (Prioritized) constructor.newInstance();
            return prioritized.getPriority();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

}
