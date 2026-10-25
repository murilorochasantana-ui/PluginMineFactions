package br.com.murilo.muriloplug.clan.base;

import br.com.murilo.muriloplug.clan.ClanHolder;
import br.com.murilo.muriloplug.economy.EconomyManager;
import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;

import java.util.Locale;

/**
 * Menu do Cofre do Clan (27 slots). Todos os membros depositam maços (sem taxa); só o líder saca, escolhendo o valor
 * com os botões +100/+1k/+10k (esquerdo soma, direito tira). Como é um {@link ClanHolder}, todo clique é cancelado
 * pelo listener dos menus do clan; as ações ficam em {@link BaseBlocks}.
 */
public final class CofreMenu implements ClanHolder {

    static final int INFO_SLOT = 4;
    static final int DEPOSIT_SLOT = 11;
    static final int AMOUNT_SLOT = 13;
    static final int WITHDRAW_SLOT = 15;
    static final int FIRST_STEP_SLOT = 20;
    static final int RESET_SLOT = 24;
    static final long[] STEPS = {100, 1_000, 10_000};
    private static final String[] LABELS = {"+100", "+1k", "+10k"};
    private static final Material[] MATERIALS = {Material.GOLD_NUGGET, Material.GOLD_INGOT, Material.GOLD_BLOCK};

    private final Inventory inventory;
    private final String tag;
    private final boolean leader;
    private long amount;

    CofreMenu(String tag, boolean leader) {
        this.tag = tag;
        this.leader = leader;
        this.inventory = Bukkit.createInventory(this, 27, Component.text("Cofre do Clan [" + tag + "]"));
    }

    String tag() {
        return tag;
    }

    boolean leader() {
        return leader;
    }

    long amount() {
        return amount;
    }

    void addAmount(long delta, long max) {
        amount = Math.max(0, Math.min(max, amount + delta));
    }

    void resetAmount() {
        amount = 0;
    }

    void render(ClanBase base, BaseSettings settings) {
        inventory.clear();
        for (int slot = 0; slot < 27; slot++) {
            inventory.setItem(slot, ShopUi.filler());
        }
        long rendendo = Math.min(base.vaultBalance(), settings.interestCap());
        inventory.setItem(INFO_SLOT, ShopUi.button(Material.GOLD_BLOCK, "§eCofre do Clan [" + tag + "]",
                "§7Saldo: §a" + EconomyManager.format(base.vaultBalance()),
                "§7Rende " + String.format(Locale.ROOT, "%.1f", settings.interestPerDay() * 100) + "% ao dia "
                        + "§8(até " + EconomyManager.format(settings.interestCap()) + ")",
                "§7Rendendo agora: §f" + EconomyManager.format(rendendo),
                "§7Vida: §c" + base.vaultHp() + "/" + settings.vaultHp()));
        inventory.setItem(DEPOSIT_SLOT, ShopUi.button(Material.CHEST, "§aDepositar maços",
                "§7Coloca TODOS os maços de dinheiro", "§7do seu inventário no Cofre (sem taxa)."));
        if (!leader) {
            inventory.setItem(WITHDRAW_SLOT, ShopUi.button(Material.BARRIER, "§cSacar", "§7Só o líder pode sacar."));
            return;
        }
        inventory.setItem(AMOUNT_SLOT, ShopUi.button(Material.NAME_TAG, "§eValor do saque: §a" + EconomyManager.format(amount)));
        inventory.setItem(WITHDRAW_SLOT, ShopUi.button(Material.LIME_CONCRETE, "§aSacar " + EconomyManager.format(amount),
                "§7Entregue em maço de dinheiro."));
        for (int i = 0; i < STEPS.length; i++) {
            String value = ShopUi.number(STEPS[i]);
            inventory.setItem(FIRST_STEP_SLOT + i, ShopUi.button(MATERIALS[i], "§6" + LABELS[i],
                    "§7Esquerdo: §a+" + value, "§7Direito: §c-" + value));
        }
        inventory.setItem(RESET_SLOT, ShopUi.button(Material.BARRIER, "§cZerar valor"));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
