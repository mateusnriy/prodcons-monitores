package br.edu.uern.prodcons.threads;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import br.edu.uern.prodcons.model.IBufferLimitado;
import br.edu.uern.prodcons.model.Item;

// Thread produtora com cota finita de itens
public class ThreadProdutor implements Runnable {

    // gerador atomico pra garantir que o ID dos itens nao se repita
    private static final AtomicInteger GERADOR_ITEM = new AtomicInteger(1);

    private final int id;
    private final String nome;
    private final IBufferLimitado buffer;
    private final int totalItens;
    private volatile int atrasoMs;
    private final AtomicBoolean emExecucao = new AtomicBoolean(true);
    private final AtomicBoolean pausado = new AtomicBoolean(false);
    private int itensProduzidos = 0;

    public ThreadProdutor(int id, IBufferLimitado buffer, int totalItens, int atrasoMs) {
        this.id = id;
        this.nome = "Produtor-" + id;
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

    public int getItensProduzidos() {
        return itensProduzidos;
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
            while (itensProduzidos < totalItens && emExecucao.get()) {
                // se tiver pausado, dorme um pouquinho e checa de novo
                while (pausado.get() && emExecucao.get()) {
                    Thread.sleep(100);
                }

                // trabalho local fora da secao critica pra nao prender o monitor
                Thread.sleep((long) (atrasoMs * (0.8 + Math.random() * 0.4)));

                if (!emExecucao.get()) {
                    break;
                }

                // fabrica o item e insere no buffer
                Item item = new Item(GERADOR_ITEM.getAndIncrement(), this.nome);
                buffer.inserir(item, this.nome);
                itensProduzidos++;
            }
        } catch (InterruptedException e) {
            // restaura a flag de interrupcao pra desligar limpo
            Thread.currentThread().interrupt();
        }
    }
}
