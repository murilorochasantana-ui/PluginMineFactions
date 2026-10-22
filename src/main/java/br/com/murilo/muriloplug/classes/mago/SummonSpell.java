package br.com.murilo.muriloplug.classes.mago;

import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.classes.PlayerClassChangeEvent;
import br.com.murilo.muriloplug.combat.CombatManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vex;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Staff de Invocação: espíritos (Vex) que atacam o inimigo mais próximo do Mago. O golpe do espírito é cancelado e
 * refeito como dano mágico do próprio Mago, então combate, KDA e assistências funcionam como nas outras magias.
 * Os espíritos somem por tempo, morte, saída, troca de classe ou de mundo do Mago, e nunca dropam nem saem do mundo.
 */
final class SummonSpell implements Listener {

    /** Os espíritos escolhem alvo a cada 10 ticks. */
    private static final int THINK_PERIOD = 10;
    /** Mais longe que isto do Mago, o espírito é trazido de volta para perto dele. */
    private static final double LEASH = 24;

    private final JavaPlugin plugin;
    private final SpellTargets targets;
    private final MagoSettings settings;
    private final TempEntities temp;
    private final Predicate<UUID> isMage;
    private final Map<UUID, List<Vex>> spirits = new HashMap<>();
    private final Map<UUID, BukkitRunnable> tasks = new HashMap<>();
    /** Fim (ms) dos espíritos de cada Mago. */
    private final Map<UUID, Long> expiresAt = new HashMap<>();

    SummonSpell(JavaPlugin plugin, SpellTargets targets, MagoSettings settings, TempEntities temp,
                Predicate<UUID> isMage) {
        this.plugin = plugin;
        this.isMage = isMage;
        this.targets = targets;
        this.settings = settings;
        this.temp = temp;
    }

    /** Retorna false (sem gastar nada) se o Mago já tem o máximo de espíritos ou nenhum nasceu. */
    boolean cast(Player mage) {
        StaffType type = StaffType.INVOCACAO;
        UUID id = mage.getUniqueId();
        int max = Math.max(0, settings.staffInt(type, "maximo"));
        int wanted = Math.max(0, settings.staffInt(type, "quantidade"));
        List<Vex> alive = spirits.computeIfAbsent(id, k -> new ArrayList<>());
        alive.removeIf(vex -> !vex.isValid());
        int toSpawn = Math.min(wanted, max - alive.size());
        if (toSpawn <= 0) {
            return false;
        }

        // Um de cada lado do Mago, olhando para a mesma direção que ele.
        Location base = mage.getLocation().add(0, 1.2, 0);
        Vector right = base.getDirection().setY(0);
        right = right.lengthSquared() < 0.0001 ? new Vector(1, 0, 0)
                : new Vector(-right.getZ(), 0, right.getX()).normalize();
        for (int i = 0; i < toSpawn; i++) {
            double side = (i % 2 == 0 ? 1 : -1) * (1.5 + i / 2);
            Location at = base.clone().add(right.clone().multiply(side));
            Vex vex = mage.getWorld().spawn(at, Vex.class, spirit -> {
                temp.tag(spirit, id);
                spirit.setCanPickupItems(false);
                spirit.getEquipment().setItemInMainHandDropChance(0f);
                spirit.getEquipment().setItemInOffHandDropChance(0f);
            });
            if (vex.isValid()) {
                alive.add(vex);
                mage.getWorld().spawnParticle(Particle.SOUL, at, 12, 0.3, 0.3, 0.3, 0.05);
            }
        }
        if (alive.isEmpty()) {
            return false;
        }
        mage.getWorld().playSound(mage.getLocation(), Sound.ENTITY_VEX_CHARGE, 1f, 0.8f);

        expiresAt.put(id, System.currentTimeMillis() + Math.round(settings.staff(type, "segundos") * 1000));
        if (!tasks.containsKey(id)) {
            BukkitRunnable task = new BukkitRunnable() {
                @Override
                public void run() {
                    think(id);
                }
            };
            tasks.put(id, task);
            task.runTaskTimer(plugin, THINK_PERIOD, THINK_PERIOD);
        }
        return true;
    }

    private void think(UUID id) {
        Player mage = Bukkit.getPlayer(id);
        Long end = expiresAt.get(id);
        if (mage == null || !mage.isOnline() || mage.isDead() || end == null || end <= System.currentTimeMillis()
                || !isMage.test(id)) {
            removeAll(id);
            return;
        }
        List<Vex> alive = spirits.getOrDefault(id, List.of());
        alive.removeIf(vex -> !vex.isValid());
        if (alive.isEmpty()) {
            removeAll(id);
            return;
        }
        LivingEntity enemy = nearestEnemy(mage);
        for (Vex vex : alive) {
            if (!vex.getWorld().equals(mage.getWorld())) {
                removeAll(id); // espírito nunca acompanha o Mago para outro mundo
                return;
            }
            if (vex.getLocation().distanceSquared(mage.getLocation()) > LEASH * LEASH) {
                vex.teleport(mage.getLocation().add(0, 1.2, 0));
            }
            // Sem inimigo, o espírito fica voando em volta do Mago.
            vex.setBound(mage.getLocation());
            vex.setTarget(enemy);
        }
    }

    /** Inimigo mais próximo do Mago: jogador ou monstro, nunca o Mago, o clan ou os espíritos deles. */
    private LivingEntity nearestEnemy(Player mage) {
        double range = settings.staff(StaffType.INVOCACAO, "alcance");
        for (LivingEntity candidate : targets.nearby(mage, mage.getLocation(), range)) {
            if (!(candidate instanceof Player) && !(candidate instanceof Enemy)) {
                continue;
            }
            UUID owner = temp.isTagged(candidate) ? temp.ownerOf(candidate) : null;
            if (owner != null && targets.isAlly(mage, owner)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    /** Remove todos os espíritos do Mago (com uma fumaça) e para a contagem. */
    void removeAll(UUID id) {
        BukkitRunnable task = tasks.remove(id);
        if (task != null) {
            task.cancel();
        }
        expiresAt.remove(id);
        List<Vex> list = spirits.remove(id);
        if (list == null) {
            return;
        }
        for (Vex vex : list) {
            if (vex.isValid()) {
                vex.getWorld().spawnParticle(Particle.SOUL, vex.getLocation(), 10, 0.3, 0.3, 0.3, 0.05);
                vex.remove();
            }
        }
    }

    /** Desligamento do plugin: nenhum espírito fica para trás. */
    void removeEverything() {
        for (UUID id : new ArrayList<>(spirits.keySet())) {
            removeAll(id);
        }
    }

    // ------------------------------------------------------------ dano e proteções

    /** O golpe do espírito vira dano mágico do Mago (só em alvos que ele pode afetar). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpiritHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Vex vex) || !temp.isTagged(vex)) {
            return;
        }
        event.setCancelled(true);
        UUID owner = temp.ownerOf(vex);
        Player mage = owner == null ? null : Bukkit.getPlayer(owner);
        if (mage != null && mage.isOnline() && targets.canAffect(mage, event.getEntity())) {
            targets.damage(mage, (LivingEntity) event.getEntity(), settings.staff(StaffType.INVOCACAO, "dano"));
        }
    }

    /** O Mago e o clan dele não machucam os espíritos. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpiritHurt(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Vex vex) || !temp.isTagged(vex)) {
            return;
        }
        Player attacker = CombatManager.attackerOf(event);
        UUID owner = temp.ownerOf(vex);
        if (attacker != null && owner != null && targets.isAlly(attacker, owner)) {
            event.setCancelled(true);
        }
    }

    /** O espírito nunca escolhe como alvo quem ele não pode atacar (o Mago, o clan, jogadores não logados). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (event.getEntityType() != EntityType.VEX || !temp.isTagged(event.getEntity())
                || event.getTarget() == null) {
            return;
        }
        UUID owner = temp.ownerOf(event.getEntity());
        Player mage = owner == null ? null : Bukkit.getPlayer(owner);
        if (mage == null || !targets.canAffect(mage, event.getTarget())) {
            event.setCancelled(true);
        }
    }

    /** Sem drops (nem a espada) e sem experiência. */
    @EventHandler
    public void onSpiritDeath(EntityDeathEvent event) {
        if (temp.isTagged(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortal(EntityPortalEvent event) {
        if (temp.isTagged(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(EntityTeleportEvent event) {
        Entity entity = event.getEntity();
        if (temp.isTagged(entity) && event.getTo() != null && !event.getTo().getWorld().equals(entity.getWorld())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ ciclo do Mago

    @EventHandler
    public void onMageDeath(PlayerDeathEvent event) {
        removeAll(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onMageQuit(PlayerQuitEvent event) {
        removeAll(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onMageWorldChange(PlayerChangedWorldEvent event) {
        removeAll(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onClassChange(PlayerClassChangeEvent event) {
        if (event.getNewClass() != PlayerClass.MAGO) {
            removeAll(event.getPlayerId());
        }
    }
}
