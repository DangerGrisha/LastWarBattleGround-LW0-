package org.lastwar_game.lastwargame.listeners.GUI;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.lastwar_game.lastwargame.managers.GameManager;

public class TeamSelectorListener implements Listener {


    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals("§eSelect Your Team")) {
            event.setCancelled(true); // Запрещаем забирать предметы из GUI

            if (event.getWhoClicked() instanceof Player) {
                Player player = (Player) event.getWhoClicked();
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem == null) return;

                String team = ""; // ✅ Объявляем переменную team

                if (clickedItem.getType() == Material.RED_WOOL) {
                    team = "RED";
                } else if (clickedItem.getType() == Material.BLUE_WOOL) {
                    team = "BLUE";
                } else {
                    return; // ✅ Если клик по другому предмету, игнорируем
                }

                GameManager.getInstance().selectTeam(player, team);
                GameManager.getInstance().updatePlayerTeam(player, team); // ✅ Обновляем данные

                // player.sendMessage("§aYou have joined the " + (team.equals("RED") ? "§cRed" : "§9Blue") + " Team!");
            }
        }
    }
}
