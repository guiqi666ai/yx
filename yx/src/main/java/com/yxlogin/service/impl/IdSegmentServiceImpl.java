package com.yxlogin.service.impl;

import com.yxlogin.common.segment.Segment;
import com.yxlogin.common.segment.SegmentBuffer;
import com.yxlogin.entity.IdSegment;
import com.yxlogin.mapper.IdSegmentMapper;
import com.yxlogin.service.IdSegmentService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author yu
 * @since 2026-09-16
 */
@Service
@Slf4j
public class IdSegmentServiceImpl extends ServiceImpl<IdSegmentMapper, IdSegment> implements IdSegmentService {
    private static final double LOAD_THRESHOLD=0.1;
    private static final int TRY_MAX=3;
    private final ConcurrentHashMap<String, SegmentBuffer> buffers=new ConcurrentHashMap<>();
    @Resource
    private ExecutorService userExecutor;
    @Override
    public long nextId(String bizTag) {
        SegmentBuffer segmentBuffer = buffers.computeIfAbsent(bizTag, this::initBuffer);
        //该循环用于多线程同时发现溢出做的并发处理
        while (true){
            Segment current = segmentBuffer.getCurrent();
            if(!current.isOver()){
                long next = current.next();
                if(next<=current.getMaxId()){
                    if(current.remainRate()<LOAD_THRESHOLD) {
                        tryLoadNext(segmentBuffer);
                    }
                    return next;
                }
            }
            switchSegment(segmentBuffer);
        }
    }

    @Override
    public void createSegment(String bizTag,Long maxId,Integer step) {
        try {
            IdSegment idSegment = new IdSegment().setBizTag(bizTag)
                    .setMaxId(maxId)
                    .setStep(step)
                    .setVersion(0L);
            save(idSegment);
        } catch (Exception e) {
            log.error("该业务双号段数据已经存在");
        }

    }

    private SegmentBuffer initBuffer(String bizTag) {
        Segment segment=loadSegment(bizTag);
        return new SegmentBuffer(bizTag,segment);
    }

    private void switchSegment(SegmentBuffer segmentBuffer) {
        segmentBuffer.getLock().lock();
        try {
            //这是在多线程下的双检判断，回到上面那个循环
            if(!segmentBuffer.getCurrent().isOver()){
                return;
            }
            Segment next = segmentBuffer.getNext();
            if(next!=null&&!next.isOver()){
                segmentBuffer.setCurrent(next);
                segmentBuffer.setNext(null);
                segmentBuffer.setLoadingNext(false);
                tryLoadNext(segmentBuffer);
            }else {
                Segment segment = loadSegment(segmentBuffer.getBizTag());
                segmentBuffer.setCurrent(segment);
            }
        }finally {
            segmentBuffer.getLock().unlock();
        }
    }
    private void tryLoadNext(SegmentBuffer segmentBuffer) {
        if(segmentBuffer.getNext()!=null||segmentBuffer.isLoadingNext()){
            return;
        }
        segmentBuffer.setLoadingNext(true);
        userExecutor.submit(()->{
            try {
                Segment segment = loadSegment(segmentBuffer.getBizTag());
                segmentBuffer.setNext(segment);
            }catch (Throwable e){
                log.error("预加载号段失败：{}",segmentBuffer.getBizTag(),e);
            }finally {
                segmentBuffer.setLoadingNext(false);
            }
        });
    }
    private Segment loadSegment(String bizTag) {
        for (int i = 0; i < TRY_MAX; i++) {
            IdSegment idSegment = baseMapper.selectByBizTag(bizTag);
            if(idSegment==null){
                throw new IllegalStateException("biz_tag不存在: " + bizTag);
            }
            int row = baseMapper.updateMaxId(idSegment.getVersion(), idSegment.getBizTag(), idSegment.getStep());

            if(row>0){
                return new Segment(idSegment.getMaxId()+1,idSegment.getMaxId()+idSegment.getStep());
            }
        }
        throw new IllegalStateException("获取号段失败，已达最大重试次数: " + bizTag);
    }
}
