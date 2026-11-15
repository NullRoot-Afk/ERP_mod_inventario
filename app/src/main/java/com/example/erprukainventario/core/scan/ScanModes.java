package com.example.erprukainventario.core.scan;

/** Claves con que cada módulo registra su ScanTaskSource en Hilt. */
public final class ScanModes {

    public static final String PICKING = "PICKING";
    public static final String DISPATCH = "DISPATCH";
    public static final String COUNT = "COUNT";
    public static final String TRANSFER_SEND = "TRANSFER_SEND";
    public static final String TRANSFER_RECEIVE = "TRANSFER_RECEIVE";

    private ScanModes() {
    }
}
