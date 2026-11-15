package com.example.erprukainventario.core.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Reglas de los números de serie (guía §10), sin dependencias de Android ni Firestore. */
public final class Serials {

    public static final int MAX_LENGTH = 40;

    private Serials() {
    }

    /**
     * Forma canónica: sin espacios y en mayúsculas ("sn 12-ab" -> "SN12-AB").
     * Devuelve null si queda vacía, es demasiado larga o trae caracteres no válidos.
     */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String s = raw.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (s.isEmpty() || s.length() > MAX_LENGTH) return null;
        return s.matches("[A-Z0-9._\\-/]+") ? s : null;
    }

    /**
     * Id de documento: una misma serie de un mismo producto existe una sola vez en
     * la empresa (no se puede recibir dos veces). "/" no es válido en un id de Firestore.
     */
    public static String docId(String companyId, String productId, String serial) {
        return (companyId + "_" + productId + "_" + serial).replace('/', '-');
    }

    /** "SN1, SN2, SN3" para la bitácora y los mensajes. */
    public static String join(List<String> serials) {
        if (serials == null || serials.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String s : serials) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s);
        }
        return sb.toString();
    }

    /** Separa texto libre (comas, espacios o saltos de línea) en series canónicas sin repetir. */
    public static List<String> parse(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text != null) {
            for (String part : text.split("[,;\\s]+")) {
                String s = normalize(part);
                if (s != null) out.add(s);
            }
        }
        return new ArrayList<>(out);
    }

    /**
     * Valida una serie antes de agregarla a una lista en captura.
     *
     * @param allowed  si no está vacía, la serie debe estar en ella (ej. lo enviado en una transferencia).
     * @param notEqual códigos del producto (EAN, SKU): leer la etiqueta equivocada no cuenta como serie.
     * @return mensaje de error, o null si se puede agregar.
     */
    public static String validateNew(String serial, List<String> current, List<String> allowed,
                                     String... notEqual) {
        if (serial == null) return "Número de serie inválido (solo letras, números y - . _ /).";
        for (String code : notEqual) {
            if (code != null && serial.equalsIgnoreCase(code.trim())) {
                return "Ese es el código del producto, no su número de serie.";
            }
        }
        if (current != null && current.contains(serial)) return "La serie " + serial + " ya está en la lista.";
        if (allowed != null && !allowed.isEmpty() && !allowed.contains(serial)) {
            return "La serie " + serial + " no viene en este envío.";
        }
        return null;
    }
}
