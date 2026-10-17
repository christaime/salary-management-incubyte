-- Seed: employees and their periodic assignments.
-- 10,000 employees, target from the spec. Hybrid layout:
--   1. ~150 curated rows with hand-picked realistic names (media & entertainment
--      flavour, real regions) at the top, matching the "spreadsheet export" style.
--   2. WITH RECURSIVE top-up from 151 to 10000, using per-region name tables
--      defined inline. No random(), no now().
--
-- Region weights (top-up only): Europe 30%, America 25%, Asia 20%, Africa 15%,
-- NULL origin 10%. Origin for curated rows is hand-picked per row.
-- Employment split: ~6/7 FULL_TIME, ~1/7 PART_TIME. CONTRACTOR is intentionally
-- not seeded.
--
-- Every employee has exactly one assignment, in effect from 2025-10-01
-- (before the first payroll period starts on 2026-01-01) with end_date NULL.
-- Every assignment lands on a (country, department, role, employment_type)
-- tuple consistent with the employee's origin region and the reference data.
--
-- Determinism:
--   matricule   = printf('EMP-%06d', n)
--   employee id = printf('%08x-0000-4000-8000-%012x', n, n)
--   assignment  = printf('%08x-0000-4000-9000-%012x', n, n)
--   curated emails: first.last@company.com
--   top-up emails : first.last.<n>@company.com
--
-- Region -> countries (from V202610171):
--   EUROPE  : DE, FR, GB
--   AMERICA : US, CA, BR
--   ASIA    : IN, CN
--   AFRICA  : CM, SN
--   NULL    : any of the 10, distributed by n % 10.
--
-- Departments and roles are picked deterministically by n % 10 and n % 12
-- respectively, so every (department, role) combination is represented.

-- ---------------------------------------------------------------------------
-- Curated employees (150 rows, hand-picked names and origins).
-- ---------------------------------------------------------------------------
INSERT INTO employees (id, matricule, name, email, birth_date, origin_country_id) VALUES
('00000001-0000-4000-8000-000000000001', 'EMP-000001', 'Amadou Diallo',
 'amadou.diallo@company.com',    '1990-05-14', 'c0000000-0000-0000-0000-000000000009'),
('00000002-0000-4000-8000-000000000002', 'EMP-000002', 'Fatima Al-Hassan',
 'fatima.alhassan@company.com',  '1985-11-22', 'c0000000-0000-0000-0000-000000000005'),
('00000003-0000-4000-8000-000000000003', 'EMP-000003', 'Jean Dupont',
 'jean.dupont@company.com',      '1988-03-30', 'c0000000-0000-0000-0000-000000000003'),
('00000004-0000-4000-8000-000000000004', 'EMP-000004', 'Emma Schmidt',
 'emma.schmidt@company.com',     '1992-07-19', 'c0000000-0000-0000-0000-000000000002'),
('00000005-0000-4000-8000-000000000005', 'EMP-000005', 'Emily Smith',
 'emily.smith@company.com',      '1991-01-12', 'c0000000-0000-0000-0000-000000000001'),
('00000006-0000-4000-8000-000000000006', 'EMP-000006', 'Carlos Gomez',
 'carlos.gomez@company.com',     '1984-09-05', 'c0000000-0000-0000-0000-000000000008'),
('00000007-0000-4000-8000-000000000007', 'EMP-000007', 'Wei Zhang',
 'wei.zhang@company.com',        '1993-12-04', 'c0000000-0000-0000-0000-00000000000a'),
('00000008-0000-4000-8000-000000000008', 'EMP-000008', 'Li Na',
 'li.na@company.com',            '1989-06-25', 'c0000000-0000-0000-0000-00000000000a'),
('00000009-0000-4000-8000-000000000009', 'EMP-000009', 'Olivia Brown',
 'olivia.brown@company.com',     '1987-02-08', 'c0000000-0000-0000-0000-000000000007'),
('0000000a-0000-4000-8000-00000000000a', 'EMP-000010', 'Lucas Martin',
 'lucas.martin@company.com',     '1994-10-17', 'c0000000-0000-0000-0000-000000000003'),
('0000000b-0000-4000-8000-00000000000b', 'EMP-000011', 'Sofia Rossi',
 'sofia.rossi@company.com',      '1990-04-21', 'c0000000-0000-0000-0000-000000000002'),
('0000000c-0000-4000-8000-00000000000c', 'EMP-000012', 'Kwame Mensah',
 'kwame.mensah@company.com',     '1986-08-11', 'c0000000-0000-0000-0000-000000000009'),
('0000000d-0000-4000-8000-00000000000d', 'EMP-000013', 'Aisha Bello',
 'aisha.bello@company.com',      '1995-03-02', 'c0000000-0000-0000-0000-000000000005'),
('0000000e-0000-4000-8000-00000000000e', 'EMP-000014', 'Hiroshi Tanaka',
 'hiroshi.tanaka@company.com',   '1983-09-15', 'c0000000-0000-0000-0000-00000000000a'),
('0000000f-0000-4000-8000-00000000000f', 'EMP-000015', 'Priya Sharma',
 'priya.sharma@company.com',     '1991-12-09', 'c0000000-0000-0000-0000-000000000004'),
('00000010-0000-4000-8000-000000000010', 'EMP-000016', 'Daniel Wilson',
 'daniel.wilson@company.com',    '1988-06-30', 'c0000000-0000-0000-0000-000000000007'),
('00000011-0000-4000-8000-000000000011', 'EMP-000017', 'Isabella Lopez',
 'isabella.lopez@company.com',   '1993-01-22', 'c0000000-0000-0000-0000-000000000008'),
('00000012-0000-4000-8000-000000000012', 'EMP-000018', 'Ahmed Benali',
 'ahmed.benali@company.com',     '1985-05-03', 'c0000000-0000-0000-0000-000000000005'),
('00000013-0000-4000-8000-000000000013', 'EMP-000019', 'Charlotte Taylor',
 'charlotte.taylor@company.com', '1992-11-26', 'c0000000-0000-0000-0000-000000000007'),
('00000014-0000-4000-8000-000000000014', 'EMP-000020', 'Yuki Sato',
 'yuki.sato@company.com',        '1990-07-14', 'c0000000-0000-0000-0000-00000000000a'),
('00000015-0000-4000-8000-000000000015', 'EMP-000021', 'Maria Garcia',
 'maria.garcia@company.com',     '1989-02-17', 'c0000000-0000-0000-0000-000000000001'),
('00000016-0000-4000-8000-000000000016', 'EMP-000022', 'Thomas Muller',
 'thomas.muller@company.com',    '1987-09-04', 'c0000000-0000-0000-0000-000000000002'),
('00000017-0000-4000-8000-000000000017', 'EMP-000023', 'Aya Kone',
 'aya.kone@company.com',         '1994-05-28', 'c0000000-0000-0000-0000-000000000009'),
('00000018-0000-4000-8000-000000000018', 'EMP-000024', 'Rahul Verma',
 'rahul.verma@company.com',      '1992-11-08', 'c0000000-0000-0000-0000-000000000004'),
('00000019-0000-4000-8000-000000000019', 'EMP-000025', 'Sarah Johnson',
 'sarah.johnson@company.com',    '1985-03-19', 'c0000000-0000-0000-0000-000000000001'),
('0000001a-0000-4000-8000-00000000001a', 'EMP-000026', 'Ling Chen',
 'ling.chen@company.com',        '1991-07-12', 'c0000000-0000-0000-0000-00000000000a'),
('0000001b-0000-4000-8000-00000000001b', 'EMP-000027', 'Mateo Alvarez',
 'mateo.alvarez@company.com',    '1993-12-01', 'c0000000-0000-0000-0000-000000000008'),
('0000001c-0000-4000-8000-00000000001c', 'EMP-000028', 'Hannah Weber',
 'hannah.weber@company.com',     '1988-06-23', 'c0000000-0000-0000-0000-000000000002'),
('0000001d-0000-4000-8000-00000000001d', 'EMP-000029', 'Moussa Diop',
 'moussa.diop@company.com',      '1986-10-14', 'c0000000-0000-0000-0000-000000000009'),
('0000001e-0000-4000-8000-00000000001e', 'EMP-000030', 'Chloe Dubois',
 'chloe.dubois@company.com',     '1995-04-07', 'c0000000-0000-0000-0000-000000000003'),
('0000001f-0000-4000-8000-00000000001f', 'EMP-000031', 'James Anderson',
 'james.anderson@company.com',   '1983-08-30', 'c0000000-0000-0000-0000-000000000007'),
('00000020-0000-4000-8000-000000000020', 'EMP-000032', 'Naomi Okafor',
 'naomi.okafor@company.com',     '1990-01-25', 'c0000000-0000-0000-0000-000000000005'),
('00000021-0000-4000-8000-000000000021', 'EMP-000033', 'Kenji Watanabe',
 'kenji.watanabe@company.com',   '1987-05-16', 'c0000000-0000-0000-0000-00000000000a'),
('00000022-0000-4000-8000-000000000022', 'EMP-000034', 'Emma Wilson',
 'emma.wilson@company.com',      '1992-09-03', 'c0000000-0000-0000-0000-000000000006'),
('00000023-0000-4000-8000-000000000023', 'EMP-000035', 'Arjun Patel',
 'arjun.patel@company.com',      '1989-12-20', 'c0000000-0000-0000-0000-000000000004'),
('00000024-0000-4000-8000-000000000024', 'EMP-000036', 'Sofia Hernandez',
 'sofia.hernandez@company.com',  '1994-07-08', 'c0000000-0000-0000-0000-000000000008'),
('00000025-0000-4000-8000-000000000025', 'EMP-000037', 'Lukas Becker',
 'lukas.becker@company.com',     '1985-11-11', 'c0000000-0000-0000-0000-000000000002'),
('00000026-0000-4000-8000-000000000026', 'EMP-000038', 'Ama Mensah',
 'ama.mensah@company.com',       '1991-03-05', 'c0000000-0000-0000-0000-000000000005'),
('00000027-0000-4000-8000-000000000027', 'EMP-000039', 'Benjamin Taylor',
 'benjamin.taylor@company.com',  '1988-01-17', 'c0000000-0000-0000-0000-000000000007'),
('00000028-0000-4000-8000-000000000028', 'EMP-000040', 'Mei Lin',
 'mei.lin@company.com',          '1993-08-22', 'c0000000-0000-0000-0000-00000000000a'),
('00000029-0000-4000-8000-000000000029', 'EMP-000041', 'Javier Morales',
 'javier.morales@company.com',   '1984-04-14', 'c0000000-0000-0000-0000-000000000008'),
('0000002a-0000-4000-8000-00000000002a', 'EMP-000042', 'Charlotte Petit',
 'charlotte.petit@company.com',  '1990-10-09', 'c0000000-0000-0000-0000-000000000003'),
('0000002b-0000-4000-8000-00000000002b', 'EMP-000043', 'Samuel Johnson',
 'samuel.johnson@company.com',   '1987-02-28', 'c0000000-0000-0000-0000-000000000006'),
('0000002c-0000-4000-8000-00000000002c', 'EMP-000044', 'Priya Nair',
 'priya.nair@company.com',       '1995-06-13', 'c0000000-0000-0000-0000-000000000004'),
('0000002d-0000-4000-8000-00000000002d', 'EMP-000045', 'Ibrahim Camara',
 'ibrahim.camara@company.com',   '1986-12-30', 'c0000000-0000-0000-0000-000000000009'),
('0000002e-0000-4000-8000-00000000002e', 'EMP-000046', 'Isabella Romano',
 'isabella.romano@company.com',  '1992-05-21', 'c0000000-0000-0000-0000-000000000002'),
('0000002f-0000-4000-8000-00000000002f', 'EMP-000047', 'Michael Chen',
 'michael.chen@company.com',     '1989-09-15', 'c0000000-0000-0000-0000-000000000001'),
('00000030-0000-4000-8000-000000000030', 'EMP-000048', 'Fatou Sow',
 'fatou.sow@company.com',        '1994-02-03', 'c0000000-0000-0000-0000-000000000009'),
('00000031-0000-4000-8000-000000000031', 'EMP-000049', 'Lena Fischer',
 'lena.fischer@company.com',     '1988-11-27', 'c0000000-0000-0000-0000-000000000002'),
('00000032-0000-4000-8000-000000000032', 'EMP-000050', 'Ravi Iyer',
 'ravi.iyer@company.com',        '1985-07-19', 'c0000000-0000-0000-0000-000000000004'),
('00000033-0000-4000-8000-000000000033', 'EMP-000051', 'Emily Davis',
 'emily.davis@company.com',      '1991-01-08', 'c0000000-0000-0000-0000-000000000001'),
('00000034-0000-4000-8000-000000000034', 'EMP-000052', 'Hiroshi Kobayashi',
 'hiroshi.kobayashi@company.com','1990-04-30', 'c0000000-0000-0000-0000-00000000000a'),
('00000035-0000-4000-8000-000000000035', 'EMP-000053', 'Camila Rojas',
 'camila.rojas@company.com',     '1993-10-16', 'c0000000-0000-0000-0000-000000000008'),
('00000036-0000-4000-8000-000000000036', 'EMP-000054', 'David Evans',
 'david.evans@company.com',      '1984-06-07', 'c0000000-0000-0000-0000-000000000007'),
('00000037-0000-4000-8000-000000000037', 'EMP-000055', 'Aisha Traore',
 'aisha.traore@company.com',     '1996-03-22', 'c0000000-0000-0000-0000-000000000009'),
('00000038-0000-4000-8000-000000000038', 'EMP-000056', 'Noah Schmitt',
 'noah.schmitt@company.com',     '1992-08-14', 'c0000000-0000-0000-0000-000000000002'),
('00000039-0000-4000-8000-000000000039', 'EMP-000057', 'Sakura Ito',
 'sakura.ito@company.com',       '1990-12-05', 'c0000000-0000-0000-0000-00000000000a'),
('0000003a-0000-4000-8000-00000000003a', 'EMP-000058', 'Diego Garcia',
 'diego.garcia@company.com',     '1987-04-18', 'c0000000-0000-0000-0000-000000000008'),
('0000003b-0000-4000-8000-00000000003b', 'EMP-000059', 'Olivia Martin',
 'olivia.martin@company.com',    '1994-09-26', 'c0000000-0000-0000-0000-000000000003'),
('0000003c-0000-4000-8000-00000000003c', 'EMP-000060', 'Kwame Asante',
 'kwame.asante@company.com',     '1986-01-11', 'c0000000-0000-0000-0000-000000000005'),
('0000003d-0000-4000-8000-00000000003d', 'EMP-000061', 'Chloe Brown',
 'chloe.brown@company.com',      '1991-05-24', 'c0000000-0000-0000-0000-000000000006'),
('0000003e-0000-4000-8000-00000000003e', 'EMP-000062', 'Vikram Singh',
 'vikram.singh@company.com',     '1988-10-31', 'c0000000-0000-0000-0000-000000000004'),
('0000003f-0000-4000-8000-00000000003f', 'EMP-000063', 'Anna Weber',
 'anna.weber@company.com',       '1993-03-15', 'c0000000-0000-0000-0000-000000000002'),
('00000040-0000-4000-8000-000000000040', 'EMP-000064', 'Omar Bello',
 'omar.bello@company.com',       '1985-08-09', 'c0000000-0000-0000-0000-000000000005'),
('00000041-0000-4000-8000-000000000041', 'EMP-000065', 'Sophia Williams',
 'sophia.williams@company.com',  '1992-12-22', 'c0000000-0000-0000-0000-000000000001'),
('00000042-0000-4000-8000-000000000042', 'EMP-000066', 'Yuki Tanaka',
 'yuki.tanaka@company.com',      '1989-02-14', 'c0000000-0000-0000-0000-00000000000a'),
('00000043-0000-4000-8000-000000000043', 'EMP-000067', 'Carlos Silva',
 'carlos.silva@company.com',     '1984-11-03', 'c0000000-0000-0000-0000-000000000008'),
('00000044-0000-4000-8000-000000000044', 'EMP-000068', 'Leila Ndiaye',
 'leila.ndiaye@company.com',     '1995-07-27', 'c0000000-0000-0000-0000-000000000009'),
('00000045-0000-4000-8000-000000000045', 'EMP-000069', 'Felix Hoffman',
 'felix.hoffman@company.com',    '1991-09-19', 'c0000000-0000-0000-0000-000000000002'),
('00000046-0000-4000-8000-000000000046', 'EMP-000070', 'Ananya Gupta',
 'ananya.gupta@company.com',     '1987-12-12', 'c0000000-0000-0000-0000-000000000004'),
('00000047-0000-4000-8000-000000000047', 'EMP-000071', 'Jack Robinson',
 'jack.robinson@company.com',    '1990-03-08', 'c0000000-0000-0000-0000-000000000007'),
('00000048-0000-4000-8000-000000000048', 'EMP-000072', 'Ama Owusu',
 'ama.owusu@company.com',        '1993-06-20', 'c0000000-0000-0000-0000-000000000005'),
('00000049-0000-4000-8000-000000000049', 'EMP-000073', 'Laura Girard',
 'laura.girard@company.com',     '1988-01-30', 'c0000000-0000-0000-0000-000000000003'),
('0000004a-0000-4000-8000-00000000004a', 'EMP-000074', 'Ryan Murphy',
 'ryan.murphy@company.com',      '1985-05-07', 'c0000000-0000-0000-0000-000000000006'),
('0000004b-0000-4000-8000-00000000004b', 'EMP-000075', 'Sofia Santos',
 'sofia.santos@company.com',     '1994-11-16', 'c0000000-0000-0000-0000-000000000008'),
('0000004c-0000-4000-8000-00000000004c', 'EMP-000076', 'Rahul Mishra',
 'rahul.mishra@company.com',     '1991-08-04', 'c0000000-0000-0000-0000-000000000004'),
('0000004d-0000-4000-8000-00000000004d', 'EMP-000077', 'Julia Wagner',
 'julia.wagner@company.com',     '1987-02-21', 'c0000000-0000-0000-0000-000000000002'),
('0000004e-0000-4000-8000-00000000004e', 'EMP-000078', 'Ibrahim Sy',
 'ibrahim.sy@company.com',       '1989-10-13', 'c0000000-0000-0000-0000-000000000009'),
('0000004f-0000-4000-8000-00000000004f', 'EMP-000079', 'Emma Thompson',
 'emma.thompson@company.com',    '1995-01-19', 'c0000000-0000-0000-0000-000000000001'),
('00000050-0000-4000-8000-000000000050', 'EMP-000080', 'Wei Huang',
 'wei.huang@company.com',        '1990-06-28', 'c0000000-0000-0000-0000-00000000000a'),
('00000051-0000-4000-8000-000000000051', 'EMP-000081', 'Nadia Konate',
 'nadia.konate@company.com',     '1986-09-10', 'c0000000-0000-0000-0000-000000000005'),
('00000052-0000-4000-8000-000000000052', 'EMP-000082', 'Oliver Harris',
 'oliver.harris@company.com',    '1992-01-04', 'c0000000-0000-0000-0000-000000000007'),
('00000053-0000-4000-8000-000000000053', 'EMP-000083', 'Priya Kapoor',
 'priya.kapoor@company.com',     '1994-05-11', 'c0000000-0000-0000-0000-000000000004'),
('00000054-0000-4000-8000-000000000054', 'EMP-000084', 'Marco Bianchi',
 'marco.bianchi@company.com',    '1988-12-15', 'c0000000-0000-0000-0000-000000000002'),
('00000055-0000-4000-8000-000000000055', 'EMP-000085', 'Ava Martinez',
 'ava.martinez@company.com',     '1993-04-26', 'c0000000-0000-0000-0000-000000000001'),
('00000056-0000-4000-8000-000000000056', 'EMP-000086', 'Kwesi Mensah',
 'kwesi.mensah@company.com',     '1985-08-17', 'c0000000-0000-0000-0000-000000000005'),
('00000057-0000-4000-8000-000000000057', 'EMP-000087', 'Li Wei',
 'li.wei@company.com',           '1991-10-05', 'c0000000-0000-0000-0000-00000000000a'),
('00000058-0000-4000-8000-000000000058', 'EMP-000088', 'Lucas Bernard',
 'lucas.bernard@company.com',    '1987-03-09', 'c0000000-0000-0000-0000-000000000003'),
('00000059-0000-4000-8000-000000000059', 'EMP-000089', 'Sara Cisse',
 'sara.cisse@company.com',       '1994-07-14', 'c0000000-0000-0000-0000-000000000009'),
('0000005a-0000-4000-8000-00000000005a', 'EMP-000090', 'Ethan Clark',
 'ethan.clark@company.com',      '1989-11-23', 'c0000000-0000-0000-0000-000000000006'),
('0000005b-0000-4000-8000-00000000005b', 'EMP-000091', 'Elena Petrova',
 'elena.petrova@company.com',    '1990-05-18', 'c0000000-0000-0000-0000-000000000002'),
('0000005c-0000-4000-8000-00000000005c', 'EMP-000092', 'Sanjay Reddy',
 'sanjay.reddy@company.com',     '1986-02-06', 'c0000000-0000-0000-0000-000000000004'),
('0000005d-0000-4000-8000-00000000005d', 'EMP-000093', 'Ana Ribeiro',
 'ana.ribeiro@company.com',      '1992-09-29', 'c0000000-0000-0000-0000-000000000008'),
('0000005e-0000-4000-8000-00000000005e', 'EMP-000094', 'Mia Collins',
 'mia.collins@company.com',      '1995-12-07', 'c0000000-0000-0000-0000-000000000001'),
('0000005f-0000-4000-8000-00000000005f', 'EMP-000095', 'Moussa Fall',
 'moussa.fall@company.com',      '1984-10-21', 'c0000000-0000-0000-0000-000000000009'),
('00000060-0000-4000-8000-000000000060', 'EMP-000096', 'Sophie Lambert',
 'sophie.lambert@company.com',   '1991-02-13', 'c0000000-0000-0000-0000-000000000003'),
('00000061-0000-4000-8000-000000000061', 'EMP-000097', 'Daniel Kim',
 'daniel.kim@company.com',       '1987-06-30', 'c0000000-0000-0000-0000-000000000006'),
('00000062-0000-4000-8000-000000000062', 'EMP-000098', 'Grace Adeyemi',
 'grace.adeyemi@company.com',    '1993-01-15', 'c0000000-0000-0000-0000-000000000005'),
('00000063-0000-4000-8000-000000000063', 'EMP-000099', 'Pablo Diaz',
 'pablo.diaz@company.com',       '1988-04-27', 'c0000000-0000-0000-0000-000000000008'),
('00000064-0000-4000-8000-000000000064', 'EMP-000100', 'Vikram Rao',
 'vikram.rao@company.com',       '1990-11-10', 'c0000000-0000-0000-0000-000000000004'),
('00000065-0000-4000-8000-000000000065', 'EMP-000101', 'Hannah Becker',
 'hannah.becker@company.com',    '1994-03-24', 'c0000000-0000-0000-0000-000000000002'),
('00000066-0000-4000-8000-000000000066', 'EMP-000102', 'Christopher Hall',
 'christopher.hall@company.com', '1985-07-16', 'c0000000-0000-0000-0000-000000000007'),
('00000067-0000-4000-8000-000000000067', 'EMP-000103', 'Ling Wang',
 'ling.wang@company.com',        '1992-10-02', 'c0000000-0000-0000-0000-00000000000a'),
('00000068-0000-4000-8000-000000000068', 'EMP-000104', 'Awa Ndiaye',
 'awa.ndiaye@company.com',       '1988-08-26', 'c0000000-0000-0000-0000-000000000009'),
('00000069-0000-4000-8000-000000000069', 'EMP-000105', 'Thomas Moreau',
 'thomas.moreau@company.com',    '1991-12-20', 'c0000000-0000-0000-0000-000000000003'),
('0000006a-0000-4000-8000-00000000006a', 'EMP-000106', 'Jessica Lewis',
 'jessica.lewis@company.com',    '1986-03-11', 'c0000000-0000-0000-0000-000000000001'),
('0000006b-0000-4000-8000-00000000006b', 'EMP-000107', 'Rohit Sharma',
 'rohit.sharma@company.com',     '1993-06-18', 'c0000000-0000-0000-0000-000000000004'),
('0000006c-0000-4000-8000-00000000006c', 'EMP-000108', 'Amara Bamba',
 'amara.bamba@company.com',      '1989-10-14', 'c0000000-0000-0000-0000-000000000005'),
('0000006d-0000-4000-8000-00000000006d', 'EMP-000109', 'Nina Fischer',
 'nina.fischer@company.com',     '1995-01-28', 'c0000000-0000-0000-0000-000000000002'),
('0000006e-0000-4000-8000-00000000006e', 'EMP-000110', 'Andrew Walker',
 'andrew.walker@company.com',    '1987-07-22', 'c0000000-0000-0000-0000-000000000006'),
('0000006f-0000-4000-8000-00000000006f', 'EMP-000111', 'Keiko Yamamoto',
 'keiko.yamamoto@company.com',   '1990-04-19', 'c0000000-0000-0000-0000-00000000000a'),
('00000070-0000-4000-8000-000000000070', 'EMP-000112', 'Thiago Costa',
 'thiago.costa@company.com',     '1984-08-07', 'c0000000-0000-0000-0000-000000000008'),
('00000071-0000-4000-8000-000000000071', 'EMP-000113', 'Isabelle Roux',
 'isabelle.roux@company.com',    '1992-02-25', 'c0000000-0000-0000-0000-000000000003'),
('00000072-0000-4000-8000-000000000072', 'EMP-000114', 'Samuel Okafor',
 'samuel.okafor@company.com',    '1994-11-02', 'c0000000-0000-0000-0000-000000000005'),
('00000073-0000-4000-8000-000000000073', 'EMP-000115', 'Laura Evans',
 'laura.evans@company.com',      '1986-05-13', 'c0000000-0000-0000-0000-000000000007'),
('00000074-0000-4000-8000-000000000074', 'EMP-000116', 'Arjun Kapoor',
 'arjun.kapoor@company.com',     '1991-09-27', 'c0000000-0000-0000-0000-000000000004'),
('00000075-0000-4000-8000-000000000075', 'EMP-000117', 'Mia Anderson',
 'mia.anderson@company.com',     '1988-03-06', 'c0000000-0000-0000-0000-000000000001'),
('00000076-0000-4000-8000-000000000076', 'EMP-000118', 'Chen Ming',
 'chen.ming@company.com',        '1993-11-15', 'c0000000-0000-0000-0000-00000000000a'),
('00000077-0000-4000-8000-000000000077', 'EMP-000119', 'Elena Schmidt',
 'elena.schmidt@company.com',    '1990-07-08', 'c0000000-0000-0000-0000-000000000002'),
('00000078-0000-4000-8000-000000000078', 'EMP-000120', 'Jean-Claude Martin',
 'jean-claude.martin@company.com','1985-02-17','c0000000-0000-0000-0000-000000000003'),
('00000079-0000-4000-8000-000000000079', 'EMP-000121', 'Ayesha Khan',
 'ayesha.khan@company.com',      '1994-06-30', 'c0000000-0000-0000-0000-000000000004'),
('0000007a-0000-4000-8000-00000000007a', 'EMP-000122', 'Matthew Scott',
 'matthew.scott@company.com',    '1987-12-04', 'c0000000-0000-0000-0000-000000000006'),
('0000007b-0000-4000-8000-00000000007b', 'EMP-000123', 'Chiamaka Eze',
 'chiamaka.eze@company.com',     '1992-04-09', 'c0000000-0000-0000-0000-000000000005'),
('0000007c-0000-4000-8000-00000000007c', 'EMP-000124', 'Gabriel Rojas',
 'gabriel.rojas@company.com',    '1989-08-21', 'c0000000-0000-0000-0000-000000000008'),
('0000007d-0000-4000-8000-00000000007d', 'EMP-000125', 'Sarah Mitchell',
 'sarah.mitchell@company.com',   '1995-03-18', 'c0000000-0000-0000-0000-000000000007'),
('0000007e-0000-4000-8000-00000000007e', 'EMP-000126', 'Takahiro Sato',
 'takahiro.sato@company.com',    '1988-10-10', 'c0000000-0000-0000-0000-00000000000a'),
('0000007f-0000-4000-8000-00000000007f', 'EMP-000127', 'Nadia Camara',
 'nadia.camara@company.com',     '1991-02-22', 'c0000000-0000-0000-0000-000000000009'),
('00000080-0000-4000-8000-000000000080', 'EMP-000128', 'Alexander Muller',
 'alexander.muller@company.com', '1986-06-05', 'c0000000-0000-0000-0000-000000000002'),
('00000081-0000-4000-8000-000000000081', 'EMP-000129', 'Nisha Patel',
 'nisha.patel@company.com',      '1993-09-12', 'c0000000-0000-0000-0000-000000000004'),
('00000082-0000-4000-8000-000000000082', 'EMP-000130', 'Robert Wilson',
 'robert.wilson@company.com',    '1984-01-27', 'c0000000-0000-0000-0000-000000000001'),
('00000083-0000-4000-8000-000000000083', 'EMP-000131', 'Fatou Ba',
 'fatou.ba@company.com',         '1994-12-09', 'c0000000-0000-0000-0000-000000000009'),
('00000084-0000-4000-8000-000000000084', 'EMP-000132', 'Yasmin Al-Rashid',
 'yasmin.al-rashid@company.com', '1990-05-23', 'c0000000-0000-0000-0000-000000000005'),
('00000085-0000-4000-8000-000000000085', 'EMP-000133', 'Liam O''Brien',
 'liam.obrien@company.com',      '1992-08-19', 'c0000000-0000-0000-0000-000000000006'),
('00000086-0000-4000-8000-000000000086', 'EMP-000134', 'Sophie Leroy',
 'sophie.leroy@company.com',     '1988-11-06', 'c0000000-0000-0000-0000-000000000003'),
('00000087-0000-4000-8000-000000000087', 'EMP-000135', 'Kofi Adjei',
 'kofi.adjei@company.com',       '1986-04-14', 'c0000000-0000-0000-0000-000000000005'),
('00000088-0000-4000-8000-000000000088', 'EMP-000136', 'Emma Rossi',
 'emma.rossi@company.com',       '1995-07-01', 'c0000000-0000-0000-0000-000000000002'),
('00000089-0000-4000-8000-000000000089', 'EMP-000137', 'Rajesh Kumar',
 'rajesh.kumar@company.com',     '1991-11-17', 'c0000000-0000-0000-0000-000000000004'),
('0000008a-0000-4000-8000-00000000008a', 'EMP-000138', 'Olivia Green',
 'olivia.green@company.com',     '1989-03-12', 'c0000000-0000-0000-0000-000000000007'),
('0000008b-0000-4000-8000-00000000008b', 'EMP-000139', 'Gabriela Lopez',
 'gabriela.lopez@company.com',   '1993-08-06', 'c0000000-0000-0000-0000-000000000008'),
('0000008c-0000-4000-8000-00000000008c', 'EMP-000140', 'Min Joon Kim',
 'minjoon.kim@company.com',      '1990-12-29', 'c0000000-0000-0000-0000-00000000000a'),
('0000008d-0000-4000-8000-00000000008d', 'EMP-000141', 'Emma Dubois',
 'emma.dubois@company.com',      '1987-04-08', 'c0000000-0000-0000-0000-000000000003'),
('0000008e-0000-4000-8000-00000000008e', 'EMP-000142', 'Michael Brown',
 'michael.brown@company.com',    '1985-09-26', 'c0000000-0000-0000-0000-000000000001'),
('0000008f-0000-4000-8000-00000000008f', 'EMP-000143', 'Aissatou Diallo',
 'aissatou.diallo@company.com',  '1994-01-20', 'c0000000-0000-0000-0000-000000000009'),
('00000090-0000-4000-8000-000000000090', 'EMP-000144', 'Lukas Wagner',
 'lukas.wagner@company.com',     '1992-07-03', 'c0000000-0000-0000-0000-000000000002'),
('00000091-0000-4000-8000-000000000091', 'EMP-000145', 'Priya Menon',
 'priya.menon@company.com',      '1988-05-27', 'c0000000-0000-0000-0000-000000000004'),
('00000092-0000-4000-8000-000000000092', 'EMP-000146', 'James Taylor',
 'james.taylor@company.com',     '1983-11-19', 'c0000000-0000-0000-0000-000000000007'),
('00000093-0000-4000-8000-000000000093', 'EMP-000147', 'Fatima Sow',
 'fatima.sow@company.com',       '1995-04-11', 'c0000000-0000-0000-0000-000000000009'),
('00000094-0000-4000-8000-000000000094', 'EMP-000148', 'Sofia Ferrari',
 'sofia.ferrari@company.com',    '1991-08-29', 'c0000000-0000-0000-0000-000000000002'),
('00000095-0000-4000-8000-000000000095', 'EMP-000149', 'Mason Clarke',
 'mason.clarke@company.com',     '1990-10-06', 'c0000000-0000-0000-0000-000000000006'),
('00000096-0000-4000-8000-000000000096', 'EMP-000150', 'Nadia Diop',
 'nadia.diop@company.com',       '1989-02-08', 'c0000000-0000-0000-0000-000000000009');


 -- ---------------------------------------------------------------------------
 -- Top-up to 10,000 via WITH RECURSIVE, using per-region name tables inline.
 -- Each row of `n` produces one employee. Region, country, department, role,
 -- employment type, birth date, and name are all deterministic functions of n.
 -- ---------------------------------------------------------------------------
 WITH RECURSIVE
 -- Region name tables (20 first names + 20 last names per region).
 eu_first(f, i) AS (VALUES
   ('Anna',0),('Lukas',1),('Mia',2),('Noah',3),('Emma',4),('Felix',5),
   ('Sofia',6),('Leon',7),('Hannah',8),('Paul',9),('Lena',10),('Jonas',11),
   ('Clara',12),('Elias',13),('Marie',14),('David',15),('Julia',16),('Simon',17),
   ('Sarah',18),('Nico',19)),
 eu_last(l, i) AS (VALUES
   ('Muller',0),('Schmidt',1),('Schneider',2),('Fischer',3),('Weber',4),
   ('Dupont',5),('Martin',6),('Bernard',7),('Petit',8),('Durand',9),
   ('Rossi',10),('Russo',11),('Smith',12),('Jones',13),('Brown',14),
   ('Taylor',15),('Wilson',16),('Evans',17),('Walker',18),('Harris',19)),
 am_first(f, i) AS (VALUES
   ('Emily',0),('Jacob',1),('Madison',2),('Michael',3),('Ava',4),('Ethan',5),
   ('Isabella',6),('Liam',7),('Olivia',8),('Noah',9),('Sophia',10),('Mason',11),
   ('Lucas',12),('Carlos',13),('Camila',14),('Diego',15),('Valentina',16),
   ('Mateo',17),('Sofia',18),('Gabriel',19)),
 am_last(l, i) AS (VALUES
   ('Smith',0),('Johnson',1),('Williams',2),('Brown',3),('Garcia',4),
   ('Martinez',5),('Lopez',6),('Hernandez',7),('Silva',8),('Santos',9),
   ('Oliveira',10),('Pereira',11),('Costa',12),('Gomez',13),('Diaz',14),
   ('Reyes',15),('Morales',16),('Torres',17),('Ramirez',18),('Flores',19)),
 as_first(f, i) AS (VALUES
   ('Wei',0),('Li',1),('Priya',2),('Arjun',3),('Mei',4),('Raj',5),
   ('Yuki',6),('Hiroshi',7),('Ananya',8),('Rahul',9),('Xia',10),('Chen',11),
   ('Sakura',12),('Kenji',13),('Neha',14),('Vikram',15),('Ming',16),('Yan',17),
   ('Aiko',18),('Ravi',19)),
 as_last(l, i) AS (VALUES
   ('Zhang',0),('Wang',1),('Li',2),('Chen',3),('Liu',4),
   ('Sharma',5),('Patel',6),('Singh',7),('Kumar',8),('Gupta',9),
   ('Tanaka',10),('Sato',11),('Suzuki',12),('Watanabe',13),('Ito',14),
   ('Yamamoto',15),('Nakamura',16),('Kobayashi',17),('Kato',18),('Yoshida',19)),
 af_first(f, i) AS (VALUES
   ('Amadou',0),('Fatima',1),('Kwame',2),('Aisha',3),('Omar',4),('Zara',5),
   ('Kofi',6),('Nadia',7),('Ibrahim',8),('Leila',9),('Yaw',10),('Ama',11),
   ('Moussa',12),('Aminata',13),('Kwesi',14),('Akosua',15),('Cheikh',16),
   ('Mariama',17),('Emeka',18),('Chiamaka',19)),
 af_last(l, i) AS (VALUES
   ('Diallo',0),('Al-Hassan',1),('Mensah',2),('Bello',3),('Traore',4),
   ('Ouattara',5),('Konate',6),('Bamba',7),('Sow',8),('Camara',9),
   ('Cisse',10),('Coulibaly',11),('Diop',12),('Ndiaye',13),('Fall',14),
   ('Ba',15),('Sy',16),('Sarr',17),('Faye',18),('Ndiaye',19)),

 -- Top-up range: 151..10000.
 nums(n) AS (
   SELECT 151 UNION ALL SELECT n + 1 FROM nums WHERE n < 10000
 ),
 -- Region per n, matching the 30/25/20/15/10 split.
 tagged(n, region) AS (
   SELECT n,
     CASE
       WHEN n <= 3149 THEN 'EUROPE'
       WHEN n <= 5648 THEN 'AMERICA'
       WHEN n <= 7647 THEN 'ASIA'
       WHEN n <= 9146 THEN 'AFRICA'
       ELSE 'NULL_ORIGIN'
     END
   FROM nums
 ),
 -- Country per region. NULL_ORIGIN cycles all 10 countries by n % 10.
 with_country(n, region, origin_country_id, country_id) AS (
   SELECT n, region,
     CASE region
       WHEN 'EUROPE'      THEN CASE n % 3
                               WHEN 0 THEN 'c0000000-0000-0000-0000-000000000002'
                               WHEN 1 THEN 'c0000000-0000-0000-0000-000000000003'
                               ELSE        'c0000000-0000-0000-0000-000000000007'
                             END
       WHEN 'AMERICA'     THEN CASE n % 3
                               WHEN 0 THEN 'c0000000-0000-0000-0000-000000000001'
                               WHEN 1 THEN 'c0000000-0000-0000-0000-000000000006'
                               ELSE        'c0000000-0000-0000-0000-000000000008'
                             END
       WHEN 'ASIA'        THEN CASE n % 2
                               WHEN 0 THEN 'c0000000-0000-0000-0000-000000000004'
                               ELSE        'c0000000-0000-0000-0000-00000000000a'
                             END
       WHEN 'AFRICA'      THEN CASE n % 2
                               WHEN 0 THEN 'c0000000-0000-0000-0000-000000000005'
                               ELSE        'c0000000-0000-0000-0000-000000000009'
                             END
       ELSE                    'c0000000-0000-0000-0000-000000000001'
     END,
     -- Assignment country: same as origin for named regions, cycles all for NULL_ORIGIN.
     CASE region
       WHEN 'EUROPE'      THEN CASE n % 3
                               WHEN 0 THEN 'c0000000-0000-0000-0000-000000000002'
                               WHEN 1 THEN 'c0000000-0000-0000-0000-000000000003'
                               ELSE        'c0000000-0000-0000-0000-000000000007'
                             END
       WHEN 'AMERICA'     THEN CASE n % 3
                               WHEN 0 THEN 'c0000000-0000-0000-0000-000000000001'
                               WHEN 1 THEN 'c0000000-0000-0000-0000-000000000006'
                               ELSE        'c0000000-0000-0000-0000-000000000008'
                             END
       WHEN 'ASIA'        THEN CASE n % 2
                               WHEN 0 THEN 'c0000000-0000-0000-0000-000000000004'
                               ELSE        'c0000000-0000-0000-0000-00000000000a'
                             END
       WHEN 'AFRICA'      THEN CASE n % 2
                               WHEN 0 THEN 'c0000000-0000-0000-0000-000000000005'
                               ELSE        'c0000000-0000-0000-0000-000000000009'
                             END
       ELSE CASE n % 10
              WHEN 0 THEN 'c0000000-0000-0000-0000-000000000001'
              WHEN 1 THEN 'c0000000-0000-0000-0000-000000000002'
              WHEN 2 THEN 'c0000000-0000-0000-0000-000000000003'
              WHEN 3 THEN 'c0000000-0000-0000-0000-000000000004'
              WHEN 4 THEN 'c0000000-0000-0000-0000-000000000005'
              WHEN 5 THEN 'c0000000-0000-0000-0000-000000000006'
              WHEN 6 THEN 'c0000000-0000-0000-0000-000000000007'
              WHEN 7 THEN 'c0000000-0000-0000-0000-000000000008'
              WHEN 8 THEN 'c0000000-0000-0000-0000-000000000009'
              ELSE        'c0000000-0000-0000-0000-00000000000a'
            END
     END
   FROM tagged
 ),
 -- Name per n, using the region's tables.
 named(n, region, origin_country_id, country_id, first, last) AS (
   SELECT wc.n, wc.region, wc.origin_country_id, wc.country_id,
     CASE wc.region
       WHEN 'EUROPE'  THEN (SELECT f FROM eu_first WHERE i = wc.n % 20)
       WHEN 'AMERICA' THEN (SELECT f FROM am_first WHERE i = wc.n % 20)
       WHEN 'ASIA'    THEN (SELECT f FROM as_first WHERE i = wc.n % 20)
       WHEN 'AFRICA'  THEN (SELECT f FROM af_first WHERE i = wc.n % 20)
       ELSE 'Casey'
     END,
     CASE wc.region
       WHEN 'EUROPE'  THEN (SELECT l FROM eu_last WHERE i = wc.n % 20)
       WHEN 'AMERICA' THEN (SELECT l FROM am_last WHERE i = wc.n % 20)
       WHEN 'ASIA'    THEN (SELECT l FROM as_last WHERE i = wc.n % 20)
       WHEN 'AFRICA'  THEN (SELECT l FROM af_last WHERE i = wc.n % 20)
       ELSE 'Morgan'
     END
   FROM with_country wc
 )
 INSERT INTO employees (id, matricule, name, email, birth_date, origin_country_id)
 SELECT
   printf('%08x-0000-4000-8000-%012x', n, n),
   printf('EMP-%06d', n),
   first || ' ' || last,
   lower(first) || '.' || lower(replace(last, ' ', '')) || '.' || printf('%06d', n)
     || '@company.com',
   -- Birth date in [1970-01-01, 2000-12-31], by n.
   date('1970-01-01', '+' || ((n * 37) % 11323) || ' days'),
   origin_country_id
 FROM named;


 -- ---------------------------------------------------------------------------
 -- One assignment per employee, in effect from 2025-10-01 with no end date.
 -- Department and role cycle by n so every (department, role) combo is hit;
 -- employment type is FULL_TIME on 6 of 7 n's, PART_TIME on the seventh.
 -- ---------------------------------------------------------------------------
 WITH RECURSIVE
 nums(n) AS (
   SELECT 1 UNION ALL SELECT n + 1 FROM nums WHERE n < 10000
 )
 INSERT INTO employee_periodic_assignments
     (id, employee_id, start_date, end_date, country_id,
      department_id, role_id, employment_type, in_effect)
 SELECT
   printf('%08x-0000-4000-9000-%012x', n, n),
   printf('%08x-0000-4000-8000-%012x', n, n),
   '2025-10-01',
   NULL,
   -- Assignment country: read from employees.origin_country_id when present,
   -- otherwise fall back to a country by n % 10. This keeps the assignment
   -- country consistent with the employee's origin where origin is known.
   COALESCE(
     (SELECT origin_country_id FROM employees
      WHERE id = printf('%08x-0000-4000-8000-%012x', n, n)),
     CASE n % 10
       WHEN 0 THEN 'c0000000-0000-0000-0000-000000000001'
       WHEN 1 THEN 'c0000000-0000-0000-0000-000000000002'
       WHEN 2 THEN 'c0000000-0000-0000-0000-000000000003'
       WHEN 3 THEN 'c0000000-0000-0000-0000-000000000004'
       WHEN 4 THEN 'c0000000-0000-0000-0000-000000000005'
       WHEN 5 THEN 'c0000000-0000-0000-0000-000000000006'
       WHEN 6 THEN 'c0000000-0000-0000-0000-000000000007'
       WHEN 7 THEN 'c0000000-0000-0000-0000-000000000008'
       WHEN 8 THEN 'c0000000-0000-0000-0000-000000000009'
       ELSE        'c0000000-0000-0000-0000-00000000000a'
     END
   ),
   -- Department cycle: n % 10 maps to the 10 departments by fixed id.
   CASE n % 10
     WHEN 0 THEN 'd0000000-0000-0000-0000-000000000001'
     WHEN 1 THEN 'd0000000-0000-0000-0000-000000000002'
     WHEN 2 THEN 'd0000000-0000-0000-0000-000000000003'
     WHEN 3 THEN 'd0000000-0000-0000-0000-000000000004'
     WHEN 4 THEN 'd0000000-0000-0000-0000-000000000005'
     WHEN 5 THEN 'd0000000-0000-0000-0000-000000000006'
     WHEN 6 THEN 'd0000000-0000-0000-0000-000000000007'
     WHEN 7 THEN 'd0000000-0000-0000-0000-000000000008'
     WHEN 8 THEN 'd0000000-0000-0000-0000-000000000009'
     ELSE        'd0000000-0000-0000-0000-00000000000a'
   END,
   -- Role cycle: n % 12 maps to the 12 roles.
   CASE n % 12
     WHEN 0 THEN 'r0000000-0000-0000-0000-000000000001'
     WHEN 1 THEN 'r0000000-0000-0000-0000-000000000002'
     WHEN 2 THEN 'r0000000-0000-0000-0000-000000000003'
     WHEN 3 THEN 'r0000000-0000-0000-0000-000000000004'
     WHEN 4 THEN 'r0000000-0000-0000-0000-000000000005'
     WHEN 5 THEN 'r0000000-0000-0000-0000-000000000006'
     WHEN 6 THEN 'r0000000-0000-0000-0000-000000000007'
     WHEN 7 THEN 'r0000000-0000-0000-0000-000000000008'
     WHEN 8 THEN 'r0000000-0000-0000-0000-000000000009'
     WHEN 9 THEN 'r0000000-0000-0000-0000-00000000000a'
     WHEN 10 THEN 'r0000000-0000-0000-0000-00000000000b'
     ELSE        'r0000000-0000-0000-0000-00000000000c'
   END,
   CASE WHEN n % 7 = 0 THEN 'PART_TIME' ELSE 'FULL_TIME' END,
   1
 FROM nums;