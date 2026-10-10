package com.yxlogin.controller.im;

import com.yx.entity.Message;
import com.yxlogin.R.Result;
import com.yxlogin.entity.ChatMessage;
import com.yxlogin.handler.ImServerHandler;
import com.yxlogin.service.ChatMessageService;
import com.yxlogin.service.GroupMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


@RestController
@Tag(name = "im接口")
@Slf4j
public class imController {
    @Resource
    private ImServerHandler imServerHandler;
    @Resource
    private GroupMemberService groupMemberService;
    @Resource
    private ChatMessageService chatMessageService;
    @PostMapping("/im/accept")
    @Operation(summary = "im服务共享接口")
    public void acceptMessageFromOtherApp(@RequestBody Message msg){
        log.info("多实例提供的外部方法被调用, msgId={}, to={}", msg.getMsgId(), msg.getToUserName());
        imServerHandler.forwardMessage(msg);
    }
    @GetMapping("/getGroupMembers")
    @Operation(summary = "获取群成员列表")
    public Result<List<Map<String, Object>>> getGroupMembers(@RequestParam Long groupId){
        List<Map<String, Object>> res=groupMemberService.getGroupMembers(groupId,1);
        return Result.success(res,"获取群成员列表成功");
    }
    @GetMapping("/getGroupRecent")
    @Operation(summary = "获取最近群聊信息")
    public Result<List<ChatMessage>> getGroupRecent(@RequestParam Long groupId){
        List<ChatMessage> res=chatMessageService.getGroupRecent(groupId);
        return Result.success(res,"获取最近群聊信息成功");
    }
    @GetMapping("/getGroupHistory")
    @Operation(summary = "分页拉群聊历史")
    public Result<List<ChatMessage>> getGroupHistory(@RequestParam Long groupId,
                                                     @RequestParam int size,
                                                     @RequestParam(required = false) Long lastMsgId) {
        List<ChatMessage> res = chatMessageService.getGroupHistory(groupId, size, lastMsgId);
        return Result.success(res, "拉取群聊历史成功");
    }
}
