package com.safebox.self_storage.service.impl;

import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import com.safebox.self_storage.service.Sprint2Access;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StorageDiscoveryService {
    public record Filters(UUID facilityId,Integer typeId,BigDecimal minVolumeM3,BigDecimal maxVolumeM3,
                          BigDecimal minPrice,BigDecimal maxPrice,String availability,LocalDate startDate,Integer months) {}
    public record UnitCard(UUID unitId,UUID facilityId,String facilityName,String address,String facilityImage,
                           Integer typeId,String typeName,String typeImage,String unitNumber,String floor,String zone,
                           BigDecimal volumeM3,BigDecimal monthlyPrice,String status,String availabilityLabel,
                           boolean bookable,long availableCount,long reservedCount,long occupiedCount,long pendingHoldCount,
                           String measurementSource) {}
    private final FacilityRepository facilities;
    private final StorageUnitRepository units;
    private final StorageUnitTypeRepository types;
    private final PricingPolicyRepository prices;
    private final RentalContractRepository contracts;
    private final ReservationRepository reservations;
    public StorageDiscoveryService(FacilityRepository facilities,StorageUnitRepository units,StorageUnitTypeRepository types,
            PricingPolicyRepository prices,RentalContractRepository contracts,ReservationRepository reservations) {
        this.facilities=facilities;this.units=units;this.types=types;this.prices=prices;this.contracts=contracts;this.reservations=reservations;
    }
    @Transactional(readOnly=true)
    public List<UnitCard> search(Filters f) {
        LocalDate today=LocalDate.now(Sprint2Access.ZONE);
        LocalDate start=f.startDate()==null?today:f.startDate();
        int months=f.months()==null?1:f.months();
        if (start.isBefore(today) || months<1 || months>36 || invalidRange(f.minVolumeM3(),f.maxVolumeM3())
                || invalidRange(f.minPrice(),f.maxPrice()) || (f.availability()!=null &&
                !Set.of("BOOKABLE","HELD","OCCUPIED","MAINTENANCE","NOT_OPEN").contains(f.availability())))
            throw new BusinessException(HttpStatus.BAD_REQUEST,"Bộ lọc ngày thuê, thể tích, giá hoặc tình trạng không hợp lệ.");
        LocalDate end=start.plusMonths(months);
        List<Facility> all=facilities.searchActiveFacilities(null,Pageable.unpaged()).getContent();
        if (f.facilityId()!=null) all=all.stream().filter(x->f.facilityId().equals(x.getFacilityId())).toList();
        Map<Integer,StorageUnitType> typeMap=new HashMap<>();types.findAll().forEach(t->typeMap.put(t.getTypeId(),t));
        List<UnitCard> result=new ArrayList<>();
        for(Facility facility:all) {
            UUID fid=facility.getFacilityId();List<StorageUnit> physical=units.findByFacilityIdOrderByUnitNumberAsc(fid);
            if(physical.isEmpty())continue;
            List<PricingPolicy> current=prices.findByFacilityIdAndStatus(fid,"ACTIVE");
            Map<Integer,List<StorageUnit>> grouped=new LinkedHashMap<>();physical.forEach(u->grouped.computeIfAbsent(u.getTypeId(),k->new ArrayList<>()).add(u));
            for(var group:grouped.entrySet()) {
                int typeId=group.getKey();if(f.typeId()!=null && f.typeId()!=typeId)continue;
                StorageUnitType type=typeMap.get(typeId);if(type==null)continue;
                BigDecimal price=CurrentPricing.forType(current,typeId,start).map(PricingPolicy::getMonthlyPrice)
                        .filter(p->p!=null && p.signum()>0).orElse(null);
                List<StorageUnit> rows=group.getValue();
                long reserved=rows.stream().filter(u->"RESERVED".equals(u.getStatus())).count();
                long occupied=rows.stream().filter(u->"OCCUPIED".equals(u.getStatus())).count();
                long held=reservations.countActivePendingOverlapping(fid,typeId,start,end,LocalDateTime.now(Sprint2Access.ZONE));
                Set<UUID> conflicts=contracts.findOverlappingUnitIds(rows.stream().map(StorageUnit::getUnitId).toList(),start,end);
                long ready=rows.stream().filter(this::ready).filter(u->!conflicts.contains(u.getUnitId())).count();
                long remaining=Math.max(0,ready-held);
                for(StorageUnit u:rows) {
                    BigDecimal volume=u.getVolumeM3();
                    if(!inRange(volume,f.minVolumeM3(),f.maxVolumeM3()) || !inRange(price,f.minPrice(),f.maxPrice()))continue;
                    boolean bookable="ACTIVE".equals(type.getStatus()) && price!=null && ready(u)
                            && !conflicts.contains(u.getUnitId()) && remaining>0;
                    String status=label(u,bookable,price,conflicts.contains(u.getUnitId()),held,remaining);
                    String category=bookable?"BOOKABLE":"RESERVED".equals(u.getStatus())||"Đang giữ chỗ".equals(status)?"HELD":
                            "OCCUPIED".equals(u.getStatus())?"OCCUPIED":"MAINTENANCE".equals(u.getStatus())?"MAINTENANCE":"NOT_OPEN";
                    if(f.availability()!=null&&!f.availability().equals(category))continue;
                    result.add(new UnitCard(u.getUnitId(),fid,facility.getName(),facility.getAddress(),safeImage(facility.getImagePath()),
                            typeId,type.getTypeName(),safeImage(type.getImagePath()),u.getUnitNumber(),u.getFloor(),u.getZone(),
                            volume,price,u.getStatus(),status,bookable,remaining,reserved,occupied,held,u.getMeasurementSource()));
                }
            }
        }
        return result;
    }
    private boolean ready(StorageUnit u) {
        // AVAILABLE is the operational booking status; verification only controls volume display.
        return "AVAILABLE".equals(u.getStatus());
    }
    private String label(StorageUnit u,boolean bookable,BigDecimal price,boolean contract,long held,long remaining) {
        if("MAINTENANCE".equals(u.getStatus()))return "Đang bảo trì";
        if("OCCUPIED".equals(u.getStatus()))return "Đang cho thuê";
        if(price==null)return "Chưa có giá thuê hiệu lực";
        if("RESERVED".equals(u.getStatus())||contract||held>0&&remaining==0&&ready(u))return "Đang giữ chỗ";
        if(bookable)return "Còn trống";
        return "Chưa mở cho thuê";
    }
    private boolean invalidRange(BigDecimal min,BigDecimal max) {return min!=null&&min.signum()<0||max!=null&&max.signum()<0||min!=null&&max!=null&&min.compareTo(max)>0;}
    private boolean inRange(BigDecimal value,BigDecimal min,BigDecimal max) {return (min==null&&max==null)||(value!=null&&(min==null||value.compareTo(min)>=0)&&(max==null||value.compareTo(max)<=0));}
    private String safeImage(String image) {return image!=null&&image.startsWith("/")&&!image.startsWith("//")&&!image.contains("..")?image:null;}
}
