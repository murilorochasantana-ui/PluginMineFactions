package br.com.murilo.muriloplug.classes.mago;

import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.classes.PlayerClassChangeEvent;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Magias do Grimório de cada jogador, salvas por UUID em {@code mago.yml} (regravado a cada mudança): as aprendidas
 * (para sempre, mesmo sem a staff), as equipadas (até {@code mago.grimorio.max-equipadas}), a selecionada e se o
 * jogador já recebeu o Grimório inicial. Também entrega esse Grimório (com Fogo) na primeira vez que alguém vira Mago.
 */
public final class GrimoireManager implements Listener {

    public enum EquipResult { EQUIPPED, UNEQUIPPED, FULL, NOT_LEARNED }

    private static final class Data {
        final Set<StaffType> learned = EnumSet.noneOf(StaffType.class);
        final List<StaffType> equipped = new ArrayList<>();
        int selected;
        boolean received;
    }

    private final JavaPlugin plugin;
    private final MagoSettings settings;
    private final ClassManager classes;
    private final GrimoireItems items;
    private final File file;
    private final Map<UUID, Data> data = new HashMap<>();

    public GrimoireManager(JavaPlugin plugin, MagoSettings settings, ClassManager classes, GrimoireItems items) {
        this.plugin = plugin;
        this.settings = settings;
        this.classes = classes;
        this.items = items;
        this.file = new File(plugin.getDataFolder(), "mago.yml");
    }

    private Data data(UUID id) {
        return data.computeIfAbsent(id, k -> new Data());
    }

    public int maxEquipped() {
        return settings.maxEquipped();
    }

    // ------------------------------------------------------------ consulta

    public boolean knows(UUID id, StaffType type) {
        Data d = data.get(id);
        return d != null && d.learned.contains(type);
    }

    /** Magias aprendidas, na ordem das staffs. */
    public List<StaffType> learned(UUID id) {
        Data d = data.get(id);
        List<StaffType> list = new ArrayList<>();
        if (d != null) {
            for (StaffType type : StaffType.values()) {
                if (d.learned.contains(type)) {
                    list.add(type);
                }
            }
        }
        return list;
    }

    /** Magias equipadas, na ordem em que foram equipadas (no máximo o limite do config). */
    public List<StaffType> equipped(UUID id) {
        Data d = data.get(id);
        if (d == null) {
            return List.of();
        }
        return List.copyOf(d.equipped.subList(0, Math.min(d.equipped.size(), maxEquipped())));
    }

    /** Magia selecionada (a que o clique direito lança), ou null se não há magia equipada. */
    public StaffType selected(UUID id) {
        List<StaffType> list = equipped(id);
        if (list.isEmpty()) {
            return null;
        }
        Data d = data.get(id);
        return list.get(d.selected >= 0 && d.selected < list.size() ? d.selected : 0);
    }

    // ------------------------------------------------------------ mudança

    /** Marca a magia como aprendida. Retorna false se já era. */
    public boolean learn(UUID id, StaffType type) {
        if (!data(id).learned.add(type)) {
            return false;
        }
        save();
        return true;
    }

    /** Equipa a magia aprendida, ou a desequipa se já estava equipada. */
    public EquipResult toggleEquip(UUID id, StaffType type) {
        Data d = data(id);
        if (!d.learned.contains(type)) {
            return EquipResult.NOT_LEARNED;
        }
        int index = d.equipped.indexOf(type);
        if (index >= 0) {
            d.equipped.remove(index);
            if (index < d.selected) {
                d.selected--;
            }
            if (d.selected >= d.equipped.size()) {
                d.selected = 0;
            }
            save();
            return EquipResult.UNEQUIPPED;
        }
        if (equipped(id).size() >= maxEquipped()) {
            return EquipResult.FULL;
        }
        d.equipped.add(type);
        save();
        return EquipResult.EQUIPPED;
    }

    /** Seleciona uma magia já equipada. */
    public void select(UUID id, StaffType type) {
        int index = equipped(id).indexOf(type);
        if (index >= 0) {
            data(id).selected = index;
            save();
        }
    }

    /** Passa para a próxima magia equipada e a devolve (null se não há nenhuma). */
    public StaffType cycle(UUID id) {
        List<StaffType> list = equipped(id);
        if (list.isEmpty()) {
            return null;
        }
        Data d = data(id);
        int current = d.selected >= 0 && d.selected < list.size() ? d.selected : 0;
        d.selected = (current + 1) % list.size();
        save();
        return list.get(d.selected);
    }

    /** Atualiza a lore dos Grimórios que o jogador carrega. */
    public void refresh(Player player) {
        UUID id = player.getUniqueId();
        items.refresh(player, equipped(id), selected(id), maxEquipped());
    }

    // ------------------------------------------------------------ Grimório inicial

    /** Primeira vez como Mago: Grimório novo, magia de Fogo aprendida e equipada. Só uma vez por jogador. */
    private void grantStarter(Player player) {
        UUID id = player.getUniqueId();
        if (!classes.hasClass(id, PlayerClass.MAGO) || data(id).received) {
            return;
        }
        Data d = data(id);
        d.received = true;
        d.learned.add(StaffType.FOGO);
        if (d.equipped.isEmpty() && maxEquipped() > 0) {
            d.equipped.add(StaffType.FOGO);
            d.selected = 0;
        }
        save();
        for (ItemStack leftover : player.getInventory().addItem(items.create()).values()) {
            player.getWorld().dropItem(player.getLocation(), leftover);
        }
        player.sendMessage("§5Você recebeu o Grimório e aprendeu a magia de " + StaffType.FOGO.coloredSpellName()
                + "§5! Use /grimorio para aprender e equipar outras magias.");
        refresh(player);
    }

    @EventHandler
    public void onClassChange(PlayerClassChangeEvent event) {
        Player player = event.getPlayer();
        if (player != null && event.getNewClass() == PlayerClass.MAGO) {
            grantStarter(player);
        }
    }

    /** Quem já era Mago antes do Grimório existir também recebe o inicial na próxima entrada. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        grantStarter(event.getPlayer());
        refresh(event.getPlayer());
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            refresh(player);
        }
    }

    // ------------------------------------------------------------ arquivo

    public void load() {
        data.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            ConfigurationSection section = players.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            try {
                Data d = new Data();
                d.received = section.getBoolean("recebeu", false);
                d.selected = section.getInt("selecionada", 0);
                for (String id : section.getStringList("aprendidas")) {
                    StaffType type = StaffType.byId(id);
                    if (type != null) {
                        d.learned.add(type);
                    }
                }
                for (String id : section.getStringList("equipadas")) {
                    StaffType type = StaffType.byId(id);
                    if (type != null && d.learned.contains(type) && !d.equipped.contains(type)) {
                        d.equipped.add(type);
                    }
                }
                data.put(UUID.fromString(key), d);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("mago.yml: entrada inválida ignorada: " + key);
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        data.forEach((id, d) -> {
            String base = "players." + id + ".";
            yaml.set(base + "recebeu", d.received);
            yaml.set(base + "selecionada", d.selected);
            yaml.set(base + "aprendidas", d.learned.stream().map(StaffType::id).toList());
            yaml.set(base + "equipadas", d.equipped.stream().map(StaffType::id).toList());
        });
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar mago.yml: " + e.getMessage());
        }
    }
}
