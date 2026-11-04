(() => {
  'use strict';
  const pages = {
    CUSTOMER: {
      title: 'Không gian của tôi',
      intro: 'Theo dõi yêu cầu thuê, hợp đồng, thanh toán và hỗ trợ.',
      links: [
        ['Tìm kho', '/storage.html'],
        ['Yêu cầu thuê', '/stitch/y_u_c_u_c_a_t_i_safebox_storage.html'],
        ['Hợp đồng', '/stitch/kho_dang_thue_safebox_storage.html'],
        ['Thanh toán và lịch hẹn', '/stitch/uc11-customer-payments-appointments.html'],
        ['Gia hạn', '/stitch/gia_han_hop_dong_safebox_storage.html'],
        ['Trả kho trước hạn', '/stitch/early-return.html'],
        ['Hỗ trợ', '/stitch/ho_tro_safebox.html']
      ]
    },
    FACILITY_STAFF: {
      title: 'Công việc tại cơ sở',
      intro: 'Xem công việc và ghi nhận số đo trong phạm vi được phân công.',
      links: [['Công việc', '/staff-tasks.html'], ['Ghi nhận số đo', '/staff-measurements.html'],
        ['Hỗ trợ khách hàng', '/stitch/ho_tro_safebox.html']]
    },
    FACILITY_MANAGER: {
      title: 'Quản lý cơ sở',
      intro: 'Theo dõi kho, xét duyệt yêu cầu và vận hành cơ sở được phân công.',
      links: [['Tổng quan', '/stitch/t_ng_quan_qu_n_l_safebox_manager.html'],
        ['Kho và số đo', '/stitch/uc11-manager-catalog.html'], ['Yêu cầu thuê', '/stitch/uc11-manager-approvals.html'],
        ['Vận hành', '/stitch/uc12-manager-operations.html'], ['Trả kho trước hạn', '/stitch/early-return.html'], ['Đề xuất giá', '/management-pricing.html'],
        ['Chính sách', '/management-policies.html'], ['Báo cáo', '/reports.html']]
    },
    BUSINESS_OPERATIONS_MANAGER: {
      title: 'Quản lý kinh doanh',
      intro: 'Quản lý cơ sở và xét duyệt đề xuất trong phạm vi kinh doanh được giao.',
      links: [['Cơ sở', '/management-business.html'], ['Bảng giá', '/management-pricing.html'],
        ['Chính sách', '/management-policies.html'], ['Báo cáo', '/reports.html']]
    },
    SYSTEM_ADMIN: {
      title: 'Quản trị hệ thống',
      intro: 'Quản lý tài khoản, phân quyền và nhật ký quản trị.',
      links: [['Tài khoản và quyền', '/management-admin.html#users'],
        ['Nhật ký quản trị', '/management-admin.html#audit']]
    }
  };
  SafeBoxAuth.requireAuth().then(ok => {
    if (!ok) return;
    const user = SafeBoxAuth.getCurrentUser();
    const page = pages[user?.role];
    if (!page) return;
    const header = document.querySelector('header');
    header.className = 'sb-header';
    header.replaceChildren();
    const brand = document.createElement('a');
    brand.className = 'sb-brand';
    brand.href = '/stitch/trang_ch_safebox_storage.html';
    brand.textContent = 'SafeBox Storage';
    const nav = document.createElement('nav');
    nav.className = 'sb-nav';
    nav.setAttribute('aria-label', 'Điều hướng');
    const home = document.createElement('a');
    home.href = '/role-home.html';
    home.textContent = 'Không gian làm việc';
    home.setAttribute('aria-current', 'page');
    nav.append(home);
    const logout = document.createElement('button');
    logout.type = 'button';
    logout.className = 'sb-button sb-secondary';
    logout.textContent = 'Đăng xuất';
    logout.addEventListener('click', () => SafeBoxAuth.logout());
    header.append(brand, nav, logout);
    window.SafeBoxNavigation?.render(header);

    const main = document.querySelector('main');
    main.className = 'sb-container';
    main.replaceChildren();
    const hero = document.createElement('section');
    hero.className = 'sb-hero';
    const eyebrow = document.createElement('p');
    eyebrow.className = 'sb-eyebrow';
    eyebrow.textContent = user.fullName || user.email || 'SafeBox';
    const title = document.createElement('h1');
    title.textContent = page.title;
    const intro = document.createElement('p');
    intro.textContent = page.intro;
    hero.append(eyebrow, title, intro);
    const grid = document.createElement('section');
    grid.className = 'sb-cards';
    grid.setAttribute('aria-label', page.title);
    for (const [label, href] of page.links) {
      const card = document.createElement('a');
      card.className = 'sb-card';
      card.href = href;
      card.style.textDecoration = 'none';
      card.style.color = 'inherit';
      const heading = document.createElement('h2');
      heading.textContent = label;
      heading.style.margin = '0';
      card.append(heading);
      grid.append(card);
    }
    main.append(hero, grid);
  });
})();
