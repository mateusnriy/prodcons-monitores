package br.edu.uern.prodcons.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;

import br.edu.uern.prodcons.telemetria.DespachanteEventos;
import br.edu.uern.prodcons.threads.MotorSimulacao;

// Servidor HTTP embutido do JDK padrao (sem precisar de tomcat ou framework pesado)
public class ServidorHttpEmbutido {

    private final int porta;
    private final DespachanteEventos despachante;
    private final MotorSimulacao motor;
    private HttpServer servidor;

    public ServidorHttpEmbutido(int porta, DespachanteEventos despachante, MotorSimulacao motor) {
        this.porta = porta;
        this.despachante = despachante;
        this.motor = motor;
    }

    public void iniciar() throws IOException {
        // cria o servidor na porta configurada (padrao 8080)
        servidor = HttpServer.create(new InetSocketAddress(porta), 0);
        servidor.setExecutor(Executors.newCachedThreadPool());

        // rota de arquivos estaticos (frontend vanilla empacotado no jar)
        servidor.createContext("/", new ManipuladorArquivosEstaticos());

        // canal SSE pra telemetria em tempo real
        servidor.createContext("/api/stream", new ManipuladorFluxoSse(despachante));

        // endpoints REST de controle (start, pause, reset, chaos, speed, status)
        ManipuladorApiControle manipuladorControle = new ManipuladorApiControle(motor);
        servidor.createContext("/api/control", manipuladorControle);
        servidor.createContext("/api/status", manipuladorControle);

        // endpoint simples de healthcheck
        servidor.createContext("/api/health", exchange -> {
            if (ManipuladorCors.tratarPreflight(exchange)) {
                return;
            }
            ManipuladorCors.aplicarCors(exchange);
            byte[] res = "{\"status\":\"UP\",\"servico\":\"Produtor-Consumidor Backend\"}".getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, res.length);
            exchange.getResponseBody().write(res);
            exchange.getResponseBody().close();
        });

        servidor.start();
        System.out.println("\u001B[32m[SISTEMA] Servidor HTTP Embutido rodando em: http://localhost:" + porta + "\u001B[0m");
        System.out.println("\u001B[32m[SISTEMA] Canal de Telemetria SSE pronto em: /api/stream\u001B[0m");
    }

    public void parar() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }
}
