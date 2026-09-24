# Plant Defense

Proyecto de la asignatura **Estructura de Datos** (Universidad Técnica de Ambato).
Integrantes: Eduardo Sánchez, Xander Real, Elvis Zambrano.
Profesor: PhD. Félix Fernández Peña.

## Estado del proyecto

- ✅ **Fase 1 — Lógica del juego (consola):** motor completo en Java usando
  Lista Enlazada (mapa), Cola FIFO (horda de zombis) y Pila LIFO (defensas).
- ✅ **Fase 2 — Interfaz gráfica jugable:** el mismo motor Java expuesto como API y
  una interfaz web (HTML/CSS/JS) con menú, tablero animado, sonidos y música,
  Libro de entidades, demos automáticas y pantallas de victoria / game over.

## Cómo jugar (interfaz web)

Requisito: **JDK 17 o superior** (`javac` y `java` en el PATH).

- **Windows:** doble clic en [`iniciar_juego.bat`](iniciar_juego.bat) — compila, arranca el servidor y abre el navegador.
- **Mac / Linux:** `./iniciar_juego.sh`
- **A mano:**
  ```bash
  cd backend
  javac -encoding UTF-8 -d out src/*.java
  java -cp out ServidorApi ../frontend 8080     # luego abre http://127.0.0.1:8080/
  ```

Usa `127.0.0.1` y no `localhost`: en Windows `localhost` prueba primero IPv6 y cada
petición se demora ~0.2 s. El servidor solo escucha en esa dirección local.

**Controles:** elige una defensa (cartas o teclas `1` `2` `3`) y haz clic en una celda del
camino; `R` retira la última defensa (Pila LIFO); `Espacio` inicia/pausa; clic derecho o
`Esc` cancela la selección. El juego corre en tiempo real (velocidades x1/x2/x3).

## Reglas y equilibrio

Empiezas con **800 monedas** y **3 vidas**; hay **8 oleadas** (40 zombis, entra uno por tick).
Cada zombi derrotado da monedas y retirar una defensa devuelve el 50 % de su costo.

| Defensa | Costo | Vida | Daño | Detalle |
|---|---|---|---|---|
| Muro | 150 | 250 | — | Bloquea el paso |
| Tirador | 200 | 100 | 10 | Alcance de **4 casillas** hacia atrás por el camino |
| Mina | 250 | 1 | 999 | Explota al contacto y se consume |

| Zombi | Vida | Daño | Velocidad | Recompensa |
|---|---|---|---|---|
| Básico | 100 | 20 | 1 tick/nodo | 50 |
| Rápido | 50 | 40 | 1 tick/nodo | 75 |
| Tanque | 300 | 10 | 2 ticks/nodo | 100 |

La clave es **retener a los zombis dentro del alcance de los Tiradores** (un Muro justo delante) y
reponer lo que caiga. Estos valores se calibraron simulando miles de partidas con el propio motor:
sin defensas o colocando al azar no se gana; una distribución razonable gana ~78 % de las veces
(con ~2.3 vidas de 3) y la mejor gana siempre. Los valores viven en `Planta.java`, `Zombie.java` y
`Juego.ALCANCE_TIRADOR`; la interfaz los lee de `/api/catalogo`.

## Cómo ejecutar la versión de consola (Fase 1)

```bash
cd backend
javac -encoding UTF-8 -d out src/*.java
java -cp out Main
```

## Estructura del repositorio

```
tower-defense-game/
├── backend/
│   └── src/               # Código Java (motor del juego, estructuras de datos)
│       ├── Main.java
│       ├── Menu.java
│       ├── Juego.java          # Motor (consola y API): GameLoop de 3 fases, eventos, estado
│       ├── ServidorApi.java    # Servidor HTTP + API JSON (sin librerías externas)
│       ├── EventoJuego.java    # Sucesos del tick (disparo, muerte...) para animar/sonar
│       ├── Json.java           # Escritura/lectura mínima de JSON
│       ├── Cola.java
│       ├── Pila.java
│       ├── ListaEnlazada.java
│       ├── Nodo.java
│       ├── EntidadJuego.java
│       ├── Planta.java
│       └── Zombie.java
│
├── frontend/
│   ├── index.html
│   ├── css/styles.css
│   ├── js/
│   │   ├── main.js        # Pantallas, HUD, controles, bucle de ticks, demo
│   │   ├── tablero.js     # Tablero 7x7 sobre el mapa ilustrado, sprites, animaciones y proyectiles
│   │   ├── audio.js       # Efectos y música
│   │   ├── assets.js      # Precarga de imágenes/animaciones
│   │   └── api.js         # Llamadas al backend
│   └── assets/
│       ├── manifest.json      # Tamaño, cuadros, duración y loop de cada animación
│       ├── images/
│       │   ├── characters/
│       │   │   ├── plants/    # Muro, Tirador, Mina (reposo, disparo/explosión, destruido)
│       │   │   └── zombies/   # Básico, Tanque, Rápido (caminar/correr, comer, muerte)
│       │   ├── effects/       # Proyectil del Tirador e impacto
│       │   ├── scenarios/     # Mapa ilustrado del tablero, fondos de victoria y game over
│       │   └── ui/            # Iconos del HUD (dinero, vida)
│       ├── videos/            # Fondo del menú y logo (sin audio)
│       └── sounds/
│           ├── effects/       # Disparos, explosiones, impactos, etc.
│           └── music/         # Música del menú, de la partida y de la victoria (.mp3)
│
├── iniciar_juego.bat / .sh    # Compila y arranca el juego
│
├── tools/                     # Scripts para preparar los assets (ver más abajo)
│   ├── procesar_assets.py
│   ├── verificar_mapa.py
│   └── spritesheet_to_gif.py
│
├── docs/                  # Documentación e informes del proyecto
│   ├── ANALISIS_PROYECTO_TOWER_DEFENSE.txt
│   └── INFORME_TORRE_DEFENSE_APE.docx
│
└── README.md
```

## Arquitectura y API

El **backend decide todo** (reglas, estructuras de datos, oleadas); el navegador solo dibuja el
estado y envía acciones. `Juego.java` sigue funcionando en consola: en modo API no imprime y
registra cada suceso como evento.

| Método y ruta | Qué hace |
|---|---|
| `GET /api/catalogo` | Datos fijos: costo, vida, daño, velocidad, recompensa |
| `POST /api/juego/nuevo` `{"demo":false}` | Crea una partida (demo = 5 oleadas) |
| `GET /api/estado` | Estado actual |
| `POST /api/tick` | Avanza un tick (ataque de plantas → acción de zombis → limpieza) |
| `POST /api/defensa` `{"tipo":"MURO","fila":0,"columna":3}` | Coloca una defensa |
| `POST /api/retirar` | Retira la última defensa colocada (Pila LIFO, devuelve 50 %) |

Cada respuesta trae `estado` (dinero, vidas, oleada, los 25 nodos de la Lista Enlazada con su
planta/zombi, la **Cola** de pendientes y la **Pila** de defensas) y `eventos` del tick:
`nueva_oleada`, `oleada_final`, `zombie_entra`, `zombie_mueve`, `zombie_ataca`, `zombie_muere`,
`zombie_meta`, `disparo`, `mina_explota`, `planta_colocada`, `planta_retirada`,
`planta_destruida`, `victoria` y `game_over`. El frontend anima y hace sonar cada evento.

El servidor también sirve `frontend/` (con soporte de `Range`, necesario para el video en loop).

**Atajos de desarrollo** (parámetros de la URL): `?auto=menu|libro|creditos|juego|demo|fin`,
`&coloca=MURO@4,TIRADOR@6` (tipo@nodo), `&inicia=1`, `&vel=0..2`, `&mudo=1`.

## Assets del juego

Todo vive en `frontend/assets/`. Las animaciones son GIF con fondo transparente;
`manifest.json` indica el tamaño, la cantidad de cuadros, la duración y si cada una
va en loop (`en_loop: true`) o se reproduce **una sola vez** (`false`: muertes,
explosión, destrucción de plantas e impacto; terminan en un cuadro vacío, así que el
elemento desaparece y el juego lo quita del DOM al cumplirse `duracion_ms`). `ancla` es el punto
donde pisa el personaje, `ref` su tamaño en reposo y `hito_ms` el instante clave de un clip
(cuando sale la semilla del Tirador).

Las animaciones de un mismo personaje comparten lienzo y punto de apoyo
(`bottom-center`), por lo que se pueden intercambiar sin que "salten". Los GIF traen
la sombra recortada: el juego dibuja la suya por CSS. Los personajes están de perfil mirando
a la derecha y el tablero los voltea (`scaleX(-1)`) al avanzar o disparar hacia la izquierda.

### Plantas y zombies (`images/characters/`)

| Personaje | Reposo / movimiento | Acción | Fin |
|---|---|---|---|
| Muro | `plants/muro_reposo.gif` (loop) | — | `plants/muro_destruido.gif` (1 vez) |
| Tirador | `plants/tirador_reposo.gif` (loop) | `plants/tirador_disparo.gif` (loop) | `plants/tirador_destruido.gif` (1 vez) |
| Mina | `plants/mina_reposo.gif` (loop) | — | `plants/mina_explosion.gif` (1 vez) |
| Zombie Básico | `zombies/basico_caminando.gif` | `zombies/basico_comiendo.gif` | `zombies/basico_muerte.gif` (1 vez) |
| Zombie Rápido | `zombies/rapido_corriendo.gif` | `zombies/rapido_comiendo.gif` | `zombies/rapido_muerte.gif` (1 vez) |
| Zombie Tanque | `zombies/tanque_caminando.gif` | `zombies/tanque_comiendo.gif` | `zombies/tanque_muerte.gif` (1 vez) |

"Comiendo" es la animación de ataque del zombie a una planta. Además hay un PNG
estático por personaje para las cartas y el Libro: `plants/muro.png`,
`plants/tirador.png`, `plants/mina.png`, `zombies/basico.png`, `zombies/rapido.png`,
`zombies/tanque.png`.

### Efectos, UI y escenarios

| Archivo | Uso |
|---|---|
| `images/effects/proyectil_tirador.gif` | Semilla que dispara el Tirador (loop) |
| `images/effects/proyectil_impacto.gif` | Impacto de la semilla (1 vez) |
| `images/ui/icono_dinero.gif`, `icono_vida.gif` | Iconos del HUD (loop) |
| `images/scenarios/mapa.jpg` | **Mapa del tablero**: una sola imagen de 1400×1400 con la cuadrícula exacta de 7×7 celdas (200 px c/u), el camino serpenteante, la piedra START y la casa ya dibujados |
| `images/scenarios/fondo_victoria.jpg`, `fondo_game_over.jpg` | Fondos de fin de partida (provisionales) |
| `videos/fondo_menu.mp4` | Fondo animado del menú (sin audio, en loop) |
| `videos/logo_juego.webm` | Logo animado con **transparencia** (WebM VP9 con alfa, sin audio); se genera desde un MP4 de fondo negro con `procesar_assets.py --solo logo` |

**Sobre el mapa** (`mapa.jpg`): se genera con `procesar_assets.py --solo mapa` desde `mapa_decorado.jpg` (recorta el
campo jugable sin el marco de madera y quita las rayas finas que la IA deja en los bordes de celda). La tierra ocupa
~80 % de cada celda del camino y las plantas/zombis pisan a 0.8 de la altura de la celda. La piedra START y la casa
van dibujadas (estáticas); cuando un zombi llega a la meta, la celda de la casa brilla en rojo y el tablero se sacude.
Los antiguos `tile_*` ya no se cargan; `tile_camino_curva.png` y `tile_inicio.gif` quedan en el repo sin usarse.

**Si cambias el mapa**, debe seguir siendo un cuadrado con 7×7 celdas y el mismo camino en serpiente
(fila 0 hacia la derecha, baja por la columna 6, fila 2 hacia la izquierda, baja por la columna 0, fila 4 hacia la
derecha, baja por la columna 6 hasta la casa en la esquina inferior derecha), con la tierra centrada en cada celda.
Compruébalo antes de usarlo:

```bash
python tools/verificar_mapa.py --imagen mapa_decorado.jpg --campo 160,140,1820   # x,y,lado del campo jugable
```

### Sonidos (`sounds/`)

Efectos (pack original de Plants vs Zombies, renombrados según el evento de `Juego.java` y convertidos a `.mp3`;
en este equipo Chrome tardaba ~3 s por cada `.ogg`/`.wav` y recibía vacías las descargas de audio con `fetch`, por eso se
usa `.mp3` con elementos `<audio>`):

| Archivo (`effects/`) | Evento |
|---|---|
| `colocar_defensa.mp3` | Se coloca una planta |
| `error.mp3` | Dinero insuficiente / acción inválida |
| `retirar_defensa.mp3` | Se retira la última defensa colocada |
| `disparo.mp3` | El Tirador ataca |
| `explosion_mina.mp3` | La Mina explota |
| `zombie_hit.mp3` | Un zombie recibe daño |
| `zombie_ataque.mp3` | Un zombie ataca una planta |
| `planta_destruida.mp3` | Una planta llega a 0 HP |
| `zombie_muerte.mp3` | Un zombie es derrotado |
| `perder_vida.mp3` | Un zombie llega a la meta (pierdes 1 vida) |
| `nueva_oleada.mp3` | Comienza una nueva oleada |
| `oleada_final.mp3` | Comienza la última oleada |
| `click_menu.mp3` | Click en botón / navegación de menú |
| `victoria.mp3` | Fin de partida ganada |
| `game_over.mp3` | Fin de partida perdida |

| Archivo (`music/`) | Uso |
|---|---|
| `musica_menu.mp3` | Menú principal (loop sin salto, -20 LUFS) |
| `musica_partida.mp3` | Durante la partida (loop sin salto, -20 LUFS) |
| `musica_victoria.mp3` | Pantalla de victoria |

### Cómo se preparan los assets (`tools/`)

Los archivos "crudos" (tal como salen de las IA) **no se suben al repo**; se procesan con:

```bash
pip install Pillow imageio-ffmpeg
python tools/procesar_assets.py --src "C:/ruta/carpeta_cruda" --dest frontend/assets
# o solo una parte:  --solo gifs | videos | logo | mapa | fondos | musica
```

El script quita marcas de agua de la IA y la sombra morada, recorta y unifica el
lienzo por personaje, corta los loops en su mejor empalme, acorta muertes y
explosiones, quita el audio de los videos, reduce los fondos, arma los loops de
música sin salto y regenera `manifest.json`. Para **reemplazar una animación**
(por ejemplo una Mina mejorada) basta con poner el GIF nuevo en la carpeta cruda con
el mismo nombre (`mina_normal.gif`, `mina_explota.gif`) y volver a ejecutarlo.
`tools/spritesheet_to_gif.py` sirve para partir hojas de sprites en cuadros y GIF y `tools/verificar_mapa.py` comprueba que un mapa calce con la cuadrícula del motor.

### Pendiente

- Reemplazar la Mina por la versión mejorada (el diseño de `mina_explota` no coincide
  con el de `mina_normal`).
- Fondos de victoria/game over animados (los actuales son imágenes).

## Estructuras de datos utilizadas

- **Lista Enlazada Simple:** representa el mapa (matriz 7x7, camino serpentino).
- **Cola (FIFO):** gestiona la horda de zombies.
- **Pila (LIFO):** gestiona las defensas colocadas por el jugador.
