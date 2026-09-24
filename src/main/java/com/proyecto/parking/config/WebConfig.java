package com.proyecto.parking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Configuración de MVC: selección de idioma.
 *
 * <p>El idioma se cambia añadiendo {@code ?lang=en} a cualquier URL y se guarda
 * en una cookie, así que la elección sobrevive a la navegación y al cierre del
 * navegador sin necesidad de tocar la sesión ni la base de datos.</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** Idiomas que la aplicación traduce. El primero es el de partida. */
    public static final List<Locale> IDIOMAS = List.of(
            Locale.forLanguageTag("es"),
            Locale.forLanguageTag("en"));

    public static final String PARAMETRO_IDIOMA = "lang";

    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver("PARKING_LANG");
        resolver.setDefaultLocale(IDIOMAS.get(0));
        resolver.setCookieMaxAge(Duration.ofDays(365));
        resolver.setCookieHttpOnly(false);
        resolver.setCookiePath("/");
        return resolver;
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName(PARAMETRO_IDIOMA);
        // Un ?lang=xx desconocido no debe reventar la petición: se ignora y se
        // mantiene el idioma actual.
        interceptor.setIgnoreInvalidLocale(true);
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }
}
