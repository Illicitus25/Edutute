# Edutute -- Institutional Attendance & Student Management App

![Platform](https://img.shields.io/badge/Platform-Android-green)
![Language](https://img.shields.io/badge/Language-Kotlin-purple)
![Backend](https://img.shields.io/badge/Backend-Firebase-orange)
![Architecture](https://img.shields.io/badge/Architecture-Client--Cloud-blue)
![Status](https://img.shields.io/badge/Status-Active-success)

Edutute is an Android application designed for educational institutions
to digitally manage classes, students, and attendance. It enables
teachers to quickly mark attendance, store student records in a cloud
database, and generate attendance reports. The system replaces
inefficient manual attendance workflows with a fast, scalable, and
reliable mobile solution.

------------------------------------------------------------------------

## Key Features

-   Faculty login with secure authentication
-   Class and student management
-   Fast attendance marking using checkbox interface
-   Cloud-based attendance storage
-   Attendance reports and statistics
-   Offline-first support with cloud synchronization (future scope)
-   Clean Material Design interface optimized for classroom use

------------------------------------------------------------------------

## Target Users

**Primary Users** - Teachers / Faculty

**Secondary Users** - School / College Administrators

------------------------------------------------------------------------

## Tech Stack

**Frontend** - Kotlin - XML Layouts - Android SDK

**Backend** - Firebase Authentication - Firebase Firestore Database

**Tools** - Android Studio - Firebase Console - GitHub

------------------------------------------------------------------------

## System Architecture

Edutute uses a lightweight client--cloud architecture:

Teacher Android App\
→ Firebase Authentication\
→ Firebase Backend Services\
→ Firestore Database

Modules include:

-   Authentication & Role Management
-   Class Management
-   Student Database
-   Attendance Capture Module
-   Reports & Analytics Module

This architecture ensures secure authentication, scalable storage, and
fast data access.

------------------------------------------------------------------------

## Database Structure

Firestore collections:

users/ - userId - name - role (teacher/admin)

classes/ - classId - className

students/ - studentId - name - rollNo - classId

attendance/ - classId - date - markedBy - presentStudentIds\[\]

------------------------------------------------------------------------

## Installation

Clone the repository:

git clone https://github.com/Illicitus25/Edutute.git

Open in Android Studio and run on emulator or Android device.

------------------------------------------------------------------------

## Future Scope

-   Admin dashboard
-   Face recognition attendance
-   Offline sync with Room database
-   Attendance export (CSV/PDF)
-   Web dashboard for institutions

------------------------------------------------------------------------

## Author

Prakhyat Singh\
B.Tech Artificial Intelligence & Machine Learning

GitHub: https://github.com/Illicitus25\
LinkedIn: https://linkedin.com/in/prakhyat25

------------------------------------------------------------------------

## License

Academic project for educational purposes.
