# Edutute

Edutute is an Android school operations app built with Kotlin, XML, and Firebase. It supports two role-aware experiences:

- `Headmaster`: full institution setup and administration
- `Faculty`: restricted day-to-day experience with filtered access

The app currently covers authentication, institution setup, faculty management, classes, subjects, students, and attendance, with shared modules reused across both roles instead of maintaining duplicate screens.

## Highlights

- Role-based authentication for `Headmaster` and `Faculty`
- Faculty self-registration linked to a valid institution via `Institutional ID`
- Headmaster-managed faculty invitations with activation by reset email
- Duplicate faculty-account prevention by linking auth to an existing faculty record
- Role-based drawer navigation and post-login routing
- Shared fragments with logic-level access control, not just hidden UI
- Personalized faculty dashboard
- Filtered class access for faculty
- Restricted attendance flows for faculty
- Firebase Authentication + Firestore-backed data storage
- Modular repository/viewmodel/UI structure

## Current Feature Set

### Authentication

- Headmaster account registration and sign-in
- Faculty account registration and sign-in
- Role resolution from stored user profile data
- Route users to the correct in-app experience after login
- Password reset support

### Institution Management

- Institution profile setup and editing
- Academic session awareness
- Faculty read-only institution profile view for restricted users

### Faculty Management

- Headmaster can add, edit, archive, and view faculty
- Faculty added by headmaster are now provisioned for account activation
- Reset/activation email is sent to invited faculty users
- Existing faculty profile is linked to the auth account to avoid duplicate records
- Faculty can view their own profile with restricted edit permissions

### Academic Structure

- Class management
- Section management
- Class-section management
- Subject management
- Teacher assignment to classes/subjects

### Student Management

- Student creation and editing
- Class-section assignment
- Roll number handling
- Student detail screens and attendance summary views

### Attendance

- Headmaster:
  - mark class attendance
  - view class attendance
  - rectify saved class attendance
  - mark faculty attendance
  - view faculty attendance
  - rectify faculty attendance
- Faculty:
  - view personalized attendance summary on dashboard
  - mark class attendance only for class-sections where they are the class teacher
  - submit class attendance only once per class-section per day
  - view class attendance for allowed classes
  - no faculty-attendance marking
  - no institution-wide analytics
  - no saved-record rectification

## Role-Based Experience

### Headmaster

The headmaster gets the full app experience, including:

- Dashboard
- Faculty
- Students
- Classes
- Subjects
- Attendance
- Institution Profile

### Faculty

Faculty uses a simplified drawer and restricted data scope:

- Dashboard
- Classes
- Attendance
- Institution
- Logout

Faculty sees only the classes they are assigned to as class teacher or subject teacher, and all restrictions are enforced in repository/viewmodel logic as well as in the UI.

## Invitation + Account Linking Flow

One of the main goals of the recent authentication work was eliminating duplicate faculty records.

### Before

- Headmaster could create a faculty profile
- Faculty could later self-register separately
- That could produce two records for the same teacher

### Now

1. Headmaster creates the faculty record once.
2. The app provisions a faculty auth account in the background.
3. A reset/activation email is sent to the faculty email address.
4. The faculty profile is stored in invited state until the faculty signs in.
5. When the faculty account becomes active, the same faculty record is linked and reused.
6. Faculty self-registration checks for an existing faculty record by institution + email and links to it instead of creating a duplicate.

This keeps the data model clean and ensures the headmaster’s faculty list remains the single source of truth.

## Tech Stack

- Language: `Kotlin`
- UI: `XML` layouts with Material 3 components
- Platform: `Android`
- Architecture style: layered `data / domain / presentation`
- Backend: `Firebase Authentication`
- Database: `Cloud Firestore`
- Async: Kotlin coroutines
- Navigation: AndroidX Navigation
- State handling: ViewModel + `StateFlow`

## Project Structure

```text
Edutute/
├── app/
│   ├── src/main/java/com/example/edutute/
│   │   ├── app/            # App bootstrap and dependency container
│   │   ├── core/           # Common constants, UI helpers, validation, utilities
│   │   ├── data/           # Firebase/Firestore-backed repository implementations
│   │   ├── domain/         # Models, repository contracts, access abstractions
│   │   └── presentation/   # Fragments, adapters, viewmodels, main navigation
│   ├── src/main/res/       # Layouts, menus, drawables, strings, themes
│   └── google-services.json
├── docs/
│   └── admin-architecture.md
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

### Presentation Modules

- `auth`
- `dashboard`
- `institution`
- `faculty`
- `classes`
- `subjects`
- `students`
- `attendance`
- `main`

## Architecture Notes

Edutute uses a simple dependency container instead of a DI framework. `AppContainer` wires repository implementations and shared services, then viewmodels consume repository interfaces.

Core patterns used in the codebase:

- `domain.repository.*` defines contracts
- `data.repository.*` provides Firebase/Firestore implementations
- `presentation.*` owns UI and viewmodels
- `RoleAccessManager` centralizes permission checks and scope filtering
- `SessionStore` maintains the current in-app session

This keeps feature code modular while still being easy to follow in a single-app-module project.

## Firestore Model Overview

The app is organized around a small set of top-level and institution-scoped collections.

### Top-level

- `users/{uid}`
- `institutions/{institutionId}`

### Institution-scoped subcollections

- `faculty`
- `classes`
- `sections`
- `subjects`
- `students`
- `academic_sessions`
- session-scoped `class_sections`
- session-scoped `teacher_assignments`
- session-scoped `student_assignments`
- attendance collections for class and faculty attendance

### Important Profile Fields

`users/{uid}` stores auth-linked profile/session metadata such as:

- `uid`
- `email`
- `role`
- `userType`
- `institutionId`
- `institutionalId`
- `linkedFacultyId`
- `accountStatus`

`institutions/{institutionId}/faculty/{facultyId}` stores faculty records such as:

- `authUid`
- `accountStatus`
- `fullName`
- `email`
- `phoneNumber`
- `employeeCode`
- `qualification`
- `joiningDate`
- `inviteSentAt`
- `status`

## Requirements

- Android Studio with current Android SDK tools
- JDK compatible with the Android Gradle Plugin in this repo
- Firebase project with:
  - Email/Password authentication enabled
  - Firestore enabled
- `google-services.json` configured for the target Firebase project

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/Illicitus25/Edutute.git
cd Edutute
```

### 2. Open in Android Studio

Open the root project folder and let Gradle sync.

### 3. Verify Firebase configuration

Make sure the project is connected to the intended Firebase project and that `app/google-services.json` matches it.

At minimum, enable:

- Firebase Authentication with Email/Password
- Cloud Firestore

### 4. Build the app

On Windows:

```powershell
.\gradlew.bat assembleDebug
```

On macOS/Linux:

```bash
./gradlew assembleDebug
```

### 5. Run on a device or emulator

Use Android Studio or:

```powershell
.\gradlew.bat installDebug
```

## Development Notes

- Role restrictions are enforced in logic, not only by hiding buttons.
- Shared fragments are reused for both headmaster and faculty flows wherever possible.
- Faculty attendance is intentionally scoped tighter than headmaster attendance.
- Faculty users cannot mark their own faculty attendance.
- Class attendance restrictions for faculty are tied to class-teacher assignments.
- The repo currently uses a single `app` module with a layered package structure.

## Navigation Summary

The app launches into an auth gate and then routes based on the resolved session:

- unauthenticated -> login
- headmaster with incomplete institution setup -> institution profile
- authenticated headmaster -> dashboard
- authenticated faculty -> faculty dashboard

Drawer contents are also swapped dynamically based on the stored role.

## Build Status

The project has been built successfully with:

```powershell
.\gradlew.bat assembleDebug
```

## Documentation

Additional architecture notes live in:

- [docs/admin-architecture.md](docs/admin-architecture.md)

## Roadmap Ideas

- Better automated tests around auth and role access
- Export/reporting features
- Notifications and reminders
- Improved analytics
- Optional offline caching strategy
- Backend-assisted invitation flow via Cloud Functions/Admin SDK

## Author

Prakhyat Singh  
B.Tech Artificial Intelligence & Machine Learning

- GitHub: [Illicitus25](https://github.com/Illicitus25)
- LinkedIn: [prakhyat25](https://linkedin.com/in/prakhyat25)

## License

This project is currently presented as an academic/product portfolio project for educational use.
