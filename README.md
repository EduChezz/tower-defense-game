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
│       ├── images/
│       │   ├── characters/
│       │   │   ├── plants/    # Sprites de plantas: muro, tirador, mina
│       │   │   └── zombies/   # Sprites de zombies: básico, tanque, rápido
│       │   ├── scenarios/     # Fondos, tiles del camino, mapa
│       │   └── ui/            # Iconos, botones, HUD
│       └── sounds/
│           ├── effects/       # Disparos, explosiones, impactos, etc.
│           └── music/         # Música de fondo / menú
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

## Assets pendientes

Las carpetas `frontend/assets/images/` y `frontend/assets/sounds/` ya están
creadas y versionadas (con `.gitkeep`) para ir agregando ahí, a medida que
se generen:

- Imágenes de personajes (plantas y zombies) y escenarios.
- Efectos de sonido y música.

### Catálogo de sonidos (`frontend/assets/sounds/`)

Efectos ya incluidos (pack original de Plants vs Zombies, renombrados según
el evento del juego que representan):

| Archivo (`effects/`) | Evento en `Juego.java` |
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
| `musica_victoria.ogg` | Música de fondo de la pantalla de victoria |

**Pendiente:** música de fondo del menú principal y de la partida (loop) —
el pack usado solo trae efectos sueltos, no pistas musicales largas.

## Estructuras de datos utilizadas

- **Lista Enlazada Simple:** representa el mapa (matriz 7x7, camino serpentino).
- **Cola (FIFO):** gestiona la horda de zombies.
- **Pila (LIFO):** gestiona las defensas colocadas por el jugador.
