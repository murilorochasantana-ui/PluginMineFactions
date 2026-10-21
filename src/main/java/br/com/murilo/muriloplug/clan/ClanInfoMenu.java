package br.com.murilo.muriloplug.clan;

import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.shop.ShopUi;
import br.com.murilo.muriloplug.stats.StatsManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** "Clan [TAG]": menu só de visualização com o banner do clan e a cabeça de cada membro. */
public final class ClanInfoMenu implements ClanHolder {

    private static final int SLOTS = 27;
    private static final int BANNER_SLOT = 4;
    private static final int FIRST_MEMBER_SLOT = 11;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final Inventory inventory;

    private ClanInfoMenu(Clan clan, ClanManager clans, StatsManager stats, ClassManager classes) {
        this.inventory = Bukkit.createInventory(this, SLOTS, Component.text("Clan [" + clan.tag() + "]"));

        int kills = 0;
        for (UUID member : clan.members()) {
            kills += stats.getKills(member);
        }
        inventory.setItem(BANNER_SLOT, banner(clan, clans, kills));

        // Os membros ocupam os slots 11 a 26 (até 16 cabeças).
        int slot = FIRST_MEMBER_SLOT;
        for (UUID member : clan.members()) {
            if (slot >= SLOTS) {
                break;
            }
            inventory.setItem(slot++, head(clan, member, stats, classes));
        }
    }

    public static void open(Player viewer, Clan clan, ClanManager clans, StatsManager stats, ClassManager classes) {
        viewer.openInventory(new ClanInfoMenu(clan, clans, stats, classes).getInventory());
    }

    private static ItemStack banner(Clan clan, ClanManager clans, int kills) {
        return ShopUi.button(Material.WHITE_BANNER, "§6[" + clan.tag() + "] " + clan.name(),
                "§7Criado em: §f" + DATE.format(Instant.ofEpochMilli(clan.createdAt()).atZone(ZoneId.systemDefault())),
                "§7Membros: §f" + clan.size() + "/" + clans.maxMembers(),
                "§7Kills dos membros: §f" + kills);
    }

    private static ItemStack head(Clan clan, UUID member, StatsManager stats, ClassManager classes) {
        PlayerClass memberClass = classes.getClass(member);
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(member));
        meta.displayName(ShopUi.text("§e" + ClanManager.nameOf(member)));
        meta.lore(java.util.List.of(
                ShopUi.text("Cargo: " + (clan.leader().equals(member) ? "§6Líder" : "§7Membro")),
                ShopUi.text("K/D/A: " + stats.getKills(member) + "/" + stats.getDeaths(member) + "/" + stats.getAssists(member)),
                ShopUi.text("Classe: " + (memberClass == null ? "§7nenhuma" : memberClass.coloredName())),
                ShopUi.text(Bukkit.getPlayer(member) != null ? "§aOnline" : "§cOffline")));
        head.setItemMeta(meta);
        return head;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
