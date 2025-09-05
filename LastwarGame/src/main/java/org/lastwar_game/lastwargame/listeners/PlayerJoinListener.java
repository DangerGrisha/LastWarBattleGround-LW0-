package org.lastwar_game.lastwargame.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.lastwar_game.lastwargame.LastWarPlugin;

import static org.lastwar_game.lastwargame.managers.LobbyItems.*;

public class PlayerJoinListener implements Listener {

    private final LastWarPlugin plugin;

    // Лобби-точка
    private static final String LOBBY_WORLD = "world";
    private static final Location LOBBY_LOC = new Location(Bukkit.getWorld(LOBBY_WORLD),
            118.5, 68.01, -183.5, 90f, 0f);

    // Точка «безопаски» для игровых миров lastwarGameX
    private static final double GAME_SAFE_X = -165.0;
    private static final double GAME_SAFE_Y = 184.0;
    private static final double GAME_SAFE_Z = 297.0;
    private static final float  GAME_SAFE_YAW = 0f;
    private static final float  GAME_SAFE_PITCH = 0f;

    public PlayerJoinListener(LastWarPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        World joinWorld = p.getWorld(); // мир, в котором игрок «проснулся» (последнее сохранение)
        String wname = (joinWorld != null) ? joinWorld.getName() : LOBBY_WORLD;

        // Небольшая задержка, чтобы мир/чанки точно подгрузились (2 секунды)
        new BukkitRunnable() {
            @Override public void run() {
                if (LOBBY_WORLD.equals(wname)) {
                    handleLobbyJoin(p);
                } else if (wname.startsWith("lastwarGame")) {
                    handleGameWorldJoin(p, joinWorld);
                } else {
                    // Фолбэк: отправим в лобби
                    handleLobbyJoin(p);
                }
            }
        }.runTaskLater(plugin, 20L); // 40 тиков = 2 секунды
    }


    /* ===================== HANDLERS ===================== */

    // Лобби-путь: чистим эффекты, снимаем старые CLR_-команды, выдаём предметы и тпшим в лобби
    private void handleLobbyJoin(Player p) {
        World lobby = Bukkit.getWorld(LOBBY_WORLD);
        if (lobby == null) {
            p.kickPlayer("§cLobby world not found!");
            return;
        }

        // Снимаем все эффекты и даём краткую неуязвимость на время телепорта
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 60, 255, false, false, false));

        // Удаляем только цветные команды CLR_ (не все команды вообще)
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team t : board.getTeams()) {
            if (t.getName().startsWith("CLR_")) t.removeEntry(p.getName());
        }

        // Телепорт
        Location target = LOBBY_LOC.clone();
        if (target.getWorld() == null) target.setWorld(lobby); // на случай, если сервер ещё не успел поставить мир в объект
        p.teleport(target);

        // Лобби-инвентарь
        p.getInventory().clear();
        giveCompass(p, "§eSelect Game");
        givePaper(p, "§bJoin Available Game");
        giveRedConcrete(p, "§cReturn to HUB");
        p.updateInventory();

        p.sendMessage("§aWelcome to the lobby! Use the compass to select a game.");
        // снимаем защиту через тик-другой
        new BukkitRunnable(){ @Override public void run() {
            p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        }}.runTaskLater(plugin, 20L);
    }

    // Игровые миры lastwarGameX: ничего не ломаем, просто переносим в безопасную точку этого мира
    private void handleGameWorldJoin(Player p, World w) {
        if (w == null) {
            // Если вдруг мир не успел подгрузиться — отправим в лобби
            handleLobbyJoin(p);
            return;
        }

        Location safe = new Location(w, GAME_SAFE_X + 0.5, GAME_SAFE_Y, GAME_SAFE_Z + 0.5, GAME_SAFE_YAW, GAME_SAFE_PITCH);
        p.teleport(safe);
        p.sendMessage("§7You have been moved to a safe point in §e" + w.getName() + "§7.");
        // Никаких очисток инвентаря/команд тут не делаем!
    }
}
