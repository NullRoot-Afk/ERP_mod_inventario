package com.example.erprukainventario.adjustment.data;

import com.example.erprukainventario.adjustment.domain.Adjustment;
import com.example.erprukainventario.adjustment.domain.AdjustmentReason;
import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.core.domain.Lot;
import com.example.erprukainventario.core.domain.OperationResult;
import com.example.erprukainventario.core.domain.Product;

import java.util.List;

public interface AdjustmentRepository {

    /** Ajustes de la bodega del usuario, del más reciente al más antiguo. */
    void getAdjustments(RepositoryCallback<List<Adjustment>> callback);

    /**
     * Crea una solicitud PENDIENTE (todavía no mueve stock).
     *
     * @param lot     lote afectado, o null (al aprobar una rebaja se descuenta por FEFO).
     * @param serials una serie por unidad si el producto controla número de serie (guía §10).
     */
    void create(Product product, Lot lot, int signedQuantity, AdjustmentReason reason, String notes,
                List<String> serials, RepositoryCallback<OperationResult> callback);

    /** Solo supervisor/jefe: aplica el ajuste al stock y deja el movimiento firmado. */
    void approve(String adjustmentId, RepositoryCallback<OperationResult> callback);

    void reject(String adjustmentId, String note, RepositoryCallback<OperationResult> callback);

    void seedDemoData(RepositoryCallback<Boolean> callback);
}
