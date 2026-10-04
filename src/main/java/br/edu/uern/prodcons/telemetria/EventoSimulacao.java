package br.edu.uern.prodcons.telemetria;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

import br.edu.uern.prodcons.model.Item;

// Evento imutavel que guarda a foto do estado a cada mudanca
public class EventoSimulacao {

    private static final DateTimeFormatter FORMATADOR_HORA = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final AtomicLong GERADOR_ID = new AtomicLong(1);

    private final long id;
    private final String timestamp;
    private final String type;
    private final String threadName;
    private final String threadRole; // "PRODUTOR", "CONSUMIDOR" ou "SISTEMA"
    private final int slotIndex;
    private final Integer itemId;
    private final int count;
    private final int capacity;
    private final int inIndex;
    private final int outIndex;
    private final Integer[] slots;
    private final String message;
    private final boolean isChaos;

    public EventoSimulacao(String type, String threadName, String threadRole, int slotIndex,
            Integer itemId, int count, int capacity, int inIndex, int outIndex,
            Item[] snapshot, String message, boolean isChaos) {
        this.id = GERADOR_ID.getAndIncrement();
        this.timestamp = LocalTime.now().format(FORMATADOR_HORA);
        this.type = type;
        this.threadName = threadName;
        this.threadRole = threadRole;
        this.slotIndex = slotIndex;
        this.itemId = itemId;
        this.count = count;
        this.capacity = capacity;
        this.inIndex = inIndex;
        this.outIndex = outIndex;
        this.message = message;
        this.isChaos = isChaos;

        // extrai so os IDs dos itens pra montar o array do json
        this.slots = new Integer[capacity];
        if (snapshot != null) {
            for (int i = 0; i < capacity && i < snapshot.length; i++) {
                this.slots[i] = (snapshot[i] != null) ? snapshot[i].getId() : null;
            }
        }
    }

    public long getId() { return id; }
    public String getTimestamp() { return timestamp; }
    public String getType() { return type; }
    public String getThreadName() { return threadName; }
    public String getThreadRole() { return threadRole; }
    public int getSlotIndex() { return slotIndex; }
    public Integer getItemId() { return itemId; }
    public int getCount() { return count; }
    public int getCapacity() { return capacity; }
    public int getInIndex() { return inIndex; }
    public int getOutIndex() { return outIndex; }
    public Integer[] getSlots() { return slots; }
    public String getMessage() { return message; }
    public boolean isChaos() { return isChaos; }

    // monta o json na mao sem precisar de biblioteca externa
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":").append(id).append(",");
        sb.append("\"timestamp\":\"").append(timestamp).append("\",");
        sb.append("\"type\":\"").append(type).append("\",");
        sb.append("\"threadName\":\"").append(threadName).append("\",");
        sb.append("\"threadRole\":\"").append(threadRole).append("\",");
        sb.append("\"slotIndex\":").append(slotIndex).append(",");
        sb.append("\"itemId\":").append(itemId == null ? "null" : itemId).append(",");
        sb.append("\"count\":").append(count).append(",");
        sb.append("\"capacity\":").append(capacity).append(",");
        sb.append("\"inIndex\":").append(inIndex).append(",");
        sb.append("\"outIndex\":").append(outIndex).append(",");
        sb.append("\"isChaos\":").append(isChaos).append(",");
        sb.append("\"message\":\"").append(message.replace("\"", "\\\"")).append("\",");
        sb.append("\"slots\":[");
        for (int i = 0; i < slots.length; i++) {
            sb.append(slots[i] == null ? "null" : slots[i]);
            if (i < slots.length - 1) {
                sb.append(",");
            }
        }
        sb.append("]}");
        return sb.toString();
    }
}
