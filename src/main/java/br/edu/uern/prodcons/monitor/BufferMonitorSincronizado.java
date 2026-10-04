package br.edu.uern.prodcons.monitor;

import java.util.Arrays;

import br.edu.uern.prodcons.model.IBufferLimitado;
import br.edu.uern.prodcons.model.Item;
import br.edu.uern.prodcons.telemetria.DespachanteEventos;
import br.edu.uern.prodcons.telemetria.EventoSimulacao;

// Implementacao do monitor canonico com synchronized, wait e notifyAll
public class BufferMonitorSincronizado implements IBufferLimitado {

    private final Item[] buffer;
    private final int capacidade;
    private int in = 0;
    private int out = 0;
    private int count = 0;
    private final int atrasoSecaoCriticaMs;
    private final DespachanteEventos despachante;

    public BufferMonitorSincronizado(int capacidade, int atrasoSecaoCriticaMs, DespachanteEventos despachante) {
        this.capacidade = capacidade;
        this.buffer = new Item[capacidade];
        this.atrasoSecaoCriticaMs = atrasoSecaoCriticaMs;
        this.despachante = despachante;
    }

    @Override
    public synchronized void inserir(Item item, String nomeThread) throws InterruptedException {
        // laco while obrigatorio pela semantica de Mesa (se acordar de wait, reavalia a condicao)
        while (count == capacidade) {
            despachante.despachar(new EventoSimulacao(
                    "BLOQUEIO_CHEIO", nomeThread, "PRODUTOR", -1, item.getId(),
                    count, capacidade, in, out, getSnapshot(),
                    "[!] [" + nomeThread + "] Bloqueado via wait(): Buffer CHEIO [" + count + "/" + capacidade + "]. Aguardando vagas.",
                    false
            ));
            wait(); // libera o lock do monitor e dorme no escalonador do SO
        }

        // simula uma preempcao do SO mantendo o lock retido (prova que a exclusao mutua funciona)
        if (atrasoSecaoCriticaMs > 0) {
            Thread.sleep(atrasoSecaoCriticaMs);
        }

        // grava o item no compartimento e avanca o ponteiro circular
        int slotGravado = in;
        buffer[slotGravado] = item;
        in = (in + 1) % capacidade;
        count++;

        int vagasRestantes = capacidade - count;
        despachante.despachar(new EventoSimulacao(
                "PRODUCAO_SUCESSO", nomeThread, "PRODUTOR", slotGravado, item.getId(),
                count, capacidade, in, out, getSnapshot(),
                "[+] [" + nomeThread + "] Inseriu " + item + " no slot [" + slotGravado + "]. Ocupação: [" + count + "/" + capacidade + "] (Livres: " + vagasRestantes + ")",
                false
        ));

        // acorda todas as threads do wait set pra evitar deadlock
        notifyAll();
    }

    @Override
    public synchronized Item remover(String nomeThread) throws InterruptedException {
        // laco while obrigatorio pela semantica de Mesa
        while (count == 0) {
            despachante.despachar(new EventoSimulacao(
                    "BLOQUEIO_VAZIO", nomeThread, "CONSUMIDOR", -1, null,
                    count, capacidade, in, out, getSnapshot(),
                    "[!] [" + nomeThread + "] Bloqueado via wait(): Buffer VAZIO [" + count + "/" + capacidade + "]. Aguardando produção.",
                    false
            ));
            wait(); // libera o lock do monitor e espera chegar item
        }

        // simula preempcao mantendo a posse do lock
        if (atrasoSecaoCriticaMs > 0) {
            Thread.sleep(atrasoSecaoCriticaMs);
        }

        // retira o item do compartimento circular
        int slotLido = out;
        Item item = buffer[slotLido];
        buffer[slotLido] = null;
        out = (out + 1) % capacidade;
        count--;

        despachante.despachar(new EventoSimulacao(
                "CONSUMO_SUCESSO", nomeThread, "CONSUMIDOR", slotLido, item != null ? item.getId() : null,
                count, capacidade, in, out, getSnapshot(),
                "[-] [" + nomeThread + "] Consumiu " + item + " do slot [" + slotLido + "]. Ocupação: [" + count + "/" + capacidade + "] (Ocupados: " + count + ")",
                false
        ));

        // notifica produtores que estavam esperando abrir vaga
        notifyAll();

        return item;
    }

    @Override
    public synchronized int getOcupacao() {
        return count;
    }

    @Override
    public int getCapacidade() {
        return capacidade;
    }

    @Override
    public synchronized int getInIndex() {
        return in;
    }

    @Override
    public synchronized int getOutIndex() {
        return out;
    }

    @Override
    public synchronized Item[] getSnapshot() {
        // copia do array pra nao vazar a referencia interna
        return Arrays.copyOf(buffer, buffer.length);
    }

    @Override
    public boolean isChaos() {
        return false;
    }
}
