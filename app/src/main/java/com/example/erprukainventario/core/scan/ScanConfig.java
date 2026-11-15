package com.example.erprukainventario.core.scan;

/**
 * Reglas de una sesión de escaneo. Cada módulo arma la suya:
 * - picking: no se puede sacar más de lo pedido, se advierte si queda incompleto (backorder).
 * - despacho: todo lo preparado debe verificarse antes de salir.
 * - conteo: "a ciegas", no se muestra el stock del sistema para no sesgar.
 */
public class ScanConfig {

    public final String screenLabel;
    public final String quantityLabel;
    public final String finishLabel;
    /** false = conteo a ciegas. */
    public final boolean showExpected;
    public final boolean allowExceedExpected;
    /** true = no se permite finalizar con líneas incompletas. */
    public final boolean requireComplete;
    /** Si no es null y hay líneas incompletas, se pide confirmación con este texto. */
    public final String incompleteWarning;
    /** Campos de texto que se piden al finalizar (ej. transportista, patente). */
    public final String[] finishPrompts;

    private ScanConfig(Builder b) {
        this.screenLabel = b.screenLabel;
        this.quantityLabel = b.quantityLabel;
        this.finishLabel = b.finishLabel;
        this.showExpected = b.showExpected;
        this.allowExceedExpected = b.allowExceedExpected;
        this.requireComplete = b.requireComplete;
        this.incompleteWarning = b.incompleteWarning;
        this.finishPrompts = b.finishPrompts;
    }

    public static class Builder {
        private final String screenLabel;
        private String quantityLabel = "Cantidad";
        private String finishLabel = "Finalizar";
        private boolean showExpected = true;
        private boolean allowExceedExpected = false;
        private boolean requireComplete = false;
        private String incompleteWarning = null;
        private String[] finishPrompts = new String[0];

        public Builder(String screenLabel) {
            this.screenLabel = screenLabel;
        }

        public Builder quantityLabel(String v) { quantityLabel = v; return this; }
        public Builder finishLabel(String v) { finishLabel = v; return this; }
        public Builder blind() { showExpected = false; allowExceedExpected = true; return this; }
        public Builder allowExceed() { allowExceedExpected = true; return this; }
        public Builder requireComplete() { requireComplete = true; return this; }
        public Builder incompleteWarning(String v) { incompleteWarning = v; return this; }
        public Builder finishPrompts(String... v) { finishPrompts = v; return this; }

        public ScanConfig build() {
            return new ScanConfig(this);
        }
    }
}
