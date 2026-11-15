package com.example.erprukainventario.count.domain;

/** Tipos de conteo de la guía §7: cíclicos (zona, categoría, aleatorio) y general. */
public enum CountType {
    ZONA("Conteo cíclico · Zona"),
    CATEGORIA("Conteo cíclico · Categoría"),
    ALEATORIO("Conteo cíclico · Aleatorio"),
    GENERAL("Conteo general");

    private final String label;

    CountType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
