package org.lastwar_game.lastwargame.teams;

import org.bukkit.ChatColor;
import org.bukkit.Material;

public enum TeamKey {
    WHITE("WHITE", ChatColor.WHITE, Material.WHITE_WOOL),
    ORANGE("ORANGE", ChatColor.GOLD, Material.ORANGE_WOOL),
    MAGENTA("MAGENTA", ChatColor.LIGHT_PURPLE, Material.MAGENTA_WOOL),
    LIGHT_BLUE("LIGHT_BLUE", ChatColor.AQUA, Material.LIGHT_BLUE_WOOL),
    YELLOW("YELLOW", ChatColor.YELLOW, Material.YELLOW_WOOL),
    LIME("LIME", ChatColor.GREEN, Material.LIME_WOOL),
    PINK("PINK", ChatColor.LIGHT_PURPLE, Material.PINK_WOOL),
    GRAY("GRAY", ChatColor.DARK_GRAY, Material.GRAY_WOOL),
    LIGHT_GRAY("LIGHT_GRAY", ChatColor.GRAY, Material.LIGHT_GRAY_WOOL),
    CYAN("CYAN", ChatColor.DARK_AQUA, Material.CYAN_WOOL),
    PURPLE("PURPLE", ChatColor.DARK_PURPLE, Material.PURPLE_WOOL),
    BLUE("BLUE", ChatColor.BLUE, Material.BLUE_WOOL),
    BROWN("BROWN", ChatColor.DARK_RED, Material.BROWN_WOOL), // closest readable tint
    GREEN("GREEN", ChatColor.DARK_GREEN, Material.GREEN_WOOL),
    RED("RED", ChatColor.RED, Material.RED_WOOL),
    BLACK("BLACK", ChatColor.BLACK, Material.BLACK_WOOL);

    public final String id;         // Scoreboard team id
    public final ChatColor chat;    // Chat color
    public final Material wool;     // Wool material

    TeamKey(String id, ChatColor chat, Material wool) {
        this.id = id;
        this.chat = chat;
        this.wool = wool;
    }

    public static TeamKey fromWool(Material m) {
        for (TeamKey t : values()) if (t.wool == m) return t;
        return null;
    }
}
