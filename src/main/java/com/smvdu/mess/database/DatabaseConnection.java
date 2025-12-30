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
            e.printStackTrace();
        }
    }
    
    public static Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(DB_URL);
            }
        } catch (SQLException e) {
            e.printStackTrace();
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
                role TEXT DEFAULT 'caretaker',
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);
        
        // Hostels table
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS hostels (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                code TEXT UNIQUE NOT NULL,
                mess_name TEXT NOT NULL
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
        
        // Admins table
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS admins (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                email TEXT UNIQUE NOT NULL,
                password TEXT NOT NULL,
                name TEXT NOT NULL,
                designation TEXT,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);
    }
    
    private static void insertDefaultData() throws SQLException {
        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM hostels");
        if (rs.next() && rs.getInt(1) > 0) {
            return; // Data already exists
        }
        
        // Insert 7 hostels with their mess names
        // Kailash & Trikuta share "Central Mess"
        // Others have mess name same as hostel name
        String[][] hostels = {
            {"Kailash Hostel", "KH", "Central Mess"},
            {"Trikuta Hostel", "TH", "Central Mess"},
            {"Vindhyachal Hostel", "VH", "Vindhyachal Hostel Mess"},
            {"Basohli Hostel", "BH", "Basohli Hostel Mess"},
            {"Nilgiri Hostel", "NH", "Nilgiri Hostel Mess"},
            {"Shivalik Hostel", "SH", "Shivalik Hostel Mess"},
            {"Vaishnavi Hostel", "VNH", "Vaishnavi Hostel Mess"}
        };
        
        PreparedStatement pstmt = connection.prepareStatement(
            "INSERT INTO hostels (name, code, mess_name) VALUES (?, ?, ?)"
        );
        
        for (String[] hostel : hostels) {
            pstmt.setString(1, hostel[0]);
            pstmt.setString(2, hostel[1]);
            pstmt.setString(3, hostel[2]);
            pstmt.executeUpdate();
        }
        
        // Insert caretakers with correct emails (password: admin123)
        String[][] caretakers = {
            {"caretaker.kailashhostel@smvdu.ac.in", "admin123", "Kailash Caretaker", "1"},
            {"caretaker.trikutahostel@smvdu.ac.in", "admin123", "Trikuta Caretaker", "2"},
            {"caretaker.vindhyachalhostel@smvdu.ac.in", "admin123", "Vindhyachal Caretaker", "3"},
            {"caretaker.basohlihostel@smvdu.ac.in", "admin123", "Basohli Caretaker", "4"},
            {"caretaker.nilgirihostel@smvdu.ac.in", "admin123", "Nilgiri Caretaker", "5"},
            {"caretaker.shivalikhostela@smvdu.ac.in", "admin123", "Shivalik Block A Caretaker", "6"},
            {"caretaker.vaishnavihostel@smvdu.ac.in", "admin123", "Vaishnavi Caretaker", "7"}
        };
        
        pstmt = connection.prepareStatement(
            "INSERT INTO users (email, password, name, hostel_id, role) VALUES (?, ?, ?, ?, 'caretaker')"
        );
        
        for (String[] caretaker : caretakers) {
            pstmt.setString(1, caretaker[0]);
            pstmt.setString(2, caretaker[1]);
            pstmt.setString(3, caretaker[2]);
            pstmt.setInt(4, Integer.parseInt(caretaker[3]));
            pstmt.executeUpdate();
        }
        
        // Insert admins with correct emails (password: admin123)
        String[][] admins = {
            {"vc.pk@smvdu.ac.in", "admin123", "Vice Chancellor", "VC"},
            {"dean.studens@smvdu.ac.in", "admin123", "Dean Student Welfare", "Dean"},
            {"registrar@smvdu.ac.in", "admin123", "Registrar", "Registrar"}
        };
        
        pstmt = connection.prepareStatement(
            "INSERT INTO admins (email, password, name, designation) VALUES (?, ?, ?, ?)"
        );
        
        for (String[] admin : admins) {
            pstmt.setString(1, admin[0]);
            pstmt.setString(2, admin[1]);
            pstmt.setString(3, admin[2]);
            pstmt.setString(4, admin[3]);
            pstmt.executeUpdate();
        }
        
        // Insert default settings
        stmt.execute("INSERT OR IGNORE INTO settings (key, value) VALUES ('per_day_rate', '120')");
        stmt.execute("INSERT OR IGNORE INTO settings (key, value) VALUES ('gst_percent', '5')");
        
        System.out.println("Default data inserted successfully!");
        System.out.println("7 Hostels: Kailash, Trikuta (Central Mess), Vindhyachal, Basohli, Nilgiri, Shivalik, Vaishnavi");
        System.out.println("3 Admins: VC, Dean, Registrar");
    }
}
