package org.lastwar_game.lastwargame.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.entity.Player;
import org.lastwar_game.lastwargame.GameWorlds;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.lastwar_game.lastwargame.managers.GameManager;
import org.lastwar_game.lastwargame.managers.LobbyManager;

import java.util.Arrays;
import java.util.List;

import static org.lastwar_game.lastwargame.managers.LobbyItems.*;

public class PlayerWorldChangeListener implements Listener {


    public static Location getRandomLocationAround(Location center, int radius) {
        double angle = Math.random() * 2 * Math.PI; // Формула нахождения радиуса
        double distance = Math.random() * radius;

        double x = center.getX() + distance * Math.cos(angle); // Формула нахождения блоков по радиусу
        double z = center.getZ() + distance * Math.sin(angle); // Формула нахождения блоков по радиусу
        double y = center.getWorld().getHighestBlockYAt((int) x, (int) z) + 1; // +1 чтобы над землёй

        return new Location(center.getWorld(), x, y, z);
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        String fromWorld = event.getFrom().getWorld().getName();
        String toWorld = event.getTo().getWorld().getName();

        World targetWorld = player.getWorld();
        Location center = new Location(targetWorld, -164.5, 184, 297.5);
        Location randomSpawn = getRandomLocationAround(center, 3);

        // ✅ Если игрок переходит из лобби в игровой мир
        if (!GameWorlds.WORLD_NAMES.contains(fromWorld) && GameWorlds.WORLD_NAMES.contains(toWorld)) {
            Bukkit.getScheduler().runTaskLater(org.lastwar_game.lastwargame.LastWarPlugin.getInstance(), () -> {

                player.teleport(randomSpawn);

                player.getInventory().remove(Material.COMPASS);
                player.getInventory().remove(Material.PAPER);
                player.getInventory().remove(Material.RED_CONCRETE);

                player.updateInventory();

                int playerCount = player.getWorld().getPlayers().size();
                int neededToStart = 4 - playerCount;
                int slotsLeft = 10 - playerCount;

                for (Player p : player.getWorld().getPlayers()) {
                    p.sendMessage("§e" + player.getName() + " joined! Players: §e" + playerCount + "/10");
                    if (neededToStart > 0) {
                        p.sendMessage("§cNeed " + neededToStart + " more players to start!");
                    } else {
                        p.sendMessage("§aGame countdown will start soon!");
                    }
                    p.sendMessage("§7" + slotsLeft + " slots left.");
                }

                // ✅ Проверяем, можно ли запустить игру
                GameManager.getInstance().checkGameStart(toWorld);
            }, 40L); // ✅ Выполняем через 30 тиков, чтобы учесть нового игрока
        }

        // ✅ Если игрок перемещается внутри игрового мира (не сбрасываем инвентарь)
        if (GameWorlds.WORLD_NAMES.contains(fromWorld) && GameWorlds.WORLD_NAMES.contains(toWorld)) {
            return;
        }

        // ✅ Если игрок перемещается в лобби – очищаем инвентарь и выдаём компас
        if (!GameWorlds.WORLD_NAMES.contains(toWorld)) {
            player.getInventory().clear();
            giveCompass(player, "§eSelect Game");
            givePaper(player, "§bJoin Available Game");
            giveRedConcrete(player, "§cReturn to HUB");
            player.updateInventory();
        }
    }

}
