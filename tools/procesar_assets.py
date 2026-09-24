#!/usr/bin/env python3
"""
procesar_assets.py
------------------
Toma los GIF / videos / fondos / música "en crudo" (tal como salen de las IA)
y deja los assets listos para el juego dentro de frontend/assets/.

Qué hace:
  GIF de personajes y efectos
    - quita marcas de agua de la IA ("Ai" y la estrella)
    - quita la elipse morada de sombra bajo los pies (el juego pone su propia sombra por CSS)
    - recorta al contenido y unifica el lienzo por personaje (todas sus animaciones
      comparten tamaño y punto de apoyo -> al cambiar de animación no "salta")
    - loops: busca el mejor punto de empalme y recorta al tramo útil
    - muertes / explosiones: recorta al tramo útil, acelera y termina en cuadro vacío;
      se reproducen UNA sola vez
    - imágenes estáticas guardadas como GIF -> PNG
  Videos: se les quita el audio y se comprimen
  Fondos: se reducen a Full HD comprimido
  Música: se normaliza el volumen, se arma un loop sin salto y se pasa a .ogg
  Además escribe frontend/assets/manifest.json con tamaño, cuadros y duración de cada animación.

Requisitos:  pip install Pillow imageio-ffmpeg

Uso:
    python tools/procesar_assets.py --src "C:/ruta/carpeta_cruda" --dest frontend/assets
    python tools/procesar_assets.py --src ... --dest ... --solo gifs      (gifs|videos|fondos|musica)

Si reemplazas un GIF (por ejemplo la Mina) en la carpeta cruda con el MISMO nombre,
basta con volver a ejecutar este script.
"""

import argparse
import io
import json
import math
import os
import re
import statistics
import subprocess
import sys

from PIL import Image, ImageChops, ImageSequence, ImageStat

if sys.stdout.encoding is None or sys.stdout.encoding.lower() != "utf-8":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

# --------------------------------------------------------------------------
# CONFIGURACIÓN
# --------------------------------------------------------------------------
# Marcas de agua conocidas (x0, y0, x1, y1) en el cuadro original de 426x240
MARCA_AI = ("comp", (0, 0, 60, 50))            # texto "Ai" arriba a la izquierda
# estrella ✦ abajo a la derecha: primero como mancha suelta y luego, por si se pintó
# encima del personaje, por su color lavanda (dentro de una caja apretada)
MARCA_ESTRELLA = [("comp", (370, 184, 416, 230)), ("lavanda", (378, 190, 402, 214))]

PLANTAS = "images/characters/plants"
ZOMBIES = "images/characters/zombies"
EFECTOS = "images/effects"
UI = "images/ui"
ESCENARIOS = "images/scenarios"

# modos:  loop        -> busca el mejor empalme (lmin..lmax cuadros)
#         loop_todo   -> deja todos los cuadros y en loop
#         unavez      -> deja todos los cuadros, se reproduce una vez y termina vacío
#         unavez_auto -> detecta el tramo útil (por cobertura), acelera y termina vacío
# alinear: None = mismo encuadre que el primer item del grupo; "w"/"h" = iguala ancho/alto
#          del primer cuadro con el del primer item (para clips con otra escala)
GRUPOS = [
    dict(nombre="muro", carpeta=PLANTAS, escala=0.8, items=[
        dict(src="muro.gif", out="muro_reposo", modo="loop", lmin=24, lmax=60, marca=[MARCA_AI], icono="muro"),
        dict(src="muro_destruido.gif", out="muro_destruido", modo="unavez", alinear="w"),
    ]),
    dict(nombre="tirador", carpeta=PLANTAS, escala=0.8, items=[
        dict(src="tirador_natural.gif", out="tirador_reposo", modo="loop", lmin=24, lmax=60, sombra=True, icono="tirador"),
        dict(src="tirador_disparo.gif", out="tirador_disparo", modo="loop", lmin=20, lmax=60, sombra=True, marca=MARCA_ESTRELLA),
        dict(src="tirador_destruido.gif", out="tirador_destruido", modo="unavez", alinear="h"),
    ]),
    dict(nombre="mina", carpeta=PLANTAS, escala=0.8, items=[
        dict(src="mina_normal.gif", out="mina_reposo", modo="loop", lmin=24, lmax=60, marca=MARCA_ESTRELLA, icono="mina"),
        dict(src="mina_explota.gif", out="mina_explosion", modo="unavez_auto", lead=6, objetivo=18, marca=MARCA_ESTRELLA, alinear="h"),
    ]),
    dict(nombre="basico", carpeta=ZOMBIES, escala=0.8, items=[
        dict(src="basico_caminado.gif", out="basico_caminando", modo="loop", lmin=8, lmax=40, sombra=True, icono="basico"),
        dict(src="basico_comiendo.gif", out="basico_comiendo", modo="loop", lmin=30, lmax=70, sombra=True),
        dict(src="basico_muerte.gif", out="basico_muerte", modo="unavez_auto", desde=16, objetivo=20, sombra=True),
    ]),
    dict(nombre="rapido", carpeta=ZOMBIES, escala=0.8, items=[
        dict(src="rapido_corriendo.gif", out="rapido_corriendo", modo="loop", lmin=8, lmax=40, sombra=True, icono="rapido"),
        dict(src="rapido_comiendo.gif", out="rapido_comiendo", modo="loop", lmin=30, lmax=70, sombra=True),
        dict(src="rapido_muerte.gif", out="rapido_muerte", modo="unavez_auto", lead=3, objetivo=18, sombra=True),
    ]),
    dict(nombre="tanque", carpeta=ZOMBIES, escala=0.8, items=[
        dict(src="tanque_caminando.gif", out="tanque_caminando", modo="loop", lmin=8, lmax=40, sombra=True, icono="tanque"),
        dict(src="tanque_comiendo.gif", out="tanque_comiendo", modo="loop", lmin=30, lmax=70, sombra=True),
        dict(src="tanque_muerte.gif", out="tanque_muerte", modo="unavez_auto", desde=32, objetivo=22, sombra=True),
    ]),
    dict(nombre="proyectil", carpeta=EFECTOS, escala=0.9, items=[
        dict(src="proyectil_tirador.gif", out="proyectil_tirador", modo="loop_todo"),
        dict(src="proyectil_impacto.gif", out="proyectil_impacto", modo="unavez"),
    ]),
]

# Elementos que se ajustan a un lienzo cuadrado (iconos y tiles animados)
AJUSTAR = [
    dict(src="icono_dinero.gif", out="icono_dinero", carpeta=UI, lado=128, modo="loop", lmin=20, lmax=70),
    dict(src="icono_vida.gif", out="icono_vida", carpeta=UI, lado=128, modo="loop", lmin=20, lmax=70),
    dict(src="tile_inicio.gif", out="tile_inicio", carpeta=ESCENARIOS, lado=192, modo="loop", lmin=20, lmax=40, marca=MARCA_ESTRELLA),
    dict(src="tile_meta.gif", out="tile_meta", carpeta=ESCENARIOS, lado=192, modo="loop", lmin=20, lmax=50),
]

# GIF que en realidad son imágenes quietas -> PNG
ESTATICOS = [
    dict(src="tile_cesped.gif", out="tile_cesped", carpeta=ESCENARIOS, cuadrado=True),
    dict(src="tile_camino.gif", out="tile_camino", carpeta=ESCENARIOS),
    dict(src="tile_camino_curva.gif", out="tile_camino_curva", carpeta=ESCENARIOS),
]

VIDEOS = [
    ("fondo_menu.mp4", "videos/fondo_menu.mp4"),
    ("logo_juego.mp4", "videos/logo_juego.mp4"),
]
FONDOS = [
    ("fondo_victoria.jpg", f"{ESCENARIOS}/fondo_victoria.jpg"),
    ("fondo_game_over.jpg", f"{ESCENARIOS}/fondo_game_over.jpg"),
]
MUSICA = [
    ("musica_menu.mp3", "sounds/music/musica_menu.ogg"),
    ("musica_partida.mp3", "sounds/music/musica_partida.ogg"),
]


# --------------------------------------------------------------------------
# UTILIDADES DE IMAGEN
# --------------------------------------------------------------------------
def cargar_gif(ruta):
    im = Image.open(ruta)
    frames, durs = [], []
    for f in ImageSequence.Iterator(im):
        durs.append(f.info.get("duration", 100) or 100)
        frames.append(f.convert("RGBA"))
    return frames, durs


def bbox(img):
    return img.getchannel("A").getbbox()


def opacos(img):
    return img.getchannel("A").histogram()[255]


def borrar_componentes(img, caja):
    """Borra las manchas conectadas que quedan por completo dentro de `caja`."""
    x0, y0, x1, y1 = caja
    W, H = img.size
    px = img.load()
    vistos = set()
    a_borrar = []
    for y in range(max(0, y0), min(H, y1)):
        for x in range(max(0, x0), min(W, x1)):
            if px[x, y][3] > 0 and (x, y) not in vistos:
                comp = [(x, y)]
                vistos.add((x, y))
                i = 0
                dentro = True
                while i < len(comp):
                    cx, cy = comp[i]
                    i += 1
                    for dx in (-1, 0, 1):
                        for dy in (-1, 0, 1):
                            nx, ny = cx + dx, cy + dy
                            if 0 <= nx < W and 0 <= ny < H and (nx, ny) not in vistos and px[nx, ny][3] > 0:
                                vistos.add((nx, ny))
                                comp.append((nx, ny))
                                if not (x0 <= nx < x1 and y0 <= ny < y1):
                                    dentro = False
                    if len(comp) > 800:  # es el personaje, no una marca
                        dentro = False
                        break
                if dentro:
                    a_borrar.extend(comp)
    for x, y in a_borrar:
        px[x, y] = (0, 0, 0, 0)


def borrar_claros(img, caja):
    """Borra pixeles casi blancos/lavanda dentro de `caja` (estrella pintada sobre el personaje)."""
    x0, y0, x1, y1 = caja
    W, H = img.size
    px = img.load()
    for y in range(max(0, y0), min(H, y1)):
        for x in range(max(0, x0), min(W, x1)):
            r, g, b, a = px[x, y]
            if a > 0 and min(r, g, b) > 150 and abs(r - b) < 45:
                px[x, y] = (0, 0, 0, 0)


def borrar_lavanda(img, caja):
    """Borra pixeles lavanda/grisáceos claros (la estrella) dentro de una caja apretada."""
    x0, y0, x1, y1 = caja
    W, H = img.size
    px = img.load()
    for y in range(max(0, y0), min(H, y1)):
        for x in range(max(0, x0), min(W, x1)):
            r, g, b, a = px[x, y]
            lum = 0.299 * r + 0.587 * g + 0.114 * b
            if a > 0 and lum > 95 and b >= r + 5 and b >= g + 5:  # tinte lavanda; tierra, hojas y humo gris no
                px[x, y] = (0, 0, 0, 0)


def quitar_motas(img):
    """Quita manchitas sueltas (restos de la sombra) en el 25% inferior del personaje."""
    bb = bbox(img)
    if not bb:
        return
    y_ini = int(bb[3] - (bb[3] - bb[1]) * 0.25)
    W, H = img.size
    px = img.load()
    vistos = set()
    for y in range(y_ini, min(H, bb[3] + 1)):
        for x in range(bb[0], min(W, bb[2] + 1)):
            if px[x, y][3] > 0 and (x, y) not in vistos:
                comp = [(x, y)]
                vistos.add((x, y))
                i = 0
                while i < len(comp) and len(comp) <= 60:
                    cx, cy = comp[i]
                    i += 1
                    for dx in (-1, 0, 1):
                        for dy in (-1, 0, 1):
                            nx, ny = cx + dx, cy + dy
                            if 0 <= nx < W and 0 <= ny < H and (nx, ny) not in vistos and px[nx, ny][3] > 0:
                                vistos.add((nx, ny))
                                comp.append((nx, ny))
                if len(comp) <= 60 and all(cy >= y_ini for _, cy in comp):
                    for cx, cy in comp:
                        px[cx, cy] = (0, 0, 0, 0)


def quitar_sombra(img):
    """Quita la elipse morada bajo los pies (solo en el 22% inferior del personaje)."""
    bb = bbox(img)
    if not bb:
        return
    x0, y0, x1, y1 = bb
    y_ini = int(y1 - (y1 - y0) * 0.22)
    px = img.load()
    for y in range(y_ini, y1):
        for x in range(x0, x1):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            lum = 0.299 * r + 0.587 * g + 0.114 * b
            if (r - g >= 4) and (b - g >= 12) and (b - r >= 3) and 45 <= lum <= 200:
                px[x, y] = (0, 0, 0, 0)


def limpiar(frame, item):
    f = frame.copy()
    for tipo, caja in item.get("marca", []):
        if tipo == "comp":
            borrar_componentes(f, caja)
        elif tipo == "lavanda":
            borrar_lavanda(f, caja)
        else:
            borrar_claros(f, caja)
    if item.get("sombra"):
        quitar_sombra(f)
        if item.get("modo") in ("loop", "loop_todo"):  # en muertes las partículas sueltas son intencionales
            quitar_motas(f)
    return f


def miniatura(f):
    fondo = Image.new("RGBA", f.size, (128, 128, 128, 255))
    fondo.alpha_composite(f)
    return fondo.convert("L").reduce(4)


def dif(a, b):
    return ImageStat.Stat(ImageChops.difference(a, b)).mean[0]


# --------------------------------------------------------------------------
# SELECCIÓN DE CUADROS
# --------------------------------------------------------------------------
def mejor_loop(frames, lmin, lmax):
    n = len(frames)
    lmax = min(lmax, n - 1)
    lmin = min(lmin, lmax)
    mini = [miniatura(f) for f in frames]
    mejor = None
    for i in range(0, n - lmin):
        for j in range(i + lmin, min(n, i + lmax + 1)):
            d = dif(mini[j], mini[i]) - 0.004 * (j - i)  # leve preferencia por tramos más largos
            if mejor is None or d < mejor[0]:
                mejor = (d, i, j)
    _, i, j = mejor
    vecinos = statistics.mean(dif(mini[k], mini[k + 1]) for k in range(i, j))
    return i, j, dif(mini[j], mini[i]), vecinos


def tramo_util(frames, lead, objetivo, desde=None):
    cov = [opacos(f) for f in frames]
    base = statistics.median(cov[:max(3, len(cov) // 5)]) or 1
    if desde is not None:  # inicio fijado a mano
        s = desde
    else:
        s = next((i for i, c in enumerate(cov) if abs(c - base) / base >= 0.08), 0)
        s = max(0, s - lead)
    e = next((i for i in range(s, len(cov)) if cov[i] < 0.03 * base), len(cov) - 1)
    idx = list(range(s, e + 1))
    if len(idx) > objetivo:  # acelera tomando cuadros repartidos parejo
        idx = [idx[round(k * (len(idx) - 1) / (objetivo - 1))] for k in range(objetivo)]
    return idx, (s, e)


def seleccionar(frames, durs, item):
    """Devuelve (lista_de_cuadros, lista_de_duraciones, es_loop, info)."""
    modo = item["modo"]
    if modo == "loop":
        i, j, seam, vec = mejor_loop(frames, item.get("lmin", 12), item.get("lmax", 60))
        sel = frames[i:j]
        return sel, durs[i:j], True, f"loop cuadros {i}-{j - 1} ({len(sel)}), salto {seam:.1f} vs normal {vec:.1f}"
    if modo == "loop_todo":
        return frames, durs, True, f"loop {len(frames)} cuadros"
    if modo == "unavez_auto":
        idx, (s, e) = tramo_util(frames, item.get("lead", 3), item.get("objetivo", 16), item.get("desde"))
        return [frames[k] for k in idx], [100] * len(idx), False, f"una vez: tramo {s}-{e} -> {len(idx)} cuadros"
    return frames, durs, False, f"una vez {len(frames)} cuadros"  # "unavez"


def cuadro_vacio(size):
    return Image.new("RGBA", size, (0, 0, 0, 0))


# --------------------------------------------------------------------------
# GUARDADO DE GIF
# --------------------------------------------------------------------------
def guardar_gif(frames, durs, ruta, loop):
    """Guarda con una paleta común (sin parpadeo de colores) y transparencia."""
    os.makedirs(os.path.dirname(ruta), exist_ok=True)
    W, H = frames[0].size
    binarios = []
    for f in frames:
        f = f.copy()
        f.putalpha(f.getchannel("A").point(lambda v: 255 if v >= 128 else 0))
        binarios.append(f)

    # relleno de las zonas transparentes con el color medio del contenido (no ensucia la paleta)
    muestras = []
    for f in binarios[:: max(1, len(binarios) // 12)]:
        rgb = f.convert("RGB")
        m = f.getchannel("A")
        if m.getbbox():
            med = tuple(int(v) for v in ImageStat.Stat(rgb, mask=m).mean)
        else:
            med = (128, 128, 128)
        relleno = Image.new("RGB", f.size, med)
        relleno.paste(rgb, mask=m)
        muestras.append(relleno)
    montaje = Image.new("RGB", (W, H * len(muestras)))
    for k, mu in enumerate(muestras):
        montaje.paste(mu, (0, k * H))
    paleta = montaje.quantize(colors=255, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)

    pals = []
    for f in binarios:
        m = f.getchannel("A")
        if m.getbbox():
            med = tuple(int(v) for v in ImageStat.Stat(f.convert("RGB"), mask=m).mean)
        else:
            med = (128, 128, 128)
        relleno = Image.new("RGB", f.size, med)
        relleno.paste(f.convert("RGB"), mask=m)
        p = relleno.quantize(palette=paleta, dither=Image.Dither.NONE)
        p.paste(255, mask=m.point(lambda v: 255 if v == 0 else 0))
        pals.append(p)

    kw = dict(save_all=True, append_images=pals[1:], duration=durs, disposal=2, transparency=255)
    if loop:
        kw["loop"] = 0
    pals[0].save(ruta, **kw)


def registrar(manifest, dest, ruta, frames, durs, loop, anchor="bottom-center"):
    rel = os.path.relpath(ruta, dest).replace("\\", "/")
    manifest[rel] = dict(
        ancho=frames[0].size[0], alto=frames[0].size[1], cuadros=len(frames),
        duracion_ms=int(sum(durs)), en_loop=bool(loop), ancla=anchor,
    )


# --------------------------------------------------------------------------
# PROCESO DE GRUPOS (personajes / efectos)
# --------------------------------------------------------------------------
def procesar_grupo(g, src, dest, manifest):
    print(f"\n[{g['nombre']}]")
    datos = []
    for it in g["items"]:
        ruta = os.path.join(src, it["src"])
        if not os.path.isfile(ruta):  # tolera un espacio antes del punto ("nombre .gif")
            alt = os.path.join(src, it["src"].replace(".gif", " .gif"))
            ruta = alt if os.path.isfile(alt) else ruta
        if not os.path.isfile(ruta):
            print(f"  ! falta {it['src']}: se omite")
            continue
        frames, durs = cargar_gif(ruta)
        frames = [limpiar(f, it) for f in frames]
        sel, sdurs, es_loop, info = seleccionar(frames, durs, it)
        print(f"  {it['out']}: {info}")
        datos.append(dict(it=it, todos=frames, sel=sel, durs=sdurs, loop=es_loop))
    if not datos:
        return

    base0 = bbox(datos[0]["todos"][0])
    bw, bh = base0[2] - base0[0], base0[3] - base0[1]
    bcx, bby = (base0[0] + base0[2]) / 2, base0[3]

    # transformación de cada item al sistema de coordenadas del primer item
    for d in datos:
        al = d["it"].get("alinear")
        if not al:
            d["s"], d["off"] = 1.0, (0.0, 0.0)
            continue
        b0 = bbox(d["todos"][0])
        iw, ih = b0[2] - b0[0], b0[3] - b0[1]
        s = (bw / iw) if al == "w" else (bh / ih)
        icx, iby = (b0[0] + b0[2]) / 2, b0[3]
        d["s"], d["off"] = s, (bcx - s * icx, bby - s * iby)

    # lienzo común = unión de todos los cuadros seleccionados
    minx = miny = 1e9
    maxx = maxy = -1e9
    for d in datos:
        for f in d["sel"]:
            bb = bbox(f)
            if not bb:
                continue
            minx = min(minx, d["off"][0] + d["s"] * bb[0])
            miny = min(miny, d["off"][1] + d["s"] * bb[1])
            maxx = max(maxx, d["off"][0] + d["s"] * bb[2])
            maxy = max(maxy, d["off"][1] + d["s"] * bb[3])
    pad = 6
    minx, miny, maxx, maxy = minx - pad, miny - pad, maxx + pad, maxy + pad
    esc = g["escala"]
    W, H = math.ceil((maxx - minx) * esc), math.ceil((maxy - miny) * esc)

    for d in datos:
        it = d["it"]
        salida = []
        for f in d["sel"]:
            k = d["s"] * esc
            ff = f.resize((max(1, round(f.width * k)), max(1, round(f.height * k))), Image.LANCZOS) if abs(k - 1) > 1e-3 else f
            lienzo = cuadro_vacio((W, H))
            lienzo.paste(ff, (round((d["off"][0] - minx) * esc), round((d["off"][1] - miny) * esc)))
            salida.append(lienzo)
        durs = list(d["durs"])
        if not d["loop"]:  # termina en cuadro vacío para que el elemento "desaparezca"
            salida.append(cuadro_vacio((W, H)))
            durs.append(100)
        ruta = os.path.join(dest, g["carpeta"], it["out"] + ".gif")
        guardar_gif(salida, durs, ruta, d["loop"])
        registrar(manifest, dest, ruta, salida, durs, d["loop"])
        print(f"    -> {os.path.relpath(ruta, dest)}  {W}x{H}  {os.path.getsize(ruta) // 1024} KB")

        # icono PNG estático (para las cartas del menú)
        if it.get("icono"):
            f0 = d["todos"][0]
            recorte = f0.crop(bbox(f0))
            k = 160 / max(recorte.size)
            recorte = recorte.resize((max(1, round(recorte.width * k)), max(1, round(recorte.height * k))), Image.LANCZOS)
            rp = os.path.join(dest, g["carpeta"], it["icono"] + ".png")
            recorte.save(rp, optimize=True)
            print(f"    -> {os.path.relpath(rp, dest)}  {recorte.size[0]}x{recorte.size[1]}")


def procesar_ajustar(a, src, dest, manifest):
    ruta = os.path.join(src, a["src"])
    if not os.path.isfile(ruta):
        print(f"  ! falta {a['src']}: se omite")
        return
    frames, durs = cargar_gif(ruta)
    frames = [limpiar(f, a) for f in frames]
    sel, sdurs, es_loop, info = seleccionar(frames, durs, a)
    minx = miny = 1e9
    maxx = maxy = -1e9
    for f in sel:
        bb = bbox(f)
        if bb:
            minx, miny = min(minx, bb[0]), min(miny, bb[1])
            maxx, maxy = max(maxx, bb[2]), max(maxy, bb[3])
    w, h = maxx - minx, maxy - miny
    lado = a["lado"]
    k = (lado - 8) / max(w, h)
    salida = []
    for f in sel:
        rec = f.crop((minx, miny, maxx, maxy))
        rec = rec.resize((max(1, round(w * k)), max(1, round(h * k))), Image.LANCZOS)
        lienzo = cuadro_vacio((lado, lado))
        lienzo.paste(rec, ((lado - rec.width) // 2, (lado - rec.height) // 2))
        salida.append(lienzo)
    out = os.path.join(dest, a["carpeta"], a["out"] + ".gif")
    guardar_gif(salida, sdurs, out, es_loop)
    registrar(manifest, dest, out, salida, sdurs, es_loop, anchor="center")
    print(f"  {a['out']}: {info}\n    -> {os.path.relpath(out, dest)}  {lado}x{lado}  {os.path.getsize(out) // 1024} KB")


def procesar_estatico(e, src, dest):
    ruta = os.path.join(src, e["src"])
    if not os.path.isfile(ruta):
        print(f"  ! falta {e['src']}: se omite")
        return
    im = Image.open(ruta).convert("RGB")
    if e.get("cuadrado") and im.width != im.height:
        lado = min(im.size)
        x = (im.width - lado) // 2
        im = im.crop((x, 0, x + lado, lado))
    out = os.path.join(dest, e["carpeta"], e["out"] + ".png")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    im.save(out, optimize=True)
    print(f"  {e['out']}: {im.size[0]}x{im.size[1]} (PNG estático)  {os.path.getsize(out) // 1024} KB")


# --------------------------------------------------------------------------
# VIDEOS / FONDOS / MÚSICA
# --------------------------------------------------------------------------
def ffmpeg_exe():
    import imageio_ffmpeg
    return imageio_ffmpeg.get_ffmpeg_exe()


def correr(cmd):
    r = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode != 0:
        print(r.stderr[-1500:])
        raise SystemExit(f"ffmpeg falló: {cmd[0]}")
    return r.stderr


def procesar_videos(src, dest):
    ff = ffmpeg_exe()
    for nombre, rel in VIDEOS:
        origen = os.path.join(src, nombre)
        if not os.path.isfile(origen):
            print(f"  ! falta {nombre}: se omite")
            continue
        out = os.path.join(dest, rel)
        os.makedirs(os.path.dirname(out), exist_ok=True)
        correr([ff, "-y", "-hide_banner", "-loglevel", "error", "-i", origen, "-an",
                "-c:v", "libx264", "-crf", "27", "-preset", "slow", "-pix_fmt", "yuv420p",
                "-movflags", "+faststart", out])
        print(f"  {nombre} -> {rel} (sin audio)  {os.path.getsize(origen) // 1024} KB -> {os.path.getsize(out) // 1024} KB")


def procesar_fondos(src, dest):
    for nombre, rel in FONDOS:
        origen = os.path.join(src, nombre)
        if not os.path.isfile(origen):
            print(f"  ! falta {nombre}: se omite")
            continue
        im = Image.open(origen).convert("RGB")
        k = 1920 / im.width
        im = im.resize((1920, round(im.height * k)), Image.LANCZOS)
        out = os.path.join(dest, rel)
        os.makedirs(os.path.dirname(out), exist_ok=True)
        im.save(out, quality=85, optimize=True, progressive=True)
        print(f"  {nombre} -> {rel}  {im.size[0]}x{im.size[1]}  {os.path.getsize(origen) // 1024} KB -> {os.path.getsize(out) // 1024} KB")


def duracion(ff, ruta):
    r = subprocess.run([ff, "-hide_banner", "-i", ruta], capture_output=True, text=True, encoding="utf-8", errors="replace")
    m = re.search(r"Duration: (\d+):(\d+):([\d.]+)", r.stderr)
    return int(m.group(1)) * 3600 + int(m.group(2)) * 60 + float(m.group(3))


def procesar_musica(src, dest, cruce=4.0):
    """Loop sin salto: el final se mezcla (crossfade) con el inicio; luego normaliza volumen."""
    ff = ffmpeg_exe()
    for nombre, rel in MUSICA:
        origen = os.path.join(src, nombre)
        if not os.path.isfile(origen):
            print(f"  ! falta {nombre}: se omite")
            continue
        D = duracion(ff, origen)
        X = cruce
        fc = (
            f"[0:a]asplit=3[h][t][b];"
            f"[h]atrim=0:{X},asetpts=PTS-STARTPTS[head];"
            f"[t]atrim={D - X:.3f}:{D:.3f},asetpts=PTS-STARTPTS[tail];"
            f"[b]atrim={X}:{D - X:.3f},asetpts=PTS-STARTPTS[body];"
            f"[tail][head]acrossfade=d={X}:c1=tri:c2=tri[xf];"
            f"[body][xf]concat=n=2:v=0:a=1[cat];"
            f"[cat]loudnorm=I=-20:TP=-2:LRA=11[out]"
        )
        out = os.path.join(dest, rel)
        os.makedirs(os.path.dirname(out), exist_ok=True)
        correr([ff, "-y", "-hide_banner", "-loglevel", "error", "-i", origen, "-filter_complex", fc,
                "-map", "[out]", "-c:a", "libvorbis", "-q:a", "4", out])
        print(f"  {nombre} -> {rel}  {D:.0f}s -> {duracion(ff, out):.0f}s (loop sin salto, -20 LUFS)  "
              f"{os.path.getsize(origen) // 1024} KB -> {os.path.getsize(out) // 1024} KB")


# --------------------------------------------------------------------------
def main():
    ap = argparse.ArgumentParser(description="Procesa los assets crudos y los deja listos para el juego.")
    ap.add_argument("--src", required=True, help="Carpeta con los archivos crudos (gif, mp4, jpg, mp3)")
    ap.add_argument("--dest", required=True, help="Carpeta de salida (frontend/assets)")
    ap.add_argument("--solo", choices=["gifs", "videos", "fondos", "musica"], help="Procesar solo una parte")
    args = ap.parse_args()

    src, dest = args.src, args.dest
    manifest_path = os.path.join(dest, "manifest.json")
    manifest = {}
    if os.path.isfile(manifest_path):
        with open(manifest_path, encoding="utf-8") as fh:
            manifest = json.load(fh)

    if args.solo in (None, "gifs"):
        print("=== GIF: personajes y efectos ===")
        for g in GRUPOS:
            procesar_grupo(g, src, dest, manifest)
        print("\n=== GIF: iconos y tiles animados ===")
        for a in AJUSTAR:
            procesar_ajustar(a, src, dest, manifest)
        print("\n=== Imágenes estáticas (GIF -> PNG) ===")
        for e in ESTATICOS:
            procesar_estatico(e, src, dest)
        with open(manifest_path, "w", encoding="utf-8") as fh:
            json.dump(dict(sorted(manifest.items())), fh, indent=2, ensure_ascii=False)
        print(f"\nmanifest: {manifest_path} ({len(manifest)} animaciones)")
    if args.solo in (None, "videos"):
        print("\n=== Videos ===")
        procesar_videos(src, dest)
    if args.solo in (None, "fondos"):
        print("\n=== Fondos ===")
        procesar_fondos(src, dest)
    if args.solo in (None, "musica"):
        print("\n=== Música ===")
        procesar_musica(src, dest)


if __name__ == "__main__":
    main()
