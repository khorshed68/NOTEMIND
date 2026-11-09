# API documentation

All endpoints are under `/api` and exchange JSON unless noted. Protected endpoints need `Authorization: Bearer <accessToken>`.

Errors share one shape:

```json
{ "timestamp": "...", "status": 400, "error": "Bad Request", "message": "...", "path": "/api/...", "fieldErrors": { "email": "..." } }
```

Common statuses: `400` invalid input, `401` not signed in, `403` role or permission denied, `404` missing or not yours, `409` conflict, `413` file too large, `429` rate limited (with `Retry-After`), `503` AI provider not configured.

Lists return `{ content, page, size, totalElements, totalPages }` and accept `page` and `size`.

## Authentication

| Method | Path | Description |
|---|---|---|
| POST | `/auth/register` | `{name, email, password}`. Returns the access token and sets the refresh cookie |
| POST | `/auth/login` | `{email, password}` |
| POST | `/auth/refresh` | Uses the cookie and rotates it |
| POST | `/auth/logout` | Revokes the refresh token |
| GET / PUT | `/auth/me` | Read profile, or update `{name}` |
| POST | `/auth/me/password` | `{currentPassword, newPassword}` |

## Courses

| Method | Path | Description |
|---|---|---|
| GET / POST | `/courses` | List, or create `{name, courseCode?, description?}` |
| GET / PUT / DELETE | `/courses/{id}` | Read, update, delete |
| POST | `/courses/{id}/quiz` | Generate a quiz from the whole course |

## Documents

| Method | Path | Description |
|---|---|---|
| POST | `/documents` | Multipart `file`, with query `courseId` or `folderId` |
| GET | `/documents` | Optional `courseId`, `folderId` |
| GET / DELETE | `/documents/{id}` | Read, delete |
| GET | `/documents/{id}/status` | Processing status |
| POST | `/documents/{id}/retry` | Retry a failed document |
| GET | `/documents/{id}/preview` | The PDF file |
| GET | `/documents/{id}/pages/{n}` | Text of one page |
| POST | `/documents/{id}/quiz` | Generate a quiz from one document |

## Chats

| Method | Path | Description |
|---|---|---|
| GET / POST | `/chats` | List (`courseId`, `folderId`), or create `{courseId or folderId, title?}` |
| GET / PUT / DELETE | `/chats/{id}` | Read, rename `{title}`, delete |
| GET | `/chats/{id}/messages` | Conversation history |
| POST | `/chats/{id}/messages` | `{content, documentIds?}`. Waits for the full answer |
| POST | `/chats/{id}/messages/stream` | Same body. Server-Sent Events |

Stream events, in order: `status` `{stage}`, `sources` `[SourceRef]`, repeated `token` `{text}`, then `done` `{userMessage, assistantMessage}` or `error` `{message}`.

`SourceRef`: `{label, documentId, documentName, pageNumber, chunkId, snippet, similarity, cited}`.

## Quizzes

| Method | Path | Description |
|---|---|---|
| GET | `/quizzes` | Your quizzes |
| GET | `/quizzes/{id}` | Questions only, never answers |
| POST | `/quizzes/{id}/attempts` | `{answers: {questionId: answer}}`. Returns the graded result |
| GET | `/quizzes/{id}/attempts/latest` | Latest result, or 204 |

Generation body: `{questionCount (1-25), difficulty, types?}`.

## Shared folders

| Method | Path | Description |
|---|---|---|
| GET | `/folders` | Folders you own or belong to |
| POST | `/folders` | CLASS_REP or ADMIN only. `{name, courseName}` |
| GET / PUT / DELETE | `/folders/{id}` | Delete is owner only |
| GET / POST | `/folders/{id}/members` | `{email, permission: VIEW or MANAGE}` |
| DELETE | `/folders/{id}/members/{userId}` | Remove a member |

## Dashboard

`GET /dashboard/stats`, `GET /dashboard/activity`

## Admin (ADMIN role)

| Method | Path | Description |
|---|---|---|
| GET | `/admin/stats` | System statistics |
| GET | `/admin/users` | Optional search `q` |
| PATCH | `/admin/users/{id}/status` | `{enabled}` |
| PATCH | `/admin/users/{id}/role` | `{role}` |
| GET / DELETE | `/admin/folders`, `/admin/folders/{id}` | Manage shared resources |

Admins cannot read students' private documents.
