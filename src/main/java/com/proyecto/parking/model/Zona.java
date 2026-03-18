package com.proyecto.parking.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "zonas")
public class Zona {

    @Id
    @Field("_id")
    private String id;

    private String nombreZona;
    private boolean habilitado = true;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNombreZona() { return nombreZona; }
    public void setNombreZona(String nombreZona) { this.nombreZona = nombreZona; }

    public boolean isHabilitado() { return habilitado; }
    public void setHabilitado(boolean habilitado) { this.habilitado = habilitado; }
}
