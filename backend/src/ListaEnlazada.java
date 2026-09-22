/**
 * Clase ListaEnlazada - Representa el mapa del juego
 * Lista enlazada simple cuyos nodos siguen un camino en serpiente
 * dentro de una matriz de TAM x TAM celdas.
 */
public class ListaEnlazada {
    public static final int TAM = 7;

    private Nodo cabeza;
    private int tamanio;

    public ListaEnlazada() {
        this.cabeza = null;
        this.tamanio = 0;
        construirCamino();
    }

    // Filas pares: recorren la fila (derecha, izquierda, derecha).
    // Filas impares: un solo nodo que baja (col 6, col 0, col 6).
    // Fila final: solo la meta en la ultima columna.
    private void construirCamino() {
        int id = 0;
        for (int fila = 0; fila < TAM - 1; fila++) {
            if (fila % 2 == 0) {
                boolean haciaDerecha = fila % 4 == 0;
                for (int i = 0; i < TAM; i++) {
                    int col = haciaDerecha ? i : TAM - 1 - i;
                    agregarFinal(nuevoNodo(id++, fila, col));
                }
            } else {
                int col = (fila % 4 == 1) ? TAM - 1 : 0;
                agregarFinal(nuevoNodo(id++, fila, col));
            }
        }
        agregarFinal(nuevoNodo(id, TAM - 1, TAM - 1));
    }

    private Nodo nuevoNodo(int id, int fila, int col) {
        Nodo nodo = new Nodo(id);
        nodo.fila = fila;
        nodo.columna = col;
        return nodo;
    }

    public void agregarFinal(Nodo nuevoNodo) {
        if (this.cabeza == null) {
            this.cabeza = nuevoNodo;
        } else {
            Nodo actual = this.cabeza;
            while (actual.siguiente != null) {
                actual = actual.siguiente;
            }
            actual.siguiente = nuevoNodo;
        }
        this.tamanio++;
    }

    public Nodo obtenerNodo(int posicion) {
        if (posicion < 0 || posicion >= this.tamanio) {
            return null;
        }

        Nodo actual = this.cabeza;
        for (int i = 0; i < posicion; i++) {
            actual = actual.siguiente;
        }
        return actual;
    }

    // Devuelve el nodo del camino en (fila, columna), o null si esa celda no es camino
    public Nodo obtenerNodoEn(int fila, int columna) {
        Nodo actual = this.cabeza;
        while (actual != null) {
            if (actual.fila == fila && actual.columna == columna) {
                return actual;
            }
            actual = actual.siguiente;
        }
        return null;
    }

    public Nodo getCabeza() {
        return this.cabeza;
    }

    public int getTamanio() {
        return this.tamanio;
    }

    // ========== DIBUJO DE LA MATRIZ ==========
    public void imprimirMapa() {
        StringBuilder sep = new StringBuilder("   +");
        StringBuilder cabecera = new StringBuilder("   ");
        for (int c = 0; c < TAM; c++) {
            sep.append("------+");
            cabecera.append("  ").append(c).append("    ");
        }

        System.out.println(cabecera);
        System.out.println(sep);
        for (int f = 0; f < TAM; f++) {
            StringBuilder linea = new StringBuilder(" " + f + " |");
            for (int c = 0; c < TAM; c++) {
                linea.append("[").append(contenidoCelda(obtenerNodoEn(f, c))).append("]|");
            }
            System.out.println(linea + "  " + flechaFila(f));
            System.out.println(sep);
        }
    }

    // Muestra la lista como cadena de nodos enlazados: cabeza → [id:contenido] → ... → null
    public void imprimirEnlaces() {
        System.out.println("LISTA ENLAZADA (mapa, " + this.tamanio + " nodos):");
        System.out.print("  cabeza → ");
        Nodo actual = this.cabeza;
        int enLinea = 0;
        while (actual != null) {
            String contenido = "-";
            if (actual.planta != null) {
                contenido = actual.planta.nombre;
            } else if (actual.zombie != null) {
                contenido = "Z:" + actual.zombie.nombre;
            }
            System.out.print("[" + actual.idPosicion + ":" + contenido + "] → ");
            actual = actual.siguiente;
            if (++enLinea % 8 == 0 && actual != null) {
                System.out.print("\n           ");
            }
        }
        System.out.println("null");
    }

    private String flechaFila(int fila) {
        if (fila == TAM - 1) return "(Meta)";
        if (fila % 2 == 1) return "v";
        return (fila % 4 == 0) ? "->" : "<-";
    }

    // 4 caracteres: entidad si hay, si no el simbolo del camino; "." si no es camino
    private String contenidoCelda(Nodo nodo) {
        if (nodo == null) {
            return centrar(".");
        }
        if (nodo.planta != null) {
            return centrar(nodo.planta.nombre.substring(0, Math.min(4, nodo.planta.nombre.length())));
        }
        if (nodo.zombie != null) {
            return centrar("Z" + nodo.zombie.nombre.charAt(0));
        }
        if (nodo == this.cabeza) {
            return centrar("S");
        }
        if (nodo.siguiente == null) {
            return centrar("E");
        }
        return centrar(simboloCamino(nodo));
    }

    // Segun por donde entra y sale el camino: = || o esquinas
    private String simboloCamino(Nodo nodo) {
        Nodo previo = obtenerNodo(nodo.idPosicion - 1);
        Nodo sig = nodo.siguiente;

        boolean izq = conecta(nodo, previo, 0, -1) || conecta(nodo, sig, 0, -1);
        boolean der = conecta(nodo, previo, 0, 1) || conecta(nodo, sig, 0, 1);
        boolean arr = conecta(nodo, previo, -1, 0) || conecta(nodo, sig, -1, 0);
        boolean aba = conecta(nodo, previo, 1, 0) || conecta(nodo, sig, 1, 0);

        if (izq && der) return "=";
        if (arr && aba) return "||";
        if (izq && aba) return "╗"; // ╗
        if (der && aba) return "╔"; // ╔
        if (izq && arr) return "╝"; // ╝
        return "╚";                 // ╚
    }

    private boolean conecta(Nodo a, Nodo b, int df, int dc) {
        return b != null && b.fila == a.fila + df && b.columna == a.columna + dc;
    }

    private String centrar(String texto) {
        int falta = 4 - texto.length();
        int izq = falta / 2;
        int der = falta - izq;
        return " ".repeat(izq) + texto + " ".repeat(der);
    }
}
