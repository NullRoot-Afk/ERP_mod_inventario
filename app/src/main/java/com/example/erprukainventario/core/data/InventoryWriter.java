package com.example.erprukainventario.core.data;

import com.example.erprukainventario.core.domain.MovementType;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Agrega a un WriteBatch los dos efectos de cualquier operación de bodega:
 * el cambio de stock del producto y el registro en la bitácora MOVEMENTS.
 * Al ir en el mismo batch que el cambio de estado del documento, o se aplica
 * todo o nada (no queda stock movido sin su movimiento, ni al revés).
 */
@Singleton
public class InventoryWriter {

    private final FirebaseFirestore firestore;
    private final WorkContext context;

    @Inject
    public InventoryWriter(FirebaseFirestore firestore, WorkContext context) {
        this.firestore = firestore;
        this.context = context;
    }

    /**
     * Suma (o resta, si delta < 0) stock en una bodega. Se usa set+merge con
     * FieldValue.increment: es atómico en el servidor y no falla si el producto
     * aún no tiene ese campo de bodega.
     */
    public void addStockChange(WriteBatch batch, String productId, String warehouseId, int delta) {
        if (productId == null || productId.isEmpty() || delta == 0) return;

        Map<String, Object> stock = new HashMap<>();
        stock.put(warehouseId, FieldValue.increment(delta));

        Map<String, Object> data = new HashMap<>();
        data.put("companyId", context.companyId());
        data.put("stock", stock);

        batch.set(firestore.collection(FirestoreCollections.PRODUCTS).document(productId), data, SetOptions.merge());
    }

    public void addMovement(WriteBatch batch, MovementType type, String productId, String productCode,
                            String productName, int quantity, String reference, String warehouseId) {
        if (quantity == 0) return;

        Map<String, Object> data = new HashMap<>();
        data.put("companyId", context.companyId());
        data.put("warehouseId", warehouseId);
        data.put("productId", productId != null ? productId : "");
        data.put("productCode", productCode);
        data.put("productName", productName);
        data.put("type", type.name());
        data.put("quantity", quantity);
        data.put("reference", reference);
        data.put("userId", context.userId());
        data.put("userName", context.userName());
        data.put("createdAt", FieldValue.serverTimestamp());

        batch.set(firestore.collection(FirestoreCollections.MOVEMENTS).document(), data);
    }

    /** Atajo: mueve stock y deja el movimiento firmado, en la bodega del usuario. */
    public void apply(WriteBatch batch, MovementType type, String productId, String productCode,
                      String productName, int delta, String reference) {
        apply(batch, type, productId, productCode, productName, delta, reference, context.warehouseId());
    }

    public void apply(WriteBatch batch, MovementType type, String productId, String productCode,
                      String productName, int delta, String reference, String warehouseId) {
        addStockChange(batch, productId, warehouseId, delta);
        addMovement(batch, type, productId, productCode, productName, delta, reference, warehouseId);
    }
}
