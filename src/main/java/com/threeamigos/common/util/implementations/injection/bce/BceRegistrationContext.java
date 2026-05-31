package com.threeamigos.common.util.implementations.injection.bce;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import jakarta.enterprise.inject.build.compatible.spi.BeanInfo;
import jakarta.enterprise.lang.model.declarations.MethodInfo;
import jakarta.enterprise.inject.spi.Bean;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.threeamigos.common.util.implementations.injection.types.ClassHelper.packageName;

/**
 * Registration-phase view over container-discovered bean and method metadata.
 */
public class BceRegistrationContext {

    private final KnowledgeBase knowledgeBase;

    BceRegistrationContext(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    public Collection<BeanInfo> beans() {
        Set<BeanInfo> result = new LinkedHashSet<>();
        for (Bean<?> bean : knowledgeBase.getBeans()) {
            Class<?> beanClass = bean.getBeanClass();
            if (beanClass == null) {
                continue;
            }
            // Ignore container internals and synthetic runtime placeholders.
            if (beanClass.getName().startsWith(packageName(Syringe.class))) {
                continue;
            }
            result.add(BceMetadata.beanInfo(beanClass));
        }
        return Collections.unmodifiableSet(result);
    }

    public BeanInfo bean(Class<?> beanClass) {
        for (Bean<?> bean : knowledgeBase.getBeans()) {
            if (bean.getBeanClass().equals(beanClass)) {
                return BceMetadata.beanInfo(beanClass);
            }
        }
        throw new IllegalArgumentException("No discovered bean for class: " + beanClass.getName());
    }

    public Collection<MethodInfo> methods(BeanInfo beanInfo) {
        Class<?> beanClass = BceMetadata.unwrapBeanClass(beanInfo);
        Method[] declared = beanClass.getDeclaredMethods();
        List<MethodInfo> result = new ArrayList<>(declared.length);
        for (Method method : declared) {
            result.add(BceMetadata.methodInfo(method));
        }
        return Collections.unmodifiableList(result);
    }

    public MethodInfo method(BeanInfo beanInfo, String name, Class<?>... parameterTypes) {
        Class<?> beanClass = BceMetadata.unwrapBeanClass(beanInfo);
        try {
            return BceMetadata.methodInfo(beanClass.getDeclaredMethod(name, parameterTypes));
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("No method " + name + " on bean class " + beanClass.getName(), e);
        }
    }
}
