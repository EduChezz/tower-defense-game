/**
 * Clase Nodo - Representa un nodo en la lista enlazada del mapa
 * Cada nodo puede contener una planta (defensa) y un zombie (enemigo)
 */
public class Nodo {
    public int idPosicion;
    public int fila;
    public int columna;
    public Planta planta;
    public Zombie zombie;
    public Nodo siguiente;

    public Nodo(int idPosicion) {
        this.idPosicion = idPosicion;
        this.planta = null;
        this.zombie = null;
        this.siguiente = null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[ ");
        
        if (planta != null) {
            sb.append(planta.nombre.substring(0, 1).toUpperCase());
        } else if (zombie != null) {
            sb.append("Z");
        } else {
            sb.append(".");
        }
        
        sb.append(" ]");
        return sb.toString();
    }
}
