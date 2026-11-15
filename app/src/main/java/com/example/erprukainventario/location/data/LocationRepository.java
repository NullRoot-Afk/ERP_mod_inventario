package com.example.erprukainventario.location.data;

import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.core.data.InventoryWriter;
import com.example.erprukainventario.core.data.WorkContext;
import com.example.erprukainventario.core.domain.LocationCode;
import com.example.erprukainventario.core.domain.OperationResult;
import com.example.erprukainventario.core.domain.Product;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Gestión de ubicaciones (slotting, guía §4). Cada producto ocupa una ubicación
 * por bodega, guardada en PRODUCTS/{id}.locations.{bodega}; el campo "location"
 * queda como ubicación por defecto para bodegas que aún no la definieron.
 */
@Singleton
public class LocationRepository {

    private final FirebaseFirestore firestore;
    private final InventoryWriter inventory;
    private final WorkContext context;

    @Inject
    public LocationRepository(FirebaseFirestore firestore, InventoryWriter inventory, WorkContext context) {
        this.firestore = firestore;
        this.inventory = inventory;
        this.context = context;
    }

    /** Reubica el producto en la bodega del usuario y deja el movimiento REUBICACION. */
    public void relocate(Product product, String rawLocation, RepositoryCallback<OperationResult> callback) {
        String target = LocationCode.normalize(rawLocation);
        if (product == null || target == null) {
            callback.onResult(OperationResult.error("Ubicación inválida. Formato: Zona-Pasillo-Estante[-Posición], ej. A-03-02-B."));
            return;
        }
        if (target.equalsIgnoreCase(product.getLocation())) {
            callback.onResult(OperationResult.error("El producto ya está en " + target + "."));
            return;
        }
        if (context.companyId() == null) {
            callback.onResult(OperationResult.error("El dispositivo no tiene empresa configurada."));
            return;
        }

        WriteBatch batch = firestore.batch();
        int units = product.stockIn(context.warehouseId());
        inventory.relocate(batch, product.getId(), product.getCode(), product.getName(),
                units, product.getLocation(), target);

        batch.commit()
                .addOnSuccessListener(v -> callback.onResult(OperationResult.ok(
                        product.getName() + " reubicado en " + target + " (" + units + " un.).")))
                .addOnFailureListener(e -> callback.onResult(OperationResult.error("No se pudo guardar la reubicación.")));
    }
}
