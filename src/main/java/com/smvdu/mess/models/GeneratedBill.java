package com.smvdu.mess.models;

import java.time.LocalDateTime;

public class GeneratedBill {
    private int id;
    private int messId;
    private String messName;
    private int month;
    private int year;
    private String billPeriod;
    private int operatingDays;
    private int totalStudents;
    private int totalStudentDays;
    private int totalAbsentDays;
    private int totalMessDays;
    private double perDayRate;
    private double subtotal;
    private double gstPercent;
    private double gstAmount;
    private double fineAmount;
    private double totalAmount;
    private String generatedBy;
    private LocalDateTime generatedAt;
    
    // Constructor
    public GeneratedBill(int id, int messId, String messName, int month, int year,
                        String billPeriod, int operatingDays, int totalStudents,
                        int totalStudentDays, int totalAbsentDays, int totalMessDays,
                        double perDayRate, double subtotal, double gstPercent,
                        double gstAmount, double fineAmount, double totalAmount,
                        String generatedBy, LocalDateTime generatedAt) {
        this.id = id;
        this.messId = messId;
        this.messName = messName;
        this.month = month;
        this.year = year;
        this.billPeriod = billPeriod;
        this.operatingDays = operatingDays;
        this.totalStudents = totalStudents;
        this.totalStudentDays = totalStudentDays;
        this.totalAbsentDays = totalAbsentDays;
        this.totalMessDays = totalMessDays;
        this.perDayRate = perDayRate;
        this.subtotal = subtotal;
        this.gstPercent = gstPercent;
        this.gstAmount = gstAmount;
        this.fineAmount = fineAmount;
        this.totalAmount = totalAmount;
        this.generatedBy = generatedBy;
        this.generatedAt = generatedAt;
    }
    
    // Getters
    public int getId() { return id; }
    public int getMessId() { return messId; }
    public String getMessName() { return messName; }
    public int getMonth() { return month; }
    public int getYear() { return year; }
    public String getBillPeriod() { return billPeriod; }
    public int getOperatingDays() { return operatingDays; }
    public int getTotalStudents() { return totalStudents; }
    public int getTotalStudentDays() { return totalStudentDays; }
    public int getTotalAbsentDays() { return totalAbsentDays; }
    public int getTotalMessDays() { return totalMessDays; }
    public double getPerDayRate() { return perDayRate; }
    public double getSubtotal() { return subtotal; }
    public double getGstPercent() { return gstPercent; }
    public double getGstAmount() { return gstAmount; }
    public double getFineAmount() { return fineAmount; }
    public double getTotalAmount() { return totalAmount; }
    public String getGeneratedBy() { return generatedBy; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    
    public String getMonthYearString() {
        String[] months = {"", "January", "February", "March", "April", "May", "June",
                          "July", "August", "September", "October", "November", "December"};
        return months[month] + " " + year;
    }
}