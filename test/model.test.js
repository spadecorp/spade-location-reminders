import {test} from 'node:test';
import assert from 'node:assert/strict';
import {coordinates,reminder,units} from '../src/model.js';
test('zero coordinates, arbitrary points and per-task settings survive',()=>{
 const p=coordinates('0','0');
 assert.deepEqual(reminder('id',' Call ',p,150,true),{id:'id',task:'Call',lat:0,lng:0,radius:150,speak:true});
 assert.equal(reminder('b','Shop',p,3000,false).speak,false);
 assert.equal(units(1250),'1.25 km');
});
test('invalid pins, empty tasks and out-of-range radii are rejected',()=>{
 for(const pair of [['',2],[20,' '],[91,0],[0,181],['bad',0]])assert.throws(()=>coordinates(...pair));
 for(const [task,p,r] of [[' ',{lat:0,lng:0},150],['Task',null,200],['Task',{lat:2,lng:3},99],['Task',{lat:2,lng:3},3001]])assert.throws(()=>reminder('a',task,p,r,true));
});
