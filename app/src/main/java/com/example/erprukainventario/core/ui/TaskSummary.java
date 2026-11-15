package com.example.erprukainventario.core.ui;

/** Fila genérica de las listas de módulos (código, subtítulo, meta y badge de estado). */
public class TaskSummary {

    public enum Badge { NEUTRAL, WARNING, SUCCESS, DANGER }

    public final String id;
    public final String code;
    public final String subtitle;
    public final String meta;
    public final String badgeLabel;
    public final Badge badge;

    public TaskSummary(String id, String code, String subtitle, String meta, String badgeLabel, Badge badge) {
        this.id = id;
        this.code = code;
        this.subtitle = subtitle;
        this.meta = meta;
        this.badgeLabel = badgeLabel;
        this.badge = badge;
    }

    public boolean matches(String query) {
        if (query == null || query.isEmpty()) return true;
        return contains(code, query) || contains(subtitle, query) || contains(meta, query);
    }

    private static boolean contains(String field, String query) {
        return field != null && field.toLowerCase(java.util.Locale.ROOT).contains(query);
    }
}
