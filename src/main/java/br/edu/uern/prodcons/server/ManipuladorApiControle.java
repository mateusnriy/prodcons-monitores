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
            // endpoint pra iniciar ou despausar as threads
            if (caminho.endsWith("/start") && "POST".equalsIgnoreCase(metodo)) {
                motor.iniciar();
                respostaJson = "{\"status\":\"ok\",\"action\":\"iniciado\"}";
            } else if (caminho.endsWith("/pause") && "POST".equalsIgnoreCase(metodo)) {
                // endpoint pra congelar temporariamente as threads
                motor.pausar();
                respostaJson = "{\"status\":\"ok\",\"action\":\"pausado\"}";
            } else if (caminho.endsWith("/reset") && "POST".equalsIgnoreCase(metodo)) {
                // endpoint pra parar tudo e recriar o buffer do zero
                motor.resetar();
                respostaJson = "{\"status\":\"ok\",\"action\":\"resetado\"}";
            } else if (caminho.endsWith("/chaos") && "POST".equalsIgnoreCase(metodo)) {
                // ativa ou desativa o modo sem travas (Modo Caos)
                boolean ativo = query != null && query.contains("active=true");
                motor.alternarModoCaos(ativo);
                respostaJson = "{\"status\":\"ok\",\"modoCaos\":" + ativo + "}";
            } else if (caminho.endsWith("/speed") && "POST".equalsIgnoreCase(metodo)) {
                // atualiza a velocidade dos produtores e consumidores dinamicamente
                int atrasoProdutor = 1200;
                int atrasoConsumidor = 1400;
                if (query != null) {
                    for (String parametro : query.split("&")) {
                        String[] par = parametro.split("=");
                        if (par.length == 2) {
                            if ("producerDelay".equals(par[0])) {
                                atrasoProdutor = Integer.parseInt(par[1]);
                            }
                            if ("consumerDelay".equals(par[0])) {
                                atrasoConsumidor = Integer.parseInt(par[1]);
                            }
                        }
                    }
                }
                motor.atualizarVelocidade(atrasoProdutor, atrasoConsumidor);
                respostaJson = "{\"status\":\"ok\",\"producerDelay\":" + atrasoProdutor + ",\"consumerDelay\":" + atrasoConsumidor + "}";
            } else if (caminho.endsWith("/status") && "GET".equalsIgnoreCase(metodo)) {
                // retorna o status atual das variaveis de controle
                respostaJson = String.format(
                        "{\"emExecucao\":%b,\"pausado\":%b,\"modoCaos\":%b,\"ocupacao\":%d,\"capacidade\":%d}",
                        motor.isEmExecucao(), motor.isPausado(), motor.isModoCaos(),
                        motor.getBufferAtual().getOcupacao(), motor.getBufferAtual().getCapacidade()
                );
            } else {
                codigoStatus = 404;
                respostaJson = "{\"erro\":\"Endpoint de controle nao encontrado\"}";
            }
        } catch (Exception e) {
            codigoStatus = 500;
            respostaJson = "{\"erro\":\"" + e.getMessage() + "\"}";
        }

        // escreve o json de resposta pro cliente HTTP
        byte[] bytes = respostaJson.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(codigoStatus, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
