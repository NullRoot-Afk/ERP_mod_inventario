package com.example.erprukainventario.core.data;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;

import java.util.Collections;
import java.util.Map;

/**
 * Destino de escrituras común a WriteBatch y Transaction. Así los "writers"
 * (stock, movimientos, ajustes, lotes) sirven igual dentro de una transacción
 * (con conexión, guiada por {@link GuardedCommit}) o de un batch (sin conexión).
 */
public interface Writes {

    void set(DocumentReference ref, Map<String, Object> data);

    void merge(DocumentReference ref, Map<String, Object> data);

    void update(DocumentReference ref, Map<String, Object> data);

    default void update(DocumentReference ref, String field, Object value) {
        update(ref, Collections.singletonMap(field, value));
    }

    static Writes of(WriteBatch batch) {
        return new Writes() {
            @Override public void set(DocumentReference ref, Map<String, Object> data) { batch.set(ref, data); }
            @Override public void merge(DocumentReference ref, Map<String, Object> data) { batch.set(ref, data, SetOptions.merge()); }
            @Override public void update(DocumentReference ref, Map<String, Object> data) { batch.update(ref, data); }
        };
    }

    static Writes of(Transaction transaction) {
        return new Writes() {
            @Override public void set(DocumentReference ref, Map<String, Object> data) { transaction.set(ref, data); }
            @Override public void merge(DocumentReference ref, Map<String, Object> data) { transaction.set(ref, data, SetOptions.merge()); }
            @Override public void update(DocumentReference ref, Map<String, Object> data) { transaction.update(ref, data); }
        };
    }
}
