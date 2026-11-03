package org.example.voice.admin.infrastructure;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.*;
import org.example.voice.admin.domain.model.AdminData.Page;
import org.example.voice.admin.domain.model.AdminMemberData.*;
import org.example.voice.admin.domain.port.AdminMemberStore;
import org.example.voice.admin.domain.type.AdminUserResource;

@Repository @RequiredArgsConstructor
public class AdminMemberPersistence implements AdminMemberStore {
    private final EntityManager em;
    public boolean activeMember(long userId) {
        return ((Number)em.createNativeQuery("select count(*) from users where id=:id and status='ACTIVE' and deleted_at is null")
                .setParameter("id",userId).getSingleResult()).longValue()==1;
    }
    public boolean visibleMember(long userId) {
        return ((Number)em.createNativeQuery("select count(*) from users where id=:id and status<>'WITHDRAWN' and deleted_at is null")
                .setParameter("id",userId).getSingleResult()).longValue()==1;
    }
    public Optional<AccountDetails> accountDetails(long userId) {
        var user=em.find(org.example.voice.user.domain.entity.User.class,userId);
        if(user==null) return Optional.empty();
        var connections=em.createQuery("select s from SocialAccount s where s.userId=:id order by s.id",org.example.voice.auth.domain.entity.SocialAccount.class)
                .setParameter("id",userId).getResultList().stream()
                .map(s->new LoginConnection(s.getProvider()==null?null:s.getProvider().name(),s.getProviderEmail(),s.getCreatedAt())).toList();
        return Optional.of(new AccountDetails(user.getEmail(),user.getLastLoginAt(),user.getUpdatedAt(),user.getDeletedAt(),
                user.getTermsAgreedAt(),user.getPrivacyAgreedAt(),connections));
    }
    private record Projection(String from,String where,String columns,String names,String order) {}
    // Every table, column and predicate is server-owned. No arbitrary SQL/table/column names from the client.
    private Projection projection(AdminUserResource r) {
        return switch(r) {
            case TRAINING_SESSIONS -> new Projection("training_sessions s","s.user_id=:user","s.id,s.content_id,s.course_step_id,s.status,s.learning_focus,s.total_learning_seconds,s.started_at,s.completed_at","id,contentId,courseStepId,status,learningFocus,totalLearningSeconds,startedAt,completedAt","s.id desc");
            case RECORDINGS -> new Projection("voice_recordings r join training_sessions s on s.id=r.training_session_id","s.user_id=:user","r.id,r.training_session_id,r.attempt_no,r.mime_type,r.file_size_bytes,r.duration_ms,r.quality_status,r.created_at,r.deleted_at","id,sessionId,attemptNo,mimeType,fileSizeBytes,durationMs,qualityStatus,createdAt,deletedAt","r.id desc");
            case ANALYSES -> new Projection("analysis_results a join voice_recordings r on r.id=a.recording_id join training_sessions s on s.id=r.training_session_id","s.user_id=:user","a.id,a.recording_id,a.status,a.overall_score,a.pronunciation_score,a.intonation_score,a.failure_code,a.active_request_event_id,a.active_execution_id,a.canonical_result_event_id,a.created_at,a.analyzed_at","id,recordingId,status,overallScore,pronunciationScore,intonationScore,failureCode,requestId,executionId,resultEventId,createdAt,analyzedAt","a.id desc");
            case PAYMENTS -> new Projection("payment_test_orders p","p.user_id=:user","p.id,p.product,p.amount,p.currency,p.status,p.created_at,p.paid_at,p.expires_at","id,product,amount,currency,status,createdAt,paidAt,expiresAt","p.created_at desc,p.id desc");
            case CANCELLATIONS -> new Projection("payment_test_cancellations c join payment_test_orders p on p.id=c.order_id","p.user_id=:user","c.id,c.order_id,c.status,c.reason,c.requested_at,c.updated_at","id,orderId,status,reason,requestedAt,updatedAt","c.requested_at desc,c.id desc");
            case INQUIRIES -> new Projection("inquiries i","i.user_id=:user","i.id,i.category,i.subject,i.status,i.created_at,i.answered_at","id,category,subject,status,createdAt,answeredAt","i.id desc");
            case NOTIFICATIONS -> new Projection("user_notifications n","n.user_id=:user","n.id,n.type,n.title,n.read_at,n.created_at","id,type,title,readAt,createdAt","n.id desc");
            case COURSE_PROGRESS -> new Projection("user_course_progress p","p.user_id=:user","p.id,p.course_id,p.last_step_id,p.status,p.progress_percent,p.updated_at","id,courseId,lastStepId,status,progressPercent,updatedAt","p.id desc");
            case TITLES -> new Projection("user_titles t","t.user_id=:user","t.user_id,t.rank,t.minimum_training_count,t.updated_at","userId,rank,minimumTrainingCount,updatedAt","t.user_id desc");
            case TITLE_EXAMS -> new Projection("title_exams t","t.user_id=:user","t.id,t.target_rank,t.status,t.training_session_id,t.analysis_id,t.score,t.created_at,t.evaluated_at","id,targetRank,status,sessionId,analysisId,score,createdAt,evaluatedAt","t.id desc");
            case DIRECT_ANALYSES -> new Projection("direct_analysis_history_links l left join direct_analysis_history h on h.job_id=l.job_id","l.user_id=:user","l.job_id,h.execution_id,h.content_id,l.created_at,h.archived_at","jobId,executionId,contentId,linkedAt,archivedAt","l.created_at desc,l.job_id desc");
            case COACHING_USAGE -> new Projection("coaching_usage_reservations c","c.user_id=:user","c.analysis_id,c.recording_id,c.request_event_id,c.state,c.window_started_at,c.window_expires_at","analysisId,recordingId,requestId,state,startsAt,expiresAt","c.analysis_id desc");
            case ONBOARDING -> new Projection("onboarding_profiles o","o.user_id=:user","o.id,o.current_level,o.goal_text,o.daily_goal_minutes,o.weekly_goal_count,o.completed_at,o.updated_at","id,currentLevel,goalText,dailyGoalMinutes,weeklyGoalCount,completedAt,updatedAt","o.id desc");
            case SCRIPT_LIBRARY -> new Projection("script_library_entries l join practice_contents c on c.id=l.content_id","l.user_id=:user","l.content_id,l.topic_id,l.created_at,c.updated_at,c.custom_deleted_at","contentId,topicId,createdAt,updatedAt,deletedAt","l.created_at desc,l.content_id desc");
            case NOTIFICATION_SETTINGS -> new Projection("notification_preferences n","n.user_id=:user","n.user_id,n.enabled,n.reminder_time,n.weekdays,n.timezone,n.updated_at","userId,enabled,reminderTime,weekdays,timezone,updatedAt","n.user_id desc");
            case PROCESSING_CONSENTS -> new Projection("processing_consents c","c.user_id=:user","c.id,c.training_session_id,c.recording_id,c.scope,c.policy_revision,c.granted_at,c.revoked_at","id,sessionId,recordingId,scope,policyRevision,grantedAt,revokedAt","c.id desc");
            case MEMBERSHIP_GRANTS -> new Projection("user_test_membership_grants g","g.user_id=:user","g.user_id,g.granted_at","userId,grantedAt","g.user_id desc");
            case PROFILE_IMAGES -> new Projection("profile_images p","p.user_id=:user","p.id,p.size_bytes,p.state,p.created_at,p.activated_at,p.cleanup_after","id,sizeBytes,state,createdAt,activatedAt,cleanupAfter","p.id desc");
            case ANALYSIS_SEGMENTS -> new Projection("analysis_segments g join analysis_results a on a.id=g.analysis_result_id join voice_recordings r on r.id=a.recording_id join training_sessions s on s.id=r.training_session_id","s.user_id=:user","g.id,g.analysis_result_id,g.sequence_no,g.start_ms,g.end_ms,g.match_type,g.result_status,g.error_type,g.pronunciation_score,g.intonation_score,g.feedback","id,analysisId,sequenceNo,startMs,endMs,matchType,resultStatus,errorType,pronunciationScore,intonationScore,feedback","g.analysis_result_id desc,g.sequence_no asc");
            case PRIVATE_CONTENTS -> new Projection("practice_contents c","c.owner_id=:user","c.id,c.content_type,c.status,c.created_at,c.updated_at","id,contentType,status,createdAt,updatedAt","c.id desc");
        };
    }
    @SuppressWarnings("unchecked") public Page<RecordView> records(long userId,AdminUserResource resource,int page,int size) {
        var p=projection(resource);
        List<Object[]> rows=em.createNativeQuery("select "+p.columns()+" from "+p.from()+" where "+p.where()+" order by "+p.order())
                .setParameter("user",userId).setFirstResult(page*size).setMaxResults(size).getResultList();
        String[] names=p.names().split(",");
        return new Page<>(rows.stream().map(row->{var map=new LinkedHashMap<String,Object>();
            for(int i=0;i<names.length;i++)map.put(names[i],scalar(row[i]));return new RecordView(map);}).toList(),page,size,count(userId,p));
    }
    @SuppressWarnings("unchecked") public List<ResourceCount> counts(long userId) {
        // Keep the existing per-resource semantics in one DB round trip; no stale member-result cache.
        var sql=new StringJoiner(" union all ");
        for(var resource:AdminUserResource.values()) {
            var p=projection(resource);
            sql.add("select '"+resource.name()+"',count(*) from "+p.from()+" where "+p.where());
        }
        List<Object[]> rows=em.createNativeQuery(sql.toString()).setParameter("user",userId).getResultList();
        return rows.stream().map(row->new ResourceCount(AdminUserResource.valueOf((String)row[0]),((Number)row[1]).longValue())).sorted(Comparator.comparing(ResourceCount::resource)).toList();
    }
    private long count(long userId,Projection p) { return ((Number)em.createNativeQuery("select count(*) from "+p.from()+" where "+p.where()).setParameter("user",userId).getSingleResult()).longValue(); }
    @SuppressWarnings("unchecked") public List<IdentifierMatch> resolve(UUID id) {
        String query="select distinct s.user_id from analysis_results a join voice_recordings r on r.id=a.recording_id "
                +"join training_sessions s on s.id=r.training_session_id join users u on u.id=s.user_id where u.status<>'WITHDRAWN' and u.deleted_at is null and (lower(a.active_request_event_id)=:id or lower(a.active_execution_id)=:id or cast(a.canonical_result_event_id as varchar)=:id)";
        List<Number> analysis=em.createNativeQuery(query).setParameter("id",id.toString()).getResultList();
        List<Number> direct=em.createNativeQuery("select distinct l.user_id from direct_analysis_history_links l left join direct_analysis_history h on h.job_id=l.job_id join users u on u.id=l.user_id where u.status<>'WITHDRAWN' and u.deleted_at is null and (cast(l.job_id as varchar)=:id or cast(h.execution_id as varchar)=:id)")
                .setParameter("id",id.toString()).getResultList();
        var result=new ArrayList<IdentifierMatch>();analysis.forEach(u->result.add(new IdentifierMatch(u.longValue(),"ANALYSIS")));
        direct.forEach(u->result.add(new IdentifierMatch(u.longValue(),"DIRECT_ANALYSIS")));return List.copyOf(result);
    }
    private Object scalar(Object value) {
        if(value==null||value instanceof String||value instanceof Number||value instanceof Boolean)return value;
        // UUID/timestamp metadata have a stable textual representation; JDBC objects never leak into JSON.
        if(value instanceof java.sql.Timestamp t)return t.toInstant().toString();
        return value.toString();
    }
}
