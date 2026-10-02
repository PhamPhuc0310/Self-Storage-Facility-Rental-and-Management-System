document.addEventListener('DOMContentLoaded', () => {
    loadFacilityDetail();
});

let currentFacilityId = null;
let currentTypeId = null;
let baseMonthlyPrice = 0;

async function loadFacilityDetail() {
    const urlParams = new URLSearchParams(window.location.search);
    currentFacilityId = urlParams.get('facilityId');
    currentTypeId = urlParams.get('typeId');

    if (!currentFacilityId || !currentTypeId) {
        // Fallback to real database ID since /api/facilities is only on UC02 branch
        currentFacilityId = '355AA102-BB0C-4252-ABF7-63BDD2EADB96';
        currentTypeId = '2';
        
        const newUrl = new URL(window.location);
        newUrl.searchParams.set('facilityId', currentFacilityId);
        newUrl.searchParams.set('typeId', currentTypeId);
        window.history.pushState({}, '', newUrl);
    }

    try {
        const response = await fetch('/api/facilities/' + currentFacilityId);
        if (!response.ok) throw new Error('Network response was not ok');
        const facility = await response.json();
        
        const unitType = facility.unitTypes.find(u => u.typeId == currentTypeId);
        if (!unitType) throw new Error('Unit type not found in facility');

        const titleEl = document.querySelector('h1.font-headline-lg');
        if (titleEl) titleEl.textContent = 'Kho ' + unitType.sizeName + ' - ' + facility.name;

        const addressEl = document.querySelector('.material-symbols-outlined.text-secondary').nextElementSibling;
        if (addressEl) addressEl.textContent = facility.name + ' - ' + facility.address;

        const availableBadge = document.querySelector('#availability-badge');
        if (availableBadge) {
            if (unitType.availableUnits > 0) {
                availableBadge.innerHTML = '<div class="flex items-center gap-2 font-label-button text-label-button text-emerald-800 font-semibold"><span class="material-symbols-outlined text-emerald-600 text-lg">check_circle</span><span>C\u00F2n ' + unitType.availableUnits + ' kho ph\u00F9 h\u1EE3p</span></div>';
            } else {
                availableBadge.innerHTML = '<div class="flex items-center gap-2 font-label-button text-label-button text-error font-semibold"><span class="material-symbols-outlined text-error text-lg">cancel</span><span>\u0110\u00E3 h\u1EBFt kho</span></div>';
                availableBadge.className = 'p-3 rounded-lg bg-error-container text-on-error-container flex items-start gap-2.5 transition-all';
            }
        }

        baseMonthlyPrice = unitType.monthlyPrice;
        document.querySelectorAll('span.text-on-surface.font-medium').forEach(el => {
             if (el.textContent.includes('/ th')) {
                 el.textContent = baseMonthlyPrice.toLocaleString('vi-VN') + ' \u20AB / th\u00E1ng';
             }
        });

        recalculatePrice();
    } catch (error) {
        console.error('Error fetching facility details:', error);
    }
}

window.recalculatePrice = async function() {
    const durationSelect = document.getElementById('duration-select');
    if (!durationSelect) return;
    const duration = durationSelect.value;
    
    document.getElementById('display-duration').textContent = duration + ' th\u00E1ng';

    try {
        const response = await fetch('/api/pricing/estimate?facilityId=' + currentFacilityId + '&typeId=' + currentTypeId + '&months=' + duration);
        if (!response.ok) throw new Error('API error');
        const estimate = await response.json();
        
        document.getElementById('total-price-display').textContent = estimate.estimatedTotal.toLocaleString('vi-VN') + ' \u20AB';
    } catch (error) {
        console.error('Estimate error:', error);
        const total = baseMonthlyPrice * duration;
        document.getElementById('total-price-display').textContent = total.toLocaleString('vi-VN') + ' \u20AB';
    }
}
