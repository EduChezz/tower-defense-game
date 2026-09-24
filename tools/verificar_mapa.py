#!/usr/bin/env python3
"""
verificar_mapa.py
-----------------
Comprueba que una imagen de mapa calza con la cuadrícula de 7x7 celdas y con el camino en
serpiente del motor (ListaEnlazada.java): las 25 celdas del camino deben ser de tierra y las
otras 24 no. Sirve para validar un mapa nuevo antes de usarlo en el juego.

Uso:
    python tools/verificar_mapa.py --imagen mapa_decorado.jpg                      # usa el campo por defecto
    python tools/verificar_mapa.py --imagen otro.jpg --campo 160,140,1820          # x,y,lado del campo jugable
    python tools/verificar_mapa.py --imagen otro.jpg --campo 160,140,1820 --guardar cuadricula.png

--campo es el cuadrado jugable (sin el marco de madera) dentro de la imagen original: x, y de la
esquina superior izquierda y el lado en píxeles. Debe ser el mismo valor de MAPAS en procesar_assets.py.
Termina con código 1 si alguna celda no coincide o si las franjas de tierra no quedan centradas.
"""

import argparse
import sys

from PIL import Image, ImageDraw

TAM = 7


def camino_del_motor():
    """Mismo trazado que ListaEnlazada.construirCamino(): filas pares recorridas, impares con un solo nodo."""
    celdas = set()
    for fila in range(TAM - 1):
        if fila % 2 == 0:
            celdas |= {(fila, c) for c in range(TAM)}
        else:
            celdas.add((fila, TAM - 1 if fila % 4 == 1 else 0))
    celdas.add((TAM - 1, TAM - 1))
    return celdas


def es_tierra(p):
    r, g, b = p
    return r > 105 and r > g + 22 and g >= b - 5


def main():
    ap = argparse.ArgumentParser(description="Verifica que un mapa calza con la cuadrícula 7x7 del motor.")
    ap.add_argument("--imagen", required=True)
    ap.add_argument("--campo", default="160,140,1820", help="x,y,lado del cuadrado jugable")
    ap.add_argument("--guardar", help="guarda una copia con la cuadrícula dibujada")
    args = ap.parse_args()

    x0, y0, lado = (int(v) for v in args.campo.split(","))
    im = Image.open(args.imagen).convert("RGB")
    if x0 + lado > im.width or y0 + lado > im.height:
        print(f"El campo {args.campo} se sale de la imagen ({im.width}x{im.height}).")
        return 1
    px = im.load()
    c = lado / TAM
    camino = camino_del_motor()

    bien = 0
    print("Tierra en el núcleo de cada celda (* = camino en el motor):")
    for f in range(TAM):
        fila = []
        for col in range(TAM):
            xa, xb = int(x0 + col * c + c * 0.2), int(x0 + (col + 1) * c - c * 0.2)
            ya, yb = int(y0 + f * c + c * 0.2), int(y0 + (f + 1) * c - c * 0.2)
            n = t = 0
            for y in range(ya, yb, 4):
                for x in range(xa, xb, 4):
                    n += 1
                    t += es_tierra(px[x, y])
            frac = t / n if n else 0
            esperado = (f, col) in camino
            if (frac > 0.5) == esperado:
                bien += 1
            fila.append(f"{frac:.2f}{'*' if esperado else ' '}{'' if (frac > 0.5) == esperado else '!'}")
        print("  fila", f, "  ".join(fila))

    print(f"\n{bien}/{TAM * TAM} celdas coinciden con el camino del motor.")

    # Comprobación fina: cada franja de tierra debe quedar CENTRADA en su fila/columna de la cuadrícula.
    # (Una cuadrícula corrida unas decenas de píxeles todavía "acierta" celda por celda, pero los
    # personajes quedarían pisando el borde de la tierra.)
    def centro_franja(perfil, inicio):
        """Centro (px) del tramo donde el perfil de tierra supera 0.5; None si no hay tierra."""
        dentro = [i for i, v in enumerate(perfil) if v >= 0.5]
        return None if not dentro else inicio + (dentro[0] + dentro[-1]) / 2

    def perfil_vertical(f):   # fila de camino f: tierra por cada y, sobre las celdas centrales 1..5
        xs = range(int(x0 + 1.2 * c), int(x0 + 5.8 * c), 6)
        ys = range(max(y0, int(y0 + (f - 0.5) * c)), min(y0 + lado, int(y0 + (f + 1.5) * c)))  # solo dentro del campo (el marco es marrón)
        return [sum(es_tierra(px[x, y]) for x in xs) / len(xs) for y in ys], ys[0]

    def perfil_horizontal(f, col):   # columna de camino en la fila f: tierra por cada x
        ys = range(int(y0 + (f + 0.2) * c), int(y0 + (f + 0.8) * c), 6)
        xs = range(max(x0, int(x0 + (col - 0.5) * c)), min(x0 + lado, int(x0 + (col + 1.5) * c)))
        return [sum(es_tierra(px[x, y]) for y in ys) / len(ys) for x in xs], xs[0]

    medidas = []
    for f in (0, 2, 4):
        perfil, ini = perfil_vertical(f)
        ctr = centro_franja(perfil, ini)
        medidas.append((f"fila {f}", None if ctr is None else (ctr - (y0 + (f + 0.5) * c)) / c))
    for f, col in ((1, TAM - 1), (3, 0)):
        perfil, ini = perfil_horizontal(f, col)
        ctr = centro_franja(perfil, ini)
        medidas.append((f"columna {col}", None if ctr is None else (ctr - (x0 + (col + 0.5) * c)) / c))

    print("Centrado de las franjas de tierra respecto a la cuadrícula (en celdas; 0 = perfecto):")
    peor = 0.0
    for nombre, e in medidas:
        print(f"  {nombre}: {'sin tierra detectada' if e is None else f'{e:+.3f}'}")
        peor = max(peor, 1.0 if e is None else abs(e))
    centrado_ok = peor <= 0.06
    print(f"  desvío máximo {peor:.3f} celdas -> {'OK' if centrado_ok else 'FALLA (tolerancia 0.06)'}")
    if not centrado_ok:
        bien = -1
    if args.guardar:
        d = ImageDraw.Draw(im)
        for k in range(TAM + 1):
            d.line([(x0 + k * c, y0), (x0 + k * c, y0 + lado)], fill=(255, 255, 0), width=3)
            d.line([(x0, y0 + k * c), (x0 + lado, y0 + k * c)], fill=(255, 255, 0), width=3)
        im.save(args.guardar)
        print("cuadrícula guardada en", args.guardar)
    return 0 if bien == TAM * TAM else 1


if __name__ == "__main__":
    sys.exit(main())
