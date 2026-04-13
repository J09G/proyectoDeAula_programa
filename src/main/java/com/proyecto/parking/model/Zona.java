package com.proyecto.parking.model;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Document(collection = "zonas")
public class Zona {

    @MongoId(FieldType.OBJECT_ID)
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
