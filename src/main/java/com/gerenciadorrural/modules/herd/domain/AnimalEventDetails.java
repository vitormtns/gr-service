package com.gerenciadorrural.modules.herd.domain;

public sealed interface AnimalEventDetails permits CreatedEventDetails, CorrectedEventDetails, LifecycleEventDetails, MovedEventDetails, TransferredEventDetails, WeighedEventDetails, HealthTreatmentEventDetails {
}
