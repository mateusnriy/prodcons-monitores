package br.edu.uern.prodcons.server;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import br.edu.uern.prodcons.threads.MotorSimulacao;

// Trata as chamadas REST que chegam da interface web pra controlar a simulacao
public class ManipuladorApiControle implements HttpHandler {

    private final MotorSimulacao motor;

    public ManipuladorApiControle(MotorSimulacao motor) {
        this.motor = motor;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // responde requisicao preflight de navegadores
        if (ManipuladorCors.tratarPreflight(exchange)) {
            return;
        }

        ManipuladorCors.aplicarCors(exchange);
        String caminho = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();
        String metodo = exchange.getRequestMethod();

        String respostaJson = "{\"status\":\"ok\"}";
        int codigoStatus = 200;

        try {
            if (caminho.endsWith("/start") && "POST".equalsIgnoreCase(metodo)) {
                // inicia do zero ou retoma se tiver pausado
                motor.iniciar();
                respostaJson = "{\"status\":\"ok\",\"action\":\"iniciado\"}";
            } else if (caminho.endsWith("/pause") && "POST".equalsIgnoreCase(metodo)) {
                // congela as threads antes da proxima operacao no buffer
                motor.pausar();
                respostaJson = "{\"status\":\"ok\",\"action\":\"pausado\"}";
            } else if (caminho.endsWith("/step") && "POST".equalsIgnoreCase(metodo)) {
                // libera um unico ciclo de uma unica thread (passo a passo)
                motor.passo();
                respostaJson = "{\"status\":\"ok\",\"action\":\"passo\"}";
            } else if (caminho.endsWith("/reset") && "POST".equalsIgnoreCase(metodo)) {
                // para tudo e recria o buffer do zero
                motor.resetar();
                respostaJson = "{\"status\":\"ok\",\"action\":\"resetado\"}";
            } else if (caminho.endsWith("/chaos") && "POST".equalsIgnoreCase(metodo)) {
                // troca entre monitor e modo caos (so com a simulacao parada)
                boolean ativo = query != null && query.contains("active=true");
                motor.alternarModoCaos(ativo);
                respostaJson = "{\"status\":\"ok\",\"modoCaos\":" + ativo + "}";
            } else if (caminho.endsWith("/speed") && "POST".equalsIgnoreCase(metodo)) {
                // se algum parametro nao vier, mantem o valor atual do motor
                int atrasoProdutor = lerParametro(query, "producerDelay", motor.getAtrasoProdutorMs());
                int atrasoConsumidor = lerParametro(query, "consumerDelay", motor.getAtrasoConsumidorMs());
                int atrasoSecaoCritica = lerParametro(query, "criticalDelay", motor.getAtrasoSecaoCriticaMs());
                motor.atualizarVelocidade(atrasoProdutor, atrasoConsumidor, atrasoSecaoCritica);
                respostaJson = "{\"status\":\"ok\",\"producerDelay\":" + atrasoProdutor
                        + ",\"consumerDelay\":" + atrasoConsumidor
                        + ",\"criticalDelay\":" + atrasoSecaoCritica + "}";
            } else if (caminho.endsWith("/status") && "GET".equalsIgnoreCase(metodo)) {
                // devolve tudo que a tela precisa pra se sincronizar quando abre/recarrega
                respostaJson = String.format(
                        "{\"emExecucao\":%b,\"pausado\":%b,\"modoCaos\":%b,\"ocupacao\":%d,\"capacidade\":%d,"
                        + "\"inIndex\":%d,\"outIndex\":%d,"
                        + "\"atrasoProdutor\":%d,\"atrasoConsumidor\":%d,\"atrasoSecaoCritica\":%d}",
                        motor.isEmExecucao(), motor.isPausado(), motor.isModoCaos(),
                        motor.getBufferAtual().getOcupacao(), motor.getBufferAtual().getCapacidade(),
                        motor.getBufferAtual().getInIndex(), motor.getBufferAtual().getOutIndex(),
                        motor.getAtrasoProdutorMs(), motor.getAtrasoConsumidorMs(), motor.getAtrasoSecaoCriticaMs()
                );
            } else {
                codigoStatus = 404;
                respostaJson = "{\"erro\":\"Endpoint de controle nao encontrado\"}";
            }
        } catch (IllegalStateException e) {
            // acao nao permitida no estado atual (ex: trocar modo rodando)
            codigoStatus = 409;
            respostaJson = "{\"erro\":\"" + escaparJson(e.getMessage()) + "\"}";
        } catch (Exception e) {
            codigoStatus = 500;
            respostaJson = "{\"erro\":\"" + escaparJson(e.getMessage()) + "\"}";
        }

        // escreve o json de resposta pro cliente HTTP
        byte[] bytes = respostaJson.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(codigoStatus, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // pega um parametro inteiro da query string (ex: producerDelay=300), nunca negativo
    private static int lerParametro(String query, String nome, int padrao) {
        if (query == null) {
            return padrao;
        }
        for (String parametro : query.split("&")) {
            String[] par = parametro.split("=");
            if (par.length == 2 && nome.equals(par[0])) {
                return Math.max(0, Integer.parseInt(par[1]));
            }
        }
        return padrao;
    }

    // evita quebrar o json se a mensagem de erro tiver aspas
    private static String escaparJson(String texto) {
        if (texto == null) {
            return "erro desconhecido";
        }
        return texto.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
