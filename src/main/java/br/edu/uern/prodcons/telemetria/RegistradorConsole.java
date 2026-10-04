package br.edu.uern.prodcons.telemetria;

// Imprime os eventos no terminal com cores ANSI
public class RegistradorConsole {

    // codigos de escape pra colorir o texto no terminal
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_VERDE = "\u001B[32m";
    private static final String ANSI_AZUL = "\u001B[34m";
    private static final String ANSI_AMARELO = "\u001B[33m";
    private static final String ANSI_CIANO = "\u001B[36m";
    private static final String ANSI_VERMELHO = "\u001B[31m\u001B[1m";
    private static final String ANSI_MAGENTA = "\u001B[35m";
    private static final String ANSI_BRANCO = "\u001B[37m";

    // escolhe a cor dependendo do tipo do evento
    public static void log(EventoSimulacao evento) {
        String cor = switch (evento.getType()) {
            case "PRODUCAO_SUCESSO" -> ANSI_VERDE;
            case "CONSUMO_SUCESSO" -> ANSI_AZUL;
            case "BLOQUEIO_CHEIO", "BLOQUEIO_VAZIO" -> ANSI_AMARELO;
            case "NOTIFICACAO", "SISTEMA_PASSO" -> ANSI_CIANO;
            case "ANOMALIA_OVERFLOW", "ANOMALIA_UNDERFLOW", "ANOMALIA_LOST_UPDATE", "COLISAO" -> ANSI_VERMELHO;
            case "SISTEMA_INICIO", "SISTEMA_FIM", "SISTEMA_PAUSA" -> ANSI_MAGENTA;
            default -> ANSI_BRANCO;
        };

        System.out.println("[" + evento.getTimestamp() + "] " + cor + evento.getMessage() + ANSI_RESET);
    }
}
