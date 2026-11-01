# Daily popular news

`GET /api/practice-contents/popular?type=NEWS&limit=10` requires the existing access-token authentication.
`type` defaults to NEWS (only NEWS is supported); `limit` defaults to 10 and accepts 1–10.
The normal success envelope contains `type`, `rankingBasis`, `periodStart`, `periodEnd`,
`generatedAt`, `nextUpdateAt`, and `items`. Each item has the public content summary fields,
contiguous `rank` (1–10), and `usageCount` (distinct completed learners).

The window is the previous calendar day in Asia/Seoul, from midnight inclusive to midnight exclusive.
COMPLETED sessions of active, non-deleted users count once per user and news article, even when
the article creates multiple sentence sessions. Failed, canceled, incomplete, private, hidden,
non-NEWS and future-publication content are excluded. No user IDs are returned.

Rank by usageCount descending, then the existing catalog order (publication time, creation time,
ID descending). If every usageCount is zero, rankingBasis is CATALOG_ORDER; otherwise it is
COMPLETED_USERS. Never display the provisional order as a measured popularity claim.

The midnight Asia/Seoul scheduler creates one persistent snapshot per date. Startup and reads
fill a missing snapshot. The unique date insert, snapshot rows and basis update share one
transaction, so concurrent requests and restarts cannot regenerate a completed daily snapshot.
Availability is checked on every read; withdrawn content disappears immediately and newly
published content can fill remaining slots with zero usage until the next snapshot.

V60 adds snapshot tables and a partial completed-session window index. V59 is already reserved
by the coaching-usage-limit branch. Existing content and training records are preserved.
