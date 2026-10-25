package com.proyecto.parking.controller;

import com.proyecto.parking.exception.RecursoNoEncontradoException;
import com.proyecto.parking.exception.ReglaNegocioException;
import com.proyecto.parking.model.Reserva;
import com.proyecto.parking.model.Reserva.EstadoReserva;
import com.proyecto.parking.security.UsuarioPrincipal;
import com.proyecto.parking.service.PagoService;
import com.proyecto.parking.service.PagoService.Resultado;
import com.proyecto.parking.service.PasarelaPago;
import com.proyecto.parking.service.ReservaService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Pago del anticipo de una reserva.
 *
 * <p>{@code /reserva/pago/**} es del cliente (lo protege la regla de
 * {@code /reserva/**}). {@code /pagos/retorno} y {@code /pagos/eventos} son públicos: los usan la pasarela y
 * el navegador del cliente al volver de pagar, y nada de lo que llega ahí se da
 * por bueno sin comprobarlo contra la API de la pasarela.</p>
 */
@Controller
public class PagoController {

    private final PagoService pagoService;
    private final ReservaService reservaService;

    public PagoController(PagoService pagoService, ReservaService reservaService) {
        this.pagoService = pagoService;
        this.reservaService = reservaService;
    }

    /** Resumen del anticipo antes de salir a la pasarela. */
    @GetMapping("/reserva/pago/{idReserva}")
    public String resumen(@AuthenticationPrincipal UsuarioPrincipal cliente,
                          @PathVariable String idReserva,
                          Model model) {
        Reserva reserva = reservaDelCliente(idReserva, cliente.getId());
        if (reserva.getEstado() != EstadoReserva.PENDIENTE_PAGO) {
            return "redirect:/cliente?scroll=reservas";
        }
        // Si pagó y no pulsó "volver al comercio", la pasarela ya lo sabe aunque
        // nosotros no: se comprueba antes de pedirle que pague otra vez.
        if (pagoService.recuperarPagoPerdido(reserva)) {
            return "redirect:/cliente?scroll=reservas";
        }
        model.addAttribute("reserva", reserva);
        model.addAttribute("limite", pagoService.limiteParaPagar(reserva));
        model.addAttribute("minutosAntelacion", pagoService.minutosAntelacionDevolucion());
        model.addAttribute("pasarela", pagoService.nombrePasarela());
        return "cliente/pago";
    }

    /**
     * Abre el intento de pago y envía al cliente a la pasarela. Es un enlace y no
     * un formulario: la política de seguridad de contenido sólo permite enviar
     * formularios a la propia aplicación.
     */
    @GetMapping("/reserva/pago/{idReserva}/ir")
    public String irALaPasarela(@AuthenticationPrincipal UsuarioPrincipal cliente,
                                @PathVariable String idReserva,
                                RedirectAttributes flash) {
        try {
            return "redirect:" + pagoService.iniciarPago(idReserva, cliente.getId());
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/cliente?scroll=reservas";
        }
    }

    /**
     * Mercado Pago devuelve aquí al cliente con el id del pago en la URL
     * ({@code payment_id}, también repetido como {@code collection_id}).
     */
    @GetMapping("/pagos/retorno")
    public String retorno(@RequestParam(name = "payment_id", required = false) String idPago,
                          @RequestParam(name = "collection_id", required = false) String idCobro,
                          RedirectAttributes flash) {
        String idTransaccion = conTexto(idPago) ? idPago : idCobro;
        // Si el cliente abandona el pago, Mercado Pago manda el texto "null".
        Resultado resultado = !conTexto(idTransaccion) || "null".equals(idTransaccion)
                ? Resultado.NO_ENCONTRADO
                : pagoService.confirmarTransaccion(idTransaccion);

        switch (resultado) {
            case APROBADO -> flash.addFlashAttribute("mensaje",
                    "Pago recibido. Tu reserva queda pendiente de que el parqueadero la confirme.");
            case EN_PROCESO -> flash.addFlashAttribute("mensaje",
                    "Tu pago se está procesando. La reserva se actualizará sola cuando se confirme.");
            case RECHAZADO -> flash.addFlashAttribute("error",
                    "El pago no se aprobó. Puedes intentarlo de nuevo desde «Mis reservas».");
            case APROBADO_SIN_RESERVA -> flash.addFlashAttribute("error",
                    "El pago llegó cuando la reserva ya no estaba disponible. Te devolveremos el dinero.");
            case NO_ENCONTRADO -> flash.addFlashAttribute("error",
                    "No pudimos comprobar el pago. Si ya pagaste, la reserva se actualizará en unos minutos.");
        }
        return "redirect:/cliente?scroll=reservas";
    }

    /**
     * Aviso (webhook) de la pasarela. Su firma viene en las cabeceras y el id
     * del pago en la URL, así que se entregan junto con el cuerpo. Siempre
     * responde 200: un error haría que lo reintentara sin fin.
     */
    @PostMapping("/pagos/eventos")
    public ResponseEntity<Void> aviso(@RequestBody(required = false) Map<String, Object> cuerpo,
                                      @RequestHeader HttpHeaders cabeceras,
                                      @RequestParam Map<String, String> parametros) {
        Map<String, String> cabecerasEnMinuscula = new HashMap<>();
        cabeceras.forEach((nombre, valores) -> {
            if (!valores.isEmpty()) {
                cabecerasEnMinuscula.put(nombre.toLowerCase(Locale.ROOT), valores.get(0));
            }
        });
        pagoService.procesarAviso(new PasarelaPago.Aviso(cuerpo, cabecerasEnMinuscula, parametros));
        return ResponseEntity.ok().build();
    }

    private static boolean conTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    private Reserva reservaDelCliente(String idReserva, String idCliente) {
        return reservaService.listarReservasCliente(idCliente).stream()
                .filter(r -> idReserva.equals(r.getId()))
                .findFirst()
                .orElseThrow(() -> RecursoNoEncontradoException.de("Reserva", idReserva));
    }
}
