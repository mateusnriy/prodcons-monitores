# Simulador do Produtor-Consumidor com monitores

**Universidade do Estado do Rio Grande do Norte (UERN)**  
**Departamento de Ciência da Computação**  
**Disciplina:** Sistemas Operacionais  
**Equipe:**
* Mateus Neri
* Guilherme Lacerda
* José Junior


**Vídeo de apresentação:** [Clique aqui](/arquivos/apresentacao.mp4)

---

## 1. Visão geral do projeto

![Simulador](/arquivos/image.png)

Este projeto implementa a solução clássica do problema da concorrência **Produtor-Consumidor (Buffer Limitado/Bounded Buffer)** utilizando **monitores**. 

O sistema foi construído em quatro blocos:
1. **Núcleo concorrente:** Utilização de travas intrínsecas (`synchronized`), variáveis de condição sob **semântica de mesa (*mesa semantics*)** com `wait()` dentro de laços `while`, e `notifyAll()` para evitar *deadlock* e *starvation*.
2. **Modo caos (demonstração empírica):** Um buffer sem travas (`BufferCaos`), permitindo visualizar em tempo real os efeitos de condições de corrida, como *race conditions*, colisões de ponteiros, *Lost Updates*, *Overflows* e *Underflows*.
3. **Servidor HTTP:** Implementado sobre o servidor padrão do JDK (`com.sun.net.httpserver.HttpServer`), transmitindo telemetria concorrente via **Server-Sent Events (SSE)** em `/api/stream` e recebendo comandos REST em `/api/control/*`.
4. **Interface web:** Dashboard visual para demonstrar de for lúdica a implementação do projeto, foi em HTML5, CSS3 e JavaScript.

---

## 2. Princípios de concorrência e monitores

### 2.1 Exclusão mútua
A manipulação do array de compartimentos circulares (`buffer`), dos ponteiros (`in`, `out`) e do contador de ocupação (`count`) ocorre sob métodos `synchronized`. Isso garante que apenas uma thread por vez execute a seção crítica dentro do monitor.

### 2.2 Semântica de mesa (*mesa syle*)
Diferente da semântica de Hoare (onde quem sinaliza cede o processador imediatamente ao sinalizado), em mesa a thread que acorda vai para a fila de prontas (*ready list*) e precisa recompetir pelo lock do monitor. Por essa razão, as condições de guarda são obrigatoriamente avaliadas em laços `while`:

```java
// Proteção contra acordamento adulterado no Produtor
while (count == capacidade) {
    despachante.despachar(...);
    wait(); // Libera o monitor e suspende a thread
}
```

```java
// Proteção contra acordamento adulterado no Consumidor
while (count == 0) {
    despachante.despachar(...);
    wait(); // Libera o monitor e suspende a thread
}
```

### 2.3 Prevenção de starvation e deadlock
Após inserir ou remover um item, a thread invoca `notifyAll()`. Isso garante que todas as threads na fila de espera (*wait set*) sejam acordadas, permitindo que consumidores e produtores progridam sem o risco de acordar a thread incorreta (que voltaria a dormir e travaria o sistema).

---

## 3. Estrutura do código-fonte
```
src/main/java/br/edu/uern/prodcons/
│
├── Principal.java                             # Ponto de entrada CLI e servidor
│
├── model/
│   ├── Item.java                             # Entidade imutável do item transferido
│   └── IBufferLimitado.java                  # Contrato polimórfico (Strategy) do buffer
│
├── monitor/
│   ├── BufferMonitorSincronizado.java        # Monitor canônico com synchronized/wait/notifyAll
│   └── BufferCaos.java                       # Buffer sem sincronização para testes de anomalias
│
├── telemetria/
│   ├── EventoSimulacao.java                  # Modelo de evento imutável com exportação JSON
│   ├── RegistradorConsole.java               # Impressão formatada no terminal com cores ANSI
│   └── DespachanteEventos.java               # Barramento thread-safe para console e SSE
│
├── threads/
│   ├── ThreadProdutor.java                   # Thread produtora com cota finita
│   ├── ThreadConsumidor.java                 # Thread consumidora com cota finita
│   └── MotorSimulacao.java                   # Orquestrador do pool de threads e ciclo de vida
│
└── server/
    ├── ManipuladorCors.java                  # Tratamento de cabeçalhos CORS e requisições OPTIONS
    ├── ManipuladorFluxoSse.java              # Transmissão SSE em tempo real (/api/stream)
    ├── ManipuladorApiControle.java           # Endpoints REST de controle (/api/control/*)
    ├── ManipuladorArquivosEstaticos.java     # Entrega do frontend empacotado no JAR
    └── ServidorHttpEmbutido.java             # Configuração e inicialização do HttpServer JDK
```

Frontend localizado em `src/main/resources/public/`:
* `index.html`: Layout com dashboard, buffer circular, wait sets e terminal.
* `style.css`: Estilização moderna em tema escuro com indicadores visuais.
* `app.js`: Lógica cliente que consome o canal SSE e emite comandos REST.

---

## 4. Requisitos de ambiente

* **Java Development Kit (JDK):** Versão 21 LTS (ou superior).
* **Apache Maven:** Versão 3.8+ (ou superior).

---

## 5. Como compilar e empacotar

Na pasta raiz do projeto (`prodcons-monitores`), execute:

```bash
mvn clean package
```

O comando irá compilar os fontes Java, copiar os arquivos estáticos e gerar o arquivo executável único:  
`target/prodcons-monitores-1.0.0.jar`

---

## 6. Como executar

### 6.1 Execução padrão

```bash
java -jar target/prodcons-monitores-1.0.0.jar
```

* O servidor subirá na porta `8080`.
* Abra o navegador em: [http://localhost:8080](http://localhost:8080).
* Pela interface gráfica, você pode iniciar, pausar, resetar, alternar o Modo Caos e regular as velocidades.
