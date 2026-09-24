# Tower Defense - Plantas vs Zombies

Proyecto de la asignatura **Estructura de Datos** (Universidad Técnica de Ambato).
Integrantes: Eduardo Sánchez, Xander Real, Elvis Zambrano.
Profesor: PhD. Félix Fernández Peña.

## Estado del proyecto

- ✅ **Fase 1 — Lógica del juego (consola):** motor completo en Java usando
  Lista Enlazada (mapa), Cola FIFO (horda de zombies) y Pila LIFO (defensas).
- 🚧 **Fase 2 — Interfaz gráfica (en curso):** frontend web (HTML/CSS/JS) que
  reemplazará la interacción por consola, con imágenes y sonidos para
  personajes y escenarios.

## Estructura del repositorio

```
tower-defense-game/
├── backend/
│   └── src/               # Código Java (motor del juego, estructuras de datos)
│       ├── Main.java
│       ├── Menu.java
│       ├── Juego.java
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
│   ├── js/main.js
│   └── assets/
│       ├── manifest.json      # Tamaño, cuadros, duración y loop de cada animación
│       ├── images/
│       │   ├── characters/
│       │   │   ├── plants/    # Muro, Tirador, Mina (reposo, disparo/explosión, destruido)
│       │   │   └── zombies/   # Básico, Tanque, Rápido (caminar/correr, comer, muerte)
│       │   ├── effects/       # Proyectil del Tirador e impacto
│       │   ├── scenarios/     # Tiles del camino, inicio/meta, fondos de victoria y game over
│       │   └── ui/            # Iconos del HUD (dinero, vida)
│       ├── videos/            # Fondo del menú y logo (sin audio)
│       └── sounds/
│           ├── effects/       # Disparos, explosiones, impactos, etc.
│           └── music/         # Música del menú, de la partida y de la victoria
│
├── tools/                     # Scripts para preparar los assets (ver más abajo)
│   ├── procesar_assets.py
│   └── spritesheet_to_gif.py
│
├── docs/                  # Documentación e informes del proyecto
│   ├── ANALISIS_PROYECTO_TOWER_DEFENSE.txt
│   └── INFORME_TORRE_DEFENSE_APE.docx
│
└── README.md
```

## Cómo ejecutar la Fase 1 (consola, Java)

```bash
cd backend/src
javac *.java
java Main
```

## Cómo ver la Fase 2 (frontend, en construcción)

```bash
cd frontend
# Abrir index.html directamente en el navegador, o servirlo con un servidor
# estático simple, por ejemplo:
python -m http.server 8080
```

Por ahora el frontend dibuja el tablero 7x7 de forma estática. El siguiente
paso es exponer el estado del `Juego.java` mediante una API (backend) para
que el frontend consuma el estado real de la partida y envíe acciones del
jugador (colocar/retirar defensas).

## Assets del juego

Todo vive en `frontend/assets/`. Las animaciones son GIF con fondo transparente;
`manifest.json` indica el tamaño, la cantidad de cuadros, la duración y si cada una
va en loop (`en_loop: true`) o se reproduce **una sola vez** (`false`: muertes,
explosión, destrucción de plantas e impacto; terminan en un cuadro vacío, así que el
elemento desaparece y el juego debe quitarlo del DOM al cumplirse `duracion_ms`).

Las animaciones de un mismo personaje comparten lienzo y punto de apoyo
(`bottom-center`), por lo que se pueden intercambiar sin que "salten". Los GIF traen
la sombra recortada: el juego debe dibujar su propia sombra por CSS. Los personajes
están de perfil mirando a la derecha; hay que voltearlos (`scaleX(-1)`) al avanzar a
la izquierda.

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
| `images/scenarios/tile_cesped.png` | Celda sin camino |
| `images/scenarios/tile_camino.png` | Camino recto (rotarlo 90° por CSS para el vertical) |
| `images/scenarios/tile_camino_curva.png` | Curva; rotarla 90°/180°/270° para las 4 esquinas |
| `images/scenarios/tile_inicio.gif`, `tile_meta.gif` | Celdas de inicio y meta (loop) |
| `images/scenarios/fondo_victoria.jpg`, `fondo_game_over.jpg` | Fondos de fin de partida (provisionales) |
| `videos/fondo_menu.mp4` | Fondo animado del menú (sin audio, en loop) |
| `videos/logo_juego.mp4` | Logo animado (sin audio; provisional, trae fondo sólido) |

### Sonidos (`sounds/`)

Efectos (pack original de Plants vs Zombies, renombrados según el evento de `Juego.java`):

| Archivo (`effects/`) | Evento |
|---|---|
| `colocar_defensa.ogg` | Se coloca una planta |
| `error.ogg` | Dinero insuficiente / acción inválida |
| `retirar_defensa.ogg` | Se retira la última defensa colocada |
| `disparo.ogg` | El Tirador ataca |
| `explosion_mina.ogg` | La Mina explota |
| `zombie_hit.ogg` | Un zombie recibe daño |
| `zombie_ataque.ogg` | Un zombie ataca una planta |
| `planta_destruida.ogg` | Una planta llega a 0 HP |
| `zombie_muerte.ogg` | Un zombie es derrotado |
| `perder_vida.ogg` | Un zombie llega a la meta (pierdes 1 vida) |
| `nueva_oleada.ogg` | Comienza una nueva oleada |
| `oleada_final.ogg` | Comienza la última oleada |
| `click_menu.ogg` | Click en botón / navegación de menú |
| `victoria.ogg` | Fin de partida ganada |
| `game_over.ogg` | Fin de partida perdida |

| Archivo (`music/`) | Uso |
|---|---|
| `musica_menu.ogg` | Menú principal (loop sin salto, -20 LUFS) |
| `musica_partida.ogg` | Durante la partida (loop sin salto, -20 LUFS) |
| `musica_victoria.ogg` | Pantalla de victoria |

### Cómo se preparan los assets (`tools/`)

Los archivos "crudos" (tal como salen de las IA) **no se suben al repo**; se procesan con:

```bash
pip install Pillow imageio-ffmpeg
python tools/procesar_assets.py --src "C:/ruta/carpeta_cruda" --dest frontend/assets
# o solo una parte:  --solo gifs | videos | fondos | musica
```

El script quita marcas de agua de la IA y la sombra morada, recorta y unifica el
lienzo por personaje, corta los loops en su mejor empalme, acorta muertes y
explosiones, quita el audio de los videos, reduce los fondos, arma los loops de
música sin salto y regenera `manifest.json`. Para **reemplazar una animación**
(por ejemplo una Mina mejorada) basta con poner el GIF nuevo en la carpeta cruda con
el mismo nombre (`mina_normal.gif`, `mina_explota.gif`) y volver a ejecutarlo.
`tools/spritesheet_to_gif.py` sirve para partir hojas de sprites en cuadros y GIF.

### Pendiente

- Reemplazar la Mina por la versión mejorada (el diseño de `mina_explota` no coincide
  con el de `mina_normal`).
- Logo definitivo (el actual dice "PLANT DEFENSE" y trae otras plantas).
- Fondos de victoria/game over animados (los actuales son imágenes).

## Estructuras de datos utilizadas

- **Lista Enlazada Simple:** representa el mapa (matriz 7x7, camino serpentino).
- **Cola (FIFO):** gestiona la horda de zombies.
- **Pila (LIFO):** gestiona las defensas colocadas por el jugador.
