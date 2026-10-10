package com.yxlogin.controller.user;

import com.yxlogin.R.Result;
import com.yxlogin.dto.UpdateUserDTO;
import com.yxlogin.entity.ChatMessage;
import com.yxlogin.entity.ImOffline;
import com.yxlogin.mapper.ChatMessageMapper;
import com.yxlogin.mapper.GroupMemberMapper;
import com.yxlogin.service.*;
import com.yxlogin.vo.GroupVo;
import com.yxlogin.vo.UserVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "用户操作接口")
public class UserController {
    @Resource
    private UserService userService;
    @Resource
    private UploadFileService uploadFileService;
    @Resource
    private FollowUserService followUserService;
    @Resource
    private ChatMessageService chatMessageService;
    @Resource
    private ChatGroupService chatGroupService;
    @Resource
    private GroupMemberService groupMemberService;

    @Operation(summary = "注销账户")
    @PostMapping("/auth/revoke")
    public Result<String> revoke(HttpServletRequest request){
        userService.revoke(request);
        return Result.success(null,"注销成功");
    }
    @PostMapping("/user/personalInfo")
    @Operation(summary = "修改个人信息")
    public Result<String> updatePersonalInfo(@RequestBody UpdateUserDTO updateUserDTO){
        userService.updatePersonalInfo(updateUserDTO);
        return Result.success();
    }

    @PostMapping("/file/upload")
    @Operation(summary = "上传文件")
    public Result<String> uploadFile(@RequestParam("file")MultipartFile file){
        String url=uploadFileService.uploadFile(file);
        return Result.success(url,"上传成功");
    }

    @PostMapping("/follow")
    @Operation(summary = "关注他人")
    public Result<String> follow(@RequestParam String toUserName){
        followUserService.follow(toUserName);
        return Result.success();
    }

    @GetMapping("/searchUser")
    @Operation(summary = "搜索某个用户")
    public Result<UserVo> searchUser(@RequestParam String userName){
        UserVo userVo=userService.searchUser(userName);
        return Result.success(userVo,"搜索成功");
    }

    @PostMapping("/cancelFollow")
    @Operation(summary = "取关他人")
    public Result<String> cancelFollow(@RequestParam String toUserName){
        followUserService.cancelFollow(toUserName);
        return Result.success();
    }
    @GetMapping("/getMyFollow")
    @Operation(summary = "获取我的关注列表")
    public Result<List<UserVo>> getMyFollow(){
        List<UserVo> myFollows=followUserService.getMyFollow();
        return Result.success(myFollows,"获取我的关注列表成功");
    }
    @GetMapping("/getFollowMy")
    @Operation(summary = "获取关注我的列表")
    public Result<List<UserVo>> getFollowMy(){
        List<UserVo> followMyList=followUserService.getFollowMy();
        return Result.success(followMyList,"获取关注我的列表成功");
    }
    @GetMapping("/getFriends")
    @Operation(summary = "获取好友列表")
    public Result<List<UserVo>> getFriends(){
        List<UserVo> friends=followUserService.getFriends();
        return Result.success(friends,"获取好友列表成功");
    }

    @PostMapping("/readMessage")
    @Operation(summary = "通过读消息将未读消息状态改成已读")
    public Result<String> readMessage(@RequestParam String fromUserName){
        chatMessageService.readMessage(fromUserName);
        return Result.success();
    }
    @GetMapping("/getRecent")
    @Operation(summary = "获取和某个用户的最近消息")
    public Result<List<ChatMessage>> getRecent(@RequestParam String fromUserName) {
        List<ChatMessage> history = chatMessageService.getRecent(fromUserName);
        return Result.success(history, "获取最近消息成功");
    }
    @GetMapping("/getHistory")
    @Operation(summary = "获取和某个用户的历史消息")
    public Result<List<ChatMessage>> getHistory(@RequestParam String fromUserName,
                                                @RequestParam int size,
                                                @RequestParam Long lastMsgId) {
        List<ChatMessage> history = chatMessageService.getHistory(fromUserName,size,lastMsgId);
        return Result.success(history, "获取历史消息成功");
    }
    @PostMapping("/createGroup")
    @Operation(summary = "创建聊天群")
    public Result<String> createGroup(@RequestParam String groupName){
        chatGroupService.createGroup(groupName);
        return Result.success();
    }
    @PostMapping("/inviteIntoGroup")
    @Operation(summary = "邀请好友进群")
    public Result<Map<String, Object>> inviteIntoGroup(@RequestParam Long groupId,
                                                       @RequestParam String username) {
        boolean needApproval = groupMemberService.inviteIntoGroup(groupId, username);
        Map<String, Object> data = new HashMap<>();
        data.put("needApproval", needApproval);
        return Result.success(data,"邀请操作成功");
    }
    @PostMapping("/leaveGroup")
    @Operation(summary = "自己退或被管理员或群主踢走")
    public Result<String> leaveGroup(@RequestParam Long groupId,
                                     @RequestParam String username ){
        groupMemberService.leaveGroup(groupId,username);
        return Result.success();
    }
    @PostMapping("/stopChat")
    @Operation(summary = "禁言")
    public Result<String> stopChat(@RequestParam Long groupId){
        groupMemberService.stopChat(groupId);
        return Result.success();
    }
    @PostMapping("/cancelStopChat")
    @Operation(summary = "取消禁言")
    public Result<String> cancelStopChat(@RequestParam Long groupId){
        groupMemberService.cancelStopChat(groupId);
        return Result.success();
    }

    @PostMapping("/setMemberRole")
    @Operation(summary = "设置成员角色")
    public Result<String> setMemberRole(@RequestParam String toUsername,@RequestParam Long groupId){
        groupMemberService.setMemberRole(toUsername,groupId);
        return Result.success();
    }
    @PostMapping("/agreeOrReject")
    @Operation(summary = "同意或拒绝入群")
    public Result<String> agreeOrReject(@RequestParam boolean isAgree,
                                        @RequestParam Long groupId,
                                        @RequestParam String inviteeName){
        groupMemberService.agreeOrReject(isAgree,groupId,inviteeName);
        return Result.success();
    }
    @PostMapping("/recallMessage")
    @Operation(summary = "撤回消息")
    public Result<String> recallMessage(@RequestParam List<Long> msgIds,
                                        @RequestParam String username){
        chatMessageService.recallMessage(msgIds,username);
        return Result.success();
    }
    @GetMapping("/getMyGroups")
    @Operation(summary = "获取我加入的群列表")
    public Result<List<GroupVo>> getMyGroups() {
        List<GroupVo> res=groupMemberService.getMyGroups();
        return Result.success(res,"获取我加入的群列表成功");
    }
    @GetMapping("/getUnreadSenders")
    @Operation(summary = "拉取未读列表")
    public Result<List<String>> getUnreadSenders() {
        List<String> res=userService.getUnreadSenders();
        return Result.success(res,"拉取未读列表成功");
    }

}
