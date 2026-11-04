(() => {
  // Existing listing links now lead to the unit inventory with live eligibility.
  const old = new URLSearchParams(location.search);
  const next = new URL('/storage.html', location.origin);
  for (const key of ['typeId', 'facilityId', 'search']) {
    if (old.has(key)) next.searchParams.set(key, old.get(key));
  }
  location.replace(next.pathname + next.search);
})();
