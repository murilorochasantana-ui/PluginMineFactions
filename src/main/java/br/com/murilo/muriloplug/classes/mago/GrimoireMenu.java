package br.com.murilo.muriloplug.classes.mago;

import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Menu "Grimório" (54 slots): as magias aprendidas no alto, a área "Aprender" no meio (o Mago clica numa staff do
 * próprio inventário) e, na linha de baixo, as magias equipadas.
 */
public final class GrimoireMenu implements GrimoireHolder {

    private static final int SIZE = 54;
    private static final int INFO_SLOT = 4;
    private static final int FIRST_LEARNED_SLOT = 10;
    private static final int LEARN_SLOT = 31;
    private static final int EQUIPPED_LABEL_SLOT = 40;
    private static final int EQUIPPED_ROW = 45;

    private final Inventory inventory;
    private final UUID player;
    private final GrimoireManager grimoire;
    private final MagoSettings settings;

    private GrimoireMenu(Player player, GrimoireManager grimoire, MagoSettings settings) {
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("Grimório"));
        this.player = player.getUniqueId();
        this.grimoire = grimoire;
        this.settings = settings;
        render();
    }

    public static void open(Player player, GrimoireManager grimoire, MagoSettings settings) {
        player.openInventory(new GrimoireMenu(player, grimoire, settings).getInventory());
    }

    /** Remonta o menu com o estado atual (depois de equipar, desequipar ou aprender). */
    void render() {
        inventory.clear();
        for (int slot = 0; slot < 9; slot++) {
            inventory.setItem(slot, ShopUi.filler());
            inventory.setItem(36 + slot, ShopUi.filler());
        }
        List<StaffType> equipped = grimoire.equipped(player);
        StaffType selected = grimoire.selected(player);
        int max = grimoire.maxEquipped();

        inventory.setItem(INFO_SLOT, ShopUi.button(Material.BOOK, "§5Grimório",
                "§7Magias aprendidas ficam no alto.",
                "§7Clique numa magia para equipar ou desequipar.",
                "§7Clique direito numa equipada para selecioná-la."));

        List<StaffType> learned = grimoire.learned(player);
        for (int i = 0; i < learned.size(); i++) {
            StaffType type = learned.get(i);
            inventory.setItem(FIRST_LEARNED_SLOT + i, spellItem(type, equipped.contains(type), type == selected));
        }
        if (learned.isEmpty()) {
            inventory.setItem(FIRST_LEARNED_SLOT, ShopUi.button(Material.GRAY_DYE, "§8Nenhuma magia aprendida"));
        }

        inventory.setItem(LEARN_SLOT, ShopUi.button(Material.ENCHANTING_TABLE, "§aAprender",
                "§7Clique numa staff do seu inventário",
                "§7para aprender a magia dela.",
                "§cA staff será consumida."));

        inventory.setItem(EQUIPPED_LABEL_SLOT, ShopUi.button(Material.WRITABLE_BOOK,
                "§5Equipadas (" + equipped.size() + "/" + max + ")"));
        int first = EQUIPPED_ROW + (9 - max) / 2;
        for (int i = 0; i < max; i++) {
            if (i < equipped.size()) {
                StaffType type = equipped.get(i);
                inventory.setItem(first + i, spellItem(type, true, type == selected));
            } else {
                inventory.setItem(first + i, ShopUi.button(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§8Slot vazio"));
            }
        }
    }

    /** Magia aprendida que está neste slot, ou null. */
    StaffType learnedAt(int slot) {
        List<StaffType> learned = grimoire.learned(player);
        int index = slot - FIRST_LEARNED_SLOT;
        return index >= 0 && index < learned.size() ? learned.get(index) : null;
    }

    /** Magia equipada que está neste slot da linha de baixo, ou null. */
    StaffType equippedAt(int slot) {
        List<StaffType> equipped = grimoire.equipped(player);
        int index = slot - (EQUIPPED_ROW + (9 - grimoire.maxEquipped()) / 2);
        return index >= 0 && index < equipped.size() ? equipped.get(index) : null;
    }

    private ItemStack spellItem(StaffType type, boolean equipped, boolean selected) {
        List<String> lore = new ArrayList<>(type.description());
        lore.add("");
        lore.add("§7Custo: " + settings.manaCost(type) + " mana");
        lore.add("§7Recarga: " + MagoSettings.format(settings.cooldownSeconds(type)) + "s");
        lore.add("");
        lore.add(equipped ? "§aEquipada §7- clique para desequipar" : "§eClique para equipar");
        if (selected) {
            lore.add("§5▶ Selecionada");
        }
        ItemStack item = ShopUi.button(StaffType.MATERIAL, type.coloredSpellName(), lore.toArray(new String[0]));
        ItemMeta meta = item.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        if (equipped) {
            meta.setEnchantmentGlintOverride(true);
        }
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
