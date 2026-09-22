/**
 * Clase Juego - Motor del juego
 * Implementa el GameLoop con 3 fases: Ataque plantas, Acción zombies, Limpieza
 *
 * - Cola de pendientes: zombies que aún no entran al mapa (entra uno por tick al nodo 0)
 * - Cola de activos: zombies en el mapa; el orden FIFO equivale al orden de avance
 * - Lista enlazada: el mapa; cada nodo guarda su planta y su zombie
 * - Pila: defensas en orden de colocación (permite retirar la última)
 */
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

    // Modo demo automático
    private boolean automatico;
    private boolean demoGanar;
    private int pausaMs = 300;

    // Cantidad de zombies por oleada (DEMO: 5 oleadas, COMPLETO: 8 oleadas)
    private static final int[] OLEADAS_DEMO = {3, 3, 4, 4, 5};
    private static final int[] OLEADAS_COMPLETO = {3, 3, 4, 4, 5, 6, 7, 8};
    private int[] cantidades;

    // Estrategia de la demo ganadora: {tipo (1 muro, 2 tirador, 3 mina), id de nodo}
    private static final int[][] ESTRATEGIA_DEMO = {
        {1, 4}, {2, 6}, {2, 7}, {3, 2}, {2, 8}, {1, 3}, {2, 9}
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

    public void setPausaMs(int pausaMs) {
        this.pausaMs = pausaMs;
    }

    public boolean gano() {
        return this.victoria;
    }

    // ========== FASE 1: ATAQUE DE PLANTAS ==========
    private void faseAtaquePlantas() {
        Nodo actual = this.mapa.getCabeza();

        while (actual != null) {
            if (actual.planta != null && actual.planta.estaVivo()) {
                if (actual.planta.tipo == Planta.TipoPlanta.TIRADOR) {
                    // Los zombies vienen desde el nodo 0: dispara al más cercano que se acerca
                    Nodo nodoObjetivo = null;
                    Nodo buscador = this.mapa.getCabeza();
                    while (buscador != null && buscador != actual) {
                        if (buscador.zombie != null && buscador.zombie.estaVivo()) {
                            nodoObjetivo = buscador;
                        }
                        buscador = buscador.siguiente;
                    }
                    if (nodoObjetivo != null) {
                        int danioAplicado = actual.planta.danoBase;
                        nodoObjetivo.zombie.recibirDano(danioAplicado);
                        System.out.println("  [EVENTO] Tirador en Nodo " + actual.idPosicion +
                                         " inflige " + danioAplicado + " daño a " + nodoObjetivo.zombie.nombre +
                                         " en Nodo " + nodoObjetivo.idPosicion);
                    }
                } else if (actual.planta.tipo == Planta.TipoPlanta.MINA) {
                    // Si hay zombie en el mismo nodo, explota
                    if (actual.zombie != null && actual.zombie.estaVivo()) {
                        int danioAplicado = actual.planta.danoBase;
                        actual.zombie.recibirDano(danioAplicado);
                        actual.planta.recibirDano(999);
                        System.out.println("  [EVENTO] Mina en Nodo " + actual.idPosicion +
                                         " explota e inflige " + danioAplicado + " daño a " + actual.zombie.nombre);
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
                System.out.println("  [EVENTO] " + zombie.nombre + " ha sido derrotado. Ganas " +
                                 zombie.recompensa + " monedas.");
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
                    System.out.println("  [EVENTO] " + zombie.nombre + " en Nodo " + nodo.idPosicion +
                                     " ataca a " + siguiente.planta.nombre + " (-" + zombie.danoBase + " HP)");
                } else if (siguiente.siguiente == null) {
                    // Llegó a la meta: sale del mapa y quita una vida
                    nodo.zombie = null;
                    this.vidas--;
                    System.out.println("  [EVENTO] " + zombie.nombre + " ha alcanzado la META. Pierdes 1 vida.");
                    continue;
                } else if (siguiente.zombie == null) {
                    nodo.zombie = null;
                    siguiente.zombie = zombie;
                    zombie.nodoActual = siguiente;
                    zombie.resetearTick();
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
            System.out.println("  [EVENTO] " + entrante.nombre + " entra al mapa.");
        }
    }

    // ========== FASE 3: LIMPIEZA Y RECOLECCIÓN DE BASURA ==========
    private void faseLimpieza() {
        Nodo actual = this.mapa.getCabeza();
        while (actual != null) {
            if (actual.planta != null && !actual.planta.estaVivo()) {
                System.out.println("  [LIMPIEZA] " + actual.planta.nombre + " en Nodo " +
                                 actual.idPosicion + " ha sido destruida.");
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

    // ========== MÉTODO PRINCIPAL: JUEGO ==========
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
            System.out.println("\n[EJECUTANDO TICK " + this.tickActual + "]");
            faseAtaquePlantas();
            faseAccionZombies();
            faseLimpieza();

            this.tickActual++;

            // Verificar si oleada terminó
            if (oleadaTerminada() && this.oleadaActual < this.totalOleadas - 1 && this.vidas > 0) {
                this.oleadaActual++;
                generarOleada(this.oleadaActual);
                System.out.println("\n>>> NUEVA OLEADA: " + (this.oleadaActual + 1) + " / " +
                                 this.totalOleadas);
                System.out.println(">>> Se han generado " + this.cantidades[this.oleadaActual] +
                                 " zombies");
            }

            // Verificar condiciones de fin
            if (this.vidas <= 0) {
                this.juegoActivo = false;
                System.out.println("\n╔════════════════════════════════════════════════════════════╗");
                System.out.println("║                    ¡GAME OVER!                            ║");
                System.out.println("║            Has perdido todas tus vidas.                   ║");
                System.out.println("╚════════════════════════════════════════════════════════════╝");
            } else if (oleadaTerminada() && this.oleadaActual >= this.totalOleadas - 1) {
                this.juegoActivo = false;
                this.victoria = true;
                System.out.println("\n╔════════════════════════════════════════════════════════════╗");
                System.out.println("║                    ¡VICTORIA!                             ║");
                System.out.println("║          Has derrotado todas las oleadas.                 ║");
                System.out.println("║              Dinero final: " + this.dinero);
                System.out.println("╚════════════════════════════════════════════════════════════╝");
            }
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
        System.out.println("✓ " + planta.nombre + " colocado en Nodo " + nodo.idPosicion +
                         " (fila " + nodo.fila + ", col " + nodo.columna + "). Dinero restante: " + this.dinero);
    }

    private void colocarDefensa() {
        System.out.println("\n[COLOCAR DEFENSA]");
        System.out.println("1. Muro (Costo: 150, HP: 400)");
        System.out.println("2. Tirador (Costo: 200, HP: 100, Daño: 20)");
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

    // Saca de la pila la última defensa que siga en pie y devuelve la mitad de su costo
    private void retirarUltimaDefensa() {
        Planta planta = this.pilaDefensas.pop();
        while (planta != null && (planta.nodo == null || planta.nodo.planta != planta)) {
            planta = this.pilaDefensas.pop(); // ya fue destruida o retirada
        }

        if (planta == null) {
            System.out.println("❌ No hay defensas para retirar.");
            return;
        }

        planta.nodo.planta = null;
        int reembolso = planta.costoEnergia / 2;
        this.dinero += reembolso;
        System.out.println("✓ " + planta.nombre + " retirado del Nodo " + planta.nodo.idPosicion +
                         ". Recuperas " + reembolso + " monedas.");
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
}
