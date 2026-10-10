package com.yxlogin.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yxlogin.R.Result;
import com.yxlogin.dto.UpdateUserDTO;
import com.yxlogin.entity.ChatMessage;
import com.yxlogin.entity.FollowUser;
import com.yxlogin.entity.ImOffline;
import com.yxlogin.entity.User;
import com.yxlogin.key.JtiKey;
import com.yxlogin.key.UserKey;
import com.yxlogin.mapper.ChatMessageMapper;
import com.yxlogin.mapper.FollowUserMapper;
import com.yxlogin.mapper.ImOfflineMapper;
import com.yxlogin.mapper.UserMapper;
import com.yxlogin.service.IdSegmentService;
import com.yxlogin.service.UserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yxlogin.util.JwtUtil;
import com.yxlogin.vo.UserVo;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.yxlogin.key.UserKey.USER_NAME_KEY;


/**
 * <p>
 * 用户 服务实现类
 * </p>
 *
 * @author yu
 * @since 2026-09-12
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    @Resource
    private BCryptPasswordEncoder bCryptPasswordEncoder;
    @Resource
    private UserMapper userMapper;
    @Resource
    private JwtUtil jwtUtil;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IdSegmentService idSegmentService;
    @Resource
    private FollowUserMapper followUserMapper;
    @Resource
    private ImOfflineMapper imOfflineMapper;
    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Override
    @Transactional
    public void registerUser(String username, String password) {
        registerMethod(username,password,"user");
        FollowUser followUser1 = new FollowUser().setToUserName(username).setFromUserName("im系统").setIsMutual(1).setId(idSegmentService.nextId("followUser"));
        FollowUser followUser2 = new FollowUser().setToUserName("im系统").setFromUserName(username).setIsMutual(1).setId(idSegmentService.nextId("followUser"));
        followUserMapper.insert(List.of(followUser2,followUser1));
    }

    @Override
    public void registerAdmin(String username, String password) {
        registerMethod(username,password,"admin");
    }
    @PostConstruct
    private void init(){
        idSegmentService.createSegment("user",0L,2);
    }
    @Override
    @Transactional
    public void revoke(HttpServletRequest request) {
        SecurityContext context = SecurityContextHolder.getContext();
        String userIdStr = (String) context.getAuthentication().getPrincipal();
        Long userId = Long.valueOf(userIdStr);
        boolean success = removeById(userId);
        if(!success){
            throw new RuntimeException("注销失败");
        }

        Cookie[] cookies = request.getCookies();
        String refreshToken=null;
        if(cookies==null||cookies.length==0){
            return;
        }
        for (Cookie cookie : cookies) {
            if(cookie.getName().equals("refreshToken")){
                refreshToken=cookie.getValue();
                break;
            }
        }
        if(refreshToken==null){
            throw new RuntimeException("refreshToken为null");
        }
        String jwtId = jwtUtil.getJwtId(refreshToken);
        stringRedisTemplate.delete(JtiKey.JTI_TEMPLATE+jwtId);

    }

    @Override
    @Transactional
    public void updatePersonalInfo(UpdateUserDTO updateUserDTO) {
        String userIdStr = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String encodedPassword = null;
        if (updateUserDTO.getPassword() != null && !updateUserDTO.getPassword().isBlank()) {
            encodedPassword = bCryptPasswordEncoder.encode(updateUserDTO.getPassword());
        }
        lambdaUpdate()
                .eq(User::getId,Long.valueOf(userIdStr))
                .set(updateUserDTO.getUsername()!=null,User::getUsername,updateUserDTO.getUsername())
                .set(updateUserDTO.getPassword()!=null,User::getPassword,encodedPassword)
                .set(updateUserDTO.getNickname()!=null,User::getNickname,updateUserDTO.getNickname())
                .update(new User());
    }

    @Override
    public UserVo searchUser(String userName) {
        String username =stringRedisTemplate.opsForValue().get(USER_NAME_KEY + userName);
        if(username==null){
            throw new RuntimeException("搜索用户不存在");
        }
        String fromUserId = (String)SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String fromUsername=baseMapper.selectById(fromUserId).getUsername();
        FollowUser followUser = followUserMapper.selectOne(new LambdaQueryWrapper<FollowUser>().eq(FollowUser::getFromUserName, fromUsername)
                .eq(FollowUser::getToUserName, userName));
        User user = lambdaQuery().eq(User::getUsername, username).one();
        UserVo userVo=new UserVo();
        userVo.setUsername(userName);
        userVo.setNickname(user.getNickname());
        userVo.setIsFollowed(followUser != null);
        return userVo;
    }

    @Override
    public List<String> getUnreadSenders() {
        String userIdStr = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = Long.valueOf(userIdStr);
        String username = userMapper.selectById(userId).getUsername();
        List<ImOffline> offlines = imOfflineMapper.selectByUserName(username);
        if(offlines==null|| CollUtil.isEmpty(offlines)) return List.of();
        List<Long> msgIds = offlines.stream().map(ImOffline::getMsgId).toList();
        List<String> res = chatMessageMapper.selectByIds(msgIds).stream().map(ChatMessage::getFromUserName).toList();
        return res;
    }


    private void registerMethod(String username, String password,String role){
        User registerUser = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if(registerUser !=null){
            throw new RuntimeException("该用户已存在");
        }
        long userId = idSegmentService.nextId("user");
        stringRedisTemplate.opsForValue().set(UserKey.USER_NAME_KEY +username,username);
        String encodePassword = bCryptPasswordEncoder.encode(password);
        User user = User.builder()
                .id(userId)
                .nickname(username)
                .password(encodePassword)
                .status(1)
                .username(username)
                .role(role)
                .build();
        userMapper.insert(user);
    }
}
