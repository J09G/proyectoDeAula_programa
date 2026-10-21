package com.proyecto.parking.controller;

import com.proyecto.parking.exception.RecursoNoEncontradoException;
import com.proyecto.parking.exception.ReglaNegocioException;
import com.proyecto.parking.model.Reserva;
import com.proyecto.parking.model.Reserva.EstadoReserva;
import com.proyecto.parking.security.UsuarioPrincipal;
import com.proyecto.parking.service.PagoService;
import com.proyecto.parking.service.PagoService.Resultado;
import com.proyecto.parking.service.ReservaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/**
 * Pago del anticipo de una reserva.
 *
 * <p>{@code /reserva/pago/**} es del cliente (lo protege la regla de
 * {@code /reserva/**}). {@code /pagos/wompi/**} es público: lo usan la pasarela y
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

    /** La pasarela devuelve aquí al cliente, con el id de la transacción en la URL. */
    @GetMapping("/pagos/wompi/retorno")
    public String retorno(@RequestParam(name = "id", required = false) String idTransaccion,
                          RedirectAttributes flash) {
        Resultado resultado = idTransaccion == null || idTransaccion.isBlank()
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

    /** Aviso de la pasarela. Siempre responde 200: un error haría que lo reintentara sin fin. */
    @PostMapping("/pagos/wompi/eventos")
    public ResponseEntity<Void> aviso(@RequestBody(required = false) Map<String, Object> aviso) {
        pagoService.procesarAviso(aviso);
        return ResponseEntity.ok().build();
    }

    private Reserva reservaDelCliente(String idReserva, String idCliente) {
        return reservaService.listarReservasCliente(idCliente).stream()
                .filter(r -> idReserva.equals(r.getId()))
                .findFirst()
                .orElseThrow(() -> RecursoNoEncontradoException.de("Reserva", idReserva));
    }
}
