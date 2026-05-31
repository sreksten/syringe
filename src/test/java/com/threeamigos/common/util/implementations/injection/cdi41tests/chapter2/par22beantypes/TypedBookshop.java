package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par22beantypes;

import jakarta.enterprise.inject.Typed;

@Typed(Business.class)
public class TypedBookshop extends Business implements Shop<Book> {
}
