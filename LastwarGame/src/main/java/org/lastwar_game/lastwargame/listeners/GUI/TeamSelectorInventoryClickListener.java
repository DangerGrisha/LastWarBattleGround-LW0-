package org.lastwar_game.lastwargame.listeners.GUI;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.lastwar_game.lastwargame.GUI.TeamSelectorGUI;
import org.lastwar_game.lastwargame.managers.GameManager;
import org.lastwar_game.lastwargame.teams.TeamKey;

public class TeamSelectorInventoryClickListener implements Listener {

    @EventHandler
    public void onTeamSelectClick(InventoryClickEvent event) {
        if (event.getView() == null || event.getView().getTitle() == null) return;
        if (!TeamSelectorGUI.GUI_TITLE.equals(event.getView().getTitle())) return;

        event.setCancelled(true); // Prevent taking items from GUI

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == null) return;

        TeamKey key = TeamKey.fromWool(clicked.getType());
        if (key == null) return;

        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Respect "locked" stage
        if (GameManager.getInstance().isTeamSelectionLocked(player.getUniqueId())) {
            player.sendMessage("§cYou can no longer change teams!");
            player.closeInventory();
            return;
        }

        // Assign and refresh
        GameManager.getInstance().selectTeam(player, key);
        player.closeInventory();
    }
}
