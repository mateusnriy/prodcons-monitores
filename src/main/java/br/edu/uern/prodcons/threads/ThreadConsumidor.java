package br.edu.uern.prodcons.threads;

import java.util.concurrent.atomic.AtomicBoolean;

import br.edu.uern.prodcons.model.IBufferLimitado;
import br.edu.uern.prodcons.model.Item;

// Thread consumidora com cota finita de itens
public class ThreadConsumidor implements Runnable {

    private final int id;
    private final String nome;
    private final IBufferLimitado buffer;
    private final int totalItens;
    private volatile int atrasoMs;
    private final AtomicBoolean emExecucao = new AtomicBoolean(true);
    private final AtomicBoolean pausado = new AtomicBoolean(false);
    private int itensConsumidos = 0;

    public ThreadConsumidor(int id, IBufferLimitado buffer, int totalItens, int atrasoMs) {
        this.id = id;
        this.nome = "Consumidor-" + id;
        this.buffer = buffer;
        this.totalItens = totalItens;
        this.atrasoMs = atrasoMs;
    }

    public void setAtrasoMs(int novoAtraso) {
        this.atrasoMs = novoAtraso;
    }

    public void pausar() {
        pausado.set(true);
    }

    public void retomar() {
        pausado.set(false);
    }

    public void parar() {
        emExecucao.set(false);
    }

    public int getItensConsumidos() {
        return itensConsumidos;
    }

    public int getTotalItens() {
        return totalItens;
    }

    public String getNome() {
        return nome;
    }

    @Override
    public void run() {
        try {
            while (itensConsumidos < totalItens && emExecucao.get()) {
                // espera enquanto tiver pausado
                while (pausado.get() && emExecucao.get()) {
                    Thread.sleep(100);
                }

                // retira o item do buffer (se tiver vazio, dorme no wait)
                Item item = buffer.remover(this.nome);
                itensConsumidos++;

                // consome/processa o item fora da secao critica
                if (item != null) {
                    Thread.sleep((long) (atrasoMs * (0.8 + Math.random() * 0.4)));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
