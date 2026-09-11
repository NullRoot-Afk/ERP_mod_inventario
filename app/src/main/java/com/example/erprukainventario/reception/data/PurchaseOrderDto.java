package com.example.erprukainventario.reception.data;

/**
 * DTO plano para deserializar la respuesta JSON del backend. Se mapea a
 * PurchaseOrder (domain) en el repositorio — nunca se expone este DTO
 * fuera de la capa data, así la UI no depende del formato exacto de la API.
 */
public class PurchaseOrderDto {
    public String id;
    public String code;
    public String supplierName;
    public int lineCount;
    public String status;   // "SIN_INICIAR" | "PARCIAL" | "COMPLETA"
    public String etaLabel;
}
