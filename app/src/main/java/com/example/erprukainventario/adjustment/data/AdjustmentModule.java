package com.example.erprukainventario.adjustment.data;

import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public abstract class AdjustmentModule {

    @Binds
    public abstract AdjustmentRepository bindAdjustmentRepository(AdjustmentRepositoryImpl impl);
}
