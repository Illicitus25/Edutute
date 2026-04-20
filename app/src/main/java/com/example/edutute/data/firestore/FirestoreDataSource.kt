package com.example.edutute.data.firestore

import com.example.edutute.core.common.FirestoreCollections
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore

class FirestoreDataSource(private val firestore: FirebaseFirestore) {
    fun users(): CollectionReference = firestore.collection(FirestoreCollections.USERS)

    fun user(uid: String): DocumentReference = users().document(uid)

    fun counters(): CollectionReference = firestore.collection(FirestoreCollections.COUNTERS)

    fun counterDocument(counterId: String): DocumentReference = counters().document(counterId)

    fun institutions(): CollectionReference = firestore.collection(FirestoreCollections.INSTITUTIONS)

    fun institution(id: String): DocumentReference = institutions().document(id)

    fun institutionCounterDocument(institutionId: String, counterId: String): DocumentReference =
        institution(institutionId)
            .collection(FirestoreCollections.COUNTERS)
            .document(counterId)

    fun faculty(institutionId: String): CollectionReference =
        institution(institutionId).collection(FirestoreCollections.FACULTY)

    fun facultyDocument(institutionId: String, facultyId: String): DocumentReference =
        faculty(institutionId).document(facultyId)

    fun classes(institutionId: String): CollectionReference =
        institution(institutionId).collection(FirestoreCollections.CLASSES)

    fun classDocument(institutionId: String, classId: String): DocumentReference =
        classes(institutionId).document(classId)

    fun sections(institutionId: String): CollectionReference =
        institution(institutionId).collection(FirestoreCollections.SECTIONS)

    fun sectionDocument(institutionId: String, sectionId: String): DocumentReference =
        sections(institutionId).document(sectionId)

    fun subjects(institutionId: String): CollectionReference =
        institution(institutionId).collection(FirestoreCollections.SUBJECTS)

    fun subjectDocument(institutionId: String, subjectId: String): DocumentReference =
        subjects(institutionId).document(subjectId)

    fun students(institutionId: String): CollectionReference =
        institution(institutionId).collection(FirestoreCollections.STUDENTS)

    fun studentDocument(institutionId: String, studentId: String): DocumentReference =
        students(institutionId).document(studentId)

    fun sessions(institutionId: String): CollectionReference =
        institution(institutionId).collection(FirestoreCollections.ACADEMIC_SESSIONS)

    fun session(institutionId: String, sessionId: String): DocumentReference =
        sessions(institutionId).document(sessionId)

    fun classSections(institutionId: String, sessionId: String): CollectionReference =
        session(institutionId, sessionId).collection(FirestoreCollections.CLASS_SECTIONS)

    fun classSectionDocument(
        institutionId: String,
        sessionId: String,
        classSectionId: String,
    ): DocumentReference = classSections(institutionId, sessionId).document(classSectionId)

    fun studentAssignments(institutionId: String, sessionId: String): CollectionReference =
        session(institutionId, sessionId).collection(FirestoreCollections.STUDENT_ASSIGNMENTS)

    fun studentAssignmentDocument(
        institutionId: String,
        sessionId: String,
        studentId: String,
    ): DocumentReference = studentAssignments(institutionId, sessionId).document(studentId)

    fun teacherAssignments(institutionId: String, sessionId: String): CollectionReference =
        session(institutionId, sessionId).collection(FirestoreCollections.TEACHER_ASSIGNMENTS)

    fun teacherAssignmentDocument(
        institutionId: String,
        sessionId: String,
        assignmentId: String,
    ): DocumentReference = teacherAssignments(institutionId, sessionId).document(assignmentId)

    fun classAttendance(institutionId: String, sessionId: String): CollectionReference =
        session(institutionId, sessionId).collection(FirestoreCollections.ATTENDANCE_SESSIONS)

    fun classAttendanceDocument(
        institutionId: String,
        sessionId: String,
        attendanceId: String,
    ): DocumentReference = classAttendance(institutionId, sessionId).document(attendanceId)

    fun facultyAttendance(institutionId: String): CollectionReference =
        institution(institutionId).collection(FirestoreCollections.ATTENDANCE_SESSIONS)

    fun facultyAttendanceDocument(
        institutionId: String,
        attendanceId: String,
    ): DocumentReference = facultyAttendance(institutionId).document(attendanceId)

    fun rollIndex(
        institutionId: String,
        sessionId: String,
        classSectionId: String,
        rollKey: String,
    ): DocumentReference = classSectionDocument(institutionId, sessionId, classSectionId)
        .collection(FirestoreCollections.ROLL_INDEX)
        .document(rollKey)
}
