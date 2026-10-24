package br.com.murilo.muriloplug.classes.suporte;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

/** Leitura (sempre ao vivo) das opções {@code suporte.*} do config.yml, com os padrões num lugar só. */
public final class SuporteSettings {

    public static final String EXTRA_HEALTH = "suporte.vida-extra";
    public static final String WEAPON_PENALTY = "suporte.penalidade-arma";
    public static final String HARMONY_MAX = "suporte.harmonia.maxima";
    public static final String HARMONY_PER_HEAL = "suporte.harmonia.por-cura";
    public static final String HARMONY_PER_LINK = "suporte.harmonia.por-vinculo";
    public static final String HARMONY_PER_ASSIST = "suporte.harmonia.por-assistencia";
    public static final String HARMONY_REGEN = "suporte.harmonia.regen";
    public static final String HARMONY_REGEN_SECONDS = "suporte.harmonia.regen-intervalo-segundos";
    public static final String LINK_PERCENT = "suporte.vinculo.porcentagem";
    public static final String LINK_STOP_HEALTH = "suporte.vinculo.parar-com-vida";
    public static final String LINK_MAX_DISTANCE = "suporte.vinculo.distancia-maxima";
    public static final String LINK_RANGE = "suporte.vinculo.alcance";
    public static final String LINK_RESISTANCE = "suporte.vinculo.resistencia";
    public static final String LINK_SHARED_HEAL = "suporte.vinculo.cura-compartilhada";
    public static final String ROOT_SECONDS = "suporte.prender.segundos";
    public static final String ROOT_RANGE = "suporte.prender.alcance";
    public static final String ROOT_COOLDOWN = "suporte.prender.recarga";
    public static final String ROOT_COST = "suporte.prender.custo";
    public static final String HEAL_AMOUNT = "suporte.cura.quantidade";
    public static final String HEAL_RADIUS = "suporte.cura.raio";
    public static final String HEAL_COOLDOWN = "suporte.cura.recarga";
    public static final String HEAL_COST = "suporte.cura.custo";
    public static final String SCROLL_LEVEL = "suporte.pergaminho.nivel";
    public static final String SCROLL_SECONDS = "suporte.pergaminho.segundos";
    public static final String SCROLL_RADIUS = "suporte.pergaminho.raio";
    public static final String SCROLL_COOLDOWN = "suporte.pergaminho.recarga";
    public static final String SCROLL_COST = "suporte.pergaminho.custo";
    public static final String FLIGHT_SECONDS = "suporte.voo.segundos";
    public static final String FLIGHT_COOLDOWN = "suporte.voo.recarga";
    public static final String FLIGHT_COST = "suporte.voo.custo";
    public static final String FALL_PROTECTION = "suporte.voo.protecao-queda-segundos";
    public static final String FLIGHT_SPEED = "suporte.voo.velocidade";
    public static final String FLIGHT_MAX_SPEED = "suporte.voo.velocidade-maxima";
    public static final String FLIGHT_MAX_HEIGHT = "suporte.voo.altura-maxima";
    public static final String SWAP_ALLY_HEALTH = "suporte.troca.vida-aliado";
    public static final String SWAP_MAX_HEALTH = "suporte.troca.vida-maxima";
    public static final String SWAP_SECONDS = "suporte.troca.segundos";
    public static final String SWAP_COOLDOWN = "suporte.troca.recarga-segundos";
    public static final String DRAIN_SECONDS = "suporte.elo.segundos";
    public static final String DRAIN_PERCENT = "suporte.elo.porcentagem";
    public static final String DRAIN_COST = "suporte.elo.custo";
    public static final String DRAIN_COOLDOWN = "suporte.elo.recarga";
    public static final String PLACE_DISTANCE = "suporte.troca-lugar.distancia";
    public static final String PLACE_COST = "suporte.troca-lugar.custo";
    public static final String PLACE_COOLDOWN = "suporte.troca-lugar.recarga";
    public static final String PLACE_FALL_PROTECTION = "suporte.troca-lugar.protecao-queda-segundos";
    public static final String RESCUER_RADIUS = "suporte.socorrista.raio";
    public static final String RESCUER_HEALTH = "suporte.socorrista.vida";
    public static final String RESCUER_SECONDS = "suporte.socorrista.segundos";
    public static final String RESCUER_LEVEL = "suporte.socorrista.nivel";
    public static final String RESCUER_COOLDOWN = "suporte.socorrista.recarga";
    public static final String SHIELD_ABSORPTION = "suporte.escudo.absorcao";
    public static final String SHIELD_SECONDS = "suporte.escudo.segundos";
    public static final String SHIELD_RANGE = "suporte.escudo.alcance";
    public static final String SHIELD_COST = "suporte.escudo.custo";
    public static final String SHIELD_COOLDOWN = "suporte.escudo.recarga";
    public static final String PURIFY_RADIUS = "suporte.purificar.raio";
    public static final String PURIFY_COST = "suporte.purificar.custo";
    public static final String PURIFY_COOLDOWN = "suporte.purificar.recarga";
    public static final String TOTEM_HEALTH = "suporte.totem.vida";
    public static final String TOTEM_LEVEL = "suporte.totem.nivel";
    public static final String TOTEM_RADIUS = "suporte.totem.raio";
    public static final String TOTEM_MAX = "suporte.totem.maximo";
    public static final String SILENCE_SECONDS = "suporte.silencio.segundos";
    public static final String SILENCE_RANGE = "suporte.silencio.alcance";
    public static final String SILENCE_COST = "suporte.silencio.custo";
    public static final String SILENCE_COOLDOWN = "suporte.silencio.recarga";
    public static final String CURSE_REDUCTION = "suporte.maldicao.reducao";
    public static final String CURSE_SECONDS = "suporte.maldicao.segundos";
    public static final String CURSE_RANGE = "suporte.maldicao.alcance";
    public static final String CURSE_COST = "suporte.maldicao.custo";
    public static final String CURSE_COOLDOWN = "suporte.maldicao.recarga";

    private static final Map<String, Object> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put(EXTRA_HEALTH, 16);
        DEFAULTS.put(WEAPON_PENALTY, 0.4);
        DEFAULTS.put(HARMONY_MAX, 100);
        DEFAULTS.put(HARMONY_PER_HEAL, 0.5);
        DEFAULTS.put(HARMONY_PER_LINK, 0.5);
        DEFAULTS.put(HARMONY_PER_ASSIST, 10);
        DEFAULTS.put(HARMONY_REGEN, 1);
        DEFAULTS.put(HARMONY_REGEN_SECONDS, 2);
        DEFAULTS.put(LINK_PERCENT, 0.35);
        DEFAULTS.put(LINK_STOP_HEALTH, 4);
        DEFAULTS.put(LINK_MAX_DISTANCE, 20);
        DEFAULTS.put(LINK_RANGE, 10);
        DEFAULTS.put(LINK_RESISTANCE, 0.1);
        DEFAULTS.put(LINK_SHARED_HEAL, 0.5);
        DEFAULTS.put(ROOT_SECONDS, 1.5);
        DEFAULTS.put(ROOT_RANGE, 20);
        DEFAULTS.put(ROOT_COOLDOWN, 8);
        DEFAULTS.put(ROOT_COST, 25);
        DEFAULTS.put(HEAL_AMOUNT, 6);
        DEFAULTS.put(HEAL_RADIUS, 6);
        DEFAULTS.put(HEAL_COOLDOWN, 8);
        DEFAULTS.put(HEAL_COST, 30);
        DEFAULTS.put(SCROLL_LEVEL, 1);
        DEFAULTS.put(SCROLL_SECONDS, 8);
        DEFAULTS.put(SCROLL_RADIUS, 8);
        DEFAULTS.put(SCROLL_COOLDOWN, 20);
        DEFAULTS.put(SCROLL_COST, 20);
        DEFAULTS.put(FLIGHT_SECONDS, 5);
        DEFAULTS.put(FLIGHT_COOLDOWN, 15);
        DEFAULTS.put(FLIGHT_COST, 15);
        DEFAULTS.put(FALL_PROTECTION, 10);
        DEFAULTS.put(FLIGHT_SPEED, 0.04);
        DEFAULTS.put(FLIGHT_MAX_SPEED, 0.6);
        DEFAULTS.put(FLIGHT_MAX_HEIGHT, 8);
        DEFAULTS.put(SWAP_ALLY_HEALTH, 8);
        DEFAULTS.put(SWAP_MAX_HEALTH, 6);
        DEFAULTS.put(SWAP_SECONDS, 60);
        DEFAULTS.put(SWAP_COOLDOWN, 180);
        DEFAULTS.put(DRAIN_SECONDS, 5);
        DEFAULTS.put(DRAIN_PERCENT, 1.0);
        DEFAULTS.put(DRAIN_COST, 35);
        DEFAULTS.put(DRAIN_COOLDOWN, 20);
        DEFAULTS.put(PLACE_DISTANCE, 30);
        DEFAULTS.put(PLACE_COST, 20);
        DEFAULTS.put(PLACE_COOLDOWN, 15);
        DEFAULTS.put(PLACE_FALL_PROTECTION, 3);
        DEFAULTS.put(RESCUER_RADIUS, 15);
        DEFAULTS.put(RESCUER_HEALTH, 0.3);
        DEFAULTS.put(RESCUER_SECONDS, 3);
        DEFAULTS.put(RESCUER_LEVEL, 2);
        DEFAULTS.put(RESCUER_COOLDOWN, 10);
        DEFAULTS.put(SHIELD_ABSORPTION, 8);
        DEFAULTS.put(SHIELD_SECONDS, 6);
        DEFAULTS.put(SHIELD_RANGE, 15);
        DEFAULTS.put(SHIELD_COST, 35);
        DEFAULTS.put(SHIELD_COOLDOWN, 12);
        DEFAULTS.put(PURIFY_RADIUS, 8);
        DEFAULTS.put(PURIFY_COST, 30);
        DEFAULTS.put(PURIFY_COOLDOWN, 10);
        DEFAULTS.put(TOTEM_HEALTH, 50);
        DEFAULTS.put(TOTEM_LEVEL, 2);
        DEFAULTS.put(TOTEM_RADIUS, 6);
        DEFAULTS.put(TOTEM_MAX, 1);
        DEFAULTS.put(SILENCE_SECONDS, 10);
        DEFAULTS.put(SILENCE_RANGE, 20);
        DEFAULTS.put(SILENCE_COST, 40);
        DEFAULTS.put(SILENCE_COOLDOWN, 20);
        DEFAULTS.put(CURSE_REDUCTION, 0.2);
        DEFAULTS.put(CURSE_SECONDS, 5);
        DEFAULTS.put(CURSE_RANGE, 20);
        DEFAULTS.put(CURSE_COST, 25);
        DEFAULTS.put(CURSE_COOLDOWN, 15);
    }

    private final JavaPlugin plugin;

    public SuporteSettings(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Acrescenta ao config as chaves que faltarem, sem apagar nada. Retorna true se alterou. */
    public static boolean addMissing(FileConfiguration config) {
        boolean changed = false;
        for (Map.Entry<String, Object> entry : DEFAULTS.entrySet()) {
            if (!config.contains(entry.getKey())) {
                config.set(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        return changed;
    }

    private double number(String path) {
        Object fallback = DEFAULTS.get(path);
        return plugin.getConfig().getDouble(path, fallback instanceof Number n ? n.doubleValue() : 0);
    }

    private static double fraction(double value) {
        return Math.max(0, Math.min(1, value));
    }

    /** Vida máxima a mais do Suporte (16 = 8 corações: 18 corações no total). */
    public double extraHealth() {
        return Math.max(0, number(EXTRA_HEALTH));
    }

    /** Fração do dano perdida com armas (0.4 = 40% menos). */
    public double weaponPenalty() {
        return fraction(number(WEAPON_PENALTY));
    }

    public double harmonyMax() {
        return Math.max(1, number(HARMONY_MAX));
    }

    /** Harmonia por ponto de vida curado (0.5 = +1 a cada 2 de vida). */
    public double harmonyPerHeal() {
        return Math.max(0, number(HARMONY_PER_HEAL));
    }

    /** Harmonia por ponto de dano que o vínculo tirou do aliado. */
    public double harmonyPerLink() {
        return Math.max(0, number(HARMONY_PER_LINK));
    }

    public double harmonyPerAssist() {
        return Math.max(0, number(HARMONY_PER_ASSIST));
    }

    /** Harmonia ganha com o tempo a cada {@link #harmonyRegenSeconds()} segundos. */
    public double harmonyRegen() {
        return Math.max(0, number(HARMONY_REGEN));
    }

    public int harmonyRegenSeconds() {
        return Math.max(1, (int) Math.round(number(HARMONY_REGEN_SECONDS)));
    }

    /** Fração do dano passada ao Suporte (0.35 = 35%), limitada a 0..1. */
    public double linkPercent() {
        return fraction(number(LINK_PERCENT));
    }

    /** Com a vida do Suporte nisto ou abaixo, o vínculo para de dividir dano. */
    public double linkStopHealth() {
        return Math.max(0, number(LINK_STOP_HEALTH));
    }

    public double linkMaxDistance() {
        return Math.max(1, number(LINK_MAX_DISTANCE));
    }

    public double linkRange() {
        return Math.max(1, number(LINK_RANGE));
    }

    /** Dano a menos do aliado vinculado (0.1 = 10%), aplicado antes da divisão. */
    public double linkResistance() {
        return fraction(number(LINK_RESISTANCE));
    }

    /** Parte da autocura do Suporte que o aliado vinculado também recebe (0.5 = metade). */
    public double linkSharedHeal() {
        return Math.max(0, number(LINK_SHARED_HEAL));
    }

    public double rootSeconds() {
        return Math.max(0.1, number(ROOT_SECONDS));
    }

    public double rootRange() {
        return Math.max(1, number(ROOT_RANGE));
    }

    public double healAmount() {
        return Math.max(0, number(HEAL_AMOUNT));
    }

    public double healRadius() {
        return Math.max(1, number(HEAL_RADIUS));
    }

    /** Nível da Velocidade do Pergaminho (1 = Velocidade I). */
    public int scrollLevel() {
        return Math.max(1, (int) Math.round(number(SCROLL_LEVEL)));
    }

    public int scrollSeconds() {
        return Math.max(1, (int) Math.round(number(SCROLL_SECONDS)));
    }

    public double scrollRadius() {
        return Math.max(1, number(SCROLL_RADIUS));
    }

    public int flightSeconds() {
        return Math.max(1, (int) Math.round(number(FLIGHT_SECONDS)));
    }

    public int fallProtectionSeconds() {
        return Math.max(1, (int) Math.round(number(FALL_PROTECTION)));
    }

    /** Velocidade de voo durante o voo da Pena (o padrão do jogo é 0.1), entre 0.01 e 1. */
    public float flightSpeed() {
        return (float) Math.max(0.01, Math.min(1, number(FLIGHT_SPEED)));
    }

    /** Velocidade horizontal máxima durante o voo da Pena, em blocos por tick. */
    public double flightMaxSpeed() {
        return Math.max(0.05, number(FLIGHT_MAX_SPEED));
    }

    /** Quantos blocos acima do ponto de partida o Suporte pode subir voando. */
    public double flightMaxHeight() {
        return Math.max(0, number(FLIGHT_MAX_HEIGHT));
    }

    /** Vida com que o aliado fica depois da Troca Equivalente (8 = 4 corações). */
    public double swapAllyHealth() {
        return Math.max(1, number(SWAP_ALLY_HEALTH));
    }

    /** Vida máxima do Suporte durante a penalidade da Troca Equivalente (6 = 3 corações). */
    public double swapMaxHealth() {
        return Math.max(1, number(SWAP_MAX_HEALTH));
    }

    public double swapSeconds() {
        return Math.max(1, number(SWAP_SECONDS));
    }

    public double drainSeconds() {
        return Math.max(0.5, number(DRAIN_SECONDS));
    }

    /** Fração do dano final do Suporte que vira cura no vinculado durante o Elo Drenante (1.0 = 100%). */
    public double drainPercent() {
        return Math.max(0, number(DRAIN_PERCENT));
    }

    public double placeDistance() {
        return Math.max(1, number(PLACE_DISTANCE));
    }

    public int placeFallProtectionSeconds() {
        return Math.max(0, (int) Math.round(number(PLACE_FALL_PROTECTION)));
    }

    public double rescuerRadius() {
        return Math.max(1, number(RESCUER_RADIUS));
    }

    /** Fração da vida máxima do aliado abaixo da qual a Socorrista ativa (0.3 = 30%). */
    public double rescuerHealth() {
        return fraction(number(RESCUER_HEALTH));
    }

    public int rescuerSeconds() {
        return Math.max(1, (int) Math.round(number(RESCUER_SECONDS)));
    }

    /** Nível da Velocidade da Socorrista (2 = Velocidade II). */
    public int rescuerLevel() {
        return Math.max(1, (int) Math.round(number(RESCUER_LEVEL)));
    }

    public double rescuerCooldownSeconds() {
        return Math.max(0, number(RESCUER_COOLDOWN));
    }

    /** Absorção do Escudo Sagrado (8 = 4 corações dourados). */
    public double shieldAbsorption() {
        return Math.max(1, number(SHIELD_ABSORPTION));
    }

    public double shieldSeconds() {
        return Math.max(1, number(SHIELD_SECONDS));
    }

    public double shieldRange() {
        return Math.max(1, number(SHIELD_RANGE));
    }

    public double purifyRadius() {
        return Math.max(1, number(PURIFY_RADIUS));
    }

    /** Totens de Cura ativos ao mesmo tempo por Suporte. */
    public int totemMax() {
        return Math.max(1, (int) Math.round(number(TOTEM_MAX)));
    }

    public double totemHealth() {
        return Math.max(1, number(TOTEM_HEALTH));
    }

    /** Nível da Regeneração do Totem (2 = Regeneração II). */
    public int totemLevel() {
        return Math.max(1, (int) Math.round(number(TOTEM_LEVEL)));
    }

    public double totemRadius() {
        return Math.max(1, number(TOTEM_RADIUS));
    }

    public double silenceSeconds() {
        return Math.max(0.5, number(SILENCE_SECONDS));
    }

    public double silenceRange() {
        return Math.max(1, number(SILENCE_RANGE));
    }

    /** Fração do dano a menos que o amaldiçoado causa (0.2 = 20%). */
    public double curseReduction() {
        return fraction(number(CURSE_REDUCTION));
    }

    public double curseSeconds() {
        return Math.max(0.5, number(CURSE_SECONDS));
    }

    public double curseRange() {
        return Math.max(1, number(CURSE_RANGE));
    }

    /** Recarga da habilidade em segundos (0 = sem recarga). */
    public double cooldownSeconds(SuporteSkill skill) {
        return Math.max(0, number(switch (skill) {
            case PRENDER -> ROOT_COOLDOWN;
            case CURA -> HEAL_COOLDOWN;
            case PERGAMINHO -> SCROLL_COOLDOWN;
            case VOO -> FLIGHT_COOLDOWN;
            case ELO -> DRAIN_COOLDOWN;
            case TROCA_LUGAR -> PLACE_COOLDOWN;
            case TROCA_EQUIVALENTE -> SWAP_COOLDOWN;
            case ESCUDO -> SHIELD_COOLDOWN;
            case PURIFICAR -> PURIFY_COOLDOWN;
            case SILENCIO -> SILENCE_COOLDOWN;
            case MALDICAO -> CURSE_COOLDOWN;
        }));
    }

    /** Custo em Harmonia da habilidade (0 = grátis). */
    public double cost(SuporteSkill skill) {
        return Math.max(0, switch (skill) {
            case PRENDER -> number(ROOT_COST);
            case CURA -> number(HEAL_COST);
            case PERGAMINHO -> number(SCROLL_COST);
            case VOO -> number(FLIGHT_COST);
            case ELO -> number(DRAIN_COST);
            case TROCA_LUGAR -> number(PLACE_COST);
            case TROCA_EQUIVALENTE -> 0;
            case ESCUDO -> number(SHIELD_COST);
            case PURIFICAR -> number(PURIFY_COST);
            case SILENCIO -> number(SILENCE_COST);
            case MALDICAO -> number(CURSE_COST);
        });
    }
}
