package br.edu.uern.prodcons.threads;

import java.util.ArrayList;
import java.util.List;

import br.edu.uern.prodcons.model.IBufferLimitado;
import br.edu.uern.prodcons.monitor.BufferCaos;
import br.edu.uern.prodcons.monitor.BufferMonitorSincronizado;
import br.edu.uern.prodcons.telemetria.DespachanteEventos;
import br.edu.uern.prodcons.telemetria.EventoSimulacao;

// Motor que gerencia o pool de threads e controla a simulacao
public class MotorSimulacao {

    private final int capacidade;
    private final int numProdutores;
    private final int numConsumidores;
    private final int itensPorProdutor;
    private volatile int atrasoProdutorMs;
    private volatile int atrasoConsumidorMs;
    private final int atrasoSecaoCriticaMs;
    private final DespachanteEventos despachante;

    private volatile boolean modoCaos = false;
    private volatile boolean emExecucao = false;
    private volatile boolean pausado = false;

    private IBufferLimitado bufferAtual;
    private final List<ThreadProdutor> produtores = new ArrayList<>();
    private final List<ThreadConsumidor> consumidores = new ArrayList<>();
    private final List<Thread> threadInstances = new ArrayList<>();

    public MotorSimulacao(int capacidade, int numProdutores, int numConsumidores,
            int itensPorProdutor, int atrasoProdutorMs, int atrasoConsumidorMs,
            int atrasoSecaoCriticaMs, DespachanteEventos despachante) {
        this.capacidade = capacidade;
        this.numProdutores = numProdutores;
        this.numConsumidores = numConsumidores;
        this.itensPorProdutor = itensPorProdutor;
        this.atrasoProdutorMs = atrasoProdutorMs;
        this.atrasoConsumidorMs = atrasoConsumidorMs;
        this.atrasoSecaoCriticaMs = atrasoSecaoCriticaMs;
        this.despachante = despachante;

        inicializarBuffer();
    }

    // cria o buffer correto de acordo com o modo escolhido
    private synchronized void inicializarBuffer() {
        if (modoCaos) {
            this.bufferAtual = new BufferCaos(capacidade, despachante);
        } else {
            this.bufferAtual = new BufferMonitorSincronizado(capacidade, atrasoSecaoCriticaMs, despachante);
        }
    }

    // alterna entre monitor e modo caos e notifica todo mundo
    public synchronized void alternarModoCaos(boolean ativarCaos) {
        if (this.modoCaos != ativarCaos) {
            this.modoCaos = ativarCaos;
            inicializarBuffer();
            despachante.despachar(new EventoSimulacao(
                    "SISTEMA_INICIO", "SISTEMA", "SISTEMA", -1, null,
                    bufferAtual.getOcupacao(), capacidade, bufferAtual.getInIndex(), bufferAtual.getOutIndex(),
                    bufferAtual.getSnapshot(),
                    ativarCaos
                            ? "⚠️ [MODO CAOS ATIVADO] Travas synchronized e primitivas wait/notify foram anuladas!"
                            : "🛡️ [MODO MONITOR ATIVADO] Exclusão mútua e variáveis de condição ativas sob Semântica de Mesa.",
                    ativarCaos
            ));
        }
    }

    // inicia ou retoma as threads
    public synchronized void iniciar() {
        // se tiver pausado, so despausa
        if (emExecucao && pausado) {
            pausado = false;
            produtores.forEach(ThreadProdutor::retomar);
            consumidores.forEach(ThreadConsumidor::retomar);
            despachante.despachar(new EventoSimulacao(
                    "SISTEMA_INICIO", "SISTEMA", "SISTEMA", -1, null,
                    bufferAtual.getOcupacao(), capacidade, bufferAtual.getInIndex(), bufferAtual.getOutIndex(),
                    bufferAtual.getSnapshot(), "▶️ Simulação retomada.", modoCaos
            ));
            return;
        }

        if (emExecucao) {
            return;
        }

        resetar();
        emExecucao = true;
        pausado = false;

        despachante.despachar(new EventoSimulacao(
                "SISTEMA_INICIO", "SISTEMA", "SISTEMA", -1, null,
                bufferAtual.getOcupacao(), capacidade, bufferAtual.getInIndex(), bufferAtual.getOutIndex(),
                bufferAtual.getSnapshot(),
                "🚀 Disparando simulação com " + numProdutores + " Produtores e " + numConsumidores + " Consumidores (" + itensPorProdutor + " itens/produtor). Modo: " + (modoCaos ? "CAOS" : "MONITOR"),
                modoCaos
        ));

        // cria as threads dos produtores
        for (int i = 1; i <= numProdutores; i++) {
            ThreadProdutor p = new ThreadProdutor(i, bufferAtual, itensPorProdutor, atrasoProdutorMs);
            produtores.add(p);
            Thread t = new Thread(p, "Produtor-" + i);
            threadInstances.add(t);
        }

        // divide a cota total de itens pros consumidores
        int totalItens = numProdutores * itensPorProdutor;
        int itensBase = totalItens / numConsumidores;
        int resto = totalItens % numConsumidores;

        for (int i = 1; i <= numConsumidores; i++) {
            int cota = itensBase + (i <= resto ? 1 : 0);
            ThreadConsumidor c = new ThreadConsumidor(i, bufferAtual, cota, atrasoConsumidorMs);
            consumidores.add(c);
            Thread t = new Thread(c, "Consumidor-" + i);
            threadInstances.add(t);
        }

        // dispara todas as threads juntas
        for (Thread t : threadInstances) {
            t.start();
        }

        // supervisora que espera todo mundo terminar com join
        new Thread(() -> {
            for (Thread t : threadInstances) {
                try {
                    t.join();
                } catch (InterruptedException ignored) {
                }
            }
            emExecucao = false;
            despachante.despachar(new EventoSimulacao(
                    "SISTEMA_FIM", "SISTEMA", "SISTEMA", -1, null,
                    bufferAtual.getOcupacao(), capacidade, bufferAtual.getInIndex(), bufferAtual.getOutIndex(),
                    bufferAtual.getSnapshot(),
                    "🏁 Ciclo de execução finalizado com sucesso. Desligamento ordenado (Clean Shutdown).",
                    modoCaos
            ));
        }, "Supervisora-Encerramento").start();
    }

    public synchronized void pausar() {
        if (!emExecucao || pausado) {
            return;
        }
        pausado = true;
        produtores.forEach(ThreadProdutor::pausar);
        consumidores.forEach(ThreadConsumidor::pausar);
        despachante.despachar(new EventoSimulacao(
                "SISTEMA_INICIO", "SISTEMA", "SISTEMA", -1, null,
                bufferAtual.getOcupacao(), capacidade, bufferAtual.getInIndex(), bufferAtual.getOutIndex(),
                bufferAtual.getSnapshot(), "⏸️ Simulação pausada.", modoCaos
        ));
    }

    // para todas as threads com interrupt e limpa a memoria
    public synchronized void resetar() {
        produtores.forEach(ThreadProdutor::parar);
        consumidores.forEach(ThreadConsumidor::parar);
        for (Thread t : threadInstances) {
            t.interrupt();
        }
        produtores.clear();
        consumidores.clear();
        threadInstances.clear();
        emExecucao = false;
        pausado = false;
        inicializarBuffer();
    }

    public void atualizarVelocidade(int pDelay, int cDelay) {
        this.atrasoProdutorMs = pDelay;
        this.atrasoConsumidorMs = cDelay;
        produtores.forEach(p -> p.setAtrasoMs(pDelay));
        consumidores.forEach(c -> c.setAtrasoMs(cDelay));
    }

    public IBufferLimitado getBufferAtual() {
        return bufferAtual;
    }

    public boolean isModoCaos() {
        return modoCaos;
    }

    public boolean isEmExecucao() {
        return emExecucao;
    }

    public boolean isPausado() {
        return pausado;
    }
}
