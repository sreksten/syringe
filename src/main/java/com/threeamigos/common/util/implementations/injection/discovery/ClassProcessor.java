package com.threeamigos.common.util.implementations.injection.discovery;

import com.threeamigos.common.util.implementations.concurrency.ParallelTaskExecutor;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ClassProcessor implements ClassConsumer {

    private final ParallelTaskExecutor taskExecutor;
    private final KnowledgeBase knowledgeBase;

    /**
     * Track classes that have been processed to prevent duplicate bean registration.
     * Although ParallelClasspathScanner deduplicates at scan time, this provides
     * an additional safety check to ensure each class is only processed once.
     */
    private final Set<Class<?>> processedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public ClassProcessor(ParallelTaskExecutor taskExecutor, KnowledgeBase knowledgeBase) {
        this.taskExecutor = Objects.requireNonNull(taskExecutor, "taskExecutor cannot be null");
        this.knowledgeBase = Objects.requireNonNull(knowledgeBase, "knowledgeBase cannot be null");
    }

    @Override
    public void add(Class<?> clazz, BeanArchiveMode beanArchiveMode) {
        Objects.requireNonNull(clazz, "Class cannot be null");
        Objects.requireNonNull(beanArchiveMode, "beanArchiveMode cannot be null");
        // Only schedule processing if we haven't seen this class before
        if (processedClasses.add(clazz)) {
            taskExecutor.schedulePlatformThread(() -> accept(clazz, beanArchiveMode));
        }
    }

    private void accept(Class<?> clazz, BeanArchiveMode beanArchiveMode) {
        // Only record discovery now; validation/registration happen later after ProcessAnnotatedType
        knowledgeBase.add(clazz, beanArchiveMode);
    }
}
