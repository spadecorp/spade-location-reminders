import {Capacitor, registerPlugin} from '@capacitor/core';
import {Geolocation} from '@capacitor/geolocation';
import L from 'leaflet';
import {initAddressSearch} from './address-search.js';
import 'leaflet/dist/leaflet.css';
import './style.css';
import {coordinates, reminder, units} from './model.js';

const el = id => document.getElementById(id);
const native = Capacitor.isNativePlatform();
const plugin = registerPlugin('Reminders');
const storageKey = 'spade.reminder.preview.v1';
const records = () => JSON.parse(localStorage.getItem(storageKey) || '[]');
const service = native ? plugin : {
  list: async () => ({items: records()}),
  save: async item => localStorage.setItem(storageKey, JSON.stringify([...records().filter(row => row.id !== item.id), item])),
  remove: async ({id}) => localStorage.setItem(storageKey, JSON.stringify(records().filter(row => row.id !== id))),
};
let map, pin, ring, point, editing, busy = false;
const isAndroid = Capacitor.getPlatform() === 'android';
el('preview').hidden = native;
el('disclosure').textContent = native ? (isAndroid
  ? 'To alert you with the app closed, allow notifications and precise location, then select “Allow all the time” in app settings. Tasks and pins stay on your phone.'
  : 'Allow notifications and location while using the app. iOS monitors the selected area after you close the app.') : '';
let selectedAddress = '', locationRequest = 0;
const dismissAddressSearch = initAddressSearch(el('address'), el('addressResults'), el('addressStatus'),
  ({lat, lng, label}) => {
    choose(lat, lng, true, label);
    el('pinInfo').focus({preventScroll: true});
  });
el('address').addEventListener('input', () => { locationRequest++; });
function renderMap() {
  if (map) return;
  map = L.map('map', {worldCopyJump:true}).setView([20, 0], 2);
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom:19, attribution:'&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a> contributors'
  }).on('tileerror', () => {
    el('mapStatus').hidden = false;
  }).on('tileload', () => {
    el('mapStatus').hidden = true;
  }).addTo(map);
  map.on('click', e => choose(e.latlng.lat, map.wrapLatLng(e.latlng).lng, map.getZoom() < 12));
}
function choose(lat, lng, center=false, address='') {
  locationRequest++;
  dismissAddressSearch();
  selectedAddress = address;
  el('address').value = address;
  point = coordinates(lat, lng);
  const pos = [point.lat, point.lng];
  if (!pin) {
    ring = L.circle(pos,{radius:+el('radius').value,color:'#8c6800',weight:2,fillColor:'#ffc90e',fillOpacity:.23,interactive:false}).addTo(map);
    pin = L.marker(pos,{interactive:false,icon:L.divIcon({className:'pin',iconSize:[16,16]})}).addTo(map);
  }
  pin.setLatLng(pos);
  ring.setLatLng(pos).setRadius(+el('radius').value);
  el('lat').value = point.lat.toFixed(6); el('lng').value = point.lng.toFixed(6);
  el('pinInfo').textContent = el('lat').value + ', ' + el('lng').value;
  if (center) map.fitBounds(ring.getBounds(),{maxZoom:16,padding:[20,20]});
}
function button(label, fn) {
  const b = document.createElement('button'); b.className='link'; b.textContent=label; b.onclick=fn; return b;
}
async function home() {
  dismissAddressSearch();
  locationRequest++;
  el('editor').hidden=true; el('home').hidden=false;
  try {
    const {items, warning} = await service.list();
    el('health').textContent=warning || '';
    const list = el('list'); list.replaceChildren();
    if (!items.length) {
      const section=document.createElement('article');section.className='empty';
      section.innerHTML='<h2>No reminders yet</h2><p>Add a task and drop a pin anywhere you want to be reminded.</p>';list.append(section);
    }
    for (const item of items) {
      const card=document.createElement('article'); card.className='card';
      const title=document.createElement('h2'); title.textContent=item.task;
      const meta=document.createElement('div');meta.className='meta';
      meta.textContent=units(item.radius)+' radius · '+(item.speak?'Voice on':'Notification')+' · '+(item.address || (item.lat.toFixed(4)+', '+item.lng.toFixed(4)));
      const actions=document.createElement('div');actions.className='actions';
      actions.append(button('Edit',()=>edit(item)),button('Complete',async e=>{
        e.currentTarget.disabled=true;
        try {await service.remove({id:item.id});await home();} catch(err){await home();el('health').textContent=err.message;}
      }));
      card.append(title,meta,actions); list.append(card);
    }
  } catch(err) {el('health').textContent=err.message;}
}
function newId() {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID();
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 15) | 64;
  bytes[8] = (bytes[8] & 63) | 128;
  const hex = Array.from(bytes, byte => byte.toString(16).padStart(2,'0')).join('');
  return [hex.slice(0,8),hex.slice(8,12),hex.slice(12,16),hex.slice(16,20),hex.slice(20)].join('-');
}
function edit(item) {
  dismissAddressSearch(); selectedAddress=''; locationRequest++;
  editing=item?.id || newId(); point=null;
  el('home').hidden=true;el('editor').hidden=false;
  el('form').reset();el('coords').open=false;
  el('heading').textContent=item?'Edit reminder':'New reminder';
  el('message').textContent='';el('settings').hidden=true;
  el('task').value=item?.task || '';el('radius').value=item?.radius ?? 300;
  el('distance').value=units(+el('radius').value);el('speak').checked=item?.speak ?? false;
  el('pinInfo').textContent='Tap anywhere on the map to place your pin.';
  renderMap();map.invalidateSize();
  if(pin){pin.remove();ring.remove();pin=ring=null;}
  if(item)choose(item.lat,item.lng,true,item.address);else map.setView([20,0],2);
  window.scrollTo(0,0);
}
el('new').onclick=()=>edit(null);
el('back').onclick=()=>{if(!busy)home();};
el('settings').onclick=async()=>{try {await plugin.settings();} catch(err){el('message').textContent=err.message;}};
el('apply').onclick=()=>{try{const p=coordinates(el('lat').value,el('lng').value);choose(p.lat,p.lng,true);el('message').textContent='';}catch(err){el('message').textContent=err.message;}};
el('near').onclick=async()=>{
  const request=++locationRequest;el('near').disabled=true;
  try {
    const location=await Geolocation.getCurrentPosition({enableHighAccuracy:true,timeout:15000});
    if(request===locationRequest && !el('editor').hidden)choose(location.coords.latitude,location.coords.longitude,true);
  } catch(err) {if(request===locationRequest)el('message').textContent='Current location unavailable. Tap the map or enter coordinates.';}
  finally {el('near').disabled=false;}
};
el('radius').oninput=()=>{el('distance').value=units(+el('radius').value);ring?.setRadius(+el('radius').value);};
el('form').onsubmit=async e=>{
  e.preventDefault();if(busy)return;
  busy=true;el('save').disabled=true;el('back').disabled=true;el('settings').hidden=true;el('message').textContent='';
  try {
    if (el('address').value.trim() && el('address').value.trim() !== selectedAddress) {
      el('address').focus();
      throw Error('Choose a suggested address or clear the search to use the map pin.');
    }
    const item=reminder(editing,el('task').value,point,el('radius').value,el('speak').checked);
    if (selectedAddress && el('address').value.trim()) item.address = selectedAddress;
    if(native) {
      let permission=await Geolocation.checkPermissions();
      if(permission.location!=='granted') permission=await Geolocation.requestPermissions({permissions:['location']});
      if(permission.location!=='granted') throw Error('Allow precise location in settings, then save again.');
    }
    await service.save(item);await home();
  } catch(err){el('message').textContent=err.message || 'Could not save this reminder.';el('settings').hidden=!native || !/permission|settings|allow/i.test(el('message').textContent);}
  finally{busy=false;el('save').disabled=false;el('back').disabled=false;}
};
document.addEventListener('visibilitychange',()=>{if(!document.hidden && !el('home').hidden)home();});
home();
