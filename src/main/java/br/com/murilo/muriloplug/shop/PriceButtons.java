package br.com.murilo.muriloplug.shop;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

/** Os sete botões de preço dos editores (+1, +10, +100, +1k, +10k, +100k, +1M), compartilhados pela loja e pelo mercado. */
public final class PriceButtons {

    /** Primeiro slot da linha de botões (slots 19 a 25). */
    public static final int FIRST_SLOT = 19;

    private static final long[] STEPS = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000};
    private static final String[] LABELS = {"+1", "+10", "+100", "+1k", "+10k", "+100k", "+1M"};
    private static final Material[] MATERIALS = {
            Material.GOLD_NUGGET, Material.GOLD_NUGGET, Material.GOLD_NUGGET,
            Material.GOLD_INGOT, Material.GOLD_INGOT, Material.GOLD_BLOCK, Material.DIAMOND_BLOCK};

    private PriceButtons() {
    }

    /** Coloca os botões no inventário, a partir de {@code firstSlot}. */
    public static void place(Inventory inventory, int firstSlot) {
        for (int i = 0; i < STEPS.length; i++) {
            String value = ShopUi.number(STEPS[i]);
            inventory.setItem(firstSlot + i, ShopUi.button(MATERIALS[i], "§6" + LABELS[i],
                    "§7Esquerdo: §a+" + value, "§7Direito: §c-" + value));
        }
    }

    /**
     * Quanto somar ao preço (positivo) ou subtrair (negativo) ao clicar no botão {@code index} (0 a 6, contado a partir
     * do primeiro slot). Retorna 0 se não for um botão de preço.
     */
    public static long delta(int index, ClickType click) {
        if (index < 0 || index >= STEPS.length) {
            return 0;
        }
        return click.isLeftClick() ? STEPS[index] : -STEPS[index];
    }

    /** Aplica a variação ao preço, sem passar de 0 nem de {@link ShopManager#MAX_PRICE}. */
    public static long clamp(long price, long delta) {
        return Math.max(0, Math.min(ShopManager.MAX_PRICE, price + delta));
    }
}
