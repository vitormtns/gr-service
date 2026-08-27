package com.gerenciadorrural.modules.organizations.api;

import com.gerenciadorrural.modules.organizations.domain.AccessibleFarm;

import java.util.List;

public record AccessibleFarmsResponse(List<Item> items) {

    static AccessibleFarmsResponse from(List<AccessibleFarm> farms) {
        return new AccessibleFarmsResponse(farms.stream()
                .map(farm -> new Item(farm.farmId().toString(), farm.farmName()))
                .toList());
    }

    public record Item(String farmId, String farmName) {
    }
}
