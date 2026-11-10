const test = require('node:test');
const assert = require('node:assert/strict');
const {formatDateVN, formatDateTimeVN, parseDateVN, toISODate, formatAppointmentTime, appointmentInstant, appointmentLocalFields, canEditAppointment, appointmentActions} = require('../../main/resources/static/js/uc12-time.js');

test('calendar dates, leap day and midnight conversions are unambiguous in every browser zone', () => {
  const {formatTimeVN}=require('../../main/resources/static/js/uc12-time.js');
  assert.equal(formatDateVN('2026-11-05'),'05/11/2026');
  assert.equal(formatDateVN('2026-05-11'),'11/05/2026');
  assert.equal(formatDateVN('2028-02-29'),'29/02/2028');
  assert.equal(formatDateVN('2026-02-29'),'—');
  assert.equal(formatDateTimeVN('2026-12-31T18:30:00Z'),'01/01/2027 01:30');
  assert.equal(formatDateVN('2026-12-31T18:30:00Z'),'01/01/2027');
  assert.equal(formatDateTimeVN('2028-02-28T18:00:00Z'),'29/02/2028 01:00');
  assert.equal(formatDateTimeVN('2026-11-05T09:10:59.9999999'),'05/11/2026 09:10');
  assert.equal(formatDateTimeVN('05/11/2026 09:10'),'—');
  assert.equal(formatDateTimeVN('2026-11-05T24:00:00Z'),'—');
  assert.equal(formatDateTimeVN('2026-02-30T09:00:00Z'),'—');
  assert.equal(formatTimeVN('2026-12-31T18:30:00Z'),'01:30');
  assert.equal(formatTimeVN('22:10:45'),'22:10');
  assert.equal(parseDateVN('29/02/2028'),'2028-02-29');
  assert.equal(parseDateVN('29/02/2026'),null);
});

test('Vietnam date display preserves day and month order', () => {
  assert.equal(formatDateVN('2026-11-10'), '10/11/2026');
  assert.equal(formatDateVN('2026-11-25'), '25/11/2026');
  assert.equal(formatDateTimeVN('2026-11-25T09:00:00'), '25/11/2026 09:00');
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
  for (const slot of ['06:00', '17:00', '22:00'])
    assert.equal(formatAppointmentTime('2026-11-26', slot), `2026-11-26T${slot}:00+07:00`);
  assert.throws(() => formatAppointmentTime('2026-11-26', '23:00'));
});

test('reschedule fields retain saved Vietnam date and time across offsets and dates', () => {
  assert.deepEqual(appointmentLocalFields('2026-11-25T06:00:00'), {date:'2026-11-25',slot:'06:00'});
  assert.deepEqual(appointmentLocalFields('2026-11-25T17:00:00+07:00'), {date:'2026-11-25',slot:'17:00'});
  assert.deepEqual(appointmentLocalFields('2026-11-25T15:00:00Z'), {date:'2026-11-25',slot:'22:00'});
  assert.deepEqual(appointmentLocalFields('2026-11-25T23:00:00Z'), {date:'2026-11-26',slot:'06:00'});
  assert.equal(appointmentLocalFields('invalid'), null);
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

test('date-time display converts instants to Vietnam and preserves local business time', () => {
  assert.equal(formatDateTimeVN('2026-11-25T23:30:00Z'), '26/11/2026 06:30');
  assert.equal(formatDateTimeVN('2026-11-26T06:30:00+07:00'), '26/11/2026 06:30');
  assert.equal(formatDateTimeVN('2026-11-26T06:30:00'), '26/11/2026 06:30');
  assert.equal(formatDateVN('2026-11-26'), '26/11/2026');
  assert.equal(formatDateTimeVN('bad'), '—');
});
