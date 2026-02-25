package com.proyecto.parking.controller;

import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.Reserva;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.service.ParqueaderoService;
import com.proyecto.parking.service.ReservaService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminController {


    @Autowired
    private ParqueaderoService parqueaderoService;

    @Autowired
    private ReservaService reservaService;

    @GetMapping("")
    public String mostrarPanelAdmin(HttpSession session, Model model) {
        try {
            Usuario admin = (Usuario) session.getAttribute("usuario");

            if (admin == null) {
                return "redirect:/login";
            }

            Parqueadero parqueadero = parqueaderoService.obtenerParqueaderoPorAdministrador(admin.getIdUsuario());
            if (parqueadero == null) {
                model.addAttribute("error", "No tienes parqueadero asignado aún.");
                return "admin/index";
            }

            if (!model.containsAttribute("reservas")) {
                List<Reserva> reservas = reservaService.listarReservasParqueadero(parqueadero.getIdParqueadero());
                model.addAttribute("reservas", reservas);
            }

            model.addAttribute("parqueadero", parqueadero);

        } catch (Exception e) {
            model.addAttribute("error", "Error al cargar el panel del administrador: " + e.getMessage());
        }

        return "admin/index";
    }

    @GetMapping("/reservas/buscar")
    public String buscarReservaPorCedula(@RequestParam("cedula") String cedula,
                                         HttpSession session,
                                         RedirectAttributes redirectAttributes) {
        try {
            Usuario admin = (Usuario) session.getAttribute("usuario");
            if (admin == null) {
                return "redirect:/login";
            }

            Parqueadero parqueadero = parqueaderoService.obtenerParqueaderoPorAdministrador(admin.getIdUsuario());
            if (parqueadero == null) {
                redirectAttributes.addFlashAttribute("error", "No tienes parqueadero asignado.");
                return "redirect:/admin";
            }

            List<Reserva> reservas = reservaService.buscarReservasPorCedulaYParqueadero(cedula, parqueadero.getIdParqueadero());
            if (reservas.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "No se encontraron reservas para la cédula: " + cedula);
            } else {
                redirectAttributes.addFlashAttribute("reservas", reservas);
                redirectAttributes.addFlashAttribute("parqueadero", parqueadero);
            }

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al buscar reservas: " + e.getMessage());
        }

        return "redirect:/admin";
    }

    @PostMapping("/editar")
    public String editarParqueadero(@RequestParam("nombre") String nombre,
                                    @RequestParam("direccion") String direccion,
                                    @RequestParam("horario") String horario,
                                    @RequestParam("tarifa") double tarifa,
                                    @RequestParam(value = "urlMaps", required = false) String urlMaps,
                                    HttpSession session,
                                    RedirectAttributes redirectAttributes) {
        try {
            Usuario admin = (Usuario) session.getAttribute("usuario");
            Parqueadero parqueadero = parqueaderoService.obtenerParqueaderoPorAdministrador(admin.getIdUsuario());

            parqueaderoService.actualizarParqueadero(
                    parqueadero.getIdParqueadero(),
                    nombre,
                    direccion,
                    horario,
                    tarifa,
                    parqueadero.getEspaciosTotales(),
                    parqueadero.getEspaciosDisponibles(),
                    parqueadero.getZona().getIdZona(),
                    urlMaps
            );

            redirectAttributes.addFlashAttribute("mensaje", "Información del parqueadero actualizada correctamente.");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al actualizar la información: " + e.getMessage());
        }

        return "redirect:/admin";
    }

    @PostMapping("/espacios")
    public String actualizarEspacios(@RequestParam("espacios") int espacios,
                                     HttpSession session,
                                     RedirectAttributes redirectAttributes) {
        try {
            Usuario admin = (Usuario) session.getAttribute("usuario");
            Parqueadero parqueadero = parqueaderoService.obtenerParqueaderoPorAdministrador(admin.getIdUsuario());

            if (espacios > parqueadero.getEspaciosTotales()) {
                redirectAttributes.addFlashAttribute("error", "Los espacios disponibles no pueden ser mayores que los espacios totales.");
                return "redirect:/admin";
            }

            parqueaderoService.actualizarParqueadero(
                    parqueadero.getIdParqueadero(),
                    parqueadero.getNombre(),
                    parqueadero.getDireccion(),
                    parqueadero.getHorario(),
                    parqueadero.getTarifaHora(),
                    parqueadero.getEspaciosTotales(),
                    espacios,
                    parqueadero.getZona().getIdZona(),
                    parqueadero.getUrlMaps()
            );

            redirectAttributes.addFlashAttribute("mensaje", "Espacios actualizados correctamente.");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al actualizar los espacios: " + e.getMessage());
        }

        return "redirect:/admin";
    }

    @PostMapping("/reserva/aceptar/{id}")
    public String aceptarReserva(@PathVariable("id") int idReserva,
                                 RedirectAttributes redirectAttributes) {
        try {
            reservaService.cambiarEstadoReserva(idReserva, "ACEPTADA");
            redirectAttributes.addFlashAttribute("mensaje", "Reserva aceptada correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al aceptar la reserva: " + e.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/reserva/rechazar/{id}")
    public String rechazarReserva(@PathVariable("id") int idReserva,
                                  RedirectAttributes redirectAttributes) {
        try {
            reservaService.cambiarEstadoReserva(idReserva, "RECHAZADA");
            redirectAttributes.addFlashAttribute("mensaje", "Reserva rechazada correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al rechazar la reserva: " + e.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/reserva/eliminar/{id}")
    public String eliminarReserva(@PathVariable("id") int idReserva,
                                  RedirectAttributes redirectAttributes) {
        try {
            reservaService.eliminarReserva(idReserva);
            redirectAttributes.addFlashAttribute("mensaje", "Reserva eliminada correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al eliminar la reserva: " + e.getMessage());
        }
        return "redirect:/admin";
    }
}
