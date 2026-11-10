(function(root) {
  function label(contract, today) {
    const rows=contract.penalties||[];
    if(rows.some(p=>p.status==='PENDING_APPROVAL'))return 'Chờ xác nhận thanh toán';
    if(Number(contract.totalPenaltyDebt)>0)return 'Đã tính phí · Chưa thanh toán đủ';
    if(contract.graceDays==null)return 'Chưa có chính sách phí áp dụng';
    if(contract.billableDays===0)return 'Trong thời gian ân hạn';
    if(!rows.length)return 'Phí chưa được tính';
    if(today&&rows.every(p=>p.periodDate<today))return 'Chưa tính phí kỳ mới';
    return Number(contract.totalPenaltyPaid)>0?'Đã thanh toán phí':'Kỳ phí không còn phải thu';
  }
  if(typeof module==='object'&&module.exports)module.exports={label};
  root.SafeBoxOverdueState={label};
})(typeof window==='undefined'?globalThis:window);
