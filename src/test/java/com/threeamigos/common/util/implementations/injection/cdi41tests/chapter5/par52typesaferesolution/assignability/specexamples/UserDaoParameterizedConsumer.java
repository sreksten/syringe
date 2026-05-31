package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.assignability.specexamples;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class UserDaoParameterizedConsumer {

    @Inject
    private Dao<User> userDao;

    @Inject
    private Dao<?> anyDao;

    @Inject
    private Dao<? extends Persistent> persistentDao;

    @Inject
    private Dao<? extends User> extendsUserDao;

    public Dao<User> getUserDao() {
        return userDao;
    }

    public Dao<?> getAnyDao() {
        return anyDao;
    }

    public Dao<? extends Persistent> getPersistentDao() {
        return persistentDao;
    }

    public Dao<? extends User> getExtendsUserDao() {
        return extendsUserDao;
    }
}
