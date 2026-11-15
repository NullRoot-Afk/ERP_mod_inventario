package com.example.erprukainventario.core.data;

import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public abstract class CoreModule {

    @Binds
    public abstract ProductRepository bindProductRepository(ProductRepositoryImpl impl);
}
