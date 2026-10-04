package br.edu.uern.prodcons.model;

// Representa o item transferido no buffer limitado
public class Item {

    // final pra garantir que o item seja imutavel e seguro entre threads
    private final int id;
    private final String nomeProdutor;
    private final long timestamp;

    public Item(int id, String nomeProdutor) {
        this.id = id;
        this.nomeProdutor = nomeProdutor;
        this.timestamp = System.currentTimeMillis();
    }

    public int getId() {
        return id;
    }

    public String getNomeProdutor() {
        return nomeProdutor;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "Item #" + id + " (por " + nomeProdutor + ")";
    }
}
