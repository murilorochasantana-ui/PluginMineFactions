package br.com.murilo.muriloplug.classes.engenheiro;

import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Linhas do sidebar do Engenheiro (null para quem não é Engenheiro). Para uma habilidade nova (Golems, Gancho,
 * Sentinelas...), crie um método {@code Component xxxLine(Player)} aqui e ligue-o na lista do SidebarManager.
 */
public final class EngenheiroSidebar {

    private final ClassManager classes;
    private final EngenheiroEnergia energy;
    private final EngenheiroSettings settings;
    private final GanchoManager hook;
    private final SentinelaManager sentinels;

    public EngenheiroSidebar(ClassManager classes, EngenheiroEnergia energy, EngenheiroSettings settings,
                             GanchoManager hook, SentinelaManager sentinels) {
        this.hook = hook;
        this.sentinels = sentinels;
        this.classes = classes;
        this.energy = energy;
        this.settings = settings;
    }

    private boolean isEngineer(Player player) {
        return classes.hasClass(player.getUniqueId(), PlayerClass.ENGENHEIRO);
    }

    /** "Energia: 42/100". */
    public Component energyLine(Player player) {
        if (!isEngineer(player)) {
            return null;
        }
        UUID id = player.getUniqueId();
        return ShopUi.text("§bEnergia: §f" + (int) energy.getEnergia(id) + "/" + (int) energy.maxEnergia());
    }

    /** "Reparar item [Shift+Clique] ✔ (80)". */
    public Component repairLine(Player player) {
        if (!isEngineer(player)) {
            return null;
        }
        double cost = settings.repairCost();
        return ShopUi.text("§aReparar item §7[Shift+Clique] ")
                .append(mark(energy.temEnergia(player.getUniqueId(), cost)))
                .append(ShopUi.text(" §8(" + (int) cost + ")"));
    }

    /** "Gancho [Besta] ✔" (ou ✖ 2s durante a recarga). */
    public Component hookLine(Player player) {
        if (!isEngineer(player)) {
            return null;
        }
        int wait = hook.remainingSeconds(player.getUniqueId());
        Component line = ShopUi.text("§7Gancho §7[Besta] ").append(mark(wait == 0));
        return wait > 0 ? line.append(ShopUi.text(" §c" + wait + "s")) : line;
    }

    /** "Sentinelas 1/3". */
    public Component sentinelLine(Player player) {
        if (!isEngineer(player)) {
            return null;
        }
        return ShopUi.text("§cSentinelas §f" + sentinels.count(player.getUniqueId()) + "/" + settings.sentinelMax());
    }

    private static Component mark(boolean usable) {
        return usable ? Component.text("✔", NamedTextColor.GREEN) : Component.text("✖", NamedTextColor.RED);
    }
}
