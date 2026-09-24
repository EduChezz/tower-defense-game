import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Clase Json - Escritura y lectura minima de JSON (sin librerias externas).
 * Escribe Map, Iterable, String, Number, Boolean, null y EventoJuego.
 * Lee campos sueltos de un objeto plano ({"tipo":"MURO","fila":2}).
 */
public class Json {
    public static String escribir(Object valor) {
        StringBuilder sb = new StringBuilder();
        escribir(valor, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void escribir(Object valor, StringBuilder sb) {
        if (valor == null) {
            sb.append("null");
        } else if (valor instanceof String) {
            escribirTexto((String) valor, sb);
        } else if (valor instanceof Number || valor instanceof Boolean) {
            sb.append(valor);
        } else if (valor instanceof EventoJuego) {
            escribir(((EventoJuego) valor).comoMapa(), sb);
        } else if (valor instanceof Map) {
            sb.append('{');
            boolean primero = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) valor).entrySet()) {
                if (!primero) sb.append(',');
                primero = false;
                escribirTexto(e.getKey(), sb);
                sb.append(':');
                escribir(e.getValue(), sb);
            }
            sb.append('}');
        } else if (valor instanceof Iterable) {
            sb.append('[');
            Iterator<?> it = ((Iterable<?>) valor).iterator();
            while (it.hasNext()) {
                escribir(it.next(), sb);
                if (it.hasNext()) sb.append(',');
            }
            sb.append(']');
        } else {
            escribirTexto(valor.toString(), sb);
        }
    }

    private static void escribirTexto(String texto, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    // ---------- lectura ----------
    public static String leerTexto(String json, String campo) {
        Matcher m = Pattern.compile("\"" + campo + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    public static Integer leerEntero(String json, String campo) {
        Matcher m = Pattern.compile("\"" + campo + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    public static Boolean leerBooleano(String json, String campo) {
        Matcher m = Pattern.compile("\"" + campo + "\"\\s*:\\s*(true|false)").matcher(json);
        return m.find() ? Boolean.valueOf(m.group(1)) : null;
    }
}
