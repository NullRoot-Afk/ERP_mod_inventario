package com.example.erprukainventario.picking.domain;

import java.util.Date;

/** Pedido de venta o transferencia a preparar y despachar. */
public class PickingOrder {

    private final String id;
    private final String code;
    private final String customerName;
    private final PickingStatus status;
    private final int lineCount;
    private final String assignedToId;
    private final String assignedToName;
    private final String dueLabel;
    // Datos de despacho (vacíos hasta despachar)
    private final String guideNumber;
    private final String carrier;
    private final String plate;
    private final Date dispatchedAt;

    public PickingOrder(String id, String code, String customerName, PickingStatus status, int lineCount,
                        String assignedToId, String assignedToName, String dueLabel,
                        String guideNumber, String carrier, String plate, Date dispatchedAt) {
        this.id = id;
        this.code = code;
        this.customerName = customerName;
        this.status = status;
        this.lineCount = lineCount;
        this.assignedToId = assignedToId;
        this.assignedToName = assignedToName;
        this.dueLabel = dueLabel;
        this.guideNumber = guideNumber;
        this.carrier = carrier;
        this.plate = plate;
        this.dispatchedAt = dispatchedAt;
    }

    public String getId() { return id; }
    public String getCode() { return code; }
    public String getCustomerName() { return customerName; }
    public PickingStatus getStatus() { return status; }
    public int getLineCount() { return lineCount; }
    public String getAssignedToId() { return assignedToId; }
    public String getAssignedToName() { return assignedToName; }
    public String getDueLabel() { return dueLabel; }
    public String getGuideNumber() { return guideNumber; }
    public String getCarrier() { return carrier; }
    public String getPlate() { return plate; }
    public Date getDispatchedAt() { return dispatchedAt; }

    public boolean isAssignedTo(String userId) {
        return userId != null && userId.equals(assignedToId);
    }
}
