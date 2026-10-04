package br.edu.uern.prodcons.monitor;

import java.util.Arrays;

import br.edu.uern.prodcons.model.IBufferLimitado;
import br.edu.uern.prodcons.model.Item;
import br.edu.uern.prodcons.telemetria.DespachanteEventos;
import br.edu.uern.prodcons.telemetria.EventoSimulacao;

// Buffer propositalmente sem travas nem wait/notify pra demonstrar erros de concorrencia
public class BufferCaos implements IBufferLimitado {

    private final Item[] buffer;
    private final int capacidade;
    private int in = 0;
    private int out = 0;
    private volatile int count = 0;
    private final DespachanteEventos despachante;

    public BufferCaos(int capacidade, DespachanteEventos despachante) {
        this.capacidade = capacidade;
        this.buffer = new Item[capacidade];
        this.despachante = despachante;
    }

    @Override
    public void inserir(Item item, String nomeThread) throws InterruptedException {
        // anomalia: tenta inserir mesmo com o buffer cheio
        if (count >= capacidade) {
            despachante.despachar(new EventoSimulacao(
                    "ANOMALIA_OVERFLOW", nomeThread, "PRODUTOR", in, item.getId(),
                    count + 1, capacidade, in, out, getSnapshot(),
                    "[X] [" + nomeThread + "] ALERTA DE OVERFLOW: Inserção com buffer lotado [" + (count + 1) + "/" + capacidade + "]!",
                    true
            ));
        }

        // anomalia: sobrescreve um item antes que ele tenha sido consumido (Lost Update)
        int slotGravado = in;
        if (buffer[slotGravado] != null) {
            despachante.despachar(new EventoSimulacao(
                    "ANOMALIA_LOST_UPDATE", nomeThread, "PRODUTOR", slotGravado, item.getId(),
                    count, capacidade, in, out, getSnapshot(),
                    "[X] [" + nomeThread + "] ALERTA DE LOST UPDATE: O item #" + buffer[slotGravado].getId() + " no slot [" + slotGravado + "] foi SOBRESCRITO antes de ser lido!",
                    true
            ));
        }

        // atraso forcado pra induzir preempcao no meio da gravacao desprotegida
        Thread.sleep(10);

        buffer[slotGravado] = item;
        in = (in + 1) % capacidade;
        count++; // operacao nao-atomica, vai dar condicao de corrida

        despachante.despachar(new EventoSimulacao(
                "PRODUCAO_SUCESSO", nomeThread, "PRODUTOR", slotGravado, item.getId(),
                count, capacidade, in, out, getSnapshot(),
                "[+] [" + nomeThread + "] (MODO CAOS) Inseriu " + item + " no slot [" + slotGravado + "]. Ocupação declarada: [" + count + "/" + capacidade + "]",
                true
        ));
    }

    @Override
    public Item remover(String nomeThread) throws InterruptedException {
        // anomalia: tenta ler com o buffer vazio (Underflow)
        if (count <= 0) {
            despachante.despachar(new EventoSimulacao(
                    "ANOMALIA_UNDERFLOW", nomeThread, "CONSUMIDOR", out, null,
                    count - 1, capacidade, in, out, getSnapshot(),
                    "[X] [" + nomeThread + "] ALERTA DE UNDERFLOW: Tentativa de leitura em buffer vazio [" + (count - 1) + "/" + capacidade + "]!",
                    true
            ));
        }

        int slotLido = out;
        Item item = buffer[slotLido];

        Thread.sleep(10);

        buffer[slotLido] = null;
        out = (out + 1) % capacidade;
        count--; // nao-atomico

        despachante.despachar(new EventoSimulacao(
                "CONSUMO_SUCESSO", nomeThread, "CONSUMIDOR", slotLido, item != null ? item.getId() : -1,
                count, capacidade, in, out, getSnapshot(),
                "[-] [" + nomeThread + "] (MODO CAOS) Removeu " + (item != null ? item : "DADO NULO/CORROMPIDO") + " do slot [" + slotLido + "]. Ocupação declarada: [" + count + "/" + capacidade + "]",
                true
        ));

        return item;
    }

    @Override
    public int getOcupacao() {
        return count;
    }

    @Override
    public int getCapacidade() {
        return capacidade;
    }

    @Override
    public int getInIndex() {
        return in;
    }

    @Override
    public int getOutIndex() {
        return out;
    }

    @Override
    public Item[] getSnapshot() {
        return Arrays.copyOf(buffer, buffer.length);
    }

    @Override
    public synchronized void restaurarEstado(Item[] snapshot, int in, int out, int count) {
        // migra ponteiros e ocupacao mesmo que irregulares
        this.in = (capacidade > 0) ? (in % capacidade) : 0;
        this.out = (capacidade > 0) ? (out % capacidade) : 0;
        this.count = count;
        if (snapshot != null) {
            for (int i = 0; i < capacidade && i < snapshot.length; i++) {
                this.buffer[i] = snapshot[i];
            }
        }
    }

    @Override
    public boolean isChaos() {
        return true;
    }
}
