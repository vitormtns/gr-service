package com.gerenciadorrural.modules.herd.domain;
import java.util.UUID;
public record PaddockSummary(UUID id, String name, String code, PaddockStatus status, long version, long occupancy) {
    public PaddockSummary(UUID id, String name, String code, PaddockStatus status, long version) { this(id,name,code,status,version,0); }
}
