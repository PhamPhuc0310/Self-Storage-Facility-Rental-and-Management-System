(function (root) {
  function managedDescription(value) {
    const text = String(value || '').trim();
    // Older catalog records include example copy. Do not present it as a verified facility fact.
    return /\b(?:demo|mock)\b|mô phỏng|ví dụ vận hành/i.test(text)
      ? 'Vui lòng liên hệ cơ sở để xác nhận thông tin.' : text || 'Thông tin đang được cập nhật.';
  }
  if (typeof module === 'object' && module.exports) module.exports = {managedDescription};
  if (root) root.SafeBoxCatalogCopy = {managedDescription};
})(typeof window === 'undefined' ? globalThis : window);
