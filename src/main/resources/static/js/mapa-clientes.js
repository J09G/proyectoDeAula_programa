/* ==========================================================================
   Mapa de parqueaderos del cliente (US-15).

   Los datos no llegan como JSON: se leen de la lista que Thymeleaf ya pintó
   (cliente/mapa.html). Cada <li> trae data-lat/data-lng, y su ficha se copia
   con cloneNode al popup del marcador. Como nunca se arma HTML con texto,
   un nombre de parqueadero no puede inyectar código en la página.
   ========================================================================== */
(function () {
  'use strict';

  var contenedor = document.getElementById('mapa-clientes');
  var items = document.querySelectorAll('#mapa-lista .mapa-item');
  if (!contenedor || !items.length || typeof L === 'undefined') return;

  var mapa = L.map(contenedor);

  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    // Mismo motivo que en mapa-ubicacion.js: OpenStreetMap pide saber qué
    // sitio usa sus teselas; se envía sólo el dominio.
    referrerPolicy: 'strict-origin-when-cross-origin',
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
  }).addTo(mapa);

  var puntos = [];

  Array.prototype.forEach.call(items, function (item) {
    var lat = parseFloat(item.getAttribute('data-lat'));
    var lng = parseFloat(item.getAttribute('data-lng'));
    if (isNaN(lat) || isNaN(lng)) return;

    var ficha = item.querySelector('.mapa-ficha').cloneNode(true);
    var marcador = L.marker([lat, lng]).addTo(mapa).bindPopup(ficha);
    puntos.push([lat, lng]);

    // Tocar un parqueadero de la lista lleva a su marcador y abre su ficha.
    function enfocar() {
      mapa.setView([lat, lng], Math.max(mapa.getZoom(), 16));
      marcador.openPopup();
      contenedor.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }
    item.addEventListener('click', function (evento) {
      if (evento.target.closest('a')) return; // el botón Reservar navega solo
      enfocar();
    });
    item.addEventListener('keydown', function (evento) {
      if (evento.key === 'Enter' || evento.key === ' ') {
        evento.preventDefault();
        enfocar();
      }
    });
  });

  // Encuadra todos los parqueaderos; con uno solo, un zoom de calle.
  if (puntos.length === 1) {
    mapa.setView(puntos[0], 16);
  } else {
    mapa.fitBounds(puntos, { padding: [40, 40] });
  }
})();
