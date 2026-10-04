package br.edu.uern.prodcons.server;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import br.edu.uern.prodcons.telemetria.DespachanteEventos;
import br.edu.uern.prodcons.telemetria.EventoSimulacao;

// Envia os eventos em tempo real pro navegador usando Server-Sent Events (SSE)
public class ManipuladorFluxoSse implements HttpHandler {

    // guarda os fluxos de saida de cada navegador conectado
    private final List<OutputStream> clientesConectados = new CopyOnWriteArrayList<>();

    public ManipuladorFluxoSse(DespachanteEventos despachante) {
        // registra a transmissao de eventos no despachante
        despachante.registrarOuvinte(this::transmitirEvento);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // responde se for requisicao de preflight do CORS
        if (ManipuladorCors.tratarPreflight(exchange)) {
            return;
        }

        // cabecalhos padrao pra manter a conexao SSE aberta continuamente
        ManipuladorCors.aplicarCors(exchange);
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);

        OutputStream os = exchange.getResponseBody();
        clientesConectados.add(os);

        // mensagem inicial de boas-vindas avisando que conectou
        String dadoInicial = "data: {\"type\":\"CONEXAO_ESTABELECIDA\",\"message\":\"Conectado ao Backend Java SSE\"}\n\n";
        try {
            os.write(dadoInicial.getBytes(StandardCharsets.UTF_8));
            os.flush();
        } catch (IOException e) {
            clientesConectados.remove(os);
        }
    }

    // envia o evento formatado como data: {...}\n\n pra cada cliente ativo
    private void transmitirEvento(EventoSimulacao evento) {
        String data = "data: " + evento.toJson() + "\n\n";
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);

        for (OutputStream cliente : clientesConectados) {
            try {
                cliente.write(bytes);
                cliente.flush();
            } catch (IOException e) {
                // se a aba do navegador fechou, tira da lista
                clientesConectados.remove(cliente);
            }
        }
    }
}
