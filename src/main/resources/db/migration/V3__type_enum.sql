-- type passa a ser o enum BUY/SELL (mesmos valores do front).
-- Converte valores legados em portugues, se existirem, e trava a coluna.
-- Se houver algum outro valor, a migracao falha de proposito: decida o mapeamento antes de aplicar.
UPDATE transactions SET type = 'BUY'  WHERE type = 'COMPRA';
UPDATE transactions SET type = 'SELL' WHERE type = 'VENDA';

ALTER TABLE transactions ALTER COLUMN type SET NOT NULL;
ALTER TABLE transactions ADD CONSTRAINT ck_transactions_type CHECK (type IN ('BUY', 'SELL'));
