import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Clase EventoJuego - Suceso ocurrido durante un tick o una accion del jugador.
 * El frontend los usa para disparar animaciones y sonidos.
 *
 * Tipos: nueva_oleada, oleada_final, zombie_entra, zombie_mueve, zombie_ataca, zombie_muere,
 *        zombie_meta, disparo, mina_explota, planta_colocada, planta_retirada,
 *        planta_destruida, victoria, game_over
 */
public class EventoJuego {
    public final String tipo;
    public final Map<String, Object> datos = new LinkedHashMap<>();

    public EventoJuego(String tipo) {
        this.tipo = tipo;
    }

    public EventoJuego con(String clave, Object valor) {
        this.datos.put(clave, valor);
        return this;
    }

    public Map<String, Object> comoMapa() {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("tipo", this.tipo);
        mapa.putAll(this.datos);
        return mapa;
    }
}
