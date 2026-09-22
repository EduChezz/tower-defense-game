/**
 * Clase Menu - Interfaz de menú principal
 */
import java.util.Scanner;

public class Menu {
    private Scanner scanner;

    public Menu() {
        this.scanner = new Scanner(System.in);
    }

    public void mostrarMenuPrincipal() {
        boolean continuar = true;
        
        while (continuar) {
            limpiarPantalla();
            mostrarBandera();
            
            System.out.println("\n╔════════════════════════════════════════════════════════════╗");
            System.out.println("║            TOWER DEFENSE - PLANTAS VS ZOMBIES             ║");
            System.out.println("║           Estructura de Datos - Proyecto APE              ║");
            System.out.println("╚════════════════════════════════════════════════════════════╝\n");
            
            System.out.println("1. Iniciar Juego");
            System.out.println("2. Libro (Información de Entidades)");
            System.out.println("3. Demo (Simulación)");
            System.out.println("4. Créditos");
            System.out.println("5. Salir");
            System.out.print("\nSelecciona una opción: ");
            
            int opcion = obtenerEntrada(1, 5);
            
            switch (opcion) {
                case 1:
                    iniciarJuego();
                    break;
                case 2:
                    mostrarLibro();
                    break;
                case 3:
                    mostrarDemo();
                    break;
                case 4:
                    mostrarCreditos();
                    break;
                case 5:
                    System.out.println("\n¡Gracias por jugar! Hasta luego.");
                    continuar = false;
                    break;
            }
        }
        this.scanner.close();
    }

    // ========== INICIAR JUEGO ==========
    private void iniciarJuego() {
        limpiarPantalla();
        System.out.println("\n╔════════════════════════════════════════════════════════════╗");
        System.out.println("║                  INICIANDO JUEGO                           ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");
        
        System.out.println("┌─ INSTRUCCIONES ─────────────────────────────────────────────┐");
        System.out.println("│ 1. Tienes 3 vidas iniciales                                 │");
        System.out.println("│ 2. Comienzas con 800 monedas                                │");
        System.out.println("│ 3. Coloca defensas para detener zombies:                    │");
        System.out.println("│    • Muro: 150 monedas (HP:400, bloquea)                    │");
        System.out.println("│    • Tirador: 200 monedas (HP:100, ataca a distancia)       │");
        System.out.println("│    • Mina: 250 monedas (HP:1, explota al contacto)          │");
        System.out.println("│ 4. Elimina todos los zombies antes de perder las 3 vidas    │");
        System.out.println("│ 5. Hay 8 oleadas de dificultad progresiva                   │");
        System.out.println("│ 6. Ganas dinero por cada zombie derrotado                   │");
        System.out.println("└─────────────────────────────────────────────────────────────┘\n");
        
        System.out.println("¿Estás listo? (Presiona ENTER para comenzar)");
        this.scanner.nextLine();
        
        Juego juego = new Juego(false, this.scanner);
        juego.ejecutar();
        
        System.out.println("\nPresiona ENTER para volver al menú");
        this.scanner.nextLine();
    }

    // ========== MOSTRAR LIBRO ==========
    private void mostrarLibro() {
        limpiarPantalla();
        System.out.println("\n╔════════════════════════════════════════════════════════════╗");
        System.out.println("║                  LIBRO - ENTIDADES DISPONIBLES              ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");
        
        System.out.println("┌─ PLANTAS (DEFENSAS) ────────────────────────────────────────┐");
        System.out.println("│ Nombre   │ HP  │ Tipo   │ Costo │ Característica             │");
        System.out.println("├──────────┼─────┼────────┼───────┼──────────────────────────┤");
        System.out.println("│ MURO     │ 400 │ BLOQUEO│  150  │ Bloquea el camino        │");
        System.out.println("│ TIRADOR  │ 100 │ ATAQUE │  200  │ Ataca a distancia (20)   │");
        System.out.println("│ MINA     │   1 │ EXPLOSIÓN 250  │ Explota: 999 daño        │");
        System.out.println("└──────────┴─────┴────────┴───────┴──────────────────────────┘\n");
        
        System.out.println("┌─ ZOMBIES (ENEMIGOS) ────────────────────────────────────────┐");
        System.out.println("│ Nombre  │ HP  │ Daño │ Velocidad │ Recompensa │ Tipo      │");
        System.out.println("├─────────┼─────┼──────┼───────────┼────────────┼──────────┤");
        System.out.println("│ BASICO  │ 100 │  10  │     1     │     50     │ Balanced │");
        System.out.println("│ TANQUE  │ 300 │   5  │     2     │    100     │ Lento    │");
        System.out.println("│ RAPIDO  │  50 │  20  │     1     │     75     │ Rápido   │");
        System.out.println("└─────────┴─────┴──────┴───────────┴────────────┴──────────┘\n");
        
        System.out.println("Dinero inicial: 800 monedas");
        System.out.println("Vidas iniciales: 3");
        System.out.println("Total de oleadas: 8");
        
        System.out.println("\nPresiona ENTER para volver al menú");
        this.scanner.nextLine();
    }

    // ========== MOSTRAR DEMO ==========
    private void mostrarDemo() {
        limpiarPantalla();
        System.out.println("\n╔════════════════════════════════════════════════════════════╗");
        System.out.println("║                     DEMOSTRACIÓN                           ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");
        
        System.out.println("1. Demo Ganar (5 oleadas, estrategia óptima)");
        System.out.println("2. Demo Perder (5 oleadas, sin defensas)");
        System.out.print("Selecciona: ");
        
        int opcion = obtenerEntrada(1, 2);
        
        if (opcion == 1) {
            ejecutarDemoGanar();
        } else {
            ejecutarDemoPerder();
        }
    }

    private void ejecutarDemoGanar() {
        System.out.println("\n[INICIANDO DEMO - ESCENARIO GANAR]\n");
        Juego demogame = new Juego(true, this.scanner);
        demogame.ejecutarDemo(true);
        
        System.out.println("\nPresiona ENTER para volver al menú");
        this.scanner.nextLine();
    }

    private void ejecutarDemoPerder() {
        System.out.println("\n[INICIANDO DEMO - ESCENARIO PERDER]\n");
        System.out.println("En este escenario, no colocarás defensas...");
        System.out.println("Presiona ENTER para continuar");
        this.scanner.nextLine();
        
        Juego demogame = new Juego(true, this.scanner);
        demogame.ejecutarDemo(false);
        
        System.out.println("\nPresiona ENTER para volver al menú");
        this.scanner.nextLine();
    }

    // ========== MOSTRAR CRÉDITOS ==========
    private void mostrarCreditos() {
        limpiarPantalla();
        System.out.println("\n╔════════════════════════════════════════════════════════════╗");
        System.out.println("║                      CRÉDITOS                              ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");
        
        System.out.println("┌─────────────────────────────────────────────────────────────┐");
        System.out.println("│          Proyecto: TOWER DEFENSE - PLANTAS VS ZOMBIES       │");
        System.out.println("│                                                             │");
        System.out.println("│  Desarrolladores:                                           │");
        System.out.println("│    • Eduardo Sánchez                                        │");
        System.out.println("│    • Xander Real                                            │");
        System.out.println("│    • Elvis Zambrano                                         │");
        System.out.println("│                                                             │");
        System.out.println("│  Universidad: Universidad Técnica de Ambato (UTA)           │");
        System.out.println("│  Carrera: Tecnologías de la Información                     │");
        System.out.println("│  Asignatura: Estructura de Datos                           │");
        System.out.println("│  Profesor: PhD. Félix Fernández Peña                        │");
        System.out.println("│                                                             │");
        System.out.println("│  Estructura de Datos Utilizadas:                            │");
        System.out.println("│    • Lista Enlazada Simple (El Mapa)                        │");
        System.out.println("│    • Cola FIFO (Horda de Zombies)                          │");
        System.out.println("│    • Pila LIFO (Defensas del Jugador)                      │");
        System.out.println("│                                                             │");
        System.out.println("│  Año: 2026                                                  │");
        System.out.println("└─────────────────────────────────────────────────────────────┘\n");
        
        System.out.println("Gracias por jugar. Espero que disfrutes el juego.\n");
        System.out.println("Presiona ENTER para volver al menú");
        this.scanner.nextLine();
    }

    // ========== UTILIDADES ==========
    private void mostrarBandera() {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║                    🧟 🌱 🎮                             ║");
        System.out.println("║          TOWER DEFENSE - ESTRUCTURA DE DATOS              ║");
        System.out.println("║                    🧟 🌱 🎮                             ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
    }

    private void limpiarPantalla() {
        for (int i = 0; i < 50; i++) {
            System.out.println();
        }
    }

    private int obtenerEntrada(int min, int max) {
        try {
            int input = Integer.parseInt(this.scanner.nextLine());
            if (input >= min && input <= max) {
                return input;
            }
            System.out.print("Ingresa un número entre " + min + " y " + max + ": ");
            return obtenerEntrada(min, max);
        } catch (Exception e) {
            System.out.print("Entrada inválida. Intenta de nuevo: ");
            return obtenerEntrada(min, max);
        }
    }
}
