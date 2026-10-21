package br.com.murilo.muriloplug.auth;

import br.com.murilo.muriloplug.combat.CombatManager;
import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.entity.HumanEntity;

import java.util.Locale;
import java.util.Set;

/**
 * Enquanto o jogador NÃO está logado, bloqueia tudo. Todos os handlers rodam em LOWEST, antes dos outros sistemas
 * do plugin: o dano cancelado aqui nem chega ao combate nem ao KDA (que só olham o dano em MONITOR e ignoram o
 * cancelado).
 */
public final class AuthRestrictionsListener implements Listener {

    /** Únicos comandos liberados antes do login. */
    private static final Set<String> ALLOWED_COMMANDS = Set.of("register", "registrar", "login", "logar");

    private final AuthManager auth;

    public AuthRestrictionsListener(AuthManager auth) {
        this.auth = auth;
    }

    private boolean notLoggedIn(HumanEntity entity) {
        return entity instanceof Player player && !auth.isLoggedIn(player.getUniqueId());
    }

    private void deny(HumanEntity entity, Cancellable event) {
        if (notLoggedIn(entity)) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ movimento, chat e comandos

    /** Não anda (X/Y/Z), mas pode girar a câmera. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || !notLoggedIn(event.getPlayer())) {
            return;
        }
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            Location stay = from.clone();
            stay.setYaw(to.getYaw());
            stay.setPitch(to.getPitch());
            event.setTo(stay);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        if (notLoggedIn(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cFaça login para falar no chat.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!notLoggedIn(event.getPlayer())) {
            return;
        }
        String message = event.getMessage().trim();
        String label = message.startsWith("/") ? message.substring(1) : message;
        int space = label.indexOf(' ');
        if (space >= 0) {
            label = label.substring(0, space);
        }
        if (!ALLOWED_COMMANDS.contains(label.toLowerCase(Locale.ROOT))) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cFaça login primeiro: /login <senha> ou /register <senha> <senha>.");
        }
    }

    /** Não vaza nomes de comandos pelo tab-complete antes do login. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onTabComplete(AsyncTabCompleteEvent event) {
        if (event.getSender() instanceof Player player && notLoggedIn(player)) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ mundo e itens

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        deny(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        deny(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        deny(event.getPlayer(), event);
    }

    /** Inclui interagir "em" entidades (PlayerInteractAtEntityEvent é filho deste evento). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        deny(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        deny(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBucketFill(PlayerBucketFillEvent event) {
        deny(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        deny(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(EntityPickupItemEvent event) {
        deny(event.getEntity() instanceof Player player ? player : null, event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        deny(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHeldItem(PlayerItemHeldEvent event) {
        deny(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        deny(event.getPlayer(), event);
    }

    // ------------------------------------------------------------ inventários

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        deny(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        deny(event.getWhoClicked(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        deny(event.getWhoClicked(), event);
    }

    // ------------------------------------------------------------ dano

    /** Quem não está logado não leva dano... */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        deny(event.getEntity() instanceof Player player ? player : null, event);
    }

    /** ...e não causa dano (golpe direto ou projétil). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageDealt(EntityDamageByEntityEvent event) {
        deny(CombatManager.attackerOf(event), event);
    }

    /** Mobs não escolhem quem não está logado como alvo. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onTarget(EntityTargetEvent event) {
        deny(event.getTarget() instanceof Player player ? player : null, event);
    }
}
