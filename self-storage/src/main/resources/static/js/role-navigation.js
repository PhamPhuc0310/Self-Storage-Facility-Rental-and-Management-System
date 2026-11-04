(() => {
  'use strict';
  const links = {
    CUSTOMER: [['Trang chủ', '/stitch/trang_ch_safebox_storage.html'], ['Tìm kho', '/storage.html'], ['Yêu cầu', '/stitch/y_u_c_u_c_a_t_i_safebox_storage.html'], ['Thanh toán và lịch hẹn', '/stitch/uc11-customer-payments-appointments.html'], ['Kho đang thuê', '/stitch/kho_dang_thue_safebox_storage.html'], ['Gia hạn', '/stitch/gia_han_hop_dong_safebox_storage.html'], ['Trả trước hạn', '/stitch/early-return.html'], ['Hỗ trợ', '/stitch/ho_tro_safebox.html']],
    FACILITY_STAFF: [['Công việc', '/staff-tasks.html'], ['Tiếp nhận trả kho', '/stitch/bien_ban_tra_kho_safebox_manager.html'], ['Ghi nhận số đo', '/staff-measurements.html'], ['Hỗ trợ', '/stitch/ho_tro_safebox.html']],
    FACILITY_MANAGER: [['Tổng quan', '/stitch/t_ng_quan_qu_n_l_safebox_manager.html'], ['Kho và số đo', '/stitch/uc11-manager-catalog.html'], ['Yêu cầu thuê', '/stitch/uc11-manager-approvals.html'], ['Vận hành', '/stitch/uc12-manager-operations.html'], ['Gia hạn', '/stitch/quan_ly_gia_han_safebox_manager.html'], ['Quá hạn', '/stitch/quan_ly_qua_han_safebox_manager.html'], ['Tiếp nhận trả kho', '/stitch/bien_ban_tra_kho_safebox_manager.html'], ['Trả trước hạn', '/stitch/early-return.html'], ['Hỗ trợ', '/stitch/ho_tro_safebox.html'], ['Đề xuất giá', '/management-pricing.html'], ['Chính sách', '/management-policies.html'], ['Báo cáo', '/reports.html']],
    BUSINESS_OPERATIONS_MANAGER: [['Tổng quan', '/role-home.html'], ['Cơ sở', '/management-business.html'], ['Bảng giá', '/management-pricing.html'], ['Chính sách', '/management-policies.html'], ['Báo cáo', '/reports.html']],
    SYSTEM_ADMIN: [['Tổng quan', '/role-home.html'], ['Tài khoản và quyền', '/management-admin.html#users'], ['Nhật ký quản trị', '/management-admin.html#audit']]
  };
  function render(header = document.querySelector('header[data-role-navigation], header.sb-header'), roleOverride) {
    if (!header) return;
    const role = roleOverride || window.SafeBoxAuth?.getCurrentUser()?.role;
    const entries = links[role] || [['Trang chủ', '/stitch/trang_ch_safebox_storage.html'], ['Tìm kho', '/storage.html']];
    header.classList.add('sb-management-header');
    const brand = header.querySelector('.sb-brand, a');
    if (brand) { brand.classList.add('sb-brand'); brand.href = role === 'CUSTOMER' || !role ? '/stitch/trang_ch_safebox_storage.html' : '/role-home.html'; brand.textContent = 'SafeBox Storage'; }
    const nav = header.querySelector('nav') || document.createElement('nav');
    nav.className = 'sb-nav sb-role-nav';
    nav.setAttribute('aria-label', 'Điều hướng theo vai trò');
    nav.replaceChildren();
    for (const [label, href] of entries) {
      const anchor = document.createElement('a');
      anchor.href = href;
      anchor.textContent = label;
      const target = new URL(href, location.origin);
      if (target.pathname === location.pathname && (!target.hash || target.hash === location.hash)) anchor.setAttribute('aria-current', 'page');
      nav.append(anchor);
    }
    if (!nav.isConnected) header.append(nav);
  }
  window.SafeBoxNavigation = { links, render };
  function initialize() {
    render();
    if (window.SafeBoxAuth?.getToken()) {
      window.SafeBoxAuth.authFetch('/api/auth/me').then(response => response.ok ? response.json() : null)
        .then(user => { if (user) render(undefined, user.role); }).catch(() => {});
    }
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initialize, {once:true});
  else initialize();
})();
