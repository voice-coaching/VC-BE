CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE,
    password VARCHAR(255),
    nickname VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED', 'WITHDRAWN')),
    terms_agreed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    privacy_agreed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_login_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,
    role VARCHAR(63) NOT NULL CHECK (role IN ('USER', 'ADMIN'))
);

CREATE TABLE social_accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    provider VARCHAR(20) NOT NULL CHECK (provider IN ('GOOGLE', 'KAKAO', 'NAVER', 'APPLE')),
    provider_user_id VARCHAR(255) NOT NULL,
    provider_email VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_social_accounts_user_provider UNIQUE (user_id, provider)
);

CREATE TABLE onboarding_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users (id),
    current_level VARCHAR(30) CHECK (current_level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    goal_text VARCHAR(500),
    daily_goal_minutes INTEGER,
    weekly_goal_count INTEGER,
    survey_answers JSONB NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE practice_contents (
    id BIGSERIAL PRIMARY KEY,
    content_type VARCHAR(30) NOT NULL CHECK (content_type IN ('NEWS', 'SENTENCE', 'ANNOUNCER', 'CLASS_PRACTICE')),
    learning_focus VARCHAR(20) NOT NULL CHECK (learning_focus IN ('PRONUNCIATION', 'INTONATION', 'BOTH')),
    category VARCHAR(255),
    title VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    script_text TEXT NOT NULL,
    difficulty VARCHAR(20) NOT NULL CHECK (difficulty IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    target_pronunciations JSONB,
    estimated_seconds INTEGER,
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'HIDDEN')),
    published_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE reference_audios (
    id BIGSERIAL PRIMARY KEY,
    content_id BIGINT NOT NULL REFERENCES practice_contents (id),
    speaker_name VARCHAR(255),
    speaker_type VARCHAR(30) CHECK (speaker_type IN ('ANNOUNCER', 'COACH', 'TTS')),
    audio_url VARCHAR(1000) NOT NULL,
    duration_ms INTEGER,
    is_primary BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE courses (
    id BIGSERIAL PRIMARY KEY,
    course_type VARCHAR(20) NOT NULL CHECK (course_type IN ('PRONUNCIATION', 'INTONATION')),
    title VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    difficulty VARCHAR(20) NOT NULL CHECK (difficulty IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    estimated_minutes INTEGER,
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'HIDDEN')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE course_steps (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL REFERENCES courses (id),
    practice_content_id BIGINT REFERENCES practice_contents (id),
    step_type VARCHAR(30) NOT NULL CHECK (step_type IN ('THEORY', 'AUDIO_EXAMPLE', 'PRACTICE', 'RESULT_REVIEW')),
    step_order INTEGER NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT,
    required BOOLEAN NOT NULL,
    CONSTRAINT uk_course_steps_order UNIQUE (course_id, step_order)
);

CREATE TABLE user_course_progress (
    id BIGSERIAL PRIMARY KEY,
    last_step_id BIGINT REFERENCES course_steps (id),
    course_id BIGINT NOT NULL REFERENCES courses (id),
    user_id BIGINT NOT NULL REFERENCES users (id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED')),
    progress_percent NUMERIC(5,2) NOT NULL DEFAULT 0.00 CHECK (progress_percent BETWEEN 0 AND 100),
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_user_course_progress UNIQUE (user_id, course_id)
);

CREATE TABLE training_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    content_id BIGINT NOT NULL REFERENCES practice_contents (id),
    course_step_id BIGINT REFERENCES course_steps (id),
    learning_focus VARCHAR(20) NOT NULL CHECK (learning_focus IN ('PRONUNCIATION', 'INTONATION', 'BOTH')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('RECORDING', 'UPLOADING', 'ANALYZING', 'COMPLETED', 'FAILED', 'CANCELED')),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    total_learning_seconds INTEGER NOT NULL DEFAULT 0 CHECK (total_learning_seconds >= 0),
    failure_reason VARCHAR(500)
);

CREATE TABLE voice_recordings (
    id BIGSERIAL PRIMARY KEY,
    training_session_id BIGINT NOT NULL REFERENCES training_sessions (id),
    attempt_no INTEGER NOT NULL CHECK (attempt_no > 0),
    audio_url VARCHAR(1000) NOT NULL,
    mime_type VARCHAR(100),
    file_size_bytes BIGINT,
    duration_ms INTEGER,
    quality_status VARCHAR(30) NOT NULL CHECK (
        quality_status IN ('PENDING', 'PASS', 'LOW_VOLUME', 'TOO_NOISY', 'TOO_SHORT', 'NO_SPEECH', 'FAILED')
    ),
    volume_score NUMERIC(5,2),
    noise_score NUMERIC(5,2),
    is_selected BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_voice_recordings_attempt UNIQUE (training_session_id, attempt_no)
);

CREATE UNIQUE INDEX uk_voice_recordings_selected
    ON voice_recordings (training_session_id)
    WHERE is_selected = TRUE AND deleted_at IS NULL;

CREATE TABLE analysis_results (
    id BIGSERIAL PRIMARY KEY,
    recording_id BIGINT NOT NULL UNIQUE REFERENCES voice_recordings (id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    transcript TEXT,
    stt_confidence NUMERIC(5,4),
    stt_model_name VARCHAR(255),
    overall_score NUMERIC(5,2),
    pronunciation_score NUMERIC(5,2),
    intonation_score NUMERIC(5,2),
    speed_wpm NUMERIC(7,2),
    speed_status VARCHAR(30) CHECK (speed_status IN ('TOO_SLOW', 'NORMAL', 'TOO_FAST')),
    stress_score NUMERIC(5,2),
    pause_score NUMERIC(5,2),
    strengths_text TEXT,
    weaknesses_text TEXT,
    summary_feedback TEXT,
    analyzed_at TIMESTAMP WITH TIME ZONE,
    failure_reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE analysis_segments (
    id BIGSERIAL PRIMARY KEY,
    analysis_result_id BIGINT NOT NULL REFERENCES analysis_results (id),
    sequence_no INTEGER NOT NULL,
    expected_text VARCHAR(255),
    recognized_text VARCHAR(255),
    start_ms INTEGER,
    end_ms INTEGER,
    match_type VARCHAR(30) NOT NULL CHECK (match_type IN ('MATCH', 'SUBSTITUTION', 'OMISSION', 'ADDITION')),
    result_status VARCHAR(30) NOT NULL CHECK (result_status IN ('NORMAL', 'CAUTION', 'NEEDS_IMPROVEMENT')),
    target_unit VARCHAR(255),
    error_type VARCHAR(255),
    pronunciation_score NUMERIC(5,2),
    intonation_score NUMERIC(5,2),
    feedback VARCHAR(1000),
    CONSTRAINT uk_analysis_segments_sequence UNIQUE (analysis_result_id, sequence_no)
);

CREATE INDEX idx_training_sessions_user_started ON training_sessions (user_id, started_at DESC);
CREATE INDEX idx_voice_recordings_session ON voice_recordings (training_session_id, deleted_at);
CREATE INDEX idx_analysis_results_status ON analysis_results (status, analyzed_at);
CREATE INDEX idx_analysis_segments_analysis ON analysis_segments (analysis_result_id, sequence_no);
