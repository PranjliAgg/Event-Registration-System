package com.eventregistration.util;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigLoader {

    private final JSONObject config;

    public ConfigLoader() throws IOException {
        this.config = loadConfig();
    }

    public String getString(String key) {
        return config.getString(key);
    }

    public JSONObject getConfig() {
        return config;
    }

    private JSONObject loadConfig() throws IOException {
        String envId = getEnv("RAZORPAY_KEY_ID");
        String envSecret = getEnv("RAZORPAY_KEY_SECRET");
        if (envId != null && envSecret != null) {
            return new JSONObject()
                    .put("razorpay_key_id", envId)
                    .put("razorpay_key_secret", envSecret);
        }

        Path[] candidates = new Path[] {
                Paths.get("config.json"),
                Paths.get(System.getProperty("user.dir"), "config.json"),
                Paths.get(System.getProperty("user.dir"), ".vscode", "config.json"),
                Paths.get(System.getProperty("user.home"), ".eventregistration", "config.json")
        };

        for (Path path : candidates) {
            if (Files.exists(path)) {
                return new JSONObject(new String(Files.readAllBytes(path)));
            }
        }

        JSONObject classpathConfig = loadFromClasspath("/config.json");
        if (classpathConfig != null) {
            return classpathConfig;
        }

        throw new IOException("Missing Razorpay configuration. Create a config.json with keys 'razorpay_key_id' and 'razorpay_key_secret', or set environment variables RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET.");
    }

    private JSONObject loadFromClasspath(String path) throws IOException {
        try (InputStream in = ConfigLoader.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return new JSONObject(new String(in.readAllBytes()));
        }
    }

    private String getEnv(String key) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? null : value;
    }
}
