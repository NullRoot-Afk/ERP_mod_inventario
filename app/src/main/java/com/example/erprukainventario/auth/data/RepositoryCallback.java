package com.example.erprukainventario.auth.data;
/**
 * Reemplazo simple de las funciones suspend de Kotlin. Se entrega en el
 * hilo principal siempre, para que quien la use (el ViewModel) pueda
 * actualizar LiveData sin preocuparse de en qué hilo está.
 */
public interface RepositoryCallback<T> {
    void onResult(T result);
}
