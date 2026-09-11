package com.example.erprukainventario.auth.data;

import android.content.Context;

import androidx.room.Room;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public class DatabaseModule {

    @Provides
    @Singleton
    public AppDatabase provideAppDatabase(@ApplicationContext Context context) {
        return Room.databaseBuilder(context, AppDatabase.class, "erp_bodega.db")
                // TODO: cuando agreguemos más entidades/versiones más adelante,
                // acá van las Migrations correspondientes. Por ahora, en
                // desarrollo, fallbackToDestructiveMigration() evita crashear
                // por cambios de esquema sin migración — NO usar en producción
                // sin revisar qué implica perder los datos locales del operario.
                .fallbackToDestructiveMigration()
                .build();
    }

    @Provides
    @Singleton
    public AuthDao provideAuthDao(AppDatabase database) {
        return database.authDao();
    }

    @Provides
    @Singleton
    public com.example.erprukainventario.reception.data.ReceptionDao provideReceptionDao(AppDatabase database) {
        return database.receptionDao();
    }
}