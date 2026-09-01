package com.example.erprukainventario.auth.data;

import com.example.erprukainventario.auth.domain.AuthUser;
import com.example.erprukainventario.auth.domain.UserRole;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AuthLocalDataSource {

    private final AuthDao authDao;

    @Inject//Constructor
    public AuthLocalDataSource(AuthDao authDao) {
        this.authDao = authDao;
    }

    public void cacheCredentials(String username, String password, AuthUser user) {
        CachedCredentialEntity entity = new CachedCredentialEntity();
        entity.userId = user.getId();          // clave primaria: ver nota en CachedCredentialEntity
        entity.username = username;
        entity.passwordHash = hash(password);  // TODO: reemplazar por BCrypt/Argon2 real
        entity.fullName = user.getFullName();
        entity.role = user.getRole().name();
        entity.warehouseId = user.getWarehouseId();
        entity.warehouseName = user.getWarehouseName();
        authDao.upsertCredential(entity);
    }

    public AuthUser validateOffline(String username, String password) {
        CachedCredentialEntity cached = authDao.findCredential(username);
        if (cached == null) return null;
        if (!cached.passwordHash.equals(hash(password))) return null;

        return new AuthUser(
                cached.userId,
                cached.fullName,
                UserRole.valueOf(cached.role),
                cached.warehouseId,
                cached.warehouseName
        );
    }

    // Placeholder de hashing. NO usar tal cual en producción (usar BCrypt/Argon2).
    private String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}