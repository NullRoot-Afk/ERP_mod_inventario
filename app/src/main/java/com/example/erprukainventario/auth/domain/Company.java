package com.example.erprukainventario.auth.domain;

/** Empresa (tenant) a la que queda vinculado este dispositivo tras la configuración inicial. */
public class Company {

    private final String id;
    private final String name;

    public Company(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() { return id; }
    public String getName() { return name; }
}
