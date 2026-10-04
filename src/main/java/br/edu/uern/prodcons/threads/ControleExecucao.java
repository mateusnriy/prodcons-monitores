package br.edu.uern.prodcons.threads;

// Controla pausa, retomada e o modo passo a passo das threads
// usa o proprio monitor do objeto (synchronized + wait/notifyAll), igual ao buffer
public class ControleExecucao {

    private boolean pausado;
    private boolean encerrado = false;

    // quantos ciclos ainda podem rodar enquanto ta pausado (cada clique no Passo soma 1)
    private int passosLiberados = 0;

    public ControleExecucao(boolean comecarPausado) {
        this.pausado = comecarPausado;
    }

    public synchronized void pausar() {
        pausado = true;
    }

    public synchronized void retomar() {
        pausado = false;
        passosLiberados = 0;
        // acorda todo mundo que tava parado esperando liberacao
        notifyAll();
    }

    // libera exatamente um ciclo pra uma unica thread
    public synchronized void liberarPasso() {
        if (pausado) {
            passosLiberados++;
            notifyAll();
        }
    }

    // usado no reset pra soltar as threads e elas sairem do run()
    public synchronized void encerrar() {
        encerrado = true;
        notifyAll();
    }

    // a thread chama isso logo antes de mexer no buffer
    // retorna false se a simulacao foi encerrada (ai a thread sai do laco)
    public synchronized boolean aguardarLiberacao() throws InterruptedException {
        // while pelo mesmo motivo do buffer: acordou, reavalia a condicao
        while (pausado && passosLiberados == 0 && !encerrado) {
            wait();
        }
        if (encerrado) {
            return false;
        }
        // se ta pausado e passou daqui, e porque pegou o passo. consome ele
        // as outras threads que acordaram juntas voltam a dormir no while
        if (pausado) {
            passosLiberados--;
        }
        return true;
    }

    public synchronized boolean isPausado() {
        return pausado;
    }
}
