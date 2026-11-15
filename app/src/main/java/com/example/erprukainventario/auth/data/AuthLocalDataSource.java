package com.example.erprukainventario.auth.data;

import com.example.erprukainventario.auth.domain.AuthUser;
import com.example.erprukainventario.auth.domain.UserRole;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton //Indicacion de un contenedor de inyeccion de dependencias
public class AuthLocalDataSource {

    private final AuthDao authDao; //Referencia al objeto que permite acceder y modificar la informacion de una tabla

    @Inject//Permite la inyeccion de authDao automaticamente
    public AuthLocalDataSource(AuthDao authDao) {
        this.authDao = authDao;
    }

    public void cacheCredentials(String username, String password, AuthUser user) {
        CachedCredentialEntity entity = new CachedCredentialEntity();
        entity.userId = user.getId();          // clave primaria: ver nota en CachedCredentialEntity
        entity.username = username;
        entity.passwordHash = hash(password);
        entity.fullName = user.getFullName();
        entity.role = user.getRole().name();
        entity.warehouseId = user.getWarehouseId();
        entity.warehouseName = user.getWarehouseName();
        //Actualizacion o insercion de un nuevo registro(Upsert)
        authDao.upsertCredential(entity); //Se le delega la responsabilidad de guardar la entidad a authDao
    }

    public AuthUser validateOffline(String username, String password) {
        CachedCredentialEntity cached = authDao.findCredential(username);//Se busca al usuario en cache
        if (cached == null) return null;//Si no se encuentra retorna nulo
        if (!cached.passwordHash.equals(hash(password))) return null;//Si se encuentra pero los hashes son diferentes retorna nulo
        //Si pasa los filtros se le crea y retorna una instancia del tipo AuthUser

        /* Se retorna un AuthUser en vez de un CachedCredentialEntity para separar los datos persistentes
         * En la base de datos local, del usuario dentro de la logica de la aplicacion */
        return new AuthUser(
                cached.userId,
                cached.fullName,
                UserRole.valueOf(cached.role),
                cached.warehouseId,
                cached.warehouseName
        );
    }
    //Metodo de hash de contraseñas
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