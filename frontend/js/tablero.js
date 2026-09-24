// tablero.js - Dibuja el mapa 7x7 y anima plantas, zombis, proyectiles y efectos.
//
// El backend manda el ESTADO (quién está en cada nodo) y los EVENTOS del tick (disparo,
// mordida, muerte...). Aquí el estado decide qué sprites existen y dónde están, y los eventos
// disparan las animaciones de una sola vez con sus sonidos, sincronizados con lo que se ve.

(function () {
  const TD = (window.TD = window.TD || {});
  const A = TD.assets;

  const TAM = 7;
  const P = 'images/characters/plants/';
  const Z = 'images/characters/zombies/';
  const PLANTAS = {
    MURO: { nombre: 'Muro', reposo: P + 'muro_reposo.gif', destruido: P + 'muro_destruido.gif' },
    TIRADOR: { nombre: 'Tirador', reposo: P + 'tirador_reposo.gif', disparo: P + 'tirador_disparo.gif', destruido: P + 'tirador_destruido.gif' },
    MINA: { nombre: 'Mina', reposo: P + 'mina_reposo.gif', explosion: P + 'mina_explosion.gif' },
  };
  const ZOMBIS = {
    BASICO: { nombre: 'Básico', caminando: Z + 'basico_caminando.gif', comiendo: Z + 'basico_comiendo.gif', muerte: Z + 'basico_muerte.gif' },
    RAPIDO: { nombre: 'Rápido', caminando: Z + 'rapido_corriendo.gif', comiendo: Z + 'rapido_comiendo.gif', muerte: Z + 'rapido_muerte.gif' },
    TANQUE: { nombre: 'Tanque', caminando: Z + 'tanque_caminando.gif', comiendo: Z + 'tanque_comiendo.gif', muerte: Z + 'tanque_muerte.gif' },
  };
  const PROYECTIL = 'images/effects/proyectil_tirador.gif';
  const IMPACTO = 'images/effects/proyectil_impacto.gif';

  // Curva base del tile: conecta ABAJO y DERECHA. Rotación (horaria) para cada par de bordes.
  const ROT_CURVA = { ES: 0, SW: 90, NW: 180, EN: 270 };

  let celda = 96;
  let tickMs = 1400;
  let elTablero = null, elCeldas = null, elEnt = null, elFx = null, elResalte = null;
  let nodos = [];
  const nodoPorId = new Map();
  const nodoPorPos = new Map();
  const plantas = new Map();
  const zombis = new Map();
  let urlCesped = '', urlCamino = '', urlCurva = '';
  let fantasma = null;
  let ultimoEstado = null;
  let temporizadores = new Set();

  // ---------------------------------------------------------------- utilidades
  function espera(ms, fn) {
    const t = setTimeout(() => { temporizadores.delete(t); fn(); }, ms);
    temporizadores.add(t);
    return t;
  }

  function pos(nodo) {  // punto donde "pisan" los personajes dentro de la celda
    return { x: (nodo.columna + 0.5) * celda, y: (nodo.fila + 0.8) * celda };
  }

  function escalaDe(clave) {
    const r = A.info(clave).ref;
    return Math.min((celda * 1.25) / r[1], (celda * 1.05) / r[0]);
  }

  function nombreZ(tipo) { return ZOMBIS[tipo] ? ZOMBIS[tipo].nombre : tipo; }

  // Crea un sprite. opciones: clase, centro (ancla en el centro del gif), escala
  function crearEnt(clave, x, y, o) {
    o = o || {};
    const info = A.info(clave);
    const s = o.escala || escalaDe(clave);
    const el = document.createElement('div');
    el.className = 'ent' + (o.clase ? ' ' + o.clase : '');
    const img = document.createElement('img');
    img.className = 'spr';
    img.draggable = false;
    const ent = { el, img, clave, s, info, centro: !!o.centro, x, y, flip: false, rot: 0 };
    aplicarGeometria(ent);
    img.src = A.url(clave);
    el.appendChild(img);
    el.style.transform = 'translate(' + x + 'px,' + y + 'px)';
    el.style.zIndex = Math.round(y);
    elEnt.appendChild(el);
    return ent;
  }

  function aplicarGeometria(ent) {
    const i = ent.info, s = ent.s;
    const ax = ent.centro ? i.ancho / 2 : i.ancla[0];
    const ay = ent.centro ? i.alto / 2 : i.ancla[1];
    ent.img.style.width = i.ancho * s + 'px';
    ent.img.style.left = -ax * s + 'px';
    ent.img.style.top = -ay * s + 'px';
    ent.img.style.transformOrigin = ax * s + 'px ' + ay * s + 'px';
  }

  function actualizarTransformImg(ent) {
    ent.img.style.transform = (ent.flip ? 'scaleX(-1) ' : '') + (ent.rot ? 'rotate(' + ent.rot + 'rad)' : '');
  }

  // Cambia de animación (mismo lienzo por personaje, así no "salta"). forzar = reiniciar la misma
  function cambiarGif(ent, clave, forzar) {
    if (ent.clave === clave && !forzar) return;
    const viejo = ent.img.src;
    ent.clave = clave;
    ent.info = A.info(clave);
    aplicarGeometria(ent);
    ent.img.src = A.url(clave);
    A.soltar(viejo);
  }

  function voltear(ent, flip) {
    if (ent.flip === flip) return;
    ent.flip = flip;
    actualizarTransformImg(ent);
  }

  function quitar(ent) {
    if (!ent || !ent.el.parentNode) return;
    A.soltar(ent.img.src);
    ent.el.remove();
  }

  function moverA(ent, x, y, ms, lineal) {
    ent.el.style.transition = ms ? 'transform ' + ms + 'ms ' + (lineal ? 'linear' : 'ease-in') : 'none';
    ent.el.style.transform = 'translate(' + x + 'px,' + y + 'px)';
    ent.el.style.zIndex = Math.round(y);
    ent.x = x; ent.y = y;
  }

  // Posición visual real (por si el sprite todavía se está deslizando)
  function posVisual(ent) {
    const r = ent.el.getBoundingClientRect();
    const b = elTablero.getBoundingClientRect();
    return { x: r.left - b.left, y: r.top - b.top };
  }

  function agregarBarra(ent, clase) {
    const b = document.createElement('div');
    b.className = 'hp ' + (clase || '');
    b.style.width = Math.round(celda * 0.62) + 'px';
    b.style.left = -Math.round(celda * 0.31) + 'px';
    b.style.top = -Math.round(ent.info.ref[1] * ent.s) - 12 + 'px';
    b.innerHTML = '<i></i>';
    ent.el.appendChild(b);
    ent.barra = b;
  }

  function actualizarHp(ent, hp, hpMax, ocultarLlena) {
    if (!ent.barra) return;
    const f = Math.max(0, Math.min(1, hp / hpMax));
    const barra = ent.barra.firstChild;
    barra.style.width = f * 100 + '%';
    barra.className = f > 0.6 ? 'alto' : f > 0.3 ? 'medio' : 'bajo';
    ent.barra.style.visibility = ocultarLlena && f >= 1 ? 'hidden' : 'visible';
  }

  // Efecto de una sola vez (explosión, muerte, destrucción...) que se limpia solo
  function efecto(clave, x, y, o) {
    o = o || {};
    const e = crearEnt(clave, x, y, { clase: 'efecto', centro: o.centro, escala: o.escala });
    if (o.flip) voltear(e, true);
    const dur = (A.info(clave).duracion_ms || 1000) + 80;
    espera(dur, () => quitar(e));
    return e;
  }

  function flotar(texto, x, y, clase) {
    const t = document.createElement('div');
    t.className = 'flota ' + (clase || '');
    t.textContent = texto;
    t.style.left = x + 'px';
    t.style.top = y + 'px';
    elFx.appendChild(t);
    espera(1100, () => t.remove());
  }

  function golpe(ent) {
    if (!ent) return;
    ent.img.classList.add('golpe');
    espera(170, () => ent.img.classList.remove('golpe'));
  }

  // ---------------------------------------------------------------- construcción
  // El alto reserva ~0.4 celda extra arriba: los personajes de la fila 0 sobresalen del tablero
  function calcularCelda(disponibleAncho, disponibleAlto) {
    return Math.max(48, Math.floor(Math.min(disponibleAncho / TAM, disponibleAlto / (TAM + 0.42))));
  }

  function dir(a, b) {
    if (b.columna > a.columna) return 'E';
    if (b.columna < a.columna) return 'W';
    return b.fila > a.fila ? 'S' : 'N';
  }

  function construir(estado, elementos, tamDisponible) {
    elTablero = elementos.tablero;
    celda = calcularCelda(tamDisponible.ancho, tamDisponible.alto);
    limpiar();

    elTablero.innerHTML = '';
    elTablero.style.width = celda * TAM + 'px';
    elTablero.style.height = celda * TAM + 'px';
    elTablero.style.setProperty('--celda', celda + 'px');
    document.documentElement.style.setProperty('--celda', celda + 'px');

    elCeldas = document.createElement('div'); elCeldas.className = 'capa capa-celdas';
    elEnt = document.createElement('div'); elEnt.className = 'capa capa-ent';
    elFx = document.createElement('div'); elFx.className = 'capa capa-fx';
    elResalte = document.createElement('div'); elResalte.className = 'resalte';
    elTablero.append(elCeldas, elEnt, elFx, elResalte);

    urlCesped = A.url('images/scenarios/tile_cesped.png');
    urlCamino = A.url('images/scenarios/tile_camino.png');
    urlCurva = A.url('images/scenarios/tile_camino_curva.png');

    nodos = estado.nodos;
    nodoPorId.clear(); nodoPorPos.clear();
    nodos.forEach((n) => { nodoPorId.set(n.id, n); nodoPorPos.set(n.fila + ',' + n.columna, n); });

    for (let f = 0; f < TAM; f++) {
      for (let c = 0; c < TAM; c++) {
        const div = document.createElement('div');
        div.className = 'celda';
        div.style.left = c * celda + 'px';
        div.style.top = f * celda + 'px';
        div.style.width = celda + 'px';
        div.style.height = celda + 'px';
        div.style.backgroundImage = 'url(' + urlCesped + ')';
        elCeldas.appendChild(div);
      }
    }

    nodos.forEach((n, i) => {
      const dirs = [];
      if (nodos[i - 1]) dirs.push(dir(n, nodos[i - 1]));
      if (nodos[i + 1]) dirs.push(dir(n, nodos[i + 1]));
      const conj = dirs.slice().sort().join('');
      const horizontal = dirs.every((d) => d === 'E' || d === 'W');
      const vertical = dirs.every((d) => d === 'N' || d === 'S');

      const img = document.createElement('img');
      img.className = 'tile';
      img.draggable = false;
      if (horizontal || vertical) {
        img.src = urlCamino;
        img.style.transform = 'rotate(' + (horizontal ? 0 : 90) + 'deg)';
      } else {
        img.src = urlCurva;
        img.style.transform = 'rotate(' + (ROT_CURVA[conj] || 0) + 'deg)';
      }
      const div = document.createElement('div');
      div.className = 'celda-camino';
      div.style.left = n.columna * celda + 'px';
      div.style.top = n.fila * celda + 'px';
      div.style.width = celda + 'px';
      div.style.height = celda + 'px';
      div.appendChild(img);
      elCeldas.appendChild(div);

      if (n.tipo === 'inicio' || n.tipo === 'meta') {  // portal y casa (animados) sobre el camino
        const marca = document.createElement('img');
        marca.className = 'marca marca-' + n.tipo;
        marca.draggable = false;
        marca.src = A.url('images/scenarios/tile_' + (n.tipo === 'inicio' ? 'inicio' : 'meta') + '.gif');
        marca.style.left = n.columna * celda - celda * 0.05 + 'px';
        marca.style.top = n.fila * celda - celda * 0.12 + 'px';
        marca.style.width = celda * 1.1 + 'px';
        marca.style.height = celda * 1.1 + 'px';
        marca.style.zIndex = n.fila * celda + 1;
        elEnt.appendChild(marca);
      }
    });
  }

  function limpiar() {
    temporizadores.forEach(clearTimeout);
    temporizadores = new Set();
    plantas.clear();
    zombis.clear();
    fantasma = null;
  }

  // ---------------------------------------------------------------- estado -> sprites
  function crearPlanta(p, nodo, conAnimacion) {
    const c = PLANTAS[p.tipo];
    const q = pos(nodo);
    const ent = crearEnt(c.reposo, q.x, q.y, { clase: 'planta' });
    ent.id = p.id; ent.tipo = p.tipo; ent.nodo = nodo.id; ent.hpMax = p.hpMax;
    agregarBarra(ent, 'planta');
    actualizarHp(ent, p.hp, p.hpMax, true);
    if (conAnimacion) {
      ent.el.classList.add('aparece');
      espera(600, () => ent.el.classList.remove('aparece'));
    }
    plantas.set(p.id, ent);
    return ent;
  }

  function crearZombi(z, nodo) {
    const c = ZOMBIS[z.tipo];
    const q = pos(nodo);
    const ent = crearEnt(c.caminando, q.x, q.y, { clase: 'zombi' });
    ent.id = z.id; ent.tipo = z.tipo; ent.nodo = nodo.id; ent.hpMax = z.hpMax;
    const prev = nodoPorId.get(nodo.id - 1);
    voltear(ent, !!(prev && nodo.columna < prev.columna));
    agregarBarra(ent, 'zombi');
    actualizarHp(ent, z.hp, z.hpMax, false);
    zombis.set(z.id, ent);
    return ent;
  }

  function sincronizar(estado, sinAnimacion) {
    ultimoEstado = estado;
    const vistosP = new Set(), vistosZ = new Set();

    estado.nodos.forEach((n) => {
      if (n.planta) {
        vistosP.add(n.planta.id);
        const ent = plantas.get(n.planta.id) || crearPlanta(n.planta, n, !sinAnimacion);
        actualizarHp(ent, n.planta.hp, n.planta.hpMax, true);
      }
      if (n.zombie) {
        vistosZ.add(n.zombie.id);
        let ent = zombis.get(n.zombie.id);
        if (!ent) ent = crearZombi(n.zombie, n);
        if (!ent.retenerHp) actualizarHp(ent, n.zombie.hp, n.zombie.hpMax, false);
        if (ent.nodo !== n.id && !ent.muriendo && !ent.saliendo) {
          const previo = nodoPorId.get(ent.nodo);
          if (previo && n.columna !== previo.columna) voltear(ent, n.columna < previo.columna);
          const q = pos(n);
          moverA(ent, q.x, q.y, n.zombie.velocidad * tickMs, true);
          ent.nodo = n.id;
        }
      }
    });

    plantas.forEach((ent, id) => {
      if (!vistosP.has(id) && !ent.destruyendo) { quitar(ent); plantas.delete(id); }
    });
    zombis.forEach((ent, id) => {
      if (!vistosZ.has(id) && !ent.muriendo && !ent.saliendo) { quitar(ent); zombis.delete(id); }
    });

    // animación de cada zombi: mordiendo o caminando
    zombis.forEach((ent) => {
      if (ent.muriendo || ent.saliendo) return;
      cambiarGif(ent, ent.ataca ? ZOMBIS[ent.tipo].comiendo : ZOMBIS[ent.tipo].caminando);
    });
  }

  // ---------------------------------------------------------------- eventos -> animaciones
  function nodoCentro(id) {
    const n = nodoPorId.get(id);
    const q = pos(n);
    return { x: q.x, y: q.y - celda * 0.5 };
  }

  function evDisparo(ev) {
    const pl = plantas.get(ev.planta);
    const objetivo = zombis.get(ev.zombie);
    if (objetivo) { objetivo.retenerHp = true; objetivo.impactosPend = (objetivo.impactosPend || 0) + 1; }
    if (!pl) return 0;

    const desde = nodoPorId.get(ev.desde), hacia = nodoPorId.get(ev.hacia);
    const mirarIzq = hacia.columna < desde.columna || (hacia.columna === desde.columna && pl.flip);
    voltear(pl, mirarIzq);
    cambiarGif(pl, PLANTAS.TIRADOR.disparo, true);
    const info = A.info(PLANTAS.TIRADOR.disparo);
    espera(info.duracion_ms, () => { if (plantas.get(ev.planta) === pl && !pl.destruyendo) cambiarGif(pl, PLANTAS.TIRADOR.reposo); });

    // La semilla sale de la boca cuando el clip llega a su "hito"
    const lanzar = info.hito_ms || 0;
    const boca = { x: pl.x + (mirarIzq ? -1 : 1) * celda * 0.42, y: pl.y - celda * 0.72 };
    const meta = nodoCentro(ev.hacia);
    const dx = meta.x - boca.x, dy = meta.y - boca.y;
    const vuelo = Math.max(200, Math.min(520, Math.hypot(dx, dy) / (celda * 7) * 1000));

    espera(lanzar, () => {
      TD.audio.efecto('disparo');
      const bala = crearEnt(PROYECTIL, boca.x, boca.y, { clase: 'bala', centro: true, escala: (celda * 0.85) / A.info(PROYECTIL).ancho });
      bala.rot = Math.atan2(dy, dx);
      actualizarTransformImg(bala);
      bala.el.style.zIndex = 5000;
      void bala.el.offsetWidth;
      moverA(bala, meta.x, meta.y, vuelo, true);
      bala.el.style.zIndex = 5000;
      espera(vuelo, () => {
        quitar(bala);
        const z = zombis.get(ev.zombie);
        const e = efecto(IMPACTO, meta.x, meta.y, { centro: true, escala: (celda * 0.9) / A.info(IMPACTO).ancho });
        e.el.style.zIndex = 5000;
        TD.audio.efecto('zombie_hit');
        flotar('-' + ev.dano, meta.x, meta.y - celda * 0.5, 'dano');
        if (z) {
          golpe(z);
          z.impactosPend = Math.max(0, (z.impactosPend || 1) - 1);
          if (!z.impactosPend) z.retenerHp = false;
          actualizarHp(z, ev.hp, z.hpMax, false);
        }
      });
    });
    return lanzar + vuelo + 40;  // cuándo termina el impacto (para retrasar la muerte)
  }

  function evMina(ev) {
    const pl = plantas.get(ev.planta);
    const q = pl ? { x: pl.x, y: pl.y } : pos(nodoPorId.get(ev.nodo));
    if (pl) { pl.destruyendo = true; quitar(pl); plantas.delete(ev.planta); }
    const e = efecto(PLANTAS.MINA.explosion, q.x, q.y);
    e.el.style.zIndex = 5000;
    TD.audio.efecto('explosion_mina');
    const z = zombis.get(ev.zombie);
    if (z) { golpe(z); }
    flotar('-' + ev.dano, q.x, q.y - celda * 0.9, 'dano');
    TD.tablero.sacudir();
  }

  function evMuere(ev, retraso) {
    const ent = zombis.get(ev.zombie);
    const c = ZOMBIS[ev.zombieTipo];
    if (!ent) return;
    ent.muriendo = true;
    espera(retraso, () => {
      const p = posVisual(ent);
      const e = efecto(c.muerte, p.x, p.y, { flip: ent.flip });
      e.el.style.zIndex = Math.round(p.y);
      TD.audio.efecto('zombie_muerte');
      flotar('+' + ev.recompensa, p.x, p.y - celda * 1.1, 'oro');
      quitar(ent);
      zombis.delete(ev.zombie);
    });
  }

  function evAtaca(ev, estadoTick) {
    const z = zombis.get(ev.zombie);
    const pl = plantas.get(ev.planta);
    if (z) {
      z.ataca = true;
      const dest = nodoPorId.get(ev.objetivo), orig = nodoPorId.get(ev.nodo);
      if (dest.columna !== orig.columna) voltear(z, dest.columna < orig.columna);
    }
    if (pl) {
      actualizarHp(pl, ev.hpPlanta, pl.hpMax, true);
      espera(250, () => golpe(pl));
    }
    if (!estadoTick.sonoMordida) { estadoTick.sonoMordida = true; TD.audio.efecto('zombie_ataque'); }
  }

  function evPlantaDestruida(ev) {
    if (ev.plantaTipo === 'MINA') return;  // la mina se destruye con su explosión
    const pl = plantas.get(ev.planta);
    const q = pl ? { x: pl.x, y: pl.y } : pos(nodoPorId.get(ev.nodo));
    if (pl) pl.destruyendo = true;
    espera(450, () => {
      if (pl) { quitar(pl); plantas.delete(ev.planta); }
      const c = PLANTAS[ev.plantaTipo];
      const e = efecto(c.destruido, q.x, q.y);
      e.el.style.zIndex = Math.round(q.y);
      TD.audio.efecto('planta_destruida');
    });
  }

  function evMeta(ev) {
    const z = zombis.get(ev.zombie);
    if (!z) return;
    z.saliendo = true;
    const meta = pos(nodos[nodos.length - 1]);
    moverA(z, meta.x, meta.y - celda * 0.1, tickMs, true);
    espera(tickMs * 0.7, () => { z.el.style.transition = 'transform ' + tickMs + 'ms linear, opacity 450ms'; z.el.style.opacity = '0'; });
    espera(tickMs + 500, () => { quitar(z); zombis.delete(ev.zombie); });
    TD.audio.efecto('perder_vida');
  }

  function evRetirada(ev) {
    const pl = plantas.get(ev.planta);
    if (!pl) return;
    pl.destruyendo = true;
    pl.el.style.transition = 'opacity 250ms';
    pl.el.style.opacity = '0';
    flotar('+' + ev.reembolso, pl.x, pl.y - celda * 0.9, 'oro');
    espera(260, () => { quitar(pl); plantas.delete(ev.planta); });
  }

  // ---------------------------------------------------------------- API pública
  TD.tablero = {
    PLANTAS, ZOMBIS, TAM,
    construir,

    setTickMs(ms) { tickMs = ms; },
    celda() { return celda; },

    reconstruir(elementos, tamDisponible) {
      if (!ultimoEstado) return;
      construir(ultimoEstado, elementos, tamDisponible);
      sincronizar(ultimoEstado, true);
    },

    // Aplica los eventos de una respuesta (animaciones) y luego sincroniza los sprites con el estado
    aplicar(respuesta) {
      const estado = respuesta.estado;
      const eventos = respuesta.eventos || [];
      zombis.forEach((z) => { z.ataca = false; });
      const tickCtx = {};

      // primero los disparos: su impacto retrasa la muerte del zombi alcanzado
      const retrasoMuerte = new Map();
      eventos.forEach((ev) => {
        if (ev.tipo === 'disparo') retrasoMuerte.set(ev.zombie, Math.max(retrasoMuerte.get(ev.zombie) || 0, evDisparo(ev)));
      });

      eventos.forEach((ev) => {
        switch (ev.tipo) {
          case 'mina_explota': evMina(ev); break;
          case 'zombie_ataca': evAtaca(ev, tickCtx); break;
          case 'zombie_muere': evMuere(ev, retrasoMuerte.get(ev.zombie) || 0); break;
          case 'planta_destruida': evPlantaDestruida(ev); break;
          case 'zombie_meta': evMeta(ev); break;
          case 'planta_retirada': evRetirada(ev); break;
          default: break;
        }
      });
      sincronizar(estado, false);
    },

    inicial(estado) { sincronizar(estado, true); },

    // ---- interacción del jugador
    celdaEn(clientX, clientY) {
      const r = elTablero.getBoundingClientRect();
      const c = Math.floor((clientX - r.left) / celda), f = Math.floor((clientY - r.top) / celda);
      if (f < 0 || c < 0 || f >= TAM || c >= TAM) return null;
      return { fila: f, columna: c };
    },

    nodoEn(fila, columna) { return nodoPorPos.get(fila + ',' + columna) || null; },

    // Se puede construir en camino sin planta ni zombi (inicio y meta no)
    esConstruible(fila, columna) {
      const n = nodoPorPos.get(fila + ',' + columna);
      if (!n || n.tipo !== 'camino') return false;
      const vivo = ultimoEstado && ultimoEstado.nodos.find((x) => x.id === n.id);
      return !!vivo && !vivo.planta && !vivo.zombie;
    },

    resaltar(fila, columna, tipo) {  // cuadro de la celda bajo el cursor + fantasma de la defensa elegida
      if (!elResalte) return;
      if (fila == null) {
        elResalte.style.display = 'none';
        if (fantasma) { quitar(fantasma); fantasma = null; }
        return;
      }
      const ok = tipo ? TD.tablero.esConstruible(fila, columna) : false;
      elResalte.style.display = 'block';
      elResalte.style.left = columna * celda + 'px';
      elResalte.style.top = fila * celda + 'px';
      elResalte.style.width = celda + 'px';
      elResalte.style.height = celda + 'px';
      elResalte.className = 'resalte ' + (tipo ? (ok ? 'valido' : 'invalido') : '');
      if (tipo && ok) {
        const q = pos({ fila, columna });
        if (!fantasma || fantasma.tipo !== tipo) {
          if (fantasma) quitar(fantasma);
          fantasma = crearEnt(PLANTAS[tipo].reposo, q.x, q.y, { clase: 'fantasma' });
          fantasma.tipo = tipo;
        }
        moverA(fantasma, q.x, q.y, 0);
      } else if (fantasma) {
        quitar(fantasma); fantasma = null;
      }
    },

    sacudir() {
      elTablero.classList.remove('sacude');
      void elTablero.offsetWidth;
      elTablero.classList.add('sacude');
    },

    // Casa de la meta: se sacude cuando un zombi llega
    golpeMeta() {
      const marca = elEnt.querySelector('.marca-meta');
      if (!marca) return;
      marca.classList.remove('sacude-casa');
      void marca.offsetWidth;
      marca.classList.add('sacude-casa');
    },

    detener() { temporizadores.forEach(clearTimeout); temporizadores = new Set(); },
  };
})();
