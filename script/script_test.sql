SELECT *
FROM event;

TRUNCATE TABLE event;

EXPLAIN
SELECT * -- 71 - 100
FROM event
WHERE content = 'ยังดีที่ GitHub แจ้งมา มันตัดแล้วให้แก้รหัสผ่านแล้วใช้ได้';

EXPLAIN
SELECT * -- 60 - 44
FROM event
WHERE event_id = 'ecfdf5d329ae69bdca40f04a33a8f8447b83824f958a8db926430cd8b2aeb350';

SELECT *
FROM event
WHERE pubkey = 'e4b2c64f0e4e54abb34d5624cd040e05ecc77f0c467cc46e2cc4d5be98abe3e3'
  AND kind = 0;

-- 287 , 426
SELECT pg_size_pretty(pg_database_size('nostr')) AS size;

SELECT *
FROM event
WHERE kind = 0;

ROLLBACK;

SELECT pubkey,
       kind,
       jsonb_array_length(tags) AS member_count
FROM event
WHERE pubkey = 'e4b2c64f0e4e54abb34d5624cd040e05ecc77f0c467cc46e2cc4d5be98abe3e3'
  AND kind = 3;


SELECT *
FROM event
WHERE pubkey = 'e4b2c64f0e4e54abb34d5624cd040e05ecc77f0c467cc46e2cc4d5be98abe3e3'
  AND kind = 3
ORDER BY created_at DESC
LIMIT 1;



