package com.example.erprukainventario.core.scan;

import java.util.List;

/** Cabecera + líneas de lo que se va a trabajar en una sesión de escaneo. */
public class ScanTask {

    private final String id;
    private final String code;
    private final String subtitle;
    private final List<ScanLine> lines;
    private final ScanConfig config;

    public ScanTask(String id, String code, String subtitle, List<ScanLine> lines, ScanConfig config) {
        this.id = id;
        this.code = code;
        this.subtitle = subtitle;
        this.lines = lines;
        this.config = config;
    }

    public ScanTask withLines(List<ScanLine> newLines) {
        return new ScanTask(id, code, subtitle, newLines, config);
    }

    public String getId() { return id; }
    public String getCode() { return code; }
    public String getSubtitle() { return subtitle; }
    public List<ScanLine> getLines() { return lines; }
    public ScanConfig getConfig() { return config; }
}
