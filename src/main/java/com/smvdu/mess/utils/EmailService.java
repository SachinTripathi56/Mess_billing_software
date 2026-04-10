package com.smvdu.mess.utils;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;

public class EmailService {

    // 🔥 Replace with your REAL Brevo API key (must start with xkeysib-)
 //private static final String API_KEY = ""; // enter your api url


    // ✅ Your VERIFIED sender email
    private static final String SENDER_EMAIL = "heapifyglobal@gmail.com"; // enter your sender email.

    /**
     * Send OTP email using Brevo API (HTTP)
     */
    public static boolean sendOTPEmail(String recipientEmail, String otp) {
        try {
            var url = new URI("https://api.brevo.com/v3/smtp/email").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("accept", "application/json");
            conn.setRequestProperty("api-key", API_KEY);
            conn.setRequestProperty("content-type", "application/json");
            conn.setDoOutput(true);

            // ✅ CLEAN JSON (NO formatting issues)
            String json = "{"
                    + "\"sender\":{"
                    + "\"name\":\"SMVDU Mess\","
                    + "\"email\":\"" + SENDER_EMAIL + "\""
                    + "},"
                    + "\"to\":[{"
                    + "\"email\":\"" + recipientEmail + "\""
                    + "}],"
                    + "\"subject\":\"Password Reset OTP\","
                    + "\"htmlContent\":\"<h2>SMVDU Mess Billing</h2>"
                    + "<p>Your OTP is:</p>"
                    + "<h1 style='color:#2196F3;'>" + otp + "</h1>"
                    + "<p>This OTP is valid for 5 minutes.</p>\""
                    + "}";

            // Send request
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = json.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();

            if (responseCode == 201) {
                System.out.println("✓ OTP email sent via Brevo to: " + recipientEmail);
                return true;
            } else {
                // 🔥 PRINT EXACT ERROR
                java.io.InputStream errorStream = conn.getErrorStream();
                if (errorStream != null) {
                    String error = new String(errorStream.readAllBytes());
                    System.err.println("✗ Brevo error: " + error);
                } else {
                    System.err.println("✗ Brevo failed. Code: " + responseCode);
                }
                return false;
            }

        } catch (Exception e) {
            System.err.println("✗ Exception sending email: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Generate 6-digit OTP
     */
    public static String generateOTP() {
        int otp = 100000 + (int)(Math.random() * 900000);
        return String.valueOf(otp);
    }
}