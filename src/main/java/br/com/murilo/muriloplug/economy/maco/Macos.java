package br.com.murilo.muriloplug.economy.maco;

import br.com.murilo.muriloplug.economy.EconomyManager;
import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Maço de Dinheiro: o dinheiro como UM item que guarda qualquer valor. É um PAPER que não empilha, com o valor em
 * {@code mineplug:dinheiro} e um id único em {@code mineplug:dinheiro_id}. O nome nunca é usado para reconhecer um
 * maço: papel renomeado não vale nada.
 *
 * <p>Toda soma, divisão e depósito acontece na thread principal, conferindo o item e mudando-o no mesmo passo. O id
 * de um maço consumido (somado em outro, depositado, gasto) vai para a lista de gastos: uma cópia dele não vale mais
 * nada nesta sessão.
 *
 * <p>Também converte as notas antigas de valor fixo ({@code mineplug:nota}) em maços com o mesmo valor total: ao
 * entrar, ao abrir baús e ao pegar do chão (a loja e o mercado são convertidos ao ligar, por {@link #convert}).
 */
public final class Macos implements Listener {

    public static final String FEE_PATH = "economia.notas.taxa-deposito";
    public static final String REWARDS_PATH = "economia.notas.recompensas-em-notas";
    public static final String MODEL_PATH = "economia.notas.modelo-maco";

    private static final int MAX_SPENT = 200_000;

    private final JavaPlugin plugin;
    private final NamespacedKey valueKey;
    private final NamespacedKey idKey;
    private final NamespacedKey legacyKey;
    private final Set<String> spent = new LinkedHashSet<>();

    public Macos(JavaPlugin plugin) {
        this.plugin = plugin;
        this.valueKey = new NamespacedKey(plugin, "dinheiro");
        this.idKey = new NamespacedKey(plugin, "dinheiro_id");
        this.legacyKey = new NamespacedKey(plugin, "nota");
    }

    public static boolean addMissing(FileConfiguration config) {
        boolean changed = false;
        Object[][] defaults = {{FEE_PATH, 0.05}, {REWARDS_PATH, true}, {MODEL_PATH, "minecraft:paper"}};
        for (Object[] entry : defaults) {
            if (!config.contains((String) entry[0])) {
                config.set((String) entry[0], entry[1]);
                changed = true;
            }
        }
        return changed;
    }

    /** Taxa do depósito (0.05 = 5%), entre 0 e 1. */
    public double depositFee() {
        return Math.max(0, Math.min(1, plugin.getConfig().getDouble(FEE_PATH, 0.05)));
    }

    /** Se os ganhos (kill, venda, eventos) chegam em maço; false = direto no saldo. */
    public boolean rewardsAsCash() {
        return plugin.getConfig().getBoolean(REWARDS_PATH, true);
    }

    // ------------------------------------------------------------ item

    /** Maço novo (id novo) com o valor dado. */
    public ItemStack create(long value) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setMaxStackSize(1);
        meta.lore(List.of(ShopUi.text("§7Maço de dinheiro."), ShopUi.text("§7Use /money depositar.")));
        NamespacedKey model = NamespacedKey.fromString(plugin.getConfig().getString(MODEL_PATH, "minecraft:paper"));
        if (model != null) {
            meta.setItemModel(model);
        }
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        item.setItemMeta(meta);
        writeValue(item, value);
        return item;
    }

    /** Troca o valor (e o nome) do maço, mantendo o id. */
    private void writeValue(ItemStack item, long value) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ShopUi.text("§a" + EconomyManager.format(value)));
        meta.getPersistentDataContainer().set(valueKey, PersistentDataType.LONG, value);
        item.setItemMeta(meta);
    }

    private String idOf(ItemStack item) {
        return item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    /** Valor do maço (0 se não for um maço de verdade ou se já foi consumido). */
    public long valueOf(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) {
            return 0;
        }
        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        Long value = data.get(valueKey, PersistentDataType.LONG);
        String id = data.get(idKey, PersistentDataType.STRING);
        if (value == null || value <= 0 || id == null || spent.contains(id)) {
            return 0;
        }
        return value;
    }

    public boolean isCash(ItemStack item) {
        return valueOf(item) > 0;
    }

    /** Marca o maço como consumido (não vale mais nada, nem uma cópia dele). */
    private void spend(ItemStack item) {
        String id = idOf(item);
        if (id == null) {
            return;
        }
        spent.add(id);
        if (spent.size() > MAX_SPENT) {
            Iterator<String> oldest = spent.iterator();
            oldest.next();
            oldest.remove();
        }
    }

    // ------------------------------------------------------------ notas antigas

    /** Valor total de uma pilha de notas antigas (0 se não for nota antiga). */
    private long legacyValue(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) {
            return 0;
        }
        Long value = item.getItemMeta().getPersistentDataContainer().get(legacyKey, PersistentDataType.LONG);
        return value == null || value <= 0 ? 0 : value * item.getAmount();
    }

    /** Nota antiga vira um maço com o mesmo valor total; outros itens voltam iguais (mesma instância). */
    public ItemStack convert(ItemStack item) {
        long value = legacyValue(item);
        return value > 0 ? create(value) : item;
    }

    /** Converte as notas antigas de um inventário. Retorna quantas pilhas foram convertidas. */
    public int convertAll(Inventory inventory) {
        int converted = 0;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            long value = legacyValue(contents[slot]);
            if (value > 0) {
                inventory.setItem(slot, create(value));
                converted++;
            }
        }
        return converted;
    }

    // ------------------------------------------------------------ inventário

    /** Soma de todos os maços no inventário do jogador (inclui a outra mão). */
    public long count(Player player) {
        long total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            total = saturatedAdd(total, valueOf(item));
        }
        return total;
    }

    /** Slot do primeiro maço do inventário (-1 se não tiver). */
    private int firstSlot(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isCash(contents[slot])) {
                return slot;
            }
        }
        return -1;
    }

    /** Entrega {@code amount}: soma no maço que o jogador já tem, ou entrega um maço novo (cai no chão se não couber). */
    public void give(Player player, long amount) {
        if (amount <= 0) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        int slot = firstSlot(player);
        if (slot >= 0) {
            ItemStack item = inventory.getItem(slot);
            writeValue(item, saturatedAdd(valueOf(item), amount));
            inventory.setItem(slot, item);
            return;
        }
        giveSeparate(player, amount);
    }

    /** Entrega um maço novo, sem juntar com os outros. */
    public void giveSeparate(Player player, long amount) {
        if (amount <= 0) {
            return;
        }
        player.getInventory().addItem(create(amount)).values()
                .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
    }

    /** Solta um maço com {@code amount} no chão. */
    public void drop(Location location, long amount) {
        if (amount > 0 && location.getWorld() != null) {
            location.getWorld().dropItemNaturally(location, create(amount));
        }
    }

    /** Tira TODOS os maços do inventário e devolve o valor tirado. */
    public long takeAll(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        long total = 0;
        for (int slot = 0; slot < contents.length; slot++) {
            long value = valueOf(contents[slot]);
            if (value > 0) {
                total = saturatedAdd(total, value);
                spend(contents[slot]);
                inventory.setItem(slot, null);
            }
        }
        return total;
    }

    /** Tira o maço da mão principal e devolve o valor tirado. */
    public long takeMainHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        long value = valueOf(hand);
        if (value > 0) {
            spend(hand);
            player.getInventory().setItemInMainHand(null);
        }
        return value;
    }

    /**
     * Tira {@code amount} de dentro dos maços (dos menores para os maiores): diminui o valor; o maço que zera some.
     * Retorna false, sem mexer em nada, se os maços não bastarem.
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
            if (isCash(contents[slot])) {
                slots.add(slot);
            }
        }
        slots.sort(Comparator.comparingLong(slot -> valueOf(contents[slot])));
        long remaining = amount;
        for (int slot : slots) {
            ItemStack item = contents[slot];
            long value = valueOf(item);
            if (value <= remaining) {
                remaining -= value;
                spend(item);
                inventory.setItem(slot, null);
            } else {
                writeValue(item, value - remaining);
                inventory.setItem(slot, item);
                remaining = 0;
            }
            if (remaining == 0) {
                break;
            }
        }
        return true;
    }

    /** Junta todos os maços do inventário no primeiro. Retorna quantos maços foram somados nele. */
    public int mergeAll(Player player) {
        PlayerInventory inventory = player.getInventory();
        int first = firstSlot(player);
        if (first < 0) {
            return 0;
        }
        ItemStack[] contents = inventory.getContents();
        long total = valueOf(contents[first]);
        int merged = 0;
        for (int slot = 0; slot < contents.length; slot++) {
            long value = slot == first ? 0 : valueOf(contents[slot]);
            if (value > 0) {
                total = saturatedAdd(total, value);
                spend(contents[slot]);
                inventory.setItem(slot, null);
                merged++;
            }
        }
        ItemStack target = contents[first];
        writeValue(target, total);
        inventory.setItem(first, target);
        return merged;
    }

    // ------------------------------------------------------------ eventos

    /** Pegar um maço do chão com outro maço no inventário: soma no existente e o do chão some. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        Item entity = event.getItem();
        ItemStack stack = entity.getItemStack();
        if (legacyValue(stack) > 0) {
            stack = convert(stack);
            entity.setItemStack(stack);
        }
        if (!(event.getEntity() instanceof Player player) || !isCash(stack) || firstSlot(player) < 0) {
            return;
        }
        event.setCancelled(true);
        if (!entity.isValid()) {
            return;
        }
        long value = valueOf(stack);
        entity.remove(); // some do chão antes de o valor entrar no maço
        spend(stack);
        give(player, value);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.4f, 1.6f);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        convertAll(event.getPlayer().getInventory());
        convertAll(event.getPlayer().getEnderChest());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        convertAll(event.getInventory());
        if (event.getPlayer() instanceof Player player) {
            convertAll(player.getInventory());
        }
    }

    private static long saturatedAdd(long a, long b) {
        long result = a + b;
        return ((a ^ result) & (b ^ result)) < 0 ? Long.MAX_VALUE : result;
    }
}
