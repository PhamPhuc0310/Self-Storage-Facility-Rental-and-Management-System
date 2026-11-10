const test=require('node:test'),assert=require('node:assert/strict');
const {label}=require('../../main/resources/static/js/overdue-state.js');
test('overdue labels reflect persisted charges and real policy states',()=>{
  assert.equal(label({graceDays:null}),'Chưa có chính sách phí áp dụng');
  assert.equal(label({graceDays:3,billableDays:0}),'Trong thời gian ân hạn');
  assert.equal(label({graceDays:0,billableDays:2}),'Phí chưa được tính');
  assert.equal(label({totalPenaltyDebt:100}),'Đã tính phí · Chưa thanh toán đủ');
  assert.equal(label({penalties:[{status:'PENDING_APPROVAL'}]}),'Chờ xác nhận thanh toán');
  assert.equal(label({graceDays:0,billableDays:2,totalPenaltyPaid:100,penalties:[{status:'PAID',periodDate:'2026-11-05'}]},'2026-11-05'),'Đã thanh toán phí');
  assert.equal(label({graceDays:0,billableDays:2,totalPenaltyPaid:100,penalties:[{status:'PAID',periodDate:'2026-11-04'}]},'2026-11-05'),'Chưa tính phí kỳ mới');
});
