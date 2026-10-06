/* ==========================================================================
   Mini-mapa de la configuración del parqueadero (US-15).

   El administrador hace clic sobre la entrada del parqueadero; el punto se
   copia a los campos ocultos latitud/longitud del formulario y se habilita
   "Guardar ubicación". Leaflet se sirve desde la propia app (js/leaflet), así
   la CSP no tiene que abrir script-src a ningún CDN.
   ========================================================================== */
(function () {
  'use strict';

  var contenedor = document.getElementById('mapa-ubicacion');
  if (!contenedor || typeof L === 'undefined') return;

  // Centro de Cartagena para los parqueaderos que aún no tienen ubicación.
  var CARTAGENA = [10.3910, -75.4794];

  var lat = parseFloat(contenedor.getAttribute('data-lat'));
  var lng = parseFloat(contenedor.getAttribute('data-lng'));
  var tieneUbicacion = !isNaN(lat) && !isNaN(lng);

  var mapa = L.map(contenedor).setView(tieneUbicacion ? [lat, lng] : CARTAGENA, tieneUbicacion ? 17 : 13);

  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    // La app manda "Referrer-Policy: same-origin", que deja a OpenStreetMap sin
    // saber qué sitio pide las imágenes; su política de uso lo exige. Aquí se
    // envía sólo el dominio, nunca la ruta.
    referrerPolicy: 'strict-origin-when-cross-origin',
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
  }).addTo(mapa);

  var marcador = tieneUbicacion ? L.marker([lat, lng]).addTo(mapa) : null;

  var campoLatitud = document.getElementById('ubicacion-latitud');
  var campoLongitud = document.getElementById('ubicacion-longitud');
  var botonGuardar = document.getElementById('ubicacion-guardar');

  mapa.on('click', function (evento) {
    var punto = evento.latlng;
    if (marcador) {
      marcador.setLatLng(punto);
    } else {
      marcador = L.marker(punto).addTo(mapa);
    }
    // Seis decimales: unos 10 cm, de sobra para ubicar una entrada.
    campoLatitud.value = punto.lat.toFixed(6);
    campoLongitud.value = punto.lng.toFixed(6);
    botonGuardar.disabled = false;
  });
})();
