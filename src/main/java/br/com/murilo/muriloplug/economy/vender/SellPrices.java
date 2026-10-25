package br.com.murilo.muriloplug.economy.vender;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tabela de venda ao servidor: preço base de cada material (config: economia.vender.precos) e o multiplicador
 * dinâmico (vender.yml). Cada unidade vendida baixa o multiplicador em {@code queda-por-unidade} até o
 * {@code minimo}; a cada hora ele recupera {@code recuperacao-por-hora} do que falta para 1.0.
 *
 * <p>Preço atual = base × multiplicador. Uma venda é calculada unidade por unidade: vender muito de uma vez fica mais
 * barato no fim. Os preços base ficam em memória (lidos do config ao ligar e alterados pelo /vender admin).
 */
public final class SellPrices {

    public static final String PRICES_PATH = "economia.vender.precos";
    public static final String DROP_PATH = "economia.vender.queda-por-unidade";
    public static final String MIN_PATH = "economia.vender.minimo";
    public static final String RECOVERY_PATH = "economia.vender.recuperacao-por-hora";

    private static final long HOUR_MILLIS = 60 * 60 * 1000L;
    private static final double EPSILON = 1.0E-9;

    /** Preços padrão (por unidade). */
    public static final Map<String, Double> DEFAULT_PRICES = new LinkedHashMap<>();

    static {
        // Minérios e lingotes
        DEFAULT_PRICES.put("COAL", 2.0);
        DEFAULT_PRICES.put("CHARCOAL", 1.0);
        DEFAULT_PRICES.put("RAW_COPPER", 1.0);
        DEFAULT_PRICES.put("COPPER_INGOT", 1.5);
        DEFAULT_PRICES.put("RAW_IRON", 4.0);
        DEFAULT_PRICES.put("IRON_INGOT", 5.0);
        DEFAULT_PRICES.put("RAW_GOLD", 6.0);
        DEFAULT_PRICES.put("GOLD_INGOT", 8.0);
        DEFAULT_PRICES.put("REDSTONE", 1.0);
        DEFAULT_PRICES.put("LAPIS_LAZULI", 2.0);
        DEFAULT_PRICES.put("QUARTZ", 2.0);
        DEFAULT_PRICES.put("EMERALD", 25.0);
        DEFAULT_PRICES.put("DIAMOND", 60.0);
        DEFAULT_PRICES.put("NETHERITE_SCRAP", 150.0);
        DEFAULT_PRICES.put("NETHERITE_INGOT", 650.0);
        // Colheitas
        DEFAULT_PRICES.put("WHEAT", 0.5);
        DEFAULT_PRICES.put("CARROT", 0.3);
        DEFAULT_PRICES.put("POTATO", 0.3);
        DEFAULT_PRICES.put("BEETROOT", 0.4);
        DEFAULT_PRICES.put("MELON_SLICE", 0.2);
        DEFAULT_PRICES.put("PUMPKIN", 1.0);
        DEFAULT_PRICES.put("SUGAR_CANE", 0.4);
        DEFAULT_PRICES.put("COCOA_BEANS", 0.5);
        DEFAULT_PRICES.put("SWEET_BERRIES", 0.3);
        // Madeiras básicas
        DEFAULT_PRICES.put("OAK_LOG", 1.0);
        DEFAULT_PRICES.put("SPRUCE_LOG", 1.0);
        DEFAULT_PRICES.put("BIRCH_LOG", 1.0);
        DEFAULT_PRICES.put("JUNGLE_LOG", 1.0);
        DEFAULT_PRICES.put("ACACIA_LOG", 1.0);
        DEFAULT_PRICES.put("DARK_OAK_LOG", 1.0);
        DEFAULT_PRICES.put("MANGROVE_LOG", 1.0);
        DEFAULT_PRICES.put("CHERRY_LOG", 1.0);
        // Drops de mobs
        DEFAULT_PRICES.put("ROTTEN_FLESH", 0.2);
        DEFAULT_PRICES.put("BONE", 1.0);
        DEFAULT_PRICES.put("ARROW", 0.5);
        DEFAULT_PRICES.put("FEATHER", 0.5);
        DEFAULT_PRICES.put("STRING", 1.0);
        DEFAULT_PRICES.put("COBWEB", 5.0);
        DEFAULT_PRICES.put("LEATHER", 3.0);
        DEFAULT_PRICES.put("GUNPOWDER", 3.0);
        DEFAULT_PRICES.put("SLIME_BALL", 4.0);
        DEFAULT_PRICES.put("BLAZE_ROD", 12.0);
        DEFAULT_PRICES.put("ENDER_PEARL", 15.0);
    }

    /** Resultado de uma cotação: quantas unidades entram na venda e quanto rende (antes de arredondar). */
    public record Quote(int units, double total) {

        /** Valor pago: o total arredondado para baixo. */
        public long payout() {
            return (long) Math.floor(total + EPSILON);
        }
    }

    private final JavaPlugin plugin;
    private final File file;
    private final String pluginNamespace;
    private final Map<Material, Double> basePrices = new EnumMap<>(Material.class);
    private final Map<Material, Double> multipliers = new EnumMap<>(Material.class);
    /** Momento da última venda de cada material (para a tendência ↓). */
    private final Map<Material, Long> lastSale = new EnumMap<>(Material.class);
    private long lastRecovery;
    private boolean dirty;

    public SellPrices(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "vender.yml");
        this.pluginNamespace = new NamespacedKey(plugin, "x").getNamespace();
    }

    public static boolean addMissing(FileConfiguration config) {
        boolean changed = false;
        if (!config.contains(PRICES_PATH)) {
            config.createSection(PRICES_PATH, DEFAULT_PRICES);
            changed = true;
        }
        changed |= addIfMissing(config, DROP_PATH, 0.002);
        changed |= addIfMissing(config, MIN_PATH, 0.3);
        changed |= addIfMissing(config, RECOVERY_PATH, 0.05);
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

    private double dropPerUnit() {
        return Math.max(0, plugin.getConfig().getDouble(DROP_PATH, 0.002));
    }

    private double minimum() {
        return Math.max(0, Math.min(1, plugin.getConfig().getDouble(MIN_PATH, 0.3)));
    }

    private double recoveryPerHour() {
        return Math.max(0, Math.min(1, plugin.getConfig().getDouble(RECOVERY_PATH, 0.05)));
    }

    // ------------------------------------------------------------ consulta

    /** Materiais com preço, na ordem da tabela. */
    public List<Material> materials() {
        return new ArrayList<>(basePrices.keySet());
    }

    public boolean hasPrice(Material material) {
        return basePrices.containsKey(material);
    }

    public double basePrice(Material material) {
        return basePrices.getOrDefault(material, 0.0);
    }

    public double multiplier(Material material) {
        return multipliers.getOrDefault(material, 1.0);
    }

    public double currentPrice(Material material) {
        return basePrice(material) * multiplier(material);
    }

    /** -1 = caindo (vendido na última hora), 1 = subindo (recuperando), 0 = estável no preço cheio. */
    public int trend(Material material) {
        Long sold = lastSale.get(material);
        if (sold != null && System.currentTimeMillis() - sold < HOUR_MILLIS) {
            return -1;
        }
        return multiplier(material) < 1.0 - EPSILON ? 1 : 0;
    }

    /**
     * Motivo de o item NÃO poder ser vendido, ou null se pode: sem preço, com nome personalizado, encantado ou
     * marcado pelo plugin (itens de classe, notas...).
     */
    public String refusal(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "vazio";
        }
        if (!hasPrice(item.getType())) {
            return "sem preço";
        }
        if (!item.getEnchantments().isEmpty()) {
            return "encantado";
        }
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta.hasDisplayName() || meta.hasItemName() || meta.hasEnchants()) {
                return "nome personalizado ou encantamento";
            }
            for (NamespacedKey key : meta.getPersistentDataContainer().getKeys()) {
                if (key.getNamespace().equals(pluginNamespace)) {
                    return "item especial";
                }
            }
        }
        return null;
    }

    /** Cotação sem mudar nada: {@code units} unidades de {@code material} vendidas agora. */
    public double quote(Material material, int units) {
        return simulate(material, units, false);
    }

    /** Vende de verdade: baixa o multiplicador unidade por unidade e devolve o total. */
    public double sell(Material material, int units) {
        if (units > 0) {
            lastSale.put(material, System.currentTimeMillis());
        }
        return simulate(material, units, true);
    }

    private double simulate(Material material, int units, boolean apply) {
        double base = basePrice(material);
        double multiplier = multiplier(material);
        double drop = dropPerUnit();
        double min = minimum();
        double total = 0;
        for (int i = 0; i < units; i++) {
            total += base * multiplier;
            multiplier = Math.max(min, multiplier - drop);
        }
        if (apply && units > 0) {
            multipliers.put(material, multiplier);
            dirty = true;
        }
        return total;
    }

    // ------------------------------------------------------------ admin

    /** Define o preço base (e grava no config). */
    public void setBasePrice(Material material, double price) {
        basePrices.put(material, price);
        plugin.getConfig().set(PRICES_PATH + "." + material.name(), price);
        plugin.saveConfig();
    }

    /** Tira o material da tabela (e do config). Retorna false se ele não tinha preço. */
    public boolean remove(Material material) {
        if (basePrices.remove(material) == null) {
            return false;
        }
        multipliers.remove(material);
        lastSale.remove(material);
        plugin.getConfig().set(PRICES_PATH + "." + material.name(), null);
        if (!plugin.getConfig().isConfigurationSection(PRICES_PATH)) {
            plugin.getConfig().createSection(PRICES_PATH); // a seção vazia continua existindo (não volta o padrão)
        }
        plugin.saveConfig();
        dirty = true;
        return true;
    }

    // ------------------------------------------------------------ recuperação e arquivo

    /** Chamado a cada minuto: aplica uma recuperação por hora completa que passou desde a última. */
    public void tick() {
        long now = System.currentTimeMillis();
        int hours = 0;
        while (now - lastRecovery >= HOUR_MILLIS && hours < 24 * 30) {
            lastRecovery += HOUR_MILLIS;
            hours++;
        }
        if (now - lastRecovery >= HOUR_MILLIS) {
            lastRecovery = now; // ficou desligado tempo demais: tudo já voltou ao normal
        }
        if (hours > 0) {
            recover(hours);
        }
        if (dirty) {
            save();
        }
    }

    /** Recupera {@code hours} horas: o que falta para 1.0 encolhe (1 - recuperação) por hora. */
    private void recover(int hours) {
        double keep = Math.pow(1 - recoveryPerHour(), hours);
        multipliers.replaceAll((material, value) -> 1.0 - (1.0 - value) * keep);
        multipliers.values().removeIf(value -> value >= 1.0 - 1.0E-4);
        dirty = true;
    }

    public void load() {
        basePrices.clear();
        ConfigurationSection prices = plugin.getConfig().getConfigurationSection(PRICES_PATH);
        if (prices != null) {
            for (String key : prices.getKeys(false)) {
                Material material = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
                double price = prices.getDouble(key, -1);
                if (material == null || !material.isItem() || price <= 0) {
                    plugin.getLogger().warning("Preço inválido em " + PRICES_PATH + ", ignorado: " + key);
                    continue;
                }
                basePrices.put(material, price);
            }
        }
        multipliers.clear();
        lastSale.clear();
        lastRecovery = System.currentTimeMillis();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        lastRecovery = yaml.getLong("ultima-recuperacao", lastRecovery);
        ConfigurationSection section = yaml.getConfigurationSection("multiplicadores");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material != null) {
                    multipliers.put(material, Math.max(0, Math.min(1, section.getDouble(key, 1.0))));
                }
            }
        }
        ConfigurationSection sales = yaml.getConfigurationSection("ultima-venda");
        if (sales != null) {
            for (String key : sales.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material != null) {
                    lastSale.put(material, sales.getLong(key));
                }
            }
        }
        tick(); // as horas em que o servidor ficou desligado também contam
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("ultima-recuperacao", lastRecovery);
        multipliers.forEach((material, value) -> yaml.set("multiplicadores." + material.name(), value));
        lastSale.forEach((material, time) -> yaml.set("ultima-venda." + material.name(), time));
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar vender.yml: " + e.getMessage());
        }
    }

    /** "$1,50" (duas casas, separador brasileiro). */
    public static String formatPrice(double value) {
        return "$" + String.format(Locale.of("pt", "BR"), "%,.2f", value);
    }
}
