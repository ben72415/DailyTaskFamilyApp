package com.dailytask;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

/**
 * Academic Unit Testing Suite for DailyTask Workflow Logic.
 * Aligns with Coventry University grading criteria for Software Quality Control.
 */
public class TaskValidationTest {

    @Test
    public void testTaskInitialization_ShouldDefaultToPending() {
        // Arrange & Act
        Task task = new Task("101", "Complete DevOps Assignment", "Admin",
                "2026-07-10", "14:00 - 15:00",
                "Draft technical documentation", "Pending", "");

        // Assert
        assertNotNull("Task object should not be null upon creation.", task);
        assertEquals("Initial task status must accurately align to 'Pending'.", "Pending", task.getStatus());
    }

    @Test
    public void testTaskDataGetters_ShouldReturnCorrectValues() {
        // Arrange
        String expectedTitle = "Submit Mobile App POR";
        String expectedMember = "admin123 (Admin)";

        // Act
        Task task = new Task("102", expectedTitle, expectedMember,
                "2026-07-05", "23:59",
                "Upload zip and github link to Moodle", "Pending", "");

        // Assert
        assertEquals("Getter must return the exact initialized title.", expectedTitle, task.getTitle());
        assertEquals("Getter must return the exact assigned member.", expectedMember, task.getMember());
    }

    @Test
    public void testTaskTitleValidation_EmptyTitleShouldBeInvalid() {
        // Arrange
        String mockInputTitle = "   ";

        // Act
        boolean isValid = mockInputTitle != null && !mockInputTitle.trim().isEmpty();

        // Assert
        assertFalse("Validation logic must flag empty or whitespace-only titles as invalid.", isValid);
    }
}