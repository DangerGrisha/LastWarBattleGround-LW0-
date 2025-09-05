package org.lastwar_game.lastwargame.GUI;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.lastwar_game.lastwargame.LastWarPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

public class ClassSelectionGUI {
    public static final String GUI_TITLE = "Class Selection";

    // фиксированный порядок
    private static final String[] ORDER = {
            "LadyNagant","Saske","Hutao","Ganyu","Dio","Naruto","BurgerMaster","Uraraka"
    };

    // все иконки RED_DYE как просили
    private static final Map<String, Material> ICONS = new LinkedHashMap<>();
    static {
        for (String id : ORDER) ICONS.put(id, Material.RED_DYE);
    }

    // “красивые” слоты в 27-слотовом меню (9*3), поперёк ряда: 10..16, затем 19..22
    private static final int[] SLOTS = {10,11,12,13,14,15,16,19,20,21,22};

    private static NamespacedKey keyClassId() { return new NamespacedKey(LastWarPlugin.getInstance(), "classId"); }
    private static NamespacedKey keySlot()    { return new NamespacedKey(LastWarPlugin.getInstance(), "slotIndex"); }

    public static void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);

        int placed = 0;
        for (int i = 0; i < ORDER.length && i < SLOTS.length; i++) {
            String classId = ORDER[i];
            int slot = SLOTS[i];
            ItemStack it = buildItem(classId, ICONS.getOrDefault(classId, Material.RED_DYE), slot);
            inv.setItem(slot, it);
            placed++;
            Bukkit.getLogger().info("[LastWarGame] [ClassGUI] put '" + classId + "' into slot #" + slot);
        }

        player.openInventory(inv);
        Bukkit.getLogger().info("[LastWarGame] [ClassGUI] opened for " + player.getName() + " | placed=" + placed);
    }

    private static ItemStack buildItem(String classId, Material icon, int slot) {
        ItemStack item = new ItemStack(icon, 1);
        ItemMeta meta = item.getItemMeta();

        // красивое отображаемое имя — только для вида
        meta.displayName(Component.text(classId));
        meta.lore(java.util.List.of(Component.text("§7Click to select")));

        // прячем подсветку
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        // критично: пишем classId и slot в PDC
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyClassId(), PersistentDataType.STRING, classId);
        pdc.set(keySlot(),    PersistentDataType.INTEGER, slot);

        item.setItemMeta(meta);
        return item;
    }

    // Подсветка выбора ТОЛЬКО у самого игрока
    public static void highlightSelectionInOpenMenu(Player player, String classId) {
        if (player.getOpenInventory() == null) return;
        if (!GUI_TITLE.equals(player.getOpenInventory().getTitle())) return;

        Inventory inv = player.getOpenInventory().getTopInventory();
        for (ItemStack it : inv.getContents()) {
            if (it == null || !it.hasItemMeta()) continue;
            ItemMeta m = it.getItemMeta();

            m.removeEnchant(Enchantment.LUCK);
            m.removeItemFlags(ItemFlag.HIDE_ENCHANTS);

            String stored = m.getPersistentDataContainer().get(keyClassId(), PersistentDataType.STRING);
            if (stored != null && stored.equalsIgnoreCase(classId)) {
                m.addEnchant(Enchantment.LUCK, 1, true);
                m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            it.setItemMeta(m);
        }
        player.updateInventory();
    }
}
