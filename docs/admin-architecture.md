# Headmaster/Admin Starter Architecture

## 1. Recommended Package Structure

```text
com.example.edutute
├── app
│   ├── AppContainer.kt
│   └── EdututeApp.kt
├── core
│   ├── common
│   │   ├── AppConstants.kt
│   │   └── FirestoreCollections.kt
│   ├── navigation
│   │   └── DrawerDestination.kt
│   ├── ui
│   │   ├── BaseFragment.kt
│   │   ├── UiState.kt
│   │   └── UiText.kt
│   └── util
│       ├── FirestoreExtensions.kt
│       └── ValidationUtils.kt
├── domain
│   ├── model
│   │   ├── enums + session models + admin entities
│   └── repository
│       ├── AuthRepository.kt
│       ├── InstitutionRepository.kt
│       ├── FacultyRepository.kt
│       ├── AcademicRepository.kt
│       ├── SubjectRepository.kt
│       └── StudentRepository.kt
├── data
│   ├── auth
│   │   ├── FirebaseAuthDataSource.kt
│   │   └── SessionResolver.kt
│   ├── firestore
│   │   ├── FirestoreDataSource.kt
│   │   └── FirestoreMappers.kt
│   └── repository
│       └── repository implementations
└── presentation
    ├── main
    ├── auth
    ├── dashboard
    ├── institution
    ├── faculty
    ├── classes
    ├── subjects
    └── students
```

## 2. Recommended Firestore Structure

### Top-level collections

```text
users/{uid}
institutions/{institutionId}
institution_codes/{normalizedCode}
```

### `users/{uid}`

Global auth-linked profile. This stays independent from institution-specific role data so the app can later support faculty login, role changes, or even multi-institution membership without restructuring auth storage.

Suggested fields:

- `uid`
- `email`
- `displayName`
- `phoneNumber`
- `photoUrl`
- `institutionId`
- `role`
- `linkedFacultyId`
- `accountStatus`
- `lastLoginAt`
- `createdAt`
- `updatedAt`

### `institutions/{institutionId}`

Master institution record.

Suggested fields:

- `id`
- `name`
- `code`
- `normalizedCode`
- `headmasterUid`
- `addressLine1`
- `addressLine2`
- `city`
- `state`
- `postalCode`
- `country`
- `contactEmail`
- `contactPhone`
- `currentSessionId`
- `setupStatus`
- `status`
- `createdAt`
- `updatedAt`

### `institution_codes/{normalizedCode}`

Small lookup collection used to protect institution code uniqueness.

Suggested fields:

- `institutionId`
- `code`
- `normalizedCode`
- `createdAt`

### Subcollections under `institutions/{institutionId}`

```text
faculty/{facultyId}
classes/{classId}
sections/{sectionId}
subjects/{subjectId}
students/{studentId}
academicSessions/{sessionId}
auditLogs/{logId}
meta/dashboardStats
```

### `institutions/{institutionId}/faculty/{facultyId}`

Institution faculty master records. These can exist before an auth account exists.

Suggested fields:

- `id`
- `institutionId`
- `authUid`
- `fullName`
- `fullNameLower`
- `email`
- `phoneNumber`
- `employeeCode`
- `designation`
- `qualification`
- `joiningDate`
- `status`
- `canLogin`
- `createdAt`
- `updatedAt`

Reasoning:

- Faculty management can begin before faculty login exists.
- Later invite flow can link `authUid` and create `users/{uid}` without changing faculty document IDs.
- Headmaster does not need a separate collection. The headmaster is represented by `users/{uid}` plus `institutions.headmasterUid`.

### `institutions/{institutionId}/classes/{classId}`

Institution-level master class definitions.

Suggested fields:

- `id`
- `name`
- `nameLower`
- `displayOrder`
- `status`
- `createdAt`
- `updatedAt`

### `institutions/{institutionId}/sections/{sectionId}`

Institution-level master section definitions.

Suggested fields:

- `id`
- `name`
- `nameLower`
- `displayOrder`
- `status`
- `createdAt`
- `updatedAt`

### `institutions/{institutionId}/subjects/{subjectId}`

Institution-level master subject definitions.

Suggested fields:

- `id`
- `code`
- `normalizedCode`
- `name`
- `nameLower`
- `shortName`
- `subjectType`
- `status`
- `createdAt`
- `updatedAt`

### `institutions/{institutionId}/students/{studentId}`

Institution-level student master record. This stores stable identity plus denormalized current assignment summary for admin list screens.

Suggested fields:

- `id`
- `institutionId`
- `admissionNumber`
- `firstName`
- `lastName`
- `fullName`
- `fullNameLower`
- `gender`
- `dateOfBirth`
- `guardianName`
- `guardianPhone`
- `email`
- `addressLine1`
- `addressLine2`
- `city`
- `state`
- `postalCode`
- `status`
- `currentSessionId`
- `currentClassSectionId`
- `currentClassSectionName`
- `currentRollNumber`
- `createdAt`
- `updatedAt`

### `institutions/{institutionId}/academicSessions/{sessionId}`

Academic session root document. Historical archive becomes natural because operational data can stay grouped by session.

Suggested fields:

- `id`
- `name` (`2026-2027`)
- `startDate`
- `endDate`
- `status` (`PLANNED`, `ACTIVE`, `ARCHIVED`)
- `isCurrent`
- `createdAt`
- `updatedAt`

### Session-scoped subcollections

```text
classSections/{classSectionId}
teacherAssignments/{assignmentId}
studentAssignments/{studentId}
timetableEntries/{timetableEntryId}
attendanceSessions/{attendanceSessionId}
exams/{examId}
marks/{markId}
```

### `classSections/{classSectionId}`

Links an institution-level class + section for a specific session.

Suggested fields:

- `id`
- `institutionId`
- `sessionId`
- `classId`
- `className`
- `classOrder`
- `sectionId`
- `sectionName`
- `sectionOrder`
- `displayName`
- `displayNameLower`
- `classTeacherUid`
- `classTeacherName`
- `coClassTeacherUid`
- `coClassTeacherName`
- `status`
- `createdAt`
- `updatedAt`

### `teacherAssignments/{assignmentId}`

Future-ready assignment relation between faculty, subject, and class-section.

Suggested fields:

- `id`
- `institutionId`
- `sessionId`
- `facultyUid`
- `facultyName`
- `subjectId`
- `subjectName`
- `subjectCode`
- `classSectionId`
- `classSectionName`
- `status`
- `createdAt`
- `updatedAt`

### `studentAssignments/{studentId}`

Current session enrollment document per student. Use the student ID as the document ID so one student has one active assignment per session.

Suggested fields:

- `studentId`
- `institutionId`
- `sessionId`
- `classSectionId`
- `classSectionName`
- `classId`
- `sectionId`
- `rollNumber`
- `status`
- `assignedAt`
- `updatedAt`

Optional roll uniqueness helper:

```text
classSections/{classSectionId}/rollIndex/{normalizedRollNumber}
```

This extra subcollection helps enforce roll number uniqueness inside a class-section without relying on race-prone client-side checks.

### `timetableEntries/{timetableEntryId}`

Future timetable slot record.

Suggested fields:

- `id`
- `institutionId`
- `sessionId`
- `classSectionId`
- `subjectId`
- `teacherUid`
- `teacherAssignmentId`
- `dayOfWeek`
- `periodIndex`
- `startTime`
- `endTime`
- `roomLabel`
- `status`
- `createdAt`
- `updatedAt`

### `attendanceSessions/{attendanceSessionId}`

Recommended attendance root design instead of storing one large class attendance document.

Suggested fields:

- `id`
- `institutionId`
- `sessionId`
- `dateKey`
- `classSectionId`
- `classSectionName`
- `subjectId`
- `subjectName`
- `teacherUid`
- `teacherName`
- `timetableEntryId`
- `periodIndex`
- `markedByUid`
- `markedAt`

Subcollection:

```text
studentAttendance/{studentId}
```

Each child doc stores:

- `studentId`
- `studentName`
- `rollNumber`
- `status` (`PRESENT`, `ABSENT`, `LATE`, `LEAVE`)
- `remarks`

Reasoning:

- attendance is naturally grouped by one class-section + date + period
- teacher later marks a whole class efficiently
- student history can still be fetched with collection group queries if needed

### `exams/{examId}`

Suggested fields:

- `id`
- `institutionId`
- `sessionId`
- `name`
- `examType`
- `startDate`
- `endDate`
- `status`
- `createdAt`
- `updatedAt`

### `marks/{markId}`

Single flat collection under session is more practical than deeply nesting marks under students or exams because future reporting queries need multiple entry points.

Suggested fields:

- `id` (recommended: `examId_subjectId_studentId`)
- `institutionId`
- `sessionId`
- `examId`
- `examName`
- `studentId`
- `studentName`
- `rollNumber`
- `classSectionId`
- `classSectionName`
- `subjectId`
- `subjectName`
- `subjectCode`
- `teacherUid`
- `obtainedMarks`
- `maximumMarks`
- `grade`
- `status`
- `createdAt`
- `updatedAt`

## 3. Authentication Flow Design

### Starter flow

1. User signs in with Firebase Authentication email/password.
2. App loads `users/{uid}`.
3. App resolves the user’s `institutionId`.
4. App loads:
   - `institutions/{institutionId}`
5. Access is granted only if:
   - Firebase user exists
   - `users/{uid}.accountStatus == ACTIVE`
   - `users/{uid}.role == HEADMASTER`
   - `institutions.headmasterUid == uid`
6. If institution setup is incomplete, route to Institution Profile first.
7. Otherwise route to the admin shell.

### Why this flow scales later

- Faculty login can reuse the same Firebase auth entry point.
- Role checks stay in Firestore, not hardcoded into the app.
- Institution isolation remains explicit and query-safe.
- Invite and approval flow can later create a Firebase user plus `users/{uid}` and then link the faculty record with `authUid` without changing the login design.

## 4. Kotlin Data Models

Recommended core models:

- `UserProfile`
- `Institution`
- `FacultyMember`
- `AcademicSession`
- `SchoolClass`
- `Section`
- `ClassSection`
- `Subject`
- `Student`
- `StudentAssignment`
- `TeacherAssignment`
- `DashboardSummary`
- `AdminSession`

Recommended enum models:

- `UserRole`
- `RecordStatus`
- `SessionStatus`
- `SetupStatus`
- `SubjectType`
- `Gender`

## 5. Repository Design

Recommended repository split:

- `AuthRepository`
  - login
  - logout
  - observe auth state
  - reset password
  - resolve admin session
- `InstitutionRepository`
  - get institution
  - create/update institution
  - get or create active academic session
- `DashboardRepository`
  - fetch summary counts
  - fetch recent placeholders
- `FacultyRepository`
  - list/search faculty
  - get faculty by id
  - create/update/delete faculty
- `AcademicRepository`
  - list/create/update/delete classes
  - list/create/update/delete sections
  - list/create/update/delete class-sections
- `SubjectRepository`
  - list/create/update/delete subjects
- `StudentRepository`
  - list/search students
  - get student detail
  - create/update/delete student
  - assign/update class-section + roll number

Why this split works:

- each module maps to a stable business area
- repositories stay readable
- future attendance/exam/report modules can be added without bloating current repositories

## 6. Firestore Collection / Document Strategy

Use this principle consistently:

- global identity in `users`
- institution-scoped master data directly under `institutions/{institutionId}`
- session-scoped operational data under `institutions/{institutionId}/academicSessions/{sessionId}`

This avoids two common Firestore problems:

- stuffing everything into flat root collections that are harder to secure
- over-nesting everything, which makes reads cumbersome and queries awkward

## 7. Likely Query Patterns

### Admin login/session

- `users/{uid}`
- `institutions/{institutionId}`

### Dashboard counts

- count faculty: `faculty where status == ACTIVE`
- count students: `students where status == ACTIVE and currentSessionId == activeSessionId`
- count class-sections: `academicSessions/{sessionId}/classSections where status == ACTIVE`
- count subjects: `subjects where status == ACTIVE`

### Faculty module

- list/search by name:
  - `faculty orderBy(fullNameLower)`
- detail:
  - `faculty/{facultyId}`

### Students module

- list/search by name:
  - `students orderBy(fullNameLower)`
- filter by current class-section:
  - `students where currentSessionId == activeSessionId and currentClassSectionId == selectedId`
- detail:
  - `students/{studentId}`
- assignment:
  - `academicSessions/{sessionId}/studentAssignments/{studentId}`

### Classes / sections

- `classes orderBy(displayOrder)`
- `sections orderBy(displayOrder)`
- `academicSessions/{sessionId}/classSections orderBy(classOrder).orderBy(sectionOrder)`

### Subjects

- `subjects orderBy(nameLower)`

## 8. Tradeoffs and Reasoning

### Why `users` and `faculty` are separated

- `users` is auth identity
- `faculty` is institution HR/master data
- this allows faculty records to exist before login is enabled for them
- later invite flow only needs to link `authUid` and create a user doc

### Why classes / sections are institution-level but class-sections are session-level

- class and section names are reusable master data
- the actual pairing for a given academic year belongs to that session
- this makes archive and promotion workflows cleaner later

### Why students have both master record and session assignment

- Firestore has no joins
- admin screens frequently need student + current class-section + roll number in one read path
- historical session data still needs a proper assignment record

### Why marks should be flat under session

- later reports need queries by exam, class-section, subject, and student
- a single marks collection with denormalized summary fields is more flexible than nesting marks only under students or only under exams

### Why attendance should use a session root doc + student subcollection

- marking attendance is batch-like by timetable slot
- per-session attendance documents avoid oversized arrays and make incremental updates safer

## 9. Denormalization Decisions

These are the denormalizations worth doing intentionally:

- store `fullNameLower`, `nameLower`, `displayNameLower` for search-friendly ordering/filtering
- store `className`, `sectionName`, `classSectionName` on `classSections`, `studentAssignments`, `teacherAssignments`, `marks`, and attendance session docs
- store `currentClassSectionId`, `currentClassSectionName`, and `currentRollNumber` on `students`
- store `facultyName`, `subjectName`, `subjectCode` on `teacherAssignments`
- store `studentName`, `rollNumber`, `examName`, `subjectName` on `marks`

Why these are justified:

- Firestore does not join documents
- admin panels need fast list rendering
- reporting modules will need names/codes without many chained reads

## 10. Security and Future Rules Direction

Rules should eventually enforce:

- authenticated user only
- `users/{uid}` must be active
- requested institution must match `users/{uid}.institutionId`
- only headmaster can write admin collections for the starter version
- faculty later gets restricted write access only to allowed collections such as attendance or marks

## 11. Starter Build Boundaries

Implemented now:

- headmaster login
- institution profile
- dashboard
- faculty CRUD
- classes / sections / class-section CRUD
- subjects CRUD
- students CRUD with class-section assignment

Intentionally left as future-ready placeholders:

- teacher assignments
- timetable builder
- attendance marking
- exam setup
- marks entry
- reports
- audit log writer
