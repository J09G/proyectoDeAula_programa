package com.proyecto.parking.controller;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPCell;
import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.RegistroParqueo;
import com.proyecto.parking.model.Reserva;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.service.ComentarioService;
import com.proyecto.parking.service.ParqueaderoService;
import com.proyecto.parking.service.RegistroParqueoService;
import com.proyecto.parking.service.ReservaService;
import com.proyecto.parking.service.ZonaService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.awt.Color;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired private ComentarioService comentarioService;
    @Autowired private ParqueaderoService parqueaderoService;
    @Autowired private ReservaService reservaService;
    @Autowired private RegistroParqueoService registroParqueoService;
    @Autowired private ZonaService zonaService;

    /* ── Lista de parqueaderos del admin ─────────────────────── */

    @GetMapping("")
    public String listarMisParqueaderos(HttpSession session, Model model) {
        Usuario admin = (Usuario) session.getAttribute("usuario");
        if (admin == null) return "redirect:/login";
        if (!admin.getRol().getNombre().equalsIgnoreCase("Administrador")) return "redirect:/error/403";

        List<Parqueadero> parqueaderos = parqueaderoService.obtenerParqueaderosPorAdministrador(admin.getId());
        model.addAttribute("parqueaderos", parqueaderos);
        model.addAttribute("zonas", zonaService.obtenerZonas());
        return "admin/index";
    }

    /* ── Registrar nuevo parqueadero ─────────────────────────── */

    @PostMapping("/registrarParqueadero")
    public String registrarParqueadero(@RequestParam String nombre,
                                       @RequestParam String direccion,
                                       @RequestParam String horario,
                                       @RequestParam double tarifa,
                                       @RequestParam("espacios_totales") int espaciosTotales,
                                       @RequestParam("espacios_disponibles") int espaciosDisponibles,
                                       @RequestParam("id_zona") String idZona,
                                       @RequestParam(value = "telefono", required = false) String telefono,
                                       @RequestParam(value = "url_maps", required = false) String urlMaps,
                                       HttpSession session,
                                       RedirectAttributes redirectAttributes) {
        try {
            Usuario admin = (Usuario) session.getAttribute("usuario");
            if (admin == null) return "redirect:/login";

            Parqueadero parqueadero = parqueaderoService.registrarParqueadero(
                    nombre, direccion, horario, tarifa,
                    espaciosTotales, espaciosDisponibles,
                    idZona, urlMaps, telefono);

            parqueaderoService.asignarAdministrador(parqueadero.getId(), admin.getId());
            parqueaderoService.cambiarEstado(parqueadero.getId(), true);

            redirectAttributes.addFlashAttribute("mensaje", "Parqueadero registrado correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al registrar el parqueadero: " + e.getMessage());
        }
        return "redirect:/admin";
    }

    /* ── Panel de gestión de un parqueadero específico ───────── */

    @GetMapping("/parqueadero/{id}")
    public String verParqueadero(@PathVariable String id,
                                 HttpSession session,
                                 Model model) {
        Usuario admin = (Usuario) session.getAttribute("usuario");
        if (admin == null) return "redirect:/login";
        if (!admin.getRol().getNombre().equalsIgnoreCase("Administrador")) return "redirect:/error/403";

        try {
            Parqueadero parqueadero = parqueaderoService.obtenerParqueaderoPorId(id);

            if (!model.containsAttribute("reservas")) {
                model.addAttribute("reservas", reservaService.listarReservasParqueadero(id));
            }

            Set<Integer> ocupados = calcularEspaciosOcupados(id);
            Set<Integer> pendientes = calcularEspaciosPendientes(id);

            model.addAttribute("parqueadero", parqueadero);
            model.addAttribute("comentarios", comentarioService.listarPorParqueadero(id));
            model.addAttribute("espaciosOcupados", ocupados);
            model.addAttribute("espaciosPendientes", pendientes);
            model.addAttribute("zonas", zonaService.obtenerZonas());
        } catch (Exception e) {
            model.addAttribute("error", "Error al cargar el parqueadero: " + e.getMessage());
        }
        return "admin/parqueadero";
    }

    /* ── Editar info del parqueadero ─────────────────────────── */

    @PostMapping("/parqueadero/{id}/editar")
    public String editarParqueadero(@PathVariable String id,
                                    @RequestParam String nombre,
                                    @RequestParam String direccion,
                                    @RequestParam String horario,
                                    @RequestParam double tarifa,
                                    @RequestParam(value = "urlMaps", required = false) String urlMaps,
                                    RedirectAttributes redirectAttributes) {
        try {
            Parqueadero parqueadero = parqueaderoService.obtenerParqueaderoPorId(id);
            parqueaderoService.actualizarParqueadero(id, nombre, direccion, horario, tarifa,
                    parqueadero.getEspaciosTotales(), parqueadero.getEspaciosDisponibles(),
                    parqueadero.getZona().getId(), urlMaps);
            redirectAttributes.addFlashAttribute("mensaje", "Información actualizada correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al actualizar: " + e.getMessage());
        }
        return "redirect:/admin/parqueadero/" + id;
    }

    /* ── Actualizar espacios disponibles ─────────────────────── */

    @PostMapping("/parqueadero/{id}/espacios")
    public String actualizarEspacios(@PathVariable String id,
                                     @RequestParam int espaciosTotales,
                                     @RequestParam int espaciosDisponibles,
                                     RedirectAttributes redirectAttributes) {
        try {
            if (espaciosDisponibles > espaciosTotales) {
                redirectAttributes.addFlashAttribute("error",
                        "Los espacios disponibles no pueden superar los totales.");
                return "redirect:/admin/parqueadero/" + id;
            }
            Parqueadero parqueadero = parqueaderoService.obtenerParqueaderoPorId(id);
            parqueaderoService.actualizarParqueadero(id, parqueadero.getNombre(), parqueadero.getDireccion(),
                    parqueadero.getHorario(), parqueadero.getTarifaHora(),
                    espaciosTotales, espaciosDisponibles,
                    parqueadero.getZona().getId(), parqueadero.getUrlMaps());
            redirectAttributes.addFlashAttribute("mensaje", "Espacios actualizados correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al actualizar espacios: " + e.getMessage());
        }
        return "redirect:/admin/parqueadero/" + id;
    }

    @PostMapping("/parqueadero/{id}/estado")
    public String toggleEstado(@PathVariable String id,
                               @RequestParam boolean habilitado,
                               RedirectAttributes redirectAttributes) {
        try {
            parqueaderoService.cambiarEstado(id, habilitado);
            redirectAttributes.addFlashAttribute("mensaje",
                    habilitado ? "Parqueadero habilitado." : "Parqueadero deshabilitado.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al cambiar estado: " + e.getMessage());
        }
        return "redirect:/admin/parqueadero/" + id;
    }

    /* ── Buscar reserva por cédula ───────────────────────────── */

    @GetMapping("/parqueadero/{id}/reservas/buscar")
    public String buscarReservaPorCedula(@PathVariable String id,
                                         @RequestParam String cedula,
                                         RedirectAttributes redirectAttributes) {
        try {
            List<Reserva> reservas = reservaService.buscarReservasPorCedulaYParqueadero(cedula, id);
            if (reservas.isEmpty()) {
                redirectAttributes.addFlashAttribute("error",
                        "No se encontraron reservas para la cédula: " + cedula);
            } else {
                redirectAttributes.addFlashAttribute("reservas", reservas);
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al buscar reservas: " + e.getMessage());
        }
        return "redirect:/admin/parqueadero/" + id;
    }

    /* ── Gestión de reservas ─────────────────────────────────── */

    @PostMapping("/parqueadero/{id}/reserva/aceptar/{rid}")
    public String aceptarReserva(@PathVariable String id, @PathVariable String rid,
                                 RedirectAttributes redirectAttributes) {
        try {
            reservaService.cambiarEstadoReserva(rid, "ACEPTADA");
            redirectAttributes.addFlashAttribute("mensaje", "Reserva aceptada.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al aceptar la reserva: " + e.getMessage());
        }
        return "redirect:/admin/parqueadero/" + id;
    }

    @PostMapping("/parqueadero/{id}/reserva/rechazar/{rid}")
    public String rechazarReserva(@PathVariable String id, @PathVariable String rid,
                                  RedirectAttributes redirectAttributes) {
        try {
            reservaService.cambiarEstadoReserva(rid, "RECHAZADA");
            redirectAttributes.addFlashAttribute("mensaje", "Reserva rechazada.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al rechazar la reserva: " + e.getMessage());
        }
        return "redirect:/admin/parqueadero/" + id;
    }

    @PostMapping("/parqueadero/{id}/reserva/eliminar/{rid}")
    public String eliminarReserva(@PathVariable String id, @PathVariable String rid,
                                  RedirectAttributes redirectAttributes) {
        try {
            reservaService.eliminarReserva(rid);
            redirectAttributes.addFlashAttribute("mensaje", "Reserva eliminada.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al eliminar la reserva: " + e.getMessage());
        }
        return "redirect:/admin/parqueadero/" + id;
    }

    /* ── Registros de parqueo ────────────────────────────────── */

    @GetMapping("/parqueadero/{id}/registros")
    public String mostrarRegistros(@PathVariable String id,
                                   HttpSession session, Model model) {
        Usuario admin = (Usuario) session.getAttribute("usuario");
        if (admin == null) return "redirect:/login";
        if (!admin.getRol().getNombre().equalsIgnoreCase("Administrador")) return "redirect:/error/403";

        try {
            Parqueadero parqueadero = parqueaderoService.obtenerParqueaderoPorId(id);
            Set<Integer> ocupados = calcularEspaciosOcupados(id);
            Set<Integer> pendientes = calcularEspaciosPendientes(id);

            model.addAttribute("parqueadero", parqueadero);
            model.addAttribute("activos", registroParqueoService.listarActivosPorParqueadero(id));
            model.addAttribute("historial", registroParqueoService.listarTodosPorParqueadero(id));
            model.addAttribute("espaciosOcupados", ocupados);
            model.addAttribute("espaciosPendientes", pendientes);
        } catch (Exception e) {
            model.addAttribute("error", "Error al cargar registros: " + e.getMessage());
        }
        return "admin/registros";
    }

    @PostMapping("/parqueadero/{id}/registros/entrada")
    public String registrarEntrada(@PathVariable String id,
                                   @RequestParam String placa,
                                   @RequestParam int espacioReservado,
                                   @RequestParam(value = "cedula", required = false) String cedula,
                                   HttpSession session,
                                   RedirectAttributes redirectAttributes) {
        try {
            Usuario admin = (Usuario) session.getAttribute("usuario");
            if (admin == null) return "redirect:/login";
            registroParqueoService.registrarEntrada(placa, cedula, id, espacioReservado);
            redirectAttributes.addFlashAttribute("mensaje",
                    "Entrada registrada para la placa " + placa.toUpperCase() + ", espacio #" + espacioReservado);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al registrar entrada: " + e.getMessage());
        }
        return "redirect:/admin/parqueadero/" + id;
    }

    @PostMapping("/parqueadero/{id}/registros/salida/{idRegistro}")
    public String registrarSalida(@PathVariable String id,
                                  @PathVariable String idRegistro,
                                  RedirectAttributes redirectAttributes) {
        try {
            RegistroParqueo registro = registroParqueoService.registrarSalida(idRegistro);
            redirectAttributes.addFlashAttribute("mensaje",
                    "Salida registrada. Valor a pagar: $" + registro.getValorPagado());
            redirectAttributes.addFlashAttribute("idFactura", registro.getId());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al registrar salida: " + e.getMessage());
        }
        return "redirect:/admin/parqueadero/" + id + "/registros";
    }

    @GetMapping("/parqueadero/{id}/registros/factura/{idRegistro}")
    public String verFactura(@PathVariable String id,
                             @PathVariable String idRegistro, Model model) {
        try {
            model.addAttribute("registro", registroParqueoService.obtenerPorId(idRegistro));
            model.addAttribute("idParqueadero", id);
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        return "admin/factura";
    }

    @GetMapping("/parqueadero/{id}/registros/factura/pdf/{idRegistro}")
    public void descargarFacturaPdf(@PathVariable String id,
                                    @PathVariable String idRegistro,
                                    HttpServletResponse response) throws IOException {
        RegistroParqueo reg = registroParqueoService.obtenerPorId(idRegistro);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=factura-" + idRegistro + ".pdf");

        Document doc = new Document(PageSize.A5);
        try {
            PdfWriter.getInstance(doc, response.getOutputStream());
            doc.open();

            Font fontTitulo    = new Font(Font.HELVETICA, 18, Font.BOLD);
            Font fontSubtitulo = new Font(Font.HELVETICA, 11, Font.NORMAL, Color.GRAY);
            Font fontLabel     = new Font(Font.HELVETICA, 10, Font.BOLD);
            Font fontValor     = new Font(Font.HELVETICA, 10, Font.NORMAL);
            Font fontTotal     = new Font(Font.HELVETICA, 14, Font.BOLD);

            Paragraph titulo = new Paragraph("ParkingApp", fontTitulo);
            titulo.setAlignment(Element.ALIGN_CENTER);
            doc.add(titulo);

            Paragraph nombre = new Paragraph(reg.getParqueadero().getNombre(), fontSubtitulo);
            nombre.setAlignment(Element.ALIGN_CENTER);
            doc.add(nombre);

            Paragraph direccion = new Paragraph(reg.getParqueadero().getDireccion(), fontSubtitulo);
            direccion.setAlignment(Element.ALIGN_CENTER);
            doc.add(direccion);

            doc.add(new Paragraph(" "));

            Paragraph facturaId = new Paragraph("Factura #" + reg.getId(), fontLabel);
            facturaId.setAlignment(Element.ALIGN_CENTER);
            doc.add(facturaId);

            doc.add(new Paragraph(" "));

            PdfPTable tabla = new PdfPTable(2);
            tabla.setWidthPercentage(100);
            tabla.setWidths(new float[]{40f, 60f});

            agregarFilaTabla(tabla, "Placa:", reg.getPlaca(), fontLabel, fontValor);
            agregarFilaTabla(tabla, "Espacio #:", String.valueOf(reg.getEspacioReservado()), fontLabel, fontValor);
            agregarFilaTabla(tabla, "Cliente:",
                    reg.getUsuario() != null ? reg.getUsuario().getNombre() : "Cliente de paso",
                    fontLabel, fontValor);

            if (reg.getUsuario() != null) {
                agregarFilaTabla(tabla, "Cédula:", reg.getUsuario().getCedula(), fontLabel, fontValor);
            }

            agregarFilaTabla(tabla, "Con reserva:",
                    reg.getReserva() != null ? "Sí - Reserva #" + reg.getReserva().getId() : "No",
                    fontLabel, fontValor);
            agregarFilaTabla(tabla, "Hora de entrada:", reg.getHoraEntrada().format(fmt), fontLabel, fontValor);
            agregarFilaTabla(tabla, "Hora de salida:", reg.getHoraSalida().format(fmt), fontLabel, fontValor);

            long horas = reg.getTiempoMinutos() / 60;
            long mins  = reg.getTiempoMinutos() % 60;
            agregarFilaTabla(tabla, "Tiempo:", horas + "h " + mins + "min", fontLabel, fontValor);
            agregarFilaTabla(tabla, "Tarifa/hora:", "$" + reg.getParqueadero().getTarifaHora(), fontLabel, fontValor);

            doc.add(tabla);
            doc.add(new Paragraph(" "));

            Paragraph total = new Paragraph("TOTAL A PAGAR: $" + reg.getValorPagado(), fontTotal);
            total.setAlignment(Element.ALIGN_RIGHT);
            doc.add(total);

        } finally {
            doc.close();
        }
    }

    /* ── Utilidades: espacios ocupados y pendientes ─────────── */

    private Set<Integer> calcularEspaciosOcupados(String idParqueadero) {
        Set<Integer> ocupados = new HashSet<>();

        reservaService.listarReservasParqueadero(idParqueadero).stream()
                .filter(r -> r.getEstado() == Reserva.EstadoReserva.ACEPTADA)
                .filter(r -> r.getEspacioReservado() != null)
                .map(Reserva::getEspacioReservado)
                .forEach(ocupados::add);

        registroParqueoService.listarActivosPorParqueadero(idParqueadero).stream()
                .filter(r -> r.getEspacioReservado() != null)
                .map(RegistroParqueo::getEspacioReservado)
                .forEach(ocupados::add);

        return ocupados;
    }

    private Set<Integer> calcularEspaciosPendientes(String idParqueadero) {
        Set<Integer> pendientes = new HashSet<>();
        reservaService.listarReservasParqueadero(idParqueadero).stream()
                .filter(r -> r.getEstado() == Reserva.EstadoReserva.PENDIENTE)
                .filter(r -> r.getEspacioReservado() != null)
                .map(Reserva::getEspacioReservado)
                .forEach(pendientes::add);
        return pendientes;
    }

    private void agregarFilaTabla(PdfPTable tabla, String label, String valor,
                                  Font fontLabel, Font fontValor) {
        PdfPCell celdaLabel = new PdfPCell(new Phrase(label, fontLabel));
        celdaLabel.setBorder(Rectangle.BOTTOM);
        celdaLabel.setPadding(6);
        tabla.addCell(celdaLabel);

        PdfPCell celdaValor = new PdfPCell(new Phrase(valor, fontValor));
        celdaValor.setBorder(Rectangle.BOTTOM);
        celdaValor.setPadding(6);
        tabla.addCell(celdaValor);
    }
}
