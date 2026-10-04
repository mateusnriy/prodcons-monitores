package br.edu.uern.prodcons.threads;

import br.edu.uern.prodcons.model.IBufferLimitado;
import br.edu.uern.prodcons.model.Item;

// Thread consumidora com cota finita de itens
public class ThreadConsumidor implements Runnable {

    private final String nome;
    private final MotorSimulacao motor;
    private final int totalItens;
    private volatile int atrasoMs;
    private final ControleExecucao controle;
    private int itensConsumidos = 0;

    public ThreadConsumidor(int id, MotorSimulacao motor, int totalItens, int atrasoMs, ControleExecucao controle) {
        this.nome = "Consumidor-" + id;
        this.motor = motor;
        this.totalItens = totalItens;
        this.atrasoMs = atrasoMs;
        this.controle = controle;
    }

    public void setAtrasoMs(int novoAtraso) {
        this.atrasoMs = novoAtraso;
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
            while (itensConsumidos < totalItens) {
                // antes de entrar no monitor, ve se pode seguir (pausa / passo a passo)
                if (!controle.aguardarLiberacao()) {
                    break;
                }

                // retira o item do buffer ativo do motor
                Item item = motor.getBufferAtual().remover(this.nome);
                itensConsumidos++;

                // consome/processa fora da secao critica (dorme mesmo se nulo, mantendo a concorrencia real no modo caos)
                Thread.sleep((long) (atrasoMs * (0.8 + Math.random() * 0.4)));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
