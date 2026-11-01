package org.example.voice.scriptlibrary.domain;

import org.example.voice.practicecontent.domain.entity.PracticeContent;
import java.util.*;

public interface ScriptLibraryRepository {
    record SavedScript(PracticeContent content,Long topicId) {}
    List<SavedScript> scripts(Long user);
    List<ScriptLibraryTopic> topics(Long user);
    Optional<ScriptLibraryEntry> entry(Long user,Long id);
    Optional<ScriptLibraryTopic> topic(Long user,Long id);
    Optional<ScriptLibraryRequest> request(Long user,String key);
    boolean topicNameExists(Long user,String digest,Long excluding);
    void add(ScriptLibraryEntry entry);
    void add(ScriptLibraryTopic topic);
    void remember(ScriptLibraryRequest request);
    void remove(ScriptLibraryEntry entry);
    void remove(ScriptLibraryTopic topic);
    long size(Long user);
}
