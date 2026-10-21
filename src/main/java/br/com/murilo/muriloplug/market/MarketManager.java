package br.com.murilo.muriloplug.market;

import br.com.murilo.muriloplug.shop.ShopManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Anúncios do mercado entre jogadores, salvos em mercado.yml a cada mudança (itens em Base64). */
public final class MarketManager {

    public static final long MAX_PRICE = ShopManager.MAX_PRICE;

    private final JavaPlugin plugin;
    private final File file;
    private final Map<Long, Listing> listings = new LinkedHashMap<>();
    private long nextId = 1;

    public MarketManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "mercado.yml");
    }

    /** Todos os anúncios, os mais novos primeiro. */
    public List<Listing> newestFirst() {
        List<Listing> list = new ArrayList<>(listings.values());
        list.sort(Comparator.comparingLong(Listing::id).reversed());
        return list;
    }

    /** Anúncios do vendedor, na ordem de criação. */
    public List<Listing> bySeller(UUID seller) {
        List<Listing> list = new ArrayList<>();
        for (Listing listing : listings.values()) {
            if (listing.seller().equals(seller)) {
                list.add(listing);
            }
        }
        list.sort(Comparator.comparingLong(Listing::id));
        return list;
    }

    public int count(UUID seller) {
        return bySeller(seller).size();
    }

    public int size() {
        return listings.size();
    }

    public Listing get(long id) {
        return listings.get(id);
    }

    /** Cria o anúncio (não há limite de anúncios por jogador). */
    public Listing add(UUID seller, String sellerName, ItemStack item, long price) {
        requireValidPrice(price);
        Listing listing = new Listing(nextId++, seller, sellerName, item, price, System.currentTimeMillis());
        listings.put(listing.id(), listing);
        save();
        return listing;
    }

    /** Muda o preço. Retorna false se o anúncio não existir mais. */
    public boolean updatePrice(long id, long price) {
        requireValidPrice(price);
        Listing listing = listings.get(id);
        if (listing == null) {
            return false;
        }
        listings.put(id, listing.withPrice(price));
        save();
        return true;
    }

    /**
     * Remove o anúncio e o devolve; retorna null se ele já não existir. É o que garante que um anúncio só possa
     * ser vendido (ou devolvido) uma vez: quem receber o objeto é o único dono do item.
     */
    public Listing remove(long id) {
        Listing removed = listings.remove(id);
        if (removed != null) {
            save();
        }
        return removed;
    }

    public void load() {
        listings.clear();
        nextId = 1;
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> map : yaml.getMapList("listings")) {
            try {
                long id = ((Number) map.get("id")).longValue();
                ItemStack item = ItemStack.deserializeBytes(Base64.getDecoder().decode(String.valueOf(map.get("item"))));
                listings.put(id, new Listing(id, UUID.fromString(String.valueOf(map.get("seller"))),
                        String.valueOf(map.get("seller-name")), item,
                        ((Number) map.get("price")).longValue(), ((Number) map.get("created")).longValue()));
                nextId = Math.max(nextId, id + 1);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Anúncio inválido em mercado.yml, ignorado: " + e.getMessage());
            }
        }
        nextId = Math.max(nextId, yaml.getLong("next-id", 1));
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("next-id", nextId);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Listing listing : listings.values()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", listing.id());
            map.put("seller", listing.seller().toString());
            map.put("seller-name", listing.sellerName());
            map.put("price", listing.price());
            map.put("created", listing.createdAt());
            map.put("item", Base64.getEncoder().encodeToString(listing.item().serializeAsBytes()));
            list.add(map);
        }
        yaml.set("listings", list);
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar mercado.yml: " + e.getMessage());
        }
    }

    private static void requireValidPrice(long price) {
        if (price < 1 || price > MAX_PRICE) {
            throw new IllegalArgumentException("Preço fora do limite (1 a " + MAX_PRICE + "): " + price);
        }
    }
}
