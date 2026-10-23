-- Apply manually before deploying code that saves stock balance snapshots.
ALTER TABLE public.stock_balance_snapshot_detail
    ADD COLUMN IF NOT EXISTS purchase_price numeric(20, 6) NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conrelid = 'public.stock_balance_snapshot_detail'::regclass
           AND conname = 'stock_balance_snapshot_detail_purchase_price_ck'
    ) THEN
        ALTER TABLE public.stock_balance_snapshot_detail
            ADD CONSTRAINT stock_balance_snapshot_detail_purchase_price_ck
            CHECK (purchase_price IS NULL OR purchase_price >= 0);
    END IF;
END $$;

COMMENT ON COLUMN public.stock_balance_snapshot_detail.purchase_price IS '스냅샷 평균 매수가';
