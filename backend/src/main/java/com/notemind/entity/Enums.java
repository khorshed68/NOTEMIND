package com.notemind.entity;

/** Holder for the small domain enums so the entity package stays uncluttered. */
public final class Enums {

    private Enums() {
    }

    public enum Role { STUDENT, CLASS_REP, ADMIN }

    public enum DocumentStatus { UPLOADED, PROCESSING, READY, FAILED }

    public enum MessageRole { USER, ASSISTANT }

    public enum FolderPermission { VIEW, MANAGE }

    public enum Difficulty { EASY, MEDIUM, HARD }

    public enum QuestionType { MCQ, TRUE_FALSE, SHORT_ANSWER }
}
