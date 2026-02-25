package me.purplertp.plugin.gui;

import me.purplertp.plugin.PurpleRTP;
import me.purplertp.plugin.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class RTPMenu {

    private final PurpleRTP plugin;

    // Black glass pane for border/filler (DonutSMP style)
    private static final ItemStack BLACK_PANE = buildStatic(Material.BLACK_STAINED_GLASS_PANE, " ");
    private static final ItemStack GRAY_PANE  = buildStatic(Material.GRAY_STAINED_GLASS_PANE,  " ");

    public RTPMenu(PurpleRTP plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        ConfigurationSection menuCfg = plugin.getConfig().getConfigurationSection("RTP-MENU");
        if (menuCfg == null) return;

        String title = MessageUtils.format(menuCfg.getString("TITLE", "&8Random Teleport"));
        int size = menuCfg.getInt("SIZE", 27);

        Inventory inv = Bukkit.createInventory(null, size, title);

        // DonutSMP style: black border, gray inner fill
        for (int i = 0; i < size; i++) {
            if (isBorder(i, size)) {
                inv.setItem(i, BLACK_PANE);
            } else {
                inv.setItem(i, GRAY_PANE);
            }
        }

        // Place buttons
        ConfigurationSection buttons = menuCfg.getConfigurationSection("BUTTONS");
        if (buttons != null) {
            for (String key : buttons.getKeys(false)) {
                ConfigurationSection btn = buttons.getConfigurationSection(key);
                if (btn == null) continue;

                String worldName  = btn.getString("WORLD", "world");
                int slot          = btn.getInt("SLOT", 0);
                String name       = MessageUtils.format(btn.getString("DISPLAY-NAME", key));
                Material material = parseMaterial(btn.getString("MATERIAL", "GRASS_BLOCK"));
                List<String> rawLore = btn.getStringList("LORE");

                int worldPlayers = Bukkit.getWorld(worldName) != null
                        ? Bukkit.getWorld(worldName).getPlayerCount() : 0;
                int ping = player.getPing();

                List<String> lore = new ArrayList<>();
                for (String line : rawLore) {
                    lore.add(MessageUtils.format(
                        line.replace("{players}", String.valueOf(worldPlayers))
                            .replace("{ping}", String.valueOf(ping))
                    ));
                }

                if (slot >= 0 && slot < size) {
                    inv.setItem(slot, buildItem(material, name, lore));
                }
            }
        }

        player.openInventory(inv);
    }

    // Returns true if slot is on the border of the inventory
    private boolean isBorder(int slot, int size) {
        int rows = size / 9;
        int row  = slot / 9;
        int col  = slot % 9;
        return row == 0 || row == rows - 1 || col == 0 || col == 8;
    }

    private static ItemStack buildStatic(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); item.setItemMeta(meta); }
        return item;
    }

    private ItemStack buildItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(name);
        if (lore != null) meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private Material parseMaterial(String name) {
        try { return Material.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return Material.GRASS_BLOCK; }
    }
}
