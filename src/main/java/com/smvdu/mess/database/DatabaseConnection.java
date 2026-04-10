package com.smvdu.mess.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConnection {

    // ================= SQLITE =================
    private static final String SQLITE_URL =
            "jdbc:sqlite:" + System.getProperty("user.home") + "/SMVDU-Mess/db/mess_billing.db";

    // ================= MYSQL RDS =================
  private static final String MYSQL_URL =
    "jdbc:mysql://smvdumessdb.c1cgcguwkhcq.ap-south-1.rds.amazonaws.com:3306/SmvduMessDb" +
    "?useSSL=true" +
    "&serverTimezone=UTC" +
    "&connectTimeout=5000" +
    "&socketTimeout=10000" +
    "&maxReconnects=1" +        // only retry once instead of 3 times
    "&autoReconnect=false";     // don't auto reconnect — we handle it manually

    private static final String MYSQL_USER     =null;  // enter ur aws user name
    private static final String MYSQL_PASSWORD = null;  // enter your password

    // ================= CONNECTION STATE =================
    // SQLite is ALWAYS the main connection used by the app
    // MySQL is ONLY used by SyncService to mirror data
    private static Connection sqliteConnection = null;
    private static Connection mysqlConnection  = null;

    // ================= INTERNET CHECK =================
    public static boolean isInternetAvailable() {
        try {
            var url  = new java.net.URL("https://www.google.com");
            var conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2000);
            conn.connect();
            conn.disconnect();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

//     public static void resetSyncFlags() {
//     try (Statement stmt = sqliteConnection.createStatement()) {
//         stmt.execute("UPDATE users SET is_synced = 0");
//         stmt.execute("UPDATE admins SET is_synced = 0");
//         stmt.execute("UPDATE hostels SET is_synced = 0");
//         stmt.execute("UPDATE messes SET is_synced = 0");
//         stmt.execute("UPDATE students SET is_synced = 0");
//         stmt.execute("UPDATE student_attendance SET is_synced = 0");
//         stmt.execute("UPDATE settings SET is_synced = 0");
//         stmt.execute("UPDATE bill_configurations SET is_synced = 0");
//         stmt.execute("UPDATE generated_bills SET is_synced = 0");
//         System.out.println("✅ Sync flags reset — full resync will happen");
//     } catch (Exception e) {
//         e.printStackTrace();
//     }
// }



    // ================= INITIALIZE =================
    // Called once from App.java on startup
    // SQLite is always the main DB — MySQL connects in background silently
    public static void initialize() {
        try {
            // ✅ Create local folder
            new File(System.getProperty("user.home") + "/SMVDU-Mess/db").mkdirs();

            // ✅ Always connect SQLite — this is the main database
            Class.forName("org.sqlite.JDBC");
            sqliteConnection = DriverManager.getConnection(SQLITE_URL);

            Statement stmt = sqliteConnection.createStatement();
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute("PRAGMA busy_timeout=5000;");
            stmt.close();

            System.out.println("💾 SQLite connected");

            // ✅ All setup runs on SQLite
            createTables();
            migrateDatabase();
            insertDefaultData();

            System.out.println("✅ Database Ready");

            // ✅ Try MySQL in background thread — does NOT block the UI
            new Thread(() -> {
                try {
                    if (!isInternetAvailable()) {
                        System.out.println("⚠️ No internet — MySQL skipped");
                        return;
                    }
                    Class.forName("com.mysql.cj.jdbc.Driver");
                    mysqlConnection = DriverManager.getConnection(
                            MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD
                    );
                    System.out.println("✅ MySQL backup connection ready");
                } catch (Exception e) {
                    System.out.println("⚠️ MySQL unavailable: " + e.getMessage());
                    mysqlConnection = null;
                }
            }, "mysql-init-thread").start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ================= GET CONNECTION =================
    // Always returns SQLite — used by entire app for all reads/writes
    public static Connection getConnection() {
        try {
            if (sqliteConnection == null || sqliteConnection.isClosed()) {
                Class.forName("org.sqlite.JDBC");
                sqliteConnection = DriverManager.getConnection(SQLITE_URL);
                System.out.println("💾 SQLite reconnected");
            }
            return sqliteConnection;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ================= GET MYSQL CONNECTION =================
    // Only used by SyncService — never by the app itself
    public static Connection getMySQLConnection() {
        try {
            if (mysqlConnection != null && !mysqlConnection.isClosed()) {
                return mysqlConnection;
            }

            if (!isInternetAvailable()) {
                System.out.println("⚠️ No internet for MySQL");
                return null;
            }

            Class.forName("com.mysql.cj.jdbc.Driver");
            mysqlConnection = DriverManager.getConnection(
                    MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD
            );
            System.out.println("✅ MySQL reconnected");
            return mysqlConnection;

        } catch (Exception e) {
            System.out.println("⚠️ MySQL connection failed: " + e.getMessage());
            mysqlConnection = null;
            return null;
        }
    }

    // ================= CLOSE ALL =================
    public static void closeAllConnections() {
        try {
            if (sqliteConnection != null && !sqliteConnection.isClosed()) {
                sqliteConnection.close();
                System.out.println("✅ SQLite closed");
            }
            if (mysqlConnection != null && !mysqlConnection.isClosed()) {
                mysqlConnection.close();
                System.out.println("✅ MySQL closed");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ================= CREATE TABLES =================
    private static void createTables() throws SQLException {
        Statement stmt = sqliteConnection.createStatement();

        // SQLite only — always AUTOINCREMENT syntax here
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                email VARCHAR(255) UNIQUE NOT NULL,
                password TEXT NOT NULL,
                name TEXT NOT NULL,
                hostel_id INTEGER NOT NULL,
                role TEXT DEFAULT 'caretaker',
                otp TEXT,
                otp_expiry DATETIME,
                is_synced INTEGER DEFAULT 0,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS hostels (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                code VARCHAR(50) UNIQUE NOT NULL,
                mess_name TEXT NOT NULL,
                mess_id INTEGER,
                is_synced INTEGER DEFAULT 0
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS students (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                entry_number VARCHAR(50) UNIQUE NOT NULL,
                name TEXT NOT NULL,
                hostel_id INTEGER NOT NULL,
                room_number TEXT,
                phone TEXT,
                email TEXT,
                is_active INTEGER DEFAULT 1,
                is_synced INTEGER DEFAULT 0,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (hostel_id) REFERENCES hostels(id)
            )
        """);

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
                is_synced INTEGER DEFAULT 0,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (student_id) REFERENCES students(id),
                UNIQUE(student_id, month, year)
            )
        """);

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
                fine_amount REAL DEFAULT 0,
                total_amount REAL NOT NULL,
                generated_by INTEGER,
                is_synced INTEGER DEFAULT 0,
                generated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (hostel_id) REFERENCES hostels(id),
                FOREIGN KEY (generated_by) REFERENCES users(id)
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS settings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                key TEXT UNIQUE NOT NULL,
                value TEXT NOT NULL,
                is_synced INTEGER DEFAULT 0
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS admins (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                email VARCHAR(255) UNIQUE NOT NULL,
                password TEXT NOT NULL,
                name TEXT NOT NULL,
                designation TEXT,
                otp TEXT,
                otp_expiry DATETIME,
                is_synced INTEGER DEFAULT 0,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS messes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name VARCHAR(255) UNIQUE NOT NULL,
                code VARCHAR(50) UNIQUE NOT NULL,
                is_synced INTEGER DEFAULT 0
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS mess_operation_days (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                mess_id INTEGER NOT NULL,
                month INTEGER NOT NULL,
                year INTEGER NOT NULL,
                operating_days INTEGER NOT NULL,
                is_synced INTEGER DEFAULT 0,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                UNIQUE (mess_id, month, year),
                FOREIGN KEY (mess_id) REFERENCES messes(id)
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS bill_configurations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                mess_id INTEGER NOT NULL,
                month INTEGER NOT NULL,
                year INTEGER NOT NULL,
                start_date TEXT NOT NULL,
                end_date TEXT NOT NULL,
                operating_days INTEGER NOT NULL,
                fine_amount REAL DEFAULT 0,
                is_synced INTEGER DEFAULT 0,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                UNIQUE (mess_id, month, year),
                FOREIGN KEY (mess_id) REFERENCES messes(id)
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS generated_bills (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                mess_id INTEGER NOT NULL,
                mess_name TEXT NOT NULL,
                month INTEGER NOT NULL,
                year INTEGER NOT NULL,
                bill_period TEXT NOT NULL,
                operating_days INTEGER NOT NULL,
                total_students INTEGER NOT NULL,
                total_student_days INTEGER NOT NULL,
                total_absent_days INTEGER NOT NULL,
                total_mess_days INTEGER NOT NULL,
                per_day_rate REAL NOT NULL,
                subtotal REAL NOT NULL,
                gst_percent REAL NOT NULL,
                gst_amount REAL NOT NULL,
                fine_amount REAL DEFAULT 0,
                total_amount REAL NOT NULL,
                generated_by TEXT NOT NULL,
                is_synced INTEGER DEFAULT 0,
                generated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (mess_id) REFERENCES messes(id)
            )
        """);

        System.out.println("✓ All tables created successfully");
        stmt.close();
    }

    // ================= MIGRATE DATABASE =================
    private static void migrateDatabase() {
        try (Statement stmt = sqliteConnection.createStatement()) {

            // Helper: check if column exists in SQLite table
            // and add it if missing

            String[] tablesNeedingIsSync = {
                "users", "admins", "hostels", "messes", "students",
                "student_attendance", "settings",
                "bill_configurations", "generated_bills"
            };

            for (String table : tablesNeedingIsSync) {
                ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")");
                boolean exists = false;
                while (rs.next()) {
                    if ("is_synced".equals(rs.getString("name"))) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    stmt.execute("ALTER TABLE " + table +
                                 " ADD COLUMN is_synced INTEGER DEFAULT 0");
                    System.out.println("✓ Added is_synced to " + table);
                }
            }

            // ✅ Check mess_id in hostels
            ResultSet rs = stmt.executeQuery("PRAGMA table_info(hostels)");
            boolean messIdExists = false;
            while (rs.next()) {
                if ("mess_id".equals(rs.getString("name"))) {
                    messIdExists = true;
                    break;
                }
            }
            if (!messIdExists) {
                stmt.execute("ALTER TABLE hostels ADD COLUMN mess_id INTEGER");
                System.out.println("✓ Added mess_id to hostels");
            }

            // ✅ Check otp columns in users
            rs = stmt.executeQuery("PRAGMA table_info(users)");
            boolean otpExists = false, otpExpiryExists = false;
            while (rs.next()) {
                String col = rs.getString("name");
                if ("otp".equals(col))        otpExists = true;
                if ("otp_expiry".equals(col)) otpExpiryExists = true;
            }
            if (!otpExists)       stmt.execute("ALTER TABLE users ADD COLUMN otp TEXT");
            if (!otpExpiryExists) stmt.execute("ALTER TABLE users ADD COLUMN otp_expiry DATETIME");

            // ✅ Check otp columns in admins
            rs = stmt.executeQuery("PRAGMA table_info(admins)");
            otpExists = false; otpExpiryExists = false;
            while (rs.next()) {
                String col = rs.getString("name");
                if ("otp".equals(col))        otpExists = true;
                if ("otp_expiry".equals(col)) otpExpiryExists = true;
            }
            if (!otpExists)       stmt.execute("ALTER TABLE admins ADD COLUMN otp TEXT");
            if (!otpExpiryExists) stmt.execute("ALTER TABLE admins ADD COLUMN otp_expiry DATETIME");

            // ✅ Insert messes
            stmt.execute("""
                INSERT OR IGNORE INTO messes (id, name, code) VALUES
                (1, 'Central Mess', 'CM'),
                (2, 'Vindhyachal Hostel Mess', 'VHM'),
                (3, 'Old Basohli Hostel Mess', 'OBHM'),
                (4, 'New Basohli Hostel Mess', 'NBHM'),
                (5, 'Nilgiri Hostel Mess', 'NHM'),
                (6, 'Shivalik A Hostel Mess', 'SAHM'),
                (7, 'Shivalik B Hostel Mess', 'SBHM'),
                (8, 'Vaishnavi Hostel Mess', 'VNHM')
            """);

            // ✅ Link hostels to messes
            stmt.execute("UPDATE hostels SET mess_id = 1 WHERE id IN (1,2) AND mess_id IS NULL");
            stmt.execute("UPDATE hostels SET mess_id = 2 WHERE id = 3 AND mess_id IS NULL");
            stmt.execute("UPDATE hostels SET mess_id = 3 WHERE id = 4 AND mess_id IS NULL");
            stmt.execute("UPDATE hostels SET mess_id = 5 WHERE id = 5 AND mess_id IS NULL");
            stmt.execute("UPDATE hostels SET mess_id = 6 WHERE id = 6 AND mess_id IS NULL");
            stmt.execute("UPDATE hostels SET mess_id = 8 WHERE id = 7 AND mess_id IS NULL");

            System.out.println("✓ Database migration completed");

        } catch (Exception e) {
            System.err.println("Migration error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ================= INSERT DEFAULT DATA =================
    private static void insertDefaultData() throws SQLException {
        Statement stmt = sqliteConnection.createStatement();

        ResultSet rsHostels = stmt.executeQuery("SELECT COUNT(*) FROM hostels");
        if (rsHostels.next() && rsHostels.getInt(1) > 0) {
            System.out.println("✓ Default data already exists");
            return;
        }

        String[][] hostels = {
            {"Kailash Hostel",      "KH",  "Central Mess"},
            {"Trikuta Hostel",      "TH",  "Central Mess"},
            {"Vindhyachal Hostel",  "VH",  "Vindhyachal Hostel Mess"},
            {"Old Basohli Hostel",  "OBH", "Old Basohli Hostel Mess"},
            {"Nilgiri Hostel",      "NH",  "Nilgiri Hostel Mess"},
            {"Shivalik A Hostel",   "SHA", "Shivalik A Hostel Mess"},
            {"Vaishnavi Hostel",    "VNH", "Vaishnavi Hostel Mess"},
            {"New Basohli Hostel",  "NBH", "New Basohli Hostel Mess"},
            {"Shivalik B Hostel",   "SHB", "Shivalik B Hostel Mess"}
        };

        PreparedStatement pstmt = sqliteConnection.prepareStatement(
                "INSERT INTO hostels (name, code, mess_name) VALUES (?, ?, ?)"
        );
        for (String[] h : hostels) {
            pstmt.setString(1, h[0]);
            pstmt.setString(2, h[1]);
            pstmt.setString(3, h[2]);
            pstmt.executeUpdate();
        }

        stmt.execute("UPDATE hostels SET mess_id = 4 WHERE id = 8");
        stmt.execute("UPDATE hostels SET mess_id = 7 WHERE id = 9");

        String[][] caretakers = {
            {"caretaker.kailashhostel@smvdu.ac.in",     "admin123", "Kailash Caretaker",     "1"},
            {"caretaker.trikutahostel@smvdu.ac.in",     "admin123", "Trikuta Caretaker",     "2"},
            {"caretaker.vindhyachalhostel@smvdu.ac.in", "admin123", "Vindhyachal Caretaker", "3"},
            {"caretaker.oldbasohlihostel@smvdu.ac.in",  "admin123", "Old Basohli Caretaker", "4"},
            {"caretaker.nilgirihostel@smvdu.ac.in",     "admin123", "Nilgiri Caretaker",     "5"},
            {"caretaker.shivalikahostel@smvdu.ac.in",   "admin123", "Shivalik A Caretaker",  "6"},
            {"caretaker.vaishnavihostel@smvdu.ac.in",   "admin123", "Vaishnavi Caretaker",   "7"},
            {"caretaker.newbasohlihostel@smvdu.ac.in",  "admin123", "New Basohli Caretaker", "8"},
            {"bigdaddyman18@gmail.com",                 "admin123", "Shivalik B Caretaker",  "9"}
        };

        pstmt = sqliteConnection.prepareStatement(
                "INSERT INTO users (email, password, name, hostel_id, role) VALUES (?, ?, ?, ?, 'caretaker')"
        );
        for (String[] c : caretakers) {
            pstmt.setString(1, c[0]);
            pstmt.setString(2, c[1]);
            pstmt.setString(3, c[2]);
            pstmt.setInt(4, Integer.parseInt(c[3]));
            pstmt.executeUpdate();
        }

        String[][] admins = {
            {"vc.pk@smvdu.ac.in",        "admin123", "Vice Chancellor",      "VC"},
            {"dean.studens@smvdu.ac.in", "admin123", "Dean Student Welfare", "Dean"},
            {"registrar@smvdu.ac.in",    "admin123", "Registrar",            "Registrar"}
        };

        pstmt = sqliteConnection.prepareStatement(
                "INSERT INTO admins (email, password, name, designation) VALUES (?, ?, ?, ?)"
        );
        for (String[] a : admins) {
            pstmt.setString(1, a[0]);
            pstmt.setString(2, a[1]);
            pstmt.setString(3, a[2]);
            pstmt.setString(4, a[3]);
            pstmt.executeUpdate();
        }

        stmt.execute("INSERT OR IGNORE INTO settings (key, value) VALUES ('per_day_rate', '120')");
        stmt.execute("INSERT OR IGNORE INTO settings (key, value) VALUES ('gst_percent', '5')");

        System.out.println("✓ Default data inserted successfully!");
        System.out.println("✓ Created 9 hostels, 9 caretakers, 3 admins");
    }
}