package com.smvdu.mess.utils;

import com.smvdu.mess.models.User;

public class SessionManager {
    private static User currentUser;
    
    public static void setCurrentUser(User user) {
        currentUser = user;
    }
    
    public static User getCurrentUser() {
        return currentUser;
    }
    
    public static void logout() {
        currentUser = null;
    }
    
    public static boolean isLoggedIn() {
        return currentUser != null;
    }
    
    public static int getCurrentHostelId() {
        return currentUser != null ? currentUser.getHostelId() : -1;
    }
}
