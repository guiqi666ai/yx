package com.yxlogin.service;

import com.yxlogin.entity.IdSegment;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author yu
 * @since 2026-09-16
 */
public interface IdSegmentService extends IService<IdSegment> {
    long nextId(String bizTag);
    void createSegment(String bizTag,Long maxId,Integer step);
}
