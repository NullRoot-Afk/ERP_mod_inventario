package com.example.erprukainventario.reception.data;

import com.example.erprukainventario.reception.domain.PurchaseOrder;
import com.example.erprukainventario.reception.domain.PurchaseOrderStatus;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Igual que AuthLocalDataSource: métodos síncronos, se llaman siempre
 * desde un hilo de background (ver ReceptionRepositoryImpl).
 */
@Singleton
public class ReceptionLocalDataSource {

    private final ReceptionDao dao;

    @Inject
    public ReceptionLocalDataSource(ReceptionDao dao) {
        this.dao = dao;
    }

    public void replaceOrdersForWarehouse(String warehouseId, List<PurchaseOrder> orders) {
        dao.clearForWarehouse(warehouseId);
        List<PurchaseOrderEntity> entities = new ArrayList<>();
        for (PurchaseOrder o : orders) {
            PurchaseOrderEntity e = new PurchaseOrderEntity();
            e.id = o.getId();
            e.code = o.getCode();
            e.supplierName = o.getSupplierName();
            e.lineCount = o.getLineCount();
            e.status = o.getStatus().name();
            e.etaLabel = o.getEtaLabel();
            e.warehouseId = warehouseId;
            entities.add(e);
        }
        dao.upsertOrders(entities);
    }

    public List<PurchaseOrder> getCachedOrders(String warehouseId) {
        List<PurchaseOrder> result = new ArrayList<>();
        for (PurchaseOrderEntity e : dao.getOrders(warehouseId)) {
            result.add(new PurchaseOrder(
                    e.id, e.code, e.supplierName, e.lineCount,
                    PurchaseOrderStatus.valueOf(e.status), e.etaLabel
            ));
        }
        return result;
    }
}