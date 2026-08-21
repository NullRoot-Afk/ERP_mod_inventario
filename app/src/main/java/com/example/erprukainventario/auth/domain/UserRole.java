package com.example.erprukainventario.auth.domain;

/**
 * Roles definidos para el módulo de bodega.
 * Enum (no un String suelto) para que cualquier switch sobre el rol
 * obligue a manejar todos los casos.
 */
public enum UserRole {
    OPERARIO,
    SUPERVISOR,
    JEFE_BODEGA
}
