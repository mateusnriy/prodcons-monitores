package br.edu.uern.prodcons.model;

// Interface comum do buffer limitado (padrao Strategy)
public interface IBufferLimitado {

    // insere item no buffer, bloqueia no wait() se tiver cheio
    void inserir(Item item, String nomeThread) throws InterruptedException;

    // remove item do buffer, bloqueia no wait() se tiver vazio
    Item remover(String nomeThread) throws InterruptedException;

    // quantidade atual de itens no buffer
    int getOcupacao();

    // capacidade maxima do buffer
    int getCapacidade();

    // ponteiro onde vai acontecer a proxima insercao
    int getInIndex();

    // ponteiro de onde vai sair a proxima remocao
    int getOutIndex();

    // copia do array de itens pra nao vazar referencia interna
    Item[] getSnapshot();

    // diz se o buffer atual e o modo caos ou o monitor oficial
    boolean isChaos();
}
