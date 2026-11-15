package com.example.erprukainventario.core.data;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Lectura tolerante de documentos Firestore: alias de campo, campos ausentes
 * y números guardados como texto no rompen la app (mismo criterio que ReceptionMapper).
 */
public final class Docs {

    private Docs() {
    }

    /** Primer campo no vacío entre los alias; si ninguno, el valor por defecto. */
    public static String str(DocumentSnapshot d, String fallback, String... fields) {
        for (String f : fields) {
            Object v = d.get(f);
            if (v instanceof String && !((String) v).isEmpty()) return (String) v;
        }
        return fallback;
    }

    public static int num(DocumentSnapshot d, String... fields) {
        for (String f : fields) {
            Object v = d.get(f);
            if (v instanceof Number) return ((Number) v).intValue();
            if (v instanceof String) {
                try {
                    return (int) Double.parseDouble(((String) v).trim());
                } catch (NumberFormatException ignored) {
                    // texto no numérico: probar el siguiente alias
                }
            }
        }
        return 0;
    }

    public static Date date(DocumentSnapshot d, String field) {
        Object v = d.get(field);
        if (v instanceof Timestamp) return ((Timestamp) v).toDate();
        if (v instanceof Date) return (Date) v;
        return null;
    }

    /** Lee un mapa {clave: número}, ej. stock por bodega. */
    public static Map<String, Integer> intMap(DocumentSnapshot d, String field) {
        Map<String, Integer> result = new HashMap<>();
        Object v = d.get(field);
        if (v instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                if (e.getKey() != null && e.getValue() instanceof Number) {
                    result.put(e.getKey().toString(), ((Number) e.getValue()).intValue());
                }
            }
        }
        return result;
    }

    /** Convierte el campo "status" en un enum; si no existe o no calza, el valor por defecto. */
    public static <E extends Enum<E>> E status(DocumentSnapshot d, Class<E> type, E fallback) {
        // d.get + instanceof (no getString): getString lanza excepción si el campo no es texto.
        Object v = d.get("status");
        if (!(v instanceof String)) return fallback;
        try {
            return Enum.valueOf(type, ((String) v).trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
