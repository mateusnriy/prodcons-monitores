package br.edu.uern.prodcons.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

// Entrega os arquivos estaticos do frontend (HTML, CSS, JS) empacotados no JAR
public class ManipuladorArquivosEstaticos implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String caminho = exchange.getRequestURI().getPath();

        // se acessar a raiz /, manda pro index.html
        if ("/".equals(caminho) || caminho.isEmpty()) {
            caminho = "/index.html";
        }

        // procura o arquivo dentro da pasta resources/public
        String caminhoRecurso = "/public" + caminho;
        InputStream is = getClass().getResourceAsStream(caminhoRecurso);

        // se o arquivo nao existir no JAR, retorna 404
        if (is == null) {
            String resposta = "404 - Arquivo nao encontrado: " + caminho;
            exchange.sendResponseHeaders(404, resposta.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resposta.getBytes());
            }
            return;
        }

        // define o Content-Type de acordo com a extensao do arquivo
        String tipoConteudo = "text/plain";
        if (caminho.endsWith(".html")) {
            tipoConteudo = "text/html; charset=UTF-8";
        } else if (caminho.endsWith(".css")) {
            tipoConteudo = "text/css; charset=UTF-8";
        } else if (caminho.endsWith(".js")) {
            tipoConteudo = "application/javascript; charset=UTF-8";
        } else if (caminho.endsWith(".svg")) {
            tipoConteudo = "image/svg+xml";
        } else if (caminho.endsWith(".json")) {
            tipoConteudo = "application/json; charset=UTF-8";
        }

        exchange.getResponseHeaders().set("Content-Type", tipoConteudo);
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, 0);

        // faz o streaming do conteudo pro navegador
        try (is; OutputStream os = exchange.getResponseBody()) {
            is.transferTo(os);
        }
    }
}
