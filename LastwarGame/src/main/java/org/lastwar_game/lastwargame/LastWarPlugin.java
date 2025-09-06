package org.lastwar_game.lastwargame;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.lastwar_game.lastwargame.commands.EndGameCommand;
import org.lastwar_game.lastwargame.commands.StopCommand;
import org.lastwar_game.lastwargame.initializers.ScoreboardInitializer;
import org.lastwar_game.lastwargame.listeners.*;
import org.lastwar_game.lastwargame.listeners.GUI.*;
import org.lastwar_game.lastwargame.listeners.PlayerJoinListener;
import org.lastwar_game.lastwargame.listeners.player_teleportation.PlayerWorldChangeListener;
import org.lastwar_game.lastwargame.managers.GameManager;
import org.lastwar_game.lastwargame.managers.LobbyManager;
import org.lastwar_game.lastwargame.managers.ServerSelectionAutoRefreshTask;

public class LastWarPlugin extends JavaPlugin {

    private static LastWarPlugin instance;
    private GameManager gameManager;
    private LobbyManager lobbyManager;

    @Override
    public void onEnable() {
        instance = this;
        this.gameManager = new GameManager();
        this.lobbyManager = new LobbyManager(this);
        getCommand("endgame").setExecutor(new EndGameCommand());
        getCommand("stop").setExecutor(new StopCommand());
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");


        getServer().getPluginManager().registerEvents(new GUIListener(), this); // Обработчик кликов в GUI
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this); // Обработчик входов игроков
//        getServer().getPluginManager().registerEvents(new CompassListener(), this); // Упрощенный UniversalCompassListener
        getServer().getPluginManager().registerEvents(new UniversalCompassListener(), this); // Обработчик кликов компасом
        getServer().getPluginManager().registerEvents(new ServerSelectionListener(this), this); // Обработчик выбора сервера
        getServer().getPluginManager().registerEvents(new GUIClickListener(), this); // Запрещаем забирать предметы из GUI
        getServer().getPluginManager().registerEvents(new PlayerWorldChangeListener(), this);
        getServer().getPluginManager().registerEvents(new TeamSelectionClickListener(), this);
        getServer().getPluginManager().registerEvents(new TeamSelectorListener(), this);
        getServer().getPluginManager().registerEvents(new ClassSelectorListener(), this);
        getServer().getPluginManager().registerEvents(new ListenerPaperRedConcrete(), this);
        getServer().getPluginManager().registerEvents(new PlayerMovementFreezeListener(), this);


        ServerSelectionAutoRefreshTask.start(this);

        GameManager.getInstance().init(this);


        //взять все миры и создать скорборды для каждого из них
        ScoreboardInitializer.initializeAll();


        getLogger().info("[LastWar] Плагин запущен!");

        // Перезапуск зависших активных миров
        for (World world : Bukkit.getWorlds()) {
            if (!world.getName().startsWith("lastwarGame")) continue;

            Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            Objective objective = scoreboard.getObjective(world.getName());
            if (objective == null) continue;

            int isGameStarted = objective.getScore("isGameStarted").getScore();
            if (isGameStarted == 1) {
                getLogger().info("Restarting leftover active game in world: " + world.getName());
                GameManager.getInstance().endGame(world);
            }
        }


    }

    @Override
    public void onDisable() {
        getLogger().info("[LastWar] Плагин выключен!");
    }

    public static LastWarPlugin getInstance() {
        return instance;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public LobbyManager getLobbyManager() {
        return lobbyManager;
    }
}
