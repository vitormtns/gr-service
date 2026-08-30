package com.gerenciadorrural.modules.herd.domain;
import java.util.List;
public record HerdAnimalPage(List<HerdAnimalSummary> items,int page,int size,long totalElements) { public int totalPages(){ return totalElements == 0 ? 0 : (int)((totalElements + size - 1) / size); } }
