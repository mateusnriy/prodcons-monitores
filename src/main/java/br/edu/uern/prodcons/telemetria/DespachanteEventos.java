package br.edu.uern.prodcons.telemetria;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

// Barramento de eventos thread-safe que manda pro terminal e pro SSE
public class DespachanteEventos {

    // lista thread-safe pra registrar ouvintes sem travar quem ta lendo
    private final CopyOnWriteArrayList<Consumer<EventoSimulacao>> ouvintes = new CopyOnWriteArrayList<>();

    public void registrarOuvinte(Consumer<EventoSimulacao> ouvinte) {
        ouvintes.add(ouvinte);
    }

    public void removerOuvinte(Consumer<EventoSimulacao> ouvinte) {
        ouvintes.remove(ouvinte);
    }

    public void despachar(EventoSimulacao evento) {
        // imprime direto no console com as cores ANSI
        RegistradorConsole.log(evento);

        // transmite pro frontend web (canal SSE)
        for (var ouvinte : ouvintes) {
            try {
                ouvinte.accept(evento);
            } catch (Exception e) {
                // se o socket do navegador cair, nao afeta as threads
            }
        }
    }
}
