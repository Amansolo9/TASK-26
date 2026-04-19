-- Enforce audit-grade immutability and a 7-year retention window at the database level
-- for reconciliation_run and reconciliation_exception. Service-layer conventions alone
-- are insufficient: triggers guarantee the guarantee even if a future write path is added.

drop trigger if exists trg_reconciliation_run_block_update;
drop trigger if exists trg_reconciliation_run_block_delete;
drop trigger if exists trg_reconciliation_exception_block_update;
drop trigger if exists trg_reconciliation_exception_block_delete;

-- reconciliation_run: core identity fields (id, location, business_date, started_at, created_by)
-- are frozen after insert. Status may only advance forward from OPEN. completed_at, once set,
-- cannot be moved. summary_json is allowed to be written during closure.
create trigger trg_reconciliation_run_block_update
before update on reconciliation_run
for each row
begin
    if OLD.id <> NEW.id
       or OLD.location_id <> NEW.location_id
       or OLD.business_date <> NEW.business_date
       or OLD.started_at <> NEW.started_at
       or OLD.created_by <> NEW.created_by then
        signal sqlstate '45000' set message_text = 'reconciliation_run immutable fields cannot be modified';
    end if;

    if OLD.status <> 'OPEN' and OLD.status <> NEW.status then
        signal sqlstate '45000' set message_text = 'reconciliation_run status transition not allowed once closed';
    end if;

    if OLD.completed_at is not null and (NEW.completed_at is null or OLD.completed_at <> NEW.completed_at) then
        signal sqlstate '45000' set message_text = 'reconciliation_run completed_at is immutable once set';
    end if;
end;

create trigger trg_reconciliation_run_block_delete
before delete on reconciliation_run
for each row
begin
    if OLD.started_at >= date_sub(current_timestamp, interval 7 year) then
        signal sqlstate '45000' set message_text = 'reconciliation_run delete is blocked before 7-year retention period';
    end if;

    if exists (
        select 1
        from retention_hold rh
        where rh.entity_type = 'reconciliation_run'
          and rh.entity_id = cast(OLD.id as char)
          and rh.is_active = true
    ) then
        signal sqlstate '45000' set message_text = 'reconciliation_run delete is blocked by active retention hold';
    end if;
end;

-- reconciliation_exception: freeze identity fields (id/run_id/exception_type/created_at).
-- The workflow allows OPEN -> IN_REVIEW -> RESOLVED -> REOPENED transitions through
-- application code, so status is left mutable; identity and creation metadata are not.
create trigger trg_reconciliation_exception_block_update
before update on reconciliation_exception
for each row
begin
    if OLD.id <> NEW.id
       or OLD.run_id <> NEW.run_id
       or OLD.exception_type <> NEW.exception_type
       or OLD.created_at <> NEW.created_at then
        signal sqlstate '45000' set message_text = 'reconciliation_exception immutable fields cannot be modified';
    end if;
end;

create trigger trg_reconciliation_exception_block_delete
before delete on reconciliation_exception
for each row
begin
    if OLD.created_at >= date_sub(current_timestamp, interval 7 year) then
        signal sqlstate '45000' set message_text = 'reconciliation_exception delete is blocked before 7-year retention period';
    end if;

    if exists (
        select 1
        from retention_hold rh
        where rh.entity_type = 'reconciliation_exception'
          and rh.entity_id = cast(OLD.id as char)
          and rh.is_active = true
    ) then
        signal sqlstate '45000' set message_text = 'reconciliation_exception delete is blocked by active retention hold';
    end if;
end;
