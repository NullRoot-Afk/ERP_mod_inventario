package com.example.erprukainventario.core.ui;

/**
 * Envoltorio para mensajes de una sola vez (Toast, diálogo de resultado) en LiveData:
 * evita que se repitan al rotar la pantalla o al volver a observar.
 */
public class Event<T> {

    private final T content;
    private boolean handled = false;

    public Event(T content) {
        this.content = content;
    }

    /** Entrega el contenido solo la primera vez; después, null. */
    public T consume() {
        if (handled) return null;
        handled = true;
        return content;
    }
}
