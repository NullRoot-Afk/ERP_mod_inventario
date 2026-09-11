package com.example.erprukainventario.auth.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.example.erprukainventario.reception.data.PurchaseOrderEntity;
import com.example.erprukainventario.reception.data.ReceptionDao;

/**
 * Base de datos Room de toda la app. Cada feature (auth, reception,
 * picking, etc.) agrega sus entidades y expone su propio DAO aquí —
 * Room recomienda UNA sola base de datos por app, no una por feature.
 *
 * version subió de 1 a 2 al agregar PurchaseOrderEntity. Como el proyecto
 * usa fallbackToDestructiveMigration() en DatabaseModule (aceptable en
 * desarrollo), no hace falta escribir una Migration todavía — pero eso
 * borra los datos locales existentes en cada cambio de versión, así que
 * antes de producción esto debe reemplazarse por Migrations reales.
 */
@Database(
        entities = {
                CachedCredentialEntity.class,
                PurchaseOrderEntity.class
        },
        version = 2,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    public abstract AuthDao authDao();

    public abstract ReceptionDao receptionDao();
}