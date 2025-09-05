package org.lastwar_game.lastwargame.GUI;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.lastwar_game.lastwargame.managers.GameManager;
import org.lastwar_game.lastwargame.teams.TeamKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class TeamSelectorGUI {

    public static final String GUI_TITLE = "§eSelect Your Team";

    public static void open(Player player) {
        // 54 slots so we can place 16 teams nicely
        Inventory gui = Bukkit.createInventory(null, 54, GUI_TITLE);

        // Fill background with glass panes
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gm = glass.getItemMeta();
        if (gm != null) { gm.setDisplayName(" "); glass.setItemMeta(gm); }
        for (int i = 0; i < 54; i++) gui.setItem(i, glass);

        // Nice grid positions for 16 items (4 rows x 4 columns)
        int[] baseSlots = {
                10, 12, 14, 16,
                19, 21, 23, 25,
                28, 30, 32, 34,
                37, 39, 41, 43
        };

        TeamKey[] keys = TeamKey.values();
        for (int i = 0; i < keys.length; i++) {
            TeamKey key = keys[i];
            gui.setItem(baseSlots[i], buildTeamIcon(player, key));
        }

        player.openInventory(gui);
    }

    // Build item for a single team with live roster in lore
    private static ItemStack buildTeamIcon(Player viewer, TeamKey key) {
        ItemStack item = new ItemStack(key.wool);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(key.chat + "Join " + key.id + " Team");
            List<String> lore = new ArrayList<>();
            lore.add("§7Members:");

            // Ask GameManager for current roster of this team
            List<String> names = GameManager.getInstance().getRosterForTeam(key);
            if (names.isEmpty()) {
                lore.add(" §8— empty —");
            } else {
                // limit to avoid super-long lores
                int cap = Math.min(12, names.size());
                for (int i = 0; i < cap; i++) {
                    lore.add(" §f• " + names.get(i));
                }
                if (names.size() > cap) lore.add(" §7... +" + (names.size() - cap) + " more");
            }

            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }
}
