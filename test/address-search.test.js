import {test} from 'node:test';
import assert from 'node:assert/strict';
import {suggestions} from '../src/address-search.js';

test('addresses include the street number and country and use Leaflet coordinate order', () => {
  const house = {
    geometry: {type: 'Point', coordinates: [-79.384, 43.6536]},
    properties: {name: 'Queen Street West', housenumber: '100', street: 'Queen Street West', city: 'Toronto', country: 'Canada'}
  };
  assert.deepEqual(suggestions([null, house, house, {...house, geometry: {type:'Point', coordinates:[43, 170]}}]), [
    {label: '100 Queen Street West, Toronto, Canada', lat: 43.6536, lng: -79.384}
  ]);
});
