package servicios;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import modelo.Punto;
import org.openapitools.client.ApiException;
import org.openapitools.client.model.Solicitud;
import org.openapitools.client.model.SolicitudResponse;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import interfaces.InterfazContactoSim;
import modelo.DatosSimulation;
import modelo.DatosSolicitud;
import modelo.Entidad;
import org.openapitools.client.ApiClient;
import org.openapitools.client.api.ResultadosApi;
import org.openapitools.client.api.SolicitudApi;
import org.openapitools.client.model.ResultsResponse;


@Service
public class ServicioContactoSim implements InterfazContactoSim {

    private final Logger logger;
    @Value("${SERVICIO_URL:http://localhost:8080}")
    private String servicioUrl;
    private final SolicitudApi solicitudApi;
    private final ResultadosApi resultadosApi;

    private final List<Entidad> entidades = new ArrayList<>();
    private final ConcurrentHashMap<Integer, DatosSolicitud> solicitudes = new ConcurrentHashMap<>();
    private final AtomicInteger ticketCounter = new AtomicInteger(1);

    private static final String usuario_cte = "AlumnoRioja";

    public ServicioContactoSim(Logger logger) {
        this.logger = logger;

        // Configuro el cliente para conectarlo con la MV
        // Uso localhost:8080 porque es el puerto redirigido (al 5000 de la VM)
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(servicioUrl);

        this.solicitudApi = new SolicitudApi(apiClient);
        this.resultadosApi = new ResultadosApi(apiClient);

        inicializarEntidades();

    }

    //Método que mencionaba para inventarme la lista
    private void inicializarEntidades() {
        Entidad e1 = new Entidad();
        e1.setId(1);
        e1.setName("Entidad A");
        e1.setDescripcion("Descripción A");

        Entidad e2 = new Entidad();
        e2.setId(2);
        e2.setName("Entidad B");
        e2.setDescripcion("Descripción B");

        Entidad e3 = new Entidad();
        e3.setId(3);
        e3.setName("Entidad C");
        e3.setDescripcion("Descripción C");

        entidades.add(e1);
        entidades.add(e2);
        entidades.add(e3);
    }

    //De momento simplemente genero un token aleatorio y lo almaceno y lo devuelvo
    @Override
    public int solicitarSimulation(DatosSolicitud sol) {

        try {

            Solicitud solicitud = new Solicitud();

            List<String> nombres = new ArrayList<>();
            List<Integer> cantidades = new ArrayList<>();

            for (Entidad e : entidades) {
                int id = e.getId();

                if (sol.getNums().containsKey(id)) {
                    nombres.add(e.getName());
                    cantidades.add(sol.getNums().get(id));
                }
            }

            solicitud.setNombreEntidades(nombres);
            solicitud.setCantidadesIniciales(cantidades);

            SolicitudResponse resp = solicitudApi.solicitudSolicitarPost(usuario_cte, solicitud);

            if (resp.getDone() != null && resp.getDone()) {
                return resp.getTokenSolicitud();
            } else {
                logger.error("Error de simulación: {}", resp.getErrorMessage());
                return -1;
            }

        } catch (ApiException e) {
            logger.error("Error al contactar con la VM: {}", e.getResponseBody());
            return -1;
        }
    }

    @Override
    public DatosSimulation descargarDatos(int ticket) {
        DatosSimulation ds = new DatosSimulation();
        try {
            // Intentamos comprobar si está terminada
            try {
                List<Integer> terminadas = solicitudApi.solicitudComprobarSolicitudGet(usuario_cte, ticket);
                if (terminadas == null || !terminadas.contains(ticket)) {
                    return null;
                }
            } catch (Exception e) {
                // Si da el error de "BEGIN_ARRAY", significa que la MV ha respondido
                // con un objeto de error o un formato nuevo.
                // En ese caso, vamos a intentar descargar los resultados directamente.
                logger.warn("El formato de comprobación ha cambiado, intentando descarga directa...");
            }

            // Llamada a los resultados
            ResultsResponse res = resultadosApi.resultadosPost(usuario_cte, ticket);

            // Si la MV responde con un 200 pero sin datos todavía, res.getData() será null
            if (res == null || res.getData() == null || res.getData().isEmpty()) {
                return null;
            }

            String rawData = res.getData();
            Map<Integer, List<Punto>> mapaPuntos = new HashMap<>();
            int maxSegundosCalculo = 0;

            String[] lineas = rawData.split("\\r?\\n");

            if (lineas.length > 0) {
                ds.setAnchoTablero(Integer.parseInt(lineas[0].trim()));
                for (int i = 1; i < lineas.length; i++) {
                    String linea = lineas[i].trim();
                    if (linea.isEmpty()) continue;
                    String[] partes = linea.split(",");
                    if (partes.length == 4) {
                        int tiempo = Integer.parseInt(partes[0].trim());
                        int y = Integer.parseInt(partes[1].trim());
                        int x = Integer.parseInt(partes[2].trim());
                        String color = partes[3].trim();
                        //Actualizo el tiempo max conocido
                        if (tiempo > maxSegundosCalculo) {
                            maxSegundosCalculo = tiempo;
                        }
                        // añado el punto a la lista correspondiente a ese tiempo dado
                        // computeIfAbsent crea la lista si es la primera vez que se ve ese segundo, creo que es la mejor opcion
                        Punto p = new Punto(y,x, color);
                        mapaPuntos.computeIfAbsent(tiempo, k -> new ArrayList<>()).add(p);
                    }
                }
            }
            //Le meto el mapa y los seg max al objeto y devuelvo
            ds.setPuntos(mapaPuntos);
            ds.setMaxSegundos(maxSegundosCalculo);
            return ds;

        } catch (ApiException ex) {
            // Si entramos aquí con un 404 o 400, es que la simulación de verdad no existe o no ha terminado
            logger.info("Simulación no lista o no encontrada para el token: {}", ticket);
            return null;
        } catch (Exception e) {
            logger.error("Error inesperado: {}", e.getMessage());
            return null;
        }
    }

    //Implemento devolviendo una copia siempre, nunca la original
    @Override
    public List<Entidad> getEntities() {
        return new ArrayList<>(entidades);
    }

    //Metodo arreglado con lo de la id
    @Override
    public boolean isValidEntityId(int id) {
        return entidades.stream().anyMatch(e -> e.getId() == id);
    }

    // Esto es para mis tests
    public int countSolicitudes() {
        return solicitudes.size();
    }
}