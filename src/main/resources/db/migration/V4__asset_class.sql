-- Classe do ativo (STOCK ou CRYPTO) passa a ser persistida; antes o front inferia pelo ticker.
-- Registros existentes recebem a mesma regra que o front usava: simbolo com digito e acao da B3
-- (PETR4, ITUB4), sem digito e cripto (BTC, BITCOIN). TRANSLATE funciona igual em PostgreSQL e H2.
ALTER TABLE transactions ADD COLUMN asset_class VARCHAR(20);

UPDATE transactions
SET asset_class = CASE
    WHEN TRANSLATE(asset_symbol, '0123456789', '') <> asset_symbol THEN 'STOCK'
    ELSE 'CRYPTO'
END
WHERE asset_class IS NULL;

ALTER TABLE transactions ALTER COLUMN asset_class SET NOT NULL;
ALTER TABLE transactions ADD CONSTRAINT ck_transactions_asset_class CHECK (asset_class IN ('STOCK', 'CRYPTO'));
