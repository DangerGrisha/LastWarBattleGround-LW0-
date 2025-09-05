package org.lastwar_game.lastwargame.listeners.GUI;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.lastwar_game.lastwargame.GUI.TeamSelectorGUI;
import org.lastwar_game.lastwargame.managers.GameManager;
import org.lastwar_game.lastwargame.teams.TeamKey;

// TeamSelectionClickListener.java
public class TeamSelectionClickListener implements Listener {
    public static final String E_TEAM_SELECTION = "§eTeam Selection";

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        final Player player = event.getPlayer();
        final ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == null || item.getItemMeta() == null) return;

        String name = item.getItemMeta().getDisplayName();
        boolean isSelector = E_TEAM_SELECTION.equals(name) || (name != null && name.startsWith("§eYour Team"));
        if (!isSelector) return;

        // NEW: блокируем смену только после старта игры
        if (!GameManager.getInstance().canChangeTeam(player)) {
            player.sendMessage("§cTeams are locked after the game starts.");
            return;
        }

        TeamSelectorGUI.open(player);
    }
    // TeamSelectorInventoryClickListener.java
    @EventHandler
    public void onTeamSelectClick(InventoryClickEvent event) {
        if (event.getView() == null || event.getView().getTitle() == null) return;
        if (!TeamSelectorGUI.GUI_TITLE.equals(event.getView().getTitle())) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!GameManager.getInstance().canChangeTeam(player)) {
            player.sendMessage("§cTeams are locked after the game starts.");
            player.closeInventory();
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == null) return;

        TeamKey key = TeamKey.fromWool(clicked.getType());
        if (key == null) return;

        GameManager.getInstance().selectTeam(player, key); // внутри — refreshAllViewers()
        player.closeInventory();
    }
}

