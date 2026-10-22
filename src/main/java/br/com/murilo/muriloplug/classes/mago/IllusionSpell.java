package br.com.murilo.muriloplug.classes.mago;

import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.classes.PlayerClassChangeEvent;
import br.com.murilo.muriloplug.combat.CombatManager;
import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Staff de Ilusão: o Mago some (hidePlayer) para quem não é do clan dele e deixa cópias falsas, ArmorStands com a
 * cabeça dele e armadura de couro (o Mannequin só existe em versões mais novas que a 1.21.4). Atacar ou lançar outra
 * magia desfaz a invisibilidade na hora.
 */
final class IllusionSpell implements Listener {

    private final JavaPlugin plugin;
    private final SpellTargets targets;
    private final MagoSettings settings;
    private final TempEntities temp;
    /** Magos invisíveis agora e o fim da invisibilidade de cada um. */
    private final Map<UUID, BukkitRunnable> active = new HashMap<>();

    IllusionSpell(JavaPlugin plugin, SpellTargets targets, MagoSettings settings, TempEntities temp) {
        this.plugin = plugin;
        this.targets = targets;
        this.settings = settings;
        this.temp = temp;
    }

    boolean cast(Player mage) {
        StaffType type = StaffType.ILUSAO;
        UUID id = mage.getUniqueId();
        long invisibleTicks = Math.max(1, Math.round(settings.staff(type, "invisivel-segundos") * 20));
        int copies = Math.max(0, (int) Math.round(settings.staff(type, "copias")));
        long copyTicks = Math.max(1, Math.round(settings.staff(type, "copias-segundos") * 20));

        end(id);
        // As cópias nascem onde o Mago estava, antes de ele sumir.
        spawnClones(mage, copies, copyTicks);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            hideFrom(viewer, mage);
        }
        BukkitRunnable timer = new BukkitRunnable() {
            @Override
            public void run() {
                end(id);
            }
        };
        timer.runTaskLater(plugin, invisibleTicks);
        active.put(id, timer);

        mage.getWorld().spawnParticle(Particle.POOF, mage.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.05);
        mage.getWorld().playSound(mage.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1f);
        return true;
    }

    // ------------------------------------------------------------ invisibilidade

    boolean isActive(UUID mage) {
        return active.containsKey(mage);
    }

    private void hideFrom(Player viewer, Player mage) {
        if (!viewer.equals(mage) && !targets.isAlly(mage, viewer.getUniqueId())) {
            viewer.hidePlayer(plugin, mage);
        }
    }

    /** Termina a invisibilidade (se havia) e mostra o Mago para todos. Retorna true se havia. */
    private boolean end(UUID id) {
        BukkitRunnable timer = active.remove(id);
        if (timer == null) {
            return false;
        }
        timer.cancel();
        Player mage = Bukkit.getPlayer(id);
        if (mage != null) {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                viewer.showPlayer(plugin, mage);
            }
        }
        return true;
    }

    /** O Mago atacou ou lançou magia: a invisibilidade acaba na hora. */
    void breakIllusion(Player mage) {
        if (end(mage.getUniqueId())) {
            mage.getWorld().playSound(mage.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.8f, 1.6f);
            mage.sendActionBar(ShopUi.text("§dIlusão desfeita."));
        }
    }

    /** A Luz revelou o Mago. */
    void reveal(Player mage) {
        if (end(mage.getUniqueId())) {
            mage.sendActionBar(ShopUi.text("§eVocê foi revelado pela luz!"));
        }
    }

    /** Desligamento do plugin: todos voltam a ser vistos e as cópias somem. */
    void endAll() {
        for (UUID id : new ArrayList<>(active.keySet())) {
            end(id);
        }
    }

    // ------------------------------------------------------------ cópias

    private void spawnClones(Player mage, int copies, long copyTicks) {
        Location origin = mage.getLocation();
        List<ArmorStand> clones = new ArrayList<>();
        for (int i = 0; i < copies; i++) {
            double angle = Math.PI * 2 * i / copies;
            Location at = origin.clone().add(Math.cos(angle) * 1.3, 0, Math.sin(angle) * 1.3);
            at.setYaw(origin.getYaw() + (i - copies / 2f) * 25f);
            at.setPitch(0);
            ArmorStand clone = mage.getWorld().spawn(at, ArmorStand.class, stand -> {
                temp.tag(stand, mage.getUniqueId());
                stand.setArms(true);
                stand.setBasePlate(false);
                stand.setCanPickupItems(false);
                stand.addDisabledSlots(EquipmentSlot.values());
                stand.getEquipment().setHelmet(head(mage));
                stand.getEquipment().setChestplate(leather(Material.LEATHER_CHESTPLATE));
                stand.getEquipment().setLeggings(leather(Material.LEATHER_LEGGINGS));
                stand.getEquipment().setBoots(leather(Material.LEATHER_BOOTS));
            });
            if (clone.isValid()) {
                clones.add(clone);
            }
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                for (ArmorStand clone : clones) {
                    if (clone.isValid()) {
                        clone.remove();
                    }
                }
            }
        }.runTaskLater(plugin, copyTicks);
    }

    private static ItemStack head(Player mage) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        meta.setPlayerProfile(mage.getPlayerProfile());
        skull.setItemMeta(meta);
        return skull;
    }

    private static ItemStack leather(Material material) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(Color.fromRGB(110, 60, 160));
        item.setItemMeta(meta);
        return item;
    }

    private void pop(ArmorStand clone) {
        clone.getWorld().spawnParticle(Particle.POOF, clone.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05);
        clone.getWorld().playSound(clone.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.6f, 1.8f);
        clone.remove();
    }

    // ------------------------------------------------------------ eventos

    /** Qualquer dano numa cópia a desfaz: nada cai e nada é quebrado. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCloneHit(EntityDamageEvent event) {
        if (event.getEntity() instanceof ArmorStand stand && temp.isTagged(stand)) {
            event.setCancelled(true);
            pop(stand);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCloneManipulate(PlayerArmorStandManipulateEvent event) {
        if (temp.isTagged(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    /** Atacar (golpe ou projétil) desfaz a ilusão, mesmo que o golpe tenha sido cancelado. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAttack(EntityDamageByEntityEvent event) {
        Player attacker = CombatManager.attackerOf(event);
        if (attacker != null && !attacker.equals(event.getEntity()) && isActive(attacker.getUniqueId())) {
            breakIllusion(attacker);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        for (UUID id : active.keySet()) {
            Player mage = Bukkit.getPlayer(id);
            if (mage != null) {
                hideFrom(event.getPlayer(), mage);
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        end(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        end(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onClassChange(PlayerClassChangeEvent event) {
        if (event.getNewClass() != PlayerClass.MAGO) {
            end(event.getPlayerId());
        }
    }
}
