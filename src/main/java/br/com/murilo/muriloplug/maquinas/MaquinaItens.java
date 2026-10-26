package br.com.murilo.muriloplug.maquinas;

import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Itens das máquinas, reconhecidos pelo PersistentDataContainer (continuam valendo depois de passar pela /loja e pelo
 * /mercado, que guardam o item inteiro):
 * <ul>
 *   <li>item da máquina: tipo + um id único (não empilha);</li>
 *   <li>item de upgrade: "TIPO:nível" (empilha só com o mesmo tipo e nível).</li>
 * </ul>
 */
public final class MaquinaItens {

    private final MaquinasConfig config;
    private final NamespacedKey tipoKey;
    private final NamespacedKey idKey;
    private final NamespacedKey upgradeKey;
    private final String namespace;

    public MaquinaItens(JavaPlugin plugin, MaquinasConfig config) {
        this.config = config;
        this.tipoKey = new NamespacedKey(plugin, "maquina_tipo");
        this.idKey = new NamespacedKey(plugin, "maquina_item_id");
        this.upgradeKey = new NamespacedKey(plugin, "maquina_upgrade");
        this.namespace = tipoKey.getNamespace();
    }

    // ------------------------------------------------------------ máquina

    /** Item da máquina, com os números atuais do tipo na lore. */
    public ItemStack criarMaquina(MaquinaTipo tipo) {
        ItemStack item = new ItemStack(config.bloco(tipo));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ShopUi.text(config.nome(tipo)));
        List<Component> lore = new ArrayList<>(MaquinaCalculos.linhasProducao(config, tipo, 0));
        lore.add(ShopUi.text(""));
        lore.add(ShopUi.text("§7Ciclo: §f" + MaquinasConfig.decimal(config.ciclo(tipo)) + "s"));
        lore.add(ShopUi.text("§7Combustível: §f" + MaquinasConfig.decimal(config.combustivelPorCiclo(tipo)) + " por ciclo"));
        lore.add(ShopUi.text("§7Armazém: §f" + config.armazemBase(tipo) + " itens"));
        lore.add(ShopUi.text(""));
        lore.add(ShopUi.text("§7Coloque no claim do seu clan"));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(tipoKey, PersistentDataType.STRING, tipo.name());
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        meta.setMaxStackSize(1);
        item.setItemMeta(meta);
        return item;
    }

    /** Tipo do item da máquina, ou null se não for um. */
    public MaquinaTipo tipoMaquina(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return MaquinaTipo.parse(item.getItemMeta().getPersistentDataContainer().get(tipoKey, PersistentDataType.STRING));
    }

    // ------------------------------------------------------------ upgrades

    public ItemStack criarUpgrade(Upgrade upgrade, int quantidade) {
        ItemStack item = new ItemStack(upgrade.tipo().material(), Math.max(1, Math.min(64, quantidade)));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ShopUi.text(upgrade.nomeColorido()));
        List<Component> lore = new ArrayList<>();
        lore.add(ShopUi.text("§7" + efeito(upgrade)));
        lore.add(ShopUi.text("§8Nível " + upgrade.nivel() + " de " + config.nivelMaximo(upgrade.tipo())));
        lore.add(ShopUi.text(""));
        lore.add(ShopUi.text("§7Coloque num slot de upgrade da máquina"));
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(upgradeKey, PersistentDataType.STRING, upgrade.serialize());
        item.setItemMeta(meta);
        return item;
    }

    /** Upgrade do item, ou null se não for um. */
    public Upgrade lerUpgrade(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return Upgrade.parse(item.getItemMeta().getPersistentDataContainer().get(upgradeKey, PersistentDataType.STRING));
    }

    /** O que o upgrade faz naquele nível, com os valores atuais da config. */
    public String efeito(Upgrade upgrade) {
        int nivel = upgrade.nivel();
        double valor = config.valorPorNivel(upgrade.tipo()) * nivel;
        String pct = MaquinasConfig.decimal(valor * 100) + "%";
        return switch (upgrade.tipo()) {
            case ESTOQUE -> "+" + MaquinasConfig.decimal(valor) + " de armazém e +"
                    + MaquinasConfig.decimal(config.estoqueCombustivelPorNivel() * nivel) + " de combustível máximo";
            case VELOCIDADE -> "-" + pct + " no tempo do ciclo";
            case EFICIENCIA -> "-" + pct + " no combustível por ciclo (e no custo do Cofre)";
            case FORTUNA -> "+" + pct + " de chance de produzir em dobro";
            case SORTE -> "+" + pct + " no peso dos itens raros";
            case BLINDAGEM -> "+" + MaquinasConfig.decimal(valor) + " de vida da máquina";
            case COLETA_AUTOMATICA -> "Armazém cheio: manda os produtos para o Baú do Clan";
            case VENDA_AUTOMATICA -> "Armazém cheio: vende ao /vender (taxa de "
                    + MaquinasConfig.decimal(config.taxaVendaAutomatica() * 100) + "%) e põe no Cofre";
        };
    }

    // ------------------------------------------------------------ outros

    /** Se o item tem alguma marca do plugin (itens especiais nunca servem de combustível). */
    public boolean especial(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        for (NamespacedKey key : item.getItemMeta().getPersistentDataContainer().getKeys()) {
            if (key.getNamespace().equals(namespace)) {
                return true;
            }
        }
        return false;
    }

    /** Unidades de combustível de 1 item (0 se não for combustível ou se for um item especial). */
    public double combustivel(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || especial(item)) {
            return 0;
        }
        return config.valorCombustivel(item.getType());
    }
}
