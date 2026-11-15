package com.example.erprukainventario.auth.data;
//Room permite manejar la persistencia de datos de forma local para funcionalidades offline
//compara las credenciales ingresadas con las guardadas localmente
import androidx.room.Database;
import androidx.room.RoomDatabase;//Clase superior que hereda hacia AppDatabase

//Clase de la base de datos Room
//definicion del esquema SQLite
@Database(//Indica de la clase consiguiente es una base de datos Room
          //declaracion de tablas locales
        entities = {
                CachedCredentialEntity.class //tabla local para almacenar credenciales y permitir acceso offline
        },
        version = 3, //Version del esquema, cambio manual para cambios en la estructura de esta
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {//declaracion de una clase abstracta que sera implementada en tiempo de compilacion por Room
    public abstract AuthDao authDao(); //metodo abstacto para acceder a los Dao de la tabla
}
