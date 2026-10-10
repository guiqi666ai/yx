package com.yxlogin.common.segment;

import java.util.concurrent.atomic.AtomicLong;

public class Segment {
    private Long start;
    private Long maxId;
    private AtomicLong value;

    public Segment(Long start,Long maxId){
        this.start=start;
        this.maxId=maxId;
        this.value=new AtomicLong(start-1);
    }
    public boolean isOver(){
        return getCurrent()>=maxId;
    }
    public long next(){
        return value.incrementAndGet();
    }
    public double remainRate(){
        long used=getCurrent()-start+1;
        long total=maxId-start+1;
        return 1.0- (double) used /total;
    }
    public long getCurrent(){
        return value.get();
    }
    public long getStart(){
        return start;
    }
    public long getMaxId(){
        return maxId;
    }
}
