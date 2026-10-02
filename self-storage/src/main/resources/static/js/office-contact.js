(() => {
  const offices = Object.freeze({
    head: 'Trụ sở chính: 7 Đ. D1, Tăng Nhơn Phú, Hồ Chí Minh 700000, Việt Nam',
    branch: 'Chi nhánh Bình Dương: phường Thái Hòa, thành phố Tân Uyên'
  });
  document.querySelectorAll('footer span').forEach(span => {
    const text = span.textContent.trim();
    if (text.startsWith('Trụ sở')) span.textContent = offices.head;
    if (text.startsWith('Chi nhánh')) span.textContent = offices.branch;
  });
  document.querySelectorAll('[data-office-map]').forEach(link => {
    const address = link.dataset.officeMap === 'branch' ? offices.branch : offices.head;
    link.href = `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(address.replace(/^[^:]+:\s*/, ''))}`;
  });
})();
