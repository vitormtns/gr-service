-- The UNIQUE constraint on (tenant_id, farm_id, operation_id) already provides
-- the exact B-tree access path used by idempotency lookups.
drop index if exists app.financial_entries_context_operation_idx;
