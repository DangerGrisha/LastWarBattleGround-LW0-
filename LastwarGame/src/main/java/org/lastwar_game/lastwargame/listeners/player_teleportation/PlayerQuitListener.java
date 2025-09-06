package org.lastwar_game.lastwargame.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.lastwar_game.lastwargame.GameWorlds;
import org.lastwar_game.lastwargame.managers.GameManager;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerQuitListener implements Listener {

    // Флаг «перенести в лобби при следующем входе»
    private static final Set<UUID> FORCE_TO_LOBBY_ON_JOIN = ConcurrentHashMap.newKeySet();

    public static void markForceToLobby(UUID id) { FORCE_TO_LOBBY_ON_JOIN.add(id); }
    public static boolean consumeForceToLobby(UUID id) { return FORCE_TO_LOBBY_ON_JOIN.remove(id); }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        String worldName = p.getWorld().getName();

        // Если игрок выходит из игрового мира — запомним, что при входе его нужно отправить в лобби
        if (GameWorlds.WORLD_NAMES.contains(worldName)) {
            markForceToLobby(p.getUniqueId());
            // Пересчёт очереди/старта теперь, когда игрок покинул мир
            GameManager.getInstance().checkGameStart(worldName);
        }
        // Если выходит из лобби — ничего особенного не делаем
    }
}
