package br.com.murilo.muriloplug.classes;

import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Confirmação verde/vermelho de escolher ("Escolher X?") ou de deixar a classe atual ("Deixar de ser X?"). */
public final class ClassConfirmMenu implements ClassHolder {

    public enum Kind { CHOOSE, LEAVE }

    static final int CANCEL_SLOT = 3;
    static final int INFO_SLOT = 4;
    static final int CONFIRM_SLOT = 5;

    private final Inventory inventory;
    private final Kind kind;
    private final PlayerClass target;
    /** Vira true na primeira confirmação, para um clique duplo não repetir a ação. */
    private boolean used;

    private ClassConfirmMenu(Kind kind, PlayerClass target) {
        this.kind = kind;
        this.target = target;
        String title = kind == Kind.CHOOSE
                ? "Escolher " + target.displayName() + "?"
                : "Deixar de ser " + target.displayName() + "?";
        String[] lore = kind == Kind.CHOOSE
                ? target.description().toArray(new String[0])
                : new String[]{"§cVocê vai perder a sua classe atual."};

        this.inventory = Bukkit.createInventory(this, 9, Component.text(title));
        inventory.setItem(CANCEL_SLOT, ShopUi.button(Material.RED_CONCRETE, "§cCancelar"));
        ItemStack info = ShopUi.button(target.icon(), target.coloredName(), lore);
        ItemMeta meta = info.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        info.setItemMeta(meta);
        inventory.setItem(INFO_SLOT, info);
        inventory.setItem(CONFIRM_SLOT, ShopUi.button(Material.LIME_CONCRETE, "§aConfirmar"));
    }

    public static void open(Player player, Kind kind, PlayerClass target) {
        player.openInventory(new ClassConfirmMenu(kind, target).getInventory());
    }

    Kind kind() {
        return kind;
    }

    PlayerClass target() {
        return target;
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
