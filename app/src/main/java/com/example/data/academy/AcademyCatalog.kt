package com.example.data.academy

import com.example.data.model.Course

object AcademyCatalog {
    val allCourses: List<Course> = coursesPart1 + coursesPart2

    fun findCourse(courseId: String): Course? = allCourses.firstOrNull { it.id == courseId }
}
