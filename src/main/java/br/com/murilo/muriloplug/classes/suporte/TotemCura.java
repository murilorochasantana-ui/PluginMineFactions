package br.com.murilo.muriloplug.classes.suporte;

import br.com.murilo.muriloplug.claim.ClaimManager;
import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.classes.PlayerClassChangeEvent;
import br.com.murilo.muriloplug.shop.ShopUi;
import br.com.murilo.muriloplug.teste.StatusRegistry;
import br.com.murilo.muriloplug.teste.TargetRelations;
import br.com.murilo.muriloplug.teste.TestMode;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Husk;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Totem de Cura (item de uso único do Suporte: clique direito em um bloco): fica no chão até ser destruído, dando
 * Regeneração aos aliados por perto. Não gasta Harmonia, não tem recarga nem tempo limite.
 *
 * <p>Como foi feito: o "corpo" que recebe os golpes é um Husk invisível, sem IA, sem gravidade, sem som e sem
 * colisão (um mob de verdade, então flechas, golpes, magias e explosões o acertam, e as habilidades das classes já o
 * enxergam como alvo). A vida é do plugin: todo dano no corpo é cancelado e o dano final é descontado da vida do
 * totem, que aparece no nome em cima ("Totem de Cura ❤ 50"). Dano de aliados (e do próprio dono) é cancelado logo no
 * começo; dano sem um inimigo por trás (fogo, queda, sufocamento) não conta, só explosões. O visual são dois
 * displays: um bloco de esmeralda pequeno na base e um Totem da Imortalidade girando em cima. Corpo e displays levam
 * a marca mineplug:suporte_totem (com o UUID do dono) no PersistentDataContainer: o {@link TargetRelations} usa a
 * marca para tratar o totem como inimigo de quem não é do clan do dono (e nunca como aliado). Os totens ativos ficam
 * registrados aqui; as entidades não são salvas no mundo, e as sobras de um desligamento brusco são apagadas ao
 * ligar o plugin e quando o chunk carrega. Nada dropa e o item não é devolvido.
 *
 * <p>O item é um Totem da Imortalidade sem a proteção contra morte (o componente é removido ao criar o item), e o
 * {@link EntityResurrectEvent} é cancelado se mesmo assim ele fosse usado: morrer segurando o Totem de Cura não salva.
 */
public final class TotemCura implements Listener {

    public static final String STATUS = "Totem de Cura";
    private static final long TICK_PERIOD = 20L;
    private static final float BASE_SIZE = 0.7f;
    private static final double ITEM_HEIGHT = 1.25;

    private static final class Totem {
        final UUID owner;
        final Husk body;
        final BlockDisplay base;
        final ItemDisplay item;
        final Location anchor;
        double health;
        float angle;

        Totem(UUID owner, Husk body, BlockDisplay base, ItemDisplay item, Location anchor, double health) {
            this.owner = owner;
            this.body = body;
            this.base = base;
            this.item = item;
            this.anchor = anchor;
            this.health = health;
        }

        boolean contains(Entity entity) {
            return body.equals(entity) || base.equals(entity) || item.equals(entity);
        }
    }

    private final JavaPlugin plugin;
    private final ClassManager classes;
    private final ClaimManager claims;
    private final SuporteSettings settings;
    private final SuporteTargets targets;
    private final TargetRelations relations;
    private final StatusRegistry statuses;
    private final SuporteItems items;
    /** Totens ativos de cada Suporte (até {@code totem.maximo}). */
    private final Map<UUID, List<Totem>> totems = new HashMap<>();
    private TestMode test = TestMode.inactive();

    public TotemCura(JavaPlugin plugin, ClassManager classes, ClaimManager claims, SuporteSettings settings,
                     SuporteTargets targets, TargetRelations relations, StatusRegistry statuses, SuporteItems items) {
        this.plugin = plugin;
        this.classes = classes;
        this.claims = claims;
        this.settings = settings;
        this.targets = targets;
        this.relations = relations;
        this.statuses = statuses;
        this.items = items;
    }

    /** Modo Teste dos administradores: o item não é gasto. */
    public void setTestMode(TestMode test) {
        this.test = test;
    }

    public void start() {
        removeLeftovers();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    /** Tira todos os totens (onDisable). */
    public void endAll() {
        for (UUID owner : List.copyOf(totems.keySet())) {
            removeAll(owner, null);
        }
        removeLeftovers();
    }

    /** Quantos totens o Suporte tem ativos agora. */
    public int count(UUID owner) {
        List<Totem> own = totems.get(owner);
        return own == null ? 0 : own.size();
    }

    // ------------------------------------------------------------ colocar

    /**
     * Coloca o totem em cima do bloco clicado e gasta 1 Totem de Cura da mão principal. Não faz nada (e não gasta o
     * item) se já há o máximo de totens ativos ou se não dá para colocar ali.
     */
    public boolean place(Player support, Block clicked) {
        UUID owner = support.getUniqueId();
        int max = settings.totemMax();
        if (count(owner) >= max) {
            support.sendActionBar(ShopUi.text("§cVocê já tem " + max + " Totem(ns) de Cura ativo(s)."));
            return false;
        }
        Block spot = clicked.getRelative(BlockFace.UP);
        if (!spot.isPassable() || !spot.getRelative(BlockFace.UP).isPassable() || spot.isLiquid()) {
            support.sendActionBar(ShopUi.text("§cSem espaço para o totem aqui."));
            return false;
        }
        Location anchor = spot.getLocation().add(0.5, 0, 0.5);
        if (!claims.canBuild(support, anchor)) {
            support.sendActionBar(ShopUi.text("§cVocê não pode construir aqui."));
            return false;
        }
        String mark = owner.toString();
        double health = settings.totemHealth();
        World world = anchor.getWorld();
        Husk body = world.spawn(anchor, Husk.class, husk -> {
            husk.setAdult();
            husk.setAI(false);
            husk.setInvisible(true);
            husk.setSilent(true);
            husk.setGravity(false);
            husk.setCollidable(false);
            husk.setCanPickupItems(false);
            husk.setPersistent(false);
            husk.setRemoveWhenFarAway(false);
            husk.getEquipment().clear();
            husk.getAttribute(Attribute.MAX_HEALTH).setBaseValue(Math.max(1, health));
            husk.setHealth(Math.max(1, health));
            husk.setCustomNameVisible(true);
            husk.getPersistentDataContainer().set(relations.totemKey(), PersistentDataType.STRING, mark);
        });
        BlockDisplay base = world.spawn(anchor, BlockDisplay.class, display -> {
            display.setBlock(Material.EMERALD_BLOCK.createBlockData());
            display.setTransformation(new Transformation(new Vector3f(-BASE_SIZE / 2, 0, -BASE_SIZE / 2),
                    new AxisAngle4f(), new Vector3f(BASE_SIZE, BASE_SIZE, BASE_SIZE), new AxisAngle4f()));
            display.setPersistent(false);
            display.getPersistentDataContainer().set(relations.totemKey(), PersistentDataType.STRING, mark);
        });
        ItemDisplay item = world.spawn(anchor.clone().add(0, ITEM_HEIGHT, 0), ItemDisplay.class, display -> {
            display.setItemStack(new ItemStack(Material.TOTEM_OF_UNDYING));
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(0.9f, 0.9f, 0.9f), new AxisAngle4f()));
            display.setBrightness(new Display.Brightness(15, 15));
            display.setInterpolationDuration((int) TICK_PERIOD);
            display.setPersistent(false);
            display.getPersistentDataContainer().set(relations.totemKey(), PersistentDataType.STRING, mark);
        });
        Totem totem = new Totem(owner, body, base, item, anchor, health);
        totems.computeIfAbsent(owner, k -> new ArrayList<>()).add(totem);
        updateName(totem);
        statuses.add(support, STATUS, StatusRegistry.INDEFINITE);
        consumeOne(support);
        world.playSound(anchor, Sound.BLOCK_AMETHYST_BLOCK_PLACE, 1f, 0.8f);
        world.playSound(anchor, Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.6f);
        world.spawnParticle(Particle.HAPPY_VILLAGER, anchor.clone().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0);
        support.sendActionBar(ShopUi.text("§aTotem de Cura colocado!"));
        return true;
    }

    /** Uso único: tira 1 Totem de Cura da mão principal (no Modo Teste o item não é gasto). */
    private void consumeOne(Player support) {
        if (test.isActive(support)) {
            return;
        }
        ItemStack hand = support.getInventory().getItemInMainHand();
        if (items.read(hand) == SuporteItem.TOTEM) {
            hand.setAmount(hand.getAmount() - 1);
            support.getInventory().setItemInMainHand(hand.getAmount() > 0 ? hand : null);
        }
    }

    private void updateName(Totem totem) {
        totem.body.customName(ShopUi.text("§aTotem de Cura §c❤ " + (int) Math.ceil(Math.max(0, totem.health))));
    }

    // ------------------------------------------------------------ relógio

    private void tick() {
        for (Map.Entry<UUID, List<Totem>> entry : List.copyOf(totems.entrySet())) {
            UUID ownerId = entry.getKey();
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner == null || owner.isDead() || !classes.hasClass(ownerId, PlayerClass.SUPORTE)) {
                removeAll(ownerId, null);
                continue;
            }
            for (Totem totem : List.copyOf(entry.getValue())) {
                if (!totem.body.isValid()) {
                    remove(totem, null); // o chunk descarregou (as entidades do totem não são salvas)
                } else {
                    pulse(owner, totem);
                }
            }
        }
    }

    /** Regeneração nos aliados por perto (renovada a cada segundo), partículas e o totem girando. */
    private void pulse(Player owner, Totem totem) {
        if (totem.body.getLocation().distanceSquared(totem.anchor) > 0.01) {
            totem.body.teleport(totem.anchor); // nada empurra o totem do lugar
        }
        int amplifier = settings.totemLevel() - 1;
        double radius = settings.totemRadius();
        for (LivingEntity ally : targets.around(owner, totem.anchor, radius)) {
            ally.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, amplifier, true, true, true));
            ally.getWorld().spawnParticle(Particle.HEART, ally.getLocation().add(0, ally.getHeight() + 0.3, 0), 2,
                    0.3, 0.1, 0.3, 0);
        }
        World world = totem.anchor.getWorld();
        Location top = totem.anchor.clone().add(0, ITEM_HEIGHT + 0.4, 0);
        world.spawnParticle(Particle.HEART, top, 3, 0.3, 0.2, 0.3, 0);
        for (int i = 0; i < 24; i++) {
            double angle = i * Math.PI / 12;
            world.spawnParticle(Particle.HAPPY_VILLAGER, totem.anchor.clone().add(Math.cos(angle) * radius, 0.2,
                    Math.sin(angle) * radius), 1, 0, 0, 0, 0);
        }
        totem.angle += (float) (Math.PI / 2);
        totem.item.setInterpolationDelay(0);
        totem.item.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(totem.angle, 0, 1, 0),
                new Vector3f(0.9f, 0.9f, 0.9f), new AxisAngle4f()));
    }

    /** Tira todos os totens do Suporte (saiu, morreu, trocou de classe, onDisable). */
    private void removeAll(UUID owner, String message) {
        List<Totem> own = totems.get(owner);
        if (own == null) {
            return;
        }
        for (Totem totem : List.copyOf(own)) {
            remove(totem, message);
        }
    }

    /** Tira um totem, com a mensagem dada para o dono (null = sem mensagem). O item não é devolvido. */
    private void remove(Totem totem, String message) {
        List<Totem> own = totems.get(totem.owner);
        if (own == null || !own.remove(totem)) {
            return;
        }
        if (own.isEmpty()) {
            totems.remove(totem.owner);
            statuses.remove(totem.owner, STATUS);
        }
        totem.body.remove();
        totem.base.remove();
        totem.item.remove();
        World world = totem.anchor.getWorld();
        world.spawnParticle(Particle.CLOUD, totem.anchor.clone().add(0, 0.8, 0), 15, 0.3, 0.5, 0.3, 0.02);
        Player player = Bukkit.getPlayer(totem.owner);
        if (player != null && message != null) {
            player.sendMessage(message);
        }
    }

    private Totem ofBody(Entity entity) {
        UUID owner = relations.totemOwner(entity);
        List<Totem> own = owner == null ? null : totems.get(owner);
        if (own != null) {
            for (Totem totem : own) {
                if (totem.body.equals(entity)) {
                    return totem;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------ dano

    /** Aliados (e o dono) não danificam o totem: cancela logo, antes dos outros sistemas verem o golpe. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFriendlyHit(EntityDamageEvent event) {
        Totem totem = ofBody(event.getEntity());
        if (totem == null) {
            return;
        }
        Entity causer = event.getDamageSource().getCausingEntity();
        boolean explosion = event.getCause() == DamageCause.ENTITY_EXPLOSION
                || event.getCause() == DamageCause.BLOCK_EXPLOSION;
        if (causer instanceof Player player ? !relations.isEnemy(player, totem.body) : causer == null && !explosion) {
            event.setCancelled(true);
        }
    }

    /** Dano de inimigo: sai da vida do totem (o dano no corpo nunca acontece de verdade). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Totem totem = ofBody(event.getEntity());
        if (totem == null) {
            return;
        }
        double damage = event.getFinalDamage();
        event.setCancelled(true);
        if (damage <= 0) {
            return;
        }
        totem.health -= damage;
        Location at = totem.anchor.clone().add(0, 1, 0);
        at.getWorld().playSound(at, Sound.BLOCK_AMETHYST_BLOCK_HIT, 1f, 0.8f);
        at.getWorld().spawnParticle(Particle.BLOCK, at, 8, 0.25, 0.4, 0.25, 0, Material.EMERALD_BLOCK.createBlockData());
        if (totem.health > 0) {
            updateName(totem);
            return;
        }
        at.getWorld().playSound(at, Sound.BLOCK_GLASS_BREAK, 1f, 0.7f);
        at.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, at, 40, 0.3, 0.5, 0.3, 0.3);
        remove(totem, "§cSeu Totem de Cura foi destruído!");
    }

    // ------------------------------------------------------------ o item não salva da morte

    /** O Totem de Cura não é um Totem da Imortalidade: quem morre segurando ele morre (e o item não é gasto). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onResurrect(EntityResurrectEvent event) {
        EntityEquipment equipment = event.getEntity().getEquipment();
        if (event.isCancelled() || event.getHand() == null || equipment == null) {
            return;
        }
        if (items.read(equipment.getItem(event.getHand())) == SuporteItem.TOTEM) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (relations.totemOwner(event.getEntity()) != null) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (relations.totemOwner(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        if (relations.totemOwner(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (relations.totemOwner(event.getRightClicked()) != null) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ fim

    @EventHandler
    public void onOwnerDeath(PlayerDeathEvent event) {
        removeAll(event.getEntity().getUniqueId(), null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeAll(event.getPlayer().getUniqueId(), null);
    }

    @EventHandler
    public void onClassChange(PlayerClassChangeEvent event) {
        if (event.getOldClass() == PlayerClass.SUPORTE) {
            removeAll(event.getPlayerId(), null);
        }
    }

    private boolean owns(Entity entity) {
        for (List<Totem> own : totems.values()) {
            for (Totem totem : own) {
                if (totem.contains(entity)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void removeLeftovers() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (relations.totemOwner(entity) != null && !owns(entity)) {
                    entity.remove();
                }
            }
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (relations.totemOwner(entity) != null && !owns(entity)) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (entity.isValid() && !owns(entity)) {
                        entity.remove();
                    }
                });
            }
        }
    }
}
