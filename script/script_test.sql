SELECT *
FROM event;

SELECT *
FROM event
WHERE to_tsvector('simple', content) @@ plainto_tsquery('simple', 'test 1');


TRUNCATE TABLE event;

DELETE FROM event
WHERE event_id = '0000005b0fc51e70b66db99ba1708b1a1b008c30db35d19d35146b3e09756029';

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
WHERE kind = 5;

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



SELECT "public"."event"."event_id",
       "public"."event"."pubkey",
       "public"."event"."created_at",
       "public"."event"."kind",
       "public"."event"."tags",
       "public"."event"."content",
       "public"."event"."sig"
FROM "public"."event"
WHERE "public"."event"."event_id" LIKE '0000%';


SELECT "public"."event"."event_id",
       "public"."event"."pubkey",
       "public"."event"."created_at",
       "public"."event"."kind",
       "public"."event"."tags",
       "public"."event"."content",
       "public"."event"."sig"
FROM "public"."event"
WHERE "public"."event"."event_id" LIKE '0000%'
   OR "public"."event"."event_id" LIKE '000%';



SELECT "public"."event"."event_id",
       "public"."event"."pubkey",
       "public"."event"."created_at",
       "public"."event"."kind",
       "public"."event"."tags",
       "public"."event"."content",
       "public"."event"."sig"
FROM "public"."event"
WHERE "public"."event"."event_id" IN (
                                      '42224859763652914db53052103f0b744df79dfc4efef7e950fc0802fc3df3c5',
                                      '2d773becf9fe47623405fa265d83db0fbd91a6148d0bfb72a9471422c2ecd511'
    )