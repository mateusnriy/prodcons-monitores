// Logica do frontend vanilla pra controlar e visualizar a simulacao em tempo real

// ---------- estado local da aplicacao ----------
// estado do botao principal: ocioso -> executando <-> pausado ; concluido quando as threads terminam
let estado = "ocioso";
let modoCaos = false;
let capacidade = 10;
let ocupacao = 0;
let indiceIn = 0;
let indiceOut = 0;
let itensSlots = new Array(capacidade).fill(null);

// guarda pra qual capacidade o anel foi desenhado (se mudar, redesenha)
let capacidadeDesenhada = 0;

// angulo acumulado dos ponteiros, assim a animacao gira pelo caminho mais curto
let anguloIn = 0;
let anguloOut = 0;

// conjuntos de threads bloqueadas no wait()
const produtoresBloqueados = new Set();
const consumidoresBloqueados = new Set();

// valores usados pelos atalhos pedagogicos (em ms)
const ATALHO_CHEIO = { produtor: 300, consumidor: 3000 };
const ATALHO_VAZIO = { produtor: 3000, consumidor: 300 };

// limite de linhas no terminal pra pagina nao ficar pesada
const MAX_LINHAS_TERMINAL = 500;

// icones do botao principal
const ICONES = {
  play: '<svg class="icone" viewBox="0 0 24 24"><path d="M7 4.5v15l13-7.5z" fill="currentColor"/></svg>',
  pause: '<svg class="icone" viewBox="0 0 24 24"><rect x="6" y="4.5" width="4" height="15" rx="1" fill="currentColor"/><rect x="14" y="4.5" width="4" height="15" rx="1" fill="currentColor"/></svg>',
  check: '<svg class="icone" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg>'
};

// geometria do anel (coordenadas do viewBox 400x400)
const SVG_NS = "http://www.w3.org/2000/svg";
const CX = 200;
const CY = 200;
const R_EXT = 155;
const R_INT = 95;

// ---------- elementos da pagina ----------
const elSseStatus = document.getElementById("sse-status");
const elSseText = document.getElementById("sse-text");
const elStatusDot = elSseStatus.querySelector(".status-dot");

const btnModoMonitor = document.getElementById("btn-modo-monitor");
const btnModoCaos = document.getElementById("btn-modo-caos");
const elModeToggle = document.getElementById("mode-toggle");

const elOccupancy = document.getElementById("metric-occupancy");
const elOccupancyProgress = document.getElementById("occupancy-progress");
const elInvariant = document.getElementById("metric-invariant");
const elInvariantSub = document.getElementById("invariant-sub");
const elPtrIn = document.getElementById("ptr-in");
const elPtrOut = document.getElementById("ptr-out");

const elBufferSvg = document.getElementById("buffer-svg");
const elTituloCapacidade = document.getElementById("titulo-capacidade");
const elListWaitProd = document.getElementById("list-wait-prod");
const elListWaitCons = document.getElementById("list-wait-cons");
const elCountWaitProd = document.getElementById("count-wait-prod");
const elCountWaitCons = document.getElementById("count-wait-cons");

const elTerminal = document.getElementById("terminal-logs");
const btnClearLogs = document.getElementById("btn-clear-logs");

const btnPrincipal = document.getElementById("btn-principal");
const elIconePrincipal = document.getElementById("icone-principal");
const elTextoPrincipal = document.getElementById("texto-principal");
const btnPasso = document.getElementById("btn-passo");
const btnReset = document.getElementById("btn-reset");
const btnForcarCheio = document.getElementById("btn-forcar-cheio");
const btnForcarVazio = document.getElementById("btn-forcar-vazio");

const sliderProd = document.getElementById("slider-producer");
const sliderCons = document.getElementById("slider-consumer");
const sliderCritica = document.getElementById("slider-critical");
const valProd = document.getElementById("val-producer");
const valCons = document.getElementById("val-consumer");
const valCritica = document.getElementById("val-critical");

// ---------- desenho do anel em SVG ----------

// cria um elemento SVG ja com os atributos
function criarSvg(tag, atributos = {}) {
  const el = document.createElementNS(SVG_NS, tag);
  for (const [nome, valor] of Object.entries(atributos)) {
    el.setAttribute(nome, valor);
  }
  return el;
}

// converte (raio, angulo) em (x, y). angulo 0 = topo, crescendo no sentido horario
function polar(raio, anguloGraus) {
  const rad = ((anguloGraus - 90) * Math.PI) / 180;
  return [CX + raio * Math.cos(rad), CY + raio * Math.sin(rad)];
}

// monta o "pedaco de pizza vazado" de cada slot
function caminhoSetor(i) {
  const passo = 360 / capacidade;
  const folga = 1.5; // espacinho entre os slots
  const a0 = i * passo + folga;
  const a1 = (i + 1) * passo - folga;
  const arcoGrande = a1 - a0 > 180 ? 1 : 0;
  const [x0, y0] = polar(R_EXT, a0);
  const [x1, y1] = polar(R_EXT, a1);
  const [x2, y2] = polar(R_INT, a1);
  const [x3, y3] = polar(R_INT, a0);
  return `M ${x0} ${y0} A ${R_EXT} ${R_EXT} 0 ${arcoGrande} 1 ${x1} ${y1} ` +
         `L ${x2} ${y2} A ${R_INT} ${R_INT} 0 ${arcoGrande} 0 ${x3} ${y3} Z`;
}

// desenha o anel inteiro (slots, ponteiros e centro)
function construirBuffer() {
  elBufferSvg.innerHTML = "";
  capacidadeDesenhada = capacidade;
  elTituloCapacidade.textContent = capacidade;
  const passo = 360 / capacidade;

  // trilha escura de fundo
  elBufferSvg.appendChild(criarSvg("circle", {
    cx: CX, cy: CY, r: (R_EXT + R_INT) / 2,
    class: "anel-trilha", "stroke-width": R_EXT - R_INT + 8
  }));

  // um setor por slot
  for (let i = 0; i < capacidade; i++) {
    const meio = (i + 0.5) * passo;
    const grupo = criarSvg("g", { class: "setor", id: "setor-" + i });
    grupo.appendChild(criarSvg("path", { d: caminhoSetor(i), class: "setor-forma" }));

    const [xi, yi] = polar(R_EXT - 13, meio);
    const indice = criarSvg("text", { x: xi, y: yi, class: "setor-indice" });
    indice.textContent = i;

    const [xc, yc] = polar((R_EXT + R_INT) / 2 - 5, meio);
    const conteudo = criarSvg("text", { x: xc, y: yc, class: "setor-item", id: "setor-item-" + i });

    grupo.append(indice, conteudo);
    elBufferSvg.appendChild(grupo);
  }

  // ponteiro IN: fica do lado de fora apontando pra dentro
  const ponteiroIn = criarSvg("g", { class: "ponteiro ponteiro-in", id: "ponteiro-in" });
  ponteiroIn.appendChild(criarSvg("polygon", {
    points: `${CX - 9},${CY - R_EXT - 21} ${CX + 9},${CY - R_EXT - 21} ${CX},${CY - R_EXT - 5}`
  }));

  // ponteiro OUT: fica do lado de dentro apontando pra fora
  const ponteiroOut = criarSvg("g", { class: "ponteiro ponteiro-out", id: "ponteiro-out" });
  ponteiroOut.appendChild(criarSvg("polygon", {
    points: `${CX - 9},${CY - R_INT + 21} ${CX + 9},${CY - R_INT + 21} ${CX},${CY - R_INT + 5}`
  }));

  // miolo com a ocupacao
  const fundoCentro = criarSvg("circle", { cx: CX, cy: CY, r: R_INT - 28, class: "centro-fundo" });
  const textoOcupacao = criarSvg("text", { x: CX, y: CY - 10, class: "centro-ocupacao", id: "centro-ocupacao" });
  const textoLegenda = criarSvg("text", { x: CX, y: CY + 16, class: "centro-legenda" });
  textoLegenda.textContent = "ocupados";
  const textoPonteiros = criarSvg("text", { x: CX, y: CY + 34, class: "centro-ponteiros", id: "centro-ponteiros" });

  elBufferSvg.append(ponteiroIn, ponteiroOut, fundoCentro, textoOcupacao, textoLegenda, textoPonteiros);

  // depois de redesenhar, os ponteiros comecam do topo de novo
  anguloIn = 0;
  anguloOut = 0;
}

// calcula o proximo angulo do ponteiro sem dar a volta inteira a toa
function proximoAngulo(atual, indice) {
  const alvo = (indice + 0.5) * (360 / capacidade);
  const delta = ((((alvo - atual) % 360) + 540) % 360) - 180; // normaliza pra [-180, 180)
  return atual + delta;
}

// atualiza a exibicao do anel e das metricas com os dados mais recentes
function renderizarBuffer() {
  if (capacidade !== capacidadeDesenhada) {
    construirBuffer();
  }

  elBufferSvg.classList.toggle("modo-caos", modoCaos);

  for (let i = 0; i < capacidade; i++) {
    const grupo = document.getElementById("setor-" + i);
    const texto = document.getElementById("setor-item-" + i);
    if (!grupo || !texto) continue;

    const itemId = itensSlots[i];
    const ocupado = itemId !== null && itemId !== undefined;
    grupo.classList.toggle("ocupado", ocupado);
    texto.textContent = ocupado ? "#" + itemId : "";

    // destaca o slot que o in / out ta apontando
    grupo.classList.toggle("alvo-in", i === indiceIn);
    grupo.classList.toggle("alvo-out", i === indiceOut);
  }

  // gira os ponteiros ate o slot certo
  anguloIn = proximoAngulo(anguloIn, indiceIn);
  anguloOut = proximoAngulo(anguloOut, indiceOut);
  document.getElementById("ponteiro-in").style.transform = `rotate(${anguloIn}deg)`;
  document.getElementById("ponteiro-out").style.transform = `rotate(${anguloOut}deg)`;

  // validacao da invariante: 0 <= count <= N
  const invarianteOk = ocupacao >= 0 && ocupacao <= capacidade;

  const centro = document.getElementById("centro-ocupacao");
  centro.textContent = `${ocupacao}/${capacidade}`;
  centro.classList.toggle("violado", !invarianteOk);
  document.getElementById("centro-ponteiros").textContent = `in=${indiceIn} · out=${indiceOut}`;

  // mostradores e barra de progresso
  elOccupancy.textContent = `${ocupacao} / ${capacidade}`;
  const pct = Math.min(100, Math.max(0, (ocupacao / capacidade) * 100));
  elOccupancyProgress.style.width = pct + "%";
  elPtrIn.textContent = indiceIn;
  elPtrOut.textContent = indiceOut;

  if (invarianteOk) {
    elInvariant.textContent = "PRESERVADA";
    elInvariant.className = "metric-value status-ok";
    elInvariantSub.textContent = "Sem violações de limite";
  } else {
    elInvariant.textContent = "VIOLADA!";
    elInvariant.className = "metric-value status-violado";
    elInvariantSub.textContent = `Ocupação anômala: ${ocupacao} fora do intervalo [0, ${capacidade}]`;
  }
}

// faz o slot piscar quando alguem escreve / le nele
function piscarSetor(indice, classe) {
  const grupo = document.getElementById("setor-" + indice);
  if (!grupo) return;
  grupo.classList.remove(classe);
  void grupo.getBoundingClientRect(); // forca o navegador a reiniciar a animacao
  grupo.classList.add(classe);
  setTimeout(() => grupo.classList.remove(classe), 900);
}

// ---------- wait sets ----------

// atualiza a lista de threads bloqueadas nas baias de espera
function renderizarWaitSets() {
  renderizarListaEspera(produtoresBloqueados, elListWaitProd, elCountWaitProd, "Nenhum produtor bloqueado");
  renderizarListaEspera(consumidoresBloqueados, elListWaitCons, elCountWaitCons, "Nenhum consumidor bloqueado");
}

function renderizarListaEspera(conjunto, elLista, elContador, textoVazio) {
  elContador.textContent = conjunto.size;
  elLista.innerHTML = "";
  if (conjunto.size === 0) {
    const dica = document.createElement("span");
    dica.className = "empty-hint";
    dica.textContent = textoVazio;
    elLista.appendChild(dica);
    return;
  }
  conjunto.forEach(nome => {
    const item = document.createElement("div");
    item.className = "waitset-item";
    const spanNome = document.createElement("span");
    spanNome.textContent = nome;
    const spanWait = document.createElement("span");
    spanWait.textContent = "wait()";
    item.append(spanNome, spanWait);
    elLista.appendChild(item);
  });
}

// ---------- terminal ----------

// adiciona uma linha colorida no terminal de auditoria
function logTerminal(mensagem, tipo = "SISTEMA", timestamp) {
  const linha = document.createElement("div");
  linha.className = "term-line";

  // escolhe a classe css pela categoria da mensagem
  if (tipo.includes("PRODUCAO")) {
    linha.classList.add("term-prod");
  } else if (tipo.includes("CONSUMO")) {
    linha.classList.add("term-cons");
  } else if (tipo.includes("BLOQUEIO")) {
    linha.classList.add("term-warn");
  } else if (tipo.includes("ANOMALIA") || tipo.includes("COLISAO")) {
    linha.classList.add("term-error");
  } else if (tipo.includes("NOTIFICACAO") || tipo.includes("PASSO") || tipo.includes("ATALHO")) {
    linha.classList.add("term-notif");
  } else {
    linha.classList.add("term-system");
  }

  const hora = timestamp || new Date().toTimeString().split(" ")[0];
  linha.textContent = `[${hora}] ${mensagem}`;
  elTerminal.appendChild(linha);

  // tira as linhas mais antigas se passar do limite
  while (elTerminal.childElementCount > MAX_LINHAS_TERMINAL) {
    elTerminal.removeChild(elTerminal.firstChild);
  }

  // rolagem automatica pro final
  elTerminal.scrollTop = elTerminal.scrollHeight;
}

// ---------- estados dos botoes ----------

// troca a cara do botao principal e habilita/desabilita o resto conforme o estado
function aplicarEstado(novoEstado) {
  estado = novoEstado;
  btnPrincipal.disabled = false;

  switch (novoEstado) {
    case "ocioso":
      definirBotaoPrincipal("Iniciar", ICONES.play, "btn-iniciar");
      btnPasso.disabled = false; // passo do zero: comeca ja pausado
      break;
    case "executando":
      definirBotaoPrincipal("Pausar", ICONES.pause, "btn-pausar");
      btnPasso.disabled = true; // passo so com a simulacao pausada
      break;
    case "pausado":
      definirBotaoPrincipal("Retomar", ICONES.play, "btn-iniciar");
      btnPasso.disabled = false;
      break;
    case "concluido":
      definirBotaoPrincipal("Concluído", ICONES.check, "btn-concluido");
      btnPrincipal.disabled = true; // so o Resetar volta pro Iniciar
      btnPasso.disabled = true;
      break;
  }

  // interruptor de modo sempre liberado para permitir demonstracao em tempo real
  btnModoMonitor.disabled = false;
  btnModoCaos.disabled = false;
  elModeToggle.title = "Alterne entre Modo Monitor e Modo Caos a qualquer momento";
}

function definirBotaoPrincipal(texto, icone, classe) {
  elTextoPrincipal.textContent = texto;
  elIconePrincipal.innerHTML = icone;
  btnPrincipal.className = "btn " + classe;
}

// marca visualmente qual lado do interruptor ta ativo
function atualizarToggleModo(caos) {
  const mudou = modoCaos !== caos;
  modoCaos = caos;
  btnModoMonitor.classList.toggle("ativo", !caos);
  btnModoCaos.classList.toggle("ativo", caos);
  btnModoMonitor.setAttribute("aria-pressed", String(!caos));
  btnModoCaos.setAttribute("aria-pressed", String(caos));
  elBufferSvg.classList.toggle("modo-caos", caos);
  if (mudou && caos) {
    produtoresBloqueados.clear();
    consumidoresBloqueados.clear();
    renderizarWaitSets();
  }
}

// zera o que ta na tela (usado no reset)
function limparVisual() {
  produtoresBloqueados.clear();
  consumidoresBloqueados.clear();
  itensSlots = new Array(capacidade).fill(null);
  ocupacao = 0;
  indiceIn = 0;
  indiceOut = 0;
  renderizarBuffer();
  renderizarWaitSets();
}

// ---------- velocidades ----------

function formatarSegundos(ms) {
  return (ms / 1000).toFixed(1) + "s";
}

function atualizarRotulosVelocidade() {
  valProd.textContent = formatarSegundos(Number(sliderProd.value));
  valCons.textContent = formatarSegundos(Number(sliderCons.value));
  valCritica.textContent = sliderCritica.value + "ms";
}

function enviarVelocidade() {
  return enviarComando("speed",
    `producerDelay=${sliderProd.value}&consumerDelay=${sliderCons.value}&criticalDelay=${sliderCritica.value}`);
}

// aplica um atalho pedagogico mexendo nos sliders e mandando pro servidor
function aplicarAtalho(valores, nome, explicacao) {
  sliderProd.value = valores.produtor;
  sliderCons.value = valores.consumidor;
  atualizarRotulosVelocidade();
  enviarVelocidade();
  logTerminal(`🎓 Atalho "${nome}": ${explicacao}`, "ATALHO");
}

// ---------- comunicacao com o servidor ----------

// requisicoes HTTP pros comandos de controle
async function enviarComando(caminho, params = "") {
  try {
    const url = `/api/control/${caminho}${params ? "?" + params : ""}`;
    const res = await fetch(url, { method: "POST" });
    const corpo = await res.json();
    if (!res.ok && corpo.erro) {
      logTerminal(`Comando "${caminho}" recusado: ${corpo.erro}`, "ANOMALIA");
    }
    return corpo;
  } catch (err) {
    logTerminal(`Erro ao enviar comando ${caminho}: ${err.message}`, "ANOMALIA");
    return null;
  }
}

// quando a pagina abre (ou recarrega), pergunta pro servidor como as coisas estao
async function sincronizarEstado() {
  try {
    const res = await fetch("/api/status");
    const s = await res.json();

    atualizarToggleModo(s.modoCaos);
    if (s.emExecucao) {
      aplicarEstado(s.pausado ? "pausado" : "executando");
    } else {
      aplicarEstado("ocioso");
    }

    capacidade = s.capacidade;
    ocupacao = s.ocupacao;
    indiceIn = s.inIndex;
    indiceOut = s.outIndex;
    if (itensSlots.length !== capacidade) {
      itensSlots = new Array(capacidade).fill(null);
    }

    sliderProd.value = s.atrasoProdutor;
    sliderCons.value = s.atrasoConsumidor;
    sliderCritica.value = s.atrasoSecaoCritica;
    atualizarRotulosVelocidade();
    renderizarBuffer();
  } catch (err) {
    logTerminal("Não consegui ler o status do servidor: " + err.message, "ANOMALIA");
  }
}

// conecta ao canal SSE do servidor Java
function conectarSSE() {
  const fonteEventos = new EventSource("/api/stream");

  fonteEventos.onopen = () => {
    elStatusDot.className = "status-dot connected";
    elSseText.textContent = "SSE Ativo";
  };

  fonteEventos.onerror = () => {
    elStatusDot.className = "status-dot error";
    elSseText.textContent = "Desconectado";
  };

  fonteEventos.onmessage = (e) => {
    try {
      const evento = JSON.parse(e.data);

      if (evento.type === "CONEXAO_ESTABELECIDA") {
        logTerminal(evento.message, "SISTEMA");
        return;
      }

      // atualiza dados do buffer
      if (evento.capacity) capacidade = evento.capacity;
      if (typeof evento.count === "number") ocupacao = evento.count;
      if (typeof evento.inIndex === "number") indiceIn = evento.inIndex;
      if (typeof evento.outIndex === "number") indiceOut = evento.outIndex;
      if (Array.isArray(evento.slots)) itensSlots = evento.slots;

      // rastreia estado das threads nas baias de espera
      if (evento.type === "BLOQUEIO_CHEIO") {
        produtoresBloqueados.add(evento.threadName);
      } else if (evento.type === "BLOQUEIO_VAZIO") {
        consumidoresBloqueados.add(evento.threadName);
      } else if (evento.type === "PRODUCAO_SUCESSO") {
        produtoresBloqueados.delete(evento.threadName);
      } else if (evento.type === "CONSUMO_SUCESSO") {
        consumidoresBloqueados.delete(evento.threadName);
      } else if (evento.type === "SISTEMA_FIM") {
        // todas as threads terminaram: so o Resetar volta pro Iniciar
        produtoresBloqueados.clear();
        consumidoresBloqueados.clear();
        if (estado !== "ocioso") {
          aplicarEstado("concluido");
        }
      }

      // o servidor e quem manda no modo (monitor ou caos)
      if (typeof evento.isChaos === "boolean") {
        atualizarToggleModo(evento.isChaos);
      }

      renderizarBuffer();
      renderizarWaitSets();

      // pisca o slot que acabou de ser mexido
      if (typeof evento.slotIndex === "number" && evento.slotIndex >= 0) {
        if (evento.type === "PRODUCAO_SUCESSO") {
          piscarSetor(evento.slotIndex, "flash-prod");
        } else if (evento.type === "CONSUMO_SUCESSO") {
          piscarSetor(evento.slotIndex, "flash-cons");
        } else if (evento.type.startsWith("ANOMALIA")) {
          piscarSetor(evento.slotIndex, "flash-erro");
        }
      }

      if (evento.message) {
        logTerminal(evento.message, evento.type, evento.timestamp);
      }
    } catch (err) {
      console.error("Erro ao processar evento SSE:", err);
    }
  };
}

// ---------- eventos da tela ----------

function configurarEventos() {
  // botao unico: Iniciar -> Pausar -> Retomar -> Pausar ...
  btnPrincipal.addEventListener("click", () => {
    if (estado === "ocioso" || estado === "pausado") {
      aplicarEstado("executando");
      enviarComando("start");
    } else if (estado === "executando") {
      aplicarEstado("pausado");
      enviarComando("pause");
    }
  });

  // passo a passo: se tiver parado, o servidor ja comeca a rodada pausada
  btnPasso.addEventListener("click", () => {
    if (estado === "ocioso" || estado === "pausado") {
      aplicarEstado("pausado");
      enviarComando("step");
    }
  });

  // unico jeito de voltar o botao principal pra "Iniciar"
  btnReset.addEventListener("click", () => {
    limparVisual();
    aplicarEstado("ocioso");
    enviarComando("reset");
    logTerminal("🔄 Simulação resetada.", "SISTEMA");
  });

  // interruptor de modo
  btnModoMonitor.addEventListener("click", () => trocarModo(false));
  btnModoCaos.addEventListener("click", () => trocarModo(true));

  // atalhos pedagogicos
  btnForcarCheio.addEventListener("click", () =>
    aplicarAtalho(ATALHO_CHEIO, "Forçar Cheio", "produtores rápidos e consumidores lentos. O buffer vai lotar e os produtores entram em wait()."));
  btnForcarVazio.addEventListener("click", () =>
    aplicarAtalho(ATALHO_VAZIO, "Forçar Vazio", "produtores lentos e consumidores rápidos. O buffer vai esvaziar e os consumidores entram em wait()."));

  // sliders: atualiza o rotulo enquanto arrasta e manda pro servidor quando solta
  [sliderProd, sliderCons, sliderCritica].forEach(slider => {
    slider.addEventListener("input", atualizarRotulosVelocidade);
    slider.addEventListener("change", enviarVelocidade);
  });

  // limpar logs
  btnClearLogs.addEventListener("click", () => {
    elTerminal.innerHTML = "";
  });
}

// pede pro servidor trocar o buffer (monitor <-> caos)
async function trocarModo(caos) {
  if (caos === modoCaos) return;
  const resposta = await enviarComando("chaos", "active=" + caos);
  if (resposta && !resposta.erro) {
    atualizarToggleModo(caos);
    // limpa o visual se estiver parado; se estiver rodando, mantem o fluxo continuo
    if (estado === "ocioso") {
      limparVisual();
    }
  }
}

// ---------- inicializacao ----------
construirBuffer();
aplicarEstado("ocioso");
atualizarRotulosVelocidade();
renderizarBuffer();
renderizarWaitSets();
configurarEventos();
sincronizarEstado();
conectarSSE();
