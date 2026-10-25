package br.com.murilo.muriloplug.economy.reparo;

import br.com.murilo.muriloplug.economy.ChatConfirmations;
import br.com.murilo.muriloplug.economy.EconomyManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /reparar (item da mão) e /reparar tudo (mão + armadura vestida), pago com {@link EconomyManager#cobrar}.
 * Preço de cada item = (durabilidade que falta / durabilidade máxima) × preço do material, × multiplicador se for
 * encantado. Itens de classe (marcados pelo plugin), inquebráveis e sem durabilidade não são consertados.
 */
public final class ReparoCommand implements CommandExecutor, TabCompleter, Listener {

    public static final String PRICES_PATH = "economia.reparo.precos";
    public static final String ENCHANTED_PATH = "economia.reparo.multiplicador-encantado";
    public static final Map<String, Integer> DEFAULT_PRICES = new LinkedHashMap<>();

    static {
        DEFAULT_PRICES.put("couro", 50);
        DEFAULT_PRICES.put("madeira", 30);
        DEFAULT_PRICES.put("pedra", 60);
        DEFAULT_PRICES.put("ouro", 150);
        DEFAULT_PRICES.put("ferro", 250);
        DEFAULT_PRICES.put("diamante", 800);
        DEFAULT_PRICES.put("netherite", 1500);
        DEFAULT_PRICES.put("outros", 300);
    }

    private static final EquipmentSlot[] ALL_SLOTS = {
            EquipmentSlot.HAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Um item a consertar: onde está, o item visto e quanto custa. */
    private record Job(EquipmentSlot slot, ItemStack seen, long price) {
    }

    private final JavaPlugin plugin;
    private final EconomyManager economy;
    private final String pluginNamespace;
    private final ChatConfirmations confirmations = new ChatConfirmations("reparar", 30);

    public ReparoCommand(JavaPlugin plugin, EconomyManager economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.pluginNamespace = new NamespacedKey(plugin, "x").getNamespace();
    }

    public static boolean addMissing(FileConfiguration config) {
        boolean changed = false;
        for (Map.Entry<String, Integer> entry : DEFAULT_PRICES.entrySet()) {
            String path = PRICES_PATH + "." + entry.getKey();
            if (!config.contains(path)) {
                config.set(path, entry.getValue());
                changed = true;
            }
        }
        if (!config.contains(ENCHANTED_PATH)) {
            config.set(ENCHANTED_PATH, 1.5);
            changed = true;
        }
        return changed;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Apenas jogadores podem usar este comando.");
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("confirmar")) {
            confirmations.confirm(player, args[1]);
            return true;
        }
        boolean all = args.length == 1 && args[0].equalsIgnoreCase("tudo");
        if (args.length > 1 || (args.length == 1 && !all)) {
            player.sendMessage("§cUso: /" + label + " [tudo]");
            return true;
        }
        if (!all) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            String problem = problem(hand);
            if (problem != null) {
                player.sendMessage("§c" + problem);
                return true;
            }
        }
        List<Job> jobs = jobs(player, all);
        if (jobs.isEmpty()) {
            player.sendMessage(all ? "§eNada do que você está usando precisa de conserto."
                    : "§eEste item não está danificado.");
            return true;
        }
        long total = total(jobs);
        String what = all ? jobs.size() + " item(ns)" : "este item";
        confirmations.ask(player, "§eConsertar " + what + " por §a" + EconomyManager.format(total)
                + "§e? §7(disponível: " + EconomyManager.format(economy.available(player)) + ")",
                p -> confirm(p, all, jobs, total));
        return true;
    }

    private void confirm(Player player, boolean all, List<Job> asked, long askedTotal) {
        // Confere de novo: os itens podem ter mudado (outro item na mão, mais dano) desde a pergunta.
        List<Job> jobs = jobs(player, all);
        long total = total(jobs);
        if (!sameItems(asked, jobs) || total != askedTotal) {
            player.sendMessage("§cOs itens mudaram desde a pergunta. Use /reparar" + (all ? " tudo" : "") + " de novo.");
            return;
        }
        if (!economy.cobrar(player, total)) {
            player.sendMessage("§cVocê não tem dinheiro suficiente. Preço: " + EconomyManager.format(total)
                    + ", disponível (saldo + notas): " + EconomyManager.format(economy.available(player)) + ".");
            return;
        }
        PlayerInventory inventory = player.getInventory();
        for (Job job : jobs) {
            ItemStack item = inventory.getItem(job.slot());
            ItemMeta meta = item.getItemMeta();
            ((Damageable) meta).setDamage(0);
            item.setItemMeta(meta);
            inventory.setItem(job.slot(), item);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
        player.sendMessage("§aConsertado! §7(" + EconomyManager.format(total) + ")");
    }

    private static boolean sameItems(List<Job> a, List<Job> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).slot() != b.get(i).slot() || !a.get(i).seen().equals(b.get(i).seen())) {
                return false;
            }
        }
        return true;
    }

    private static long total(List<Job> jobs) {
        long total = 0;
        for (Job job : jobs) {
            total += job.price();
        }
        return total;
    }

    /** Itens danificados e consertáveis: a mão (e a armadura vestida, com {@code all}). */
    private List<Job> jobs(Player player, boolean all) {
        List<Job> jobs = new ArrayList<>();
        for (EquipmentSlot slot : all ? ALL_SLOTS : new EquipmentSlot[]{EquipmentSlot.HAND}) {
            ItemStack item = player.getInventory().getItem(slot);
            if (problem(item) == null && item.getItemMeta() instanceof Damageable damageable && damageable.getDamage() > 0) {
                jobs.add(new Job(slot, item.clone(), price(item, damageable.getDamage())));
            }
        }
        return jobs;
    }

    /** Por que o item não pode ser consertado, ou null se pode. */
    private String problem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "Segure na mão o item que quer consertar.";
        }
        if (item.getType().getMaxDurability() <= 0 || !(item.getItemMeta() instanceof Damageable)) {
            return "Este item não tem durabilidade.";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta.isUnbreakable()) {
            return "Este item é inquebrável.";
        }
        for (NamespacedKey key : meta.getPersistentDataContainer().getKeys()) {
            if (key.getNamespace().equals(pluginNamespace)) {
                return "Itens de classe não podem ser consertados aqui.";
            }
        }
        return null;
    }

    private long price(ItemStack item, int damage) {
        int max = item.getItemMeta() instanceof Damageable d && d.hasMaxDamage() ? d.getMaxDamage()
                : item.getType().getMaxDurability();
        double fraction = Math.min(1, damage / (double) Math.max(1, max));
        double price = fraction * plugin.getConfig().getDouble(PRICES_PATH + "." + category(item.getType()),
                DEFAULT_PRICES.getOrDefault(category(item.getType()), 300));
        if (!item.getEnchantments().isEmpty()) {
            price *= Math.max(1, plugin.getConfig().getDouble(ENCHANTED_PATH, 1.5));
        }
        return Math.max(1, (long) Math.ceil(price));
    }

    /** Categoria de preço pelo nome do material. */
    private static String category(Material material) {
        String name = material.name();
        if (name.startsWith("LEATHER_")) {
            return "couro";
        }
        if (name.startsWith("WOODEN_")) {
            return "madeira";
        }
        if (name.startsWith("STONE_")) {
            return "pedra";
        }
        if (name.startsWith("GOLDEN_")) {
            return "ouro";
        }
        if (name.startsWith("IRON_") || name.startsWith("CHAINMAIL_")) {
            return "ferro";
        }
        if (name.startsWith("DIAMOND_")) {
            return "diamante";
        }
        if (name.startsWith("NETHERITE_")) {
            return "netherite";
        }
        return "outros";
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        confirmations.forget(event.getPlayer().getUniqueId());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? List.of("tudo") : List.of();
    }
}
