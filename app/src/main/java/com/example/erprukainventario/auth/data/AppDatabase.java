package com.example.erprukainventario.auth.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;

/**
 * Base de datos Room de la app. Hoy solo guarda las credenciales
 * cacheadas para el login offline. Las órdenes de recepción ya no se
 * cachean aquí: Firestore mantiene su propio caché offline en disco.
 *
 * Si cambias entidades, sube 'version'. Mientras DatabaseModule use
 * fallbackToDestructiveMigration() no necesitas escribir Migrations,
 * pero se pierden los datos locales en cada cambio de versión.
 */
@Database(
        entities = {
                CachedCredentialEntity.class
        },
        version = 3,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    public abstract AuthDao authDao();
}
