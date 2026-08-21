package com.example.erprukainventario.auth.data;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * IMPORTANTE: passwordHash debe ser un hash (BCrypt/Argon2), nunca la
 * contraseña en texto plano. El nombre del campo es explícito a propósito
 * para que no se preste a guardar algo distinto por error.
 */
@Entity(tableName = "cached_credentials")
public class CachedCredentialEntity {

    @PrimaryKey
    public String username;

    public String passwordHash;
    public String userId;
    public String fullName;
    public String role;
    public String warehouseId;
    public String warehouseName;
}
