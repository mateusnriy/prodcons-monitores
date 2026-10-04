package br.edu.uern.prodcons.model;

public interface IBoundedBuffer {

    // Insere um item gerado pelo produtor especificado em threadName.
    // Pode bloquear via wait() se o buffer estiver cheio, lançando InterruptedException.
    void inserir(Item item, String threadName) throws InterruptedException;

    // Extrai e remove o próximo item disponível para o consumidor especificado.
    // Pode bloquear via wait() se o buffer estiver vazio, lançando InterruptedException.
    Item remover(String threadName) throws InterruptedException;

    // Retorna a quantidade atual de itens no buffer (variável 'count')
    int getOcupacao();

    // Retorna o tamanho máximo físico do buffer (variável 'capacidade')
    int getCapacidade();

    // Retorna o índice do próximo slot onde será feita uma inserção (ponteiro 'in')
    int getInIndex();

    // Retorna o índice do próximo slot de onde será feita uma remoção (ponteiro 'out')
    int getOutIndex();

    // Retorna uma cópia defensiva do array de slots para fins de telemetria
    Item[] getSnapshot();

    // Informa se a instância ativa é o Modo Caos (sem sincronização) ou o Monitor Canônico
    boolean isChaos();
}