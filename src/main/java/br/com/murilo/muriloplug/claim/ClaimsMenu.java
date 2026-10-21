package br.com.murilo.muriloplug.claim;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

/** Menu "Meus Claims". O holder próprio identifica o inventário, sem depender do título. */
public final class ClaimsMenu implements InventoryHolder {

    private static final int SLOTS = 9;

    private final Inventory inventory;

    private ClaimsMenu(Player player, List<Claim> claims) {
        this.inventory = Bukkit.createInventory(this, SLOTS, Component.text("Meus Claims"));
        for (int i = 0; i < claims.size() && i < SLOTS; i++) {
            inventory.setItem(i, head(player, claims.get(i), i + 1));
        }
    }

    public static void open(Player player, List<Claim> claims) {
        player.openInventory(new ClaimsMenu(player, claims).getInventory());
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private static ItemStack head(Player player, Claim claim, int amount) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD, amount);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(line(claim.name(), NamedTextColor.YELLOW));
        meta.lore(List.of(
                line("Mundo: " + claim.world(), NamedTextColor.GRAY),
                line("X: " + claim.minX() + " a " + claim.maxX(), NamedTextColor.GRAY),
                line("Z: " + claim.minZ() + " a " + claim.maxZ(), NamedTextColor.GRAY),
                line("Tamanho: " + claim.width() + "x" + claim.depth(), NamedTextColor.GRAY),
                line("Amigos: " + (claim.trusted().isEmpty() ? "nenhum" : String.join(", ", claim.trustedNames())),
                        NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
