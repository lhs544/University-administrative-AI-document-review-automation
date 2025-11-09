package com.cnu.docserver.submission.event;

import org.springframework.context.ApplicationEvent;

public class SubmissionCreatedEvent extends ApplicationEvent {

    private final Integer submissionId;

    public SubmissionCreatedEvent(Object source, Integer submissionId) {
        super(source);
        this.submissionId = submissionId;
    }

    public Integer getSubmissionId() {
        return submissionId;
    }
}