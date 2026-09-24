package com.proyecto.parking.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El formulario de registro era el punto más débil: el navegador pedía
 * confirmar la contraseña pero el servidor nunca comparaba las dos, y no había
 * longitud mínima. Estos tests fijan ese contrato.
 */
class RegistroClienteFormTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void abrirValidador() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void cerrarValidador() {
        factory.close();
    }

    private RegistroClienteForm formularioValido() {
        RegistroClienteForm form = new RegistroClienteForm();
        form.setNombre("Ana Pérez");
        form.setCedula("1023456789");
        form.setPlaca("ABC123");
        form.setEmail("ana@test.com");
        form.setPassword("unaClaveSegura1");
        form.setConfirmPassword("unaClaveSegura1");
        return form;
    }

    private Set<String> propiedadesConError(RegistroClienteForm form) {
        return validator.validate(form).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    @DisplayName("un formulario correcto no produce errores")
    void sinErroresCuandoTodoEsCorrecto() {
        assertThat(validator.validate(formularioValido())).isEmpty();
    }

    @Test
    @DisplayName("detecta contraseñas que no coinciden")
    void passwordsDistintas() {
        RegistroClienteForm form = formularioValido();
        form.setConfirmPassword("otraCosaDistinta");

        Set<ConstraintViolation<RegistroClienteForm>> errores = validator.validate(form);

        assertThat(errores).hasSize(1);
        // Los mensajes son claves de i18n. Este validador es el de Jakarta a
        // secas, sin el MessageSource de Spring, así que se comprueba la
        // plantilla del mensaje y no el texto ya traducido.
        assertThat(errores.iterator().next().getMessageTemplate())
                .isEqualTo("{validacion.password.noCoinciden}");
        assertThat(errores.iterator().next().getPropertyPath())
                .hasToString("passwordsCoinciden");
    }

    @Test
    @DisplayName("exige una longitud mínima de contraseña")
    void passwordCorta() {
        RegistroClienteForm form = formularioValido();
        form.setPassword("corta");
        form.setConfirmPassword("corta");

        assertThat(propiedadesConError(form)).contains("password");
    }

    @ParameterizedTest
    @ValueSource(strings = {"AB1", "ABCD1234", "123ABC", "", "   "})
    @DisplayName("rechaza placas con formato inválido")
    void placasInvalidas(String placa) {
        RegistroClienteForm form = formularioValido();
        form.setPlaca(placa);

        assertThat(propiedadesConError(form)).contains("placa");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABC123", "ABC-123", "ABC12D", "abc123"})
    @DisplayName("acepta los formatos de placa habituales")
    void placasValidas(String placa) {
        RegistroClienteForm form = formularioValido();
        form.setPlaca(placa);

        assertThat(propiedadesConError(form)).doesNotContain("placa");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "abcdefgh", "1234567890123"})
    @DisplayName("rechaza cédulas con formato inválido")
    void cedulasInvalidas(String cedula) {
        RegistroClienteForm form = formularioValido();
        form.setCedula(cedula);

        assertThat(propiedadesConError(form)).contains("cedula");
    }

    @Test
    @DisplayName("rechaza un correo mal formado")
    void correoInvalido() {
        RegistroClienteForm form = formularioValido();
        form.setEmail("esto-no-es-un-correo");

        assertThat(propiedadesConError(form)).contains("email");
    }
}
