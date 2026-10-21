package br.com.murilo.muriloplug.classes.arqueiro;

import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tecla F com o arco (alterna o modo de tiro), rajada de 3 flechas e travas da Flecha Explosiva
 * (só Arqueiro atira, sai sempre sozinha e sempre gasta 1 flecha).
 */
public final class ArcherBowListener implements Listener {

    private static final int EXTRA_ARROWS = 2;
    private static final int OFFHAND_SLOT = 40;
    private static final int STORAGE_SIZE = 36;

    private final JavaPlugin plugin;
    private final ClassManager classes;
    private final ArqueiroSettings settings;
    private final ShotModes modes;
    private final ArrowItems arrows;
    private final Map<UUID, Burst> bursts = new HashMap<>();

    public ArcherBowListener(JavaPlugin plugin, ClassManager classes, ArqueiroSettings settings,
                             ShotModes modes, ArrowItems arrows) {
        this.plugin = plugin;
        this.classes = classes;
        this.settings = settings;
        this.modes = modes;
        this.arrows = arrows;
    }

    private boolean isArcher(Player player) {
        return classes.hasClass(player.getUniqueId(), PlayerClass.ARQUEIRO);
    }

    private static void actionBar(Player player, String legacy) {
        player.sendActionBar(ShopUi.text(legacy));
    }

    /** Tecla F com o arco na mão principal: alterna o modo e não troca o arco de mão. */
    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!isArcher(player) || !settings.isArcherBow(player.getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);
        boolean burst = modes.toggle(player.getUniqueId());
        actionBar(player, burst ? "§6Modo: rajada de 3" : "§aModo: 1 flecha");
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, burst ? 1.4f : 1f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack ammo = event.getConsumable();
        boolean archer = isArcher(player);

        if (arrows.isExplosive(ammo)) {
            shootExplosive(event, player, ammo, archer);
            return;
        }
        if (!archer || !settings.isArcherBow(event.getBow()) || !modes.isBurst(player.getUniqueId())
                || !(event.getProjectile() instanceof AbstractArrow original)
                || !(original instanceof Arrow || original instanceof SpectralArrow)) {
            return;
        }
        int wait = modes.cooldownRemainingSeconds(player.getUniqueId());
        if (wait > 0) {
            actionBar(player, "§cRajada em recarga: " + wait + "s");
            return;
        }
        modes.startCooldown(player.getUniqueId(), settings.burstCooldownSeconds());
        startBurst(player, event.getBow(), original, ammo);
    }

    private void shootExplosive(EntityShootBowEvent event, Player player, ItemStack ammo, boolean archer) {
        if (!archer) {
            event.setCancelled(true);
            player.sendMessage("§cSomente Arqueiros podem usar a Flecha Explosiva.");
            player.updateInventory();
            return;
        }
        if (event.getProjectile() instanceof AbstractArrow arrow) {
            arrows.markProjectile(arrow.getPersistentDataContainer());
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        }
        // Com Infinidade o jogo não gasta a flecha; a explosiva sempre gasta (no criativo nada é gasto).
        if (event.getBow() != null && event.getBow().containsEnchantment(Enchantment.INFINITY)
                && player.getGameMode() != GameMode.CREATIVE) {
            takeOne(player.getInventory(), ammo);
        }
    }

    private void startBurst(Player player, ItemStack bow, AbstractArrow original, ItemStack ammo) {
        UUID id = player.getUniqueId();
        ItemStack template = ammo == null ? new ItemStack(Material.ARROW) : ammo.asOne();
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        boolean infinite = bow.containsEnchantment(Enchantment.INFINITY) && template.getType() == Material.ARROW;
        Burst previous = bursts.remove(id);
        if (previous != null) {
            previous.cancel();
        }
        Burst burst = new Burst(player, bow.clone(), original, template, creative, infinite);
        bursts.put(id, burst);
        int interval = settings.burstIntervalTicks();
        burst.runTaskTimer(plugin, interval, interval);
    }

    /** As flechas extras da rajada, uma a cada intervalo. */
    private final class Burst extends BukkitRunnable {

        private final Player player;
        private final ItemStack bow;
        private final ItemStack template;
        private final boolean creative;
        private final boolean infinite;
        private final int slot;
        private final double speed;
        private final boolean spectral;
        private final boolean critical;
        private final double damage;
        private final int pierce;
        private final ItemStack pickupItem;
        private final PotionType potionType;
        private final List<PotionEffect> effects;
        private final Color color;
        private int remaining = EXTRA_ARROWS;

        Burst(Player player, ItemStack bow, AbstractArrow original, ItemStack template, boolean creative, boolean infinite) {
            this.player = player;
            this.bow = bow;
            this.template = template;
            this.creative = creative;
            this.infinite = infinite;
            this.slot = player.getInventory().getHeldItemSlot();
            this.speed = original.getVelocity().length();
            this.spectral = original instanceof SpectralArrow;
            this.critical = original.isCritical();
            this.damage = original.getDamage();
            this.pierce = original.getPierceLevel();
            this.pickupItem = original.getItemStack();
            Arrow tipped = original instanceof Arrow a ? a : null;
            this.potionType = tipped == null ? null : tipped.getBasePotionType();
            this.effects = tipped == null ? List.of() : List.copyOf(tipped.getCustomEffects());
            this.color = tipped == null ? null : tipped.getColor();
        }

        @Override
        public void run() {
            PlayerInventory inventory = player.getInventory();
            if (!player.isOnline() || player.isDead() || inventory.getHeldItemSlot() != slot
                    || !settings.isArcherBow(inventory.getItemInMainHand()) || !isArcher(player)) {
                finish();
                return;
            }
            // Anti-duplicação: só é recolhível do chão se uma flecha real foi gasta para ela.
            boolean spent = false;
            if (!creative) {
                if (findArrow(inventory, template) < 0) {
                    finish();
                    return;
                }
                if (!infinite) {
                    spent = takeOne(inventory, template);
                }
            }
            launchExtra(spent);
            if (--remaining <= 0) {
                finish();
            }
        }

        private void launchExtra(boolean spent) {
            Vector velocity = player.getEyeLocation().getDirection().multiply(speed);
            Class<? extends AbstractArrow> type = spectral ? SpectralArrow.class : Arrow.class;
            AbstractArrow extra = player.launchProjectile(type, velocity);
            extra.setCritical(critical);
            extra.setDamage(damage);
            extra.setPierceLevel(pierce);
            // Força e Impacto são lidos do arco que disparou a flecha; Chama é aplicada na hora.
            extra.setWeapon(bow.clone());
            if (pickupItem != null) {
                extra.setItemStack(pickupItem.clone());
            }
            if (bow.containsEnchantment(Enchantment.FLAME)) {
                extra.setFireTicks(100);
            }
            extra.setPickupStatus(spent ? AbstractArrow.PickupStatus.ALLOWED : AbstractArrow.PickupStatus.CREATIVE_ONLY);
            copyPotion(extra);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 1.2f);
        }

        private void copyPotion(AbstractArrow extra) {
            if (!(extra instanceof Arrow arrow)) {
                return;
            }
            if (potionType != null) {
                arrow.setBasePotionType(potionType);
            }
            for (PotionEffect effect : effects) {
                arrow.addCustomEffect(effect, true);
            }
            if (color != null) {
                arrow.setColor(color);
            }
        }

        private void finish() {
            bursts.remove(player.getUniqueId(), this);
            cancel();
        }
    }

    /** Primeira posição do inventário (mão secundária primeiro, como o jogo) com uma flecha igual ao modelo. */
    private static int findArrow(PlayerInventory inventory, ItemStack template) {
        if (template.isSimilar(inventory.getItem(OFFHAND_SLOT))) {
            return OFFHAND_SLOT;
        }
        for (int i = 0; i < STORAGE_SIZE; i++) {
            if (template.isSimilar(inventory.getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    /** Gasta 1 flecha igual ao modelo. Retorna false se não havia. */
    private static boolean takeOne(PlayerInventory inventory, ItemStack template) {
        if (template == null) {
            return false;
        }
        int found = findArrow(inventory, template);
        if (found < 0) {
            return false;
        }
        ItemStack stack = inventory.getItem(found);
        if (stack.getAmount() <= 1) {
            inventory.setItem(found, null);
        } else {
            stack.setAmount(stack.getAmount() - 1);
            inventory.setItem(found, stack);
        }
        return true;
    }

    private void cancelBurst(UUID id) {
        Burst burst = bursts.remove(id);
        if (burst != null) {
            burst.cancel();
        }
    }

    public void cancelAll() {
        bursts.values().forEach(Burst::cancel);
        bursts.clear();
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        cancelBurst(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        cancelBurst(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelBurst(event.getPlayer().getUniqueId());
    }
}
