package org.example.voice.scriptlibrary.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.example.voice.practicecontent.controller.dto.PracticeContentDetailResponseDto;
import org.example.voice.scriptlibrary.application.ScriptLibraryService;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor @RequestMapping("/api/users/me/script-library")
public class ScriptLibraryController {
    private final ScriptLibraryService library;
    public record ScriptInput(String title,String scriptText,Long topicId) {}
    public record TopicInput(String name) {}
    public record DeleteInput(List<Long> ids) {}
    public record Library(List<PracticeContentDetailResponseDto> items,List<ScriptLibraryService.Topic> topics,java.util.Map<String,String> assignments) {}
    @GetMapping public ResponseEntity<ApiResponse<Library>> list(@AuthenticationPrincipal LoginUser user){
        var data=library.list(user.id());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("내 원고 보관함입니다.",new Library(data.items().stream().map(PracticeContentDetailResponseDto::fromCustom).toList(),data.topics(),data.assignments())));
    }
    @PostMapping public ResponseEntity<ApiResponse<PracticeContentDetailResponseDto>> create(@AuthenticationPrincipal LoginUser user,@RequestBody ScriptInput input,@RequestHeader(value="Idempotency-Key",required=false) String key){
        var data=library.save(user.id(),null,new ScriptLibraryService.Input(input.title(),input.scriptText(),input.topicId()),key);
        return ResponseEntity.status(201).body(ApiResponse.success("원고를 저장했습니다.",PracticeContentDetailResponseDto.fromCustom(data)));
    }
    @PutMapping("/{id}") public ApiResponse<PracticeContentDetailResponseDto> update(@AuthenticationPrincipal LoginUser user,@PathVariable Long id,@RequestBody ScriptInput input,@RequestHeader(value="Idempotency-Key",required=false) String key){
        return ApiResponse.success("원고를 수정했습니다.",PracticeContentDetailResponseDto.fromCustom(library.save(user.id(),id,new ScriptLibraryService.Input(input.title(),input.scriptText(),input.topicId()),key)));
    }
    @PostMapping("/delete") public ApiResponse<Void> delete(@AuthenticationPrincipal LoginUser user,@RequestBody DeleteInput input){library.delete(user.id(),input.ids());return ApiResponse.success("보관함에서 삭제했습니다.",null);}
    @PostMapping("/topics") public ResponseEntity<ApiResponse<ScriptLibraryService.Topic>> createTopic(@AuthenticationPrincipal LoginUser user,@RequestBody TopicInput input){return ResponseEntity.status(201).body(ApiResponse.success("주제를 만들었습니다.",library.createTopic(user.id(),input.name())));}
    @PutMapping("/topics/{id}") public ApiResponse<ScriptLibraryService.Topic> renameTopic(@AuthenticationPrincipal LoginUser user,@PathVariable Long id,@RequestBody TopicInput input){return ApiResponse.success("주제를 수정했습니다.",library.renameTopic(user.id(),id,input.name()));}
    @DeleteMapping("/topics/{id}") public ApiResponse<Void> deleteTopic(@AuthenticationPrincipal LoginUser user,@PathVariable Long id){library.deleteTopic(user.id(),id);return ApiResponse.success("주제를 삭제했습니다.",null);}
}
