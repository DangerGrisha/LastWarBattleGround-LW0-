package org.lastwar_game.lastwargame.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.lastwar_game.lastwargame.GameWorlds;
import org.lastwar_game.lastwargame.LastWarPlugin;
import org.lastwar_game.lastwargame.listeners.player_teleportation.PlayerWorldChangeListener;
import org.lastwar_game.lastwargame.listeners.PlayerQuitListener;

import static org.lastwar_game.lastwargame.managers.LobbyItems.*;

public class PlayerJoinListener implements Listener {

    private final LastWarPlugin plugin;

    // Лобби
    private static final String   LOBBY_WORLD = "world";
    private static final Location LOBBY_LOC   = new Location(Bukkit.getWorld(LOBBY_WORLD),
            118.5, 68.01, -183.5, 90f, 0f);

    public PlayerJoinListener(LastWarPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        World joinWorld = p.getWorld();
        String wname = (joinWorld != null) ? joinWorld.getName() : LOBBY_WORLD;

        // Если на прошлом выходе игрок был в game-мире — переносим в лобби при входе
        if (PlayerQuitListener.consumeForceToLobby(p.getUniqueId())) {
            handleLobbyJoin(p);
            return;
        }

        // Обычный вход: в лобби — оформляем лобби; в игровой мир — ничего не трогаем
        if (LOBBY_WORLD.equalsIgnoreCase(wname)) {
            handleLobbyJoin(p);
        } else if (GameWorlds.WORLD_NAMES.contains(wname)) {
            // Ничего не делаем — игрок продолжает игру там, где остановился
        } else {
            // Непонятный мир — отправим в лобби
            handleLobbyJoin(p);
        }
    }

    /* ===================== ЛОББИ ===================== */

    private void handleLobbyJoin(Player p) {
        World lobby = Bukkit.getWorld(LOBBY_WORLD);
        if (lobby == null) {
            p.kickPlayer("§cLobby world not found!");
            return;
        }

        // Снять эффекты + короткая защита на время тп
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 60, 255, false, false, false));

        // Удаляем только цветовые команды CLR_
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team t : board.getTeams()) {
            if (t.getName().startsWith("CLR_")) t.removeEntry(p.getName());
        }

        // Тп без задержки
        Location target = LOBBY_LOC.clone();
        if (target.getWorld() == null) target.setWorld(lobby);
        p.teleport(target);

        // ПОЛНАЯ очистка инвентаря и экипировки
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.getInventory().setItemInOffHand(null);

        // Лобби-предметы
        giveCompass(p, "§eSelect Game");
        givePaper(p, "§bJoin Available Game");
        giveRedConcrete(p, "§cReturn to HUB");
        p.updateInventory();

        p.sendMessage("§aWelcome to the lobby! Use the compass to select a game.");

        // Снять защиту через секунду
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType())), 20L);
    }
}
