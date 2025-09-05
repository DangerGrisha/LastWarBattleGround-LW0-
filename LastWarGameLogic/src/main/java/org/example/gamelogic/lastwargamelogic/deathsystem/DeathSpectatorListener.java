package org.example.gamelogic.lastwargamelogic.deathsystem;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class DeathSpectatorListener implements Listener {

    private final JavaPlugin plugin;

    // Прямоугольник рандомной точки (твоя зона fill):
    // fill -259 34 110 -74 34 479  =>  X: -259..-74,  Z: 110..479  (Y берём highestBlockYAt+1)
    private static final int RX1 = -259, RZ1 = 110;
    private static final int RX2 = -74,  RZ2 = 479;

    public DeathSpectatorListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        final Player player = event.getEntity();
        final Player killer = player.getKiller();

        // если не хочешь, чтобы вещи падали — раскомментируй:
        // event.getDrops().clear();
        // event.setDroppedExp(0);

        // Делаем "мгновенный спектатор" без экрана смерти:
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;

                // пропустить экран смерти (Spigot/Paper API)
                try {
                    player.spigot().respawn();
                } catch (Throwable ignored) {
                    // на чистом CraftBukkit spigot().respawn() может быть недоступен — тогда игрок нажмёт Respawn вручную
                }


                player.setGameMode(GameMode.SPECTATOR);

                // Куда телепортировать зрителя:
                Location dest;
                if (killer != null && killer.isOnline() && killer.getWorld().equals(player.getWorld())) {
                    dest = killer.getLocation().clone().add(0, 1.5, 0);
                } else {
                    dest = randomPointInRect(player.getWorld(), RX1, RZ1, RX2, RZ2);
                }

                // Поворот головы в сторону цели (опционально)
                player.teleport(dest);
            }
        }.runTask(plugin); // выполняем на следующем тике
    }

    /** Рандомная безопасная точка внутри прямоугольника по XZ; Y берётся по поверхности. */
    private Location randomPointInRect(World w, int x1, int z1, int x2, int z2) {
        int xmin = Math.min(x1, x2), xmax = Math.max(x1, x2);
        int zmin = Math.min(z1, z2), zmax = Math.max(z1, z2);

        double x = xmin + Math.random() * (xmax - xmin);
        double z = zmin + Math.random() * (zmax - zmin);

        int y = w.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 1;
        return new Location(w, x + 0.5, y, z + 0.5);
    }
}
