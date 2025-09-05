package org.lastwar_game.lastwargame.listeners;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.lastwar_game.lastwargame.managers.GameManager;
import org.lastwar_game.lastwargame.GameWorlds;

import java.util.Arrays;
import java.util.List;

public class PlayerQuitListener implements Listener {

    private static final String LOBBY_WORLD = "world";


   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent event) {
       Player player = event.getPlayer();
       World playerWorld = player.getWorld();
        GameWorlds gw = GameWorlds.;
       // ✅ Проверяем, был ли игрок в игровом мире
       if (gw.contains(playerWorld.getName()) || !playerWorld.getName().equals(LOBBY_WORLD)) {
           Bukkit.getLogger().info("[LastWar] " + player.getName() + " left from " + playerWorld.getName() + ". Returning to lobby on next join.");
           GameManager.getInstance().checkGameStart(player.getWorld().getName());
       }
   }
}
