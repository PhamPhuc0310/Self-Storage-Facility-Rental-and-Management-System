(function (root) {
  function validDate(value) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
    const [year, month, day] = value.split('-').map(Number);
    const check = new Date(0);
    check.setUTCFullYear(year, month - 1, day);
    return year > 0 && check.getUTCFullYear() === year && check.getUTCMonth() + 1 === month && check.getUTCDate() === day;
  }
  function parseDateVN(value) {
    const match = typeof value === 'string' && /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(value.trim());
    if (!match) return null;
    const iso = `${match[3]}-${match[2]}-${match[1]}`;
    return validDate(iso) ? iso : null;
  }
  function toISODate(value) {
    const iso = parseDateVN(value);
    if (!iso) throw new Error('Ngày không hợp lệ. Vui lòng nhập DD/MM/YYYY.');
    return iso;
  }
  function formatDateVN(value) {
    if (!value) return '—';
    const text = String(value);
    const date = validDate(text) ? text : appointmentLocalFields(text)?.date;
    const match = date && /^(\d{4})-(\d{2})-(\d{2})$/.exec(date);
    return match ? `${match[3]}/${match[2]}/${match[1]}` : '—';
  }
  function formatTimeVN(value) {
    if (typeof value === 'string' && /^(?:[01]\d|2[0-3]):[0-5]\d(?::[0-5]\d(?:\.\d+)?)?$/.test(value)) return value.slice(0, 5);
    return appointmentLocalFields(value)?.slot || '—';
  }
  function formatDateTimeVN(value) {
    if (!value) return '—';
    const fields = appointmentLocalFields(String(value).replace(' ', 'T'));
    return fields ? `${formatDateVN(fields.date)} ${fields.slot}` : '—';
  }
  function todayISODateVN() {
    const parts = Object.fromEntries(new Intl.DateTimeFormat('vi-VN', {
      timeZone:'Asia/Ho_Chi_Minh', year:'numeric', month:'2-digit', day:'2-digit'
    }).formatToParts(new Date()).filter(part => part.type !== 'literal').map(part => [part.type, part.value]));
    return `${parts.year}-${parts.month}-${parts.day}`;
  }
  function formatAppointmentTime(date, slot) {
    if (typeof date !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(date) ||
        parseDateVN(formatDateVN(date)) !== date ||
        typeof slot !== 'string' || !/^(?:0[6-9]|1[0-9]|2[0-2]):00$/.test(slot)) {
      throw new Error('Ngày hoặc khung giờ nhận kho không hợp lệ.');
    }
    // Form values are already Vietnam wall-clock time. Do not convert through Date/UTC.
    return `${date}T${slot}:00+07:00`;
  }
  function appointmentInstant(value) {
    if (typeof value !== 'string') return NaN;
    value = value.replace(' ', 'T');
    if (
        !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,9})?)?(?:Z|[+-]\d{2}:\d{2})?$/.test(value)) return NaN;
    if (!validDate(value.slice(0, 10)) || !/^(?:[01]\d|2[0-3]):[0-5]\d(?::[0-5]\d)?$/.test(value.slice(11).split(/[Z+.-]/)[0])) return NaN;
    // Business APIs serialize Vietnam LocalDateTime without an offset; auth-token UTC fields are internal only.
    return Date.parse(/[Zz]|[+-]\d{2}:\d{2}$/.test(value) ? value : `${value}+07:00`);
  }
  function appointmentLocalFields(value) {
    const instant = appointmentInstant(value);
    if (!Number.isFinite(instant)) return null;
    const parts = Object.fromEntries(new Intl.DateTimeFormat('vi-VN', {
      timeZone:'Asia/Ho_Chi_Minh', year:'numeric', month:'2-digit', day:'2-digit',
      hour:'2-digit', minute:'2-digit', hourCycle:'h23'
    }).formatToParts(new Date(instant)).filter(part => part.type !== 'literal').map(part => [part.type, part.value]));
    return {date:`${parts.year}-${parts.month}-${parts.day}`, slot:`${parts.hour}:${parts.minute}`};
  }
  function canEditAppointment(appointment, now = Date.now()) {
    return appointment?.status === 'SCHEDULED' && appointmentInstant(appointment.appointmentTime) > now;
  }
  function appointmentActions(appointment, termsVersion, depositRemainingAmount, now = Date.now()) {
    const canCancel = canEditAppointment(appointment, now);
    return {canCancel, canChange: canCancel && !(termsVersion === 'v2' && depositRemainingAmount > 0)};
  }

  const api = Object.freeze({formatDateVN, formatDateTimeVN, formatTimeVN, parseDateVN, toISODate, todayISODateVN, formatAppointmentTime, appointmentInstant, appointmentLocalFields, canEditAppointment, appointmentActions});
  if (typeof module === 'object' && module.exports) module.exports = api;
  if (root) root.SafeBoxUc12Time = api;
})(typeof window === 'undefined' ? globalThis : window);
