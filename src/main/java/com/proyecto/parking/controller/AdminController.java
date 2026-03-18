package com.proyecto.parking.controller;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPCell;
import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.RegistroParqueo;
import com.proyecto.parking.model.Reserva;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.service.ParqueaderoService;
import com.proyecto.parking.service.RegistroParqueoService;
import com.proyecto.parking.service.ReservaService;
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
import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private ParqueaderoService parqueaderoService;

    @Autowired
    private ReservaService reservaService;

    @Autowired
    private RegistroParqueoService registroParqueoService;

    @GetMapping("")
    public String mostrarPanelAdmin(HttpSession session, Model model) {
        try {
            Usuario admin = (Usuario) session.getAttribute("usuario");

            if (admin == null) return "redirect:/login";
            if (!admin.getRol().getNombre().equalsIgnoreCase("Administrador")) return "redirect:/error/403";

            Parqueadero parqueadero = parqueaderoService.obtenerParqueaderoPorAdministrador(admin.getId());
            if (parqueadero == null) {
                model.addAttribute("error", "No tienes parqueadero asignado aún.");
                return "admin/index";
            }

            if (!model.containsAttribute("reservas")) {
                List<Reserva> reservas = reservaService.listarReservasParqueadero(parqueadero.getId());
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
            if (admin == null) return "redirect:/login";
            if (!admin.getRol().getNombre().equalsIgnoreCase("Administrador")) return "redirect:/error/403";

            Parqueadero parqueadero = parqueaderoService.obtenerParqueaderoPorAdministrador(admin.getId());
            if (parqueadero == null) {
                redirectAttributes.addFlashAttribute("error", "No tienes parqueadero asignado.");
                return "redirect:/admin";
            }

            List<Reserva> reservas = reservaService.buscarReservasPorCedulaYParqueadero(cedula, parqueadero.getId());
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
            Parqueadero parqueadero = parqueaderoService.obtenerParqueaderoPorAdministrador(admin.getId());

            parqueaderoService.actualizarParqueadero(
                    parqueadero.getId(),
                    nombre,
                    direccion,
                    horario,
                    tarifa,
                    parqueadero.getEspaciosTotales(),
                    parqueadero.getEspaciosDisponibles(),
                    parqueadero.getZona().getId(),
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
            Parqueadero parqueadero = parqueaderoService.obtenerParqueaderoPorAdministrador(admin.getId());

            if (espacios > parqueadero.getEspaciosTotales()) {
                redirectAttributes.addFlashAttribute("error", "Los espacios disponibles no pueden ser mayores que los espacios totales.");
                return "redirect:/admin";
            }

            parqueaderoService.actualizarParqueadero(
                    parqueadero.getId(),
                    parqueadero.getNombre(),
                    parqueadero.getDireccion(),
                    parqueadero.getHorario(),
                    parqueadero.getTarifaHora(),
                    parqueadero.getEspaciosTotales(),
                    espacios,
                    parqueadero.getZona().getId(),
                    parqueadero.getUrlMaps()
            );

            redirectAttributes.addFlashAttribute("mensaje", "Espacios actualizados correctamente.");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al actualizar los espacios: " + e.getMessage());
        }

        return "redirect:/admin";
    }

    @PostMapping("/reserva/aceptar/{id}")
    public String aceptarReserva(@PathVariable("id") String idReserva,
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
    public String rechazarReserva(@PathVariable("id") String idReserva,
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
    public String eliminarReserva(@PathVariable("id") String idReserva,
                                  RedirectAttributes redirectAttributes) {
        try {
            reservaService.eliminarReserva(idReserva);
            redirectAttributes.addFlashAttribute("mensaje", "Reserva eliminada correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al eliminar la reserva: " + e.getMessage());
        }
        return "redirect:/admin";
    }

    @GetMapping("/registros")
    public String mostrarRegistros(HttpSession session, Model model) {
        try {
            Usuario admin = (Usuario) session.getAttribute("usuario");
            if (admin == null) return "redirect:/login";
            if (!admin.getRol().getNombre().equalsIgnoreCase("Administrador")) return "redirect:/error/403";

            Parqueadero parqueadero = parqueaderoService.obtenerParqueaderoPorAdministrador(admin.getId());
            if (parqueadero == null) {
                model.addAttribute("error", "No tienes parqueadero asignado.");
                return "admin/registros";
            }

            model.addAttribute("parqueadero", parqueadero);
            model.addAttribute("activos", registroParqueoService.listarActivosPorParqueadero(parqueadero.getId()));
            model.addAttribute("historial", registroParqueoService.listarTodosPorParqueadero(parqueadero.getId()));
        } catch (Exception e) {
            model.addAttribute("error", "Error al cargar registros: " + e.getMessage());
        }
        return "admin/registros";
    }

    @PostMapping("/registros/entrada")
    public String registrarEntrada(@RequestParam String placa,
                                   HttpSession session,
                                   RedirectAttributes redirectAttributes) {
        try {
            Usuario admin = (Usuario) session.getAttribute("usuario");
            if (admin == null) return "redirect:/login";
            if (!admin.getRol().getNombre().equalsIgnoreCase("Administrador")) return "redirect:/error/403";

            Parqueadero parqueadero = parqueaderoService.obtenerParqueaderoPorAdministrador(admin.getId());
            registroParqueoService.registrarEntrada(placa, null, parqueadero.getId());
            redirectAttributes.addFlashAttribute("mensaje", "Entrada registrada correctamente para la placa " + placa.toUpperCase());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al registrar entrada: " + e.getMessage());
        }
        return "redirect:/admin/registros";
    }

    @PostMapping("/registros/salida/{idRegistro}")
    public String registrarSalida(@PathVariable String idRegistro,
                                  RedirectAttributes redirectAttributes) {
        try {
            RegistroParqueo registro = registroParqueoService.registrarSalida(idRegistro);
            redirectAttributes.addFlashAttribute("mensaje", "Salida registrada. Valor a pagar: $" + registro.getValorPagado());
            redirectAttributes.addFlashAttribute("idFactura", registro.getId());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al registrar salida: " + e.getMessage());
        }
        return "redirect:/admin/registros";
    }

    @GetMapping("/registros/factura/{idRegistro}")
    public String verFactura(@PathVariable String idRegistro, Model model) {
        try {
            RegistroParqueo registro = registroParqueoService.obtenerPorId(idRegistro);
            model.addAttribute("registro", registro);
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        return "admin/factura";
    }

    @GetMapping("/registros/factura/pdf/{idRegistro}")
    public void descargarFacturaPdf(@PathVariable String idRegistro, HttpServletResponse response) throws IOException {
        RegistroParqueo reg = registroParqueoService.obtenerPorId(idRegistro);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=factura-" + idRegistro + ".pdf");

        Document doc = new Document(PageSize.A5);
        try {
            PdfWriter.getInstance(doc, response.getOutputStream());
            doc.open();

            Font fontTitulo = new Font(Font.HELVETICA, 18, Font.BOLD);
            Font fontSubtitulo = new Font(Font.HELVETICA, 11, Font.NORMAL, Color.GRAY);
            Font fontLabel = new Font(Font.HELVETICA, 10, Font.BOLD);
            Font fontValor = new Font(Font.HELVETICA, 10, Font.NORMAL);
            Font fontTotal = new Font(Font.HELVETICA, 14, Font.BOLD);

            Paragraph titulo = new Paragraph("ParkingApp", fontTitulo);
            titulo.setAlignment(Element.ALIGN_CENTER);
            doc.add(titulo);

            Paragraph parqueaderoNombre = new Paragraph(reg.getParqueadero().getNombre(), fontSubtitulo);
            parqueaderoNombre.setAlignment(Element.ALIGN_CENTER);
            doc.add(parqueaderoNombre);

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
            long mins = reg.getTiempoMinutos() % 60;
            agregarFilaTabla(tabla, "Tiempo de parqueo:", horas + "h " + mins + "min", fontLabel, fontValor);
            agregarFilaTabla(tabla, "Tarifa por hora:", "$" + reg.getParqueadero().getTarifaHora(), fontLabel, fontValor);

            doc.add(tabla);
            doc.add(new Paragraph(" "));

            Paragraph total = new Paragraph("TOTAL A PAGAR: $" + reg.getValorPagado(), fontTotal);
            total.setAlignment(Element.ALIGN_RIGHT);
            doc.add(total);

        } finally {
            doc.close();
        }
    }

    private void agregarFilaTabla(PdfPTable tabla, String label, String valor, Font fontLabel, Font fontValor) {
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
