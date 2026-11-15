package com.example.erprukainventario.picking.data;

import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.core.domain.OperationResult;
import com.example.erprukainventario.picking.domain.PickingOrder;

import java.util.List;

public interface PickingRepository {

    /** Pedidos de la bodega del usuario (todos los estados). */
    void getOrders(RepositoryCallback<List<PickingOrder>> callback);

    /**
     * Toma el pedido: queda asignado al usuario. Con markInProgress pasa a EN_PROCESO
     * (no se usa para pedidos PARCIAL, que deben seguir visibles en Despacho).
     * expectedAssigneeId es el asignado que vio el usuario: si otro lo tomó entretanto,
     * se rechaza (conflicto) en vez de quitárselo sin aviso.
     */
    void assignToMe(String orderId, String expectedAssigneeId, boolean markInProgress,
                    RepositoryCallback<OperationResult> callback);

    void seedDemoData(RepositoryCallback<Boolean> callback);
}
