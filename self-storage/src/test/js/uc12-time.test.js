const test = require('node:test');
const assert = require('node:assert/strict');
const {formatDateVN, formatDateTimeVN, parseDateVN, toISODate, formatAppointmentTime, appointmentInstant, canEditAppointment, appointmentActions} = require('../../main/resources/static/js/uc12-time.js');

test('Vietnam date display preserves day and month order', () => {
  assert.equal(formatDateVN('2026-11-10'), '10/11/2026');
  assert.equal(formatDateVN('2026-11-25'), '25/11/2026');
  assert.equal(formatDateTimeVN('2026-11-25T09:00:00'), '25/11/2026 – 09:00');
});

test('Vietnam date input is parsed strictly before sending ISO to APIs', () => {
  assert.equal(parseDateVN('03/04/2026'), '2026-04-03');
  assert.equal(toISODate('31/12/2026'), '2026-12-31');
  assert.equal(parseDateVN('31/02/2026'), null);
  assert.throws(() => toISODate('31/02/2026'));
});

test('booking and rescheduling preserve Vietnam wall-clock time and +07:00 offset', () => {
  const appointmentTime = formatAppointmentTime('2026-11-25', '09:00');
  assert.equal(appointmentTime, '2026-11-25T09:00:00+07:00');
  assert.equal(JSON.stringify({appointmentTime}), '{"appointmentTime":"2026-11-25T09:00:00+07:00"}');
  assert.equal(formatAppointmentTime('2026-11-26', '16:00'), '2026-11-26T16:00:00+07:00');
});

test('invalid form values cannot send an offsetless or UTC time', () => {
  assert.throws(() => formatAppointmentTime('2026-11-25', '09:00Z'));
  assert.throws(() => formatAppointmentTime('', '09:00'));
});

test('appointment editability parses Vietnam time exactly once with or without an API offset', () => {
  const now = Date.parse('2026-11-12T00:00:00Z');
  const explicit = '2026-11-12T08:00:00+07:00';
  const local = '2026-11-12T08:00:00';
  const fractional = '2026-11-12T08:00:00.123+07:00';
  assert.equal(appointmentInstant(explicit), Date.parse(explicit));
  assert.equal(appointmentInstant(local), Date.parse(explicit));
  assert.equal(appointmentInstant(fractional), Date.parse(fractional));
  assert.equal(canEditAppointment({status:'SCHEDULED', appointmentTime:explicit}, now), true);
  assert.equal(canEditAppointment({status:'SCHEDULED', appointmentTime:local}, now), true);
  assert.equal(canEditAppointment({status:'SCHEDULED', appointmentTime:fractional}, now), true);
  assert.equal(canEditAppointment({status:'SCHEDULED', appointmentTime:explicit}, Date.parse(explicit)), false);
  assert.equal(canEditAppointment({status:'CANCELLED', appointmentTime:explicit}, now), false);
  assert.equal(canEditAppointment({status:'SCHEDULED', appointmentTime:'invalid'}, now), false);
});

test('a scheduled appointment remains cancellable after refund while rescheduling waits for full deposit', () => {
  const appointment = {status:'SCHEDULED', appointmentTime:'2026-11-12T08:00:00+07:00'};
  const now = Date.parse('2026-11-12T00:00:00Z');
  assert.deepEqual(appointmentActions(appointment, 'v2', 1, now), {canCancel:true, canChange:false});
  assert.deepEqual(appointmentActions(appointment, 'v2', 0, now), {canCancel:true, canChange:true});
  assert.deepEqual(appointmentActions({...appointment, status:'CANCELLED'}, 'v2', 0, now), {canCancel:false, canChange:false});
});
