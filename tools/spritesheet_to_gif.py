#!/usr/bin/env python3
"""
spritesheet_to_gif.py
----------------------
Toma una sprite sheet (una imagen con N cuadros de animación en una sola
fila horizontal, todos del mismo tamaño) y genera:

  1. Los N cuadros individuales como PNG (carpeta frames/).
  2. Un GIF animado en loop infinito, listo para usar en el juego.

Uso:
    python tools/spritesheet_to_gif.py --input ruta/a/sheet.png --frames 4 --output muro_idle

Opciones:
    --input          Ruta a la imagen de la sprite sheet (requerido)
    --frames         Cantidad de cuadros en la fila (default: 4)
    --output         Nombre base de salida, sin extensión (default: nombre del input)
    --out-dir        Carpeta donde se guardan los resultados (default: junto al input)
    --duration-ms    Duración de cada cuadro en milisegundos (default: 200)
    --remove-bg      Si se pasa, convierte a transparente el color de fondo
                      (por defecto detecta el color de la esquina superior
                      izquierda del primer cuadro, con tolerancia)
    --tolerance      Tolerancia de color para --remove-bg (default: 24, 0-255)
"""

import argparse
import io
import os
import sys

from PIL import Image

# En Windows la consola suele usar cp1252, que no soporta los símbolos que
# usamos en los mensajes (✓, ⚠️, ✅, ❌). Forzamos UTF-8 para evitar
# UnicodeEncodeError sin tener que renunciar a los símbolos.
if sys.stdout.encoding is None or sys.stdout.encoding.lower() != "utf-8":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")


def quitar_fondo(imagen: Image.Image, color_fondo, tolerancia: int) -> Image.Image:
    """Convierte a transparente cualquier pixel parecido a color_fondo."""
    imagen = imagen.convert("RGBA")
    datos = imagen.getdata()
    nuevos_datos = []
    r0, g0, b0 = color_fondo[:3]

    for r, g, b, a in datos:
        if abs(r - r0) <= tolerancia and abs(g - g0) <= tolerancia and abs(b - b0) <= tolerancia:
            nuevos_datos.append((r, g, b, 0))
        else:
            nuevos_datos.append((r, g, b, a))

    imagen.putdata(nuevos_datos)
    return imagen


def main():
    parser = argparse.ArgumentParser(description="Convierte una sprite sheet en cuadros + GIF animado.")
    parser.add_argument("--input", required=True, help="Ruta a la sprite sheet")
    parser.add_argument("--frames", type=int, default=4, help="Cantidad de cuadros en la fila")
    parser.add_argument("--output", default=None, help="Nombre base de salida (sin extensión)")
    parser.add_argument("--out-dir", default=None, help="Carpeta de salida")
    parser.add_argument("--duration-ms", type=int, default=200, help="Duración de cada cuadro (ms)")
    parser.add_argument("--remove-bg", action="store_true", help="Quitar el color de fondo (transparencia)")
    parser.add_argument("--tolerance", type=int, default=24, help="Tolerancia de color para --remove-bg")
    args = parser.parse_args()

    if not os.path.isfile(args.input):
        print(f"❌ No se encontró el archivo: {args.input}")
        sys.exit(1)

    nombre_base = args.output or os.path.splitext(os.path.basename(args.input))[0]
    carpeta_salida = args.out_dir or os.path.dirname(os.path.abspath(args.input))
    carpeta_frames = os.path.join(carpeta_salida, "frames")
    os.makedirs(carpeta_frames, exist_ok=True)

    sheet = Image.open(args.input).convert("RGBA")
    ancho_total, alto = sheet.size

    if ancho_total % args.frames != 0:
        print(f"⚠️  Aviso: el ancho total ({ancho_total}px) no es divisible exactamente "
              f"entre {args.frames} cuadros. Los cuadros pueden quedar desalineados.")

    ancho_cuadro = ancho_total // args.frames

    color_fondo = sheet.getpixel((0, 0)) if args.remove_bg else None

    cuadros = []
    for i in range(args.frames):
        caja = (i * ancho_cuadro, 0, (i + 1) * ancho_cuadro, alto)
        cuadro = sheet.crop(caja)

        if args.remove_bg:
            cuadro = quitar_fondo(cuadro, color_fondo, args.tolerance)

        ruta_cuadro = os.path.join(carpeta_frames, f"{nombre_base}_frame{i + 1}.png")
        cuadro.save(ruta_cuadro)
        cuadros.append(cuadro)
        print(f"✓ Cuadro {i + 1}/{args.frames} guardado: {ruta_cuadro}")

    ruta_gif = os.path.join(carpeta_salida, f"{nombre_base}.gif")
    cuadros[0].save(
        ruta_gif,
        save_all=True,
        append_images=cuadros[1:],
        duration=args.duration_ms,
        loop=0,
        disposal=2,
    )
    print(f"\n✅ GIF animado creado: {ruta_gif}")
    print(f"   Cuadros individuales en: {carpeta_frames}")


if __name__ == "__main__":
    main()
