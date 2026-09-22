/**
 * Clase Pila - Estructura LIFO para las defensas del jugador
 * Solo se puede retirar la última defensa colocada
 */
public class Pila {
    private NodoPila tope;
    private int tamanio;

    private class NodoPila {
        Planta planta;
        NodoPila siguiente;

        NodoPila(Planta planta) {
            this.planta = planta;
            this.siguiente = null;
        }
    }

    public Pila() {
        this.tope = null;
        this.tamanio = 0;
    }

    public void push(Planta planta) {
        NodoPila nuevoNodo = new NodoPila(planta);
        nuevoNodo.siguiente = this.tope;
        this.tope = nuevoNodo;
        this.tamanio++;
    }

    public Planta pop() {
        if (this.tope == null) {
            return null;
        }
        
        Planta planta = this.tope.planta;
        this.tope = this.tope.siguiente;
        this.tamanio--;
        return planta;
    }

    public Planta peek() {
        if (this.tope == null) {
            return null;
        }
        return this.tope.planta;
    }

    public int getTamanio() {
        return this.tamanio;
    }

    public boolean estaVacia() {
        return this.tamanio == 0;
    }

    public void imprimirPila() {
        if (this.estaVacia()) {
            System.out.println("PILA DEFENSAS (LIFO): [Vacía]");
            return;
        }

        System.out.print("PILA DEFENSAS (LIFO) tope → ");
        NodoPila actual = this.tope;
        while (actual != null) {
            String pos = actual.planta.nodo != null ? "@Nodo " + actual.planta.nodo.idPosicion : "";
            System.out.print(actual.planta.nombre + pos + " → ");
            actual = actual.siguiente;
        }
        System.out.println("[BASE] | Total: " + this.tamanio);
    }
}
