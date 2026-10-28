/*
 * Ocupación estimada por el modelo predictivo.
 *
 * Cada bloque [data-prediccion] indica el parqueadero y el campo de fecha del
 * que depende (datetime-local o date). Al cambiar la fecha se piden al
 * servidor las franjas del día y se dibujan como barras.
 */
(function () {
  'use strict';

  function dibujar(bloque, datos, horaElegida) {
    var barras = bloque.querySelector('[data-barras]');
    var resumen = bloque.querySelector('[data-resumen]');
    var nota = bloque.querySelector('[data-nota]');
    barras.textContent = '';

    datos.franjas.forEach(function (f) {
      var columna = document.createElement('div');
      columna.className = 'prediccion-columna nivel-' + f.nivel.toLowerCase();
      if (f.hora === horaElegida) { columna.classList.add('elegida'); }
      columna.title = f.hora + ':00 · ' + f.porcentaje + ' %';

      var barra = document.createElement('span');
      barra.className = 'prediccion-barra';
      barra.style.height = Math.max(4, f.porcentaje) + '%';

      var hora = document.createElement('span');
      hora.className = 'prediccion-hora';
      hora.textContent = f.hora;

      columna.appendChild(barra);
      columna.appendChild(hora);
      barras.appendChild(columna);
    });

    if (resumen) {
      var franja = null;
      datos.franjas.forEach(function (f) { if (f.hora === horaElegida) { franja = f; } });
      if (franja === null) {
        // Sin hora elegida (panel del administrador): se resalta la franja más llena.
        franja = datos.franjas.reduce(function (a, b) { return b.porcentaje > a.porcentaje ? b : a; });
        resumen.textContent = bloque.dataset.txtPico
          .replace('{hora}', franja.hora + ':00').replace('{pct}', franja.porcentaje);
      } else {
        resumen.textContent = bloque.dataset.txtFranja
          .replace('{hora}', franja.hora + ':00').replace('{pct}', franja.porcentaje)
          .replace('{nivel}', bloque.dataset['txt' + franja.nivel.charAt(0) + franja.nivel.slice(1).toLowerCase()]);
      }
      resumen.className = 'prediccion-resumen nivel-' + franja.nivel.toLowerCase();
    }
    if (nota) { nota.hidden = datos.conHistorial; }
    bloque.hidden = false;
  }

  function actualizar(bloque, campo) {
    var valor = campo.value;                       // 2026-10-13T18:00 o 2026-10-13
    if (!valor) { bloque.hidden = true; return; }
    var dia = valor.slice(0, 10);
    var horaElegida = valor.length > 10 ? parseInt(valor.slice(11, 13), 10) : -1;
    if (horaElegida !== -1) {
      horaElegida = Math.max(6, Math.min(22, horaElegida));
    }

    fetch(bloque.dataset.url + '?idParqueadero=' + encodeURIComponent(bloque.dataset.parqueadero)
        + '&fecha=' + encodeURIComponent(dia), { credentials: 'same-origin', headers: { 'Accept': 'application/json' } })
      .then(function (r) { return r.ok ? r.json() : null; })
      .then(function (datos) {
        if (datos && datos.franjas && datos.franjas.length) { dibujar(bloque, datos, horaElegida); }
        else { bloque.hidden = true; }
      })
      .catch(function () { bloque.hidden = true; });
  }

  document.querySelectorAll('[data-prediccion]').forEach(function (bloque) {
    var campo = document.getElementById(bloque.dataset.campo);
    if (!campo) { return; }
    campo.addEventListener('change', function () { actualizar(bloque, campo); });
    actualizar(bloque, campo);
  });
})();
