(function () {
  var sidebar  = document.getElementById('sidebar');
  var overlay  = document.getElementById('sbOverlay');
  var hamburger = document.getElementById('sbHamburger');

  if (!sidebar || !overlay || !hamburger) return;

  function openSidebar() {
    sidebar.classList.add('open');
    overlay.classList.add('open');
  }
  function closeSidebar() {
    sidebar.classList.remove('open');
    overlay.classList.remove('open');
  }

  hamburger.addEventListener('click', openSidebar);
  overlay.addEventListener('click', closeSidebar);
})();
