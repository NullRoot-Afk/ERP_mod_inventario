package com.example.erprukainventario.auth.data;
//Room permite manejar la persistencia de datos de forma local para funcionalidades offline
//compara las credenciales ingresadas con las guardadas localmente
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao//Marca eL inicio de la implementacion de la interfaz que se implementara automaticamente en tiempo de compilacion
public interface AuthDao {
    //Inserta las credenciales a la tabla local, si las credenciales ya esta cacheadas se reemplazara la antigua por la recien
    //ingresada, esto es para cuando el usuario logre logearse con una contraseña distinta a la que estaba cacheada
    //esto sucede con los cambios de contraseña ya que generan un hash distinto y necesitan ser actualizados.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertCredential(CachedCredentialEntity entity);
    //Room valida el query en tiempo de compilacion
    //las sintaxis ":parametro" se utiliza para pasar parametros java a una sentencia SQL sin tener que concatenarlos manualmente(+)
    //esto ayuda a evitar vulnerabilidades a ataques tipo SQLInyection
    @Query("SELECT * FROM cached_credentials WHERE username = :username LIMIT 1")
    CachedCredentialEntity findCredential(String username);
}


