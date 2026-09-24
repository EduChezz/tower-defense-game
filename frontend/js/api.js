// api.js - Comunicación con el backend Java (ServidorApi).
// Toda partida vive en el servidor; el navegador solo dibuja el estado y envía acciones.

(function () {
  const TD = (window.TD = window.TD || {});

  async function pedir(ruta, cuerpo) {
    const opciones = cuerpo === undefined
      ? { method: 'GET' }
      : { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(cuerpo) };
    const r = await fetch(ruta, opciones);
    if (!r.ok) throw new Error('El servidor respondió ' + r.status + ' en ' + ruta);
    return r.json();
  }

  TD.api = {
    catalogo: () => pedir('/api/catalogo'),
    nuevo: (demo) => pedir('/api/juego/nuevo', { demo: !!demo }),
    estado: () => pedir('/api/estado'),
    tick: () => pedir('/api/tick', {}),
    defensa: (tipo, fila, columna) => pedir('/api/defensa', { tipo, fila, columna }),
    retirar: () => pedir('/api/retirar', {}),
  };
})();
