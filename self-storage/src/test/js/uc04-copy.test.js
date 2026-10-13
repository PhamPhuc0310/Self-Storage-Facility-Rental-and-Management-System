const test = require('node:test');
const assert = require('node:assert/strict');
const {managedDescription} = require('../../main/resources/static/js/uc04-copy.js');

test('catalog descriptions from managed records remain unchanged when suitable for display', () => {
  assert.equal(managedDescription('Cơ sở gần trục Đào Trí.'), 'Cơ sở gần trục Đào Trí.');
});

test('editorial examples and demo specifications are not rendered as verified facts', () => {
  assert.equal(managedDescription('Ví dụ vận hành: camera ở lối đi.'), 'Vui lòng liên hệ cơ sở để xác nhận thông tin.');
  assert.equal(managedDescription('Kho mát 0–10°C theo thông số demo.'), 'Vui lòng liên hệ cơ sở để xác nhận thông tin.');
  assert.equal(managedDescription(''), 'Thông tin đang được cập nhật.');
});
