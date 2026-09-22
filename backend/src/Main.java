/**
 * Clase Main - Punto de entrada del programa
 * 
 * TOWER DEFENSE - PLANTAS VS ZOMBIES
 * Proyecto de Estructura de Datos
 * 
 * Integrantes: Eduardo Sánchez, Xander Real, Elvis Zambrano
 * Universidad Técnica de Ambato (UTA)
 * Asignatura: Estructura de Datos
 * Profesor: PhD. Félix Fernández Peña
 * 
 * ESTRUCTURAS DE DATOS UTILIZADAS:
 * - Lista Enlazada Simple: Representa el mapa (25 nodos en una matriz 7x7)
 * - Cola (FIFO): Gestiona la horda de zombies
 * - Pila (LIFO): Gestiona las defensas del jugador
 * 
 * ENTIDADES:
 * - Plantas: Muro (150), Tirador (200), Mina (250)
 * - Zombies: Básico (50), Tanque (100), Rápido (75)
 * 
 * MECÁNICAS:
 * - GameLoop con 3 fases: Ataque plantas, Acción zombies, Limpieza
 * - Dinero inicial: 800 monedas
 * - Vidas iniciales: 3
 * - Oleadas totales: 8
 * - Demo: 5 oleadas
 */

public class Main {
    public static void main(String[] args) {
        Menu menu = new Menu();
        menu.mostrarMenuPrincipal();
    }
}
