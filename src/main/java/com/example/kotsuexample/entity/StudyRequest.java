package com.example.kotsuexample.entity;

import com.example.kotsuexample.entity.enums.StudyRequestStatus;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "study_requests")
@Getter
@NoArgsConstructor
public class StudyRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "study_recruit_id", nullable = false)
    private StudyRecruit studyRecruit;

    @Column(nullable = false)
    private String title;

    @Lob
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StudyRequestStatus status = StudyRequestStatus.PENDING;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Builder
    public StudyRequest(User user, StudyRecruit studyRecruit, String title, String message, StudyRequestStatus status, LocalDateTime requestedAt) {
        this.user = user;
        this.studyRecruit = studyRecruit;
        this.title = title;
        this.message = message;
        this.status = status;
        this.requestedAt = requestedAt;
    }

    public void updateStatus(StudyRequestStatus studyRequestStatus) {
        this.status = studyRequestStatus;
    }
}
