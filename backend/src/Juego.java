/**
 * Clase Juego - Motor del juego
 * Implementa el GameLoop con 3 fases: Ataque plantas, Acción zombies, Limpieza
 *
 * - Cola de pendientes: zombies que aún no entran al mapa (entra uno por tick al nodo 0)
 * - Cola de activos: zombies en el mapa; el orden FIFO equivale al orden de avance
 * - Lista enlazada: el mapa; cada nodo guarda su planta y su zombie
 * - Pila: defensas en orden de colocación (permite retirar la última)
 *
 * Dos modos de uso:
 *  - Consola: new Juego(demo, scanner) + ejecutar()   (Menu.java)
 *  - API web: Juego.paraApi(demo) y luego avanzarTick() / colocarDefensaApi() / retirarUltimaApi();
 *    en este modo no se escribe en consola y cada suceso queda registrado como EventoJuego.
 */
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

public class Juego {
    private ListaEnlazada mapa;
    private Cola colaZombies;   // Zombies pendientes de entrar al mapa
    private Cola colaActivos;   // Zombies en el mapa, en orden de entrada (el primero es el más avanzado)
    private Pila pilaDefensas;
    private int dinero;
    private int dineroInicial;
    private int vidas;
    private int vidasInicial;
    private int tickActual;
    private int oleadaActual;
    private int totalOleadas;
    private boolean juegoActivo;
    private boolean victoria;
    private Random random;
    private Scanner scanner;

    // Modo API: sin consola, con registro de eventos
    private boolean silencioso;
    private final List<EventoJuego> eventos = new ArrayList<>();

    // Modo demo automático
    private boolean automatico;
    private boolean demoGanar;
    private int pausaMs = 300;

    // El Tirador solo alcanza a los zombies de los ALCANCE_TIRADOR nodos anteriores al suyo en el camino
    public static final int ALCANCE_TIRADOR = 4;

    // Cantidad de zombies por oleada (DEMO: 5 oleadas, COMPLETO: 8 oleadas)
    private static final int[] OLEADAS_DEMO = {3, 3, 4, 4, 5};
    private static final int[] OLEADAS_COMPLETO = {3, 3, 4, 4, 5, 6, 7, 8};
    private int[] cantidades;

    // Estrategia de la demo ganadora: {tipo (1 muro, 2 tirador, 3 mina), id de nodo}
    // Los muros (nodos 4 y 5) detienen a los zombies dentro del alcance de los Tiradores (nodos 6 a 9)
    private static final int[][] ESTRATEGIA_DEMO = {
        {2, 6}, {1, 5}, {2, 7}, {1, 4}, {3, 3}, {2, 8}, {2, 9}
    };

    public Juego(boolean isDemo, Scanner scanner) {
        this.mapa = new ListaEnlazada();
        this.colaZombies = new Cola();
        this.colaActivos = new Cola();
        this.pilaDefensas = new Pila();
        this.dineroInicial = 800;
        this.dinero = this.dineroInicial;
        this.vidasInicial = 3;
        this.vidas = this.vidasInicial;
        this.tickActual = 0;
        this.oleadaActual = 0;
        this.cantidades = isDemo ? OLEADAS_DEMO : OLEADAS_COMPLETO;
        this.totalOleadas = this.cantidades.length;
        this.juegoActivo = true;
        this.random = new Random();
        this.scanner = scanner;
    }

    // Partida para la API web: sin Scanner ni consola, con la primera oleada ya generada
    public static Juego paraApi(boolean isDemo) {
        Juego juego = new Juego(isDemo, null);
        juego.silencioso = true;
        juego.generarOleada(0);
        return juego;
    }

    public void setPausaMs(int pausaMs) {
        this.pausaMs = pausaMs;
    }

    public boolean gano() {
        return this.victoria;
    }

    // ========== SALIDA: CONSOLA Y EVENTOS ==========
    private void log(String mensaje) {
        if (!this.silencioso) {
            System.out.println(mensaje);
        }
    }

    // Crea un evento; solo se guarda en modo API
    private EventoJuego evento(String tipo) {
        EventoJuego e = new EventoJuego(tipo);
        if (this.silencioso) {
            this.eventos.add(e);
        }
        return e;
    }

    // Devuelve los eventos acumulados desde la última llamada y limpia la lista
    public List<EventoJuego> tomarEventos() {
        List<EventoJuego> copia = new ArrayList<>(this.eventos);
        this.eventos.clear();
        return copia;
    }

    // ========== FASE 1: ATAQUE DE PLANTAS ==========
    private void faseAtaquePlantas() {
        Nodo actual = this.mapa.getCabeza();

        while (actual != null) {
            if (actual.planta != null && actual.planta.estaVivo()) {
                if (actual.planta.tipo == Planta.TipoPlanta.TIRADOR) {
                    // Los zombies vienen desde el nodo 0: dispara al más cercano que se acerca,
                    // dentro de su alcance (los ALCANCE_TIRADOR nodos anteriores al suyo)
                    Nodo nodoObjetivo = null;
                    Nodo buscador = this.mapa.obtenerNodo(Math.max(0, actual.idPosicion - ALCANCE_TIRADOR));
                    while (buscador != null && buscador != actual) {
                        if (buscador.zombie != null && buscador.zombie.estaVivo()) {
                            nodoObjetivo = buscador;
                        }
                        buscador = buscador.siguiente;
                    }
                    if (nodoObjetivo != null) {
                        int danioAplicado = actual.planta.danoBase;
                        nodoObjetivo.zombie.recibirDano(danioAplicado);
                        log("  [EVENTO] Tirador en Nodo " + actual.idPosicion +
                            " inflige " + danioAplicado + " daño a " + nodoObjetivo.zombie.nombre +
                            " en Nodo " + nodoObjetivo.idPosicion);
                        evento("disparo")
                            .con("planta", actual.planta.id)
                            .con("desde", actual.idPosicion)
                            .con("hacia", nodoObjetivo.idPosicion)
                            .con("zombie", nodoObjetivo.zombie.id)
                            .con("dano", danioAplicado)
                            .con("hp", nodoObjetivo.zombie.hp);
                    }
                } else if (actual.planta.tipo == Planta.TipoPlanta.MINA) {
                    // Si hay zombie en el mismo nodo, explota
                    if (actual.zombie != null && actual.zombie.estaVivo()) {
                        int danioAplicado = actual.planta.danoBase;
                        actual.zombie.recibirDano(danioAplicado);
                        actual.planta.recibirDano(999);
                        log("  [EVENTO] Mina en Nodo " + actual.idPosicion +
                            " explota e inflige " + danioAplicado + " daño a " + actual.zombie.nombre);
                        evento("mina_explota")
                            .con("planta", actual.planta.id)
                            .con("nodo", actual.idPosicion)
                            .con("zombie", actual.zombie.id)
                            .con("dano", danioAplicado);
                    }
                }
            }
            actual = actual.siguiente;
        }
    }

    // ========== FASE 2: ACCIÓN Y AVANCE DE ZOMBIES ==========
    private void faseAccionZombies() {
        // FIFO: el primero de la cola es el más avanzado, así libera su nodo antes que los de atrás
        Cola procesados = new Cola();

        while (!this.colaActivos.estaVacia()) {
            Zombie zombie = this.colaActivos.desencolar();
            Nodo nodo = zombie.nodoActual;

            if (!zombie.estaVivo()) {
                this.dinero += zombie.recompensa;
                log("  [EVENTO] " + zombie.nombre + " ha sido derrotado. Ganas " +
                    zombie.recompensa + " monedas.");
                evento("zombie_muere")
                    .con("zombie", zombie.id)
                    .con("zombieTipo", zombie.tipo.name())
                    .con("nodo", nodo.idPosicion)
                    .con("recompensa", zombie.recompensa);
                nodo.zombie = null;
                continue;
            }

            zombie.incrementarTick();
            if (zombie.puedeAvanzar()) {
                Nodo siguiente = nodo.siguiente;

                if (siguiente.planta != null && siguiente.planta.tipo != Planta.TipoPlanta.MINA
                        && siguiente.planta.estaVivo()) {
                    // La planta bloquea el paso: el zombie la ataca
                    siguiente.planta.recibirDano(zombie.danoBase);
                    log("  [EVENTO] " + zombie.nombre + " en Nodo " + nodo.idPosicion +
                        " ataca a " + siguiente.planta.nombre + " (-" + zombie.danoBase + " HP)");
                    evento("zombie_ataca")
                        .con("zombie", zombie.id)
                        .con("zombieTipo", zombie.tipo.name())
                        .con("nodo", nodo.idPosicion)
                        .con("planta", siguiente.planta.id)
                        .con("objetivo", siguiente.idPosicion)
                        .con("dano", zombie.danoBase)
                        .con("hpPlanta", siguiente.planta.hp);
                } else if (siguiente.siguiente == null) {
                    // Llegó a la meta: sale del mapa y quita una vida
                    nodo.zombie = null;
                    this.vidas--;
                    log("  [EVENTO] " + zombie.nombre + " ha alcanzado la META. Pierdes 1 vida.");
                    evento("zombie_meta")
                        .con("zombie", zombie.id)
                        .con("zombieTipo", zombie.tipo.name())
                        .con("nodo", nodo.idPosicion)
                        .con("vidas", this.vidas);
                    continue;
                } else if (siguiente.zombie == null) {
                    nodo.zombie = null;
                    siguiente.zombie = zombie;
                    zombie.nodoActual = siguiente;
                    zombie.resetearTick();
                    evento("zombie_mueve")
                        .con("zombie", zombie.id)
                        .con("desde", nodo.idPosicion)
                        .con("hasta", siguiente.idPosicion);
                }
            }

            procesados.encolar(zombie);
        }

        // Restaurar los zombies activos conservando el orden
        while (!procesados.estaVacia()) {
            this.colaActivos.encolar(procesados.desencolar());
        }

        // Entra el siguiente zombie pendiente si el nodo de inicio está libre
        Nodo inicio = this.mapa.getCabeza();
        if (!this.colaZombies.estaVacia() && inicio.zombie == null) {
            Zombie entrante = this.colaZombies.desencolar();
            entrante.nodoActual = inicio;
            inicio.zombie = entrante;
            this.colaActivos.encolar(entrante);
            log("  [EVENTO] " + entrante.nombre + " entra al mapa.");
            evento("zombie_entra")
                .con("zombie", entrante.id)
                .con("zombieTipo", entrante.tipo.name())
                .con("nodo", inicio.idPosicion);
        }
    }

    // ========== FASE 3: LIMPIEZA Y RECOLECCIÓN DE BASURA ==========
    private void faseLimpieza() {
        Nodo actual = this.mapa.getCabeza();
        while (actual != null) {
            if (actual.planta != null && !actual.planta.estaVivo()) {
                log("  [LIMPIEZA] " + actual.planta.nombre + " en Nodo " +
                    actual.idPosicion + " ha sido destruida.");
                evento("planta_destruida")
                    .con("planta", actual.planta.id)
                    .con("plantaTipo", actual.planta.tipo.name())
                    .con("nodo", actual.idPosicion);
                actual.planta = null;
            }
            actual = actual.siguiente;
        }
    }

    private boolean hayZombiesEnMapa() {
        return !this.colaActivos.estaVacia();
    }

    private boolean oleadaTerminada() {
        return this.colaZombies.estaVacia() && !hayZombiesEnMapa();
    }

    // ========== UN TICK COMPLETO (consola y API) ==========
    // Ejecuta las 3 fases, avanza el reloj, lanza la siguiente oleada y revisa el fin de partida
    public void avanzarTick() {
        if (!this.juegoActivo) {
            return;
        }

        log("\n[EJECUTANDO TICK " + this.tickActual + "]");
        faseAtaquePlantas();
        faseAccionZombies();
        faseLimpieza();

        this.tickActual++;

        // Verificar si oleada terminó
        if (oleadaTerminada() && this.oleadaActual < this.totalOleadas - 1 && this.vidas > 0) {
            this.oleadaActual++;
            generarOleada(this.oleadaActual);
            log("\n>>> NUEVA OLEADA: " + (this.oleadaActual + 1) + " / " + this.totalOleadas);
            log(">>> Se han generado " + this.cantidades[this.oleadaActual] + " zombies");
        }

        // Verificar condiciones de fin
        if (this.vidas <= 0) {
            this.juegoActivo = false;
            log("\n╔════════════════════════════════════════════════════════════╗");
            log("║                    ¡GAME OVER!                            ║");
            log("║            Has perdido todas tus vidas.                   ║");
            log("╚════════════════════════════════════════════════════════════╝");
            evento("game_over");
        } else if (oleadaTerminada() && this.oleadaActual >= this.totalOleadas - 1) {
            this.juegoActivo = false;
            this.victoria = true;
            log("\n╔════════════════════════════════════════════════════════════╗");
            log("║                    ¡VICTORIA!                             ║");
            log("║          Has derrotado todas las oleadas.                 ║");
            log("║              Dinero final: " + this.dinero);
            log("╚════════════════════════════════════════════════════════════╝");
            evento("victoria").con("dinero", this.dinero);
        }
    }

    // ========== MOSTRAR ESTADO DEL JUEGO ==========
    private void mostrarEstado() {
        System.out.println("\n╔════════════════════════════════════════════════════════════╗");
        System.out.println("║              TICK " + this.tickActual + " | OLEADA " + (this.oleadaActual + 1) + " / " +
                         this.totalOleadas + " | DINERO: " + this.dinero + " | VIDAS: " + this.vidas);
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        mostrarMapa();
        System.out.println("Zombies en el mapa: " + this.colaActivos.getTamanio() +
                         " | Por llegar: " + this.colaZombies.getTamanio() +
                         " | Defensas: " + this.pilaDefensas.getTamanio());
    }

    private void mostrarMapa() {
        System.out.println();
        this.mapa.imprimirMapa();
        System.out.println();
    }

    // ========== MÉTODO PRINCIPAL: JUEGO (CONSOLA) ==========
    public void ejecutar() {
        generarOleada(0);

        while (this.juegoActivo && this.vidas > 0) {
            if (this.automatico) {
                if (this.demoGanar) {
                    aplicarEstrategiaDemo();
                }
                mostrarEstado();
                pausar();
                if (this.tickActual > 600) {
                    break;
                }
            } else {
                mostrarEstado();
                turnoJugador();
            }

            // Ejecutar Game Loop
            avanzarTick();
        }
    }

    // ========== DEMOS AUTOMÁTICAS ==========
    public void ejecutarDemo(boolean ganar) {
        this.automatico = true;
        this.demoGanar = ganar;
        ejecutar();
    }

    // Coloca, en orden, las defensas de la estrategia que falten y que alcance el dinero
    private void aplicarEstrategiaDemo() {
        for (int[] paso : ESTRATEGIA_DEMO) {
            Nodo nodo = this.mapa.obtenerNodo(paso[1]);
            if (nodo.planta != null || nodo.zombie != null) {
                continue;
            }
            Planta planta = crearPlanta(paso[0]);
            if (this.dinero >= planta.costoEnergia) {
                colocarPlanta(planta, nodo);
            }
        }
    }

    private void pausar() {
        if (this.pausaMs <= 0) {
            return;
        }
        try {
            Thread.sleep(this.pausaMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ========== TURNO DEL JUGADOR ==========
    private void turnoJugador() {
        System.out.println("\n[ACCIONES]");
        System.out.println("1. Continuar turno");
        System.out.println("2. Colocar defensa");
        System.out.println("3. Ver información");
        System.out.println("4. Retirar última defensa colocada");
        System.out.print("Opción: ");

        int opcion = obtenerEntrada(1, 4);

        if (opcion == 2) {
            colocarDefensa();
        } else if (opcion == 3) {
            mostrarInformacion();
        } else if (opcion == 4) {
            retirarUltimaDefensa();
        }
    }

    // ========== GENERAR OLEADA ==========
    private void generarOleada(int numOleada) {
        int cantidadZombies = this.cantidades[numOleada];

        for (int i = 0; i < cantidadZombies; i++) {
            int tipoAleatorio = this.random.nextInt(3);
            Zombie nuevoZombie;

            if (tipoAleatorio == 0) {
                nuevoZombie = new Zombie(Zombie.BASICO);
            } else if (tipoAleatorio == 1) {
                nuevoZombie = new Zombie(Zombie.TANQUE);
            } else {
                nuevoZombie = new Zombie(Zombie.RAPIDO);
            }

            this.colaZombies.encolar(nuevoZombie);
        }

        evento("nueva_oleada").con("oleada", numOleada + 1).con("zombies", cantidadZombies);
        if (numOleada == this.totalOleadas - 1 && this.totalOleadas > 1) {
            evento("oleada_final");
        }
    }

    // ========== COLOCAR / RETIRAR DEFENSA ==========
    private Planta crearPlanta(int tipo) {
        if (tipo == 1) {
            return new Planta(Planta.MURO);
        } else if (tipo == 2) {
            return new Planta(Planta.TIRADOR);
        }
        return new Planta(Planta.MINA);
    }

    private void colocarPlanta(Planta planta, Nodo nodo) {
        nodo.planta = planta;
        planta.nodo = nodo;
        this.dinero -= planta.costoEnergia;
        this.pilaDefensas.push(planta);
        log("✓ " + planta.nombre + " colocado en Nodo " + nodo.idPosicion +
            " (fila " + nodo.fila + ", col " + nodo.columna + "). Dinero restante: " + this.dinero);
        evento("planta_colocada")
            .con("planta", planta.id)
            .con("plantaTipo", planta.tipo.name())
            .con("nodo", nodo.idPosicion)
            .con("costo", planta.costoEnergia);
    }

    private void colocarDefensa() {
        System.out.println("\n[COLOCAR DEFENSA]");
        System.out.println("1. Muro (Costo: 150, HP: 250)");
        System.out.println("2. Tirador (Costo: 200, HP: 100, Daño: 10, Alcance: " + ALCANCE_TIRADOR + " nodos)");
        System.out.println("3. Mina (Costo: 250, HP: 1, Daño: 999)");
        System.out.print("Selecciona tipo: ");

        int tipo = obtenerEntrada(1, 3);
        Planta plantaAColocar = crearPlanta(tipo);

        if (this.dinero < plantaAColocar.costoEnergia) {
            System.out.println("❌ Dinero insuficiente. Necesitas " + plantaAColocar.costoEnergia +
                             " pero tienes " + this.dinero);
            return;
        }

        System.out.print("Fila (0-6): ");
        int fila = obtenerEntrada(0, ListaEnlazada.TAM - 1);
        System.out.print("Columna (0-6): ");
        int columna = obtenerEntrada(0, ListaEnlazada.TAM - 1);
        Nodo nodoSeleccionado = this.mapa.obtenerNodoEn(fila, columna);

        if (nodoSeleccionado == null) {
            System.out.println("❌ Esa celda no es parte del camino.");
            return;
        }
        if (nodoSeleccionado == this.mapa.getCabeza() || nodoSeleccionado.siguiente == null) {
            System.out.println("❌ No se puede construir en el inicio (S) ni en la meta (E).");
            return;
        }
        if (nodoSeleccionado.planta != null) {
            System.out.println("❌ Ya hay una planta en ese nodo.");
            return;
        }
        if (nodoSeleccionado.zombie != null) {
            System.out.println("❌ Hay un zombie en ese nodo.");
            return;
        }

        colocarPlanta(plantaAColocar, nodoSeleccionado);
    }

    // Saca de la pila la última defensa que siga en pie y devuelve la mitad de su costo.
    // Devuelve la planta retirada, o null si no había ninguna.
    private Planta retirarUltimaPlanta() {
        Planta planta = this.pilaDefensas.pop();
        while (planta != null && (planta.nodo == null || planta.nodo.planta != planta)) {
            planta = this.pilaDefensas.pop(); // ya fue destruida o retirada
        }

        if (planta == null) {
            return null;
        }

        planta.nodo.planta = null;
        int reembolso = planta.costoEnergia / 2;
        this.dinero += reembolso;
        log("✓ " + planta.nombre + " retirado del Nodo " + planta.nodo.idPosicion +
            ". Recuperas " + reembolso + " monedas.");
        evento("planta_retirada")
            .con("planta", planta.id)
            .con("plantaTipo", planta.tipo.name())
            .con("nodo", planta.nodo.idPosicion)
            .con("reembolso", reembolso);
        return planta;
    }

    private void retirarUltimaDefensa() {
        if (retirarUltimaPlanta() == null) {
            System.out.println("❌ No hay defensas para retirar.");
        }
    }

    private void mostrarInformacion() {
        System.out.println("\n[INFORMACIÓN DE JUEGO]");
        System.out.println("Dinero: " + this.dinero);
        System.out.println("Vidas: " + this.vidas);
        System.out.println("Oleada: " + (this.oleadaActual + 1) + " / " + this.totalOleadas);
        System.out.println("Zombies en el mapa: " + this.colaActivos.getTamanio());
        System.out.println("Zombies pendientes en cola: " + this.colaZombies.getTamanio());
        System.out.println("Defensas colocadas: " + this.pilaDefensas.getTamanio());
    }

    private int obtenerEntrada(int min, int max) {
        while (true) {
            try {
                int input = Integer.parseInt(this.scanner.nextLine().trim());
                if (input >= min && input <= max) {
                    return input;
                }
                System.out.print("Ingresa un número entre " + min + " y " + max + ": ");
            } catch (NumberFormatException e) {
                System.out.print("Entrada inválida. Intenta de nuevo: ");
            }
        }
    }

    // ==================================================================
    // API WEB: acciones del jugador (devuelven null si salió bien, o el motivo del error)
    // ==================================================================
    public String colocarDefensaApi(String tipo, int fila, int columna) {
        if (!this.juegoActivo) {
            return "La partida ya terminó.";
        }

        Planta planta;
        if ("MURO".equals(tipo)) {
            planta = crearPlanta(1);
        } else if ("TIRADOR".equals(tipo)) {
            planta = crearPlanta(2);
        } else if ("MINA".equals(tipo)) {
            planta = crearPlanta(3);
        } else {
            return "Tipo de defensa inválido.";
        }

        if (this.dinero < planta.costoEnergia) {
            return "Dinero insuficiente. Necesitas " + planta.costoEnergia + " pero tienes " + this.dinero;
        }

        Nodo nodo = this.mapa.obtenerNodoEn(fila, columna);
        if (nodo == null) {
            return "Esa celda no es parte del camino.";
        }
        if (nodo == this.mapa.getCabeza() || nodo.siguiente == null) {
            return "No se puede construir en el inicio ni en la meta.";
        }
        if (nodo.planta != null) {
            return "Ya hay una planta en ese nodo.";
        }
        if (nodo.zombie != null) {
            return "Hay un zombie en ese nodo.";
        }

        colocarPlanta(planta, nodo);
        return null;
    }

    public String retirarUltimaApi() {
        if (!this.juegoActivo) {
            return "La partida ya terminó.";
        }
        if (retirarUltimaPlanta() == null) {
            return "No hay defensas para retirar.";
        }
        return null;
    }

    // ==================================================================
    // API WEB: estado completo de la partida (para serializar a JSON)
    // ==================================================================
    public Map<String, Object> estado() {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("tick", this.tickActual);
        e.put("dinero", this.dinero);
        e.put("vidas", this.vidas);
        e.put("vidasIniciales", this.vidasInicial);
        e.put("oleada", this.oleadaActual + 1);
        e.put("totalOleadas", this.totalOleadas);
        e.put("activo", this.juegoActivo);
        e.put("victoria", this.victoria);
        e.put("zombisEnMapa", this.colaActivos.getTamanio());
        e.put("zombisPendientes", this.colaZombies.getTamanio());

        // Mapa: nodos en el orden del camino (cabeza -> meta)
        List<Object> nodos = new ArrayList<>();
        Nodo actual = this.mapa.getCabeza();
        while (actual != null) {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("id", actual.idPosicion);
            n.put("fila", actual.fila);
            n.put("columna", actual.columna);
            n.put("tipo", actual == this.mapa.getCabeza() ? "inicio" : (actual.siguiente == null ? "meta" : "camino"));
            n.put("planta", actual.planta == null ? null : entidadPlanta(actual.planta));
            n.put("zombie", actual.zombie == null ? null : entidadZombie(actual.zombie));
            nodos.add(n);
            actual = actual.siguiente;
        }
        e.put("nodos", nodos);

        // Cola (FIFO): zombies pendientes, del frente al final
        List<Object> cola = new ArrayList<>();
        for (Zombie z : this.colaZombies.comoLista()) {
            cola.add(entidadZombie(z));
        }
        e.put("cola", cola);

        // Pila (LIFO): defensas que siguen en pie, del tope a la base
        List<Object> pila = new ArrayList<>();
        for (Planta p : this.pilaDefensas.comoLista()) {
            if (p.nodo != null && p.nodo.planta == p) {
                Map<String, Object> m = entidadPlanta(p);
                m.put("nodo", p.nodo.idPosicion);
                pila.add(m);
            }
        }
        e.put("pila", pila);
        return e;
    }

    private Map<String, Object> entidadPlanta(Planta p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id);
        m.put("tipo", p.tipo.name());
        m.put("hp", p.hp);
        m.put("hpMax", p.hpMax);
        return m;
    }

    private Map<String, Object> entidadZombie(Zombie z) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", z.id);
        m.put("tipo", z.tipo.name());
        m.put("hp", z.hp);
        m.put("hpMax", z.hpMax);
        m.put("velocidad", z.velocidad);
        m.put("ticks", z.ticksAcumulados);
        return m;
    }

    // Datos fijos del juego (costos, vida, daño...) para las cartas y el Libro
    public static Map<String, Object> catalogo() {
        Map<String, Object> c = new LinkedHashMap<>();
        List<Object> plantas = new ArrayList<>();
        for (Planta p : new Planta[] {Planta.MURO, Planta.TIRADOR, Planta.MINA}) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tipo", p.tipo.name());
            m.put("nombre", p.nombre);
            m.put("hp", p.hpMax);
            m.put("dano", p.danoBase);
            m.put("costo", p.costoEnergia);
            plantas.add(m);
        }
        List<Object> zombies = new ArrayList<>();
        for (Zombie z : new Zombie[] {Zombie.BASICO, Zombie.RAPIDO, Zombie.TANQUE}) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tipo", z.tipo.name());
            m.put("nombre", z.nombre);
            m.put("hp", z.hpMax);
            m.put("dano", z.danoBase);
            m.put("velocidad", z.velocidad);
            m.put("recompensa", z.recompensa);
            zombies.add(m);
        }
        c.put("plantas", plantas);
        c.put("zombies", zombies);
        c.put("alcanceTirador", ALCANCE_TIRADOR);
        c.put("dineroInicial", 800);
        c.put("vidasIniciales", 3);
        c.put("oleadas", OLEADAS_COMPLETO.length);
        c.put("tam", ListaEnlazada.TAM);
        return c;
    }
}
