package br.com.murilo.muriloplug.maquinas;

import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contas dos bônus, sempre com os valores atuais da config (mudou no painel, vale no próximo segundo):
 * <ul>
 *   <li>nível efetivo de um upgrade: o instalado, só se o tipo for permitido na máquina, até o nível máximo atual;</li>
 *   <li>ciclo = max(base × ciclo-mínimo, base × (1 − valor × nível da VELOCIDADE));</li>
 *   <li>combustível e custo do Cofre = max(base × combustível-mínimo, base × (1 − valor × nível da EFICIENCIA));</li>
 *   <li>armazém / combustível máximo = base + nível do ESTOQUE × valor; vida = vida + nível da BLINDAGEM × valor;</li>
 *   <li>chance de dobro = nível da FORTUNA × valor; peso dos raros × (1 + nível da SORTE × valor).</li>
 * </ul>
 */
public final class MaquinaCalculos {

    private MaquinaCalculos() {
    }

    public static int nivel(MaquinasConfig config, Maquina maquina, UpgradeTipo tipo) {
        Upgrade upgrade = maquina.upgrade(tipo);
        if (upgrade == null || !config.upgradePermitido(maquina.tipo, tipo)) {
            return 0;
        }
        return Math.min(upgrade.nivel(), config.nivelMaximo(tipo));
    }

    private static double bonus(MaquinasConfig config, Maquina maquina, UpgradeTipo tipo) {
        return nivel(config, maquina, tipo) * config.valorPorNivel(tipo);
    }

    /** Segundos do ciclo com a VELOCIDADE (sem o Engenheiro e o modo teste, que mudam a velocidade do relógio). */
    public static double ciclo(MaquinasConfig config, Maquina maquina) {
        double base = config.ciclo(maquina.tipo);
        double comBonus = base * (1 - bonus(config, maquina, UpgradeTipo.VELOCIDADE));
        return Math.max(0.05, Math.max(base * config.cicloMinimo(), comBonus));
    }

    /** Fator da EFICIENCIA sobre o combustível e o custo do Cofre. */
    private static double fatorEficiencia(MaquinasConfig config, Maquina maquina) {
        return Math.max(config.combustivelMinimo(), 1 - bonus(config, maquina, UpgradeTipo.EFICIENCIA));
    }

    public static double combustivelPorCiclo(MaquinasConfig config, Maquina maquina) {
        return config.combustivelPorCiclo(maquina.tipo) * fatorEficiencia(config, maquina);
    }

    public static double custoHora(MaquinasConfig config, Maquina maquina) {
        return config.custoHora(maquina.tipo) * fatorEficiencia(config, maquina);
    }

    public static int armazemMaximo(MaquinasConfig config, Maquina maquina) {
        return (int) Math.round(config.armazemBase(maquina.tipo) + bonus(config, maquina, UpgradeTipo.ESTOQUE));
    }

    public static double combustivelMaximo(MaquinasConfig config, Maquina maquina) {
        return config.combustivelMaximoBase(maquina.tipo)
                + nivel(config, maquina, UpgradeTipo.ESTOQUE) * config.estoqueCombustivelPorNivel();
    }

    public static double chanceDobro(MaquinasConfig config, Maquina maquina) {
        return Math.min(1, bonus(config, maquina, UpgradeTipo.FORTUNA));
    }

    public static double bonusRaro(MaquinasConfig config, Maquina maquina) {
        return bonus(config, maquina, UpgradeTipo.SORTE);
    }

    public static int vidaMaxima(MaquinasConfig config, Maquina maquina) {
        return (int) Math.round(config.vida(maquina.tipo) + bonus(config, maquina, UpgradeTipo.BLINDAGEM));
    }

    /** Peso do produto com o bônus da SORTE (só nos raros). */
    public static double peso(Produto produto, double bonusRaro) {
        return produto.raro() ? produto.peso() * (1 + bonusRaro) : produto.peso();
    }

    /** Chance (0 a 1) de cada produto num sorteio, na ordem da tabela. */
    public static Map<Produto, Double> chances(List<Produto> tabela, double bonusRaro) {
        double soma = 0;
        for (Produto produto : tabela) {
            soma += peso(produto, bonusRaro);
        }
        Map<Produto, Double> chances = new LinkedHashMap<>();
        for (Produto produto : tabela) {
            chances.put(produto, soma <= 0 ? 0 : peso(produto, bonusRaro) / soma);
        }
        return chances;
    }

    /** Itens em média por ciclo (sorteios × soma de chance × quantidade média). */
    public static double mediaPorCiclo(MaquinasConfig config, MaquinaTipo tipo) {
        double media = 0;
        for (Map.Entry<Produto, Double> entry : chances(config.producao(tipo), 0).entrySet()) {
            media += entry.getValue() * (entry.getKey().min() + entry.getKey().max()) / 2.0;
        }
        return media * config.sorteios(tipo);
    }

    /** Linhas "Carvão 35% (1-3)" da tabela de produção, para lores. */
    public static List<Component> linhasProducao(MaquinasConfig config, MaquinaTipo tipo, double bonusRaro) {
        List<Component> linhas = new ArrayList<>();
        linhas.add(ShopUi.text("§eProdução (por sorteio):"));
        for (Map.Entry<Produto, Double> entry : chances(config.producao(tipo), bonusRaro).entrySet()) {
            Produto produto = entry.getKey();
            linhas.add(ShopUi.text("§8• ").append(nome(produto.item()))
                    .append(ShopUi.text(" §e" + porcento(entry.getValue()) + " §8(" + produto.min() + "-" + produto.max()
                            + ")" + (produto.raro() ? " §d★ raro" : ""))));
        }
        if (linhas.size() == 1) {
            linhas.add(ShopUi.text("§8• §7(tabela vazia)"));
        }
        return linhas;
    }

    /** Nome do item na língua do jogador (branco, sem itálico). */
    public static Component nome(Material material) {
        return ShopUi.text("").append(Component.translatable(material.translationKey(), NamedTextColor.WHITE));
    }

    /** 0.351 -> "35,1%". */
    public static String porcento(double fracao) {
        return MaquinasConfig.decimal(Math.round(fracao * 1000) / 10.0).replace('.', ',') + "%";
    }
}
