package com.example.erprukainventario.core.data;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.google.firebase.firestore.Blob;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Fotos de evidencia (guía §3: discrepancias con evidencia fotográfica).
 *
 * Modelo: EVIDENCE/{id} (companyId, image: Blob JPEG, createdByName, createdAt)
 *
 * Se guardan en Firestore (JPEG ya comprimido por {@link com.example.erprukainventario.core.ui.PhotoCapture},
 * bajo el límite de 1 MB por documento) en vez de Cloud Storage: así la foto queda
 * en la misma cola offline que el resto de la operación y no requiere un bucket
 * aparte ni el plan de pago de Firebase.
 */
@Singleton
public class EvidenceRepository {

    private final FirebaseFirestore firestore;
    private final WorkContext context;

    @Inject
    public EvidenceRepository(FirebaseFirestore firestore, WorkContext context) {
        this.firestore = firestore;
        this.context = context;
    }

    /** Agrega la foto al batch y entrega el id para referenciarla; null si no hay foto. */
    public String addPhoto(WriteBatch batch, byte[] jpeg) {
        if (jpeg == null || jpeg.length == 0) return null;
        DocumentReference ref = firestore.collection(FirestoreCollections.EVIDENCE).document();
        Map<String, Object> data = new HashMap<>();
        data.put("companyId", context.companyId());
        data.put("image", Blob.fromBytes(jpeg));
        data.put("createdByName", context.userName());
        data.put("createdAt", FieldValue.serverTimestamp());
        batch.set(ref, data);
        return ref.getId();
    }

    /** Entrega la foto decodificada, o null si no existe o no se pudo leer. */
    public void getPhoto(String evidenceId, RepositoryCallback<Bitmap> callback) {
        if (evidenceId == null || evidenceId.isEmpty()) {
            callback.onResult(null);
            return;
        }
        firestore.collection(FirestoreCollections.EVIDENCE).document(evidenceId).get()
                .addOnSuccessListener(doc -> {
                    Object v = doc.get("image");
                    if (v instanceof Blob) {
                        byte[] bytes = ((Blob) v).toBytes();
                        callback.onResult(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
                    } else {
                        callback.onResult(null);
                    }
                })
                .addOnFailureListener(e -> callback.onResult(null));
    }
}
