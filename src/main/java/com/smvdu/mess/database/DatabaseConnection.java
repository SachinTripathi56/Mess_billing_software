package com.smvdu.mess.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConnection {
    
    private static final String DB_URL = "jdbc:sqlite:mess_billing.db";
    private static Connection connection;
    
    public static void initialize() {
        try {
            connection = DriverManager.getConnection(DB_URL);
            createTables();
            insertDefaultData();
            System.out.println("Database initialized successfully!");
        } catch (SQLException e) {
        }
    }
    
    public static Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(DB_URL);
            }
        } catch (SQLException e) {
        }
        return connection;
    }
    
    private static void createTables() throws SQLException {
        Statement stmt = connection.createStatement();
        
        // Users table (Caretakers)
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                email TEXT UNIQUE NOT NULL,
                password TEXT NOT NULL,
                name TEXT NOT NULL,
                hostel_id INTEGER NOT NULL,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);
        
        // Hostels table
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS hostels (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                code TEXT UNIQUE NOT NULL
            )
        """);
        
        // Students table
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS students (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                entry_number TEXT UNIQUE NOT NULL,
                name TEXT NOT NULL,
                hostel_id INTEGER NOT NULL,
                room_number TEXT,
                phone TEXT,
                email TEXT,
                is_active INTEGER DEFAULT 1,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (hostel_id) REFERENCES hostels(id)
            )
        """);
        
        // Monthly attendance/mess days table
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS student_attendance (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                student_id INTEGER NOT NULL,
                month INTEGER NOT NULL,
                year INTEGER NOT NULL,
                total_days INTEGER NOT NULL,
                mess_days INTEGER NOT NULL,
                absent_days INTEGER DEFAULT 0,
                remarks TEXT,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (student_id) REFERENCES students(id),
                UNIQUE(student_id, month, year)
            )
        """);
        
        // Bills table
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS bills (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                hostel_id INTEGER NOT NULL,
                month INTEGER NOT NULL,
                year INTEGER NOT NULL,
                total_students INTEGER NOT NULL,
                total_mess_days INTEGER NOT NULL,
                per_day_rate REAL NOT NULL,
                subtotal REAL NOT NULL,
                gst_percent REAL DEFAULT 5.0,
                gst_amount REAL NOT NULL,
                total_amount REAL NOT NULL,
                generated_by INTEGER,
                generated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (hostel_id) REFERENCES hostels(id),
                FOREIGN KEY (generated_by) REFERENCES users(id)
            )
        """);
        
        // Settings table
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS settings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                key TEXT UNIQUE NOT NULL,
                value TEXT NOT NULL
            )
        """);
    }
    
    private static void insertDefaultData() throws SQLException {
        // Check if hostels already exist
        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM hostels");
        if (rs.next() && rs.getInt(1) > 0) {
            return; // Data already exists
        }
        
        // Insert 5 hostels
        String[] hostels = {
            "('Vyas Bhawan', 'VB')",
            "('Trikuta Bhawan', 'TB')",
            "('Nilgiri Bhawan', 'NB')",
            "('Kailash Bhawan', 'KB')",
            "('Vindhyachal Bhawan', 'VNB')"
        };
        
        for (String hostel : hostels) {
            stmt.execute("INSERT INTO hostels (name, code) VALUES " + hostel);
        }
        
        // Insert default caretakers (password: admin123)
        String[][] users = {
            {"vyas@smvdu.ac.in", "admin123", "Vyas Caretaker", "1"},
            {"trikuta@smvdu.ac.in", "admin123", "Trikuta Caretaker", "2"},
            {"nilgiri@smvdu.ac.in", "admin123", "Nilgiri Caretaker", "3"},
            {"kailash@smvdu.ac.in", "admin123", "Kailash Caretaker", "4"},
            {"vindhyachal@smvdu.ac.in", "admin123", "Vindhyachal Caretaker", "5"}
        };
        
        PreparedStatement pstmt = connection.prepareStatement(
            "INSERT INTO users (email, password, name, hostel_id) VALUES (?, ?, ?, ?)"
        );
        
        for (String[] user : users) {
            pstmt.setString(1, user[0]);
            pstmt.setString(2, user[1]); // In production, hash this!
            pstmt.setString(3, user[2]);
            pstmt.setInt(4, Integer.parseInt(user[3]));
            pstmt.executeUpdate();
        }
        
        // Insert default settings
        stmt.execute("INSERT OR IGNORE INTO settings (key, value) VALUES ('per_day_rate', '120')");
        stmt.execute("INSERT OR IGNORE INTO settings (key, value) VALUES ('gst_percent', '5')");
        
        System.out.println("Default data inserted successfully!");
    }
}

