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
