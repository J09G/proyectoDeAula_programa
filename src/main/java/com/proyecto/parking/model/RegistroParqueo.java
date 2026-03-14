package com.proyecto.parking.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "registro_parqueo")
public class RegistroParqueo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idRegistro")
    private Integer idRegistro;

    @Column(nullable = false, length = 10)
    private String placa;

    @ManyToOne
    @JoinColumn(name = "idUsuario", nullable = true)
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "idParqueadero", nullable = false)
    private Parqueadero parqueadero;

    @ManyToOne
    @JoinColumn(name = "idReserva", nullable = true)
    private Reserva reserva;

    @Column(nullable = false)
    private LocalDateTime horaEntrada;

    @Column(nullable = true)
    private LocalDateTime horaSalida;

    @Column(nullable = true)
    private Long tiempoMinutos;

    @Column(nullable = true)
    private Double valorPagado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoRegistro estado = EstadoRegistro.ACTIVO;

    public enum EstadoRegistro {
        ACTIVO, FINALIZADO
    }

    public Integer getIdRegistro() { return idRegistro; }
    public void setIdRegistro(Integer idRegistro) { this.idRegistro = idRegistro; }

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

    public EstadoRegistro getEstado() { return estado; }
    public void setEstado(EstadoRegistro estado) { this.estado = estado; }
}
