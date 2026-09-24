package com.proyecto.parking.model;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.LocalDateTime;

@Document(collection = "registros_parqueo")
@CompoundIndexes({
        @CompoundIndex(name = "idx_parqueadero_estado", def = "{'parqueadero': 1, 'estado': 1}"),
        @CompoundIndex(name = "idx_placa_estado", def = "{'placa': 1, 'estado': 1}"),
        @CompoundIndex(name = "idx_usuario_estado", def = "{'usuario': 1, 'estado': 1}")
})
public class RegistroParqueo {

    @MongoId(FieldType.OBJECT_ID)
    private String id;

    private String placa;
    private Integer espacioReservado;

    @DBRef(lazy = true)
    private Usuario usuario;

    @DBRef(lazy = true)
    private Parqueadero parqueadero;

    @DBRef(lazy = true)
    private Reserva reserva;

    private LocalDateTime horaEntrada;
    private LocalDateTime horaSalida;
    private Long tiempoMinutos;
    private Double valorPagado;

    /**
     * Tarifa por hora vigente en el momento de la entrada. Se congela aquí para
     * que un cambio de tarifa del administrador no altere facturas ya emitidas
     * ni el cobro de vehículos que ya estaban dentro.
     */
    private Double tarifaHoraAplicada;

    private EstadoRegistro estado = EstadoRegistro.ACTIVO;

    public enum EstadoRegistro {
        ACTIVO, FINALIZADO
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPlaca() { return placa; }
    public void setPlaca(String placa) { this.placa = placa; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public Parqueadero getParqueadero() { return parqueadero; }
    public void setParqueadero(Parqueadero parqueadero) { this.parqueadero = parqueadero; }

    public Reserva getReserva() { return reserva; }
    public void setReserva(Reserva reserva) { this.reserva = reserva; }

    public LocalDateTime getHoraEntrada() { return horaEntrada; }
    public void setHoraEntrada(LocalDateTime horaEntrada) { this.horaEntrada = horaEntrada; }

    public LocalDateTime getHoraSalida() { return horaSalida; }
    public void setHoraSalida(LocalDateTime horaSalida) { this.horaSalida = horaSalida; }

    public Long getTiempoMinutos() { return tiempoMinutos; }
    public void setTiempoMinutos(Long tiempoMinutos) { this.tiempoMinutos = tiempoMinutos; }

    public Double getValorPagado() { return valorPagado; }
    public void setValorPagado(Double valorPagado) { this.valorPagado = valorPagado; }

    public Double getTarifaHoraAplicada() { return tarifaHoraAplicada; }
    public void setTarifaHoraAplicada(Double tarifaHoraAplicada) { this.tarifaHoraAplicada = tarifaHoraAplicada; }

    public Integer getEspacioReservado() { return espacioReservado; }
    public void setEspacioReservado(Integer espacioReservado) { this.espacioReservado = espacioReservado; }

    public EstadoRegistro getEstado() { return estado; }
    public void setEstado(EstadoRegistro estado) { this.estado = estado; }
}
