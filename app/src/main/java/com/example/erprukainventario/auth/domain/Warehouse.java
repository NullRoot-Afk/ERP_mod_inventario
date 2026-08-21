package com.example.erprukainventario.auth.domain;

/** Bodega disponible para seleccionar en el login. */
public class Warehouse {

    private final String id;
    private final String name;

    public Warehouse(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() { return id; }
    public String getName() { return name; }

    // equals/hashCode para poder comparar listas (usado en la Activity
    // para saber si hay que re-armar el adapter del selector).
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Warehouse)) return false;
        Warehouse w = (Warehouse) o;
        return id.equals(w.id) && name.equals(w.name);
    }

    @Override
    public int hashCode() {
        return id.hashCode() * 31 + name.hashCode();
    }

    @Override
    public String toString() {
        // Usado directamente por el ArrayAdapter del AutoCompleteTextView.
        return name;
    }
}
