package br.edu.uern.prodcons.telemetria;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

// Barramento concorrente e thread-safe de eventos de simulação.
// Despacha simultaneamente para o terminal CLI e para o canal de streaming SSE.

public class EventoBroadcaster {
    private final CopyOnWriteArrayList<Consumer<EventoSimulacao>> ouvintes = new CopyOnWriteArrayList<>();

    public void registrarOuvinte(Consumer<EventoSimulacao> ouvinte) {
        ouvintes.add(ouvinte);
    }

    public void removerOuvinte(Consumer<EventoSimulacao> ouvinte) {
        ouvintes.remove(ouvinte);
    }

    public void despachar(EventoSimulacao evento) {
        // Saída 1: Terminal Padrão (CLI com códigos ANSI)
        ConsoleLogger.log(evento);

        // Saída 2: Interface Web (Servidor SSE)
        for (var ouvinte : ouvintes) {
            try {
                ouvinte.accept(evento);
            } catch (Exception e) {
                // Falhas transitórias no socket não interferem na concorrência
            }
        }
    }
}