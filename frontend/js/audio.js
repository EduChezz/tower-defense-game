// audio.js - Efectos de sonido (se pueden solapar) y música (con fundidos).
//
// Todo se reproduce con elementos <audio>; los efectos se precargan una vez y cada sonido se
// dispara con un clon, así varios pueden sonar a la vez. (No se usa fetch + Web Audio porque en
// algunos equipos Windows la descarga de audio con fetch() es interceptada y llega vacía.)
// Los navegadores bloquean el audio hasta que la persona interactúa con la página:
// despertar() se llama en el primer clic/tecla y reanuda lo que estaba pendiente.

(function () {
  const TD = (window.TD = window.TD || {});
  const RUTA = 'assets/sounds/';

  const EFECTOS = [
    'click_menu', 'colocar_defensa', 'error', 'retirar_defensa', 'disparo', 'explosion_mina',
    'zombie_hit', 'zombie_ataque', 'planta_destruida', 'zombie_muerte', 'perder_vida',
    'nueva_oleada', 'oleada_final', 'victoria', 'game_over',
  ];
  // Volumen relativo de cada efecto (los muy frecuentes van más bajos)
  const VOLUMEN = { disparo: 0.45, zombie_hit: 0.55, zombie_ataque: 0.5, click_menu: 0.7, zombie_muerte: 0.7 };
  const MUSICA = {
    menu: { archivo: 'music/musica_menu.mp3', loop: true },
    partida: { archivo: 'music/musica_partida.mp3', loop: true },
    victoria: { archivo: 'music/musica_victoria.mp3', loop: false },
  };
  const VOL_MUSICA = 0.32;
  const VOL_EFECTOS = 0.85;

  const base = {};   // efectos ya cargados: nombre -> <audio>
  let musicaEl = null;
  let musicaNombre = null;
  let musicaPendiente = null;
  const ajustes = { musica: true, efectos: true };
  try {
    Object.assign(ajustes, JSON.parse(localStorage.getItem('td_audio') || '{}'));
  } catch (e) { /* sin localStorage: se usan los valores por defecto */ }

  function guardar() {
    try { localStorage.setItem('td_audio', JSON.stringify(ajustes)); } catch (e) { /* ignorar */ }
  }

  function fundir(el, hasta, ms, alTerminar) {
    const desde = el.volume;
    const pasos = Math.max(1, Math.round(ms / 40));
    let i = 0;
    const t = setInterval(() => {
      i++;
      el.volume = Math.min(1, Math.max(0, desde + ((hasta - desde) * i) / pasos));
      if (i >= pasos) {
        clearInterval(t);
        if (alTerminar) alTerminar();
      }
    }, 40);
  }

  TD.audio = {
    efectos: EFECTOS,
    ajustes,

    // Precarga todos los efectos en segundo plano (no bloquea el arranque del juego)
    async cargarEfectos(alProgresar) {
      if (/[?&]mudo=1/.test(location.search)) return;  // solo para pruebas automáticas sin audio
      await Promise.all(EFECTOS.map((nombre) => new Promise((listo) => {
        const a = new Audio(RUTA + 'effects/' + nombre + '.mp3');
        a.preload = 'auto';
        let hecho = false;
        const fin = (ok) => {
          if (hecho) return;
          hecho = true;
          if (ok) base[nombre] = a; else console.warn('No se pudo cargar el sonido', nombre);
          if (alProgresar) alProgresar();
          listo();
        };
        a.addEventListener('canplaythrough', () => fin(true), { once: true });
        a.addEventListener('error', () => fin(false), { once: true });
        setTimeout(() => fin(false), 15000);
      })));
    },

    // Llamar dentro de un gesto del usuario (clic, tecla)
    despertar() {
      if (musicaPendiente && ajustes.musica) {
        const n = musicaPendiente;
        musicaPendiente = null;
        TD.audio.musica(n);
      }
    },

    efecto(nombre, opciones) {
      const molde = base[nombre];
      if (!ajustes.efectos || !molde) return;
      const o = opciones || {};
      const a = molde.cloneNode();  // un clon por disparo: permite que suenen varios a la vez
      a.volume = Math.min(1, VOL_EFECTOS * (VOLUMEN[nombre] || 1) * (o.volumen || 1));
      a.playbackRate = (o.velocidad || 1) * (0.96 + Math.random() * 0.08);
      a.play().catch(() => {});
    },

    // Cambia la música con un fundido. musica(null) la detiene.
    musica(nombre) {
      if (nombre === musicaNombre && musicaEl && !musicaEl.paused) return;
      const anterior = musicaEl;
      if (anterior) fundir(anterior, 0, 500, () => anterior.pause());
      musicaEl = null;
      musicaNombre = nombre;
      if (!nombre || !MUSICA[nombre]) return;
      if (!ajustes.musica) { musicaPendiente = nombre; return; }

      const el = new Audio(RUTA + MUSICA[nombre].archivo);
      el.loop = MUSICA[nombre].loop;
      el.volume = 0;
      musicaEl = el;
      el.play().then(() => fundir(el, VOL_MUSICA, 900)).catch(() => {
        musicaPendiente = nombre;  // bloqueada por el navegador: se reintenta en el primer gesto
        musicaEl = null;
      });
    },

    alternarMusica() {
      ajustes.musica = !ajustes.musica;
      guardar();
      if (ajustes.musica) {
        const n = musicaNombre;
        musicaNombre = null;
        TD.audio.musica(n || 'menu');
      } else if (musicaEl) {
        const el = musicaEl;
        fundir(el, 0, 300, () => el.pause());
        musicaPendiente = musicaNombre;
        musicaEl = null;
      }
      return ajustes.musica;
    },

    alternarEfectos() {
      ajustes.efectos = !ajustes.efectos;
      guardar();
      return ajustes.efectos;
    },
  };
})();
