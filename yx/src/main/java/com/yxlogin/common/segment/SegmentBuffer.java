package com.yxlogin.common.segment;

import java.util.concurrent.locks.ReentrantLock;

public class SegmentBuffer {
    private final String bizTag;
    private volatile Segment next;
    private volatile Segment current;
    private volatile boolean loadingNext=false;
    private final ReentrantLock reentrantLock=new ReentrantLock();
    public SegmentBuffer(String bizTag,Segment current){
        this.bizTag=bizTag;
        this.current=current;
    }
    public String getBizTag(){
        return bizTag;
    }
    public void setNext(Segment next){
        this.next=next;
    }
    public Segment getNext(){
        return next;
    }
    public Segment getCurrent() { return current; }
    public void setCurrent(Segment current) { this.current = current; }
    public boolean isLoadingNext() { return loadingNext; }
    public void setLoadingNext(boolean loadingNext) { this.loadingNext = loadingNext; }
    public ReentrantLock getLock() { return reentrantLock; }
}
