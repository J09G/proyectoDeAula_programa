package com.proyecto.parking.model;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;

/**
 * Enlace de recuperación de contraseña pendiente de usar.
 *
 * <p>Solo se guarda el hash SHA-256 del token, nunca el token: quien lea la
 * base de datos ve huellas, no enlaces que funcionen. El token en claro solo
 * existe en el correo que recibe el usuario.</p>
 */
@Document(collection = "tokens_recuperacion")
public class TokenRecuperacion {

    @MongoId(FieldType.OBJECT_ID)
    private String id;

    @Indexed(unique = true)
    private String hashToken;

    @Indexed
    private String idUsuario;

    private Instant creadoEn;

    /**
     * Índice TTL: Mongo borra el documento solo cuando pasa esta fecha. Su
     * limpieza corre cada ~60 s, así que el servicio también compara la fecha
     * y no confía solo en que el documento ya no exista.
     */
    @Indexed(expireAfter = "0s")
    private Instant expiraEn;

    public TokenRecuperacion() {}

    public TokenRecuperacion(String hashToken, String idUsuario, Instant creadoEn, Instant expiraEn) {
        this.hashToken = hashToken;
        this.idUsuario = idUsuario;
        this.creadoEn = creadoEn;
        this.expiraEn = expiraEn;
    }

    public String getId() { return id; }

    public String getHashToken() { return hashToken; }

    public String getIdUsuario() { return idUsuario; }

    public Instant getCreadoEn() { return creadoEn; }

    public Instant getExpiraEn() { return expiraEn; }

    public boolean estaVencido(Instant ahora) {
        return !ahora.isBefore(expiraEn);
    }
}
