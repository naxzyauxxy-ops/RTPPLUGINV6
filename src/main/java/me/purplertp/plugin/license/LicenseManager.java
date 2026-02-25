package me.purplertp.plugin.license;

import me.purplertp.plugin.PurpleRTP;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class LicenseManager {

    private final PurpleRTP plugin;
    private boolean valid = false;

    public LicenseManager(PurpleRTP plugin) {
        this.plugin = plugin;
    }

    public boolean isValid() {
        return valid;
    }

    public boolean validate() {
        String key = plugin.getConfig().getString("LICENSE-KEY", "");
        String serverPort = String.valueOf(plugin.getServer().getPort());

        if (key.isEmpty()) {
            printInvalidBanner("NO LICENSE KEY SET! Add LICENSE-KEY to config.yml");
            return false;
        }

        try {
            // TODO: Replace with your actual license server URL
            URL url = new URL("https://your-license-server.com/api/validate");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setDoOutput(true);

            String body = "{\"key\":\"" + key + "\",\"port\":\"" + serverPort + "\"}";
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();
                valid = response.toString().contains("\"valid\":true");
            }

            conn.disconnect();
        } catch (Exception e) {
            plugin.getLogger().warning("[PurpleRTP] License server unreachable: " + e.getMessage());
            valid = false;
        }

        return valid;
    }

    private void printInvalidBanner(String reason) {
        plugin.getLogger().severe("╔══════════════════════════════════════╗");
        plugin.getLogger().severe("║        PURPLERTP - INVALID LICENSE   ║");
        plugin.getLogger().severe("║  " + reason);
        plugin.getLogger().severe("║  Purchase at: your-store-url.com     ║");
        plugin.getLogger().severe("╚══════════════════════════════════════╝");
    }
}
