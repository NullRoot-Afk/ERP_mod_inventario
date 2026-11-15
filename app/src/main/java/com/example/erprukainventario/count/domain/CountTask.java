package com.example.erprukainventario.count.domain;

import java.util.Date;

public class CountTask {

    private final String id;
    private final String name;
    private final CountType type;
    private final CountStatus status;
    private final int lineCount;
    private final String createdByName;
    private final int differencesCount;
    private final String summary;
    private final String closedByName;
    private final Date closedAt;

    public CountTask(String id, String name, CountType type, CountStatus status, int lineCount,
                     String createdByName, int differencesCount, String summary,
                     String closedByName, Date closedAt) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.status = status;
        this.lineCount = lineCount;
        this.createdByName = createdByName;
        this.differencesCount = differencesCount;
        this.summary = summary;
        this.closedByName = closedByName;
        this.closedAt = closedAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public CountType getType() { return type; }
    public CountStatus getStatus() { return status; }
    public int getLineCount() { return lineCount; }
    public String getCreatedByName() { return createdByName; }
    public int getDifferencesCount() { return differencesCount; }
    public String getSummary() { return summary; }
    public String getClosedByName() { return closedByName; }
    public Date getClosedAt() { return closedAt; }
}
