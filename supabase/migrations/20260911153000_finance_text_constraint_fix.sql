-- PostgreSQL text values cannot contain NUL. Calling chr(0) while evaluating
-- the original constraints raises an error for every write, so retain the
-- length validation without constructing an invalid text value.
alter table app.financial_categories
    drop constraint financial_categories_name_check,
    drop constraint financial_categories_code_check;

alter table app.financial_categories
    add constraint financial_categories_name_check
        check (length(btrim(name)) between 1 and 120),
    add constraint financial_categories_code_check
        check (code is null or length(btrim(code)) between 1 and 60);

alter table app.financial_entries
    drop constraint financial_entries_description_check;

alter table app.financial_entries
    add constraint financial_entries_description_check
        check (length(btrim(description)) between 1 and 240);
