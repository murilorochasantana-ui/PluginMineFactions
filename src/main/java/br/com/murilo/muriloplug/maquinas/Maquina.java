package br.com.murilo.muriloplug.maquinas;

import br.com.murilo.muriloplug.clan.base.BaseLoc;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Uma máquina colocada no mundo. O armazém, o combustível e os upgrades moram aqui (não no inventário de um menu):
 * quem abrir o menu vê sempre os mesmos dados. Só o {@link MaquinaManager} altera.
 */
public final class Maquina {

    final String id;
    final MaquinaTipo tipo;
    final String clan;
    final BaseLoc local;
    /** Bloco colocado (o do tipo na hora de colocar; se o admin trocar o bloco do tipo, esta continua igual). */
    final Material bloco;
    /** Produtos guardados (material -> quantidade). */
    final Map<Material, Integer> armazem = new LinkedHashMap<>();
    /** Upgrades instalados, na ordem dos slots. */
    final List<Upgrade> upgrades = new ArrayList<>();
    double combustivel;
    /** Segundos de ciclo já andados. */
    double progresso;
    /** Parte decimal dos sorteios por ciclo que ainda não virou sorteio. */
    double sorteioResto;
    /** Parte decimal do custo do Cofre que ainda não foi cobrada. */
    double custoResto;
    int vida;
    long escudoAte;
    long ultimoDano;
    long ultimaRegeneracao;

    // Só em memória:
    MaquinaStatus status = MaquinaStatus.PRODUZINDO;
    boolean engenheiroPerto;
    long ultimaTentativaAuto;

    Maquina(String id, MaquinaTipo tipo, String clan, BaseLoc local, Material bloco) {
        this.id = id;
        this.tipo = tipo;
        this.clan = clan;
        this.local = local;
        this.bloco = bloco;
    }

    public String id() {
        return id;
    }

    public MaquinaTipo tipo() {
        return tipo;
    }

    /** TAG do clan dono. */
    public String clan() {
        return clan;
    }

    public BaseLoc local() {
        return local;
    }

    public Material bloco() {
        return bloco;
    }

    public Map<Material, Integer> armazem() {
        return Collections.unmodifiableMap(armazem);
    }

    public List<Upgrade> upgrades() {
        return Collections.unmodifiableList(upgrades);
    }

    public double combustivel() {
        return combustivel;
    }

    public double progresso() {
        return progresso;
    }

    public int vida() {
        return vida;
    }

    public MaquinaStatus status() {
        return status;
    }

    public boolean engenheiroPerto() {
        return engenheiroPerto;
    }

    public boolean comEscudo() {
        return escudoAte > System.currentTimeMillis();
    }

    /** Itens guardados, somando todos os produtos. */
    public int totalArmazem() {
        int total = 0;
        for (int quantidade : armazem.values()) {
            total += quantidade;
        }
        return total;
    }

    /** Upgrade instalado deste tipo, ou null. */
    public Upgrade upgrade(UpgradeTipo tipo) {
        for (Upgrade upgrade : upgrades) {
            if (upgrade.tipo() == tipo) {
                return upgrade;
            }
        }
        return null;
    }
}
