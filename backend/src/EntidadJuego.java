/**
 * Clase Abstracta EntidadJuego - Padre de Planta y Zombie
 */
public abstract class EntidadJuego {
    public String nombre;
    public int hp;
    public int hpMax;
    public int danoBase;

    public EntidadJuego(String nombre, int hp, int danoBase) {
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
