package br.com.murilo.muriloplug.raid;

import br.com.murilo.muriloplug.economy.EconomyManager;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Resumo de um raid que já acabou (o que vai para o chat, para o menu e para o raids.yml). */
public record RaidRecord(String defender, String defenderName, String claimName, String world, int x, int z,
                         long start, long end, int blocks, int containers, int coreDamage, int vaultDamage,
                         boolean coreDestroyed, long moneyLost, List<String> attackers, List<String> attackerKeys) {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    public RaidRecord {
        attackers = List.copyOf(attackers);
        attackerKeys = List.copyOf(attackerKeys);
    }

    public String date() {
        return DATE.format(Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()));
    }

    public String duration() {
        long seconds = Math.max(0, (end - start) / 1000);
        return String.format(Locale.ROOT, "%dm %02ds", seconds / 60, seconds % 60);
    }

    /** Linhas do resumo (com cores). */
    public List<String> lines() {
        List<String> lines = new ArrayList<>();
        lines.add("§7Claim: §f" + claimName + " §8(" + x + ", " + z + ")");
        lines.add("§7Defensor: §f" + defenderName);
        lines.add("§7Quando: §f" + date() + " §7- duração: §f" + duration());
        lines.add("§7Blocos destruídos: §f" + blocks);
        lines.add("§7Baús/containers destruídos ou abertos: §f" + containers);
        lines.add("§7Dano no Núcleo: §f" + coreDamage + " §7| no Cofre: §f" + vaultDamage);
        lines.add("§7Núcleo destruído: " + (coreDestroyed ? "§cSim" : "§aNão"));
        lines.add("§7Dinheiro perdido do Cofre: §f" + EconomyManager.format(moneyLost));
        lines.add("§7Atacantes: §c" + (attackers.isEmpty() ? "desconhecidos" : String.join("§7, §c", attackers)));
        return lines;
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("defensor", defender);
        map.put("defensor-nome", defenderName);
        map.put("claim", claimName);
        map.put("mundo", world);
        map.put("x", x);
        map.put("z", z);
        map.put("inicio", start);
        map.put("fim", end);
        map.put("blocos", blocks);
        map.put("containers", containers);
        map.put("dano-nucleo", coreDamage);
        map.put("dano-cofre", vaultDamage);
        map.put("nucleo-destruido", coreDestroyed);
        map.put("dinheiro-perdido", moneyLost);
        map.put("atacantes", attackers);
        map.put("atacantes-chaves", attackerKeys);
        return map;
    }

    public static RaidRecord deserialize(Map<?, ?> map) {
        return new RaidRecord(String.valueOf(map.get("defensor")), String.valueOf(map.get("defensor-nome")),
                String.valueOf(map.get("claim")), String.valueOf(map.get("mundo")), number(map, "x").intValue(),
                number(map, "z").intValue(), number(map, "inicio").longValue(), number(map, "fim").longValue(),
                number(map, "blocos").intValue(), number(map, "containers").intValue(),
                number(map, "dano-nucleo").intValue(), number(map, "dano-cofre").intValue(),
                Boolean.TRUE.equals(map.get("nucleo-destruido")), number(map, "dinheiro-perdido").longValue(),
                strings(map.get("atacantes")), strings(map.get("atacantes-chaves")));
    }

    private static Number number(Map<?, ?> map, String key) {
        return map.get(key) instanceof Number number ? number : 0;
    }

    private static List<String> strings(Object raw) {
        List<String> list = new ArrayList<>();
        if (raw instanceof List<?> items) {
            items.forEach(item -> list.add(String.valueOf(item)));
        }
        return list;
    }
}
