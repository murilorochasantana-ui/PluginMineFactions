package br.com.murilo.muriloplug.economy.notas;

import br.com.murilo.muriloplug.economy.EconomyManager;
import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Notas de dinheiro: o dinheiro como item. Uma nota é um PAPER com {@code mineplug:nota} (o valor) no
 * PersistentDataContainer; o nome nunca é usado para reconhecer uma nota, então papel renomeado não vale nada.
 * Notas do mesmo valor são idênticas e empilham até {@link #MAX_STACK}.
 *
 * <p>Todos os métodos que mexem em inventário rodam na thread principal; cada um faz a conferência e a mudança no
 * mesmo passo, sem nada entre elas (é o que impede duplicar notas).
 */
public final class Notas {

    public static final String VALUES_PATH = "economia.notas.valores";
    public static final String MODELS_PATH = "economia.notas.modelos";
    public static final String FEE_PATH = "economia.notas.taxa-deposito";
    public static final String REWARDS_PATH = "economia.notas.recompensas-em-notas";
    public static final String MAX_WITHDRAW_PATH = "economia.notas.saque-maximo";
    public static final List<Long> DEFAULT_VALUES = List.of(1L, 10L, 100L, 1_000L, 10_000L);
    public static final int MAX_STACK = 99;

    private final JavaPlugin plugin;
    private final NamespacedKey key;

    public Notas(JavaPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "nota");
    }

    /** Acrescenta ao config as chaves das notas que faltarem. */
    public static boolean addMissing(FileConfiguration config) {
        boolean changed = false;
        if (!config.contains(VALUES_PATH)) {
            config.set(VALUES_PATH, DEFAULT_VALUES);
            changed = true;
        }
        if (!config.contains(MODELS_PATH)) {
            Map<String, Object> models = new LinkedHashMap<>();
            for (long value : DEFAULT_VALUES) {
                models.put(String.valueOf(value), "minecraft:paper");
            }
            config.createSection(MODELS_PATH, models);
            changed = true;
        }
        changed |= addIfMissing(config, FEE_PATH, 0.05);
        changed |= addIfMissing(config, REWARDS_PATH, true);
        changed |= addIfMissing(config, MAX_WITHDRAW_PATH, 10_000_000);
        return changed;
    }

    private static boolean addIfMissing(FileConfiguration config, String path, Object value) {
        if (config.contains(path)) {
            return false;
        }
        config.set(path, value);
        return true;
    }

    // ------------------------------------------------------------ config

    /** Valores das notas, do maior para o menor (só positivos, sem repetir). */
    public List<Long> values() {
        List<Long> values = new ArrayList<>();
        for (Object raw : plugin.getConfig().getList(VALUES_PATH, DEFAULT_VALUES)) {
            try {
                long value = Long.parseLong(String.valueOf(raw).trim());
                if (value > 0 && !values.contains(value)) {
                    values.add(value);
                }
            } catch (NumberFormatException ignored) {
                // valor inválido no config: ignorado
            }
        }
        if (values.isEmpty()) {
            values.addAll(DEFAULT_VALUES);
        }
        values.sort(Comparator.reverseOrder());
        return values;
    }

    /** Taxa do depósito (0.05 = 5%), entre 0 e 1. */
    public double depositFee() {
        return Math.max(0, Math.min(1, plugin.getConfig().getDouble(FEE_PATH, 0.05)));
    }

    /** Se os ganhos (kill, venda, eventos) chegam em notas; false = direto no saldo. */
    public boolean rewardsAsNotes() {
        return plugin.getConfig().getBoolean(REWARDS_PATH, true);
    }

    /** Maior valor que um /money sacar entrega de uma vez (evita espalhar milhares de notas no chão). */
    public long maxWithdraw() {
        return Math.max(1, plugin.getConfig().getLong(MAX_WITHDRAW_PATH, 10_000_000));
    }

    private String model(long value) {
        ConfigurationSection models = plugin.getConfig().getConfigurationSection(MODELS_PATH);
        String model = models == null ? null : models.getString(String.valueOf(value));
        return model == null || model.isBlank() ? "minecraft:paper" : model.trim();
    }

    // ------------------------------------------------------------ item

    /** Pilha de {@code amount} notas de {@code value} (até {@link #MAX_STACK}). */
    public ItemStack create(long value, int amount) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setMaxStackSize(MAX_STACK); // o papel comum empilha só até 64
        meta.displayName(ShopUi.text("§a" + EconomyManager.format(value)));
        meta.lore(List.of(ShopUi.text("§7Nota de dinheiro. Use /money depositar.")));
        NamespacedKey model = NamespacedKey.fromString(model(value));
        if (model != null) {
            meta.setItemModel(model);
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.LONG, value);
        item.setItemMeta(meta);
        item.setAmount(Math.max(1, Math.min(MAX_STACK, amount)));
        return item;
    }

    /** Valor de UMA nota (0 se o item não for uma nota de verdade). */
    public long valueOf(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) {
            return 0;
        }
        Long value = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.LONG);
        return value == null || value <= 0 ? 0 : value;
    }

    public boolean isNote(ItemStack item) {
        return valueOf(item) > 0;
    }

    /** Valor total da pilha (valor × quantidade). */
    public long stackValue(ItemStack item) {
        long value = valueOf(item);
        return value <= 0 ? 0 : saturatedMultiply(value, item.getAmount());
    }

    /** Pilhas de notas que somam {@code amount}, usando as maiores notas possíveis. */
    public List<ItemStack> breakdown(long amount) {
        List<ItemStack> stacks = new ArrayList<>();
        long remaining = amount;
        for (long value : values()) {
            long count = remaining / value;
            remaining -= count * value;
            while (count > 0) {
                int stack = (int) Math.min(MAX_STACK, count);
                stacks.add(create(value, stack));
                count -= stack;
            }
        }
        // Sobra menor que a menor nota (só se o config não tiver nota de $1): não é entregue.
        return stacks;
    }

    // ------------------------------------------------------------ inventário

    /** Soma de todas as notas no inventário do jogador (inclui a outra mão). */
    public long count(Player player) {
        long total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            total = saturatedAdd(total, stackValue(item));
        }
        return total;
    }

    /** Entrega {@code amount} em notas; o que não couber cai aos pés do jogador. */
    public void give(Player player, long amount) {
        if (amount <= 0) {
            return;
        }
        Location feet = player.getLocation();
        for (ItemStack stack : breakdown(amount)) {
            player.getInventory().addItem(stack).values()
                    .forEach(rest -> player.getWorld().dropItem(feet, rest));
        }
    }

    /** Solta {@code amount} em notas no chão. */
    public void drop(Location location, long amount) {
        if (amount <= 0 || location.getWorld() == null) {
            return;
        }
        for (ItemStack stack : breakdown(amount)) {
            location.getWorld().dropItemNaturally(location, stack);
        }
    }

    /** Tira TODAS as notas do inventário e devolve o valor tirado. */
    public long takeAll(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        long total = 0;
        for (int slot = 0; slot < contents.length; slot++) {
            long value = stackValue(contents[slot]);
            if (value > 0) {
                total = saturatedAdd(total, value);
                inventory.setItem(slot, null);
            }
        }
        return total;
    }

    /** Tira as notas da mão principal e devolve o valor tirado. */
    public long takeMainHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        long value = stackValue(hand);
        if (value > 0) {
            player.getInventory().setItemInMainHand(null);
        }
        return value;
    }

    /**
     * Tira notas que somem pelo menos {@code amount} (das menores para as maiores) e devolve o troco em notas.
     * Retorna false, sem mexer em nada, se as notas do inventário não bastarem.
     */
    public boolean take(Player player, long amount) {
        if (amount <= 0) {
            return true;
        }
        if (count(player) < amount) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isNote(contents[slot])) {
                slots.add(slot);
            }
        }
        slots.sort(Comparator.comparingLong(slot -> valueOf(contents[slot])));
        long collected = 0;
        for (int slot : slots) {
            ItemStack item = contents[slot];
            long value = valueOf(item);
            int needed = (int) Math.min(item.getAmount(), ceilDiv(amount - collected, value));
            collected += needed * value;
            if (needed >= item.getAmount()) {
                inventory.setItem(slot, null);
            } else {
                item.setAmount(item.getAmount() - needed);
                inventory.setItem(slot, item);
            }
            if (collected >= amount) {
                break;
            }
        }
        give(player, collected - amount);
        return true;
    }

    private static long ceilDiv(long a, long b) {
        return (a + b - 1) / b;
    }

    private static long saturatedAdd(long a, long b) {
        long result = a + b;
        return ((a ^ result) & (b ^ result)) < 0 ? Long.MAX_VALUE : result;
    }

    private static long saturatedMultiply(long a, long b) {
        long high = Math.multiplyHigh(a, b);
        long low = a * b;
        return (high == 0 && low >= 0) ? low : Long.MAX_VALUE;
    }
}
