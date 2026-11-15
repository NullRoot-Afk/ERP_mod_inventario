package com.example.erprukainventario.picking.data;

import com.example.erprukainventario.core.data.Docs;
import com.example.erprukainventario.picking.domain.PickingOrder;
import com.example.erprukainventario.picking.domain.PickingStatus;
import com.google.firebase.firestore.DocumentSnapshot;

/** Único lugar que conoce los campos de PICKING_ORDERS (lo usan Picking y Despacho). */
public final class PickingMapper {

    private PickingMapper() {
    }

    public static PickingOrder toOrder(DocumentSnapshot doc) {
        return new PickingOrder(
                doc.getId(),
                Docs.str(doc, "PK-" + doc.getId(), "code"),
                Docs.str(doc, "Cliente N/A", "customerName", "customer"),
                Docs.status(doc, PickingStatus.class, PickingStatus.PENDIENTE),
                Docs.num(doc, "lineCount"),
                Docs.str(doc, "", "assignedToId"),
                Docs.str(doc, "", "assignedToName"),
                Docs.str(doc, "", "dueLabel"),
                Docs.str(doc, "", "guideNumber"),
                Docs.str(doc, "", "carrier"),
                Docs.str(doc, "", "plate"),
                Docs.date(doc, "dispatchedAt")
        );
    }
}
