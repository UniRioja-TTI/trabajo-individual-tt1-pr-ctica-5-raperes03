package servicios;

import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import interfaces.InterfazEnviarEmails;
import modelo.Destinatario;

@Service
public class ServicioEnviarEmails implements InterfazEnviarEmails {

    private final Logger logger;

    //De momento solo tiene que recibir el logger en el constructor
    public ServicioEnviarEmails(Logger logger) {
        this.logger = logger;
    }

    //De momento solo tiene que actualizar el logger
    @Override
    public boolean enviarEmail(Destinatario dest, String email) {
        if (dest == null || email == null) {
            logger.warn("enviarEmail ha fallado, el dest o el email son nulos");
            return false;
        }

        logger.info("enviarEmail ha funcionado: destinatario={}, contenido={}", dest, email);
        return true;
    }
}