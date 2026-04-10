package com.smvdu.mess.utils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.UUID;

/**
 * Generates a unique ID for this PC on first run, then reuses it forever.
 * Stored at: ~/SMVDU-Mess/device.id
 */
public class DeviceIdManager {

    private static final String DEVICE_ID_PATH =
            System.getProperty("user.home") + "/SMVDU-Mess/device.id";

    private static String cachedDeviceId = null;

    public static String getDeviceId() {
        if (cachedDeviceId != null) return cachedDeviceId;

        File file = new File(DEVICE_ID_PATH);

        // If already generated before, just read it
        if (file.exists()) {
            try (FileReader fr = new FileReader(file)) {
                char[] buf = new char[64];
                int len = fr.read(buf);
                cachedDeviceId = new String(buf, 0, len).trim();
                return cachedDeviceId;
            } catch (Exception e) {
                System.err.println("⚠️ Could not read device ID, regenerating");
            }
        }

        // First time — generate and save
        try {
            String hostname = "unknown";
            try { hostname = java.net.InetAddress.getLocalHost().getHostName(); }
            catch (Exception ignored) {}

            cachedDeviceId = hostname + "-" + UUID.randomUUID();

            new File(System.getProperty("user.home") + "/SMVDU-Mess").mkdirs();
            try (FileWriter fw = new FileWriter(file)) {
                fw.write(cachedDeviceId);
            }
            System.out.println("✅ Device ID created: " + cachedDeviceId);
        } catch (Exception e) {
            // Fallback — not persisted but works for this session
            cachedDeviceId = "fallback-" + UUID.randomUUID();
            System.err.println("⚠️ Device ID not persisted: " + e.getMessage());
        }

        return cachedDeviceId;
    }
}
