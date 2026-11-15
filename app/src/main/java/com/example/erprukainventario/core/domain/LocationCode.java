package com.example.erprukainventario.core.domain;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Código de ubicación de bodega (guía §4): Zona-Pasillo-Estante[-Posición],
 * ej. "A-03-02-B". Las etiquetas impresas pueden traer el prefijo "LOC:" para
 * distinguirlas de un código de producto al escanear.
 */
public final class LocationCode {

    private static final Pattern PATTERN =
            Pattern.compile("^([A-Z]{1,2})-([0-9]{1,2})-([0-9]{1,2})(?:-([A-Z0-9]{1,2}))?$");

    private final String zone;
    private final String aisle;
    private final String shelf;
    private final String position;

    private LocationCode(String zone, String aisle, String shelf, String position) {
        this.zone = zone;
        this.aisle = aisle;
        this.shelf = shelf;
        this.position = position;
    }

    /** null si el texto no es una ubicación válida. Acepta "loc:a-3-2-b", espacios, etc. */
    public static LocationCode parse(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '-').replace('_', '-');
        if (s.startsWith("LOC:")) s = s.substring(4).trim();
        Matcher m = PATTERN.matcher(s);
        if (!m.matches()) return null;
        return new LocationCode(m.group(1), pad(m.group(2)), pad(m.group(3)), m.group(4));
    }

    public static boolean isValid(String raw) {
        return parse(raw) != null;
    }

    /** Forma canónica (dos dígitos en pasillo y estante), ej. "A-03-02-B". */
    public static String normalize(String raw) {
        LocationCode code = parse(raw);
        return code != null ? code.toString() : null;
    }

    public String getZone() { return zone; }
    public String getAisle() { return aisle; }
    public String getShelf() { return shelf; }
    public String getPosition() { return position; }

    /** "Zona A · Pasillo 03" — agrupa la vista de stock por ubicación. */
    public String aisleLabel() {
        return "Zona " + zone + " · Pasillo " + aisle;
    }

    private static String pad(String n) {
        return n.length() == 1 ? "0" + n : n;
    }

    @Override
    public String toString() {
        return zone + "-" + aisle + "-" + shelf + (position != null ? "-" + position : "");
    }
}
