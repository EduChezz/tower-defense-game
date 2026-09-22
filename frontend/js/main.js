// Tower Defense - Plantas vs Zombies - Fase 2 (Interfaz)
//
// Este archivo es el punto de partida del frontend. Por ahora dibuja
// el tablero 7x7 de forma estática en el navegador.
//
// Próximo paso: conectar con el backend (Java) mediante una API REST
// que exponga el estado del juego (Juego.java) y reciba acciones del
// jugador (colocar/retirar defensas), en lugar de leerlo desde consola.

const TAM = 7;

function crearTablero() {
  const tablero = document.getElementById("tablero");
  tablero.innerHTML = "";

  for (let fila = 0; fila < TAM; fila++) {
    for (let col = 0; col < TAM; col++) {
      const celda = document.createElement("div");
      celda.className = "celda";
      celda.dataset.fila = fila;
      celda.dataset.columna = col;
      tablero.appendChild(celda);
    }
  }
}

document.addEventListener("DOMContentLoaded", crearTablero);
