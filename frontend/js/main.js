// main.js - Pantallas, HUD, controles y bucle del juego (tiempo real con pausa).
//
// Flujo: carga de assets -> menú -> partida. Cada "tick" se pide al backend (POST /api/tick);
// la respuesta trae el estado completo y los eventos, que dibuja tablero.js.

(function () {
  const TD = (window.TD = window.TD || {});
  const A = TD.assets;
  const $ = (id) => document.getElementById(id);
  const $$ = (sel, raiz) => Array.from((raiz || document).querySelectorAll(sel));

  const VELOCIDADES = [{ ms: 1400, texto: '⏩ x1' }, { ms: 750, texto: '⏩ x2' }, { ms: 450, texto: '⏩ x3' }];
  const NOMBRES_Z = { BASICO: 'Básico', RAPIDO: 'Rápido', TANQUE: 'Tanque' };
  const NOMBRES_P = { MURO: 'Muro', TIRADOR: 'Tirador', MINA: 'Mina' };
  const ICONO_P = { MURO: 'images/characters/plants/muro.png', TIRADOR: 'images/characters/plants/tirador.png', MINA: 'images/characters/plants/mina.png' };
  const ICONO_Z = { BASICO: 'images/characters/zombies/basico.png', RAPIDO: 'images/characters/zombies/rapido.png', TANQUE: 'images/characters/zombies/tanque.png' };
  const REPOSO_P = { MURO: 'images/characters/plants/muro_reposo.gif', TIRADOR: 'images/characters/plants/tirador_reposo.gif', MINA: 'images/characters/plants/mina_reposo.gif' };
  const CAMINA_Z = { BASICO: 'images/characters/zombies/basico_caminando.gif', RAPIDO: 'images/characters/zombies/rapido_corriendo.gif', TANQUE: 'images/characters/zombies/tanque_caminando.gif' };
  const DESCRIPCION = {
    MURO: 'Bloquea el paso: los zombis deben destruirlo para avanzar.',
    TIRADOR: 'Dispara a distancia al zombi más cercano que viene por el camino.',
    MINA: 'Explota al contacto con un zombi (999 de daño) y se consume.',
    BASICO: 'Equilibrado en vida, daño y velocidad.',
    RAPIDO: 'Poca vida pero golpea fuerte.',
    TANQUE: 'Mucha vida; avanza lento (2 ticks por nodo) y golpea poco.',
  };
  // Estrategia de la demo ganadora: [tipo, id de nodo] (la misma de la versión de consola)
  const ESTRATEGIA_DEMO = [['MURO', 4], ['TIRADOR', 6], ['TIRADOR', 7], ['MINA', 2], ['TIRADOR', 8], ['MURO', 3], ['TIRADOR', 9]];

  const S = {
    catalogo: null,
    estado: null,
    seleccion: null,       // tipo de defensa elegida en las cartas
    corriendo: false,
    velocidad: 0,
    timer: null,
    demo: false,
    demoGanar: true,
    terminada: false,
    ocupado: false,
    ultimoDinero: null,
  };

  // ------------------------------------------------------------------ pantallas
  function mostrar(nombre) {
    $$('.pantalla').forEach((p) => p.classList.toggle('activa', p.id === 'pantalla-' + nombre));
    const menu = nombre === 'menu';
    $$('#pantalla-menu video').forEach((v) => { if (menu) v.play().catch(() => {}); else v.pause(); });
  }

  function toast(texto) {
    const t = $('toast');
    t.textContent = texto;
    t.classList.add('visible');
    clearTimeout(toast.h);
    toast.h = setTimeout(() => t.classList.remove('visible'), 2200);
  }

  function imgAnimada(clave, clase) {
    const img = document.createElement('img');
    img.src = A.url(clave);
    if (clase) img.className = clase;
    img.alt = '';
    img.draggable = false;
    return img;
  }

  // ------------------------------------------------------------------ carga
  async function arrancar() {
    const barra = $('carga-barra'), texto = $('carga-texto');
    try {
      const [cat] = await Promise.all([
        TD.api.catalogo(),
        A.cargar((hecho, total) => {
          barra.style.width = Math.round((hecho / total) * 100) + '%';
          texto.textContent = 'Cargando imágenes… ' + Math.round((hecho / total) * 100) + '%';
        }),
      ]);
      S.catalogo = cat;
    } catch (e) {
      texto.textContent = 'No pude conectar con el servidor del juego. ¿Está ejecutándose ServidorApi? (' + e.message + ')';
      texto.classList.add('error');
      return;
    }
    barra.style.width = '100%';
    texto.textContent = '¡Todo listo!';
    montarLibro();
    montarCartas();
    actualizarBotonesAudio();

    const auto = new URLSearchParams(location.search).get('auto');
    if (auto) { lanzarAutomatico(auto); return; }   // atajos de desarrollo/pruebas (?auto=menu|juego|demo|libro|fin)
    const b = $('btn-comenzar');
    b.hidden = false;
    b.addEventListener('click', () => {
      TD.audio.despertar();
      TD.audio.efecto('click_menu');
      irAlMenu();
    });
  }

  function irAlMenu() {
    detener();
    mostrar('menu');
    TD.audio.musica('menu');
  }

  // ------------------------------------------------------------------ libro
  function montarLibro() {
    const tarj = (tipo, d, esPlanta) => {
      const div = document.createElement('div');
      div.className = 'tarjeta-libro ' + (esPlanta ? 'planta' : 'zombi');
      const marco = document.createElement('div');
      marco.className = 'tarjeta-gif';
      marco.appendChild(imgAnimada(esPlanta ? REPOSO_P[tipo] : CAMINA_Z[tipo]));
      const stats = esPlanta
        ? '<li>❤ Vida <b>' + d.hp + '</b></li><li>⚔ Daño <b>' + d.dano + '</b></li><li>💰 Costo <b>' + d.costo + '</b></li>'
        : '<li>❤ Vida <b>' + d.hp + '</b></li><li>⚔ Daño <b>' + d.dano + '</b></li><li>👣 Velocidad <b>' + d.velocidad + ' tick/nodo</b></li><li>💰 Recompensa <b>' + d.recompensa + '</b></li>';
      const info = document.createElement('div');
      info.innerHTML = '<h4>' + (esPlanta ? d.nombre : NOMBRES_Z[tipo]) + '</h4><p>' + DESCRIPCION[tipo] + '</p><ul>' + stats + '</ul>';
      div.append(marco, info);
      return div;
    };
    const cp = $('libro-plantas'), cz = $('libro-zombis');
    cp.innerHTML = ''; cz.innerHTML = '';
    S.catalogo.plantas.forEach((p) => cp.appendChild(tarj(p.tipo, p, true)));
    S.catalogo.zombies.forEach((z) => cz.appendChild(tarj(z.tipo, z, false)));
    $('libro-reglas').innerHTML =
      'Empiezas con <b>' + S.catalogo.dineroInicial + '</b> monedas y <b>' + S.catalogo.vidasIniciales + '</b> vidas. ' +
      'Hay <b>' + S.catalogo.oleadas + '</b> oleadas. Cada zombi derrotado te da monedas; si uno llega a la meta pierdes una vida. ' +
      'Retirar una defensa devuelve la mitad de su costo (sale de la <b>Pila</b>: siempre la última que pusiste).';
  }

  // ------------------------------------------------------------------ cartas de defensa
  function montarCartas() {
    const cont = $('cartas');
    cont.innerHTML = '';
    S.catalogo.plantas.forEach((p, i) => {
      const c = document.createElement('button');
      c.className = 'carta';
      c.dataset.tipo = p.tipo;
      const foto = document.createElement('div');
      foto.className = 'carta-foto';
      foto.appendChild(imgAnimada(REPOSO_P[p.tipo]));
      const txt = document.createElement('div');
      txt.className = 'carta-txt';
      txt.innerHTML = '<b>' + p.nombre + '</b><span class="costo">💰 ' + p.costo + '</span><small>❤ ' + p.hp + (p.dano ? ' · ⚔ ' + p.dano : '') + '</small><kbd>' + (i + 1) + '</kbd>';
      c.append(foto, txt);
      c.addEventListener('click', () => elegir(p.tipo));
      cont.appendChild(c);
    });
  }

  function elegir(tipo) {
    if (S.terminada || S.demo) return;
    TD.audio.despertar();
    const nueva = S.seleccion === tipo ? null : tipo;
    if (nueva) {
      const cat = S.catalogo.plantas.find((p) => p.tipo === nueva);
      if (S.estado && S.estado.dinero < cat.costo) { TD.audio.efecto('error'); toast('Dinero insuficiente para ' + cat.nombre); return; }
    }
    S.seleccion = nueva;
    TD.audio.efecto('click_menu');
    refrescarCartas();
    $('tablero').classList.toggle('modo-colocar', !!nueva);
    if (!nueva) TD.tablero.resaltar(null);
  }

  function refrescarCartas() {
    $$('.carta').forEach((c) => {
      const cat = S.catalogo.plantas.find((p) => p.tipo === c.dataset.tipo);
      c.classList.toggle('elegida', S.seleccion === c.dataset.tipo);
      c.classList.toggle('sin-dinero', !!S.estado && S.estado.dinero < cat.costo);
      c.disabled = S.demo;
    });
    $('btn-retirar').disabled = S.demo || !S.estado || S.estado.pila.length === 0;
  }

  // ------------------------------------------------------------------ HUD y paneles
  function montarVidas(n) {
    const cont = $('hud-vidas');
    cont.innerHTML = '';
    for (let i = 0; i < n; i++) {  // cada corazón arranca desfasado para que no giren todos a la vez
      const im = document.createElement('img');
      im.className = 'corazon';
      im.alt = '';
      cont.appendChild(im);
      setTimeout(() => { im.src = A.url('images/ui/icono_vida.gif'); }, i * 500);
    }
  }

  function actualizarHud(e) {
    const dinero = $('hud-dinero');
    if (S.ultimoDinero !== null && e.dinero !== S.ultimoDinero) {
      dinero.classList.remove(e.dinero > S.ultimoDinero ? 'baja' : 'sube', e.dinero > S.ultimoDinero ? 'sube' : 'baja');
      void dinero.offsetWidth;
      dinero.classList.add(e.dinero > S.ultimoDinero ? 'sube' : 'baja');
    }
    S.ultimoDinero = e.dinero;
    dinero.textContent = e.dinero;
    $('hud-oleada').textContent = e.oleada;
    $('hud-total').textContent = e.totalOleadas;
    $('hud-enmapa').textContent = e.zombisEnMapa;
    $('hud-pendientes').textContent = e.zombisPendientes;
    $$('#hud-vidas .corazon').forEach((c, i) => c.classList.toggle('perdido', i >= e.vidas));
    refrescarCartas();

    // Cola (FIFO): del frente al final
    const cola = $('cola-lista');
    cola.innerHTML = '';
    if (!e.cola.length) cola.innerHTML = '<span class="vacio">— vacía —</span>';
    e.cola.slice(0, 9).forEach((z, i) => {
      const chip = document.createElement('div');
      chip.className = 'chip' + (i === 0 ? ' frente' : '');
      chip.title = NOMBRES_Z[z.tipo] + ' (' + z.hp + ' HP)';
      chip.appendChild(imgAnimada(ICONO_Z[z.tipo]));
      if (i === 0) chip.insertAdjacentHTML('beforeend', '<em>frente</em>');
      cola.appendChild(chip);
    });
    if (e.cola.length > 9) cola.insertAdjacentHTML('beforeend', '<span class="mas">+' + (e.cola.length - 9) + '</span>');

    // Pila (LIFO): del tope a la base
    const pila = $('pila-lista');
    pila.innerHTML = '';
    if (!e.pila.length) pila.innerHTML = '<span class="vacio">— vacía —</span>';
    e.pila.slice(0, 7).forEach((p, i) => {
      const fila = document.createElement('div');
      fila.className = 'pila-item' + (i === 0 ? ' tope' : '');
      fila.appendChild(imgAnimada(ICONO_P[p.tipo]));
      fila.insertAdjacentHTML('beforeend', '<span>' + NOMBRES_P[p.tipo] + ' · nodo ' + p.nodo + '</span>' + (i === 0 ? '<em>tope</em>' : ''));
      pila.appendChild(fila);
    });
    if (e.pila.length > 7) pila.insertAdjacentHTML('beforeend', '<span class="mas">+' + (e.pila.length - 7) + ' más</span>');
  }

  function registrar(texto, clase) {
    const ul = $('registro');
    const li = document.createElement('li');
    li.textContent = texto;
    if (clase) li.className = clase;
    ul.prepend(li);
    while (ul.children.length > 8) ul.lastChild.remove();
  }

  function banner(texto, clase) {
    const b = $('banner');
    b.textContent = texto;
    b.className = 'banner ' + (clase || '');
    void b.offsetWidth;
    b.classList.add('muestra');
  }

  function destello() {
    const f = $('flash');
    f.classList.remove('activo');
    void f.offsetWidth;
    f.classList.add('activo');
  }

  // Eventos que no son animaciones del tablero: sonidos de acciones, oleadas, registro, fin
  function procesarEventos(eventos, estado) {
    const esFinal = eventos.some((e) => e.tipo === 'oleada_final');
    eventos.forEach((ev) => {
      switch (ev.tipo) {
        case 'nueva_oleada':
          banner('Oleada ' + ev.oleada + ' / ' + estado.totalOleadas);
          registrar('▶ Oleada ' + ev.oleada + ': ' + ev.zombies + ' zombis', 'oleada');
          if (ev.oleada > 1 && !esFinal) TD.audio.efecto('nueva_oleada');
          break;
        case 'oleada_final':
          banner('¡OLEADA FINAL!', 'final');
          TD.audio.efecto('oleada_final');
          break;
        case 'zombie_entra': registrar('Entra un zombi ' + NOMBRES_Z[ev.zombieTipo] + ' (nodo 0)'); break;
        case 'planta_colocada':
          TD.audio.efecto('colocar_defensa');
          registrar('✓ ' + NOMBRES_P[ev.plantaTipo] + ' en el nodo ' + ev.nodo + ' (−' + ev.costo + ')', 'bien');
          break;
        case 'planta_retirada':
          TD.audio.efecto('retirar_defensa');
          registrar('↩ ' + NOMBRES_P[ev.plantaTipo] + ' retirado del nodo ' + ev.nodo + ' (+' + ev.reembolso + ')');
          break;
        case 'disparo': registrar('Tirador (nodo ' + ev.desde + ') → −' + ev.dano + ' al nodo ' + ev.hacia); break;
        case 'mina_explota': registrar('💥 Mina del nodo ' + ev.nodo + ' explota (−' + ev.dano + ')', 'mal'); break;
        case 'zombie_ataca': registrar('Zombi ' + NOMBRES_Z[ev.zombieTipo] + ' muerde en el nodo ' + ev.objetivo + ' (−' + ev.dano + ')', 'mal'); break;
        case 'zombie_muere': registrar('☠ Zombi ' + NOMBRES_Z[ev.zombieTipo] + ' derrotado (+' + ev.recompensa + ')', 'bien'); break;
        case 'planta_destruida': if (ev.plantaTipo !== 'MINA') registrar('✖ ' + NOMBRES_P[ev.plantaTipo] + ' destruido en el nodo ' + ev.nodo, 'mal'); break;
        case 'zombie_meta':
          registrar('⚠ Un zombi ' + NOMBRES_Z[ev.zombieTipo] + ' llegó a la meta. Vidas: ' + ev.vidas, 'mal');
          destello();
          TD.tablero.golpeMeta();
          break;
        default: break;
      }
    });
    const fin = eventos.find((e) => e.tipo === 'victoria' || e.tipo === 'game_over');
    if (fin) terminarPartida(fin.tipo === 'victoria', estado);
  }

  // Aplica una respuesta del servidor: eventos, animaciones y HUD
  function aplicar(resp) {
    S.estado = resp.estado;
    procesarEventos(resp.eventos || [], resp.estado);
    TD.tablero.aplicar(resp);
    actualizarHud(resp.estado);
  }

  // ------------------------------------------------------------------ partida
  function elementosTablero() { return { tablero: $('tablero') }; }
  function tamDisponible() {
    const c = $('centro');
    return { ancho: c.clientWidth - 36, alto: c.clientHeight - 36 };
  }

  async function iniciarPartida(demo, ganar) {
    detener();
    S.demo = !!demo;
    S.demoGanar = ganar !== false;
    S.terminada = false;
    S.seleccion = null;
    S.ultimoDinero = null;
    $('insignia-demo').hidden = !demo;
    $('registro').innerHTML = '';
    $('tablero').classList.remove('modo-colocar');
    $('hud-icono-dinero').src = A.url('images/ui/icono_dinero.gif');
    TD.tablero.setTickMs(VELOCIDADES[S.velocidad].ms);

    mostrar('juego');
    const resp = await TD.api.nuevo(demo);
    S.estado = resp.estado;
    montarVidas(resp.estado.vidasIniciales);
    TD.tablero.construir(resp.estado, elementosTablero(), tamDisponible());
    TD.tablero.inicial(resp.estado);
    procesarEventos(resp.eventos || [], resp.estado);
    actualizarHud(resp.estado);
    actualizarBotonPausa();
    TD.audio.musica('partida');

    if (demo) iniciarBucle();
    else registrar('Coloca tus defensas y pulsa ▶ Iniciar (o Espacio).', 'oleada');
  }

  function detener() {
    S.corriendo = false;
    clearTimeout(S.timer);
    TD.tablero.detener();
  }

  function actualizarBotonPausa() {
    const b = $('btn-pausa');
    b.textContent = S.terminada ? '■ Fin' : S.corriendo ? '⏸ Pausa' : (S.estado && S.estado.tick > 0 ? '▶ Seguir' : '▶ Iniciar');
    b.disabled = S.terminada || S.demo;
    $('btn-vel').textContent = VELOCIDADES[S.velocidad].texto;
  }

  function iniciarBucle() {
    if (S.corriendo || S.terminada) return;
    S.corriendo = true;
    actualizarBotonPausa();
    programar(0);
  }

  function pausar() {
    S.corriendo = false;
    clearTimeout(S.timer);
    actualizarBotonPausa();
  }

  function programar(ms) {
    clearTimeout(S.timer);
    S.timer = setTimeout(paso, ms);
  }

  async function paso() {
    if (!S.corriendo || S.terminada) return;
    const t0 = performance.now();
    try {
      if (S.demo && S.demoGanar) await turnoDemo();
      const resp = await TD.api.tick();
      aplicar(resp);
    } catch (e) {
      console.error(e);
      pausar();
      toast('Se perdió la conexión con el servidor');
      return;
    }
    if (S.corriendo && !S.terminada) programar(Math.max(0, VELOCIDADES[S.velocidad].ms - (performance.now() - t0)));
  }

  // Demo automática: coloca en orden las defensas de la estrategia que falten y alcance el dinero
  async function turnoDemo() {
    for (const [tipo, id] of ESTRATEGIA_DEMO) {
      const cat = S.catalogo.plantas.find((p) => p.tipo === tipo);
      const nodo = S.estado.nodos[id];
      if (S.estado.dinero >= cat.costo && !nodo.planta && !nodo.zombie) {
        const r = await TD.api.defensa(tipo, nodo.fila, nodo.columna);
        aplicar(r);
      }
    }
  }

  function terminarPartida(victoria, estado) {
    S.terminada = true;
    pausar();
    actualizarBotonPausa();
    setTimeout(() => {
      TD.audio.musica(null);
      $('fin-titulo').textContent = victoria ? '¡VICTORIA!' : 'GAME OVER';
      $('fin-titulo').className = victoria ? 'gana' : 'pierde';
      $('fin-detalle').innerHTML = victoria
        ? 'Derrotaste las ' + estado.totalOleadas + ' oleadas.<br>Dinero final: <b>' + estado.dinero + '</b> · Vidas restantes: <b>' + estado.vidas + '</b>'
        : 'Los zombis llegaron a tu casa.<br>Llegaste a la oleada <b>' + estado.oleada + ' / ' + estado.totalOleadas + '</b> (tick ' + estado.tick + ').';
      $('fin-fondo').style.backgroundImage = 'url(' + A.url('images/scenarios/' + (victoria ? 'fondo_victoria.jpg' : 'fondo_game_over.jpg')) + ')';
      mostrar('fin');
      if (victoria) { TD.audio.efecto('victoria'); TD.audio.musica('victoria'); } else { TD.audio.efecto('game_over'); }
    }, 1800);
  }

  // ------------------------------------------------------------------ interacción con el tablero
  async function colocar(fila, columna) {
    if (S.ocupado || !S.seleccion || S.terminada || S.demo) return;
    S.ocupado = true;
    try {
      const r = await TD.api.defensa(S.seleccion, fila, columna);
      if (!r.ok) {
        TD.audio.efecto('error');
        toast(r.error);
      }
      aplicar(r);
      if (r.ok && S.estado.dinero < S.catalogo.plantas.find((p) => p.tipo === S.seleccion).costo) {
        S.seleccion = null;
        $('tablero').classList.remove('modo-colocar');
        TD.tablero.resaltar(null);
        refrescarCartas();
      }
    } catch (e) {
      toast('Error de conexión con el servidor');
    }
    S.ocupado = false;
  }

  async function retirar() {
    if (S.ocupado || S.terminada || S.demo) return;
    S.ocupado = true;
    try {
      const r = await TD.api.retirar();
      if (!r.ok) { TD.audio.efecto('error'); toast(r.error); }
      aplicar(r);
    } catch (e) {
      toast('Error de conexión con el servidor');
    }
    S.ocupado = false;
  }

  function conectarTablero() {
    const tab = $('tablero');
    tab.addEventListener('mousemove', (ev) => {
      if (!S.seleccion) return;
      const c = TD.tablero.celdaEn(ev.clientX, ev.clientY);
      if (c) TD.tablero.resaltar(c.fila, c.columna, S.seleccion); else TD.tablero.resaltar(null);
    });
    tab.addEventListener('mouseleave', () => TD.tablero.resaltar(null));
    tab.addEventListener('click', (ev) => {
      TD.audio.despertar();
      const c = TD.tablero.celdaEn(ev.clientX, ev.clientY);
      if (c && S.seleccion) colocar(c.fila, c.columna);
    });
    tab.addEventListener('contextmenu', (ev) => {  // clic derecho: cancelar la selección
      ev.preventDefault();
      if (S.seleccion) elegir(S.seleccion);
    });
  }

  // ------------------------------------------------------------------ botones y teclado
  function actualizarBotonesAudio() {
    const a = TD.audio.ajustes;
    $$('[data-accion="musica"] span').forEach((s) => { s.textContent = 'Música: ' + (a.musica ? 'sí' : 'no'); });
    $$('[data-accion="efectos"] span').forEach((s) => { s.textContent = 'Efectos: ' + (a.efectos ? 'sí' : 'no'); });
    $('btn-musica').classList.toggle('apagado', !a.musica);
    $('btn-efectos').classList.toggle('apagado', !a.efectos);
  }

  function accion(nombre) {
    TD.audio.despertar();
    if (nombre !== 'musica' && nombre !== 'efectos') TD.audio.efecto('click_menu');
    switch (nombre) {
      case 'jugar': iniciarPartida(false); break;
      case 'jugar-de-nuevo': iniciarPartida(S.demo, S.demoGanar); break;
      case 'libro': mostrar('libro'); break;
      case 'creditos': mostrar('creditos'); break;
      case 'demo': $('dialogo-demo').hidden = false; break;
      case 'cerrar-dialogo': $('dialogo-demo').hidden = true; break;
      case 'demo-ganar': $('dialogo-demo').hidden = true; iniciarPartida(true, true); break;
      case 'demo-perder': $('dialogo-demo').hidden = true; iniciarPartida(true, false); break;
      case 'volver-menu': irAlMenu(); break;
      case 'musica': TD.audio.alternarMusica(); actualizarBotonesAudio(); break;
      case 'efectos': TD.audio.alternarEfectos(); actualizarBotonesAudio(); break;
      default: break;
    }
  }

  function conectarControles() {
    document.addEventListener('click', (ev) => {
      const b = ev.target.closest('[data-accion]');
      if (b) accion(b.dataset.accion);
    });
    $('btn-pausa').addEventListener('click', () => { TD.audio.despertar(); if (S.corriendo) pausar(); else iniciarBucle(); });
    $('btn-vel').addEventListener('click', () => {
      S.velocidad = (S.velocidad + 1) % VELOCIDADES.length;
      TD.tablero.setTickMs(VELOCIDADES[S.velocidad].ms);
      actualizarBotonPausa();
    });
    $('btn-retirar').addEventListener('click', retirar);
    $('btn-musica').addEventListener('click', () => { TD.audio.alternarMusica(); actualizarBotonesAudio(); });
    $('btn-efectos').addEventListener('click', () => { TD.audio.alternarEfectos(); actualizarBotonesAudio(); });
    $('btn-salir').addEventListener('click', () => { TD.audio.despertar(); irAlMenu(); });

    document.addEventListener('keydown', (ev) => {
      if (!$('pantalla-juego').classList.contains('activa')) return;
      if (ev.key === '1') elegir('MURO');
      else if (ev.key === '2') elegir('TIRADOR');
      else if (ev.key === '3') elegir('MINA');
      else if (ev.key === 'Escape' && S.seleccion) elegir(S.seleccion);
      else if (ev.key === 'r' || ev.key === 'R') retirar();
      else if (ev.key === ' ') { ev.preventDefault(); if (!S.demo && !S.terminada) { if (S.corriendo) pausar(); else iniciarBucle(); } }
    });
    // Primer gesto del usuario: los navegadores permiten audio desde aquí
    ['pointerdown', 'keydown'].forEach((t) => document.addEventListener(t, () => TD.audio.despertar(), { passive: true }));

    let h = null;
    window.addEventListener('resize', () => {
      clearTimeout(h);
      h = setTimeout(() => {
        if ($('pantalla-juego').classList.contains('activa') && S.estado) TD.tablero.reconstruir(elementosTablero(), tamDisponible());
      }, 200);
    });
  }

  // Atajos de desarrollo: ?auto=menu | libro | creditos | juego | demo | fin  (&coloca=MURO@4,TIRADOR@6  &inicia=1  &vel=0..2  &mudo=1)
  async function lanzarAutomatico(auto) {
    const q = new URLSearchParams(location.search);
    if (auto === 'menu') { irAlMenu(); return; }
    if (auto === 'libro') { mostrar('libro'); return; }
    if (auto === 'creditos') { mostrar('creditos'); return; }
    if (auto === 'demo') { if (q.get('vel')) S.velocidad = Math.min(VELOCIDADES.length - 1, +q.get('vel')); await iniciarPartida(true, q.get('ganar') !== '0'); return; }
    if (auto === 'fin') { S.estado = { totalOleadas: 8, dinero: 1200, vidas: 2, oleada: 8, tick: 90 }; terminarPartida(q.get('gana') !== '0', S.estado); return; }
    if (q.get('vel')) S.velocidad = Math.min(VELOCIDADES.length - 1, +q.get('vel'));
    await iniciarPartida(false);
    for (const par of (q.get('coloca') || '').split(',').filter(Boolean)) {
      const [tipo, id] = par.split('@');
      const n = S.estado.nodos[+id];
      aplicar(await TD.api.defensa(tipo, n.fila, n.columna));
    }
    if (q.get('inicia')) iniciarBucle();
  }

  // ------------------------------------------------------------------ arranque
  document.addEventListener('DOMContentLoaded', () => {
    conectarControles();
    conectarTablero();
    arrancar();
  });
})();
