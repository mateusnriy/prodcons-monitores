package br.edu.uern.prodcons.threads;

import java.util.concurrent.atomic.AtomicInteger;

import br.edu.uern.prodcons.model.IBufferLimitado;
import br.edu.uern.prodcons.model.Item;

// Thread produtora com cota finita de itens
public class ThreadProdutor implements Runnable {

    // gerador atomico pra garantir que o ID dos itens nao se repita
    private static final AtomicInteger GERADOR_ITEM = new AtomicInteger(1);

    private final String nome;
    private final MotorSimulacao motor;
    private final int totalItens;
    private volatile int atrasoMs;
    private final ControleExecucao controle;
    private int itensProduzidos = 0;

    public ThreadProdutor(int id, MotorSimulacao motor, int totalItens, int atrasoMs, ControleExecucao controle) {
        this.nome = "Produtor-" + id;
        this.motor = motor;
        this.totalItens = totalItens;
        this.atrasoMs = atrasoMs;
        this.controle = controle;
    }

    // volta o ID dos itens pra 1 a cada nova rodada
    public static void reiniciarContador() {
        GERADOR_ITEM.set(1);
    }

    public void setAtrasoMs(int novoAtraso) {
        this.atrasoMs = novoAtraso;
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
            while (itensProduzidos < totalItens) {
                // trabalho local fora da secao critica (fabricando o item)
                Thread.sleep((long) (atrasoMs * (0.8 + Math.random() * 0.4)));

                // antes de entrar no monitor, ve se pode seguir (pausa / passo a passo)
                if (!controle.aguardarLiberacao()) {
                    break;
                }

                // fabrica o item e insere no buffer ativo do motor
                Item item = new Item(GERADOR_ITEM.getAndIncrement(), this.nome);
                motor.getBufferAtual().inserir(item, this.nome);
                itensProduzidos++;
            }
        } catch (InterruptedException e) {
            // restaura a flag de interrupcao pra desligar limpo
            Thread.currentThread().interrupt();
        }
    }
}
