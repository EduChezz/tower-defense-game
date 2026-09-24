/**
 * Clase Abstracta EntidadJuego - Padre de Planta y Zombie
 */
public abstract class EntidadJuego {
    private static int contador = 0;

    public final int id;        // Identificador unico (lo usa el frontend para animar cada entidad)
    public String nombre;
    public int hp;
    public int hpMax;
    public int danoBase;

    public EntidadJuego(String nombre, int hp, int danoBase) {
        this.id = ++contador;
        this.nombre = nombre;
        this.hp = hp;
        this.hpMax = hp;
        this.danoBase = danoBase;
    }

    public void recibirDano(int cantidad) {
        this.hp -= cantidad;
        if (this.hp < 0) {
            this.hp = 0;
        }
    }

    public boolean estaVivo() {
        return this.hp > 0;
    }

    @Override
    public String toString() {
        return nombre + " (HP: " + hp + "/" + hpMax + ")";
    }
}
