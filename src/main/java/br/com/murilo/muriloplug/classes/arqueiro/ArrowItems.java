package br.com.murilo.muriloplug.classes.arqueiro;

import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Cria a Flecha Explosiva e a reconhece pelo PersistentDataContainer (chave mineplug:flecha), nunca pelo nome. */
public final class ArrowItems {

    public static final String EXPLOSIVE_ID = "explosiva";
    public static final String GRAPPLE_ID = "gancho";

    private final NamespacedKey arrowKey;

    public ArrowItems(JavaPlugin plugin) {
        this.arrowKey = new NamespacedKey(plugin, "flecha");
    }

    public ItemStack createExplosive(int amount) {
        ItemStack item = ShopUi.button(Material.ARROW, "§cFlecha Explosiva",
                "§71.5x de dano no acerto direto.",
                "§7Explosão de até 3 corações (raio 3).",
                "§7Recarga de 8s. Precisa puxar o arco.",
                "§cSomente Arqueiros podem usar.");
        item.setAmount(amount);
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(arrowKey, PersistentDataType.STRING, EXPLOSIVE_ID);
        item.setItemMeta(meta);
        return item;
    }

    /** Flecha Gancho do Engenheiro (só funciona na besta). */
    public ItemStack createGrapple(int amount) {
        ItemStack item = ShopUi.button(Material.ARROW, "§7Flecha Gancho",
                "§7Só funciona na besta.",
                "§5Somente Engenheiros");
        item.setAmount(amount);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(arrowKey, PersistentDataType.STRING, GRAPPLE_ID);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isGrapple(ItemStack item) {
        if (item == null || item.getType() != Material.ARROW || !item.hasItemMeta()) {
            return false;
        }
        return GRAPPLE_ID.equals(item.getItemMeta().getPersistentDataContainer().get(arrowKey, PersistentDataType.STRING));
    }

    public void markGrappleProjectile(PersistentDataContainer container) {
        container.set(arrowKey, PersistentDataType.STRING, GRAPPLE_ID);
    }

    public boolean isGrappleProjectile(PersistentDataContainer container) {
        return GRAPPLE_ID.equals(container.get(arrowKey, PersistentDataType.STRING));
    }

    private static String[] lore(SpecialArrow type) {
        String[] description = switch (type) {
            case PERFURANTE -> new String[]{
                    "§7Mais pesada: força total só com o arco puxado 40% mais tempo.",
                    "§7Atravessa alvos (cada um leva 30% menos dano que o anterior)",
                    "§7e causa +20% de dano em quem usa armadura.",
                    "§7Atravessa uma parede de 1 bloco (e vira flecha normal)."};
            case GELO -> new String[]{"§7Ao acertar: Lentidão II por 3 segundos."};
            case FUMACA -> new String[]{
                    "§7Ao acertar algo: nuvem de fumaça por 5 segundos.",
                    "§7Quem está dentro fica com Cegueira."};
            case CORRENTE -> new String[]{"§7Ao acertar um alvo: ele fica preso por 1 segundo."};
            case CORDA -> new String[]{
                    "§7Ao acertar um bloco: puxa você até ele (até 30 blocos).",
                    "§7Sem dano de queda por 3s. Recarga de 3s."};
            case EXPLOSIVA -> new String[]{"§7Explode como um creeper, sem quebrar blocos."};
        };
        String[] lines = new String[description.length + 2];
        System.arraycopy(description, 0, lines, 0, description.length);
        lines[description.length] = "§7Coloque na mão secundária e atire com o arco.";
        lines[description.length + 1] = "§aSomente Arqueiros podem usar.";
        return lines;
    }

    /** Cria a flecha especial (com brilho). A Explosiva mantém o item de sempre. */
    public ItemStack create(SpecialArrow type, int amount) {
        if (type == SpecialArrow.EXPLOSIVA) {
            return createExplosive(amount);
        }
        ItemStack item = ShopUi.button(Material.ARROW, type.displayName(), lore(type));
        item.setAmount(amount);
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(arrowKey, PersistentDataType.STRING, type.id());
        item.setItemMeta(meta);
        return item;
    }

    /** Tipo da flecha especial do Arqueiro que o item é, ou null (flecha comum, Gancho ou outro item). */
    public SpecialArrow typeOf(ItemStack item) {
        if (item == null || item.getType() != Material.ARROW || !item.hasItemMeta()) {
            return null;
        }
        return SpecialArrow.byId(item.getItemMeta().getPersistentDataContainer().get(arrowKey, PersistentDataType.STRING));
    }

    /** Tipo da flecha especial disparada (entidade), ou null. */
    public SpecialArrow typeOfProjectile(PersistentDataContainer container) {
        return SpecialArrow.byId(container.get(arrowKey, PersistentDataType.STRING));
    }

    public void markProjectile(PersistentDataContainer container, SpecialArrow type) {
        container.set(arrowKey, PersistentDataType.STRING, type.id());
    }

    /** Flecha especial (Explosiva, Gancho ou qualquer outra marcada com a chave mineplug:flecha). */
    public boolean isSpecial(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(arrowKey, PersistentDataType.STRING);
    }

    public boolean isExplosive(ItemStack item) {
        if (item == null || item.getType() != Material.ARROW || !item.hasItemMeta()) {
            return false;
        }
        return EXPLOSIVE_ID.equals(item.getItemMeta().getPersistentDataContainer().get(arrowKey, PersistentDataType.STRING));
    }

    /** Marca a flecha disparada (entidade) como Flecha Explosiva. */
    public void markProjectile(PersistentDataContainer container) {
        container.set(arrowKey, PersistentDataType.STRING, EXPLOSIVE_ID);
    }

    public boolean isExplosiveProjectile(PersistentDataContainer container) {
        return EXPLOSIVE_ID.equals(container.get(arrowKey, PersistentDataType.STRING));
    }
}
