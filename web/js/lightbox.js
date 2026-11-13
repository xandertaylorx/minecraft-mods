// Gallery lightbox: click a screenshot to view it large, ✕ / Esc / click outside to close,
// arrows (or swipe on phones) to flip through. Works on every .gallery on the page.
(function () {
	var links = Array.prototype.slice.call(document.querySelectorAll('.gallery a'));
	if (!links.length) return;

	var box = document.createElement('div');
	box.className = 'lightbox';
	box.setAttribute('role', 'dialog');
	box.setAttribute('aria-modal', 'true');
	box.setAttribute('aria-hidden', 'true');
	box.innerHTML =
		'<button class="lb-close" aria-label="Close">✕</button>' +
		'<button class="lb-prev" aria-label="Previous">‹</button>' +
		'<figure><img alt=""><figcaption></figcaption></figure>' +
		'<button class="lb-next" aria-label="Next">›</button>';
	document.body.appendChild(box);

	var img = box.querySelector('img');
	var cap = box.querySelector('figcaption');
	var index = 0;
	var lastFocus = null;

	function show(i) {
		index = (i + links.length) % links.length;
		var a = links[index];
		var thumb = a.querySelector('img');
		img.src = a.getAttribute('href');
		img.alt = thumb ? thumb.alt : '';
		cap.textContent = (thumb && thumb.alt ? thumb.alt + '  ·  ' : '') + (index + 1) + ' / ' + links.length;
	}

	function open(i) {
		lastFocus = document.activeElement;
		show(i);
		box.classList.add('open');
		box.setAttribute('aria-hidden', 'false');
		document.documentElement.style.overflow = 'hidden';
		box.querySelector('.lb-close').focus();
	}

	function close() {
		box.classList.remove('open');
		box.setAttribute('aria-hidden', 'true');
		document.documentElement.style.overflow = '';
		img.removeAttribute('src');
		if (lastFocus) lastFocus.focus();
	}

	links.forEach(function (a, i) {
		a.addEventListener('click', function (e) {
			e.preventDefault();
			open(i);
		});
	});

	box.querySelector('.lb-close').addEventListener('click', close);
	box.querySelector('.lb-prev').addEventListener('click', function (e) { e.stopPropagation(); show(index - 1); });
	box.querySelector('.lb-next').addEventListener('click', function (e) { e.stopPropagation(); show(index + 1); });
	box.addEventListener('click', function (e) {
		if (e.target === box || e.target.tagName === 'FIGURE') close();
	});
	document.addEventListener('keydown', function (e) {
		if (!box.classList.contains('open')) return;
		if (e.key === 'Escape') close();
		else if (e.key === 'ArrowLeft') show(index - 1);
		else if (e.key === 'ArrowRight') show(index + 1);
	});

	// Swipe left/right on touch screens
	var startX = null;
	box.addEventListener('touchstart', function (e) { startX = e.touches[0].clientX; }, { passive: true });
	box.addEventListener('touchend', function (e) {
		if (startX === null) return;
		var dx = e.changedTouches[0].clientX - startX;
		if (Math.abs(dx) > 50) show(index + (dx < 0 ? 1 : -1));
		startX = null;
	});
})();
