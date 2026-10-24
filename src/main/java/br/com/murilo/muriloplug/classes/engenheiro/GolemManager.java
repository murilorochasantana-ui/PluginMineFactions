package br.com.murilo.muriloplug.classes.engenheiro;

import br.com.murilo.muriloplug.classes.SilenceManager;
import br.com.murilo.muriloplug.auth.AuthManager;
import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.classes.PlayerClassChangeEvent;
import br.com.murilo.muriloplug.combat.CombatManager;
import br.com.murilo.muriloplug.shop.ShopUi;
import br.com.murilo.muriloplug.teste.TargetRelations;
import br.com.murilo.muriloplug.teste.TestMode;
import com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Golems do Engenheiro: o Golem de Cobre (invocado com o item) e o Golem de Ferro (3 de cobre juntos com Shift + F).
 * A IA original é removida (MobGoals) e um relógio próprio os comanda: fora de combate seguem o dono; em combate
 * atacam o inimigo engajado mais próximo dele. O dano é do dono (causador) e o golem é a entidade direta, então
 * combate, KDA e assistências contam para o Engenheiro. Os golems não são salvos: somem quando o dono sai, morre,
 * deixa de ser Engenheiro ou troca de mundo.
 */
public final class GolemManager implements Listener {

    private static final long TICK_PERIOD = 2L;
    private static final double FOLLOW_START = 4.0;
    private static final double FOLLOW_STOP = 3.0;
    private static final double TELEPORT_DISTANCE = 20.0;
    private static final double TARGET_RANGE = 12.0;
    private static final double SPEED = 1.3;
    private static final double COPPER_REACH = 1.2;
    private static final double IRON_REACH = 1.8;
    private static final long ENGAGE_MILLIS = CombatManager.COMBAT_SECONDS * 1000L;
    private static final long SWAP_DEBOUNCE_MILLIS = 400L;
    private static final long RECALL_WARN_MILLIS = 2000L;
    private static final String COPPER = "cobre";
    private static final String IRON = "ferro";

    private final JavaPlugin plugin;
    private final ClassManager classes;
    private final AuthManager auth;
    private final TargetRelations relations;
    private final CombatManager combat;
    private final EngenheiroSettings settings;
    private final GolemItems items;
    private final EngenheiroEnergia energy;
    private final EngenheiroAcoes actions;
    private final NamespacedKey ownerKey;
    private final NamespacedKey typeKey;
    private final Map<UUID, List<Mob>> copper = new HashMap<>();
    private final Map<UUID, Mob> iron = new HashMap<>();
    /** Por dono: inimigo -> momento (ms) em que o engajamento acaba. */
    private final Map<UUID, Map<UUID, Long>> engaged = new HashMap<>();
    private final Map<UUID, Long> lastAttack = new HashMap<>();
    private final Map<UUID, String> lastName = new HashMap<>();
    private final Map<UUID, Long> lastSwap = new HashMap<>();
    private final Map<UUID, Long> lastRecallWarn = new HashMap<>();
    private Consumer<UUID> changeListener = id -> { };
    private TestMode test = TestMode.inactive();

    public GolemManager(JavaPlugin plugin, ClassManager classes, AuthManager auth, TargetRelations relations,
                        CombatManager combat, EngenheiroSettings settings, GolemItems items,
                        EngenheiroEnergia energy, EngenheiroAcoes actions) {
        this.plugin = plugin;
        this.classes = classes;
        this.auth = auth;
        this.relations = relations;
        this.combat = combat;
        this.settings = settings;
        this.items = items;
        this.energy = energy;
        this.actions = actions;
        this.ownerKey = new NamespacedKey(plugin, "golem_dono");
        this.typeKey = new NamespacedKey(plugin, "golem_tipo");
    }

    /** Remove sobras de uma execução anterior e liga o relógio dos golems. */
    public void start() {
        for (World world : Bukkit.getWorlds()) {
            removeMarked(world.getEntities());
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    /** Remove todos os golems (desligamento do plugin). */
    public void shutdown() {
        for (World world : Bukkit.getWorlds()) {
            removeMarked(world.getEntities());
        }
        copper.clear();
        iron.clear();
        engaged.clear();
        lastAttack.clear();
        lastName.clear();
    }

    /** Chamado (com o id do dono) quando a quantidade de golems dele muda, para atualizar o sidebar. */
    public void setChangeListener(Consumer<UUID> changeListener) {
        this.changeListener = changeListener;
    }

    public void setTestMode(TestMode test) {
        this.test = test;
    }

    // ------------------------------------------------------------ consulta

    /** Golems de Cobre vivos do jogador. */
    public int copperCount(UUID owner) {
        List<Mob> list = copper.get(owner);
        if (list == null) {
            return 0;
        }
        list.removeIf(golem -> !golem.isValid());
        return list.size();
    }

    public boolean hasIronGolem(UUID owner) {
        Mob golem = iron.get(owner);
        if (golem != null && !golem.isValid()) {
            iron.remove(owner);
            return false;
        }
        return golem != null;
    }

    /** Shift + F funcionaria agora: 3 golems de cobre, nenhum de ferro e energia. */
    public boolean canFuse(UUID owner) {
        return copperCount(owner) >= settings.golemMax() && !hasIronGolem(owner)
                && energy.temEnergia(owner, settings.ironGolemEnergyCost());
    }

    private UUID ownerOf(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isGolem(Entity entity) {
        return entity.getPersistentDataContainer().has(ownerKey, PersistentDataType.STRING);
    }

    private boolean isEngineer(Player player) {
        return classes.hasClass(player.getUniqueId(), PlayerClass.ENGENHEIRO);
    }

    // ------------------------------------------------------------ item: nunca vira bloco

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent event) {
        if (items.isGolemItem(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ itens antigos (bloco de cobre)

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        items.migrateInventory(event.getPlayer().getInventory());
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            items.migrateInventory(player.getInventory());
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player && items.isLegacyGolemItem(event.getItem().getItemStack())) {
            event.getItem().setItemStack(items.upgradeLegacy(event.getItem().getItemStack()));
        }
    }

    // ------------------------------------------------------------ invocar

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || !items.isGolemItem(item) || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!auth.isLoggedIn(id)) {
            return;
        }
        if (!isEngineer(player)) {
            player.sendMessage("§5Somente Engenheiros");
            return;
        }
        if (SilenceManager.blocks(player)) {
            return;
        }
        if (hasIronGolem(id)) {
            player.sendMessage("§cEnquanto o Golem de Ferro existir, não dá para invocar Golems de Cobre.");
            return;
        }
        int max = settings.golemMax();
        if (copperCount(id) >= max) {
            player.sendMessage("§cVocê já tem " + max + " Golems de Cobre.");
            return;
        }
        double cost = settings.golemEnergyCost();
        if (!energy.temEnergia(id, cost)) {
            player.sendMessage("§cEnergia insuficiente (" + (int) energy.getEnergia(id) + "/" + (int) cost + ").");
            return;
        }
        Block above = event.getClickedBlock().getRelative(org.bukkit.block.BlockFace.UP);
        if (!above.isPassable()) {
            player.sendMessage("§cSem espaço para o golem aqui.");
            return;
        }
        Location where = above.getLocation().add(0.5, 0, 0.5);
        CopperGolem golem = where.getWorld().spawn(where, CopperGolem.class, g -> prepare(g, player, COPPER,
                settings.golemHealth()));
        if (golem == null) {
            return;
        }
        golem.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        golem.getEquipment().setDropChance(EquipmentSlot.HAND, 0f);
        copper.computeIfAbsent(id, k -> new ArrayList<>()).add(golem);
        energy.gastarEnergia(id, cost);
        if (!test.isActive(id)) {
            item.setAmount(item.getAmount() - 1);
        }
        where.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, where.clone().add(0, 0.5, 0), 25, 0.3, 0.4, 0.3, 0.2);
        where.getWorld().playSound(where, Sound.BLOCK_COPPER_PLACE, 1f, 1f);
        player.sendMessage("§6Golem de Cobre invocado! §7(" + copperCount(id) + "/" + max + ")");
        actions.registrarAcao(player);
        changeListener.accept(id);
    }

    /** Configuração comum dos dois golems: marca, vida, nome, sem drop e sem a IA original. */
    private void prepare(Mob golem, Player owner, String type, double health) {
        golem.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        golem.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type);
        golem.setPersistent(false);
        golem.setRemoveWhenFarAway(false);
        golem.setCanPickupItems(false);
        golem.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
        golem.setHealth(health);
        golem.setCustomNameVisible(true);
        Bukkit.getMobGoals().removeAllGoals(golem);
        if (golem instanceof CopperGolem copperGolem) {
            copperGolem.setWeatheringState(io.papermc.paper.world.WeatheringCopperState.UNAFFECTED);
            copperGolem.setOxidizing(CopperGolem.Oxidizing.waxed()); // não oxida nem vira estátua
        }
        if (golem instanceof IronGolem ironGolem) {
            ironGolem.setPlayerCreated(true);
        }
        golem.customName(ShopUi.text(nameOf(golem, owner)));
    }

    private String nameOf(Mob golem, Player owner) {
        int life = (int) Math.ceil(golem.getHealth());
        return golem instanceof IronGolem ? "§fGolem de Ferro de " + owner.getName() + " §c❤ " + life
                : "§6Golem de " + owner.getName() + " §c❤ " + life;
    }

    // ------------------------------------------------------------ consertar

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractGolem(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        UUID owner = ownerOf(clicked);
        if (owner == null) {
            return;
        }
        event.setCancelled(true); // ninguém monta, entrega item, tosquia nem encera o golem
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || !player.getUniqueId().equals(owner)
                || !(clicked instanceof Mob golem) || !auth.isLoggedIn(owner)) {
            return;
        }
        if (SilenceManager.blocks(player)) {
            return;
        }
        if (golem instanceof IronGolem) {
            player.sendMessage("§cO Golem de Ferro não pode ser consertado.");
            return;
        }
        double max = golem.getAttribute(Attribute.MAX_HEALTH).getValue();
        if (golem.getHealth() >= max) {
            player.sendMessage("§eEste golem já está com a vida cheia.");
            return;
        }
        double cost = settings.golemRepairCost();
        if (!energy.temEnergia(owner, cost)) {
            player.sendMessage("§cEnergia insuficiente (" + (int) energy.getEnergia(owner) + "/" + (int) cost + ").");
            return;
        }
        energy.gastarEnergia(owner, cost);
        golem.setHealth(Math.min(max, golem.getHealth() + settings.golemRepairHealth()));
        golem.customName(ShopUi.text(nameOf(golem, player)));
        Location at = golem.getLocation().add(0, 0.6, 0);
        golem.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at, 20, 0.3, 0.3, 0.3, 0.2);
        golem.getWorld().playSound(at, Sound.ENTITY_IRON_GOLEM_REPAIR, 0.6f, 1.4f);
        actions.registrarAcao(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onShear(PlayerShearEntityEvent event) {
        if (isGolem(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ juntar (Shift + F)

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking() || !isEngineer(player)) {
            return;
        }
        event.setCancelled(true); // Shift + F do Engenheiro é a fusão: não troca o item de mão
        UUID id = player.getUniqueId();
        if (!auth.isLoggedIn(id) || SilenceManager.blocks(player)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastSwap.get(id);
        if (last != null && now - last < SWAP_DEBOUNCE_MILLIS) {
            return;
        }
        lastSwap.put(id, now);
        if (hasIronGolem(id)) {
            player.sendMessage("§cVocê já tem um Golem de Ferro.");
            return;
        }
        int needed = settings.golemMax();
        if (copperCount(id) < needed) {
            player.sendMessage("§cPrecisa de " + needed + " Golems de Cobre vivos (" + copperCount(id) + "/" + needed + ").");
            return;
        }
        double cost = settings.ironGolemEnergyCost();
        if (!energy.temEnergia(id, cost)) {
            player.sendMessage("§cEnergia insuficiente (" + (int) energy.getEnergia(id) + "/" + (int) cost + ").");
            return;
        }
        List<Mob> golems = new ArrayList<>(copper.get(id));
        Location middle = golems.get(golems.size() / 2).getLocation();
        World world = middle.getWorld();
        IronGolem big = world.spawn(middle, IronGolem.class, g -> prepare(g, player, IRON, settings.ironGolemHealth()));
        if (big == null) {
            return;
        }
        energy.gastarEnergia(id, cost);
        for (Mob golem : golems) {
            world.spawnParticle(Particle.PORTAL, golem.getLocation().add(0, 0.6, 0), 30, 0.3, 0.4, 0.3, 0.5);
            discard(golem);
        }
        copper.remove(id);
        iron.put(id, big);
        world.spawnParticle(Particle.EXPLOSION, middle.clone().add(0, 1, 0), 1);
        world.spawnParticle(Particle.ELECTRIC_SPARK, middle.clone().add(0, 1, 0), 60, 0.6, 0.8, 0.6, 0.3);
        world.playSound(middle, Sound.BLOCK_ANVIL_USE, 1f, 0.8f);
        world.playSound(middle, Sound.ENTITY_IRON_GOLEM_REPAIR, 1f, 0.7f);
        player.sendMessage("§fGolem de Ferro formado!");
        actions.registrarAcao(player);
        changeListener.accept(id);
    }

    // ------------------------------------------------------------ dano, combate e morte

    /** O dono não machuca o próprio golem: fora de combate o golpe corpo a corpo o recolhe. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHitOwnGolem(EntityDamageByEntityEvent event) {
        UUID owner = ownerOf(event.getEntity());
        Player attacker = CombatManager.attackerOf(event);
        if (owner == null || attacker == null || !attacker.getUniqueId().equals(owner)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof Mob golem)) {
            return; // projétil do dono: sem dano e sem recolher
        }
        if (combat.isInCombat(attacker)) {
            long now = System.currentTimeMillis();
            Long last = lastRecallWarn.get(owner);
            if (last == null || now - last >= RECALL_WARN_MILLIS) {
                lastRecallWarn.put(owner, now);
                attacker.sendMessage("§cVocê não pode recolher golems em combate.");
            }
            return;
        }
        recall(attacker, golem);
    }

    /** Remove o golem e devolve o item (Cobre: 1; Ferro: 3 Golems de Cobre). A energia gasta não volta. */
    private void recall(Player owner, Mob golem) {
        UUID id = owner.getUniqueId();
        int amount = 1;
        if (golem instanceof IronGolem) {
            amount = settings.golemMax();
            iron.remove(id);
        } else {
            List<Mob> list = copper.get(id);
            if (list != null) {
                list.remove(golem);
            }
        }
        Location at = golem.getLocation().add(0, 0.6, 0);
        golem.getWorld().spawnParticle(Particle.POOF, at, 15, 0.3, 0.4, 0.3, 0.02);
        golem.getWorld().playSound(at, Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.8f);
        discard(golem);
        owner.getInventory().addItem(items.create(amount)).values()
                .forEach(left -> owner.getWorld().dropItem(owner.getLocation(), left));
        owner.sendMessage("§aGolem recolhido.");
        changeListener.accept(id);
    }

    /** Empurrão reduzido nos golpes dos golems; o Golem de Ferro não lança o alvo para o alto. */
    @EventHandler(ignoreCancelled = true)
    public void onKnockback(EntityKnockbackByEntityEvent event) {
        Entity hitBy = event.getHitBy();
        if (!isGolem(hitBy)) {
            return;
        }
        double factor = hitBy instanceof IronGolem ? settings.ironGolemKnockback() : settings.golemKnockback();
        event.setKnockback(event.getKnockback().multiply(factor));
    }

    /** Registra quem está engajado com quem e põe o Engenheiro em combate quando o golem bate ou apanha. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        Player attacker = CombatManager.attackerOf(event);
        LivingEntity rawAttacker = event.getDamager() instanceof LivingEntity living ? living
                : event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter
                ? shooter : null;
        LivingEntity source = attacker != null ? attacker : rawAttacker;

        UUID victimOwner = ownerOf(victim);
        if (victimOwner != null) { // o golem apanhou
            Player owner = Bukkit.getPlayer(victimOwner);
            if (owner == null || source == null || source.equals(owner) || isGolem(source)) {
                return;
            }
            combat.engage(owner);
            if (relations.isEnemy(owner, source)) {
                engage(owner, source);
                if (attacker != null) {
                    combat.engage(owner, attacker);
                }
            }
            return;
        }
        if (attacker != null && event.getDamager() instanceof org.bukkit.entity.Golem && isGolem(event.getDamager())
                && !(victim instanceof Player)) { // o golem bateu em boneco ou mob
            combat.engage(attacker);
        }
        if (attacker != null && !attacker.equals(victim) && !isGolem(victim)) {
            engage(attacker, victim);
        }
        if (victim instanceof Player player && source != null && !isGolem(source) && !source.equals(player)) {
            engage(player, source);
        }
    }

    private void engage(Player owner, LivingEntity enemy) {
        if (!isEngineer(owner) || !relations.isEnemy(owner, enemy)) {
            return;
        }
        engaged.computeIfAbsent(owner.getUniqueId(), k -> new HashMap<>())
                .put(enemy.getUniqueId(), System.currentTimeMillis() + ENGAGE_MILLIS);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onGolemDeath(EntityDeathEvent event) {
        UUID owner = ownerOf(event.getEntity());
        if (owner == null) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        forget(event.getEntity().getUniqueId());
        Player player = Bukkit.getPlayer(owner);
        if (player != null) {
            player.sendMessage(event.getEntity() instanceof IronGolem ? "§cSeu Golem de Ferro foi destruído!"
                    : "§cUm dos seus Golems de Cobre foi destruído!");
        }
        // A lista só é limpa no próximo relógio (a entidade ainda é válida durante o evento): avisa o sidebar depois.
        plugin.getServer().getScheduler().runTask(plugin, () -> changeListener.accept(owner));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortal(EntityPortalEvent event) {
        if (isGolem(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ quando o dono some

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        removeAll(id);
        engaged.remove(id);
        lastSwap.remove(id);
        lastRecallWarn.remove(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        removeAll(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        removeAll(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onClassChange(PlayerClassChangeEvent event) {
        if (event.getOldClass() == PlayerClass.ENGENHEIRO) {
            removeAll(event.getPlayerId());
        }
    }

    /** Golem salvo por engano num chunk (queda do servidor, por exemplo): não deve existir sem dono ativo. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        removeMarked(event.getEntities());
    }

    private void removeMarked(List<Entity> entities) {
        for (Entity entity : List.copyOf(entities)) {
            if (isGolem(entity)) {
                entity.remove();
            }
        }
    }

    /** Remove todos os golems do jogador (o item não volta). */
    public void removeAll(UUID owner) {
        boolean had = false;
        List<Mob> list = copper.remove(owner);
        if (list != null) {
            list.forEach(this::discard);
            had = !list.isEmpty();
        }
        Mob big = iron.remove(owner);
        if (big != null) {
            discard(big);
            had = true;
        }
        if (had) {
            changeListener.accept(owner);
        }
    }

    private void discard(Mob golem) {
        if (golem.isValid()) {
            golem.getWorld().spawnParticle(Particle.POOF, golem.getLocation().add(0, 0.6, 0), 12, 0.3, 0.4, 0.3, 0.02);
            golem.remove();
        }
        forget(golem.getUniqueId());
    }

    private void forget(UUID golemId) {
        lastAttack.remove(golemId);
        lastName.remove(golemId);
    }

    // ------------------------------------------------------------ relógio da IA

    private void tick() {
        Set<UUID> owners = new HashSet<>(copper.keySet());
        owners.addAll(iron.keySet());
        for (UUID ownerId : owners) {
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner == null || owner.isDead() || !isEngineer(owner)) {
                removeAll(ownerId);
                continue;
            }
            List<Mob> units = new ArrayList<>();
            List<Mob> list = copper.get(ownerId);
            if (list != null) {
                list.removeIf(golem -> !golem.isValid());
                units.addAll(list);
            }
            Mob big = iron.get(ownerId);
            if (big != null && !big.isValid()) {
                iron.remove(ownerId);
                big = null;
            }
            if (big != null) {
                units.add(big);
            }
            if (units.isEmpty()) {
                continue;
            }
            LivingEntity target = combat.isInCombat(owner) ? pickTarget(owner) : null;
            for (Mob golem : units) {
                if (!golem.getWorld().equals(owner.getWorld())) {
                    discard(golem);
                    continue;
                }
                refreshName(golem, owner);
                if (target != null) {
                    fight(owner, golem, target);
                } else {
                    follow(owner, golem);
                }
            }
        }
    }

    private void refreshName(Mob golem, Player owner) {
        String name = nameOf(golem, owner);
        if (!name.equals(lastName.put(golem.getUniqueId(), name))) {
            golem.customName(ShopUi.text(name));
        }
    }

    /** Inimigo engajado mais próximo do Engenheiro, a até {@link #TARGET_RANGE} blocos. */
    private LivingEntity pickTarget(Player owner) {
        Map<UUID, Long> map = engaged.get(owner.getUniqueId());
        if (map == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        LivingEntity best = null;
        double bestDistance = TARGET_RANGE * TARGET_RANGE;
        Iterator<Map.Entry<UUID, Long>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entry.getValue() <= now || !(entity instanceof LivingEntity living) || living.isDead()
                    || !living.isValid()) {
                iterator.remove();
                continue;
            }
            if (!living.getWorld().equals(owner.getWorld()) || isGolem(living) || !relations.isEnemy(owner, living)) {
                continue;
            }
            if (living instanceof Player player && (!auth.isLoggedIn(player.getUniqueId())
                    || player.getGameMode() == GameMode.SPECTATOR)) {
                continue;
            }
            double distance = living.getLocation().distanceSquared(owner.getLocation());
            if (distance <= bestDistance) {
                best = living;
                bestDistance = distance;
            }
        }
        return best;
    }

    private void fight(Player owner, Mob golem, LivingEntity target) {
        boolean ironGolem = golem instanceof IronGolem;
        double reach = ironGolem ? IRON_REACH : COPPER_REACH;
        if (!golem.getBoundingBox().expand(reach).overlaps(target.getBoundingBox())) {
            golem.getPathfinder().moveTo(target, SPEED);
            return;
        }
        golem.getPathfinder().stopPathfinding();
        golem.lookAt(target);
        long now = System.currentTimeMillis();
        double seconds = ironGolem ? settings.ironGolemIntervalSeconds() : settings.golemIntervalSeconds();
        Long last = lastAttack.get(golem.getUniqueId());
        if (last != null && now - last < seconds * 1000L) {
            return;
        }
        lastAttack.put(golem.getUniqueId(), now);
        if (ironGolem) {
            golem.playEffect(EntityEffect.IRON_GOLEN_ATTACK);
        } else {
            golem.swingMainHand();
        }
        DamageSource source = DamageSource.builder(DamageType.MOB_ATTACK).withCausingEntity(owner)
                .withDirectEntity(golem).build();
        target.damage(ironGolem ? settings.ironGolemDamage() : settings.golemDamage(), source);
        combat.engage(owner);
    }

    private void follow(Player owner, Mob golem) {
        Location mine = golem.getLocation();
        Location theirs = owner.getLocation();
        double distance = mine.distance(theirs);
        if (distance > TELEPORT_DISTANCE) {
            golem.teleport(theirs);
            golem.getPathfinder().stopPathfinding();
        } else if (distance > FOLLOW_START) {
            golem.getPathfinder().moveTo(owner, SPEED);
        } else if (distance <= FOLLOW_STOP && golem.getPathfinder().hasPath()) {
            golem.getPathfinder().stopPathfinding();
        }
    }
}
