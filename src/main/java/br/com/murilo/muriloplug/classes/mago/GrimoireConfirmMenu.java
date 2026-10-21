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

/** Confirmação verde/vermelho de aprender uma magia ("Aprender Fogo?"): a staff é consumida. */
public final class GrimoireConfirmMenu implements GrimoireHolder {

    static final int CANCEL_SLOT = 3;
    static final int INFO_SLOT = 4;
    static final int CONFIRM_SLOT = 5;

    private final Inventory inventory;
    private final StaffType spell;
    /** Slot do inventário do jogador onde estava a staff quando ele clicou. */
    private final int staffSlot;
    /** Vira true na primeira confirmação, para um clique duplo não repetir a ação. */
    private boolean used;

    private GrimoireConfirmMenu(StaffType spell, int staffSlot) {
        this.spell = spell;
        this.staffSlot = staffSlot;
        this.inventory = Bukkit.createInventory(this, 9, Component.text("Aprender " + spell.spellName() + "?"));
        inventory.setItem(CANCEL_SLOT, ShopUi.button(Material.RED_CONCRETE, "§cCancelar"));
        ItemStack info = ShopUi.button(StaffType.MATERIAL, spell.coloredSpellName(),
                "§7Aprender " + spell.coloredSpellName() + "§7?", "§cA staff será consumida.");
        ItemMeta meta = info.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        info.setItemMeta(meta);
        inventory.setItem(INFO_SLOT, info);
        inventory.setItem(CONFIRM_SLOT, ShopUi.button(Material.LIME_CONCRETE, "§aConfirmar"));
    }

    public static void open(Player player, StaffType spell, int staffSlot) {
        player.openInventory(new GrimoireConfirmMenu(spell, staffSlot).getInventory());
    }

    StaffType spell() {
        return spell;
    }

    int staffSlot() {
        return staffSlot;
    }

    boolean used() {
        return used;
    }

    void markUsed() {
        used = true;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
