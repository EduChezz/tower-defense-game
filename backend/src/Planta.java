/**
 * Clase Planta - Defensas del jugador
 * Tipos: MURO, TIRADOR, MINA
 */
public class Planta extends EntidadJuego {
    public enum TipoPlanta {
        MURO,      // Bloquea
        TIRADOR,   // Ataca a distancia
        MINA       // Explota al contacto
    }

    public int costoEnergia;
    public TipoPlanta tipo;
    public Nodo nodo;   // Nodo donde está colocada (null si no está en el mapa)

    // CATÁLOGO ESTÁTICO DE PLANTAS
    public static Planta MURO = new Planta("Muro", 400, 0, 150, TipoPlanta.MURO);
    public static Planta TIRADOR = new Planta("Tirador", 100, 20, 200, TipoPlanta.TIRADOR);
    public static Planta MINA = new Planta("Mina", 1, 999, 250, TipoPlanta.MINA);

    public Planta(String nombre, int hp, int danoBase, int costoEnergia, TipoPlanta tipo) {
        super(nombre, hp, danoBase);
        this.costoEnergia = costoEnergia;
        this.tipo = tipo;
    }

    // Constructor para crear instancias clonadas
    public Planta(Planta plantaMaestra) {
        super(plantaMaestra.nombre, plantaMaestra.hpMax, plantaMaestra.danoBase);
        this.costoEnergia = plantaMaestra.costoEnergia;
        this.tipo = plantaMaestra.tipo;
    }

    @Override
    public String toString() {
        return nombre + " (" + tipo + ") [HP: " + hp + "/" + hpMax + ", Costo: " + costoEnergia + "]";
    }
}
