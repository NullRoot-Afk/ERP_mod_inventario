package com.example.erprukainventario.adjustment.data;

import com.example.erprukainventario.adjustment.domain.Adjustment;
import com.example.erprukainventario.adjustment.domain.AdjustmentReason;
import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.core.domain.OperationResult;
import com.example.erprukainventario.core.domain.Product;

import java.util.List;

public interface AdjustmentRepository {

    /** Ajustes de la bodega del usuario, del más reciente al más antiguo. */
    void getAdjustments(RepositoryCallback<List<Adjustment>> callback);

    /** Crea una solicitud PENDIENTE (todavía no mueve stock). */
    void create(Product product, int signedQuantity, AdjustmentReason reason, String notes,
                RepositoryCallback<Boolean> callback);

    /** Solo supervisor/jefe: aplica el ajuste al stock y deja el movimiento firmado. */
    void approve(String adjustmentId, RepositoryCallback<OperationResult> callback);

    void reject(String adjustmentId, String note, RepositoryCallback<OperationResult> callback);

    void seedDemoData(RepositoryCallback<Boolean> callback);
}
