package br.com.murilo.muriloplug.clan;

import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;

/** Confirmação verde/vermelho de expulsar, passar a liderança ou desfazer o clan. Guarda a ação, a TAG e o alvo. */
public final class ClanConfirmMenu implements ClanHolder {

    public enum Action { KICK, TRANSFER, DISBAND }

    static final int CANCEL_SLOT = 3;
    static final int INFO_SLOT = 4;
    static final int CONFIRM_SLOT = 5;

    private final Inventory inventory;
    private final Action action;
    private final String clanTag;
    private final UUID target;
    /** Vira true na primeira confirmação, para um clique duplo não repetir a ação. */
    private boolean used;

    private ClanConfirmMenu(Action action, Clan clan, UUID target) {
        this.action = action;
        this.clanTag = clan.tag();
        this.target = target;

        String title;
        ItemStack info;
        switch (action) {
            case KICK -> {
                title = "Expulsar " + ClanManager.nameOf(target) + "?";
                info = head(target, "§eExpulsar " + ClanManager.nameOf(target));
            }
            case TRANSFER -> {
                title = "Passar liderança para " + ClanManager.nameOf(target) + "?";
                info = head(target, "§eNovo líder: " + ClanManager.nameOf(target));
            }
            default -> {
                title = "Desfazer o clan [" + clan.tag() + "]?";
                info = ShopUi.button(Material.WHITE_BANNER, "§6[" + clan.tag() + "] " + clan.name(),
                        "§cO clan será apagado para todos os membros.");
            }
        }
        this.inventory = Bukkit.createInventory(this, 9, Component.text(title));
        inventory.setItem(CANCEL_SLOT, ShopUi.button(Material.RED_CONCRETE, "§cCancelar"));
        inventory.setItem(INFO_SLOT, info);
        inventory.setItem(CONFIRM_SLOT, ShopUi.button(Material.LIME_CONCRETE, "§aConfirmar"));
    }

    public static void open(Player player, Action action, Clan clan, UUID target) {
        player.openInventory(new ClanConfirmMenu(action, clan, target).getInventory());
    }

    private static ItemStack head(UUID player, String name) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(player));
        meta.displayName(ShopUi.text(name));
        head.setItemMeta(meta);
        return head;
    }

    Action action() {
        return action;
    }

    String clanTag() {
        return clanTag;
    }

    UUID target() {
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
