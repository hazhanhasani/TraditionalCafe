-- Freeze due dates for historical open debts created before per-entry due_at existed.
-- This prevents later edits to a customer's default due_days from changing old debt aging.

UPDATE customer_ledger
SET due_at = datetime(
  created_at,
  '+' || (
    SELECT c.due_days
    FROM customers c
    WHERE c.id = customer_ledger.customer_id
  ) || ' days'
)
WHERE due_at IS NULL
  AND amount > 0
  AND entry_type IN ('debt','adjustment')
  AND EXISTS (
    SELECT 1
    FROM customers c
    WHERE c.id = customer_ledger.customer_id
      AND c.due_days > 0
  );
