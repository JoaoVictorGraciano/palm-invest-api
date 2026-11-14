-- AssetClass ganha FII, ETF e BDR. Apenas amplia o CHECK criado em V4; nenhum dado existente muda,
-- porque todos os valores ja gravados (STOCK, CRYPTO) continuam validos.
-- DROP + ADD de constraint nomeada funciona igual em PostgreSQL e H2.
ALTER TABLE transactions DROP CONSTRAINT ck_transactions_asset_class;
ALTER TABLE transactions ADD CONSTRAINT ck_transactions_asset_class
    CHECK (asset_class IN ('STOCK', 'CRYPTO', 'FII', 'ETF', 'BDR'));
