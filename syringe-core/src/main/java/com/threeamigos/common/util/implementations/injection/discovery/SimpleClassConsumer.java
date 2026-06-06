package com.threeamigos.common.util.implementations.injection.discovery;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Used for tests. If we put this class in the test package, we have a problem with classloader isolation -
 * the classes loaded by the test URLClassLoader are not visible to the classes loaded by the application classloader.
 * This class should not be included in the final .jar file.
 */
public class SimpleClassConsumer implements ClassConsumer {

    private final Collection<Class<?>> classes = new ConcurrentLinkedQueue<>();

    @Override
    public void add(Class<?> clazz, BeanArchiveMode beanArchiveMode) {
        // For testing purposes, we just collect classes regardless of bean archive mode
        classes.add(clazz);
    }

    public Collection<Class<?>> getClasses() {
        return classes;
    }
}
