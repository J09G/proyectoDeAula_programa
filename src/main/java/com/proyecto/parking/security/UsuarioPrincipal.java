package com.proyecto.parking.security;

import com.proyecto.parking.model.Usuario;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Identidad del usuario autenticado.
 *
 * <p>Sustituye al antiguo {@code session.setAttribute("usuario", usuario)}, que
 * guardaba la entidad completa —incluido el hash de la contraseña— y quedaba
 * congelada: si el superadmin deshabilitaba a alguien o le cambiaba el rol, la
 * sesión seguía viendo los datos viejos. Aquí sólo viajan los datos mínimos y
 * {@link EstadoCuentaFilter} los revalida en cada petición.</p>
 */
public class UsuarioPrincipal implements UserDetails {

    private final String id;
    private final String correo;
    private final String contrasena;
    private final String nombre;
    private final String rol;
    private final boolean habilitado;

    public UsuarioPrincipal(Usuario usuario) {
        this.id = usuario.getId();
        this.correo = usuario.getCorreo();
        this.contrasena = usuario.getContrasena();
        this.nombre = usuario.getNombre();
        this.rol = usuario.getRol() != null ? usuario.getRol().getNombre() : null;
        this.habilitado = usuario.isHabilitado();
    }

    public String getId() { return id; }

    public String getNombre() { return nombre; }

    public String getCorreo() { return correo; }

    /** Nombre del rol tal y como está en la base de datos (p. ej. "Administrador"). */
    public String getRol() { return rol; }

    /** Authority de Spring Security (p. ej. "ROLE_ADMINISTRADOR"). */
    public String getAuthority() {
        return rol == null ? "ROLE_ANONIMO" : "ROLE_" + rol.toUpperCase();
    }

    public boolean tieneRol(String nombreRol) {
        return rol != null && rol.equalsIgnoreCase(nombreRol);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(getAuthority()));
    }

    @Override
    public String getPassword() { return contrasena; }

    @Override
    public String getUsername() { return correo; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return habilitado; }
}
