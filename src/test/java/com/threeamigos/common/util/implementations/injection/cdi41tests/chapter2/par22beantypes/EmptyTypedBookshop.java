package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par22beantypes;

import jakarta.enterprise.inject.Typed;

@Typed()
public class EmptyTypedBookshop extends Business implements Shop<Book> {
}
