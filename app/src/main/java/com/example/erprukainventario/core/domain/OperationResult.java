package com.example.erprukainventario.core.domain;

/** Resultado de una operación de escritura: éxito/fallo y un mensaje para mostrar al usuario. */
public class OperationResult {

    private final boolean success;
    private final String message;

    private OperationResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static OperationResult ok(String message) {
        return new OperationResult(true, message);
    }

    public static OperationResult error(String message) {
        return new OperationResult(false, message);
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
}
