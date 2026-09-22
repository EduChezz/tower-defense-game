/**
 * Clase Cola - Estructura FIFO para los zombies
 * Los zombies entran por el final y se procesan desde el frente
 */
public class Cola {
    private NodoCola frente;
    private NodoCola final_cola;
    private int tamanio;

    private class NodoCola {
        Zombie zombie;
        NodoCola siguiente;

        NodoCola(Zombie zombie) {
            this.zombie = zombie;
            this.siguiente = null;
        }
    }

    public Cola() {
        this.frente = null;
        this.final_cola = null;
        this.tamanio = 0;
    }

    public void encolar(Zombie zombie) {
        NodoCola nuevoNodo = new NodoCola(zombie);
        
        if (this.frente == null) {
            this.frente = nuevoNodo;
            this.final_cola = nuevoNodo;
        } else {
            this.final_cola.siguiente = nuevoNodo;
            this.final_cola = nuevoNodo;
        }
        this.tamanio++;
    }

    public Zombie desencolar() {
        if (this.frente == null) {
            return null;
        }
        
        Zombie zombie = this.frente.zombie;
        this.frente = this.frente.siguiente;
        
        if (this.frente == null) {
            this.final_cola = null;
        }
        
        this.tamanio--;
        return zombie;
    }

    public Zombie verFrente() {
        if (this.frente == null) {
            return null;
        }
        return this.frente.zombie;
    }

    public int getTamanio() {
        return this.tamanio;
    }

    public boolean estaVacia() {
        return this.tamanio == 0;
    }

    public void imprimirCola(String titulo) {
        if (this.estaVacia()) {
            System.out.println(titulo + ": [Vacía]");
            return;
        }

        System.out.print(titulo + ": ");
        NodoCola actual = this.frente;
        while (actual != null) {
            System.out.print(actual.zombie.nombre + "(HP:" + actual.zombie.hp + ") → ");
            actual = actual.siguiente;
        }
        System.out.println("[FIN]");
    }
}
