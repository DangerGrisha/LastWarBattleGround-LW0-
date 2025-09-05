package org.lastwar_game.lastwargame.listeners.GUI;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.lastwar_game.lastwargame.GUI.ClassSelectionGUI;
import org.lastwar_game.lastwargame.LastWarPlugin;
import org.lastwar_game.lastwargame.managers.GameManager;

public class ClassSelectorListener implements Listener {

    private final GameManager gm = LastWarPlugin.getInstance().getGameManager();
    private NamespacedKey keyClassId() { return new NamespacedKey(LastWarPlugin.getInstance(), "classId"); }
    private NamespacedKey keySlot()    { return new NamespacedKey(LastWarPlugin.getInstance(), "slotIndex"); }

    @EventHandler
    public void onClassClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getView() == null || e.getView().getTitle() == null) return;
        if (!ClassSelectionGUI.GUI_TITLE.equals(e.getView().getTitle())) return;

        e.setCancelled(true); // запрещаем перемещение

        // кликаем только по верхнему инвентарю (GUI)
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        ItemMeta meta = clicked.getItemMeta();
        String classId = meta.getPersistentDataContainer().get(keyClassId(), PersistentDataType.STRING);
        Integer slot = meta.getPersistentDataContainer().get(keySlot(), PersistentDataType.INTEGER);

        LastWarPlugin.getInstance().getLogger().info(
                "[LastWarGame] [ClassSelector] CLICK top slot=" + e.getSlot() +
                        " raw=" + e.getRawSlot() +
                        " pdc.classId=" + classId +
                        " pdc.slot=" + slot +
                        " type=" + clicked.getType()
        );

        if (classId == null || classId.isEmpty()) return;

        // сохраняем выбор без блокировок и без чата
        gm.assignClassFree(p, classId);

        // подсветить только у этого игрока
        ClassSelectionGUI.highlightSelectionInOpenMenu(p, classId);
    }

    @EventHandler
    public void onClassMenuClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        String world = p.getWorld().getName();

        if (gm.isClassSelectionActive(world) &&
                ClassSelectionGUI.GUI_TITLE.equals(e.getView().getTitle())) {
            p.getServer().getScheduler().runTask(LastWarPlugin.getInstance(),
                    () -> ClassSelectionGUI.open(p)); // не даём закрыть
        }
    }
}
