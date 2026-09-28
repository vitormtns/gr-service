package com.gerenciadorrural.modules.herd.domain;

import java.util.UUID;

public sealed interface AnimalEventDetails permits CreatedEventDetails, CorrectedEventDetails, LifecycleEventDetails, MovedEventDetails, TransferredEventDetails, WeighedEventDetails, HealthTreatmentEventDetails, AnimalEventDetails.HealthTreatmentRetractionEventDetails, BreedingEventDetails, PregnancyEventDetails, CalvingEventDetails, BornEventDetails, MilkRecordedEventDetails, MotherCorrectedEventDetails, AnimalNoteEventDetails {

    record HealthTreatmentRetractionEventDetails(UUID treatmentId, UUID retractionId, String reason)
            implements AnimalEventDetails {
    }
}
