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
    private volatile int atrasoSecaoCriticaMs;
    private final DespachanteEventos despachante;

    private volatile boolean modoCaos = false;
    private volatile boolean emExecucao = false;
    private volatile boolean pausado = false;

    private IBufferLimitado bufferAtual;
    // controle de pausa/passo compartilhado pelas threads da rodada atual
    private ControleExecucao controle = new ControleExecucao(false);
    private final List<ThreadProdutor> produtores = new ArrayList<>();
    private final List<ThreadConsumidor> consumidores = new ArrayList<>();
    private final List<Thread> threadsAtivas = new ArrayList<>();

    // muda a cada reset, pra supervisora antiga nao anunciar o fim de uma rodada descartada
    private int geracao = 0;

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

    // atalho pra mandar evento de sistema com a foto atual do buffer
    private void despacharSistema(String tipo, String mensagem) {
        despachante.despachar(new EventoSimulacao(
                tipo, "SISTEMA", "SISTEMA", -1, null,
                bufferAtual.getOcupacao(), capacidade, bufferAtual.getInIndex(), bufferAtual.getOutIndex(),
                bufferAtual.getSnapshot(), mensagem, modoCaos
        ));
    }

    // alterna entre monitor e modo caos (suporta troca dinamica a quente)
    public synchronized void alternarModoCaos(boolean ativarCaos) {
        if (this.modoCaos == ativarCaos) {
            return;
        }
        this.modoCaos = ativarCaos;
        IBufferLimitado bufferAntigo = this.bufferAtual;

        // cria o novo buffer conforme o modo escolhido
        if (ativarCaos) {
            this.bufferAtual = new BufferCaos(capacidade, despachante);
        } else {
            this.bufferAtual = new BufferMonitorSincronizado(capacidade, atrasoSecaoCriticaMs, despachante);
        }

        // migra o estado do buffer anterior para o novo
        if (bufferAntigo != null) {
            this.bufferAtual.restaurarEstado(
                    bufferAntigo.getSnapshot(),
                    bufferAntigo.getInIndex(),
                    bufferAntigo.getOutIndex(),
                    bufferAntigo.getOcupacao()
            );
            // se o antigo era monitor sincronizado, acorda threads que estavam no wait set
            if (bufferAntigo instanceof BufferMonitorSincronizado monitorAntigo) {
                synchronized (monitorAntigo) {
                    monitorAntigo.notifyAll();
                }
            }
        }

        despacharSistema("SISTEMA_INICIO", ativarCaos
                ? "⚠️ [MODO CAOS ATIVADO] Travas synchronized e primitivas wait/notify foram anuladas!"
                : "🛡️ [MODO MONITOR ATIVADO] Exclusão mútua e variáveis de condição ativas sob Semântica de Mesa.");
    }

    // inicia do zero ou retoma se tiver pausado
    public synchronized void iniciar() {
        if (emExecucao && pausado) {
            pausado = false;
            controle.retomar();
            despacharSistema("SISTEMA_INICIO", "▶️ Simulação retomada.");
            return;
        }
        if (emExecucao) {
            return;
        }
        dispararThreads(false);
    }

    public synchronized void pausar() {
        if (!emExecucao || pausado) {
            return;
        }
        pausado = true;
        controle.pausar();
        despacharSistema("SISTEMA_PAUSA", "⏸️ Simulação pausada. Use o Passo pra avançar um ciclo por vez.");
    }

    // passo a passo: libera um unico ciclo de uma unica thread
    public synchronized void passo() {
        // se ainda nao comecou, cria as threads ja pausadas
        if (!emExecucao) {
            dispararThreads(true);
        }
        // passo so faz sentido com a simulacao pausada
        if (!pausado) {
            return;
        }
        controle.liberarPasso();
        despacharSistema("SISTEMA_PASSO", "⏭️ Passo liberado: uma única thread vai executar um ciclo.");
    }

    // cria e dispara as threads da rodada (podendo ja comecar pausado pro passo a passo)
    private void dispararThreads(boolean comecarPausado) {
        resetar();
        ThreadProdutor.reiniciarContador();
        emExecucao = true;
        pausado = comecarPausado;
        controle = new ControleExecucao(comecarPausado);
        final int minhaGeracao = geracao;

        despacharSistema("SISTEMA_INICIO",
                "🚀 Disparando simulação com " + numProdutores + " Produtores e " + numConsumidores
                + " Consumidores (" + itensPorProdutor + " itens/produtor). Modo: " + (modoCaos ? "CAOS" : "MONITOR")
                + (comecarPausado ? " [pausada no modo passo a passo]" : ""));

        // cria as threads dos produtores
        for (int i = 1; i <= numProdutores; i++) {
            ThreadProdutor p = new ThreadProdutor(i, this, itensPorProdutor, atrasoProdutorMs, controle);
            produtores.add(p);
            threadsAtivas.add(new Thread(p, p.getNome()));
        }

        // divide a cota total de itens pros consumidores
        int totalItens = numProdutores * itensPorProdutor;
        int itensBase = totalItens / numConsumidores;
        int resto = totalItens % numConsumidores;

        for (int i = 1; i <= numConsumidores; i++) {
            int cota = itensBase + (i <= resto ? 1 : 0);
            ThreadConsumidor c = new ThreadConsumidor(i, this, cota, atrasoConsumidorMs, controle);
            consumidores.add(c);
            threadsAtivas.add(new Thread(c, c.getNome()));
        }

        // dispara todas as threads juntas
        for (Thread t : threadsAtivas) {
            t.start();
        }

        // copia a lista pq o reset limpa a original enquanto a supervisora ainda ta no join
        List<Thread> threadsDaRodada = new ArrayList<>(threadsAtivas);

        // supervisora que espera todo mundo terminar com join
        new Thread(() -> {
            for (Thread t : threadsDaRodada) {
                try {
                    t.join();
                } catch (InterruptedException e) {
                    return;
                }
            }
            synchronized (MotorSimulacao.this) {
                // se teve reset no meio, essa rodada nao vale mais
                if (minhaGeracao != geracao) {
                    return;
                }
                emExecucao = false;
                pausado = false;
                despacharSistema("SISTEMA_FIM",
                        "🏁 Ciclo de execução finalizado com sucesso. Desligamento ordenado (Clean Shutdown).");
            }
        }, "Supervisora-Encerramento").start();
    }

    // para todas as threads e recria o buffer zerado
    public synchronized void resetar() {
        geracao++;
        controle.encerrar();
        for (Thread t : threadsAtivas) {
            t.interrupt();
        }
        produtores.clear();
        consumidores.clear();
        threadsAtivas.clear();
        emExecucao = false;
        pausado = false;
        inicializarBuffer();
    }

    // muda as velocidades em tempo real (inclusive a secao critica)
    public synchronized void atualizarVelocidade(int atrasoProdutor, int atrasoConsumidor, int atrasoSecaoCritica) {
        this.atrasoProdutorMs = atrasoProdutor;
        this.atrasoConsumidorMs = atrasoConsumidor;
        this.atrasoSecaoCriticaMs = atrasoSecaoCritica;
        produtores.forEach(p -> p.setAtrasoMs(atrasoProdutor));
        consumidores.forEach(c -> c.setAtrasoMs(atrasoConsumidor));
        // so o monitor tem secao critica com atraso, o caos nao tem trava
        if (bufferAtual instanceof BufferMonitorSincronizado monitor) {
            monitor.setAtrasoSecaoCriticaMs(atrasoSecaoCritica);
        }
    }

    public IBufferLimitado getBufferAtual() {
        return bufferAtual;
    }

    public int getAtrasoProdutorMs() {
        return atrasoProdutorMs;
    }

    public int getAtrasoConsumidorMs() {
        return atrasoConsumidorMs;
    }

    public int getAtrasoSecaoCriticaMs() {
        return atrasoSecaoCriticaMs;
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
