package com.threeamigos.common.util.implementations.injection.testpackages.misc;

import jakarta.inject.Inject;
import java.util.List;

public class GenericService<T> {

    private final T[] genericArray;           // Becomes GenericArrayType
    private final Processor<T> processor;     // T is a TypeVariable
    private final List<? extends Number> numbers; // ? extends Number is a WildcardType

    @Inject
    public GenericService(
            T[] genericArray,
            Processor<T> processor,
            List<? extends Number> numbers
    ) {
        this.genericArray = genericArray;
        this.processor = processor;
        this.numbers = numbers;
    }

    public void run() {
        System.out.println("Array length: " + genericArray.length);
        if (genericArray.length > 0) {
            processor.process(genericArray[0]);
        }
        System.out.println("Numbers: " + numbers);
    }
}
