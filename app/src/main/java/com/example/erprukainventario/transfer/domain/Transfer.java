package com.example.erprukainventario.transfer.domain;

/** Traslado de stock entre bodegas de la misma empresa (guía §9). */
public class Transfer {

    private final String id;
    private final String code;
    private final String originId;
    private final String originName;
    private final String destinationId;
    private final String destinationName;
    private final TransferStatus status;
    private final int lineCount;
    private final String notes;
    private final String createdByName;
    private final String carrier;
    private final boolean hasDiscrepancy;

    public Transfer(String id, String code, String originId, String originName, String destinationId,
                    String destinationName, TransferStatus status, int lineCount, String notes,
                    String createdByName, String carrier, boolean hasDiscrepancy) {
        this.id = id;
        this.code = code;
        this.originId = originId;
        this.originName = originName;
        this.destinationId = destinationId;
        this.destinationName = destinationName;
        this.status = status;
        this.lineCount = lineCount;
        this.notes = notes;
        this.createdByName = createdByName;
        this.carrier = carrier;
        this.hasDiscrepancy = hasDiscrepancy;
    }

    public String getId() { return id; }
    public String getCode() { return code; }
    public String getOriginId() { return originId; }
    public String getOriginName() { return originName; }
    public String getDestinationId() { return destinationId; }
    public String getDestinationName() { return destinationName; }
    public TransferStatus getStatus() { return status; }
    public int getLineCount() { return lineCount; }
    public String getNotes() { return notes; }
    public String getCreatedByName() { return createdByName; }
    public String getCarrier() { return carrier; }
    public boolean hasDiscrepancy() { return hasDiscrepancy; }
}
