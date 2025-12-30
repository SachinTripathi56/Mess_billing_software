package com.smvdu.mess.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class UpdateDatabaseForCentralMess {
    
    public static void main(String[] args) {
        try {
            DatabaseConnection.initialize();
            Connection conn = DatabaseConnection.getConnection();
            Statement stmt = conn.createStatement();
            
            System.out.println("Adding mess_id to hostels table...");
            
            // Add mess_id column
            try {
                stmt.execute("ALTER TABLE hostels ADD COLUMN mess_id INTEGER");
                System.out.println("✓ Added mess_id column");
            } catch (SQLException e) {
                System.out.println("✓ mess_id column already exists");
            }
            
            // Create messes table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS messes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT UNIQUE NOT NULL,
                    code TEXT UNIQUE NOT NULL
                )
            """);
            System.out.println("✓ Created messes table");
            
            // Clear old data
            stmt.execute("DELETE FROM messes");
            
            // Insert messes
            stmt.execute("INSERT INTO messes (id, name, code) VALUES (1, 'Central Mess', 'CM')");
            stmt.execute("INSERT INTO messes (id, name, code) VALUES (2, 'Vindhyachal Hostel Mess', 'VHM')");
            stmt.execute("INSERT INTO messes (id, name, code) VALUES (3, 'Basohli Hostel Mess', 'BHM')");
            stmt.execute("INSERT INTO messes (id, name, code) VALUES (4, 'Nilgiri Hostel Mess', 'NHM')");
            stmt.execute("INSERT INTO messes (id, name, code) VALUES (5, 'Shivalik Hostel Mess', 'SHM')");
            stmt.execute("INSERT INTO messes (id, name, code) VALUES (6, 'Vaishnavi Hostel Mess', 'VNHM')");
            System.out.println("✓ Inserted 6 messes");
            
            // Update hostels with mess_id
            // Kailash and Trikuta → Central Mess (mess_id = 1)
            stmt.execute("UPDATE hostels SET mess_id = 1 WHERE id IN (1, 2)"); // Kailash, Trikuta
            stmt.execute("UPDATE hostels SET mess_id = 2 WHERE id = 3"); // Vindhyachal
            stmt.execute("UPDATE hostels SET mess_id = 3 WHERE id = 4"); // Basohli
            stmt.execute("UPDATE hostels SET mess_id = 4 WHERE id = 5"); // Nilgiri
            stmt.execute("UPDATE hostels SET mess_id = 5 WHERE id = 6"); // Shivalik
            stmt.execute("UPDATE hostels SET mess_id = 6 WHERE id = 7"); // Vaishnavi
            System.out.println("✓ Updated hostels with mess_id");
            
            System.out.println("\n=== CENTRAL MESS SETUP COMPLETE ===");
            System.out.println("Kailash Hostel (id=1) → Central Mess (mess_id=1)");
            System.out.println("Trikuta Hostel (id=2) → Central Mess (mess_id=1)");
            System.out.println("Other hostels → Their own mess");
            
            conn.close();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
