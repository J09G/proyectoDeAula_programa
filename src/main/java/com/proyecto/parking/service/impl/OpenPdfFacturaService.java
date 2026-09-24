package com.proyecto.parking.service.impl;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.proyecto.parking.exception.ReglaNegocioException;
import com.proyecto.parking.model.RegistroParqueo;
import com.proyecto.parking.service.FacturaPdfService;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.OutputStream;
import java.time.format.DateTimeFormatter;

/**
 * Implementación con OpenPDF.
 *
 * <p>Vivía dentro de {@code AdminController}: ochenta líneas de maquetación
 * mezcladas con la lógica HTTP. Aquí se puede probar por separado y el
 * controlador queda en tres líneas.</p>
 */
@Service
public class OpenPdfFacturaService implements FacturaPdfService {

    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Font TITULO = new Font(Font.HELVETICA, 18, Font.BOLD);
    private static final Font SUBTITULO = new Font(Font.HELVETICA, 11, Font.NORMAL, Color.GRAY);
    private static final Font ETIQUETA = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Font VALOR = new Font(Font.HELVETICA, 10, Font.NORMAL);
    private static final Font TOTAL = new Font(Font.HELVETICA, 14, Font.BOLD);

    @Override
    public void generar(RegistroParqueo registro, OutputStream destino) {
        if (registro.getHoraSalida() == null || registro.getValorPagado() == null) {
            throw new ReglaNegocioException(
                    "Todavía no se puede facturar: el vehículo no ha registrado su salida.");
        }

        Document documento = new Document(PageSize.A5);
        try {
            PdfWriter.getInstance(documento, destino);
            documento.open();

            escribirCabecera(documento, registro);
            documento.add(new Paragraph(" "));
            documento.add(construirTabla(registro));
            documento.add(new Paragraph(" "));
            escribirTotal(documento, registro);

        } finally {
            documento.close();
        }
    }

    private void escribirCabecera(Document documento, RegistroParqueo registro) {
        documento.add(centrado("ParkingApp", TITULO));
        documento.add(centrado(registro.getParqueadero().getNombre(), SUBTITULO));
        documento.add(centrado(registro.getParqueadero().getDireccion(), SUBTITULO));
        documento.add(new Paragraph(" "));
        documento.add(centrado("Factura #" + registro.getId(), ETIQUETA));
    }

    private PdfPTable construirTabla(RegistroParqueo registro) {
        PdfPTable tabla = new PdfPTable(2);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[]{40f, 60f});

        agregarFila(tabla, "Placa:", registro.getPlaca());
        agregarFila(tabla, "Espacio #:", String.valueOf(registro.getEspacioReservado()));
        agregarFila(tabla, "Cliente:",
                registro.getUsuario() != null ? registro.getUsuario().getNombre() : "Cliente de paso");

        if (registro.getUsuario() != null) {
            agregarFila(tabla, "Cédula:", registro.getUsuario().getCedula());
        }

        agregarFila(tabla, "Con reserva:",
                registro.getReserva() != null
                        ? "Sí - Reserva #" + registro.getReserva().getId()
                        : "No");
        agregarFila(tabla, "Hora de entrada:", registro.getHoraEntrada().format(FORMATO_FECHA));
        agregarFila(tabla, "Hora de salida:", registro.getHoraSalida().format(FORMATO_FECHA));
        agregarFila(tabla, "Tiempo:", formatearDuracion(registro.getTiempoMinutos()));
        agregarFila(tabla, "Tarifa/hora:", "$" + tarifaDe(registro));

        return tabla;
    }

    private void escribirTotal(Document documento, RegistroParqueo registro) {
        Paragraph total = new Paragraph("TOTAL A PAGAR: $" + registro.getValorPagado(), TOTAL);
        total.setAlignment(Element.ALIGN_RIGHT);
        documento.add(total);
    }

    private Paragraph centrado(String texto, Font fuente) {
        Paragraph parrafo = new Paragraph(texto != null ? texto : "", fuente);
        parrafo.setAlignment(Element.ALIGN_CENTER);
        return parrafo;
    }

    private void agregarFila(PdfPTable tabla, String etiqueta, String valor) {
        tabla.addCell(celda(etiqueta, ETIQUETA));
        tabla.addCell(celda(valor != null ? valor : "-", VALOR));
    }

    private PdfPCell celda(String texto, Font fuente) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, fuente));
        celda.setBorder(Rectangle.BOTTOM);
        celda.setPadding(6);
        return celda;
    }

    private String formatearDuracion(Long minutos) {
        if (minutos == null) {
            return "-";
        }
        return (minutos / 60) + "h " + (minutos % 60) + "min";
    }

    private Object tarifaDe(RegistroParqueo registro) {
        // Se muestra la tarifa congelada al entrar, que es la que se cobró.
        return registro.getTarifaHoraAplicada() != null
                ? registro.getTarifaHoraAplicada()
                : registro.getParqueadero().getTarifaHora();
    }
}
