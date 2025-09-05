package org.lastwar_game.lastwargame.managers;

import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;
import org.example.gamelogic.lastwargamelogic.LastWarGameLogic;
import org.example.gamelogic.lastwargamelogic.privat.CoreSpawner;
import org.lastwar_game.lastwargame.GUI.ClassSelectionGUI;
import org.lastwar_game.lastwargame.GUI.ServerSelectionGUI;
import org.lastwar_game.lastwargame.LastWarPlugin;
import org.lastwar_game.lastwargame.teams.TeamKey;

import java.util.*;
import java.util.stream.Collectors;

public class GameManager {

    /* ===================== SINGLETON ===================== */

    private static GameManager instance;
    public static GameManager getInstance() {
        if (instance == null) instance = new GameManager();
        return instance;
    }

    private JavaPlugin plugin;
    public void init(JavaPlugin plugin) { this.plugin = plugin; }

    /* ===================== CONSTANTS / CONFIG ===================== */

    // Время матча/бордера
    public static final int GAME_TOTAL_SECONDS     = 20 * 60; // 20 минут
    public static final int BORDER_SHRINK_LAST_SEC = 5 * 60;  // последние 5 минут — сужение

    // Прямоугольник арены (по твоим координатам)
    private static final int SPAWN_X1 = -259, SPAWN_Z1 = 110;
    private static final int SPAWN_X2 =  -74, SPAWN_Z2 = 479;

    // Размер бордера
    public static final int BORDER_START_MARGIN = 48;  // запас
    public static final int BORDER_END_SIZE     = 28;  // финальный диаметр

    // Центр арены/бордера
    private static final double ARENA_CENTER_X = (SPAWN_X1 + SPAWN_X2) / 2.0;
    private static final double ARENA_CENTER_Z = (SPAWN_Z1 + SPAWN_Z2) / 2.0;
    private static final int ARENA_WIDTH       = Math.abs(SPAWN_X1 - SPAWN_X2) + 1;
    private static final int ARENA_HEIGHT      = Math.abs(SPAWN_Z1 - SPAWN_Z2) + 1;
    private static final int BORDER_START_SIZE = Math.max(ARENA_WIDTH, ARENA_HEIGHT) + BORDER_START_MARGIN;

    // Разлёт между цветовыми командами при спавне
    private static final double MIN_TEAM_SPACING = 18.0;

    // Доступные классы (имена тегов == имена классов)
    private final List<String> classOptions = Arrays.asList(
            "LadyNagant", "Saske", "Hutao", "Ganyu", "Dio", "Naruto", "BurgerMaster", "Uraraka"
    );

    // Единый пул классовых тегов (если будут алиасы — добавь сюда)
    public static final Set<String> ALL_CLASS_TAGS = new HashSet<>(Arrays.asList(
            "LadyNagant", "Saske", "Hutao", "Ganyu", "Dio", "Naruto", "BurgerMaster", "Uraraka"
    ));

    /* ===================== STATE ===================== */

    // Игрок → цветная команда (16 цветов из TeamKey)
    private final Map<UUID, TeamKey> playerColorTeams = new HashMap<>();

    // Игрок → SIDE ("RED"/"BLUE") — для счёта/GUI
    private final Map<UUID, String> playerSides = new HashMap<>();

    // Игрок → выбранный класс (доп. кэш, источник истины — теги)
    private final Map<UUID, String> playerClasses = new HashMap<>();
    private final Map<String, UUID> takenClasses  = new HashMap<>(); // не используется в free-режиме

    // Флаги и таймеры
    private final Map<String, BukkitRunnable> gameTimers              = new HashMap<>();
    private final Map<String, BossBar>       bossBars                = new HashMap<>();
    private final Map<UUID, Location>        frozenPlayers           = new HashMap<>();
    private final Set<UUID>                  lockedTeams             = new HashSet<>();
    private final Set<String>                restartingWorlds        = new HashSet<>();
    private final Set<String>                classSelectionActive    = new HashSet<>();
    private final Set<String>                endingWorlds            = new HashSet<>();

    // Сгенерированные точки спавна по цвет-командам
    private final Map<String, Map<TeamKey, Location>> worldTeamSpawns = new HashMap<>();

    private final Random rnd = new Random();

    /* ===================== LOG HELPERS ===================== */

    private static void log(String s)  { Bukkit.getLogger().info("[LastWarGame] " + s); }
    private static void warn(String s) { Bukkit.getLogger().warning("[LastWarGame] " + s); }

    /* ===================== TEAMS (COLOR/SIDE) ===================== */

    private String colorTeamId(TeamKey key) { return "CLR_" + key.id; }

    private Team ensureScoreboardTeam(TeamKey key) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String name = colorTeamId(key);
        Team t = board.getTeam(name);
        if (t == null) {
            t = board.registerNewTeam(name);
            t.setColor(key.chat);
            t.setPrefix(key.chat.toString());
        }
        return t;
    }

    public boolean isTeamSelectionLocked(UUID id) { return lockedTeams.contains(id); }
    public TeamKey getPlayerColorTeam(Player p)    { return playerColorTeams.get(p.getUniqueId()); }
    public String  getPlayerSide(Player p)         { return playerSides.get(p.getUniqueId()); }

    public void selectTeam(Player player, TeamKey key) {
        if (isTeamSelectionLocked(player.getUniqueId())) {
            player.sendMessage("§cYou can no longer change teams!");
            return;
        }

        // save color & move between CLR_ teams only
        playerColorTeams.put(player.getUniqueId(), key);

        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team t : board.getTeams()) if (t.getName().startsWith("CLR_")) t.removeEntry(player.getName());
        ensureScoreboardTeam(key).addEntry(player.getName());

        // side balance
        int red  = (int) playerSides.values().stream().filter("RED"::equals).count();
        int blue = (int) playerSides.values().stream().filter("BLUE"::equals).count();
        playerSides.put(player.getUniqueId(), red <= blue ? "RED" : "BLUE");

        // name color + list name + roster item
        player.setDisplayName(key.chat + player.getName() + ChatColor.RESET);
        player.setPlayerListName(player.getDisplayName());
        player.getInventory().setItem(4, buildTeamRosterItem(key));

        // refresh team GUI for viewers in this world
        TeamSelectorGUI.refreshAllViewers();
        updateTeamItemsForAllInWorld(player.getWorld().getName());

        player.sendMessage("§aYou joined " + key.chat + key.id + " §ateam (" +
                ("RED".equals(playerSides.get(player.getUniqueId())) ? "§cRED" : "§9BLUE") + ChatColor.RESET + " side).");
    }

    public void selectTeam(Player player, String teamId) {
        try {
            selectTeam(player, TeamKey.valueOf(teamId.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            String up = teamId.toUpperCase(Locale.ROOT);
            if (up.equals("RED") || up.equals("BLUE")) {
                TeamKey color = playerColorTeams.getOrDefault(player.getUniqueId(), up.equals("RED") ? TeamKey.RED : TeamKey.BLUE);
                playerColorTeams.put(player.getUniqueId(), color);
                ensureScoreboardTeam(color).addEntry(player.getName());
                playerSides.put(player.getUniqueId(), up);
                player.setDisplayName(color.chat + player.getName() + ChatColor.RESET);
                player.setPlayerListName(player.getDisplayName());
                player.getInventory().setItem(4, buildTeamRosterItem(color));
                updateTeamItemsForAllInWorld(player.getWorld().getName());
                player.sendMessage("§aYou joined " + color.chat + color.id + " §ateam (" +
                        (up.equals("RED") ? "§cRED" : "§9BLUE") + ChatColor.RESET + " side).");
            } else {
                player.sendMessage("§cUnknown team: " + teamId);
            }
        }
    }

    public void updatePlayerTeam(Player player, String side) {
        TeamKey color = playerColorTeams.getOrDefault(player.getUniqueId(), "RED".equals(side) ? TeamKey.RED : TeamKey.BLUE);
        selectTeam(player, color);
        playerSides.put(player.getUniqueId(), "RED".equals(side) ? "RED" : "BLUE");
        player.sendMessage("§aYou are now on the " + ("RED".equals(side) ? "§cRed" : "§9Blue") + " §aside.");
    }

    private ItemStack buildTeamRosterItem(TeamKey key) {
        ItemStack item = new ItemStack(key.wool);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§eYour Team: " + key.chat + key.id);
            List<String> lore = new ArrayList<>();
            lore.add("§7Members:");
            List<String> names = getRosterForTeam(key);
            if (names.isEmpty()) lore.add(" §8— empty —");
            else {
                int cap = Math.min(12, names.size());
                for (int i = 0; i < cap; i++) lore.add(" §f• " + names.get(i));
                if (names.size() > cap) lore.add(" §7... +" + (names.size() - cap) + " more");
            }
            meta.setLore(lore);
            meta.addEnchant(Enchantment.LUCK, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    public List<String> getRosterForTeam(TeamKey key) {
        List<String> names = Bukkit.getOnlinePlayers().stream()
                .filter(p -> key.equals(playerColorTeams.get(p.getUniqueId())))
                .map(Player::getName)
                .sorted(String::compareToIgnoreCase)
                .toList();
        if (!names.isEmpty()) return names;

        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team t = board.getTeam(colorTeamId(key));
        if (t == null) return List.of();
        return t.getEntries().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .map(Player::getName)
                .sorted(String::compareToIgnoreCase)
                .toList();
    }

    public void updateTeamItemsForAllInWorld(String worldName) {
        World w = Bukkit.getWorld(worldName);
        if (w == null) return;
        for (Player p : w.getPlayers()) {
            TeamKey key = playerColorTeams.get(p.getUniqueId());
            if (key != null) p.getInventory().setItem(4, buildTeamRosterItem(key));
        }
    }

    private void assignSidesIfMissing(List<Player> players) {
        for (Player p : players) {
            UUID id = p.getUniqueId();

            // SIDE: балансируем только счётные стороны
            if (!playerSides.containsKey(id)) {
                int red = (int) playerSides.values().stream().filter("RED"::equals).count();
                int blue = (int) playerSides.values().stream().filter("BLUE"::equals).count();
                playerSides.put(id, red <= blue ? "RED" : "BLUE");
            }

            // COLOR TEAM (16): теперь не RED/BLUE по умолчанию, а одна из всех 16
            ensureColorTeamAssigned(p);
        }

        if (!players.isEmpty()) updateTeamItemsForAllInWorld(players.get(0).getWorld().getName());
    }

    public boolean canChangeTeam(Player p) {
        World w = p.getWorld();
        if (w == null) return true;
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective obj = sb.getObjective(w.getName());
        if (obj == null) return true;
        return obj.getScore("isGameStarted").getScore() == 0;
    }

    /* ===================== QUEUE / START ===================== */

    public void checkGameStart(String worldName) {
        List<Player> players = getPlayersInWorld(worldName);
        if (players.size() >= 2 && players.size() <= 10) {
            if (gameTimers.containsKey(worldName)) stopGameCountdown(worldName);
            startGameCountdown(worldName, players);
        }
    }

    private void startGameCountdown(String worldName, List<Player> players) {
        for (Player player : players) giveTeamSelectionItem(player);

        BukkitRunnable timer = new BukkitRunnable() {
            int countdown = 15;
            @Override public void run() {
                List<Player> updatedPlayers = getPlayersInWorld(worldName);
                if (updatedPlayers.size() < 2) {
                    gameTimers.remove(worldName);
                    cancel();
                    return;
                }
                if (countdown <= 0) {
                    finishQueue(worldName, updatedPlayers);
                    cancel();
                } else {
                    Bukkit.broadcastMessage("§eGame in " + worldName + " starts in " + countdown + " seconds...");
                    countdown--;
                }
            }
        };
        timer.runTaskTimer(LastWarPlugin.getInstance(), 0L, 20L);
        gameTimers.put(worldName, timer);
    }

    public void giveTeamSelectionItem(Player player) {
        ItemStack teamSelector = new ItemStack(Material.WHITE_WOOL);
        ItemMeta meta = teamSelector.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§eTeam Selection");
            teamSelector.setItemMeta(meta);
        }
        player.getInventory().setItem(4, teamSelector);
    }

    public boolean isClassSelectionActive(String worldName) { return classSelectionActive.contains(worldName); }

    public void finishQueue(String worldName, List<Player> players) {
        finalizeSidesBalance(worldName);

        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective obj = sb.getObjective(worldName);
        if (obj != null) obj.getScore("isClassSelectionStarted").setScore(1);
        else warn("Objective for world " + worldName + " not found when starting game.");

        // закрыть селектор команд и убрать шерсть у всех (это мешало в редких случаях)
        clearTeamSelectorUIForAll(worldName);

        // лочим смену КОМАНД (цветов) на время выбора классов
        for (Player p : players) lockedTeams.add(p.getUniqueId());

        // страхуем SIDE/цвет
        assignSidesIfMissing(players);

        // массовый выбор классов на 20 сек
        log("[queue] start class selection in " + worldName + " players=" + players.size());
        startClassSelectionForAll(players, worldName);

        gameTimers.remove(worldName);
    }
    // Закрыть GUI выбора команды и очистить слот 4 у всех в мире
    private void clearTeamSelectorUIForAll(String worldName) {
        for (Player p : getPlayersInWorld(worldName)) {
            try {
                if (p.getOpenInventory() != null
                        && p.getOpenInventory().getTitle() != null
                        && TeamSelectorGUI.GUI_TITLE.equals(p.getOpenInventory().getTitle())) {
                    p.closeInventory();
                }
            } catch (Throwable t) {
                Bukkit.getLogger().warning("[LastWarGame] clearTeamSelectorUIForAll: " + p.getName() + " -> " + t.getMessage());
            }
            // убираем предмет селектора команд (мы его кладём в слот 4)
            p.getInventory().clear(4);
        }
    }


    private void finalizeSidesBalance(String worldName) {
        List<Player> players = getPlayersInWorld(worldName);
        assignSidesIfMissing(players);

        while (true) {
            int red  = (int) players.stream().filter(p -> "RED".equals(playerSides.get(p.getUniqueId()))).count();
            int blue = players.size() - red;
            if (Math.abs(red - blue) <= 1) break;

            String from = red > blue ? "RED" : "BLUE";
            String to   = from.equals("RED") ? "BLUE" : "RED";
            Player move = players.stream().filter(p -> from.equals(playerSides.get(p.getUniqueId()))).findAny().orElse(null);
            if (move == null) break;
            playerSides.put(move.getUniqueId(), to);
            move.sendMessage(ChatColor.YELLOW + "Balanced to " + (to.equals("RED") ? ChatColor.RED + "RED" : ChatColor.BLUE + "BLUE") + ChatColor.RESET + " side.");
        }

        int red  = (int) players.stream().filter(p -> "RED".equals(playerSides.get(p.getUniqueId()))).count();
        int blue = players.size() - red;
        for (Player p : players) {
            p.sendMessage(ChatColor.YELLOW + "Final sides:");
            p.sendMessage(ChatColor.RED + "RED: " + red);
            p.sendMessage(ChatColor.BLUE + "BLUE: " + blue);
        }
    }

    /* ===================== CLASS SELECTION (FREE MODE) ===================== */

    private void clearClassTags(Player p) {
        for (String tag : new HashSet<>(p.getScoreboardTags())) if (ALL_CLASS_TAGS.contains(tag)) p.removeScoreboardTag(tag);
    }
    private void startClassSelectionForAll(List<Player> players, String worldName) {
        classSelectionActive.add(worldName);

        for (Player p : players) {
            // чистим ТОЛЬКО классовые теги + сбрасываем флаг кита
            ClassItemManager.stripAllClassTags(p);
            p.removeScoreboardTag("KIT_GIVEN");
            playerClasses.remove(p.getUniqueId());
            log("[classSelect] open for " + p.getName());
            ClassSelectionGUI.open(p);
        }

        // авто-закрытие через 20с + фолбэк
        new BukkitRunnable() {
            @Override public void run() {
                classSelectionActive.remove(worldName);

                for (Player p : getPlayersInWorld(worldName)) {
                    boolean hasClassTag = p.getScoreboardTags().stream().anyMatch(ALL_CLASS_TAGS::contains);
                    if (!hasClassTag) {
                        String rndClass = classOptions.get(rnd.nextInt(classOptions.size()));
                        assignClassFree(p, rndClass);
                        log("[classSelect.timeout] " + p.getName() + " -> random '" + rndClass + "'");
                    }
                    if (p.getOpenInventory() != null && ClassSelectionGUI.GUI_TITLE.equals(p.getOpenInventory().getTitle())) {
                        p.closeInventory();
                    }
                }
                startGame(worldName);
            }
        }.runTaskLater(LastWarPlugin.getInstance(), 20L * 20);
    }


    /** Свободный выбор класса (источник истины — теги) */
    public void assignClassFree(Player player, String className) {
        log("[assignClassFree] request " + player.getName() + " -> '" + className + "'");
        if (!classOptions.contains(className)) {
            warn("[assignClassFree] REJECT: " + className + " not in classOptions");
            return;
        }
        clearClassTags(player);
        player.addScoreboardTag(className);
        playerClasses.put(player.getUniqueId(), className);
        log("[assignClassFree] applied to " + player.getName() + " | tags=" + player.getScoreboardTags());
    }

    public boolean isClassAvailable(String className) { return !playerClasses.containsValue(className); }
    public boolean isClassTaken(String className)     { return takenClasses.containsKey(className);   }

    private void startGame(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) { warn("World not found: " + worldName); return; }

        Bukkit.broadcastMessage("§aThe game starts now!");

        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective objective = sb.getObjective(worldName);

        new GameEndCheckerTask(worldName, LastWarPlugin.getInstance())
                .runTaskTimer(LastWarPlugin.getInstance(), 0L, 100L);

        Objective deathObj = sb.getObjective("DeathCount");
        if (deathObj != null) for (Player player : world.getPlayers()) deathObj.getScore(player.getName()).setScore(0);
        else warn("Objective DeathCount not found when starting game.");

        createAndStartBossBarr(worldName);
        new GoalMonitorTask(worldName).runTaskTimer(LastWarPlugin.getInstance(), 0L, 20L);

        if (objective != null) {
            objective.getScore("isClassSelectionStarted").setScore(0);
            objective.getScore("isGameStarted").setScore(1);
        } else warn("Objective for world " + worldName + " not found when starting game.");

        for (Player p : world.getPlayers()) log("[startGame] " + p.getName() + " tags=" + p.getScoreboardTags());

        // 1) страхуем SIDE/цвет
        assignSidesIfMissing(getPlayersInWorld(worldName));
        applyColorTeamsToScoreboard(world);

        // 2) у каждого должен быть РОВНО ОДИН классовый тег
        for (Player p : world.getPlayers()) {
            String chosen = null;
            for (String opt : classOptions) if (p.getScoreboardTags().contains(opt)) { chosen = opt; break; }
            if (chosen == null) {
                chosen = classOptions.get(rnd.nextInt(classOptions.size()));
                log("[startGame.fixup] " + p.getName() + " had NO class tag -> assign '" + chosen + "'");
            } else {
                log("[startGame.fixup] " + p.getName() + " has '" + chosen + "'");
            }
            ClassItemManager.stripAllClassTags(p);
            p.addScoreboardTag(chosen);
            p.removeScoreboardTag("KIT_GIVEN"); // чтобы 100% выдался кит
            log("[kits] " + p.getName() + " tagsAfter=" + p.getScoreboardTags());
        }

        // 3) выдаём киты
        for (Player p : world.getPlayers()) {
            ClassItemManager.giveItemsForTaggedClass(p);
        }

        // 4) тотемы
        giveStartingTotems(world, 5);

        // 5) спавны + freeze/телепорт ЧЕРЕЗ 1 ТИК (иногда TP конфликтует с закрытием/выдачей)
        Bukkit.getScheduler().runTask(LastWarPlugin.getInstance(), () -> {
            computeTeamSpawnsForWorld(worldName);
            freezeTime(worldName);
        });
    }



    private void applyColorTeamsToScoreboard(World world) {
        for (Player p : world.getPlayers()) {
            TeamKey key = playerColorTeams.get(p.getUniqueId());
            if (key == null) continue;
            Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
            for (Team t : board.getTeams()) if (t.getName().startsWith("CLR_")) t.removeEntry(p.getName());
            ensureScoreboardTeam(key).addEntry(p.getName());
        }
    }

    public void giveStartingTotems(World world, int count) {
        for (Player p : world.getPlayers()) {
            int left = count;

            if (p.getInventory().getItemInOffHand() == null
                    || p.getInventory().getItemInOffHand().getType() == Material.AIR) {
                p.getInventory().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING, 1));
                left--;
            }
            if (left > 0) p.getInventory().addItem(new ItemStack(Material.TOTEM_OF_UNDYING, left));
            p.updateInventory();
        }
    }

    /* ===================== FREEZE / TELEPORT ===================== */

    public boolean isPlayerFrozen(UUID id)          { return frozenPlayers.containsKey(id); }
    public Location getFrozenLocation(UUID id)      { return frozenPlayers.get(id); }
    public void freezePlayer(Player p, Location l)  { frozenPlayers.put(p.getUniqueId(), l); }
    public void unfreezeAllPlayers()                { frozenPlayers.clear(); }

    public void freezeTime(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        List<Player> players = world.getPlayers();
        if (players.isEmpty()) return;

        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective obj = sb.getObjective(worldName);
        if (obj != null) obj.getScore("isFrozen").setScore(1);

        assignSidesIfMissing(players);

        for (Player p : players) {
            if (!playerColorTeams.containsKey(p.getUniqueId())) {
                playerSides.putIfAbsent(p.getUniqueId(), "RED");
                TeamKey tk = "RED".equals(playerSides.get(p.getUniqueId())) ? TeamKey.RED : TeamKey.BLUE;
                playerColorTeams.put(p.getUniqueId(), tk);
                ensureScoreboardTeam(tk).addEntry(p.getName());
                warn("[freezeTime] " + p.getName() + " had no TeamKey -> set " + tk);
            }
        }

        computeTeamSpawnsForWorld(worldName);
        Map<TeamKey, Location> teamSpawns = worldTeamSpawns.get(worldName);
        if (teamSpawns == null || teamSpawns.isEmpty()) {
            World w = Bukkit.getWorld(worldName);
            teamSpawns = new HashMap<>();
            if (w != null) {
                teamSpawns.put(TeamKey.RED,  randomPointInRect(w, SPAWN_X1, SPAWN_Z1, SPAWN_X2, SPAWN_Z2));
                teamSpawns.put(TeamKey.BLUE, randomPointInRect(w, SPAWN_X1, SPAWN_Z1, SPAWN_X2, SPAWN_Z2));
            }
            worldTeamSpawns.put(worldName, teamSpawns);
            warn("[freezeTime] teamSpawns empty -> fallback RED/BLUE");
        }

        log("[freezeTime] world=" + worldName + " players=" + players.size()
                + " usedTeams=" + collectUsedColorTeams(worldName)
                + " spawns=" + teamSpawns.size());

        for (Player p : players) {
            TeamKey key = playerColorTeams.get(p.getUniqueId());
            Location base = teamSpawns.get(key);
            if (base == null) { warn("[freezeTime] no spawn for team=" + key + " -> skip " + p.getName()); continue; }

            Location spawn = jitterInsideRect(base, world, 3);
            boolean tp = p.teleport(spawn);
            frozenPlayers.put(p.getUniqueId(), spawn);
            log("[freezeTime] TP " + p.getName() + " ok=" + tp + " -> "
                    + String.format("(%.1f, %.1f, %.1f) team=%s", spawn.getX(), (double)spawn.getY(), spawn.getZ(), key));
        }

        Bukkit.broadcastMessage("§aAll players in " + world.getName() + " are now frozen!");

        new BukkitRunnable() {
            int countdown = 15;
            @Override public void run() {
                if (countdown <= 0) {
                    Bukkit.broadcastMessage("§aAll players in " + world.getName() + " can move again!");
                    unfreezeAllPlayers();
                    if (obj != null) obj.getScore("isFrozen").setScore(0);
                    cancel(); return;
                }
                Bukkit.broadcastMessage("§7Unfreeze in §e" + countdown + "§7 seconds...");
                countdown--;
            }
        }.runTaskTimer(LastWarPlugin.getInstance(), 0L, 20L);
    }



    private Location jitterInsideRect(Location base, World w, int radius) {
        for (int i = 0; i < 10; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2.0;
            double r   = rnd.nextDouble() * radius;
            double x   = base.getX() + Math.cos(ang) * r;
            double z   = base.getZ() + Math.sin(ang) * r;
            if (insideRect(x, z)) {
                int y = w.getHighestBlockYAt((int)Math.floor(x), (int)Math.floor(z)) + 1;
                return new Location(w, x, y, z);
            }
        }
        double x = clamp(base.getX(), Math.min(SPAWN_X1, SPAWN_X2), Math.max(SPAWN_X1, SPAWN_X2));
        double z = clamp(base.getZ(), Math.min(SPAWN_Z1, SPAWN_Z2), Math.max(SPAWN_Z1, SPAWN_Z2));
        int y = w.getHighestBlockYAt((int)Math.floor(x), (int)Math.floor(z)) + 1;
        return new Location(w, x, y, z);
    }

    private boolean insideRect(double x, double z) {
        double xmin = Math.min(SPAWN_X1, SPAWN_X2);
        double xmax = Math.max(SPAWN_X1, SPAWN_X2);
        double zmin = Math.min(SPAWN_Z1, SPAWN_Z2);
        double zmax = Math.max(SPAWN_Z1, SPAWN_Z2);
        return x >= xmin && x <= xmax && z >= zmin && z <= zmax;
    }

    private double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    /* ===================== WORLD BORDER ===================== */

    public void createAndStartBossBarr(String worldName) {
        BossBar bar = Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SOLID);
        bossBars.put(worldName, bar);

        World w = Bukkit.getWorld(worldName);
        if (w != null) for (Player p : w.getPlayers()) bar.addPlayer(p);

        // Выставляем border сразу
        setupWorldBorder(worldName);

        final int total = GAME_TOTAL_SECONDS;
        final int[] left = { total };

        new BukkitRunnable() {
            @Override public void run() {
                World w = Bukkit.getWorld(worldName);
                if (w == null) { cancel(); return; }

                BossBar current = bossBars.get(worldName);
                if (current != null) {
                    for (Player p : w.getPlayers()) if (!current.getPlayers().contains(p)) current.addPlayer(p);
                }

                if (left[0] == BORDER_SHRINK_LAST_SEC) startBorderShrink(worldName);

                bar.setTitle(ChatColor.GOLD + worldName + ChatColor.GRAY + " (" +
                        ChatColor.YELLOW + fmtTime(left[0]) + ChatColor.GRAY + ")");
                double progress = Math.max(0.0, Math.min(1.0, left[0] / (double) total));
                bar.setProgress(progress);

                left[0]--;
                if (left[0] < 0) {
                    bar.setTitle("§eTime's up!");
                    World world = Bukkit.getWorld(worldName);
                    handleGameEndAfter600Seconds(world, LastWarPlugin.getInstance());
                    cancel();
                }
            }
        }.runTaskTimer(LastWarPlugin.getInstance(), 0L, 20L);
    }

    private String fmtTime(int secs) { return String.format("%02d:%02d", secs / 60, secs % 60); }

    private void setupWorldBorder(String worldName) {
        World w = Bukkit.getWorld(worldName);
        if (w == null) return;
        WorldBorder border = w.getWorldBorder();
        border.setCenter(ARENA_CENTER_X, ARENA_CENTER_Z);
        border.setSize(BORDER_START_SIZE);
        border.setWarningTime(5);
        border.setDamageAmount(0.2);
        border.setDamageBuffer(2);
    }

    private void startBorderShrink(String worldName) {
        World w = Bukkit.getWorld(worldName);
        if (w == null) return;
        w.getWorldBorder().setCenter(ARENA_CENTER_X, ARENA_CENTER_Z);
        w.getWorldBorder().setSize(BORDER_END_SIZE, BORDER_SHRINK_LAST_SEC);
    }

    private void resetWorldBorder(String worldName) {
        World w = Bukkit.getWorld(worldName);
        if (w == null) return;

        WorldBorder wb = w.getWorldBorder();
        wb.setCenter(ARENA_CENTER_X, ARENA_CENTER_Z);
        wb.setSize(BORDER_START_SIZE);
        wb.setWarningDistance(10);
        wb.setWarningTime(5);
        wb.setDamageBuffer(2);
        wb.setDamageAmount(0.5);
    }

    /* ===================== GOALS / OVERTIME / END ===================== */

    public static void scheduleTimeout(World world) {
        JavaPlugin plugin = LastWarPlugin.getInstance();
        new BukkitRunnable() {
            @Override public void run() {
                Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
                Objective obj = scoreboard.getObjective(world.getName());
                if (obj == null) return;
                int red = obj.getScore("RED").getScore();
                int blue = obj.getScore("BLUE").getScore();
                if (red == blue) {
                    plugin.getLogger().info("[LastWar] Timeout reached, but it's a tie — waiting for overtime logic.");
                    return;
                }
                GameManager.handleGameEndAfter600Seconds(world, plugin);
            }
        }.runTaskLater(plugin, 1200 * 20L);
    }

    public static void handleGameEndAfter600Seconds(World world, JavaPlugin plugin) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective objective = scoreboard.getObjective(world.getName());
        if (objective == null) return;

        if (objective.getScore("isGameStarted").getScore() == 0) {
            log("Overtime task cancelled because game has ended.");
            return;
        }

        int red = objective.getScore("RED").getScore();
        int blue = objective.getScore("BLUE").getScore();

        if (red > blue) {
            Bukkit.broadcastMessage("§c§lRED wins the match!");
            scheduleFinalEnd(world, plugin, 15);
        } else if (blue > red) {
            Bukkit.broadcastMessage("§b§lBLUE wins the match!");
            scheduleFinalEnd(world, plugin, 15);
        } else {
            Bukkit.broadcastMessage("§6§lIt's a draw! Entering §eOVERTIME §6for 2 more minutes!");
            new BukkitRunnable() {
                int seconds = 180;
                @Override public void run() {
                    Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
                    Objective objective = scoreboard.getObjective(world.getName());
                    if (seconds <= 0) {
                        if (objective == null || objective.getScore("isGameStarted").getScore() == 0) {
                            cancel(); return;
                        } else {
                            Bukkit.broadcastMessage("§c§lOvertime is over!");
                            GameManager.handleGameEndAfter600Seconds(world, plugin);
                            cancel(); return;
                        }
                    }
                    seconds--;
                }
            }.runTaskTimer(plugin, 0L, 20L);
        }
    }

    public static void scheduleFinalEnd(World world, JavaPlugin plugin, int delaySeconds) {
        Plugin logicMain = Bukkit.getPluginManager().getPlugin("LastWarGameLogic");
        CoreSpawner spawner = new CoreSpawner((JavaPlugin) logicMain);
        spawner.clearAllArmorStands(world);

        for (Player player : world.getPlayers())
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 10f, 1f);

        for (Player player : world.getPlayers()) player.getInventory().clear(4);

        new BukkitRunnable() {
            @Override public void run() { GameManager.getInstance().endGame(world); }
        }.runTaskLater(plugin, delaySeconds * 20L);
    }

    /* ===================== LAST TEAM ALIVE WIN ===================== */

    private Set<TeamKey> getAliveColorTeamsInWorld(String worldName) {
        World w = Bukkit.getWorld(worldName);
        if (w == null) return Collections.emptySet();
        Set<TeamKey> alive = new HashSet<>();
        for (Player p : w.getPlayers()) {
            if (p.getGameMode() == GameMode.SURVIVAL) {
                TeamKey k = playerColorTeams.get(p.getUniqueId());
                if (k != null) alive.add(k);
            }
        }
        return alive;
    }

    public void checkVictoryByLastTeam(String worldName) {
        if (endingWorlds.contains(worldName)) return;

        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective obj = sb.getObjective(worldName);
        if (obj == null || obj.getScore("isGameStarted").getScore() == 0) return;

        Set<TeamKey> alive = getAliveColorTeamsInWorld(worldName);
        if (alive.size() == 1) {
            TeamKey winner = alive.iterator().next();
            startVictoryCelebration(worldName, winner);
        }
    }

    private void startVictoryCelebration(String worldName, TeamKey winner) {
        World w = Bukkit.getWorld(worldName);
        if (w == null) return;

        endingWorlds.add(worldName);

        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective obj = sb.getObjective(worldName);
        if (obj != null) obj.getScore("isGameStarted").setScore(0);

        Bukkit.broadcastMessage(winner.chat + "§l" + winner.id + " §awins the match!");

        BossBar bar = bossBars.get(worldName);
        if (bar != null) {
            bar.setColor(BarColor.YELLOW);
            bar.setTitle(winner.chat + "§l" + winner.id + " §7— Celebration (10s)");
            bar.setProgress(1.0);
        }

        new BukkitRunnable() {
            int ticks = 20; // 20 * 0.5s = 10s
            @Override public void run() {
                if (ticks-- <= 0) {
                    scheduleFinalEnd(w, LastWarPlugin.getInstance(), 0);
                    cancel(); return;
                }
                for (Player p : w.getPlayers()) {
                    if (p.getGameMode() != GameMode.SURVIVAL) continue;
                    TeamKey k = playerColorTeams.get(p.getUniqueId());
                    if (k != winner) continue;

                    Location c = p.getLocation().clone().add(0, 1.0, 0);
                    for (int i = 0; i < 3; i++)
                        spawnFireworkOnce(c.add((Math.random() - 0.5) * 2, 0, (Math.random() - 0.5) * 2));
                }
            }
        }.runTaskTimer(LastWarPlugin.getInstance(), 0L, 10L);
    }

    private void spawnFireworkOnce(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;
        Firework fw = world.spawn(loc, Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .withColor(Color.WHITE, Color.AQUA, Color.YELLOW)
                .withFade(Color.ORANGE)
                .with(FireworkEffect.Type.BALL_LARGE)
                .trail(true).flicker(true)
                .build());
        meta.setPower(0);
        fw.setFireworkMeta(meta);
        fw.detonate();
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                GameManager.getInstance().checkVictoryByLastTeam(player.getWorld().getName()), 2L);
    }

    /* ===================== WORLD RESTART ===================== */

    public void endGame(World world) {
        LastWarGameLogic.removeActiveGameWorld(world);

        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getWorld().equals(world))
                .collect(Collectors.toList());

        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Player p : world.getPlayers()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "setclass Clear " + p.getName());
            for (Team t : board.getTeams()) if (t.getName().startsWith("CLR_")) t.removeEntry(p.getName());
        }

        for (Player p : players) new HashSet<>(p.getScoreboardTags()).forEach(p::removeScoreboardTag);

        BossBar bar = bossBars.remove(world.getName());
        if (bar != null) bar.removeAll();

        // 🔽 ВОТ ЗДЕСЬ — возвращаем скины
        for (Player p : players) restoreOwnSkin(p);

        for (Player p : players) {
            removePlayerData(p.getUniqueId());
            p.teleport(Bukkit.getWorld("world").getSpawnLocation());
            LobbyItems.giveTo(p);
        }

        worldTeamSpawns.remove(world.getName());
        restartWorld(world.getName());
    }

    public void restartWorld(String worldName) {
        Plugin mv = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
        if (mv == null || !mv.isEnabled()) {
            warn("Multiverse-Core is not available!");
            return;
        }

        markWorldRestarting(worldName);

        // Сброс счётчиков-скоров
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective objective = scoreboard.getObjective(worldName);
        if (objective != null) {
            objective.getScore("RED").setScore(0);
            objective.getScore("BLUE").setScore(0);
            objective.getScore("Timer").setScore(0);
            objective.getScore("isGameStarted").setScore(0);
            objective.getScore("isFrozen").setScore(0);
            objective.getScore("isGoalScored").setScore(0);
            objective.getScore("isClassSelectionStarted").setScore(0);
        }

        // Пересоздание мира
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv delete " + worldName);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv confirm");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv clone lastwarGame0 " + worldName);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv import " + worldName + " normal");

        // Вернём бордер чуть позже (когда мир точно загружен)
        new BukkitRunnable() {
            @Override public void run() { resetWorldBorder(worldName); }
        }.runTaskLater(LastWarPlugin.getInstance(), 60L);

        // Увести игроков в лобби (если вдруг кто-то остался)
        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getWorld().getName().equals(worldName))
                .collect(Collectors.toList());
        for (Player p : players) {
            removePlayerData(p.getUniqueId());
            p.teleport(Bukkit.getWorld("world").getSpawnLocation());
            LobbyItems.giveTo(p);
        }

        new BukkitRunnable() { @Override public void run() { checkGameStart(worldName); } }
                .runTaskLater(LastWarPlugin.getInstance(), 100L);

        new BukkitRunnable() {
            @Override public void run() {
                unmarkWorldRestarting(worldName);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getOpenInventory() != null &&
                            "Server Selection".equals(player.getOpenInventory().getTitle())) {
                        ServerSelectionGUI.open(player);
                    }
                }
            }
        }.runTaskLater(LastWarPlugin.getInstance(), 60L);
    }

    public void markWorldRestarting(String worldName)   { restartingWorlds.add(worldName);    }
    public void unmarkWorldRestarting(String worldName) { restartingWorlds.remove(worldName); }
    public boolean isWorldRestarting(String worldName)  { return restartingWorlds.contains(worldName); }

    /* ===================== TEAM SELECTOR GUI (INNER) ===================== */

    public static class TeamSelectorGUI {
        public static final String GUI_TITLE = "§eSelect Your Team";

        public static final int[] TEAM_SLOTS = {
                10, 12, 14, 16,
                19, 21, 23, 25,
                28, 30, 32, 34,
                37, 39, 41, 43
        };

        public static void open(Player player) {
            Inventory gui = Bukkit.createInventory(null, 54, GUI_TITLE);

            ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta gm = glass.getItemMeta();
            if (gm != null) { gm.setDisplayName(" "); glass.setItemMeta(gm); }
            for (int i = 0; i < 54; i++) gui.setItem(i, glass);

            TeamKey[] keys = TeamKey.values();
            for (int i = 0; i < keys.length && i < TEAM_SLOTS.length; i++) {
                gui.setItem(TEAM_SLOTS[i], buildTeamIcon(player, keys[i]));
            }
            player.openInventory(gui);
        }

        public static ItemStack buildTeamIcon(Player viewer, TeamKey key) {
            ItemStack item = new ItemStack(key.wool);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(key.chat + "Join " + key.id + " Team");
                List<String> lore = new ArrayList<>();
                lore.add("§7Members:");
                List<String> names = GameManager.getInstance().getRosterForTeam(key);
                if (names.isEmpty()) lore.add(" §8— empty —");
                else {
                    int cap = Math.min(12, names.size());
                    for (int i = 0; i < cap; i++) lore.add(" §f• " + names.get(i));
                    if (names.size() > cap) lore.add(" §7... +" + (names.size() - cap) + " more");
                }
                meta.setLore(lore);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
            return item;
        }

        public static void refreshAllViewers() {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.getOpenInventory() == null) continue;
                if (!GUI_TITLE.equals(viewer.getOpenInventory().getTitle())) continue;
                Inventory inv = viewer.getOpenInventory().getTopInventory();
                TeamKey[] keys = TeamKey.values();
                for (int i = 0; i < keys.length && i < TEAM_SLOTS.length; i++) {
                    inv.setItem(TEAM_SLOTS[i], buildTeamIcon(viewer, keys[i]));
                }
                viewer.updateInventory();
            }
        }
    }

    /* ===================== MISC / HELPERS ===================== */

    private void stopGameCountdown(String worldName) {
        BukkitRunnable r = gameTimers.remove(worldName);
        if (r != null) r.cancel();
    }

    private List<Player> getPlayersInWorld(String worldName) {
        World w = Bukkit.getWorld(worldName);
        return (w == null) ? Collections.emptyList() : new ArrayList<>(w.getPlayers());
    }

    public void resetWorldState(String worldName) {
        stopGameCountdown(worldName);
        for (Player player : getPlayersInWorld(worldName)) {
            UUID id = player.getUniqueId();
            playerColorTeams.remove(id);
            playerSides.remove(id);
            playerClasses.remove(id);
            lockedTeams.remove(id);
            takenClasses.values().remove(id);
        }
        worldTeamSpawns.remove(worldName);
        classSelectionActive.remove(worldName);
    }

    public void removePlayerData(UUID uuid) {
        playerColorTeams.remove(uuid);
        playerSides.remove(uuid);
        playerClasses.remove(uuid);
        lockedTeams.remove(uuid);
        takenClasses.values().remove(uuid);
    }

    private int min(int a, int b){ return Math.min(a,b); }
    private int max(int a, int b){ return Math.max(a,b); }

    private Location randomPointInRect(World w, int x1, int z1, int x2, int z2) {
        int xmin = min(x1, x2), xmax = max(x1, x2);
        int zmin = min(z1, z2), zmax = max(z1, z2);
        double x = xmin + rnd.nextDouble() * (xmax - xmin);
        double z = zmin + rnd.nextDouble() * (zmax - zmin);
        int y = w.getHighestBlockYAt((int)Math.floor(x), (int)Math.floor(z)) + 1;
        return new Location(w, x + 0.5, y, z + 0.5);
    }

    private boolean farEnough(Location a, Location b, double minDist) {
        if (a == null || b == null) return true;
        return a.distance(b) >= minDist;
    }

    private Location jitter(Location base, World w, int radius){
        if (base == null || radius <= 0) return base;
        double ang = rnd.nextDouble() * Math.PI * 2.0;
        double r = rnd.nextDouble() * radius;
        double x = base.getX() + Math.cos(ang)*r;
        double z = base.getZ() + Math.sin(ang)*r;
        int y = w.getHighestBlockYAt((int)Math.floor(x), (int)Math.floor(z)) + 1;
        return new Location(w, x, y, z);
    }

    private Set<TeamKey> collectUsedColorTeams(String worldName){
        World w = Bukkit.getWorld(worldName);
        if (w == null) return Collections.emptySet();
        Set<TeamKey> used = new HashSet<>();
        for (Player p : w.getPlayers()) {
            TeamKey k = playerColorTeams.get(p.getUniqueId());
            if (k != null) used.add(k);
        }
        return used;
    }
    private static void restoreOwnSkin(Player p) {
        String n = p.getName();
        try {
            // вернуть скин по собственному нику
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "skin set " + n + " " + n);
            // обновить визуально
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "skin update " + n);
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[LastWarGame] Failed to restore skin for " + n + ": " + t);
        }
    }


    private void computeTeamSpawnsForWorld(String worldName){
        World w = Bukkit.getWorld(worldName);
        if (w == null) return;

        Set<TeamKey> used = collectUsedColorTeams(worldName);
        if (used.isEmpty()) {
            // редкий случай: страховка
            used = new HashSet<>(Arrays.asList(TeamKey.RED, TeamKey.BLUE));
            Bukkit.getLogger().warning("[computeTeamSpawns] used teams empty -> fallback to RED/BLUE");
        }

        Map<TeamKey, Location> map = new HashMap<>();
        List<TeamKey> order = new ArrayList<>(used);
        Collections.shuffle(order, rnd);

        for (TeamKey key : order) {
            Location chosen = null;
            for (int attempt = 0; attempt < 64; attempt++) {
                Location candidate = randomPointInRect(w, SPAWN_X1, SPAWN_Z1, SPAWN_X2, SPAWN_Z2);
                boolean ok = true;
                for (Location other : map.values()) {
                    if (!farEnough(candidate, other, MIN_TEAM_SPACING)) { ok = false; break; }
                }
                if (ok) { chosen = candidate; break; }
            }
            if (chosen == null) chosen = randomPointInRect(w, SPAWN_X1, SPAWN_Z1, SPAWN_X2, SPAWN_Z2);
            map.put(key, chosen);
        }
        worldTeamSpawns.put(worldName, map);
        Bukkit.getLogger().info("[computeTeamSpawns] world=" + worldName + " -> " + map);
    }
    /** Выбрать первую свободную цветную команду для мира, иначе любую случайную */
    private TeamKey nextFreeColorTeam(String worldName) {
        Set<TeamKey> used = collectUsedColorTeams(worldName);
        for (TeamKey k : TeamKey.values()) {
            if (!used.contains(k)) return k; // возьмём свободную
        }
        TeamKey[] all = TeamKey.values();
        return all[rnd.nextInt(all.length)]; // все заняты — возьмём любую
    }

    /** Если у игрока нет цвет-команды, назначаем одну из 16-ти (желательно свободную) */
    private void ensureColorTeamAssigned(Player p) {
        if (playerColorTeams.containsKey(p.getUniqueId())) return;

        TeamKey key = nextFreeColorTeam(p.getWorld().getName());
        playerColorTeams.put(p.getUniqueId(), key);

        // завести/обновить тиму на скорборде
        ensureScoreboardTeam(key).addEntry(p.getName());

        // визуалка
        p.setDisplayName(key.chat + p.getName() + ChatColor.RESET);
        p.setPlayerListName(p.getDisplayName());

        // предмет с ростером в слот 4
        p.getInventory().setItem(4, buildTeamRosterItem(key));

        Bukkit.getLogger().info("[autoTeam] " + p.getName() + " -> color team " + key.id);
    }


}
