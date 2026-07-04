package com.dailytask;

public class Task {
    private String id;
    private String title;
    private String member;
    private String date;
    private String time;
    private String notes;
    private String status;
    private String image;

    private String cloudDocId;
    private String createdBy;

    public Task(String id, String title, String member, String date, String time, String notes, String status, String image) {
        this.id = id;
        this.title = title;
        this.member = member;
        this.date = date;
        this.time = time;
        this.notes = notes;
        this.status = status;
        this.image = image;
    }

    public String getCloudDocId() { return cloudDocId; }
    public void setCloudDocId(String cloudDocId) { this.cloudDocId = cloudDocId; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getMember() { return member; }
    public String getDate() { return date; }
    public String getTime() { return time; }
    public String getNotes() { return notes; }
    public String getStatus() { return status; }
    public String getImage() { return image; }
}