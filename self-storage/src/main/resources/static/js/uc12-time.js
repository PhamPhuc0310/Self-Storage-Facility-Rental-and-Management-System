(function (root) {
  function parseDateVN(value) {
    const match = typeof value === 'string' && /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(value.trim());
    if (!match) return null;
    const day = Number(match[1]), month = Number(match[2]), year = Number(match[3]);
    const check = new Date(Date.UTC(year, month - 1, day));
    if (check.getUTCFullYear() !== year || check.getUTCMonth() + 1 !== month || check.getUTCDate() !== day) return null;
    return `${match[3]}-${match[2]}-${match[1]}`;
  }
  function toISODate(value) {
    const iso = parseDateVN(value);
    if (!iso) throw new Error('Ngày không hợp lệ. Vui lòng nhập DD/MM/YYYY.');
    return iso;
  }
  function formatDateVN(value) {
    if (!value) return '—';
    const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(value));
    return match ? `${match[3]}/${match[2]}/${match[1]}` : '—';
  }
  function formatDateTimeVN(value) {
    if (!value) return '—';
    const match = /^(\d{4}-\d{2}-\d{2})[T ](\d{2}):(\d{2})/.exec(String(value));
    return match ? `${formatDateVN(match[1])} – ${match[2]}:${match[3]}` : '—';
  }
  function todayISODateVN() {
    const parts = Object.fromEntries(new Intl.DateTimeFormat('en-US', {
      timeZone:'Asia/Ho_Chi_Minh', year:'numeric', month:'2-digit', day:'2-digit'
    }).formatToParts(new Date()).filter(part => part.type !== 'literal').map(part => [part.type, part.value]));
    return `${parts.year}-${parts.month}-${parts.day}`;
  }
  function formatAppointmentTime(date, slot) {
    if (typeof date !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(date) ||
        parseDateVN(formatDateVN(date)) !== date ||
        typeof slot !== 'string' || !/^(?:08|09|10|11|13|14|15|16):00$/.test(slot)) {
      throw new Error('Ngày hoặc khung giờ nhận kho không hợp lệ.');
    }
    // Form values are already Vietnam wall-clock time. Do not convert through Date/UTC.
    return `${date}T${slot}:00+07:00`;
  }
  function appointmentInstant(value) {
    if (typeof value !== 'string' ||
        !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,9})?)?(?:Z|[+-]\d{2}:\d{2})?$/.test(value)) return NaN;
    // AppointmentView currently returns LocalDateTime. Treat an offsetless value as Vietnam wall-clock time.
    return Date.parse(/[Zz]|[+-]\d{2}:\d{2}$/.test(value) ? value : `${value}+07:00`);
  }
  function canEditAppointment(appointment, now = Date.now()) {
    return appointment?.status === 'SCHEDULED' && appointmentInstant(appointment.appointmentTime) > now;
  }
  function appointmentActions(appointment, termsVersion, depositRemainingAmount, now = Date.now()) {
    const canCancel = canEditAppointment(appointment, now);
    return {canCancel, canChange: canCancel && !(termsVersion === 'v2' && depositRemainingAmount > 0)};
  }

  const api = Object.freeze({formatDateVN, formatDateTimeVN, parseDateVN, toISODate, todayISODateVN, formatAppointmentTime, appointmentInstant, canEditAppointment, appointmentActions});
  if (typeof module === 'object' && module.exports) module.exports = api;
  if (root) root.SafeBoxUc12Time = api;
})(typeof window === 'undefined' ? globalThis : window);
