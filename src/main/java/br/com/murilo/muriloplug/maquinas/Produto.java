package br.com.murilo.muriloplug.maquinas;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.Map;

/** Uma linha da tabela de produção: item, peso relativo, quantidade mínima e máxima por sorteio e se é raro. */
public record Produto(Material item, double peso, int min, int max, boolean raro) {

    public Produto {
        peso = Math.max(0, peso);
        min = Math.max(1, min);
        max = Math.max(min, max);
    }

    public Produto comPeso(double novo) {
        return new Produto(item, novo, min, max, raro);
    }

    public Produto comMin(int novo) {
        return new Produto(item, peso, novo, Math.max(novo, max), raro);
    }

    public Produto comMax(int novo) {
        return new Produto(item, peso, Math.min(min, novo), novo, raro);
    }

    public Produto comRaro(boolean novo) {
        return new Produto(item, peso, min, max, novo);
    }

    Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("item", item.name());
        map.put("peso", peso);
        map.put("min", min);
        map.put("max", max);
        map.put("raro", raro);
        return map;
    }

    /** Linha do arquivo, ou null se o item for inválido. */
    static Produto deserialize(Map<?, ?> map) {
        Material material = Material.matchMaterial(String.valueOf(map.get("item")));
        if (material == null || !material.isItem() || material.isAir()) {
            return null;
        }
        return new Produto(material, number(map.get("peso"), 1), (int) number(map.get("min"), 1),
                (int) number(map.get("max"), 1), Boolean.TRUE.equals(map.get("raro")));
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    @Override
    public String toString() {
        return item.name() + " peso " + peso + " " + min + "-" + max + (raro ? " raro" : "");
    }
}
