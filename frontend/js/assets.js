// assets.js - Carga y entrega de imágenes/animaciones.
//
// Todas las imágenes se descargan una vez como Blob; cada <img> recibe su propia URL
// (URL.createObjectURL) para que un GIF de "una sola vez" REINICIE su animación cada vez
// que se vuelve a crear, y para que los GIF en loop no queden sincronizados entre sí.

(function () {
  const TD = (window.TD = window.TD || {});
  const BASE = 'assets/';

  // Imágenes estáticas que no aparecen en el manifest (que solo lista los GIF)
  const ESTATICAS = [
    'images/characters/plants/muro.png',
    'images/characters/plants/tirador.png',
    'images/characters/plants/mina.png',
    'images/characters/zombies/basico.png',
    'images/characters/zombies/rapido.png',
    'images/characters/zombies/tanque.png',
    'images/scenarios/mapa.jpg',
    'images/scenarios/fondo_victoria.jpg',
    'images/scenarios/fondo_game_over.jpg',
  ];

  // Si algo tarda demasiado se descarta y el juego continúa (sin ese archivo)
  function conTiempo(promesa, ms, nombre) {
    let temporizador;
    const limite = new Promise((resolver) => {
      temporizador = setTimeout(() => { console.warn('Tiempo agotado cargando', nombre); resolver(); }, ms);
    });
    return Promise.race([promesa, limite]).finally(() => clearTimeout(temporizador));
  }

  const A = (TD.assets = {
    manifest: {},
    blobs: new Map(),

    // Datos de una animación: ancho, alto, cuadros, duracion_ms, en_loop, ancla [x,y], ref [w,h], hito_ms
    info(clave) {
      return A.manifest[clave];
    },

    // URL nueva (y por lo tanto animación reiniciada) para una imagen ya descargada
    url(clave) {
      const blob = A.blobs.get(clave);
      return blob ? URL.createObjectURL(blob) : BASE + clave;
    },

    soltar(url) {
      if (url && url.startsWith('blob:')) URL.revokeObjectURL(url);
    },

    async cargar(alProgresar) {
      const resp = await fetch(BASE + 'manifest.json', { cache: 'no-cache' });
      A.manifest = await resp.json();

      const claves = Object.keys(A.manifest).concat(ESTATICAS);
      const total = claves.length;
      let hechos = 0;
      const avanzar = () => alProgresar && alProgresar(++hechos, total);

      const tareas = claves.map((clave) => conTiempo((async () => {
        try {
          const r = await fetch(BASE + clave);
          if (!r.ok) throw new Error(r.status);
          A.blobs.set(clave, await r.blob());
        } catch (e) {
          console.warn('No se pudo cargar', clave, e);
        }
      })(), 12000, clave).then(avanzar));
      // Los sonidos se cargan en segundo plano: el juego arranca sin esperarlos y cada efecto
      // empieza a sonar en cuanto llega (así una descarga lenta de audio no retrasa nada).
      TD.audio.cargarEfectos().catch((e) => console.warn('Sonidos:', e));
      await Promise.all(tareas);
    },
  });
})();
