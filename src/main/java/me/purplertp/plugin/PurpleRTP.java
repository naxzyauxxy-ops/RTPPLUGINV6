package me.purplertp.plugin;

import me.purplertp.plugin.commands.RTPAdminCommand;
import me.purplertp.plugin.commands.RTPCommand;
import me.purplertp.plugin.gui.RTPMenuListener;
import me.purplertp.plugin.license.LicenseManager;
import me.purplertp.plugin.managers.CooldownManager;
import me.purplertp.plugin.managers.RTPManager;
import org.bukkit.plugin.java.JavaPlugin;

public class PurpleRTP extends JavaPlugin {

    private static PurpleRTP instance;
    private CooldownManager cooldownManager;
    private RTPManager rtpManager;
    private LicenseManager licenseManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        licenseManager = new LicenseManager(this);

        // Validate license async so it doesn't block server startup
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            boolean valid = licenseManager.validate();

            getServer().getScheduler().runTask(this, () -> {
                if (!valid) {
                    getLogger().severe("╔══════════════════════════════════════╗");
                    getLogger().severe("║   PURPLERTP DISABLED - INVALID KEY   ║");
                    getLogger().severe("║   Purchase at: your-store-url.com    ║");
                    getLogger().severe("╚══════════════════════════════════════╝");
                    getServer().getPluginManager().disablePlugin(this);
                    return;
                }

                // License valid — boot the plugin
                this.cooldownManager = new CooldownManager(this);
                this.rtpManager = new RTPManager(this);

                getCommand("rtp").setExecutor(new RTPCommand(this));
                getCommand("rtpadmin").setExecutor(new RTPAdminCommand(this));
                getServer().getPluginManager().registerEvents(new RTPMenuListener(this), this);

                getLogger().info("╔══════════════════════════════════════╗");
                getLogger().info("║     PurpleRTP v1.0.0 - Licensed      ║");
                getLogger().info("╚══════════════════════════════════════╝");
            });
        });
    }

    @Override
    public void onDisable() {
        if (cooldownManager != null) cooldownManager.saveCooldowns();
        getLogger().info("PurpleRTP disabled.");
    }

    public static PurpleRTP getInstance() { return instance; }
    public CooldownManager getCooldownManager() { return cooldownManager; }
    public RTPManager getRtpManager() { return rtpManager; }
    public LicenseManager getLicenseManager() { return licenseManager; }
}
