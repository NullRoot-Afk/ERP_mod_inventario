package com.example.erprukainventario.auth.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * userId es la clave primaria (no username): así, si el username de una
 * persona cambia en el backend, el próximo login online actualiza esta
 * misma fila (REPLACE por userId) en vez de crear una fila nueva y dejar
 * la vieja como una credencial offline huérfana todavía válida.
 */
@Entity(tableName = "cached_credentials")
public class CachedCredentialEntity {

    @PrimaryKey
    @NonNull
    public String userId;

    public String username;
    public String passwordHash;
    public String fullName;
    public String role;
    public String warehouseId;
    public String warehouseName;
}
