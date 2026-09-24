package com.proyecto.parking.service.impl;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Pequeñas ayudas para trabajar con {@link Pageable}. */
final class PageableUtils {

    private PageableUtils() {}

    /**
     * Aplica un orden por defecto cuando quien llama no pidió ninguno. Sin esto,
     * MongoDB devuelve los documentos en un orden no garantizado y una misma
     * página puede repetir o saltarse registros entre peticiones.
     */
    static Pageable conOrdenPorDefecto(Pageable pageable, Sort porDefecto) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), porDefecto);
    }
}
