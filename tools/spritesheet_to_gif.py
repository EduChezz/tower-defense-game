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
    --frames         Cantidad de columnas (cuadros por fila) (default: 4).
                      Ignorado si se usa --auto-split.
    --rows           Cantidad de filas de la cuadrícula (default: 1)
    --auto-split     En vez de dividir el ancho en partes iguales, detecta
                      automáticamente los espacios en blanco reales entre
                      personajes y recorta cada uno a su propio contenido
                      (ideal cuando los personajes no están perfectamente
                      centrados en columnas parejas).
    --min-gap        Ancho mínimo (px) de espacio en blanco para considerarlo
                      un separador real entre personajes (default: 20,
                      solo aplica con --auto-split)
    --min-width      Ancho mínimo (px) de un bloque para contar como
                      personaje válido (default: 40, solo con --auto-split)
    --select         Lista de cuadros a usar para el GIF final, en el orden
                      deseado, numerados 1..N en orden de lectura (fila por
                      fila / izquierda a derecha). Ej: "2,3,4,5". Si se
                      omite, se usan todos los cuadros en orden.
    --output         Nombre base de salida, sin extensión (default: nombre del input)
    --out-dir        Carpeta donde se guardan los resultados (default: junto al input)
    --duration-ms    Duración de cada cuadro en milisegundos (default: 200)
    --remove-bg      Si se pasa, convierte a transparente el color de fondo
                      (por defecto detecta el color de la esquina superior
                      izquierda del primer cuadro, con tolerancia)
    --tolerance      Tolerancia de color para --remove-bg / --auto-split
                      (default: 24, 0-255)
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


def es_color_fondo(pixel, color_fondo, tolerancia):
    r, g, b = pixel[:3]
    r0, g0, b0 = color_fondo[:3]
    return abs(r - r0) <= tolerancia and abs(g - g0) <= tolerancia and abs(b - b0) <= tolerancia


def detectar_columnas_con_contenido(banda: Image.Image, color_fondo, tolerancia: int):
    """Para cada columna x, dice si tiene algún pixel que NO sea fondo."""
    ancho, alto = banda.size
    px = banda.load()
    columnas = []
    paso_y = max(1, alto // 300)  # muestreo para no revisar cada fila en imágenes muy altas
    for x in range(ancho):
        tiene_contenido = False
        for y in range(0, alto, paso_y):
            if not es_color_fondo(px[x, y], color_fondo, tolerancia):
                tiene_contenido = True
                break
        columnas.append(tiene_contenido)
    return columnas


def agrupar_en_bloques(columnas, min_gap: int, min_ancho: int):
    """A partir de la máscara de columnas con/sin contenido, arma rangos
    (inicio, fin) por personaje, absorbiendo espacios en blanco angostos
    (sombras, antialiasing) como parte del mismo bloque."""
    n = len(columnas)
    bloques = []
    x = 0
    while x < n:
        if not columnas[x]:
            x += 1
            continue
        inicio = x
        x += 1
        while x < n:
            if columnas[x]:
                x += 1
                continue
            x2 = x
            while x2 < n and not columnas[x2]:
                x2 += 1
            if x2 - x < min_gap and x2 < n:
                x = x2  # espacio angosto: seguimos absorbiendo el mismo bloque
                continue
            break
        fin = x
        if fin - inicio >= min_ancho:
            bloques.append((inicio, fin))
        x = fin
    return bloques


def recortar_al_contenido(imagen: Image.Image, color_fondo, tolerancia: int, margen: int = 6):
    """Recorta el rectángulo vacío sobrante arriba/abajo/izq/der de un bloque."""
    ancho, alto = imagen.size
    px = imagen.load()
    min_x, min_y, max_x, max_y = ancho, alto, 0, 0
    encontrado = False
    paso = max(1, min(ancho, alto) // 300)
    for y in range(0, alto, paso):
        for x in range(0, ancho, paso):
            if not es_color_fondo(px[x, y], color_fondo, tolerancia):
                encontrado = True
                min_x, min_y = min(min_x, x), min(min_y, y)
                max_x, max_y = max(max_x, x), max(max_y, y)
    if not encontrado:
        return imagen
    min_x = max(0, min_x - margen)
    min_y = max(0, min_y - margen)
    max_x = min(ancho, max_x + margen)
    max_y = min(alto, max_y + margen)
    return imagen.crop((min_x, min_y, max_x, max_y))


def auto_dividir_banda(banda: Image.Image, color_fondo, tolerancia: int, min_gap: int, min_ancho: int):
    """Devuelve la lista de cuadros (uno por personaje) detectados en una
    banda horizontal, ya recortados a su propio contenido."""
    columnas = detectar_columnas_con_contenido(banda, color_fondo, tolerancia)
    rangos = agrupar_en_bloques(columnas, min_gap, min_ancho)
    cuadros = []
    for inicio, fin in rangos:
        bloque = banda.crop((inicio, 0, fin, banda.size[1]))
        bloque = recortar_al_contenido(bloque, color_fondo, tolerancia)
        cuadros.append(bloque)
    return cuadros


def unificar_canvas(cuadros, color_fondo):
    """Coloca todos los cuadros en un lienzo del mismo tamaño (el máximo
    entre todos), alineados abajo-centro, para que el GIF no 'salte' de
    tamaño entre frame y frame. El lienzo se rellena con el color de fondo
    (no con transparencia directa) para evitar bordes negros al remover el
    fondo después: quitar_fondo() se encarga de volver todo transparente
    de forma pareja al final."""
    ancho_max = max(c.size[0] for c in cuadros)
    alto_max = max(c.size[1] for c in cuadros)
    resultado = []
    for c in cuadros:
        lienzo = Image.new("RGBA", (ancho_max, alto_max), (*color_fondo[:3], 255))
        x = (ancho_max - c.size[0]) // 2
        y = alto_max - c.size[1]  # alineado abajo (los personajes "pisan" la misma línea base)
        lienzo.paste(c, (x, y))
        resultado.append(lienzo)
    return resultado


def main():
    parser = argparse.ArgumentParser(description="Convierte una sprite sheet en cuadros + GIF animado.")
    parser.add_argument("--input", required=True, help="Ruta a la sprite sheet")
    parser.add_argument("--frames", type=int, default=4, help="Cantidad de columnas (cuadros por fila)")
    parser.add_argument("--rows", type=int, default=1, help="Cantidad de filas de la cuadrícula")
    parser.add_argument("--auto-split", action="store_true",
                         help="Detectar automáticamente los espacios en blanco reales entre personajes")
    parser.add_argument("--min-gap", type=int, default=20, help="Ancho mínimo de separador (solo --auto-split)")
    parser.add_argument("--min-width", type=int, default=40, help="Ancho mínimo de un bloque válido (solo --auto-split)")
    parser.add_argument("--select", default=None,
                         help="Cuadros a usar para el GIF final, ej: '2,3,4,5' (1-indexado, orden de lectura)")
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
    ancho_total, alto_total = sheet.size

    color_fondo = sheet.getpixel((0, 0)) if (args.remove_bg or args.auto_split) else None

    if args.auto_split:
        alto_banda = alto_total // args.rows
        todos_los_cuadros = []
        for f in range(args.rows):
            banda = sheet.crop((0, f * alto_banda, ancho_total, (f + 1) * alto_banda))
            todos_los_cuadros.extend(
                auto_dividir_banda(banda, color_fondo, args.tolerance, args.min_gap, args.min_width)
            )

        if not todos_los_cuadros:
            print("❌ auto-split no detectó ningún personaje. Prueba bajar --min-width o --min-gap.")
            sys.exit(1)

        print(f"(auto-split detectó {len(todos_los_cuadros)} cuadro(s) de contenido)")
        todos_los_cuadros = unificar_canvas(todos_los_cuadros, color_fondo)

        if args.remove_bg:
            todos_los_cuadros = [quitar_fondo(c, color_fondo, args.tolerance) for c in todos_los_cuadros]

        for i, cuadro in enumerate(todos_los_cuadros, start=1):
            ruta_cuadro = os.path.join(carpeta_frames, f"{nombre_base}_frame{i}.png")
            cuadro.save(ruta_cuadro)
            print(f"✓ Cuadro {i}/{len(todos_los_cuadros)} guardado: {ruta_cuadro}")
    else:
        if ancho_total % args.frames != 0:
            print(f"⚠️  Aviso: el ancho total ({ancho_total}px) no es divisible exactamente "
                  f"entre {args.frames} columnas. Los cuadros pueden quedar desalineados.")
        if alto_total % args.rows != 0:
            print(f"⚠️  Aviso: el alto total ({alto_total}px) no es divisible exactamente "
                  f"entre {args.rows} filas. Los cuadros pueden quedar desalineados.")

        ancho_cuadro = ancho_total // args.frames
        alto_cuadro = alto_total // args.rows

        todos_los_cuadros = []
        numero = 1
        for f in range(args.rows):
            for c in range(args.frames):
                caja = (c * ancho_cuadro, f * alto_cuadro, (c + 1) * ancho_cuadro, (f + 1) * alto_cuadro)
                cuadro = sheet.crop(caja)

                if args.remove_bg:
                    cuadro = quitar_fondo(cuadro, color_fondo, args.tolerance)

                ruta_cuadro = os.path.join(carpeta_frames, f"{nombre_base}_frame{numero}.png")
                cuadro.save(ruta_cuadro)
                todos_los_cuadros.append(cuadro)
                print(f"✓ Cuadro {numero}/{args.frames * args.rows} guardado: {ruta_cuadro}")
                numero += 1

    if args.select:
        indices = [int(x.strip()) - 1 for x in args.select.split(",")]
        cuadros_gif = [todos_los_cuadros[i] for i in indices]
        print(f"\n(Usando solo los cuadros {args.select} para el GIF final)")
    else:
        cuadros_gif = todos_los_cuadros

    ruta_gif = os.path.join(carpeta_salida, f"{nombre_base}.gif")
    cuadros_gif[0].save(
        ruta_gif,
        save_all=True,
        append_images=cuadros_gif[1:],
        duration=args.duration_ms,
        loop=0,
        disposal=2,
    )
    print(f"\n✅ GIF animado creado: {ruta_gif}")
    print(f"   Cuadros individuales en: {carpeta_frames}")


if __name__ == "__main__":
    main()
