package br.com.murilo.muriloplug.classes.mago;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.List;

/** Staff de Raio: acerta o primeiro alvo na mira (sem atravessar blocos) e pula em cadeia para os mais próximos. */
final class LightningSpell {

    private final SpellTargets targets;
    private final MagoSettings settings;
    private final MagoCombos combos;

    LightningSpell(SpellTargets targets, MagoSettings settings, MagoCombos combos) {
        this.targets = targets;
        this.settings = settings;
        this.combos = combos;
    }

    /** Retorna false (sem gastar nada) se não houver alvo na mira. */
    boolean cast(Player mage) {
        StaffType type = StaffType.RAIO;
        double range = settings.staff(type, "alcance");
        double damage = settings.staff(type, "dano");
        int jumps = settings.staffInt(type, "saltos");
        double jumpDistance = settings.staff(type, "distancia-salto");

        World world = mage.getWorld();
        Location eye = mage.getEyeLocation();
        // O rastreio para no primeiro bloco sólido ou alvo: parede na frente = nenhum alvo.
        RayTraceResult hit = world.rayTrace(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true, 0.4,
                entity -> targets.canAffect(mage, entity));
        if (hit == null || !(hit.getHitEntity() instanceof LivingEntity first)) {
            return false;
        }

        // Condutor (Gelo -> Raio): primeiro alvo marcado pelo Gelo = mais saltos e mais longe.
        if (combos.conductor(mage, first)) {
            jumps = Math.max(jumps, combos.conductorJumps());
            jumpDistance = Math.max(jumpDistance, combos.conductorDistance());
        }

        List<LivingEntity> chain = new ArrayList<>();
        chain.add(first);
        LivingEntity current = first;
        for (int i = 0; i < jumps; i++) {
            Location from = SpellTargets.bodyCenter(current);
            LivingEntity next = null;
            for (LivingEntity candidate : targets.nearby(mage, from, jumpDistance)) {
                if (!chain.contains(candidate) && SpellTargets.clearBetween(from, SpellTargets.bodyCenter(candidate))) {
                    next = candidate;
                    break; // a lista já vem do mais perto para o mais longe
                }
            }
            if (next == null) {
                break;
            }
            chain.add(next);
            current = next;
        }

        Location previous = eye.clone().subtract(0, 0.3, 0);
        for (LivingEntity target : chain) {
            Location center = SpellTargets.bodyCenter(target);
            SpellTargets.line(world, previous, center, Particle.ELECTRIC_SPARK);
            world.strikeLightningEffect(target.getLocation());
            targets.damage(mage, target, damage);
            previous = center;
        }
        return true;
    }
}
