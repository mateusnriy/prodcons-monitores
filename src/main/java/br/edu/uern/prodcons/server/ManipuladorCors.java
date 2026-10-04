package br.edu.uern.prodcons.server;

import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;

// Configura os cabecalhos CORS pra permitir chamadas do navegador sem bloqueio
public class ManipuladorCors {

    // adiciona os headers necessarios pra liberar requisicoes de qualquer origem
    public static void aplicarCors(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    // responde a requisicao OPTIONS (preflight) se o navegador pedir antes do POST
    public static boolean tratarPreflight(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            aplicarCors(exchange);
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }
}
