package com.yxlogin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yxlogin.entity.FollowUser;
import com.yxlogin.entity.User;
import com.yxlogin.mapper.FollowUserMapper;
import com.yxlogin.mapper.UserMapper;
import com.yxlogin.service.FollowUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yxlogin.service.IdSegmentService;
import com.yxlogin.vo.UserVo;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


/**
 * <p>
 * 用户关注表 服务实现类
 * </p>
 *
 * @author yu
 * @since 2026-09-23
 */
@Service
public class FollowUserServiceImpl extends ServiceImpl<FollowUserMapper, FollowUser> implements FollowUserService {
    @Resource
    private UserMapper userMapper;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private IdSegmentService idSegmentService;
    @PostConstruct
    void init(){
        idSegmentService.createSegment("followUser",0L,2);
    }
    @Override
    public void follow(String toUserName) {
        String fromUserId = (String)SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String fromUsername=userMapper.selectById(fromUserId).getUsername();
        String lockKey="follow:lock:"+fromUsername+"-"+toUserName;
        RLock rLock = redissonClient.getLock(lockKey);
        try {
            boolean acquired= rLock.tryLock(3, TimeUnit.SECONDS);
            //三秒内不断抢锁
            if(!acquired){
                throw new RuntimeException("操作频繁，请稍后重试");
            }
            FollowUserServiceImpl proxy = (FollowUserServiceImpl) AopContext.currentProxy();
            proxy.doFollow(toUserName, fromUsername);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            if(rLock.isHeldByCurrentThread()){
                rLock.unlock();
            }
        }
    }
    @Transactional(rollbackFor = Exception.class)
    public void doFollow(String toUserName,String fromUsername) throws InterruptedException {

        //这里要注意，查对方对我们的关注要将自己和对方用户名对调
        Optional<FollowUser> optionalFollowUser = lambdaQuery().eq(FollowUser::getFromUserName, toUserName)
                .eq(FollowUser::getToUserName, fromUsername).oneOpt();
        FollowUser selectFollowUser = optionalFollowUser.orElse(null);
        FollowUser followUser=new FollowUser()
                .setFromUserName(fromUsername)
                .setToUserName(toUserName);
        //没有关注
        if(selectFollowUser==null){
            followUser.setIsMutual(0);
        }
        //已关注
        else {
            //没有互关
            if (selectFollowUser.getIsMutual()==0) {
                followUser.setIsMutual(1);
                lambdaUpdate().eq(FollowUser::getFromUserName, toUserName)
                        .eq(FollowUser::getToUserName, fromUsername)
                        .set(FollowUser::getIsMutual,1)
                        .update();
            }
            //互关了
            else {
                throw new RuntimeException("已互关不可以重复关注");
            }
        }
        try {
            save(followUser);
        } catch (DuplicateKeyException e) {
            // 唯一索引冲突，代表已经关注过
            throw new RuntimeException("你已经关注该用户，不可重复关注");
        }
    }

    @Override
    public void cancelFollow(String toUserName) {
        String fromUserId = (String)SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String fromUsername=userMapper.selectById(fromUserId).getUsername();
        String lockKey="follow:lock:"+fromUsername+"-"+toUserName;
        RLock rLock = redissonClient.getLock(lockKey);
        boolean acquired;
        try {
            acquired = rLock.tryLock(3, TimeUnit.SECONDS);
            if(!acquired){
                throw new RuntimeException("操作频繁，请稍后重试");
            }
            FollowUserServiceImpl proxy = (FollowUserServiceImpl) AopContext.currentProxy();
            proxy.doCancel(toUserName, fromUsername);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            if(rLock.isHeldByCurrentThread()){
                rLock.unlock();
            }
        }
    }

    @Override
    public List<UserVo> getFriends() {
        String fromUserId = (String)SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String fromUsername=userMapper.selectById(fromUserId).getUsername();
        List<FollowUser> followUsers = lambdaQuery().eq(FollowUser::getFromUserName, fromUsername)
                .eq(FollowUser::getIsMutual, 1).list();
        List<String> tUserNameList = followUsers.stream().map(FollowUser::getToUserName).collect(Collectors.toList());
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getUsername, tUserNameList));
        Map<String, String> map = users.stream().collect(Collectors.toMap(User::getUsername, User::getNickname));
        return followUsers.stream()
                .map(followUser -> {
                    UserVo userVo=new UserVo();
                    userVo.setNickname(map.get(followUser.getToUserName()));
                    userVo.setUsername(followUser.getToUserName());
                    return userVo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<UserVo> getMyFollow() {
        String fromUserId = (String)SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userMapper.selectById(fromUserId);
        String fromUsername = user.getUsername();
        List<FollowUser> followUsers = lambdaQuery().eq(FollowUser::getFromUserName, fromUsername)
                .list();
        List<String> tUserNameList = followUsers.stream().map(FollowUser::getToUserName).collect(Collectors.toList());
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getUsername, tUserNameList));
        Map<String, String> map = users.stream().collect(Collectors.toMap(User::getUsername, User::getNickname));

        return followUsers.stream()
                .map(followUser -> {
                    UserVo userVo=new UserVo();
                    userVo.setIsFollowed(followUser.getIsMutual()==1);
                    userVo.setNickname(map.get(followUser.getToUserName()));
                    userVo.setUsername(followUser.getToUserName());
                    return userVo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<UserVo> getFollowMy() {
        String fromUserId = (String)SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String fromUsername=userMapper.selectById(fromUserId).getUsername();
        List<FollowUser> followUsers = lambdaQuery().eq(FollowUser::getToUserName, fromUsername)
                .list();
        List<String> fUserNameList = followUsers.stream().map(FollowUser::getFromUserName).collect(Collectors.toList());
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getUsername, fUserNameList));
        Map<String, String> map = users.stream().collect(Collectors.toMap(User::getUsername, User::getNickname));

        return followUsers.stream()
                .map(followUser -> {
                    UserVo userVo=new UserVo();
                    userVo.setIsFollowed(followUser.getIsMutual()==1);
                    userVo.setNickname(map.get(followUser.getFromUserName()));
                    userVo.setUsername(followUser.getFromUserName());
                    return userVo;
                })
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public void doCancel(String toUserName, String fromUsername) {
        Optional<FollowUser> optionalFollowUser = lambdaQuery().eq(FollowUser::getFromUserName, fromUsername)
                .eq(FollowUser::getToUserName, toUserName).oneOpt();
        FollowUser followUser = optionalFollowUser.orElse(null);
        if(followUser==null) throw new RuntimeException("没有关注，不能进行取关");
        Optional<FollowUser> optionalSelectFollowUser = lambdaQuery().eq(FollowUser::getFromUserName, toUserName)
                .eq(FollowUser::getToUserName, fromUsername).oneOpt();
        removeById(followUser);
        optionalSelectFollowUser.ifPresent(selectFollowUser -> lambdaUpdate().eq(FollowUser::getFromUserName, toUserName)
                .eq(FollowUser::getToUserName, fromUsername)
                .set(FollowUser::getIsMutual, 0)
                .update());
    }
}
