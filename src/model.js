export function coordinates(lat, lng) {
  if ([lat, lng].some(v => v == null || String(v).trim() === '')) throw Error('Enter both coordinates.');
  const point = {lat: Number(lat), lng: Number(lng)};
  if (!Number.isFinite(point.lat) || !Number.isFinite(point.lng) || Math.abs(point.lat) > 90 || Math.abs(point.lng) > 180) throw Error('Check latitude (−90 to 90) and longitude (−180 to 180).');
  return point;
}
export function reminder(id, task, point, radius, speak) {
  task = task.trim();
  if (!task || task.length > 120) throw Error('Enter a task of 1–120 characters.');
  if (!point) throw Error('Place a pin on the map or enter coordinates.');
  radius = Number(radius);
  if (!Number.isInteger(radius) || radius < 100 || radius > 3000) throw Error('Choose a radius between 100 m and 3 km.');
  return {id, task, ...coordinates(point.lat, point.lng), radius, speak: Boolean(speak)};
}
export function units(meters) {
  return meters >= 1000 ? (meters / 1000) + ' km' : meters + ' m';
}
