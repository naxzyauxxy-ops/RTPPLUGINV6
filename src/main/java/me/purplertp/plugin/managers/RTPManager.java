package me.purplertp.plugin.managers;

import me.purplertp.plugin.PurpleRTP;
import me.purplertp.plugin.utils.MessageUtils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class RTPManager {

    private final PurpleRTP plugin;
    private final Set<UUID> inRtp = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public RTPManager(PurpleRTP plugin) {
        this.plugin = plugin;
    }

    public int getPlayersInRtp() {
        return inRtp.size();
    }

    public boolean isInRtp(UUID uuid) {
        return inRtp.contains(uuid);
    }

    public void cancelRtp(UUID uuid) {
        inRtp.remove(uuid);
    }

    private void actionbar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(MessageUtils.format(message)));
    }

    public void randomTeleport(Player player, String worldName) {
        if (!plugin.getConfig().getBoolean("ENABLED", true)) {
            actionbar(player, plugin.getConfig().getString("MESSAGES.DISABLED", "&cRTP is disabled."));
            return;
        }

        int maxPlayers = plugin.getConfig().getInt("SETTINGS.PLAYERS-IN-RTP", 150);
        if (inRtp.size() >= maxPlayers) {
            actionbar(player, plugin.getConfig().getString("MESSAGES.MAX-PLAYERS", "&cToo many players using RTP."));
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            actionbar(player, plugin.getConfig().getString("MESSAGES.WORLD-NOT-EXIST", "&cWorld not found."));
            return;
        }

        if (!player.hasPermission("purplertp.bypass.cooldown") &&
                plugin.getCooldownManager().isOnCooldown(player.getUniqueId(), worldName)) {
            long remaining = plugin.getCooldownManager().getRemainingCooldown(player.getUniqueId(), worldName);
            String msg = plugin.getConfig().getString("MESSAGES.COOLDOWN", "&cWait {remaining}s.")
                    .replace("{remaining}", String.valueOf(remaining));
            actionbar(player, msg);
            return;
        }

        String path = "WORLD-SETTINGS." + worldName + ".";
        int maxRadius   = plugin.getConfig().getInt(path + "MAX-RADIUS", 10000);
        int minRadius   = plugin.getConfig().getInt(path + "MIN-RADIUS", 1000);
        int centerX     = plugin.getConfig().getInt(path + "CENTER-X", 0);
        int centerZ     = plugin.getConfig().getInt(path + "CENTER-Z", 0);
        int cooldown    = plugin.getConfig().getInt(path + "COOLDOWN", 0);
        int maxAttempts = plugin.getConfig().getInt("SETTINGS.MAX-ATTEMPTS", 25);
        int countdown   = plugin.getConfig().getInt("SETTINGS.COUNTDOWN", 5);

        inRtp.add(player.getUniqueId());

        // Searching ticker
        BukkitRunnable searchingTicker = new BukkitRunnable() {
            int dots = 0;
            @Override
            public void run() {
                if (!player.isOnline() || !inRtp.contains(player.getUniqueId())) { cancel(); return; }
                actionbar(player, "&bSearching" + ".".repeat(dots % 4));
                dots++;
            }
        };
        searchingTicker.runTaskTimer(plugin, 0L, 5L);

        // Snapshot start position for move detection
        final double startX = player.getLocation().getX();
        final double startZ = player.getLocation().getZ();
        Location startLoc = player.getLocation().clone();

        new BukkitRunnable() {
            @Override
            public void run() {
                Location safeLoc = findSafeLocation(world, centerX, centerZ, minRadius, maxRadius, maxAttempts);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        searchingTicker.cancel();

                        if (!player.isOnline()) {
                            inRtp.remove(player.getUniqueId());
                            return;
                        }

                        if (safeLoc == null) {
                            inRtp.remove(player.getUniqueId());
                            actionbar(player, plugin.getConfig().getString("MESSAGES.MAX-ATTEMPTS", "&cNo safe location found.")
                                    .replace("{attempts}", String.valueOf(maxAttempts)));
                            return;
                        }

                        // Countdown — cancels if player moves
                        new BukkitRunnable() {
                            int secondsLeft = countdown;

                            @Override
                            public void run() {
                                if (!player.isOnline()) {
                                    inRtp.remove(player.getUniqueId());
                                    cancel();
                                    return;
                                }

                                // Check if player moved (X or Z changed by more than 0.15)
                                double dx = Math.abs(player.getLocation().getX() - startX);
                                double dz = Math.abs(player.getLocation().getZ() - startZ);
                                if (dx > 0.15 || dz > 0.15) {
                                    cancel();
                                    inRtp.remove(player.getUniqueId());
                                    actionbar(player, "&cTeleport cancelled &7— &cdon't move!");
                                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.2f);
                                    return;
                                }

                                if (secondsLeft > 0) {
                                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.8f);
                                    actionbar(player, "&fTeleporting in &b" + secondsLeft + "s");
                                    secondsLeft--;
                                } else {
                                    cancel();
                                    inRtp.remove(player.getUniqueId());

                                    // Depart
                                    player.playSound(startLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.6f);
                                    player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, startLoc.clone().add(0, 1, 0), 40, 0.3, 0.8, 0.3, 0.05);

                                    player.teleport(safeLoc);

                                    // Arrival
                                    safeLoc.getWorld().spawnParticle(Particle.REVERSE_PORTAL, safeLoc.clone().add(0, 1, 0), 60, 0.4, 1, 0.4, 0.04);
                                    player.playSound(safeLoc, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 2.0f);

                                    actionbar(player, "");
                                    plugin.getCooldownManager().setCooldown(player.getUniqueId(), worldName, cooldown);
                                }
                            }
                        }.runTaskTimer(plugin, 0L, 20L);
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }

    private Location findSafeLocation(World world, int centerX, int centerZ,
                                       int minRadius, int maxRadius, int maxAttempts) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < maxAttempts; i++) {
            int x = centerX + (random.nextInt(minRadius, maxRadius + 1) * (random.nextBoolean() ? 1 : -1));
            int z = centerZ + (random.nextInt(minRadius, maxRadius + 1) * (random.nextBoolean() ? 1 : -1));

            Chunk chunk = world.getChunkAt(x >> 4, z >> 4);
            if (!chunk.isLoaded()) chunk.load();

            int y = world.getHighestBlockYAt(x, z);
            Location loc = new Location(world, x + 0.5, y + 1, z + 0.5);
            loc.setYaw(random.nextFloat() * 360);

            if (isSafe(loc)) return loc;
        }
        return null;
    }

    private boolean isSafe(Location loc) {
        Block feet   = loc.getBlock();
        Block head   = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);

        if (!feet.getType().isAir() || !head.getType().isAir()) return false;
        if (!ground.getType().isSolid()) return false;

        Material g = ground.getType();
        if (g == Material.WATER || g == Material.LAVA ||
            g == Material.FIRE  || g == Material.CACTUS) return false;

        if (loc.getY() <= loc.getWorld().getMinHeight()) return false;

        return true;
    }
}
