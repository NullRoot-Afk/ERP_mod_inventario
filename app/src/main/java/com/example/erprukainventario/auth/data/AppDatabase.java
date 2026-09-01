package com.example.erprukainventario.auth.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;
//esta clase define una base de datos local con room
@Database(
            entities = {CachedCredentialEntity.class}, //definicion de la clase que sera una tabla en la bd
            version = 1,
            exportSchema = false
    )
    public abstract class AppDatabase extends RoomDatabase {
        public abstract AuthDao authDao();
    }
