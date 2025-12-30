package com.smvdu.mess.models;

public class User {
    private int id;
    private String email;
    private String name;
    private int hostelId;
    private String hostelName;
    private String messName;  // NEW
    
    public User(int id, String email, String name, int hostelId, String hostelName) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.hostelId = hostelId;
        this.hostelName = hostelName;
    }
    
    // NEW Constructor with mess name
    public User(int id, String email, String name, int hostelId, String hostelName, String messName) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.hostelId = hostelId;
        this.hostelName = hostelName;
        this.messName = messName;
    }
    
    // Getters
    public int getId() { return id; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public int getHostelId() { return hostelId; }
    public String getHostelName() { return hostelName; }
    public String getMessName() { return messName; }  // NEW
}
