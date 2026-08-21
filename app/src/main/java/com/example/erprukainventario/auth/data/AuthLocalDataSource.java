package com.example.erprukainventario.auth.data;

import com.example.erprukainventario.auth.domain.AuthUser;
import com.example.erprukainventario.auth.domain.UserRole;
import com.example.erprukainventario.auth.domain.Warehouse;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Todos los métodos son SÍNCRONOS (bloqueantes) a propósito: Room en Java
 * no usa suspend/Flow, así que quien llame a esta clase debe hacerlo desde
 * un hilo de background (ver AuthRepositoryImpl, que usa un ExecutorService).
 */
@Singleton
public class AuthLocalDataSource {

    private final AuthDao authDao;

    @Inject
    public AuthLocalDataSource(AuthDao authDao) {
        this.authDao = authDao;
    }

    public void saveWarehouses(List<Warehouse> warehouses) {
        List<CachedWarehouseEntity> entities = new ArrayList<>();
        for (Warehouse w : warehouses) {
            CachedWarehouseEntity entity = new CachedWarehouseEntity();
            entity.id = w.getId();
            entity.name = w.getName();
            entities.add(entity);
        }
        authDao.upsertWarehouses(entities);
    }

    public List<Warehouse> getCachedWarehouses() {
        List<Warehouse> result = new ArrayList<>();
        for (CachedWarehouseEntity entity : authDao.getWarehouses()) {
            result.add(new Warehouse(entity.id, entity.name));
        }
        return result;
    }

    public void cacheCredentials(String username, String password, AuthUser user) {
        CachedCredentialEntity entity = new CachedCredentialEntity();
        entity.username = username;
        entity.passwordHash = hash(password); // TODO: reemplazar por BCrypt/Argon2 real
        entity.userId = user.getId();
        entity.fullName = user.getFullName();
        entity.role = user.getRole().name();
        entity.warehouseId = user.getWarehouseId();
        entity.warehouseName = user.getWarehouseName();
        authDao.upsertCredential(entity);
    }

    public AuthUser validateOffline(String username, String password, String warehouseId) {
        CachedCredentialEntity cached = authDao.findCredential(username, warehouseId);
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