package com.safebox.self_storage.dto.support;
import java.util.*;
public record StaffScopeView(UUID userId,String fullName,String email,List<FacilityView> facilities){public record FacilityView(UUID facilityId,String name,String address){}}
