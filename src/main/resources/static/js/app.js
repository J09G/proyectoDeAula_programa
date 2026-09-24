/* ============================================================================
   ParkingApp — comportamiento común
   ----------------------------------------------------------------------------
   Sin dependencias: menú de navegación en móvil, tema y cambio de idioma.
   El tema se APLICA en un script en línea dentro del <head> (fragments/layout)
   para que no haya destello al cargar; aquí sólo se alterna.
   ========================================================================== */

(function () {
  'use strict';

  /* ── Menú de navegación en móvil ─────────────────────────────────────── */
  var toggle = document.getElementById('navToggle');
  var links  = document.getElementById('navLinks');

  if (toggle && links) {
    toggle.addEventListener('click', function () {
      var abierto = links.classList.toggle('abierto');
      toggle.setAttribute('aria-expanded', abierto ? 'true' : 'false');
    });
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') { links.classList.remove('abierto'); }
    });
    // Un clic fuera cierra el menú: en móvil es lo que se espera.
    document.addEventListener('click', function (e) {
      if (!links.contains(e.target) && !toggle.contains(e.target)) {
        links.classList.remove('abierto');
      }
    });
  }

  /* ── Tema claro / oscuro ─────────────────────────────────────────────── */
  var CLAVE_TEMA = 'parking-tema';

  function temaActual() {
    var explicito = document.documentElement.getAttribute('data-theme');
    if (explicito) { return explicito; }
    return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches
      ? 'dark' : 'light';
  }

  var btnTema = document.getElementById('btnTema');
  if (btnTema) {
    btnTema.addEventListener('click', function () {
      var nuevo = temaActual() === 'dark' ? 'light' : 'dark';
      document.documentElement.setAttribute('data-theme', nuevo);
      try {
        localStorage.setItem(CLAVE_TEMA, nuevo);
      } catch (e) { /* sin almacenamiento el tema dura lo que la pestaña */ }
    });
  }

  /* ── Cambio de idioma ────────────────────────────────────────────────── */
  /* Se reconstruye la URL actual con ?lang=xx conservando los demás parámetros
     (página, filtros de búsqueda) para no perder el contexto. */
  var enlacesIdioma = document.querySelectorAll('.lang-switch a[data-lang]');
  Array.prototype.forEach.call(enlacesIdioma, function (enlace) {
    var url = new URL(window.location.href);
    url.searchParams.set('lang', enlace.getAttribute('data-lang'));
    enlace.setAttribute('href', url.pathname + url.search);
  });

  /* ── Paginación: "Ir a la página" ────────────────────────────────────── */
  /* El usuario escribe la página en base 1; la URL la espera en base 0. Un
     número fuera de rango se lleva al extremo más cercano en vez de dar una
     página vacía. */
  Array.prototype.forEach.call(document.querySelectorAll('form.pag-ir'), function (form) {
    form.addEventListener('submit', function (e) {
      e.preventDefault();
      var total = parseInt(form.getAttribute('data-total'), 10) || 1;
      var n = parseInt(form.querySelector('input').value, 10);
      if (isNaN(n)) { return; }
      n = Math.min(Math.max(n, 1), total);
      window.location.href = form.getAttribute('data-base') + (n - 1) + (form.getAttribute('data-params') || '');
    });
  });

  /* ── Desplazamiento a una sección vía ?scroll=id ─────────────────────── */
  /* Lo usa el enlace "Mis reservas" de la barra, que apunta a la misma página
     que "Zonas" pero a otra sección. */
  var destino = new URL(window.location.href).searchParams.get('scroll');
  if (destino) {
    var seccion = document.getElementById(destino);
    if (seccion) { seccion.scrollIntoView({ behavior: 'smooth', block: 'start' }); }
  }

})();
