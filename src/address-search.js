export function suggestions(features) {
  const matches = [];
  for (const feature of features) {
    if (feature?.geometry?.type !== 'Point') continue;
    const [lng, lat] = feature.geometry.coordinates ?? [];
    if (!Number.isFinite(lat) || Math.abs(lat) > 90 || !Number.isFinite(lng) || Math.abs(lng) > 180) continue;
    const p = feature.properties ?? {};
    const street = [p.housenumber, p.street].filter(Boolean).join(' ');
    const parts = [p.name === p.street && street ? '' : p.name, street, p.city || p.district || p.locality, p.state, p.postcode, p.country];
    const label = [...new Set(parts.filter(Boolean))].join(', ');
    if (label && !matches.some(m => m.label === label && m.lat === lat && m.lng === lng)) {
      matches.push({label, lat, lng});
    }
  }
  return matches;
}

export function initAddressSearch(input, list, status, onSelect) {
  let timer, pending;
  function dismiss() {
    clearTimeout(timer);
    pending?.abort();
    pending = null;
    list.replaceChildren();
    status.textContent = '';
  }
  async function search(query) {
    const controller = new AbortController();
    pending = controller;
    const timeout = setTimeout(() => controller.abort(), 10000);
    status.textContent = 'Searching…';
    try {
      const url = new URL('https://photon.komoot.io/api/');
      url.search = new URLSearchParams({q: query, limit: '5'}).toString();
      const response = await fetch(url, {signal: controller.signal, credentials: 'omit', referrerPolicy: 'no-referrer'});
      if (!response.ok) throw new Error('Address lookup failed');
      const data = await response.json();
      if (pending !== controller) return;
      const matches = suggestions(data.features);
      for (const match of matches) {
        const button = document.createElement('button');
        button.type = 'button';
        button.textContent = match.label;
        button.onclick = () => {
          onSelect(match);
          status.textContent = 'Pin placed at ' + match.label;
        };
        list.append(button);
      }
      status.textContent = matches.length ? 'Choose a matching address.' : 'No matches. Add a city or postal code, or tap the map.';
    } catch {
      if (pending === controller) status.textContent = 'Address search is unavailable. Try again, tap the map, or enter coordinates.';
    } finally {
      clearTimeout(timeout);
      if (pending === controller) pending = null;
    }
  }
  function schedule() {
    dismiss();
    const query = input.value.trim();
    if (query.length >= 3) timer = setTimeout(() => search(query), 550);
  }
  input.addEventListener('input', schedule);
  input.addEventListener('keydown', event => {
    if (event.key === 'Escape') dismiss();
    if (event.key === 'ArrowDown' || event.key === 'Enter') {
      event.preventDefault();
      if (list.firstElementChild) list.firstElementChild.focus();
      else schedule();
    }
  });
  list.addEventListener('keydown', event => {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      const next = event.key === 'ArrowDown' ? event.target.nextElementSibling : event.target.previousElementSibling;
      (next || input).focus();
    } else if (event.key === 'Escape') {
      dismiss();
      input.focus();
    }
  });
  return dismiss;
}
