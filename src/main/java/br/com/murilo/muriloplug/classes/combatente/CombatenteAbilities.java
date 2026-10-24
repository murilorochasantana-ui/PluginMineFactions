package br.com.murilo.muriloplug.classes.combatente;

import br.com.murilo.muriloplug.classes.SilenceManager;
import br.com.murilo.muriloplug.auth.AuthManager;
import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.combat.CombatManager;
import br.com.murilo.muriloplug.shop.ShopUi;
import br.com.murilo.muriloplug.teste.StatusRegistry;
import br.com.murilo.muriloplug.teste.TargetRelations;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Habilidades do Combatente que gastam Fúria: Investida (F com espada ou machado), Provocar (Shift + clique direito
 * com espada ou machado) e Golpe Pulando (automático no golpe crítico). Também dá a Fúria dos golpes e do dano
 * levado, protege da queda depois da Investida e mostra a dica das teclas ao pegar espada ou machado.
 * "Inimigo" = jogador que não é do clan (e está logado) ou mob hostil.
 */
public final class CombatenteAbilities implements Listener {

    private static final long TAUNT_DEBOUNCE_MILLIS = 300L;
    /** Uma investida dura no máximo isto (ticks), mesmo sem alcançar o alvo. */
    private static final int DASH_MAX_TICKS = 15;
    private static final double ARRIVAL_DISTANCE = 2.0;
    private static final String TIP = "§7[F] Investida  §8|  §7[Shift+F] Berserker  §8|  "
            + "§7[Shift+Clique] Provocar  §8|  §7Pulo+golpe: Golpe Pulando";

    private final JavaPlugin plugin;
    private final ClassManager classes;
    private final CombatenteSettings settings;
    private final FuryManager fury;
    private final TargetRelations relations;
    private final StatusRegistry statuses;
    private final AuthManager auth;
    private final Map<UUID, Long> lastDash = new HashMap<>();
    private final Map<UUID, Long> lastTaunt = new HashMap<>();
    /** Fim (ms) da proteção contra queda depois da Investida. */
    private final Map<UUID, Long> fallProtectedUntil = new HashMap<>();
    /** Por provocado: Combatente -> fim (ms) do bônus de dano. */
    private final Map<UUID, Map<UUID, Long>> provoked = new HashMap<>();
    /** O golpe com bônus do Golpe Pulando que está sendo processado (a área sai no MONITOR, com o dano final). */
    private EntityDamageByEntityEvent pendingLeap;

    public CombatenteAbilities(JavaPlugin plugin, ClassManager classes, CombatenteSettings settings, FuryManager fury,
                               TargetRelations relations, AuthManager auth, StatusRegistry statuses) {
        this.plugin = plugin;
        this.classes = classes;
        this.settings = settings;
        this.fury = fury;
        this.relations = relations;
        this.statuses = statuses;
        this.auth = auth;
    }

    private boolean isCombatente(Player player) {
        return classes.hasClass(player.getUniqueId(), PlayerClass.COMBATENTE);
    }

    static boolean isMeleeWeapon(ItemStack item) {
        if (item == null) {
            return false;
        }
        String name = item.getType().name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE");
    }

    private boolean isEnemy(Player self, Entity entity) {
        if (entity.equals(self) || !(entity instanceof LivingEntity living) || entity instanceof ArmorStand
                || living.isDead() || !living.isValid() || !relations.isEnemy(self, living)) {
            return false;
        }
        if (entity instanceof Player player) {
            return auth.isLoggedIn(player.getUniqueId()) && player.getGameMode() != GameMode.SPECTATOR;
        }
        return relations.isDummy(entity) || entity instanceof Enemy;
    }

    private static Location center(Entity entity) {
        return entity.getBoundingBox().getCenter().toLocation(entity.getWorld());
    }

    // ------------------------------------------------------------ Fúria dos golpes, Golpe Pulando, Provocar

    /** Bônus que mexem no dano antes de ele ser aplicado: Provocar e Golpe Pulando. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDealt(EntityDamageByEntityEvent event) {
        if (fury.isAreaDamage()) {
            return;
        }
        Player attacker = CombatManager.attackerOf(event);
        if (attacker == null || !isCombatente(attacker)) {
            return;
        }
        UUID id = attacker.getUniqueId();
        Map<UUID, Long> marks = provoked.get(event.getEntity().getUniqueId());
        Long until = marks == null ? null : marks.get(id);
        if (until != null && until > System.currentTimeMillis()) {
            event.setDamage(event.getDamage() * (1 + settings.tauntBonus()));
        }
        // Golpe crítico (caindo, depois de pular) em um inimigo, com Fúria para pagar.
        if (event.getCause() == DamageCause.ENTITY_ATTACK && event.getDamager() == attacker && event.isCritical()
                && isEnemy(attacker, event.getEntity()) && !SilenceManager.isSilenced(attacker)
                && fury.spend(id, settings.leapCost())) {
            event.setDamage(event.getDamage() * (1 + settings.leapBonus()));
            pendingLeap = event;
        }
    }

    /** Com o dano final já conhecido: dá a Fúria e faz a área do Golpe Pulando. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDealtDone(EntityDamageByEntityEvent event) {
        if (fury.isAreaDamage()) {
            return;
        }
        Player attacker = CombatManager.attackerOf(event);
        double finalDamage = event.getFinalDamage();

        if (attacker != null && isCombatente(attacker)) {
            if (event == pendingLeap) {
                pendingLeap = null;
                if (event.getEntity() instanceof LivingEntity victim) {
                    leapArea(attacker, victim, finalDamage * settings.leapAreaFraction());
                }
            }
            if (event.getCause() == DamageCause.ENTITY_ATTACK && event.getDamager() == attacker && finalDamage > 0
                    && isEnemy(attacker, event.getEntity())) {
                fury.add(attacker.getUniqueId(), settings.furyPerHit());
            }
        }
        // Dano levado de outra entidade (golpe ou projétil).
        if (event.getEntity() instanceof Player victim && finalDamage > 0 && !victim.equals(attacker)
                && isCombatente(victim)) {
            fury.add(victim.getUniqueId(), settings.furyPerDamageTaken());
        }
    }

    /** Impacto no chão e metade do dano final em todos os OUTROS inimigos perto do alvo (conta como do Combatente). */
    private void leapArea(Player attacker, LivingEntity victim, double amount) {
        World world = victim.getWorld();
        Location ground = victim.getLocation();
        double radius = settings.leapRadius();
        world.spawnParticle(Particle.EXPLOSION, ground, 3, radius / 3, 0.1, radius / 3, 0);
        world.spawnParticle(Particle.CLOUD, ground, 30, radius / 2, 0.1, radius / 2, 0.1);
        world.playSound(ground, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.3f);
        world.playSound(ground, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.8f);
        if (amount <= 0) {
            return;
        }
        List<LivingEntity> hit = new ArrayList<>();
        for (Entity nearby : victim.getNearbyEntities(radius, radius, radius)) {
            if (!nearby.equals(victim) && isEnemy(attacker, nearby)
                    && center(nearby).distanceSquared(center(victim)) <= radius * radius) {
                hit.add((LivingEntity) nearby);
            }
        }
        DamageSource source = DamageSource.builder(DamageType.PLAYER_ATTACK)
                .withCausingEntity(attacker).withDirectEntity(attacker).build();
        fury.runAreaDamage(() -> {
            for (LivingEntity target : hit) {
                target.damage(amount, source);
            }
        });
    }

    // ------------------------------------------------------------ Investida

    /** F (sem agachar) com espada ou machado: Investida. Com outro item na mão, o F funciona normal. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking() || !isCombatente(player) || !isMeleeWeapon(player.getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);
        UUID id = player.getUniqueId();
        if (!auth.isLoggedIn(id) || SilenceManager.blocks(player)) {
            return;
        }
        if (fury.isBerserk(id)) {
            fury.warn(player, "§cInvestida indisponível durante o Berserker.");
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastDash.get(id);
        if (last != null && now - last < settings.dashIntervalMillis() && !fury.isTest(id)) {
            return;
        }
        lastDash.put(id, now);

        Location eye = player.getEyeLocation();
        RayTraceResult hit = player.getWorld().rayTrace(eye, eye.getDirection(), settings.dashRange(),
                FluidCollisionMode.NEVER, true, 0.4, entity -> isEnemy(player, entity));
        if (hit == null || !(hit.getHitEntity() instanceof LivingEntity target)) {
            return; // sem alvo válido (ou parede na frente): não faz nada e não gasta
        }
        double cost = settings.dashCost();
        if (fury.fury(id) < cost) {
            fury.warn(player, "§cFúria insuficiente (" + (int) fury.fury(id) + "/" + (int) cost + ")");
            return;
        }
        fury.spend(id, cost);
        dash(player, target);
    }

    private void dash(Player player, LivingEntity target) {
        UUID id = player.getUniqueId();
        double speed = settings.dashSpeed();
        fallProtectedUntil.put(id, System.currentTimeMillis() + DASH_MAX_TICKS * 50L + settings.dashFallProtectionMillis());
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.7f);

        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || !target.isValid() || elapsed++ >= DASH_MAX_TICKS
                        || !player.getWorld().equals(target.getWorld())) {
                    finish();
                    return;
                }
                Location from = center(player);
                Location to = center(target);
                Vector toTarget = to.toVector().subtract(from.toVector());
                double distance = toTarget.length();
                if (distance <= ARRIVAL_DISTANCE) {
                    arrive(toTarget);
                    return;
                }
                Vector direction = toTarget.clone().normalize();
                // Parede entre os dois: para (o impulso de velocidade já respeita as colisões do jogo).
                if (player.getWorld().rayTraceBlocks(from, direction, Math.min(distance, speed + 0.5),
                        FluidCollisionMode.NEVER, true) != null) {
                    finish();
                    return;
                }
                Vector velocity = direction.multiply(speed);
                velocity.setY(Math.max(-0.3, Math.min(0.4, velocity.getY())) + 0.05);
                player.setVelocity(velocity);
                player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 0.5, 0), 4, 0.2, 0.2, 0.2, 0.02);
            }

            /** Chegou ao lado do alvo: empurrão pequeno nele e o Combatente para. */
            private void arrive(Vector toTarget) {
                Vector push = toTarget.clone().setY(0);
                if (push.lengthSquared() > 0.0001) {
                    push.normalize().multiply(settings.dashPush()).setY(0.25);
                    target.setVelocity(push);
                }
                player.setVelocity(player.getVelocity().multiply(0.2));
                Location at = center(target);
                at.getWorld().spawnParticle(Particle.CRIT, at, 15, 0.3, 0.4, 0.3, 0.3);
                at.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.9f);
                finish();
            }

            private void finish() {
                cancel();
                // Até 3s sem dano de queda a partir do fim da investida.
                fallProtectedUntil.put(id, System.currentTimeMillis() + settings.dashFallProtectionMillis());
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Sem dano de queda logo depois da Investida. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != DamageCause.FALL || !(event.getEntity() instanceof Player player)) {
            return;
        }
        Long until = fallProtectedUntil.get(player.getUniqueId());
        if (until != null && until > System.currentTimeMillis()) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ Provocar

    /** Shift + clique direito com espada ou machado (no ar ou em bloco que não abre nada). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if ((action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK)
                || event.getHand() != EquipmentSlot.HAND || !isTauntClick(event.getPlayer())) {
            return;
        }
        if (event.getClickedBlock() != null && event.getClickedBlock().getType().isInteractable()) {
            return;
        }
        taunt(event.getPlayer());
    }

    /** Shift + clique direito em uma entidade. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() == EquipmentSlot.HAND && isTauntClick(event.getPlayer())) {
            taunt(event.getPlayer());
        }
    }

    private boolean isTauntClick(Player player) {
        return player.isSneaking() && isCombatente(player) && isMeleeWeapon(player.getInventory().getItemInMainHand());
    }

    private void taunt(Player player) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastTaunt.get(id);
        if ((last != null && now - last < TAUNT_DEBOUNCE_MILLIS) || !auth.isLoggedIn(id)
                || SilenceManager.blocks(player)) {
            return;
        }
        lastTaunt.put(id, now);
        provoked.values().removeIf(marks -> {
            marks.values().removeIf(end -> end <= now);
            return marks.isEmpty();
        });

        int wait = fury.tauntCooldownSeconds(id);
        if (wait > 0) {
            fury.warn(player, "§cProvocar em recarga: " + wait + "s");
            return;
        }
        double cost = settings.tauntCost();
        if (fury.fury(id) < cost) {
            fury.warn(player, "§cFúria insuficiente (" + (int) fury.fury(id) + "/" + (int) cost + ")");
            return;
        }
        double radius = settings.tauntRadius();
        List<LivingEntity> enemies = new ArrayList<>();
        for (Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
            if (isEnemy(player, nearby) && center(nearby).distanceSquared(center(player)) <= radius * radius) {
                enemies.add((LivingEntity) nearby);
            }
        }
        if (enemies.isEmpty()) {
            return; // ninguém por perto: não faz nada e não gasta
        }
        fury.spend(id, cost);
        fury.startTauntCooldown(id);
        int seconds = settings.tauntSeconds();
        long until = now + seconds * 1000L;
        for (LivingEntity enemy : enemies) {
            enemy.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, seconds * 20, 0));
            provoked.computeIfAbsent(enemy.getUniqueId(), k -> new HashMap<>()).put(id, until);
            statuses.add(enemy, "Provocado", seconds);
        }
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1f, 0.6f);
        world.spawnParticle(Particle.ANGRY_VILLAGER, player.getLocation().add(0, 1.5, 0), 12, 0.6, 0.3, 0.6, 0);
        fury.warn(player, "§6Provocar! §7(" + enemies.size() + " inimigo(s))");
    }

    /** Tira o Provocar deste inimigo (bônus de dano e brilho) na hora (Purificar do Suporte). */
    public void clearProvoked(LivingEntity enemy) {
        if (provoked.remove(enemy.getUniqueId()) != null) {
            enemy.removePotionEffect(PotionEffectType.GLOWING);
        }
    }

    // ------------------------------------------------------------ dica das teclas

    /** Ao passar a segurar espada ou machado, mostra a dica por ~3s (mensagens de habilidade têm prioridade). */
    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!isCombatente(player) || !auth.isLoggedIn(id) || fury.isBerserk(id) || fury.warnedRecently(id)
                || !isMeleeWeapon(player.getInventory().getItem(event.getNewSlot()))) {
            return;
        }
        player.sendActionBar(ShopUi.text(TIP));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastDash.remove(id);
        lastTaunt.remove(id);
        fallProtectedUntil.remove(id);
        provoked.remove(id);
        provoked.values().forEach(marks -> marks.remove(id));
    }
}
