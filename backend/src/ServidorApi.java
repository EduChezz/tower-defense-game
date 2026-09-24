import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Clase ServidorApi - Servidor HTTP del juego (sin librerias externas: com.sun.net.httpserver).
 *
 * Sirve el frontend (carpeta frontend/) y la API JSON del motor Juego:
 *   GET  /api/catalogo        Datos fijos: costos, vida, daño, velocidad...
 *   POST /api/juego/nuevo     Crea una partida nueva        {"demo":false}
 *   GET  /api/estado          Estado actual de la partida
 *   POST /api/tick            Avanza un tick y devuelve {estado, eventos}
 *   POST /api/defensa         Coloca una defensa            {"tipo":"MURO","fila":0,"columna":3}
 *   POST /api/retirar         Retira la ultima defensa colocada (Pila LIFO)
 *
 * Uso:  java ServidorApi [carpetaFrontend] [puerto]
 */
public class ServidorApi {
    private static Juego juego = Juego.paraApi(false);
    private static Path raizWeb;

    public static void main(String[] args) throws Exception {
        raizWeb = localizarFrontend(args.length > 0 ? args[0] : null);
        int puerto = args.length > 1 ? Integer.parseInt(args[1]) : 8080;

        // Solo escucha en localhost: el juego no queda expuesto a la red
        HttpServer servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), puerto), 0);
        servidor.createContext("/api/", ServidorApi::manejarApi);
        servidor.createContext("/", ServidorApi::servirArchivo);
        servidor.setExecutor(Executors.newCachedThreadPool());  // hilos elasticos: un video a medio descargar no bloquea al resto
        servidor.start();

        System.out.println("=====================================================");
        System.out.println("  PLANT DEFENSE");
        System.out.println("  Juego:      http://127.0.0.1:" + puerto + "/");
        System.out.println("  Frontend:   " + raizWeb.toAbsolutePath().normalize());
        System.out.println("  (Ctrl+C para detener el servidor)");
        System.out.println("=====================================================");
    }

    private static Path localizarFrontend(String indicada) {
        String[] candidatas = indicada != null
                ? new String[] {indicada}
                : new String[] {"frontend", "../frontend", "../../frontend"};
        for (String c : candidatas) {
            Path p = Paths.get(c);
            if (Files.isDirectory(p) && Files.exists(p.resolve("index.html"))) {
                return p;
            }
        }
        throw new IllegalStateException("No encuentro la carpeta frontend/ (con index.html). " +
                "Ejecuta: java ServidorApi <ruta a la carpeta frontend>");
    }

    // ================= API =================
    private static void manejarApi(HttpExchange ex) throws IOException {
        try {
            String ruta = ex.getRequestURI().getPath();
            String metodo = ex.getRequestMethod();

            String cuerpo = "";
            try (InputStream in = ex.getRequestBody()) {
                cuerpo = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }

            Map<String, Object> respuesta = new LinkedHashMap<>();
            synchronized (ServidorApi.class) {
                if ("/api/catalogo".equals(ruta) && "GET".equals(metodo)) {
                    responderJson(ex, 200, Juego.catalogo());
                    return;
                } else if ("/api/estado".equals(ruta) && "GET".equals(metodo)) {
                    respuesta.put("estado", juego.estado());
                    respuesta.put("eventos", juego.tomarEventos());
                } else if ("/api/juego/nuevo".equals(ruta) && "POST".equals(metodo)) {
                    Boolean demo = Json.leerBooleano(cuerpo, "demo");
                    juego = Juego.paraApi(demo != null && demo);
                    respuesta.put("estado", juego.estado());
                    respuesta.put("eventos", juego.tomarEventos());
                } else if ("/api/tick".equals(ruta) && "POST".equals(metodo)) {
                    juego.avanzarTick();
                    respuesta.put("estado", juego.estado());
                    respuesta.put("eventos", juego.tomarEventos());
                } else if ("/api/defensa".equals(ruta) && "POST".equals(metodo)) {
                    String tipo = Json.leerTexto(cuerpo, "tipo");
                    Integer fila = Json.leerEntero(cuerpo, "fila");
                    Integer columna = Json.leerEntero(cuerpo, "columna");
                    String error = (tipo == null || fila == null || columna == null)
                            ? "Faltan datos: tipo, fila y columna."
                            : juego.colocarDefensaApi(tipo, fila, columna);
                    respuesta.put("ok", error == null);
                    respuesta.put("error", error);
                    respuesta.put("estado", juego.estado());
                    respuesta.put("eventos", juego.tomarEventos());
                } else if ("/api/retirar".equals(ruta) && "POST".equals(metodo)) {
                    String error = juego.retirarUltimaApi();
                    respuesta.put("ok", error == null);
                    respuesta.put("error", error);
                    respuesta.put("estado", juego.estado());
                    respuesta.put("eventos", juego.tomarEventos());
                } else {
                    Map<String, Object> nf = new LinkedHashMap<>();
                    nf.put("error", "Ruta no encontrada: " + metodo + " " + ruta);
                    responderJson(ex, 404, nf);
                    return;
                }
            }
            responderJson(ex, 200, respuesta);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "Error interno: " + e);
            responderJson(ex, 500, err);
        }
    }

    private static void responderJson(HttpExchange ex, int codigo, Object cuerpo) throws IOException {
        Headers h = ex.getResponseHeaders();
        h.set("Cache-Control", "no-store");
        if (cuerpo == null) {
            ex.sendResponseHeaders(codigo, -1);
            ex.close();
            return;
        }
        byte[] datos = Json.escribir(cuerpo).getBytes(StandardCharsets.UTF_8);
        h.set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(codigo, datos.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(datos);
        }
    }

    // ================= ARCHIVOS ESTATICOS =================
    private static void servirArchivo(HttpExchange ex) throws IOException {
        try {
            String ruta = ex.getRequestURI().getPath();
            if ("/".equals(ruta)) {
                ruta = "/index.html";
            }
            Path archivo = raizWeb.resolve(ruta.substring(1)).normalize();
            // Evita salir de la carpeta del frontend (../)
            if (!archivo.startsWith(raizWeb.normalize()) || !Files.isRegularFile(archivo)) {
                byte[] nf = "404 - No encontrado".getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                ex.sendResponseHeaders(404, nf.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(nf);
                }
                return;
            }

            long total = Files.size(archivo);
            Headers h = ex.getResponseHeaders();
            h.set("Content-Type", tipoMime(archivo.getFileName().toString()));
            h.set("Accept-Ranges", "bytes");
            // Codigo y estilos: siempre revalidar. Imagenes, video y audio: cache 1 hora.
            boolean codigo = ruta.endsWith(".html") || ruta.endsWith(".js") || ruta.endsWith(".css") || ruta.endsWith(".json");
            h.set("Cache-Control", codigo ? "no-cache" : "public, max-age=3600");

            long inicio = 0;
            long fin = total - 1;
            int estado = 200;
            String rango = ex.getRequestHeaders().getFirst("Range");
            if (rango != null && rango.startsWith("bytes=")) {  // necesario para reproducir/repetir video
                String[] partes = rango.substring(6).split("-", 2);
                try {
                    if (!partes[0].isEmpty()) {
                        inicio = Long.parseLong(partes[0]);
                        if (partes.length > 1 && !partes[1].isEmpty()) {
                            fin = Math.min(Long.parseLong(partes[1]), total - 1);
                        }
                    } else if (partes.length > 1 && !partes[1].isEmpty()) {  // ultimos N bytes
                        inicio = Math.max(0, total - Long.parseLong(partes[1]));
                    }
                    if (inicio > fin || inicio >= total) {
                        h.set("Content-Range", "bytes */" + total);
                        ex.sendResponseHeaders(416, -1);
                        return;
                    }
                    estado = 206;
                    h.set("Content-Range", "bytes " + inicio + "-" + fin + "/" + total);
                } catch (NumberFormatException e) {
                    inicio = 0;
                    fin = total - 1;
                }
            }

            long longitud = fin - inicio + 1;
            if ("HEAD".equals(ex.getRequestMethod())) {
                ex.sendResponseHeaders(estado, -1);
                return;
            }
            ex.sendResponseHeaders(estado, longitud);
            try (OutputStream os = ex.getResponseBody(); RandomAccessFile raf = new RandomAccessFile(archivo.toFile(), "r")) {
                raf.seek(inicio);
                byte[] buffer = new byte[64 * 1024];
                long restante = longitud;
                while (restante > 0) {
                    int leidos = raf.read(buffer, 0, (int) Math.min(buffer.length, restante));
                    if (leidos < 0) break;
                    os.write(buffer, 0, leidos);
                    restante -= leidos;
                }
            }
        } catch (IOException e) {
            // El navegador cancela peticiones de video/audio a mitad de camino: es normal
        } finally {
            ex.close();
        }
    }

    private static String tipoMime(String nombre) {
        String n = nombre.toLowerCase();
        if (n.endsWith(".html")) return "text/html; charset=utf-8";
        if (n.endsWith(".css")) return "text/css; charset=utf-8";
        if (n.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (n.endsWith(".json")) return "application/json; charset=utf-8";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".ico")) return "image/x-icon";
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".webm")) return "video/webm";
        if (n.endsWith(".ogg")) return "audio/ogg";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }
}
