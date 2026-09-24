/**
 * Clase Zombie - Enemigos del jugador
 * Tipos: BASICO, TANQUE, RAPIDO
 */
public class Zombie extends EntidadJuego {
    public enum TipoZombie {
        BASICO,    // Equilibrado
        TANQUE,    // Mucha vida, lento
        RAPIDO     // Poca vida, rápido
    }

    public int velocidad;          // Ticks necesarios para avanzar de nodo
    public int ticksAcumulados;    // Contador actual
    public Nodo nodoActual;
    public TipoZombie tipo;
    public int recompensa;         // Dinero al ser derrotado

    // CATÁLOGO ESTÁTICO DE ZOMBIES
    public static Zombie BASICO = new Zombie("Basico", 100, 20, 1, TipoZombie.BASICO, 50);
    public static Zombie TANQUE = new Zombie("Tanque", 300, 10, 2, TipoZombie.TANQUE, 100);
    public static Zombie RAPIDO = new Zombie("Rapido", 50, 40, 1, TipoZombie.RAPIDO, 75);

    public Zombie(String nombre, int hp, int danoBase, int velocidad, TipoZombie tipo, int recompensa) {
        super(nombre, hp, danoBase);
        this.velocidad = velocidad;
        this.ticksAcumulados = 0;
        this.nodoActual = null;
        this.tipo = tipo;
        this.recompensa = recompensa;
    }

    // Constructor para crear instancias clonadas
    public Zombie(Zombie zombieMaestro) {
        super(zombieMaestro.nombre, zombieMaestro.hpMax, zombieMaestro.danoBase);
        this.velocidad = zombieMaestro.velocidad;
        this.ticksAcumulados = 0;
        this.nodoActual = null;
        this.tipo = zombieMaestro.tipo;
        this.recompensa = zombieMaestro.recompensa;
    }

    public void incrementarTick() {
        this.ticksAcumulados++;
    }

    public boolean puedeAvanzar() {
        return this.ticksAcumulados >= this.velocidad;
    }

    public void resetearTick() {
        this.ticksAcumulados = 0;
    }

    @Override
    public String toString() {
        return nombre + " (" + tipo + ") [HP: " + hp + "/" + hpMax + ", Vel: " + velocidad + "]";
    }
}
