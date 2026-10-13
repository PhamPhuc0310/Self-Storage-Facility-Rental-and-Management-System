package com.safebox.self_storage.service;

import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.repository.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractQueryService {
    public record ContractView(UUID contractId, UUID reservationId, String contractNumber, String facilityName,
                               String unitNumber, LocalDate startDate, LocalDate endDate, String status) {}
    private final Sprint2Access access;
    private final RentalContractRepository contracts;
    private final StorageUnitRepository units;
    private final FacilityRepository facilities;
    public ContractQueryService(Sprint2Access access, RentalContractRepository contracts, StorageUnitRepository units, FacilityRepository facilities) {
        this.access = access; this.contracts = contracts; this.units = units; this.facilities = facilities;
    }
    @Transactional(readOnly = true)
    public List<ContractView> mine() {
        return contracts.findByCustomerIdOrderByStartDateDesc(access.currentId()).stream().map(this::view).toList();
    }
    @Transactional(readOnly = true)
    public ContractView get(UUID id) { return view(access.contract(id, false)); }
    private ContractView view(RentalContract c) {
        StorageUnit unit = units.findById(c.unitId).orElse(null);
        String facilityName = unit == null ? "" : facilities.findById(unit.getFacilityId()).map(Facility::getName).orElse("");
        return new ContractView(c.contractId, c.reservationId, c.contractNumber, facilityName,
                unit == null ? null : unit.getUnitNumber(), c.startDate, c.endDate, c.status);
    }
}
