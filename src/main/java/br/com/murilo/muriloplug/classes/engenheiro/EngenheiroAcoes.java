package br.com.murilo.muriloplug.classes.engenheiro;

import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Ações do Engenheiro (reparar item, invocar golem, consertar golem, juntar golems, gancho, sentinela, colocar
 * estrutura). Cada uma chama {@link #registrarAcao} depois de acontecer: com armadura de ferro, há chance de choque.
 */
public final class EngenheiroAcoes {

    private final ClassManager classes;
    private final EngenheiroSettings settings;

    public EngenheiroAcoes(ClassManager classes, EngenheiroSettings settings) {
        this.classes = classes;
        this.settings = settings;
    }

    public static boolean isIron(Material material) {
        return material == Material.IRON_HELMET || material == Material.IRON_CHESTPLATE
                || material == Material.IRON_LEGGINGS || material == Material.IRON_BOOTS;
    }

    /** Peças de ferro vestidas (0 a 4). */
    public static int ironPieces(Player player) {
        int count = 0;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece != null && isIron(piece.getType())) {
                count++;
            }
        }
        return count;
    }

    /** Registra uma ação do Engenheiro: 15% por peça de ferro (até 60%) de levar um choque que ignora armadura. */
    public void registrarAcao(Player player) {
        if (!classes.hasClass(player.getUniqueId(), PlayerClass.ENGENHEIRO) || player.isDead()) {
            return;
        }
        int pieces = ironPieces(player);
        if (pieces == 0 || ThreadLocalRandom.current().nextDouble() >= pieces * settings.ironChancePerPiece()) {
            return;
        }
        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation().add(0, 1, 0), 40, 0.4, 0.7, 0.4, 0.3);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.4f, 1.6f);
        player.sendMessage("§e⚡ Sua armadura de ferro deu curto-circuito!");
        // Dano mágico: o jogo não aplica os pontos de armadura a ele.
        player.damage(settings.ironShockDamage(), DamageSource.builder(DamageType.MAGIC).build());
    }
}
