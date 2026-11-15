package com.example.erprukainventario.adjustment.domain;

/**
 * Motivos de ajuste (guía §8). sign: -1 siempre resta, +1 siempre suma,
 * 0 = el usuario elige si es entrada o salida.
 */
public enum AdjustmentReason {
    MERMA("Merma", -1),
    VENCIDO("Producto vencido", -1),
    DANADO("Producto dañado", -1),
    EXTRAVIADO("Extraviado", -1),
    INGRESO_NO_REGISTRADO("Ingreso no registrado", 1),
    DIFERENCIA_CONTEO("Diferencia de conteo", 0),
    OTRO("Otro", 0);

    private final String label;
    private final int sign;

    AdjustmentReason(String label, int sign) {
        this.label = label;
        this.sign = sign;
    }

    public String getLabel() { return label; }
    public int getSign() { return sign; }

    public static AdjustmentReason fromLabel(String label) {
        for (AdjustmentReason r : values()) {
            if (r.label.equals(label)) return r;
        }
        return OTRO;
    }

    public static AdjustmentReason fromName(String name) {
        try {
            return name != null ? valueOf(name) : OTRO;
        } catch (IllegalArgumentException e) {
            return OTRO;
        }
    }

    @Override
    public String toString() {
        return label; // usado por el ArrayAdapter del selector
    }
}
