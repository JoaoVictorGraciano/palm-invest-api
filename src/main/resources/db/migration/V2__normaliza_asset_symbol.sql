-- A API passou a normalizar assetSymbol (trim + maiusculas) na entrada.
-- Alinha os registros antigos para que "petr4" e "PETR4" sejam o mesmo ativo.
UPDATE transactions
SET asset_symbol = UPPER(TRIM(asset_symbol))
WHERE asset_symbol IS NOT NULL
  AND asset_symbol <> UPPER(TRIM(asset_symbol));
