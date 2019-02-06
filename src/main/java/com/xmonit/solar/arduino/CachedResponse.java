package com.xmonit.solar.arduino;

public class CachedResponse<DataT> {

    private String tty;
    private String cmd;
    private DataT data;
    private long whenMs = 0;
    public long maxAgeMs = 30*60*1000;

    public CachedResponse(String cmd){
        this.cmd = cmd;
    }

    public synchronized void invalidate() {
        this.data = null;
        this.tty = null;
        this.whenMs = 0;
    }

    public synchronized void update(DataT data){
        this.data = data;
        this.whenMs = System.currentTimeMillis();
    }

    public synchronized DataT getLatest() {
        boolean expired = System.currentTimeMillis() - whenMs > maxAgeMs;
        return !expired ? data : null;
    }
}
