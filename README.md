<h1 align="center">🎓 Edutute</h1>
<p align="center">
  <b>A modern Android school operations app built with Kotlin, XML, and Firebase</b>
</p>

<p align="center">
  Edutute delivers two role-aware experiences:
  <br/>
  <b>Headmaster</b> for full institution administration
  ·
  <b>Faculty</b> for restricted day-to-day academic operations
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/UI-XML%20%2B%20Material%203-1976D2?style=for-the-badge&logo=materialdesign&logoColor=white" />
  <img src="https://img.shields.io/badge/Backend-Firebase-FFCA28?style=for-the-badge&logo=firebase&logoColor=black" />
  <img src="https://img.shields.io/badge/Database-Cloud%20Firestore-FF6F00?style=for-the-badge&logo=firebase&logoColor=white" />
  <img src="https://img.shields.io/badge/Architecture-MVVM%20%2F%20Layered-0A66C2?style=for-the-badge" />
</p>

---

## ✨ Overview

**Edutute** is an Android-based school operations app designed to streamline institutional workflows.  
It currently supports:

- authentication
- institution setup
- faculty management
- class and subject management
- student handling
- attendance tracking

Instead of maintaining duplicate screens for different users, Edutute reuses **shared modules** across both roles wherever possible, while enforcing access restrictions through app logic.

---

## 🌟 Highlights

- 🔐 Role-based authentication for **Headmaster** and **Faculty**
- 🏫 Faculty self-registration linked through a valid **Institutional ID**
- 📩 Headmaster-managed faculty onboarding with activation via reset email
- 🧩 Prevention of duplicate faculty accounts by linking auth to existing faculty records
- 🧭 Role-aware drawer navigation and post-login routing
- 🛡️ Shared fragments with **logic-level access control**, not just hidden UI
- 📊 Personalized faculty dashboard
- 📚 Filtered class access for faculty
- ✅ Restricted attendance permissions for faculty
- ☁️ Firebase Authentication + Cloud Firestore-backed data storage
- 🧱 Modular repository / ViewModel / UI structure

---

## 📦 Current Feature Set

### 🔐 Authentication

- Headmaster account registration and sign-in
- Faculty account registration and sign-in
- Role resolution from stored user profile data
- Post-login routing to the correct in-app experience
- Password reset support

### 🏛️ Institution Management

- Institution profile setup and editing
- Academic session awareness
- Read-only institution profile access for faculty users

### 👩‍🏫 Faculty Management

- Headmaster can add, edit, archive, and view faculty members
- Faculty added by the headmaster can be provisioned for account activation
- Reset / activation emails are sent to invited faculty users
- Existing faculty profiles are linked to auth accounts to avoid duplicate records
- Faculty can view their own profile with restricted edit permissions

### 🏫 Academic Structure

- Class management
- Section management
- Class-section management
- Subject management
- Teacher assignment to classes and subjects

### 👨‍🎓 Student Management

- Student creation and editing
- Class-section assignment
- Roll number management
- Student detail screens
- Attendance summary views

### 🗓️ Attendance

#### Headmaster
- Mark class attendance
- View class attendance
- Rectify saved class attendance
- Mark faculty attendance
- View faculty attendance
- Rectify faculty attendance

#### Faculty
- View personalized attendance summary on the dashboard
- Mark class attendance only for class-sections where they are the class teacher
- Submit class attendance only once per class-section per day
- View class attendance for permitted classes
- No faculty-attendance marking
- No institution-wide analytics
- No saved-record rectification

---

## 👥 Role-Based Experience

## Headmaster

The **Headmaster** gets the complete app experience:

- Dashboard
- Faculty
- Students
- Classes
- Subjects
- Attendance
- Institution Profile

## Faculty

The **Faculty** experience is simplified and restricted:

- Dashboard
- Classes
- Attendance
- Institution
- Logout

Faculty can only access classes where they are assigned as:

- **Class Teacher**
- **Subject Teacher**

> All restrictions are enforced at both the **repository / ViewModel level** and the **UI level**.

---

## 🔗 Invitation + Account Linking Flow

One of the main goals of the recent authentication work was to eliminate duplicate faculty records.

### 🚫 Previous Flow

- Headmaster created a faculty profile
- Faculty later self-registered separately
- This could create two records for the same teacher

### ✅ Current Flow

1. Headmaster creates the faculty record once  
2. The app provisions a faculty auth account in the background  
3. A reset / activation email is sent to the faculty email address  
4. The faculty profile stays in an invited state until sign-in  
5. Once activated, the same faculty record is linked and reused  
6. Faculty self-registration checks for an existing faculty record using **institution + email** and links it instead of creating a new one  

This keeps the headmaster’s faculty list as the **single source of truth**.

---

## 🛠️ Tech Stack

<p>
  <img src="https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/XML-FF6F00?style=flat-square&logo=xml&logoColor=white" />
  <img src="https://img.shields.io/badge/Material%203-1E88E5?style=flat-square&logo=materialdesign&logoColor=white" />
  <img src="https://img.shields.io/badge/AndroidX-34A853?style=flat-square&logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Firebase-FFCA28?style=flat-square&logo=firebase&logoColor=black" />
  <img src="https://img.shields.io/badge/Cloud%20Firestore-FF8F00?style=flat-square&logo=firebase&logoColor=white" />
  <img src="https://img.shields.io/badge/Coroutines-0095D5?style=flat-square" />
  <img src="https://img.shields.io/badge/Navigation%20Component-3F51B5?style=flat-square" />
  <img src="https://img.shields.io/badge/ViewModel-00695C?style=flat-square" />
  <img src="https://img.shields.io/badge/StateFlow-5E35B1?style=flat-square" />
</p>

| Layer | Tools / Technologies |
|------|------|
| **Language** | Kotlin |
| **UI** | XML layouts + Material 3 |
| **Platform** | Android |
| **Architecture** | Layered `data / domain / presentation` |
| **Backend** | Firebase Authentication |
| **Database** | Cloud Firestore |
| **Async** | Kotlin Coroutines |
| **Navigation** | AndroidX Navigation |
| **State Handling** | ViewModel + `StateFlow` |

---

## 🧱 Project Structure

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
