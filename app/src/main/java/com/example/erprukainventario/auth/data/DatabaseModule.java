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
                // TODO: antes de producción reemplazar por Migrations reales:
                // fallbackToDestructiveMigration() borra los datos locales
                // (credenciales cacheadas) en cada cambio de versión.
                .fallbackToDestructiveMigration()
                .build();
    }

    // Faltaba este binding: era la causa del error [Dagger/MissingBinding] AuthDao.
    @Provides
    public AuthDao provideAuthDao(AppDatabase db) {
        return db.authDao();
    }
}
