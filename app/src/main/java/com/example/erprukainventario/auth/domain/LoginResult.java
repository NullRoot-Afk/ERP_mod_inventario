package com.example.erprukainventario.auth.domain;

/**
 * Java no tiene sealed classes previas a Java 17 (Android suele compilar
 * con Java 8/11), así que se modela como una clase con dos factory methods
 * y un flag `success`. La UI SIEMPRE debe chequear isSuccess() antes de
 * leer los getters correspondientes.
 */
public class LoginResult {

    private final boolean success;
    private final AuthUser user;       // no-null si success == true
    private final boolean fromCache;   // true si el login se validó offline
    private final String errorMessage; // no-null si success == false

    private LoginResult(boolean success, AuthUser user, boolean fromCache, String errorMessage) {
        this.success = success;
        this.user = user;
        this.fromCache = fromCache;
        this.errorMessage = errorMessage;
    }

    public static LoginResult success(AuthUser user, boolean fromCache) {
        return new LoginResult(true, user, fromCache, null);
    }

    public static LoginResult error(String message) {
        return new LoginResult(false, null, false, message);
    }

    public boolean isSuccess() { return success; }
    public AuthUser getUser() { return user; }
    public boolean isFromCache() { return fromCache; }
    public String getErrorMessage() { return errorMessage; }
}
