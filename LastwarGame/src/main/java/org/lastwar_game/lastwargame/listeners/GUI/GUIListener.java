package org.lastwar_game.lastwargame.listeners.GUI;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.lastwar_game.lastwargame.GUI.ClassSelectionGUI;
import org.lastwar_game.lastwargame.managers.GameManager;
import org.lastwar_game.lastwargame.LastWarPlugin;
// GUIListener.java
import org.lastwar_game.lastwargame.teams.TeamKey;

import java.util.UUID;

public class GUIListener implements Listener {

    private final GameManager gameManager;

    public GUIListener() {
        this.gameManager = LastWarPlugin.getInstance().getGameManager();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();

        // окно массового выбора классов
        if (e.getView() != null && ClassSelectionGUI.GUI_TITLE.equals(e.getView().getTitle())) {
            e.setCancelled(true); // нельзя забирать предметы

            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getItemMeta() == null) return;

            String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName()).trim();
            // ожидаем, что name совпадает с "LadyNagant"/"Archer"/"Tank"/"Saske"
            GameManager gm = LastWarPlugin.getInstance().getGameManager();
            gm.assignClassFree(p, name); // <- КЛЮЧЕВОЕ: сразу записываем класс и тег

            // визуально «подсветим» только у этого игрока
            ItemMeta m = clicked.getItemMeta();
            m.addEnchant(Enchantment.LUCK, 1, true);
            m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            clicked.setItemMeta(m);
            // возвращаем предмет на место (локально для этого окна)
            e.getInventory().setItem(e.getRawSlot(), clicked);
            p.updateInventory();
        }
    }
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        Player p = (Player) e.getPlayer();

        GameManager gm = LastWarPlugin.getInstance().getGameManager();
        // пока идёт 20-секундная фаза — не даём закрывать
        if (gm.isClassSelectionActive(p.getWorld().getName())
                && ClassSelectionGUI.GUI_TITLE.equals(e.getView().getTitle())) {
            Bukkit.getScheduler().runTask(LastWarPlugin.getInstance(), () -> ClassSelectionGUI.open(p));
        }
    }
}
