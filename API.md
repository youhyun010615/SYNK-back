# SYNK API 명세서

## Base URL
http://localhost:8080

## 인증
Authorization: Bearer {token}

## 공통 Response
{
"success": true,
"data": {},
"message": "응답 메시지"
}

---

## API 목록

### 인증 (Auth)
| 기능 | Method | URL | 토큰 |
|------|--------|-----|------|
| 카카오 로그인/회원가입 | POST | /api/auth/kakao | 불필요 |
| Google 로그인/회원가입 | POST | /api/auth/google | 불필요 |
| 로그아웃 | POST | /api/auth/logout | 필요 |

### 방 (Room)
| 기능 | Method | URL | 토큰 |
|------|--------|-----|------|
| 방 생성 | POST | /api/rooms | 필요 |
| 내 방 목록 조회 | GET | /api/rooms/my | 필요 |
| 방 참여 | POST | /api/rooms/join | 필요 |
| 초대 정보 조회 | GET | /api/rooms/{roomId}/invite | 필요 |
| 방 상세 조회 | GET | /api/rooms/{roomId} | 필요 |
| 방 설정 수정 | PATCH | /api/rooms/{roomId} | 필요 |
| 방 나가기 | DELETE | /api/rooms/{roomId}/leave | 필요 |
| 방 멤버 목록 조회 | GET | /api/rooms/{roomId}/members | 필요 |
| 방의 앨범 목록 조회 | GET | /api/rooms/{roomId}/albums | 필요 |
| SYNKLOG 생성 | POST | /api/rooms/{roomId}/albums/{date}/synklog | 필요 |
| 특정 날짜 SYNKLOG 조회 | GET | /api/rooms/{roomId}/albums/{date}/synklog | 필요 |
| 채팅 메시지 목록 조회 | GET | /api/rooms/{roomId}/chats | 필요 |
| 채팅 메시지 전송 | POST | /api/rooms/{roomId}/chats | 필요 |
| 채팅 메시지 리액션 추가 | POST | /api/rooms/{roomId}/chats/{messageId}/reactions | 필요 |

### 미션 (Mission)
| 기능 | Method | URL | 토큰 |
|------|--------|-----|------|
| 진행 중인 미션 조회 | GET | /api/missions/active | 필요 |
| 미션 참여 현황 조회 | GET | /api/missions/{missionId}/participants | 필요 |

### 제출 (Submission)
| 기능 | Method | URL | 토큰 |
|------|--------|-----|------|
| 미션 제출 | POST | /api/submissions | 필요 |
| 제출 현황 조회 | GET | /api/submissions/missions/{missionId} | 불필요 |
| 개별 영상 조회 | GET | /api/submissions/{submissionId} | 필요 |

### 알림 (Notifications)
| 기능 | Method | URL | 토큰 |
|------|--------|-----|------|
| 알림 목록 조회 | GET | /api/notifications | 필요 |

### 도감 (Collections)
| 기능 | Method | URL | 토큰 |
|------|--------|-----|------|
| 도감 목록 조회 | GET | /api/collections | 필요 |
| 미션 상세 조회 | GET | /api/collections/missions/{missionId} | 필요 |

### 유저 (User)
| 기능 | Method | URL | 토큰 |
|------|--------|-----|------|
| 내 프로필 조회 | GET | /api/users/me | 필요 |
| 프로필 수정 | PATCH | /api/users/me | 필요 |
| 알림 설정 수정 | PATCH | /api/users/me/notifications | 필요 |
| 회원 탈퇴 | DELETE | /api/users/me | 필요 |

---

## 상세 명세

### 인증 (Auth)

#### POST /api/auth/kakao
설명: 카카오 엑세스 토큰으로 로그인/회원가입 처리
Request:
{
"accessToken": "ya29.a0AfH6..."
}
Response (기존 회원):
{
"success": true,
"data": {
"token": "eyJhbGciOiJ...",
"isNewUser": false,
"userId": 1,
"name": "유현",
"profileImage": "😊"
},
"message": "로그인 성공"
}
Response (신규 회원):
{
"success": true,
"data": {
"token": "eyJhbGciOiJ...",
"isNewUser": true,
"userId": 2,
"name": "홍길동",
"profileImage": null
},
"message": "회원가입 및 로그인 성공"
}
Status:
- 200: 로그인/회원가입 성공
- 401: 유효하지 않은 카카오 토큰

#### POST /api/auth/google
설명: Google 엑세스 토큰으로 로그인/회원가입 처리
Request:
{
"accessToken": "ya29.a0AfH6..."
}
Response (기존 회원):
{
"success": true,
"data": {
"token": "eyJhbGciOiJ...",
"isNewUser": false,
"userId": 1,
"name": "유현",
"profileImage": "😊"
},
"message": "로그인 성공"
}
Response (신규 회원):
{
"success": true,
"data": {
"token": "eyJhbGciOiJ...",
"isNewUser": true,
"userId": 2,
"name": "홍길동",
"profileImage": null
},
"message": "회원가입 및 로그인 성공"
}
Status:
- 200: 로그인/회원가입 성공
- 401: 유효하지 않은 Google 토큰

#### POST /api/auth/logout
설명: 로그아웃 처리
Request: 없음
Response:
{
"success": true,
"message": "로그아웃 완료"
}
Status:
- 200: 로그아웃 성공
- 401: 인증 토큰 없음

---

### 방 (Room)

#### POST /api/rooms
설명: 새로운 방을 생성합니다
Request:
{
"name": "새벽밤",
"thumbnail": "https://...",
"maxMembers": 5,
"dailyMissionCount": 5,
"missionStartTime": "10:00",
"missionEndTime": "22:00"
}
Response:
{
"success": true,
"data": {
"roomId": 1,
"code": "7X8K2",
"name": "새벽밤",
"thumbnail": "https://...",
"createdAt": "2026-05-07T22:30:00"
},
"message": "방 생성 완료"
}
Status:
- 200: 생성 성공
- 401: 인증 토큰 없음

#### GET /api/rooms/my
설명: 내가 참여한 방 목록 조회 (참여중/대기중 구분)
Request: 없음
Response:
{
"success": true,
"data": {
"active": [
{
"id": 1,
"name": "아침반",
"totalMissions": 5,
"completedMissions": 2,
"isAllCompleted": false,
"roomThumbnail": "https://...",
"memberProfiles": [
{"userId": 1, "profileImage": "😊"},
{"userId": 2, "profileImage": "🐱"},
{"userId": 3, "profileImage": "😂"},
{"userId": 4, "profileImage": "🍕"},
{"userId": 5, "profileImage": "🎀"}
]
},
{
"id": 2,
"name": "zz반",
"totalMissions": 3,
"completedMissions": 3,
"isAllCompleted": true,
"roomThumbnail": "https://...",
"memberProfiles": [
{"userId": 1, "profileImage": "😊"},
{"userId": 2, "profileImage": "🐱"},
{"userId": 3, "profileImage": "😂"},
{"userId": 4, "profileImage": "🍕"},
{"userId": 5, "profileImage": "🎀"}
]
}
],
"waiting": [
{
"id": 3,
"name": "축구부",
"currentMembers": 3,
"maxMembers": 5,
"waitingCount": 2,
"roomThumbnail": "https://...",
"memberProfiles": [
{"userId": 1, "profileImage": "😊"},
{"userId": 2, "profileImage": "🐱"},
{"userId": 3, "profileImage": "😂"}
]
}
]
},
"message": "방 목록 조회 성공"
}
Status:
- 200: 조회 성공
- 401: 인증 토큰 없음

#### POST /api/rooms/join
설명: 초대 코드를 입력하여 방에 참가합니다
Request:
{
"code": "7X8K2"
}
Response:
{
"success": true,
"data": {
"roomId": 1,
"roomName": "새벽반",
"currentMembers": 3,
"maxMembers": 5
},
"message": "방 참가 완료"
}
Status:
- 200: 참여 성공
- 400: 이미 참여 중인 방
- 401: 인증 토큰 없음
- 404: 존재하지 않는 코드

#### GET /api/rooms/{roomId}/invite
설명: 방 초대 정보 조회
Request: 없음
Response:
{
"success": true,
"data": {
"roomId": 1,
"roomName": "새벽밤",
"code": "7X8K2",
"inviteUrl": "synk.app/r/7X8K2",
"thumbnail": "https://..."
},
"message": "초대 정보 조회 성공"
}
Status:
- 200: 조회 성공
- 401: 인증 토큰 없음
- 403: 방 멤버가 아님
- 404: 존재하지 않는 방

#### GET /api/rooms/{roomId}
설명: 특정 방의 상세 정보를 조회합니다
Request: 없음
Response:
{
"success": true,
"data": {
"id": 1,
"name": "새벽밤",
"code": "7X8K2",
"ownerId": 1,
"currentMembers": 5,
"maxMembers": 5,
"dailyMissionCount": 5,
"missionStartTime": "10:00",
"missionEndTime": "22:00",
"members": [
{"userId": 1, "name": "유현", "profileImage": "😊"},
{"userId": 2, "name": "아영", "profileImage": "🐱"},
{"userId": 3, "name": "지민", "profileImage": "😂"},
{"userId": 4, "name": "수현", "profileImage": "🍕"},
{"userId": 5, "name": "대주", "profileImage": "🎀"}
],
"recentAlbums": [
{"date": "2026.05.07", "thumbnail": "https://..."},
{"date": "2026.05.03", "thumbnail": "https://..."}
]
},
"message": "방 상세 조회 성공"
}
Status:
- 200: 조회 성공
- 401: 인증 토큰 없음
- 403: 방 멤버가 아님
- 404: 존재하지 않는 방

#### PATCH /api/rooms/{roomId}
설명: 방 설정 수정 (방장만 가능)
Request:
{
"name": "새벽밤",
"maxMembers": 5,
"dailyMissionCount": 5,
"missionStartTime": "10:00",
"missionEndTime": "22:00"
}
Response:
{
"success": true,
"message": "방 설정 수정 완료"
}
Status:
- 200: 수정 성공
- 401: 인증 토큰 없음
- 403: 방장이 아님
- 404: 존재하지 않는 방

#### GET /api/rooms/{roomId}/members
설명: 방 멤버 목록을 상세 조회합니다 (방 멤버만 가능)
Request: 없음
Response:
{
"success": true,
"data": [
{"userId": 1, "name": "유현", "profileImage": "😊", "isOwner": true, "joinedAt": "2026-05-07T22:30:00"},
{"userId": 2, "name": "아영", "profileImage": "🐱", "isOwner": false, "joinedAt": "2026-05-07T22:31:00"}
],
"message": "멤버 목록 조회 성공"
}
Status:
- 200: 조회 성공
- 401: 인증 토큰 없음
- 403: 방 멤버가 아님
- 404: 존재하지 않는 방

#### DELETE /api/rooms/{roomId}/leave
설명: 특정 방에서 나갑니다
Request: 없음
Response:
{
"success": true,
"message": "방 나가기 완료"
}
Status:
- 200: 나가기 성공
- 401: 인증 토큰 없음
- 403: 방 멤버가 아님
- 404: 존재하지 않는 방

#### GET /api/rooms/{roomId}/albums
설명: 특정 방의 날짜별 앨범 목록 조회 (전체 보기)
Request: 없음
Response:
{
"success": true,
"data": [
{
"date": "2026.05.07",
"thumbnail": "https://...",
"memberProfiles": [
{"userId": 1, "profileImage": "😊"},
{"userId": 2, "profileImage": "🐱"},
{"userId": 3, "profileImage": "😂"},
{"userId": 4, "profileImage": "🍕"},
{"userId": 5, "profileImage": "🎀"}
]
},
{
"date": "2026.05.03",
"thumbnail": "https://...",
"memberProfiles": [
{"userId": 1, "profileImage": "😊"},
{"userId": 2, "profileImage": "🐱"},
{"userId": 3, "profileImage": "😂"},
{"userId": 4, "profileImage": "🍕"}
]
},
{
"date": "2026.05.01",
"thumbnail": "https://...",
"memberProfiles": [
{"userId": 1, "profileImage": "😊"},
{"userId": 2, "profileImage": "🐱"},
{"userId": 3, "profileImage": "😂"},
{"userId": 4, "profileImage": "🍕"}
]
}
],
"message": "앨범 목록 조회 성공"
}
Status:
- 200: 조회 성공
- 401: 인증 토큰 없음
- 404: 존재하지 않는 방

#### POST /api/rooms/{roomId}/albums/{date}/synklog
설명: 특정 날짜의 SYNKLOG를 생성 요청합니다
Request: 없음
Response:
{
"success": true,
"data": {
"synklogId": 1,
"status": "PROCESSING"
},
"message": "SYNKLOG 생성 요청 완료"
}
Status:
- 200: 생성 요청 성공
- 400: 이미 생성된 SYNKLOG 존재
- 401: 인증 토큰 없음

#### GET /api/rooms/{roomId}/albums/{date}/synklog
설명: 특정 날짜의 SYNKLOG 조회 (폴링용)
Request: 없음
Response (PROCESSING):
{
"success": true,
"data": {
"synklogId": 1,
"date": "2026.05.07",
"status": "PROCESSING",
"synklogVideoUrl": null
},
"message": "SYNKLOG 생성 중"
}
Response (COMPLETED):
{
"success": true,
"data": {
"synklogId": 1,
"date": "2026.05.07",
"status": "COMPLETED",
"synklogVideoUrl": "https://...",
"thumbnail": "https://...",
"missions": [
{"missionTitle": "지금 내 표정 그대로 찍기"},
{"missionTitle": "지금 손에 있는 것 찍기"}
]
},
"message": "SYNKLOG 조회 성공"
}
Status:
- 200: 조회 성공
- 401: 인증 토큰 없음
- 404: SYNKLOG 없음

#### GET /api/rooms/{roomId}/chats
설명: 특정 방의 채팅 메시지 목록 조회
Request: 없음
Response:
{
"success": true,
"data": {
"roomName": "새벽밤",
"memberCount": 5,
"todayMissionCompleted": true,
"todayMissionDate": "2026-05-07",
"messages": [
{
"messageId": 1,
"userId": 1,
"userName": "유현",
"profileImage": "https://...",
"messageType": "TEXT",
"content": "이번에 잘 찍힌 듯",
"createdAt": "2026-05-07T22:35:00",
"isMyMessage": false,
"reactions": null
},
{
"messageId": 2,
"userId": 3,
"userName": "지민",
"profileImage": "https://...",
"messageType": "TEXT",
"content": "ㅋㅋㅋㅋㅋㅋㅋ 인정~",
"createdAt": "2026-05-07T22:35:30",
"isMyMessage": false,
"reactions": null
},
{
"messageId": 3,
"userId": 2,
"userName": "아영",
"profileImage": "https://...",
"messageType": "TEXT",
"content": "우리반 최고 ㅎㅎ",
"createdAt": "2026-05-07T22:36:00",
"isMyMessage": true,
"reactions": null
},
{
"messageId": 4,
"userId": 5,
"userName": "대주",
"profileImage": "https://...",
"messageType": "TEXT",
"content": "릴스 조회수 터지자✨",
"createdAt": "2026-05-07T22:36:30",
"isMyMessage": false,
"reactions": [
{"emoji": "❤️", "count": 3}
]
}
]
},
"message": "채팅 조회 성공"
}
Status:
- 200: 조회 성공
- 401: 인증 토큰 없음
- 403: 방 멤버가 아님
- 404: 존재하지 않는 방

#### POST /api/rooms/{roomId}/chats
설명: 채팅 메시지를 전송합니다
Request:
{
"messageType": "TEXT",
"content": "우리반 최고 ㅎㅎ"
}
Response:
{
"success": true,
"data": {
"messageId": 3,
"createdAt": "2026-05-07T22:36:00"
},
"message": "메시지 전송 완료"
}
Status:
- 200: 전송 성공
- 401: 인증 토큰 없음
- 403: 방 멤버가 아님

#### POST /api/rooms/{roomId}/chats/{messageId}/reactions
설명: 채팅 메시지에 리액션을 추가합니다
Request:
{
"emoji": "❤️"
}
Response:
{
"success": true,
"message": "리액션 추가 완료"
}
Status:
- 200: 추가 성공
- 401: 인증 토큰 없음

---

### 미션 (Mission)

#### GET /api/missions/active
설명: roomId 없으면 모든 방, 있으면 특정 방의 진행 중인 미션 조회
Query Parameter: roomId (선택)
Request: 없음
Response (특정 방 - roomId 있을 때):
{
"success": true,
"data": {
"id": 1,
"roomId": 1,
"roomName": "새벽밤",
"title": "지금 내 표정 그대로 찍기",
"description": "꾸미지 말고, 있는 그대로!",
"missionDate": "2026-05-26",
"slotTime": "14:30",
"deadline": "2026-05-26T14:35:00",
"remainingSeconds": 139
},
"message": "진행 중인 미션 조회 성공"
}
Response (모든 방 - roomId 없을 때):
{
"success": true,
"data": [
{
"id": 1,
"roomId": 1,
"roomName": "새벽밤",
"title": "지금 내 표정 그대로 찍기",
"description": "꾸미지 말고, 있는 그대로!",
"missionDate": "2026-05-26",
"slotTime": "14:30",
"deadline": "2026-05-26T14:35:00",
"remainingSeconds": 139
},
{
"id": 2,
"roomId": 2,
"roomName": "아침반",
"title": "지금 손에 있는 것 찍기",
"description": "손에 뭐가 있나요?",
"missionDate": "2026-05-26",
"slotTime": "14:30",
"deadline": "2026-05-26T14:35:00",
"remainingSeconds": 139
}
],
"message": "진행 중인 미션 조회 성공"
}
Response (진행 중인 미션 없을 때):
{
"success": true,
"data": null,
"message": "진행 중인 미션 없음"
}
Status:
- 200: 조회 성공
- 401: 인증 토큰 없음

#### GET /api/missions/{missionId}/participants
설명: 미션의 실시간 참여 현황을 조회합니다 (폴링용)
Request: 없음
Response:
{
"success": true,
"data": {
"missionId": 1,
"title": "지금 내 표정 그대로 찍기",
"missionDate": "2026-05-26",
"slotTime": "14:30",
"deadline": "2026-05-26T14:35:00",
"remainingSeconds": 139,
"status": "ACTIVE",
"totalMembers": 5,
"submittedCount": 3,
"participants": [
{"userId": 1, "name": "유현", "profileImage": "😊", "status": "SUBMITTED", "submittedAt": "2026-05-26T14:31:00"},
{"userId": 2, "name": "아영", "profileImage": "🐱", "status": "SUBMITTED", "submittedAt": "2026-05-26T14:32:00"},
{"userId": 3, "name": "지민", "profileImage": "😂", "status": "SUBMITTED", "submittedAt": "2026-05-26T14:33:00"},
{"userId": 4, "name": "수현", "profileImage": "🍕", "status": "PENDING", "submittedAt": null},
{"userId": 5, "name": "대주", "profileImage": "🎀", "status": "PENDING", "submittedAt": null}
]
},
"message": "참여 현황 조회 성공"
}
Status:
- 200: 조회 성공
- 401: 인증 토큰 없음
- 403: 방 멤버가 아님
- 404: 존재하지 않는 미션

---

### 제출 (Submission)

#### POST /api/submissions
설명: 촬영한 영상을 제출합니다
Request:
{
"roomId": 1,
"missionId": 5,
"videoUrl": "https://..."
}
Response:
{
"success": true,
"data": {
"id": 1,
"submittedAt": "2026-05-11T23:30:45"
},
"message": "제출 완료"
}
Status:
- 200: 제출 성공
- 400: 이미 제출한 미션
- 401: 인증 토큰 없음
- 404: 존재하지 않는 미션

#### GET /api/submissions/missions/{missionId}
설명: 특정 미션의 모든 제출물을 조회합니다
Request: 없음
Response:
{
"success": true,
"data": {
"remainingSeconds": 139,
"participants": [
{"name": "유현", "profileImage": "https://...", "status": "완료"},
{"name": "아영", "profileImage": "https://...", "status": "완료"},
{"name": "지민", "profileImage": "https://...", "status": "완료"},
{"name": "수현", "profileImage": "https://...", "status": "미완료"},
{"name": "대주", "profileImage": "https://...", "status": "미완료"}
]
},
"message": "참여 현황 조회 성공"
}
Status:
- 200: 조회 성공
- 404: 존재하지 않는 미션

#### GET /api/submissions/{submissionId}
설명: 콜라주에서 특정 사용자의 영상을 조회합니다
Request: 없음
Response:
{
"success": true,
"data": {
"id": 1,
"userId": 4,
"userName": "수현",
"profileImage": "🍕",
"missionTitle": "지금 내 표정 그대로 찍기",
"submittedAt": "2026-05-07T22:30:37",
"videoUrl": "https://..."
},
"message": "영상 조회 성공"
}
Status:
- 200: 조회 성공
- 401: 인증 토큰 없음
- 404: 존재하지 않는 제출

---

### 알림 (Notifications)

#### GET /api/notifications
설명: 내 알림 목록을 조회합니다 (오늘/이번 주 구분)
Request: 없음
Response:
{
"success": true,
"data": {
"today": [
{
"id": 1,
"type": "MISSION_START",
"title": "새벽밤에서 미션이 울렸어요",
"content": "지금 내 표정 그대로 찍기 · 22:30",
"createdAt": "2026-05-07T22:30:00",
"isRead": false,
"relatedId": 5
},
{
"id": 2,
"type": "MISSION_COMPLETE",
"title": "결과가 도착했어요",
"content": "새벽밤 · 5명 모두 참여 · 22:35",
"createdAt": "2026-05-07T22:35:00",
"isRead": true,
"relatedId": 5
},
{
"id": 3,
"type": "SYNKLOG_CREATED",
"title": "SYNKLOG 생성 완료",
"content": "새벽밤 · 2026.05.07 · 22:40",
"createdAt": "2026-05-07T22:40:00",
"isRead": true,
"relatedId": 1
}
],
"thisWeek": [
{
"id": 4,
"type": "MEMBER_JOIN",
"title": "지민님이 대학동기에 들어왔어요",
"content": "2일 전",
"createdAt": "2026-05-05T14:20:00",
"isRead": true,
"relatedId": 2
},
{
"id": 5,
"type": "MEMBER_JOIN",
"title": "수현님이 새벽밤에 들어왔어요",
"content": "4일 전",
"createdAt": "2026-05-03T10:00:00",
"isRead": true,
"relatedId": 1
}
]
},
"message": "알림 조회 성공"
}
Status:
- 200: 조회 성공
- 401: 인증 토큰 없음

---

### 도감 (Collections)

#### GET /api/collections
설명: 내가 완료한 미션 도감을 조회합니다
Request: 없음
Response:
{
"success": true,
"data": {
"completionRate": 42,
"completedCount": 38,
"totalCount": 90,
"missions": [
{"missionId": 1, "title": "지금 내 표정 그대로 찍기", "thumbnail": "https://...", "completedTimes": 14, "lastCompletedDate": "2026.05.07"},
{"missionId": 2, "title": "지금 손에 있는 것 찍기", "thumbnail": "https://...", "completedTimes": 8, "lastCompletedDate": "2026.05.05"},
{"missionId": 3, "title": "발밑 찍기", "thumbnail": "https://...", "completedTimes": 6, "lastCompletedDate": "2026.05.02"},
{"missionId": 4, "title": "하늘 찍기", "thumbnail": "https://...", "completedTimes": 5, "lastCompletedDate": "2026.04.30"}
]
},
"message": "도감 조회 성공"
}
Status:
- 200: 조회 성공
- 401: 인증 토큰 없음

#### GET /api/collections/missions/{missionId}
설명: 특정 미션의 상세 정보와 내 기록을 조회합니다
Request: 없음
Response:
{
"success": true,
"data": {
"missionId": 1,
"title": "지금 내 표정 그대로 찍기",
"description": "카메라 앞, 있는 그대로",
"completedTimes": 14,
"lastCompletedDate": "2026.05.07",
"records": [
{"recordId": 1, "roomName": "새벽밤", "date": "2026.05.07", "thumbnail": "https://...", "videoUrl": "https://..."},
{"recordId": 2, "roomName": "새벽밤", "date": "2026.04.27", "thumbnail": "https://...", "videoUrl": "https://..."},
{"recordId": 3, "roomName": "대학동기", "date": "2026.02.15", "thumbnail": "https://...", "videoUrl": "https://..."},
{"recordId": 4, "roomName": "여행빠", "date": "2026.01.17", "thumbnail": "https://...", "videoUrl": "https://..."}
]
},
"message": "미션 상세 조회 성공"
}
Status:
- 200: 조회 성공
- 401: 인증 토큰 없음
- 404: 존재하지 않는 미션

---

### 유저 (User)

#### GET /api/users/me
설명: 내 프로필 정보를 조회합니다
Request: 없음
Response:
{
"success": true,
"data": {
"userId": 1,
"name": "유현",
"username": "iam.synk",
"profileImage": "😊",
"missionNotification": true,
"resultNotification": true,
"highlightNotification": true
},
"message": "프로필 조회 성공"
}
Status:
- 200: 조회 성공
- 401: 인증 토큰 없음

#### PATCH /api/users/me
설명: 내 프로필 정보를 수정합니다
Request:
{
"name": "유현",
"username": "iam.synk",
"profileImage": "😊"
}
Response:
{
"success": true,
"message": "프로필 수정 완료"
}
Status:
- 200: 수정 성공
- 401: 인증 토큰 없음

#### PATCH /api/users/me/notifications
설명: 알림 설정을 변경합니다
Request:
{
"missionNotification": true,
"resultNotification": true,
"highlightNotification": false
}
Response:
{
"success": true,
"message": "알림 설정 변경 완료"
}
Status:
- 200: 수정 성공
- 401: 인증 토큰 없음

#### DELETE /api/users/me
설명: 회원 탈퇴 처리합니다
Request: 없음
Response:
{
"success": true,
"message": "회원 탈퇴 완료"
}
Status:
- 200: 탈퇴 성공
- 401: 인증 토큰 없음