(() => {
  'use strict';
  const phone = {
    digits: '02873005585',
    display: '028 7300 5585',
    href: 'tel:02873005585'
  };

  function render(root = document) {
    root.querySelectorAll('[data-support-phone]').forEach(node => {
      if (node.textContent !== phone.display) node.textContent = phone.display;
    });
    root.querySelectorAll('[data-support-link]').forEach(node => {
      if (node.getAttribute('href') !== phone.href) node.href = phone.href;
      if (!node.querySelector('[data-support-phone]') && !node.textContent.trim()) node.textContent = phone.display;
    });
  }

  function start() {
    render();
    new MutationObserver(records => {
      if (records.some(record => [...record.addedNodes].some(node => node.nodeType === 1 &&
        (node.matches('[data-support-phone], [data-support-link]') || node.querySelector('[data-support-phone], [data-support-link]'))))) render();
    }).observe(document.body, {childList: true, subtree: true});
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start, {once: true});
  else start();
  window.SafeBoxSupport = Object.freeze({...phone, render});
})();
