package com.example.erprukainventario.reception.data;

import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public abstract class ReceptionModule {

    @Binds
    public abstract ReceptionRepository bindReceptionRepository(ReceptionRepositoryImpl impl);
}
