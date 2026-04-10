package com.smvdu.mess.utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.smvdu.mess.database.DatabaseConnection;

public class SyncService {

    // =====================================================================
    //  FLAG: has the initial MySQL → SQLite pull completed successfully?
    //  Checked every sync cycle — retried until it succeeds.
    // =====================================================================
    private static volatile boolean initialPullDone = false;

    public static boolean isInitialPullDone() { return initialPullDone; }

    // =====================================================================
    //  SYNC ALL  (SQLite → MySQL push, called every 30 s)
    // =====================================================================
    public static void syncAll() {
        System.out.println("🔄 syncAll() called");

        if (!DatabaseConnection.isInternetAvailable()) {
            System.out.println("⚠️ No internet, skipping sync");
            return;
        }

        Connection remote = DatabaseConnection.getMySQLConnection();
        if (remote == null) {
            System.out.println("⚠️ MySQL not available, skipping sync");
            return;
        }

        // If pull never succeeded yet, retry it now before pushing
        if (!initialPullDone) {
            System.out.println("🔄 Initial pull not done yet — retrying...");
            pullFromMySQL();
        }

        System.out.println("🔄 Starting push sync...");
        syncMesses();
        syncHostels();
        syncUsers();
        syncAdmins();
        syncStudents();
        syncStudentAttendance();
        syncSettings();
        syncBillConfigurations();
        syncGeneratedBills();
        System.out.println("✅ Push sync completed");
    }

    // =====================================================================
    //  PULL FROM MYSQL → SQLITE
    //  Called once at first successful login (internet guaranteed at that
    //  point). Also retried by syncAll() if it failed before.
    //  Uses INSERT OR IGNORE so existing local data is NEVER overwritten.
    //  All pulled rows get is_synced=1 so they are not pushed back.
    // =====================================================================
    public static void pullFromMySQL() {
        try {
            Connection local  = DatabaseConnection.getConnection();
            Connection remote = DatabaseConnection.getMySQLConnection();
            if (remote == null) {
                System.out.println("⚠️ Pull skipped — MySQL not available");
                return;
            }

            System.out.println("🔽 Pulling data from MySQL...");

            pullMesses(local, remote);
            pullHostels(local, remote);
            pullUsers(local, remote);
            pullAdmins(local, remote);
            pullStudents(local, remote);
            pullStudentAttendance(local, remote);
            pullSettings(local, remote);
            pullBillConfigurations(local, remote);
            pullGeneratedBills(local, remote);

            initialPullDone = true;
            System.out.println("✅ Pull from MySQL complete");

        } catch (Exception e) {
            // Do NOT set initialPullDone — syncAll() will retry next cycle
            System.err.println("❌ Pull from MySQL failed (will retry): " + e.getMessage());
            e.printStackTrace();
        }
    }

    // -------  individual pull methods  -----------------------------------

    private static void pullMesses(Connection local, Connection remote) throws Exception {
        ResultSet rs = remote.createStatement().executeQuery("SELECT * FROM messes");
        PreparedStatement ps = local.prepareStatement(
            "INSERT OR IGNORE INTO messes (id, name, code, is_synced) VALUES (?,?,?,1)"
        );
        while (rs.next()) {
            ps.setInt   (1, rs.getInt("id"));
            ps.setString(2, rs.getString("name"));
            ps.setString(3, rs.getString("code"));
            ps.executeUpdate();
        }
        rs.close(); ps.close();
        System.out.println("  ✓ Messes pulled");
    }

    private static void pullHostels(Connection local, Connection remote) throws Exception {
        ResultSet rs = remote.createStatement().executeQuery("SELECT * FROM hostels");
        PreparedStatement ps = local.prepareStatement(
            "INSERT OR IGNORE INTO hostels (id, name, code, mess_name, mess_id, is_synced) " +
            "VALUES (?,?,?,?,?,1)"
        );
        while (rs.next()) {
            ps.setInt   (1, rs.getInt("id"));
            ps.setString(2, rs.getString("name"));
            ps.setString(3, rs.getString("code"));
            ps.setString(4, rs.getString("mess_name"));
            ps.setObject(5, rs.getObject("mess_id"));
            ps.executeUpdate();
        }
        rs.close(); ps.close();
        System.out.println("  ✓ Hostels pulled");
    }

    private static void pullUsers(Connection local, Connection remote) throws Exception {
        ResultSet rs = remote.createStatement().executeQuery("SELECT * FROM users");
        PreparedStatement ps = local.prepareStatement(
            "INSERT OR IGNORE INTO users " +
            "(email, password, name, hostel_id, role, otp, otp_expiry, is_synced) " +
            "VALUES (?,?,?,?,?,?,?,1)"
        );
        while (rs.next()) {
            ps.setString(1, rs.getString("email"));
            ps.setString(2, rs.getString("password"));
            ps.setString(3, rs.getString("name"));
            ps.setInt   (4, rs.getInt("hostel_id"));
            ps.setString(5, rs.getString("role"));
            ps.setString(6, rs.getString("otp"));
            ps.setString(7, rs.getString("otp_expiry"));
            ps.executeUpdate();
        }
        rs.close(); ps.close();
        System.out.println("  ✓ Users pulled");
    }

    private static void pullAdmins(Connection local, Connection remote) throws Exception {
        ResultSet rs = remote.createStatement().executeQuery("SELECT * FROM admins");
        PreparedStatement ps = local.prepareStatement(
            "INSERT OR IGNORE INTO admins " +
            "(email, password, name, designation, otp, otp_expiry, is_synced) " +
            "VALUES (?,?,?,?,?,?,1)"
        );
        while (rs.next()) {
            ps.setString(1, rs.getString("email"));
            ps.setString(2, rs.getString("password"));
            ps.setString(3, rs.getString("name"));
            ps.setString(4, rs.getString("designation"));
            ps.setString(5, rs.getString("otp"));
            ps.setString(6, rs.getString("otp_expiry"));
            ps.executeUpdate();
        }
        rs.close(); ps.close();
        System.out.println("  ✓ Admins pulled");
    }

    private static void pullStudents(Connection local, Connection remote) throws Exception {
        ResultSet rs = remote.createStatement().executeQuery("SELECT * FROM students");
        PreparedStatement ps = local.prepareStatement(
            "INSERT OR IGNORE INTO students " +
            "(entry_number, name, hostel_id, room_number, phone, email, is_active, is_synced) " +
            "VALUES (?,?,?,?,?,?,?,1)"
        );
        while (rs.next()) {
            ps.setString(1, rs.getString("entry_number"));
            ps.setString(2, rs.getString("name"));
            ps.setInt   (3, rs.getInt("hostel_id"));
            ps.setString(4, rs.getString("room_number"));
            ps.setString(5, rs.getString("phone"));
            ps.setString(6, rs.getString("email"));
            ps.setInt   (7, rs.getInt("is_active"));
            ps.executeUpdate();
        }
        rs.close(); ps.close();
        System.out.println("  ✓ Students pulled");
    }

    private static void pullStudentAttendance(Connection local, Connection remote) throws Exception {
        ResultSet rs = remote.createStatement().executeQuery("SELECT * FROM student_attendance");
        PreparedStatement ps = local.prepareStatement(
            "INSERT OR IGNORE INTO student_attendance " +
            "(student_id, month, year, total_days, mess_days, absent_days, remarks, is_synced) " +
            "VALUES (?,?,?,?,?,?,?,1)"
        );
        while (rs.next()) {
            ps.setInt   (1, rs.getInt("student_id"));
            ps.setInt   (2, rs.getInt("month"));
            ps.setInt   (3, rs.getInt("year"));
            ps.setInt   (4, rs.getInt("total_days"));
            ps.setInt   (5, rs.getInt("mess_days"));
            ps.setInt   (6, rs.getInt("absent_days"));
            ps.setString(7, rs.getString("remarks"));
            ps.executeUpdate();
        }
        rs.close(); ps.close();
        System.out.println("  ✓ Student attendance pulled");
    }

    private static void pullSettings(Connection local, Connection remote) throws Exception {
        ResultSet rs = remote.createStatement().executeQuery("SELECT * FROM settings");
        PreparedStatement ps = local.prepareStatement(
            "INSERT OR IGNORE INTO settings (`key`, value, is_synced) VALUES (?,?,1)"
        );
        while (rs.next()) {
            ps.setString(1, rs.getString("key"));
            ps.setString(2, rs.getString("value"));
            ps.executeUpdate();
        }
        rs.close(); ps.close();
        System.out.println("  ✓ Settings pulled");
    }

    private static void pullBillConfigurations(Connection local, Connection remote) throws Exception {
        ResultSet rs = remote.createStatement().executeQuery("SELECT * FROM bill_configurations");
        PreparedStatement ps = local.prepareStatement(
            "INSERT OR IGNORE INTO bill_configurations " +
            "(mess_id, month, year, start_date, end_date, operating_days, fine_amount, is_synced) " +
            "VALUES (?,?,?,?,?,?,?,1)"
        );
        while (rs.next()) {
            ps.setInt   (1, rs.getInt("mess_id"));
            ps.setInt   (2, rs.getInt("month"));
            ps.setInt   (3, rs.getInt("year"));
            ps.setString(4, rs.getString("start_date"));
            ps.setString(5, rs.getString("end_date"));
            ps.setInt   (6, rs.getInt("operating_days"));
            ps.setDouble(7, rs.getDouble("fine_amount"));
            ps.executeUpdate();
        }
        rs.close(); ps.close();
        System.out.println("  ✓ Bill configurations pulled");
    }

    private static void pullGeneratedBills(Connection local, Connection remote) throws Exception {
        ResultSet rs = remote.createStatement().executeQuery("SELECT * FROM generated_bills");
        PreparedStatement ps = local.prepareStatement(
            "INSERT OR IGNORE INTO generated_bills " +
            "(mess_id, mess_name, month, year, bill_period, operating_days, " +
            " total_students, total_student_days, total_absent_days, total_mess_days, " +
            " per_day_rate, subtotal, gst_percent, gst_amount, fine_amount, " +
            " total_amount, generated_by, generated_at, is_synced) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,1)"
        );
        while (rs.next()) {
            ps.setInt   (1,  rs.getInt("mess_id"));
            ps.setString(2,  rs.getString("mess_name"));
            ps.setInt   (3,  rs.getInt("month"));
            ps.setInt   (4,  rs.getInt("year"));
            ps.setString(5,  rs.getString("bill_period"));
            ps.setInt   (6,  rs.getInt("operating_days"));
            ps.setInt   (7,  rs.getInt("total_students"));
            ps.setInt   (8,  rs.getInt("total_student_days"));
            ps.setInt   (9,  rs.getInt("total_absent_days"));
            ps.setInt   (10, rs.getInt("total_mess_days"));
            ps.setDouble(11, rs.getDouble("per_day_rate"));
            ps.setDouble(12, rs.getDouble("subtotal"));
            ps.setDouble(13, rs.getDouble("gst_percent"));
            ps.setDouble(14, rs.getDouble("gst_amount"));
            ps.setDouble(15, rs.getDouble("fine_amount"));
            ps.setDouble(16, rs.getDouble("total_amount"));
            ps.setString(17, rs.getString("generated_by"));
            ps.setString(18, rs.getString("generated_at"));
            ps.executeUpdate();
        }
        rs.close(); ps.close();
        System.out.println("  ✓ Generated bills pulled");
    }

    // =====================================================================
    //  PUSH SYNC METHODS  (SQLite → MySQL, unchanged from your original)
    // =====================================================================

    public static void syncUsers() {
        try {
            Connection local  = DatabaseConnection.getConnection();
            Connection remote = DatabaseConnection.getMySQLConnection();
            if (remote == null) return;

            remote.createStatement().execute("SET FOREIGN_KEY_CHECKS = 0");

            PreparedStatement ps = local.prepareStatement(
                "SELECT * FROM users WHERE is_synced = 0"
            );
            ResultSet rs = ps.executeQuery();
            boolean anySynced = false;

            while (rs.next()) {
                try (PreparedStatement up = remote.prepareStatement("""
                    INSERT INTO users
                        (email, password, name, hostel_id, role, otp, otp_expiry)
                    VALUES (?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE
                        password=VALUES(password),
                        name=VALUES(name),
                        hostel_id=VALUES(hostel_id),
                        role=VALUES(role),
                        otp=VALUES(otp),
                        otp_expiry=VALUES(otp_expiry)
                """)) {
                    up.setString(1, rs.getString("email"));
                    up.setString(2, rs.getString("password"));
                    up.setString(3, rs.getString("name"));
                    up.setInt   (4, rs.getInt("hostel_id"));
                    up.setString(5, rs.getString("role"));
                    up.setString(6, rs.getString("otp"));
                    up.setString(7, rs.getString("otp_expiry"));
                    up.executeUpdate();
                    anySynced = true;
                } catch (Exception e) {
                    System.err.println("❌ Failed to sync user: "
                        + rs.getString("email") + " → " + e.getMessage());
                }

                try (PreparedStatement mark = local.prepareStatement(
                    "UPDATE users SET is_synced = 1 WHERE email = ?"
                )) {
                    mark.setString(1, rs.getString("email"));
                    mark.executeUpdate();
                }
            }

            remote.createStatement().execute("SET FOREIGN_KEY_CHECKS = 1");
            rs.close(); ps.close();
            if (anySynced) System.out.println("✓ Users synced");

        } catch (Exception e) {
            System.err.println("Users sync error: " + e.getMessage());
        }
    }

    public static void syncAdmins() {
        try {
            Connection local  = DatabaseConnection.getConnection();
            Connection remote = DatabaseConnection.getMySQLConnection();
            if (remote == null) return;

            PreparedStatement ps = local.prepareStatement(
                "SELECT * FROM admins WHERE is_synced = 0"
            );
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                try (PreparedStatement up = remote.prepareStatement("""
                    INSERT INTO admins
                        (email, password, name, designation, otp, otp_expiry)
                    VALUES (?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE
                        password=VALUES(password),
                        name=VALUES(name),
                        designation=VALUES(designation),
                        otp=VALUES(otp),
                        otp_expiry=VALUES(otp_expiry)
                """)) {
                    up.setString(1, rs.getString("email"));
                    up.setString(2, rs.getString("password"));
                    up.setString(3, rs.getString("name"));
                    up.setString(4, rs.getString("designation"));
                    up.setString(5, rs.getString("otp"));
                    up.setString(6, rs.getString("otp_expiry"));
                    up.executeUpdate();
                }
                markSynced(local, "admins", "email", rs.getString("email"));
            }
            rs.close(); ps.close();
            System.out.println("✓ Admins synced");

        } catch (Exception e) {
            System.err.println("Admins sync error: " + e.getMessage());
        }
    }

    public static void syncHostels() {
        try {
            Connection local  = DatabaseConnection.getConnection();
            Connection remote = DatabaseConnection.getMySQLConnection();
            if (remote == null) return;

            PreparedStatement ps = local.prepareStatement(
                "SELECT * FROM hostels WHERE is_synced = 0"
            );
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                try (PreparedStatement up = remote.prepareStatement("""
                    INSERT INTO hostels (id, name, code, mess_name, mess_id)
                    VALUES (?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE
                        name=VALUES(name),
                        code=VALUES(code),
                        mess_name=VALUES(mess_name),
                        mess_id=VALUES(mess_id)
                """)) {
                    up.setInt   (1, rs.getInt("id"));
                    up.setString(2, rs.getString("name"));
                    up.setString(3, rs.getString("code"));
                    up.setString(4, rs.getString("mess_name"));
                    up.setObject(5, rs.getObject("mess_id"));
                    up.executeUpdate();
                }
                markSyncedById(local, "hostels", rs.getInt("id"));
            }
            rs.close(); ps.close();
            System.out.println("✓ Hostels synced");

        } catch (Exception e) {
            System.err.println("Hostels sync error: " + e.getMessage());
        }
    }

    public static void syncMesses() {
        try {
            Connection local  = DatabaseConnection.getConnection();
            Connection remote = DatabaseConnection.getMySQLConnection();
            if (remote == null) return;

            PreparedStatement ps = local.prepareStatement(
                "SELECT * FROM messes WHERE is_synced = 0"
            );
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                try (PreparedStatement up = remote.prepareStatement("""
                    INSERT INTO messes (id, name, code)
                    VALUES (?,?,?)
                    ON DUPLICATE KEY UPDATE
                        name=VALUES(name),
                        code=VALUES(code)
                """)) {
                    up.setInt   (1, rs.getInt("id"));
                    up.setString(2, rs.getString("name"));
                    up.setString(3, rs.getString("code"));
                    up.executeUpdate();
                }
                markSyncedById(local, "messes", rs.getInt("id"));
            }
            rs.close(); ps.close();
            System.out.println("✓ Messes synced");

        } catch (Exception e) {
            System.err.println("Messes sync error: " + e.getMessage());
        }
    }

    public static void syncStudents() {
        try {
            Connection local  = DatabaseConnection.getConnection();
            Connection remote = DatabaseConnection.getMySQLConnection();
            if (remote == null) return;

            PreparedStatement ps = local.prepareStatement(
                "SELECT * FROM students WHERE is_synced = 0"
            );
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                try (PreparedStatement up = remote.prepareStatement("""
                    INSERT INTO students
                        (entry_number, name, hostel_id, room_number, phone, email, is_active)
                    VALUES (?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE
                        name=VALUES(name),
                        hostel_id=VALUES(hostel_id),
                        room_number=VALUES(room_number),
                        phone=VALUES(phone),
                        email=VALUES(email),
                        is_active=VALUES(is_active)
                """)) {
                    up.setString(1, rs.getString("entry_number"));
                    up.setString(2, rs.getString("name"));
                    up.setInt   (3, rs.getInt("hostel_id"));
                    up.setString(4, rs.getString("room_number"));
                    up.setString(5, rs.getString("phone"));
                    up.setString(6, rs.getString("email"));
                    up.setInt   (7, rs.getInt("is_active"));
                    up.executeUpdate();
                }
                markSynced(local, "students", "entry_number", rs.getString("entry_number"));
            }
            rs.close(); ps.close();
            System.out.println("✓ Students synced");

        } catch (Exception e) {
            System.err.println("Students sync error: " + e.getMessage());
        }
    }

    public static void syncStudentAttendance() {
        try {
            Connection local  = DatabaseConnection.getConnection();
            Connection remote = DatabaseConnection.getMySQLConnection();
            if (remote == null) return;

            PreparedStatement ps = local.prepareStatement(
                "SELECT * FROM student_attendance WHERE is_synced = 0"
            );
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                try (PreparedStatement up = remote.prepareStatement("""
                    INSERT INTO student_attendance
                        (student_id, month, year, total_days, mess_days, absent_days, remarks)
                    VALUES (?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE
                        total_days=VALUES(total_days),
                        mess_days=VALUES(mess_days),
                        absent_days=VALUES(absent_days),
                        remarks=VALUES(remarks)
                """)) {
                    up.setInt   (1, rs.getInt("student_id"));
                    up.setInt   (2, rs.getInt("month"));
                    up.setInt   (3, rs.getInt("year"));
                    up.setInt   (4, rs.getInt("total_days"));
                    up.setInt   (5, rs.getInt("mess_days"));
                    up.setInt   (6, rs.getInt("absent_days"));
                    up.setString(7, rs.getString("remarks"));
                    up.executeUpdate();
                }
                markSyncedById(local, "student_attendance", rs.getInt("id"));
            }
            rs.close(); ps.close();
            System.out.println("✓ Student attendance synced");

        } catch (Exception e) {
            System.err.println("Attendance sync error: " + e.getMessage());
        }
    }

    public static void syncSettings() {
        try {
            Connection local  = DatabaseConnection.getConnection();
            Connection remote = DatabaseConnection.getMySQLConnection();
            if (remote == null) return;

            PreparedStatement ps = local.prepareStatement(
                "SELECT * FROM settings WHERE is_synced = 0"
            );
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                try (PreparedStatement up = remote.prepareStatement("""
                    INSERT INTO settings (`key`, value)
                    VALUES (?,?)
                    ON DUPLICATE KEY UPDATE
                        value=VALUES(value)
                """)) {
                    up.setString(1, rs.getString("key"));
                    up.setString(2, rs.getString("value"));
                    up.executeUpdate();
                }
                markSyncedById(local, "settings", rs.getInt("id"));
            }
            rs.close(); ps.close();
            System.out.println("✓ Settings synced");

        } catch (Exception e) {
            System.err.println("Settings sync error: " + e.getMessage());
        }
    }

    public static void syncBillConfigurations() {
        try {
            Connection local  = DatabaseConnection.getConnection();
            Connection remote = DatabaseConnection.getMySQLConnection();
            if (remote == null) return;

            PreparedStatement ps = local.prepareStatement(
                "SELECT * FROM bill_configurations WHERE is_synced = 0"
            );
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                try (PreparedStatement up = remote.prepareStatement("""
                    INSERT INTO bill_configurations
                        (mess_id, month, year, start_date, end_date, operating_days, fine_amount)
                    VALUES (?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE
                        start_date=VALUES(start_date),
                        end_date=VALUES(end_date),
                        operating_days=VALUES(operating_days),
                        fine_amount=VALUES(fine_amount)
                """)) {
                    up.setInt   (1, rs.getInt("mess_id"));
                    up.setInt   (2, rs.getInt("month"));
                    up.setInt   (3, rs.getInt("year"));
                    up.setString(4, rs.getString("start_date"));
                    up.setString(5, rs.getString("end_date"));
                    up.setInt   (6, rs.getInt("operating_days"));
                    up.setDouble(7, rs.getDouble("fine_amount"));
                    up.executeUpdate();
                }
                markSyncedById(local, "bill_configurations", rs.getInt("id"));
            }
            rs.close(); ps.close();
            System.out.println("✓ Bill configurations synced");

        } catch (Exception e) {
            System.err.println("Bill config sync error: " + e.getMessage());
        }
    }

    public static void syncGeneratedBills() {
        try {
            Connection local  = DatabaseConnection.getConnection();
            Connection remote = DatabaseConnection.getMySQLConnection();
            if (remote == null) return;

            PreparedStatement ps = local.prepareStatement(
                "SELECT * FROM generated_bills WHERE is_synced = 0"
            );
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                try (PreparedStatement up = remote.prepareStatement("""
                    INSERT INTO generated_bills (
                        mess_id, mess_name, month, year, bill_period,
                        operating_days, total_students, total_student_days,
                        total_absent_days, total_mess_days, per_day_rate,
                        subtotal, gst_percent, gst_amount, fine_amount,
                        total_amount, generated_by, generated_at
                    ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE
                        total_amount=VALUES(total_amount),
                        generated_at=VALUES(generated_at)
                """)) {
                    up.setInt   (1,  rs.getInt("mess_id"));
                    up.setString(2,  rs.getString("mess_name"));
                    up.setInt   (3,  rs.getInt("month"));
                    up.setInt   (4,  rs.getInt("year"));
                    up.setString(5,  rs.getString("bill_period"));
                    up.setInt   (6,  rs.getInt("operating_days"));
                    up.setInt   (7,  rs.getInt("total_students"));
                    up.setInt   (8,  rs.getInt("total_student_days"));
                    up.setInt   (9,  rs.getInt("total_absent_days"));
                    up.setInt   (10, rs.getInt("total_mess_days"));
                    up.setDouble(11, rs.getDouble("per_day_rate"));
                    up.setDouble(12, rs.getDouble("subtotal"));
                    up.setDouble(13, rs.getDouble("gst_percent"));
                    up.setDouble(14, rs.getDouble("gst_amount"));
                    up.setDouble(15, rs.getDouble("fine_amount"));
                    up.setDouble(16, rs.getDouble("total_amount"));
                    up.setString(17, rs.getString("generated_by"));
                    up.setString(18, rs.getString("generated_at"));
                    up.executeUpdate();
                }
                markSyncedById(local, "generated_bills", rs.getInt("id"));
            }
            rs.close(); ps.close();
            System.out.println("✓ Generated bills synced");

        } catch (Exception e) {
            System.err.println("Generated bills sync error: " + e.getMessage());
        }
    }

    // =====================================================================
    //  HELPERS
    // =====================================================================
    private static void markSynced(Connection local, String table,
                                    String col, String val) throws Exception {
        try (PreparedStatement ps = local.prepareStatement(
            "UPDATE " + table + " SET is_synced = 1 WHERE " + col + " = ?"
        )) {
            ps.setString(1, val);
            ps.executeUpdate();
        }
    }

    private static void markSyncedById(Connection local,
                                        String table, int id) throws Exception {
        try (PreparedStatement ps = local.prepareStatement(
            "UPDATE " + table + " SET is_synced = 1 WHERE id = ?"
        )) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public static void closeConnections() {
        System.out.println("✅ SyncService shutdown");
    }

    public static void debugSync() {
        try {
            Connection local  = DatabaseConnection.getConnection();
            Connection remote = DatabaseConnection.getMySQLConnection();
            if (remote == null) { System.out.println("❌ DEBUG: MySQL is null"); return; }

            String[] tables = { "users","admins","hostels","messes","students",
                                 "student_attendance","settings","bill_configurations","generated_bills" };
            for (String t : tables) {
                try {
                    ResultSet rs = local.createStatement()
                        .executeQuery("SELECT COUNT(*) FROM " + t + " WHERE is_synced = 0");
                    rs.next();
                    System.out.println("📊 " + t + " unsynced: " + rs.getInt(1));
                } catch (Exception e) {
                    System.out.println("❌ " + t + ": " + e.getMessage());
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
