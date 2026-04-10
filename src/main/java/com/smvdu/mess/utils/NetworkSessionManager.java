package com.smvdu.mess.utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;

import com.smvdu.mess.database.DatabaseConnection;

/**
 * Manages caretaker session locking across multiple PCs via MySQL.
 *
 * Rules:
 *  - Only caretakers are locked (one PC at a time).
 *  - Admins skip the lock — multiple admins can view simultaneously.
 *  - A heartbeat runs every 60 s to keep the session alive.
 *  - If heartbeat goes stale (>2 min), another PC may take over.
 *  - On logout the session is explicitly released.
 */
public class NetworkSessionManager {

    // How long before a session is considered dead (milliseconds)
    private static final long STALE_THRESHOLD_MS = 2 * 60 * 1000; // 2 minutes

    private static Thread heartbeatThread = null;
    private static volatile String activeEmail = null;

    // =====================================================================
    //  RESULT ENUM  — returned to LoginController so UI can react
    // =====================================================================
    public enum SessionResult {
        SUCCESS,
        NO_INTERNET,               // internet required but not available
        SESSION_ACTIVE_ELSEWHERE,  // another PC holds the session right now
        MYSQL_UNAVAILABLE,         // can't reach MySQL (unexpected)
        ERROR
    }

    // =====================================================================
    //  ENSURE SESSIONS TABLE EXISTS IN MYSQL
    //  Called once from DatabaseConnection after MySQL connects.
    // =====================================================================
public static void ensureSessionsTable() {
    try {
        Connection remote = DatabaseConnection.getMySQLConnection();
        if (remote == null) return;

        try (var stmt = remote.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sessions (
                    id              INT PRIMARY KEY AUTO_INCREMENT,
                    user_email      VARCHAR(255) UNIQUE NOT NULL,
                    device_id       VARCHAR(512) NOT NULL,
                    logged_in_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
                    last_heartbeat  DATETIME DEFAULT CURRENT_TIMESTAMP,
                    is_active       TINYINT DEFAULT 1
                )
            """);
        }
        System.out.println("✅ Sessions table ready");
    } catch (Exception e) {
        System.err.println("⚠️ Could not create sessions table: " + e.getMessage());
    }
}

    // =====================================================================
    //  ACQUIRE SESSION  — call this during caretaker login AFTER
    //  credentials are verified against local SQLite.
    // =====================================================================
    public static SessionResult acquireSession(String email) {

        // 1. Internet is mandatory for caretaker login
        if (!DatabaseConnection.isInternetAvailable()) {
            return SessionResult.NO_INTERNET;
        }

        // 2. Connect to MySQL
        Connection remote = DatabaseConnection.getMySQLConnection();
        if (remote == null) {
            return SessionResult.MYSQL_UNAVAILABLE;
        }

        String myDeviceId = DeviceIdManager.getDeviceId();

        try {
            // 3. Check if a session already exists for this email
            PreparedStatement ps = remote.prepareStatement(
                "SELECT device_id, last_heartbeat FROM sessions " +
                "WHERE user_email = ? AND is_active = 1"
            );
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String activeDevice  = rs.getString("device_id");
                Timestamp lastBeat   = rs.getTimestamp("last_heartbeat");
                rs.close(); ps.close();

                boolean isMyDevice = myDeviceId.equals(activeDevice);
                boolean isStale    = isStale(lastBeat);

                if (isMyDevice) {
                    // Same PC re-logging in — just refresh
                    refreshSession(remote, email, myDeviceId);

                } else if (isStale) {
                    // Other PC crashed or lost internet — safe to take over
                    System.out.println("⚠️ Stale session detected, taking over for: " + email);
                    takeOverSession(remote, email, myDeviceId);

                } else {
                    // Actively locked by another PC
                    return SessionResult.SESSION_ACTIVE_ELSEWHERE;
                }

            } else {
                rs.close(); ps.close();
                // No active session — create one
                createSession(remote, email, myDeviceId);
            }

            return SessionResult.SUCCESS;

        } catch (Exception e) {
            System.err.println("❌ Session acquire error: " + e.getMessage());
            e.printStackTrace();
            return SessionResult.ERROR;
        }
    }

    // =====================================================================
    //  RELEASE SESSION  — call on logout or app shutdown
    // =====================================================================
    public static void releaseSession(String email) {
        stopHeartbeat();
        activeEmail = null;

        try {
            Connection remote = DatabaseConnection.getMySQLConnection();
            if (remote == null) return;

            PreparedStatement ps = remote.prepareStatement(
                "UPDATE sessions SET is_active = 0 WHERE user_email = ? AND device_id = ?"
            );
            ps.setString(1, email);
            ps.setString(2, DeviceIdManager.getDeviceId());
            ps.executeUpdate();
            ps.close();
            System.out.println("✅ Session released for: " + email);

        } catch (Exception e) {
            System.err.println("⚠️ Could not release session: " + e.getMessage());
        }
    }

    // =====================================================================
    //  HEARTBEAT  — keeps session alive every 60 s
    // =====================================================================
    public static void startHeartbeat(String email) {
        activeEmail = email;
        stopHeartbeat(); // kill any old thread first

        heartbeatThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(60_000); // every 60 seconds

                    Connection remote = DatabaseConnection.getMySQLConnection();
                    if (remote != null) {
                        PreparedStatement ps = remote.prepareStatement(
                            "UPDATE sessions SET last_heartbeat = NOW() " +
                            "WHERE user_email = ? AND device_id = ? AND is_active = 1"
                        );
                        ps.setString(1, email);
                        ps.setString(2, DeviceIdManager.getDeviceId());
                        int rows = ps.executeUpdate();
                        ps.close();

                        if (rows > 0) {
                            System.out.println("💓 Heartbeat sent for: " + email);
                        } else {
                            // Session was taken over by someone else — warn but don't crash
                            System.out.println("⚠️ Heartbeat: session lost for: " + email);
                        }
                    }
                    // If no internet — silently skip; stale threshold gives 2 min grace

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    System.err.println("⚠️ Heartbeat error: " + e.getMessage());
                }
            }
        }, "session-heartbeat");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
        System.out.println("💓 Heartbeat started for: " + email);
    }

    public static void stopHeartbeat() {
        if (heartbeatThread != null && heartbeatThread.isAlive()) {
            heartbeatThread.interrupt();
            heartbeatThread = null;
        }
    }

    // =====================================================================
    //  PRIVATE HELPERS
    // =====================================================================
    private static boolean isStale(Timestamp lastBeat) {
        if (lastBeat == null) return true;
        return (System.currentTimeMillis() - lastBeat.getTime()) > STALE_THRESHOLD_MS;
    }

    private static void createSession(Connection remote, String email, String deviceId)
            throws Exception {
        PreparedStatement ps = remote.prepareStatement(
            "INSERT INTO sessions (user_email, device_id, logged_in_at, last_heartbeat, is_active) " +
            "VALUES (?, ?, NOW(), NOW(), 1) " +
            "ON DUPLICATE KEY UPDATE " +
            "device_id=VALUES(device_id), logged_in_at=NOW(), last_heartbeat=NOW(), is_active=1"
        );
        ps.setString(1, email);
        ps.setString(2, deviceId);
        ps.executeUpdate();
        ps.close();
        System.out.println("✅ Session created for: " + email);
    }

    private static void refreshSession(Connection remote, String email, String deviceId)
            throws Exception {
        PreparedStatement ps = remote.prepareStatement(
            "UPDATE sessions SET last_heartbeat = NOW(), is_active = 1 " +
            "WHERE user_email = ? AND device_id = ?"
        );
        ps.setString(1, email);
        ps.setString(2, deviceId);
        ps.executeUpdate();
        ps.close();
        System.out.println("✅ Session refreshed for: " + email);
    }

    private static void takeOverSession(Connection remote, String email, String deviceId)
            throws Exception {
        PreparedStatement ps = remote.prepareStatement(
            "UPDATE sessions SET device_id = ?, logged_in_at = NOW(), " +
            "last_heartbeat = NOW(), is_active = 1 " +
            "WHERE user_email = ?"
        );
        ps.setString(1, deviceId);
        ps.setString(2, email);
        ps.executeUpdate();
        ps.close();
        System.out.println("✅ Session taken over for: " + email);
    }
}
