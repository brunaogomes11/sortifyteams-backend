-- Seed dos 10 esportes do MVP (Fluxo 3). Idempotente: roda em todo startup.
-- Minimos por time calibrados para partidas informais (C6).
INSERT INTO tb_esporte (id, nome, icone, exige_goleiro, jogadores_minimos_por_time) VALUES
    ('01SORTIFYESPORTE0000000001', 'Vôlei',             'volleyball',       FALSE, 6),
    ('01SORTIFYESPORTE0000000002', 'Basquete',          'basketball',       FALSE, 3),
    ('01SORTIFYESPORTE0000000003', 'Handebol',          'handball',         TRUE,  5),
    ('01SORTIFYESPORTE0000000004', 'Futebol',           'soccer',           TRUE,  7),
    ('01SORTIFYESPORTE0000000005', 'Tênis',             'tennis',           FALSE, 1),
    ('01SORTIFYESPORTE0000000006', 'Beach Tênis',       'beach-tennis',     FALSE, 2),
    ('01SORTIFYESPORTE0000000007', 'Futsal',            'futsal',           TRUE,  4),
    ('01SORTIFYESPORTE0000000008', 'Futebol Society',   'society',          TRUE,  5),
    ('01SORTIFYESPORTE0000000009', 'Vôlei de Praia',    'beach-volleyball', FALSE, 2),
    ('01SORTIFYESPORTE0000000010', 'Handebol de Praia', 'beach-handball',   TRUE,  3)
ON CONFLICT (nome) DO NOTHING;

-- ---------------------------------------------------------------------------
-- spec 002 (D2/FR-029): a coluna do binário do APK fica FORA do mapeamento JPA
-- de proposito -- se fosse campo da entidade, todo findById traria ~30 MB para
-- a heap. Criada aqui, idempotente, e marcada como EXTERNAL para que
-- substring() leia so os chunks da faixa pedida: o padrao (EXTENDED) comprime,
-- e valor comprimido obriga o Postgres a descomprimir tudo para devolver
-- qualquer pedaco -- o que mataria o Range/retomada (C10).
-- STORAGE so vale para valores NOVOS, entao precisa existir antes do 1o APK.
ALTER TABLE tb_versao_runtime_arquivo ADD COLUMN IF NOT EXISTS conteudo BYTEA;
ALTER TABLE tb_versao_runtime_arquivo ALTER COLUMN conteudo SET STORAGE EXTERNAL;

-- spec 002 (Fase 5): mesma abordagem do APK para os assets de conteudo -- a
-- coluna binaria fica fora do mapeamento JPA. Assets sao pequenos (bundle de
-- poucos MB, imagens de KB), entao aqui nao ha leitura por faixa; EXTERNAL
-- mesmo assim porque bundle .hbc e imagens ja vem comprimidos.
ALTER TABLE tb_asset_conteudo ADD COLUMN IF NOT EXISTS conteudo BYTEA;
ALTER TABLE tb_asset_conteudo ALTER COLUMN conteudo SET STORAGE EXTERNAL;
