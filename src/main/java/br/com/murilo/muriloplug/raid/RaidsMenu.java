package br.com.murilo.muriloplug.raid;

import br.com.murilo.muriloplug.clan.ClanHolder;
import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * /clan raids (27 slots): os últimos raids sofridos (ou, na outra aba, os feitos), um item por raid com o resumo na
 * lore. Só leitura: como é um {@link ClanHolder}, todo clique é cancelado; a troca de aba fica no {@link RaidManager}.
 */
public final class RaidsMenu implements ClanHolder {

    static final int SUFFERED_SLOT = 18;
    static final int INFO_SLOT = 22;
    static final int MADE_SLOT = 26;

    private final Inventory inventory;
    private final String key;
    private final String title;

    RaidsMenu(String key, String title) {
        this.key = key;
        this.title = title;
        this.inventory = Bukkit.createInventory(this, 27, Component.text("Raids " + title));
    }

    String key() {
        return key;
    }

    void render(List<RaidRecord> records, boolean made) {
        inventory.clear();
        for (int slot = 18; slot < 27; slot++) {
            inventory.setItem(slot, ShopUi.filler());
        }
        // Mais novo primeiro, até 18 (os dois primeiros andares).
        for (int i = 0; i < Math.min(18, records.size()); i++) {
            RaidRecord record = records.get(records.size() - 1 - i);
            String name = made ? "§6Raid em [" + record.defenderName() + "] §7" + record.date()
                    : "§cRaid em " + record.claimName() + " §7" + record.date();
            inventory.setItem(i, ShopUi.button(made ? Material.IRON_SWORD : Material.TNT, name,
                    record.lines().toArray(String[]::new)));
        }
        if (records.isEmpty()) {
            inventory.setItem(4, ShopUi.button(Material.BARRIER, made ? "§7Nenhum raid feito ainda" : "§7Nenhum raid sofrido"));
        }
        inventory.setItem(SUFFERED_SLOT, ShopUi.button(Material.TNT, (made ? "§7" : "§c§l") + "Raids sofridos",
                "§7Clique para ver"));
        inventory.setItem(MADE_SLOT, ShopUi.button(Material.IRON_SWORD, (made ? "§6§l" : "§7") + "Raids feitos",
                "§7Clique para ver"));
        inventory.setItem(INFO_SLOT, ShopUi.button(Material.BOOK, "§e" + title,
                "§7Os últimos 10 raids ficam guardados.",
                "§7Um raid acaba depois de alguns minutos",
                "§7sem nenhum ataque."));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
