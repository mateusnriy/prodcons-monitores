package br.edu.uern.prodcons.model;

// DTO Representação imutável de um item transferido pelo Bounded Buffer.

public class Item {

    // O 'final' impede qualquer reatribuição após o construtor.
    private final int id;
    private final String nomeProdutor;
    private final long timestamp; 

    // Recebe o identificador único e o nome do produtor de origem.
    public Item(int id, String nomeProdutor) {
        this.id = id;
        this.nomeProdutor = nomeProdutor;
        this.timestamp = System.currentTimeMillis();
        // O timestamp é obtido automaticamente na hora em que o objeto é instanciado na memória.
    }

    // Getters para acessar os atributos do item. Não há setters, pois o objeto é imutável.
    public int getId() {
        return id;
    }

    public String getNomeProdutor() {
        return nomeProdutor;
    }

    public long getTimestamp() {
        return timestamp;
    }

    // Fornece uma representação em string do item, incluindo seu identificador e o nome do produtor.  
    @Override
    public String toString() {
        return "Item #" + id + " (por " + nomeProdutor + ")";
    }

}
