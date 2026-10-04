package br.edu.uern.prodcons;

import br.edu.uern.prodcons.server.ServidorHttpEmbutido;
import br.edu.uern.prodcons.telemetria.DespachanteEventos;
import br.edu.uern.prodcons.threads.MotorSimulacao;

// Ponto de entrada CLI e servidor do projeto Bounded Buffer com monitores
public class Principal {

    public static void main(String[] args) throws Exception {
        boolean modoCaos = false;
        boolean iniciarWeb = true;
        boolean autoIniciar = false;
        int porta = 8080;
        int capacidade = 10;
        int numProdutores = 3;
        int numConsumidores = 3;
        int itensPorProdutor = 50;
        int atrasoProdutor = 1200;
        int atrasoConsumidor = 1400;
        int atrasoSecaoCritica = 180;

        // faz o parse dos parametros passados pela linha de comando
        for (String arg : args) {
            if ("--chaos".equalsIgnoreCase(arg)) {
                modoCaos = true;
            } else if ("--no-web".equalsIgnoreCase(arg)) {
                iniciarWeb = false;
            } else if ("--auto".equalsIgnoreCase(arg)) {
                autoIniciar = true;
            } else if (arg.startsWith("--port=")) {
                porta = Integer.parseInt(arg.substring(7));
            } else if (arg.startsWith("--capacity=")) {
                capacidade = Integer.parseInt(arg.substring(11));
            } else if (arg.startsWith("--producers=")) {
                numProdutores = Integer.parseInt(arg.substring(12));
            } else if (arg.startsWith("--consumers=")) {
                numConsumidores = Integer.parseInt(arg.substring(12));
            } else if (arg.startsWith("--items=")) {
                itensPorProdutor = Integer.parseInt(arg.substring(8));
            }
        }

        // cria o despachante de eventos que manda logs pro console e pro SSE
        DespachanteEventos despachante = new DespachanteEventos();

        // cria o motor de simulacao com as configuracoes informadas
        MotorSimulacao motor = new MotorSimulacao(
                capacidade, numProdutores, numConsumidores, itensPorProdutor,
                atrasoProdutor, atrasoConsumidor, atrasoSecaoCritica, despachante
        );

        if (modoCaos) {
            motor.alternarModoCaos(true);
        }

        if (iniciarWeb) {
            ServidorHttpEmbutido servidor = new ServidorHttpEmbutido(porta, despachante, motor);
            servidor.iniciar();
        }

        if (autoIniciar || !iniciarWeb) {
            System.out.println("[*] Disparando simulacao automatica no terminal...");
            motor.iniciar();
        } else {
            System.out.println("[*] Servidor ativo! Abra o navegador em http://localhost:" + porta);
        }
    }
}
