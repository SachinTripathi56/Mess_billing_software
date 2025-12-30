package com.smvdu.mess.utils;

public class AdminSessionManager {
    private static String adminName;
    private static String designation;
    
    public static void setAdminInfo(String name, String desig) {
        adminName = name;
        designation = desig;
    }
    
    public static String getAdminName() {
        return adminName;
    }
    
    public static String getDesignation() {
        return designation;
    }
    
    public static void clear() {
        adminName = null;
        designation = null;
    }
}
