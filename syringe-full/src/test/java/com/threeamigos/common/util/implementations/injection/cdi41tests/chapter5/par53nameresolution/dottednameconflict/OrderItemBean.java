package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par53nameresolution.dottednameconflict;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Named;

@Dependent
@Named("order.item")
public class OrderItemBean {
}
