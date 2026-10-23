package br.com.murilo.muriloplug.classes.engenheiro;

import br.com.murilo.muriloplug.auth.AuthManager;
import br.com.murilo.muriloplug.claim.ClaimManager;
import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.shop.ShopUi;
import br.com.murilo.muriloplug.teste.TargetRelations;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sentinelas do Engenheiro: blocos que avisam o dono, na tela, quando um inimigo chega perto. Ficam salvas em
 * sentinelas.yml (dono, mundo, posição). Quebradas pelo dono, voltam como item; destruídas por outro (ou por uma
 * explosão), somem sem drop e o dono é avisado. Inimigo = jogador logado ou boneco de teste inimigo (mobs comuns
 * não disparam o alerta).
 */
public final class SentinelaManager implements Listener {

    private static final long CHECK_TICKS = 20L;

    private record Sentinel(UUID owner, String world, int x, int y, int z) {
        String key() {
            return key(world, x, y, z);
        }

        static String key(String world, int x, int y, int z) {
            return world + ":" + x + ":" + y + ":" + z;
        }
    }

    private final JavaPlugin plugin;
    private final ClassManager classes;
    private final ClaimManager claims;
    private final AuthManager auth;
    private final TargetRelations relations;
    private final EngenheiroSettings settings;
    private final SentinelaItems items;
    private final EngenheiroAcoes actions;
    private final File file;
    private final Map<String, Sentinel> sentinels = new LinkedHashMap<>();
    /** Momento (ms) do último alerta de cada sentinela. */
    private final Map<String, Long> lastAlert = new HashMap<>();

    public SentinelaManager(JavaPlugin plugin, ClassManager classes, ClaimManager claims, AuthManager auth,
                            TargetRelations relations, EngenheiroSettings settings, SentinelaItems items,
                            EngenheiroAcoes actions) {
        this.plugin = plugin;
        this.classes = classes;
        this.claims = claims;
        this.auth = auth;
        this.relations = relations;
        this.settings = settings;
        this.items = items;
        this.actions = actions;
        this.file = new File(plugin.getDataFolder(), "sentinelas.yml");
    }

    // ------------------------------------------------------------ arquivo

    public void load() {
        sentinels.clear();
        if (!file.exists()) {
            return;
        }
        for (String line : YamlConfiguration.loadConfiguration(file).getStringList("sentinelas")) {
            String[] parts = line.split("\\|");
            try {
                Sentinel sentinel = new Sentinel(UUID.fromString(parts[0]), parts[1], Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3]), Integer.parseInt(parts[4]));
                sentinels.put(sentinel.key(), sentinel);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("sentinelas.yml: entrada inválida ignorada: " + line);
            }
        }
    }

    private void save() {
        List<String> lines = new ArrayList<>();
        for (Sentinel s : sentinels.values()) {
            lines.add(s.owner() + "|" + s.world() + "|" + s.x() + "|" + s.y() + "|" + s.z());
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("sentinelas", lines);
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar sentinelas.yml: " + e.getMessage());
        }
    }

    /** Quantas sentinelas o jogador tem colocadas. */
    public int count(UUID owner) {
        int total = 0;
        for (Sentinel s : sentinels.values()) {
            if (s.owner().equals(owner)) {
                total++;
            }
        }
        return total;
    }

    private Sentinel at(Block block) {
        return sentinels.get(Sentinel.key(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
    }

    private void remove(Sentinel sentinel) {
        sentinels.remove(sentinel.key());
        lastAlert.remove(sentinel.key());
        save();
    }

    private void notifyDestroyed(Sentinel sentinel) {
        Player owner = Bukkit.getPlayer(sentinel.owner());
        if (owner != null) {
            owner.sendMessage("§cSua Sentinela em (" + sentinel.x() + ", " + sentinel.y() + ", " + sentinel.z()
                    + ") foi destruída!");
        }
    }

    // ------------------------------------------------------------ colocar e quebrar

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!items.isSentinela(event.getItemInHand())) {
            return;
        }
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!classes.hasClass(id, PlayerClass.ENGENHEIRO)) {
            event.setCancelled(true);
            player.sendMessage("§cSomente Engenheiros podem usar a Sentinela.");
        } else if (!auth.isLoggedIn(id)) {
            event.setCancelled(true);
        } else if (!claims.canBuild(player, event.getBlockPlaced().getLocation())) {
            event.setCancelled(true);
            player.sendMessage("§cVocê não pode construir aqui.");
        } else if (count(id) >= settings.sentinelMax()) {
            event.setCancelled(true);
            player.sendMessage("§cVocê já tem " + settings.sentinelMax() + " Sentinelas colocadas.");
        }
    }

    /** Só registra se a colocação passou por todos (nenhum outro sistema cancelou). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaced(BlockPlaceEvent event) {
        if (!items.isSentinela(event.getItemInHand())) {
            return;
        }
        Block block = event.getBlockPlaced();
        Sentinel sentinel = new Sentinel(event.getPlayer().getUniqueId(), block.getWorld().getName(), block.getX(),
                block.getY(), block.getZ());
        sentinels.put(sentinel.key(), sentinel);
        save();
        event.getPlayer().sendMessage("§aSentinela colocada! §7(" + count(sentinel.owner()) + "/"
                + settings.sentinelMax() + ")");
        actions.registrarAcao(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Sentinel sentinel = at(event.getBlock());
        if (sentinel == null) {
            return;
        }
        event.setDropItems(false);
        remove(sentinel);
        Player player = event.getPlayer();
        if (sentinel.owner().equals(player.getUniqueId())) {
            if (player.getGameMode() != GameMode.CREATIVE) {
                player.getInventory().addItem(items.create(1)).values().forEach(
                        left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            }
            player.sendMessage("§eSentinela recolhida.");
        } else {
            notifyDestroyed(sentinel);
        }
    }

    /** Pistão não move sentinela (a posição guardada ficaria errada). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> at(block) != null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> at(block) != null)) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ explosões

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        explode(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        explode(event.blockList());
    }

    /** A sentinela atingida some sem drop (sai da lista de blocos) e o dono é avisado. */
    private void explode(List<Block> blocks) {
        for (Block block : List.copyOf(blocks)) {
            Sentinel sentinel = at(block);
            if (sentinel != null) {
                blocks.remove(block);
                block.setType(Material.AIR, false);
                remove(sentinel);
                notifyDestroyed(sentinel);
            }
        }
    }

    // ------------------------------------------------------------ vigia

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, CHECK_TICKS, CHECK_TICKS);
    }

    private boolean isTarget(Player owner, Entity entity) {
        if (!(entity instanceof LivingEntity living) || living.isDead() || entity.equals(owner)
                || !relations.isEnemy(owner, living)) {
            return false;
        }
        if (entity instanceof Player player) {
            return auth.isLoggedIn(player.getUniqueId()) && player.getGameMode() != GameMode.SPECTATOR;
        }
        return relations.isDummy(entity);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        long interval = settings.sentinelAlertSeconds() * 1000L;
        double radius = settings.sentinelRadius();
        for (Sentinel sentinel : List.copyOf(sentinels.values())) {
            Player owner = Bukkit.getPlayer(sentinel.owner());
            World world = Bukkit.getWorld(sentinel.world());
            if (owner == null || world == null || !world.isChunkLoaded(sentinel.x() >> 4, sentinel.z() >> 4)) {
                continue;
            }
            Block block = world.getBlockAt(sentinel.x(), sentinel.y(), sentinel.z());
            if (block.getType() != Material.OBSERVER) {
                remove(sentinel); // o bloco sumiu por outro meio
                notifyDestroyed(sentinel);
                continue;
            }
            Long last = lastAlert.get(sentinel.key());
            if (last != null && now - last < interval) {
                continue;
            }
            Location center = block.getLocation().add(0.5, 0.5, 0.5);
            LivingEntity nearest = null;
            double best = Double.MAX_VALUE;
            for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
                double distance = entity.getLocation().distanceSquared(center);
                if (distance <= radius * radius && distance < best && isTarget(owner, entity)) {
                    nearest = (LivingEntity) entity;
                    best = distance;
                }
            }
            if (nearest != null) {
                lastAlert.put(sentinel.key(), now);
                alert(owner, sentinel, center, nearest);
            }
        }
    }

    private void alert(Player owner, Sentinel sentinel, Location center, LivingEntity enemy) {
        String name = enemy instanceof Player player ? player.getName()
                : enemy.customName() != null ? PlainTextComponentSerializer.plainText().serialize(enemy.customName())
                : "Boneco";
        owner.showTitle(Title.title(ShopUi.text("§c⚠ INIMIGO DETECTADO"),
                ShopUi.text("§7" + name + " perto da sua Sentinela (" + sentinel.x() + ", " + sentinel.y() + ", "
                        + sentinel.z() + ")"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))));
        owner.playSound(owner.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.9f);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (owner.isOnline()) {
                owner.playSound(owner.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
            }
        }, 4L);
        center.getWorld().spawnParticle(Particle.DUST, center, 40, 0.6, 0.6, 0.6, 0,
                new Particle.DustOptions(Color.RED, 1.6f));
    }
}
